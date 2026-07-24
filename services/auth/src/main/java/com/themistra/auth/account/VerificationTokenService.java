package com.themistra.auth.account;

import com.themistra.auth.common.Hashing;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Single-use, hashed, TTL'd tokens for email verification and password reset (R3–R5, L5).
 * Purpose-generic by design ({@link VerificationToken.Purpose#EMAIL_VERIFY} /
 * {@link VerificationToken.Purpose#PASSWORD_RESET}) — this service only rejects tokens whose
 * account is {@code DELETED}/{@code SUSPENDED}; purpose-specific account-state rules belong to
 * callers.
 *
 * <p>{@code verify} and {@code consume} both return {@code Optional<UUID>} — empty for every
 * failure reason (not found, expired, already used, or an unusable account) — deliberately
 * uniform so no caller can distinguish why a token was rejected (R5).</p>
 */
@Service
public class VerificationTokenService {

    private static final int RAW_TOKEN_BYTES = 32;
    private static final int MAX_ISSUE_ATTEMPTS = 3;

    private final VerificationTokenRepository tokenRepository;
    private final AccountRepository accountRepository;
    private final VerificationTokenProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public VerificationTokenService(VerificationTokenRepository tokenRepository,
                                    AccountRepository accountRepository,
                                    VerificationTokenProperties properties, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Invalidates any prior unused token for {@code (accountUuid, purpose)}, then issues a new
     * one. The raw token is returned exactly once; only its hash is persisted.
     *
     * @throws AccountNotFoundException if {@code accountUuid} does not resolve to an account —
     * this is an internal-caller error signal, not the uniform R5 path (unlike {@code verify}/
     * {@code consume}, {@code issue} is never called with attacker-controlled input).
     */
    @Transactional
    public VerificationTokenResult issue(UUID accountUuid, VerificationToken.Purpose purpose) {
        Objects.requireNonNull(accountUuid, "accountUuid must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");

        Account account = accountRepository.findByAccountUuid(accountUuid)
                .orElseThrow(() -> new AccountNotFoundException(accountUuid));

        Instant now = clock.instant();
        tokenRepository.invalidateActive(account.getId(), purpose, now);

        Instant expiresAt = now.plus(properties.ttlMinutes(), ChronoUnit.MINUTES);
        for (int attempt = 1; attempt <= MAX_ISSUE_ATTEMPTS; attempt++) {
            String rawToken = generateRawToken();
            String tokenHash = Hashing.sha256(rawToken);
            VerificationToken token =
                    VerificationToken.create(account.getId(), purpose, tokenHash, now, expiresAt);
            try {
                VerificationToken saved = tokenRepository.saveAndFlush(token);
                return new VerificationTokenResult(rawToken, saved, accountUuid, purpose);
            } catch (DataIntegrityViolationException e) {
                // token_hash collision (astronomically unlikely at 32 random bytes) - retry
            }
        }
        throw new IllegalStateException(
                "Failed to generate a unique verification token after " + MAX_ISSUE_ATTEMPTS + " attempts");
    }

    /** Read-only check; does not mutate state. */
    @Transactional(readOnly = true)
    public Optional<UUID> verify(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");

        Optional<VerificationToken> tokenOpt =
                tokenRepository.findByTokenHash(Hashing.sha256(rawToken));
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }
        VerificationToken token = tokenOpt.get();
        Instant now = clock.instant();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        return resolveUsableAccount(token.getAccountId());
    }

    /** Atomic verify-and-mark; the sole state-mutating redemption path. */
    @Transactional
    public Optional<UUID> consume(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");

        String tokenHash = Hashing.sha256(rawToken);
        Optional<VerificationToken> tokenOpt = tokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }
        VerificationToken token = tokenOpt.get();

        // Resolve and check the account before mutating anything: a token belonging to a
        // deleted/suspended account must never be marked used by a rejected attempt.
        Optional<UUID> accountUuid = resolveUsableAccount(token.getAccountId());
        if (accountUuid.isEmpty()) {
            return Optional.empty();
        }

        int updated = tokenRepository.markConsumed(tokenHash, clock.instant());
        if (updated == 0) {
            return Optional.empty();
        }
        return accountUuid;
    }

    private Optional<UUID> resolveUsableAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .filter(this::isAccountUsable)
                .map(Account::getAccountUuid);
    }

    private boolean isAccountUsable(Account account) {
        return account.getStatus() != AccountStatus.DELETED
                && account.getStatus() != AccountStatus.SUSPENDED;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * @param rawToken returned exactly once; never persisted or logged.
     * @param token the persisted row (hash only — no raw-token field exists to leak).
     */
    public record VerificationTokenResult(
            String rawToken, VerificationToken token, UUID accountUuid, VerificationToken.Purpose purpose) {
    }
}
