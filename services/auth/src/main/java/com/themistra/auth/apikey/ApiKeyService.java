package com.themistra.auth.apikey;

import com.themistra.auth.account.AccountNotFoundException;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.InvalidAccountStateException;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.mfa.MfaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Create, list, revoke, and exchange merchant API keys (R30/R32/R33, L7). No {@code Account}
 * entity import (L12) — account interaction composes only {@link AccountService}'s public methods
 * and {@link ApiKeyRepository}'s UUID/id resolvers, the same discipline {@link MfaService}
 * established.
 *
 * <p>No controller exists yet (T25/T26) — {@link #create} independently re-verifies the
 * {@code MERCHANT} role, {@code ACTIVE} account status, and confirmed MFA itself rather than
 * trusting a caller, matching {@code MfaService.requireActiveAccount}'s defense-in-depth
 * precedent (frozen brief, Phase 4 disposition #6).</p>
 */
@Service
public class ApiKeyService {

    private static final String MERCHANT_ROLE = "MERCHANT";
    private static final List<String> DEFAULT_SCOPES = List.of("merchant.api");
    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 24;
    private static final int SECRET_LENGTH = 32;
    private static final int MAX_NAME_LENGTH = 100;
    /** A syntactically valid but unattainable SHA-256 hex digest, compared against on the
     * malformed-key/unknown-prefix rejection paths purely to normalize their timing against the
     * real comparison path below — never matches anything, never persisted. */
    private static final String DUMMY_HASH = "0".repeat(64);

    private final ApiKeyRepository apiKeyRepository;
    private final AccountService accountService;
    private final RoleService roleService;
    private final MfaService mfaService;
    private final AuditService auditService;
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyProperties apiKeyProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, AccountService accountService,
                         RoleService roleService, MfaService mfaService, AuditService auditService,
                         ApiKeyHasher apiKeyHasher, ApiKeyProperties apiKeyProperties, Clock clock) {
        this.apiKeyRepository = apiKeyRepository;
        this.accountService = accountService;
        this.roleService = roleService;
        this.mfaService = mfaService;
        this.auditService = auditService;
        this.apiKeyHasher = apiKeyHasher;
        this.apiKeyProperties = apiKeyProperties;
        this.clock = clock;
    }

    /**
     * Creates a new API key (R30). Requires the caller to hold {@code MERCHANT}, be
     * {@code ACTIVE}, and have confirmed MFA (frozen brief disposition #6) — all three checked
     * here, not assumed. The plaintext key is returned exactly once; only its SHA-256 hash is
     * persisted.
     *
     * @throws IllegalArgumentException if {@code name} is blank or longer than 100 characters
     * @throws InvalidAccountStateException if the account is not {@code ACTIVE}
     * @throws ApiKeyNotAuthorizedException if the caller lacks {@code MERCHANT} or confirmed MFA
     */
    @Transactional
    public CreateApiKeyResult create(UUID accountUuid, String name) {
        requireValidName(name);
        requireMerchantWithConfirmedMfa(accountUuid);
        Long accountId = resolveAccountId(accountUuid);

        String suffix = randomAlphanumeric(SUFFIX_LENGTH);
        String secret = randomAlphanumeric(SECRET_LENGTH);
        String prefix = apiKeyProperties.prefix() + suffix;
        String fullKey = prefix + "." + secret;
        String keyHash = apiKeyHasher.hash(fullKey);
        Instant now = clock.instant();

        ApiKey apiKey = apiKeyRepository.save(
                ApiKey.create(accountId, prefix, keyHash, name, DEFAULT_SCOPES, now));

        recordAudit("api_key.created", AuditOutcome.SUCCESS, accountUuid, accountUuid);
        return new CreateApiKeyResult(apiKey.getKeyUuid(), fullKey, apiKey.getName(), now);
    }

    /** Every key the caller owns, metadata only — never the hash (task statement; transitively, R34). */
    @Transactional(readOnly = true)
    public List<ApiKeyMetadata> list(UUID accountUuid) {
        Long accountId = resolveAccountId(accountUuid);
        return apiKeyRepository.findByAccountId(accountId).stream().map(this::toMetadata).toList();
    }

    /**
     * Revokes a key the caller owns (task statement; transitively, R35). Idempotent: revoking an
     * already-revoked key succeeds silently and records no additional audit event, via
     * {@link ApiKeyRepository#revokeIfActive}'s rows-affected return value — matching
     * {@code RoleService.removeRole}'s "nothing changed, nothing to audit" precedent exactly. A
     * key that doesn't exist or isn't owned by the caller is rejected identically
     * ({@link ApiKeyNotFoundException}) — no enumeration oracle.
     */
    @Transactional
    public void revoke(UUID accountUuid, UUID keyUuid) {
        Long accountId = resolveAccountId(accountUuid);
        ApiKey apiKey = apiKeyRepository.findByKeyUuid(keyUuid)
                .filter(key -> key.getAccountId().equals(accountId))
                .orElseThrow(ApiKeyNotFoundException::new);

        if (apiKeyRepository.revokeIfActive(apiKey.getId(), clock.instant()) > 0) {
            recordAudit("api_key.revoked", AuditOutcome.SUCCESS, accountUuid, accountUuid);
        }
    }

    /**
     * Validates a presented key ({@code prefix.secret}) and, on success, updates
     * {@code last_used_at} (R32). Every candidate {@code findByPrefix} returns is checked with a
     * constant-time comparison — the loop never short-circuits on the first match — before this
     * method decides success or failure, closing the timing side-channel a first-match-wins
     * short-circuit would open (frozen brief disposition #4). The malformed-key and
     * unknown-prefix paths perform a dummy comparison against {@link #DUMMY_HASH} before
     * rejecting, so they take comparable time to the real mismatch path rather than returning
     * measurably faster (Phase 8 finding #6).
     *
     * <p>Revoked, expired, malformed, and hash-mismatched keys are all rejected identically via
     * {@link ApiKeyExchangeRejectedException} (R33). Every rejection is audited: when a specific
     * row's hash actually matched but it's revoked/expired, the audit targets that row's own
     * account, not merely the first candidate sharing its prefix (Phase 8 finding #4); a malformed
     * key, unknown prefix, or hash mismatch against every candidate has no single identifiable
     * "intended" row, so those audit with a {@code null} account, which {@link AuditService}
     * already supports for account-less events (frozen brief disposition #10).</p>
     *
     * <p><strong>Known, deliberate scope limit (Phase 8 finding #9):</strong> this method does not
     * verify the owning account is still {@code ACTIVE} — a suspended/deleted merchant's existing
     * key remains valid until explicitly revoked or expired. R30/R32/R33 (this task's scoped
     * requirements) do not call for an account-status check here, and adding one means an extra
     * account lookup on every exchange call, a potentially hot path. Documented, not silently
     * assumed — a future task should decide whether to close this gap.</p>
     */
    @Transactional
    public ExchangeResult exchange(String presentedKey) {
        int separator = presentedKey == null ? -1 : presentedKey.indexOf('.');
        if (separator <= 0 || separator == presentedKey.length() - 1) {
            apiKeyHasher.matches(String.valueOf(presentedKey), DUMMY_HASH);
            recordAudit("api_key.exchange_failed", AuditOutcome.FAILURE, null, null);
            throw new ApiKeyExchangeRejectedException();
        }
        String prefix = presentedKey.substring(0, separator);

        List<ApiKey> candidates = apiKeyRepository.findByPrefix(prefix);
        if (candidates.isEmpty()) {
            apiKeyHasher.matches(presentedKey, DUMMY_HASH);
            recordAudit("api_key.exchange_failed", AuditOutcome.FAILURE, null, null);
            throw new ApiKeyExchangeRejectedException();
        }

        ApiKey matched = null;
        for (ApiKey candidate : candidates) {
            boolean hashMatches = apiKeyHasher.matches(presentedKey, candidate.getKeyHash());
            if (hashMatches && matched == null) {
                matched = candidate;
            }
        }

        Instant now = clock.instant();
        boolean eligible = matched != null
                && matched.getRevokedAt() == null
                && (matched.getExpiresAt() == null || matched.getExpiresAt().isAfter(now));

        if (!eligible) {
            // matched != null: this specific row's hash was correct but it's revoked/expired -
            // audit its own account. matched == null: no candidate's hash matched at all, so no
            // row can be identified as "the intended one" - fall back to the first candidate
            // sharing this prefix (in practice the only one; per frozen brief disposition #10,
            // "a row was matched" for audit purposes means a prefix-level candidate exists, not
            // that its hash matched).
            Long accountIdForAudit = matched != null ? matched.getAccountId() : candidates.getFirst().getAccountId();
            UUID auditAccountUuid = resolveAccountUuidQuietly(accountIdForAudit);
            recordAudit("api_key.exchange_failed", AuditOutcome.FAILURE, auditAccountUuid, auditAccountUuid);
            throw new ApiKeyExchangeRejectedException();
        }

        apiKeyRepository.updateLastUsedAt(matched.getId(), now);
        UUID accountUuid = resolveAccountUuidQuietly(matched.getAccountId());
        recordAudit("api_key.exchanged", AuditOutcome.SUCCESS, accountUuid, accountUuid);
        return new ExchangeResult(accountUuid, matched.getScopes());
    }

    private void requireValidName(String name) {
        if (name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "name must be non-blank and at most " + MAX_NAME_LENGTH + " characters");
        }
    }

    private void requireMerchantWithConfirmedMfa(UUID accountUuid) {
        AccountResponse account = accountService.getByUuid(accountUuid);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException(accountUuid, account.status(), "create an API key");
        }
        if (!roleService.resolveEffectiveRoles(accountUuid).contains(MERCHANT_ROLE)) {
            throw new ApiKeyNotAuthorizedException();
        }
        if (!mfaService.hasConfirmedTotpEnrollment(accountUuid)) {
            throw new ApiKeyNotAuthorizedException();
        }
    }

    private Long resolveAccountId(UUID accountUuid) {
        return apiKeyRepository.findAccountIdByUuid(accountUuid)
                .orElseThrow(() -> new AccountNotFoundException(accountUuid));
    }

    /** Best-effort: only used to pick an audit target, never to gate exchange's success/failure
     * decision — a missing account here (should not happen in practice) simply yields a null
     * audit target rather than failing the exchange call itself. */
    private UUID resolveAccountUuidQuietly(Long accountId) {
        return apiKeyRepository.findAccountUuidById(accountId).orElse(null);
    }

    private String randomAlphanumeric(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHANUMERIC.charAt(secureRandom.nextInt(ALPHANUMERIC.length())));
        }
        return builder.toString();
    }

    private ApiKeyMetadata toMetadata(ApiKey apiKey) {
        return new ApiKeyMetadata(apiKey.getKeyUuid(), apiKey.getName(), apiKey.getScopes(),
                apiKey.getCreatedAt(), apiKey.getLastUsedAt(), apiKey.getExpiresAt(), apiKey.getRevokedAt());
    }

    private void recordAudit(String eventType, AuditOutcome outcome, UUID accountUuid, UUID actorUuid) {
        auditService.record(new RecordAuditEventRequest(
                eventType, outcome, accountUuid, actorUuid, null, null, null, null));
    }

    /** @param plaintextKey the full {@code prefix.secret} key; returned exactly once, never persisted. */
    public record CreateApiKeyResult(UUID keyUuid, String plaintextKey, String name, Instant createdAt) {

        public CreateApiKeyResult {
            Objects.requireNonNull(keyUuid, "keyUuid must not be null");
            Objects.requireNonNull(plaintextKey, "plaintextKey must not be null");
        }

        /** Overridden so the plaintext key can never leak via a default record {@code toString()}. */
        @Override
        public String toString() {
            return "CreateApiKeyResult[REDACTED]";
        }
    }

    /** No hash/secret field by construction — this record cannot leak key material. */
    public record ApiKeyMetadata(UUID keyUuid, String name, List<String> scopes, Instant createdAt,
                                  Instant lastUsedAt, Instant expiresAt, Instant revokedAt) {
    }

    public record ExchangeResult(UUID accountUuid, List<String> scopes) {
    }
}
