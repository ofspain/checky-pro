package com.themistra.auth.apikey;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.InvalidAccountStateException;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.dto.AuditEventResponse;
import com.themistra.auth.authz.DuplicateRoleException;
import com.themistra.auth.authz.DuplicateRoleTemplateException;
import com.themistra.auth.authz.RoleService;
import com.themistra.auth.authz.dto.CreateRoleRequest;
import com.themistra.auth.authz.dto.CreateRoleTemplateRequest;
import com.themistra.auth.common.Hashing;
import com.themistra.auth.mfa.MfaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real Postgres (Testcontainers) — proves T24's behaviors against the actual
 * schema (post-{@code V7}) and the real {@code AccountService}/{@code RoleService}/
 * {@code MfaService} collaborators, not mocks. Fixture accounts go through
 * {@code AccountService.register}/{@code activateEmail} directly (no breach-check property
 * override needed — matches {@code SasLoginIntegrationTest}'s established working precedent, not
 * {@code MfaPersistenceIntegrationTest}'s repository-bypassing pattern, which is what actually
 * needed that override).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApiKeyServiceIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final Pattern PLAINTEXT_KEY_PATTERN =
            Pattern.compile("^ck_live_[A-Za-z0-9]{24}\\.[A-Za-z0-9]{32}$");

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private AccountService accountService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    /** {@code revokeIfActive} and the raw {@code EntityManager} update in {@link
     * #createRawApiKeyRow} are custom/native operations called directly from this test, bypassing
     * {@code ApiKeyService}'s own {@code @Transactional} boundary — they need their own
     * short-lived transaction, matching {@code MfaPersistenceIntegrationTest}'s established
     * {@code inOwnTransaction} convention for exactly this situation. */
    private void inOwnTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    @Test // AC1-AC4, AC10 - the named test (R30)
    void shouldCreateApiKeyAndShowPlaintextExactlyOnce() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("create-plaintext@example.com");

        ApiKeyService.CreateApiKeyResult result = apiKeyService.create(accountUuid, "CI pipeline key");

        assertThat(result.plaintextKey()).matches(PLAINTEXT_KEY_PATTERN);
        entityManager.clear();
        ApiKey stored = apiKeyRepository.findByKeyUuid(result.keyUuid()).orElseThrow();
        assertThat(stored.getPrefix()).hasSize(32);
        assertThat(stored.getKeyHash()).isEqualTo(Hashing.sha256(result.plaintextKey()));
        assertThat(stored.getKeyHash()).isNotEqualTo(result.plaintextKey());
        assertThat(stored.getScopes()).containsExactly("merchant.api");
        assertThat(latestAuditEventType(accountUuid)).isEqualTo("api_key.created");
    }

    @Test
    void createRejectsNonMerchantAccount() {
        UUID accountUuid = registerAndActivate("no-merchant-role@example.com");

        assertThatThrownBy(() -> apiKeyService.create(accountUuid, "key"))
                .isInstanceOf(ApiKeyNotAuthorizedException.class);
    }

    @Test
    void createRejectsUnconfirmedMfa() {
        UUID accountUuid = registerAndActivate("no-confirmed-mfa@example.com");
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", accountUuid);

        assertThatThrownBy(() -> apiKeyService.create(accountUuid, "key"))
                .isInstanceOf(ApiKeyNotAuthorizedException.class);
    }

    @Test
    void createRejectsNonActiveAccount() {
        AccountResponse registered = accountService.register(
                new RegisterAccountRequest("pending-verification@example.com", PASSWORD));

        assertThatThrownBy(() -> apiKeyService.create(registered.accountUuid(), "key"))
                .isInstanceOf(InvalidAccountStateException.class);
    }

    @Test
    void createRejectsBlankOrOverlongName() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("invalid-name@example.com");

        assertThatThrownBy(() -> apiKeyService.create(accountUuid, ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> apiKeyService.create(accountUuid, "x".repeat(101)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test // AC9 - the named test (R33): revoked, expired, malformed, and hash-mismatched all
          // uniform
    void shouldRejectRevokedOrUnknownApiKeyWithUniform401() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("uniform-rejection@example.com");

        assertThatThrownBy(() -> apiKeyService.exchange("not-a-valid-key-shape"))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        assertThatThrownBy(() -> apiKeyService.exchange("ck_live_totallyunknown12345678.abcdefghijklmnopqrstuvwxyzabcdef"))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        assertThatThrownBy(() -> apiKeyService.exchange(".abcdefghijklmnopqrstuvwxyzabcdef")) // empty prefix
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
        assertThatThrownBy(() -> apiKeyService.exchange("ck_live_validprefixshape00000000.")) // empty secret
                .isInstanceOf(ApiKeyExchangeRejectedException.class);

        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "wrong secret target");
        String[] parts = created.plaintextKey().split("\\.", 2);
        String wrongSecretKey = parts[0] + "." + "z".repeat(32);
        assertThatThrownBy(() -> apiKeyService.exchange(wrongSecretKey))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);

        apiKeyService.revoke(accountUuid, created.keyUuid());
        assertThatThrownBy(() -> apiKeyService.exchange(created.plaintextKey()))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);

        Long accountId = apiKeyRepository.findAccountIdByUuid(accountUuid).orElseThrow();
        String expiredPlaintext = createRawApiKeyRow(
                accountId, "expired key", Instant.now().minusSeconds(3600));
        assertThatThrownBy(() -> apiKeyService.exchange(expiredPlaintext))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);
    }

    @Test
    void exchangeUpdatesLastUsedAt() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("touch-last-used@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "touch key");
        assertThat(soleMetadata(accountUuid, created.keyUuid()).lastUsedAt()).isNull();

        ApiKeyService.ExchangeResult result = apiKeyService.exchange(created.plaintextKey());

        assertThat(soleMetadata(accountUuid, created.keyUuid()).lastUsedAt()).isNotNull();
        assertThat(result.accountUuid()).isEqualTo(accountUuid);
        assertThat(result.scopes()).containsExactly("merchant.api");
        assertThat(latestAuditEventType(accountUuid)).isEqualTo("api_key.exchanged");
    }

    @Test // Phase 3/9 disposition #4: every findByPrefix candidate is checked, success finds the
          // right one even when it isn't the first row returned
    void exchangeChecksEveryPrefixCollisionCandidate() {
        UUID accountA = registerAndActivate("collision-a@example.com");
        UUID accountB = registerAndActivate("collision-b@example.com");
        Long accountIdA = apiKeyRepository.findAccountIdByUuid(accountA).orElseThrow();
        Long accountIdB = apiKeyRepository.findAccountIdByUuid(accountB).orElseThrow();
        String sharedPrefix = "ck_live_sharedcollision01";

        apiKeyRepository.save(ApiKey.create(accountIdA, sharedPrefix,
                Hashing.sha256(sharedPrefix + ".firstsecretxxxxxxxxxxxxxxxxxxxx"),
                "A's key", List.of(), Instant.now()));
        apiKeyRepository.save(ApiKey.create(accountIdB, sharedPrefix,
                Hashing.sha256(sharedPrefix + ".secondsecretyyyyyyyyyyyyyyyyyyy"),
                "B's key", List.of(), Instant.now()));

        ApiKeyService.ExchangeResult result =
                apiKeyService.exchange(sharedPrefix + ".secondsecretyyyyyyyyyyyyyyyyyyy");

        assertThat(result.accountUuid()).isEqualTo(accountB);
    }

    @Test // Phase 8/9 finding #4's fix: a revoked/expired MATCH is audited against its own
          // account, not merely whichever candidate came first for a shared prefix
    void exchangeAuditsTheMatchedAccountEvenWhenItIsNotTheFirstCandidate() {
        UUID accountA = registerAndActivate("audit-collision-a@example.com");
        UUID accountB = registerAndActivate("audit-collision-b@example.com");
        Long accountIdA = apiKeyRepository.findAccountIdByUuid(accountA).orElseThrow();
        Long accountIdB = apiKeyRepository.findAccountIdByUuid(accountB).orElseThrow();
        String sharedPrefix = "ck_live_auditcollision01";
        String bSecretKey = sharedPrefix + ".revokedsecretzzzzzzzzzzzzzzzzzzz";

        apiKeyRepository.save(ApiKey.create(accountIdA, sharedPrefix,
                Hashing.sha256(sharedPrefix + ".activesecretxxxxxxxxxxxxxxxxxxx"),
                "A's active key", List.of(), Instant.now()));
        ApiKey bKey = apiKeyRepository.save(ApiKey.create(accountIdB, sharedPrefix,
                Hashing.sha256(bSecretKey), "B's revoked key", List.of(), Instant.now()));
        inOwnTransaction(() -> apiKeyRepository.revokeIfActive(bKey.getId(), Instant.now()));

        assertThatThrownBy(() -> apiKeyService.exchange(bSecretKey))
                .isInstanceOf(ApiKeyExchangeRejectedException.class);

        assertThat(latestAuditEventType(accountB)).isEqualTo("api_key.exchange_failed");
        assertThat(latestAuditEventType(accountA)).isNotEqualTo("api_key.exchange_failed");
    }

    @Test
    void listReturnsOnlyTheCallersOwnKeysWithNoSecretMaterial() {
        UUID accountA = seedMerchantWithConfirmedMfa("list-a@example.com");
        UUID accountB = seedMerchantWithConfirmedMfa("list-b@example.com");
        apiKeyService.create(accountA, "A key 1");
        apiKeyService.create(accountA, "A key 2");
        apiKeyService.create(accountB, "B key 1");

        List<ApiKeyService.ApiKeyMetadata> aKeys = apiKeyService.list(accountA);

        assertThat(aKeys).hasSize(2);
        assertThat(aKeys).extracting(ApiKeyService.ApiKeyMetadata::name)
                .containsExactlyInAnyOrder("A key 1", "A key 2");
        // ApiKeyMetadata has no hash/secret field by construction - nothing further to assert.
    }

    @Test // Phase 9 fix for finding #5/#2: idempotent revoke records exactly one audit event
    void revokeIsIdempotent() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("idempotent-revoke@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "revoke me");

        apiKeyService.revoke(accountUuid, created.keyUuid());
        apiKeyService.revoke(accountUuid, created.keyUuid());

        long revokedEventCount = auditService.list(accountUuid, Pageable.unpaged()).stream()
                .filter(event -> event.eventType().equals("api_key.revoked"))
                .count();
        assertThat(revokedEventCount).isEqualTo(1);
    }

    @Test
    void revokeOfNonOwnedKeyFails() {
        UUID owner = seedMerchantWithConfirmedMfa("owner@example.com");
        UUID stranger = seedMerchantWithConfirmedMfa("stranger@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(owner, "not yours");

        assertThatThrownBy(() -> apiKeyService.revoke(stranger, created.keyUuid()))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test // Phase 11 gap #5: the "doesn't exist at all" half of the no-enumeration contract,
          // distinct from revokeOfNonOwnedKeyFails's "exists but isn't yours" half
    void revokeOfUnknownKeyFails() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("revoke-unknown@example.com");

        assertThatThrownBy(() -> apiKeyService.revoke(accountUuid, UUID.randomUUID()))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test // Phase 11 gap #6: list's actual (undocumented-by-a-requirement-ID) contract - revoked
          // keys still appear, with a non-null revokedAt, rather than being filtered out
    void listIncludesRevokedKeys() {
        UUID accountUuid = seedMerchantWithConfirmedMfa("list-revoked@example.com");
        ApiKeyService.CreateApiKeyResult created = apiKeyService.create(accountUuid, "will be revoked");

        apiKeyService.revoke(accountUuid, created.keyUuid());

        assertThat(soleMetadata(accountUuid, created.keyUuid()).revokedAt()).isNotNull();
    }

    @Test // Phase 11 gap #8: RoleService.resolveEffectiveRoles expands templates, not just direct
          // assignments - the MERCHANT gate must honor that, not just a direct-assignment check
    void createAcceptsMerchantViaRoleTemplate() {
        String email = "merchant-via-template@example.com";
        UUID accountUuid = registerAndActivate(email);
        ensureRoleExists("MERCHANT");
        ensureRoleTemplateExists("MERCHANT_TEMPLATE", Set.of("MERCHANT"));
        roleService.assignRoleTemplate(accountUuid, "MERCHANT_TEMPLATE", accountUuid);
        seedConfirmedMfa(accountUuid);

        ApiKeyService.CreateApiKeyResult result = apiKeyService.create(accountUuid, "via template");

        assertThat(result.plaintextKey()).matches(PLAINTEXT_KEY_PATTERN);
    }

    private UUID seedMerchantWithConfirmedMfa(String email) {
        UUID accountUuid = registerAndActivate(email);
        ensureRoleExists("MERCHANT");
        roleService.assignRole(accountUuid, "MERCHANT", accountUuid);
        seedConfirmedMfa(accountUuid);
        return accountUuid;
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, PASSWORD));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    private void ensureRoleExists(String roleName) {
        try {
            roleService.createRole(new CreateRoleRequest(roleName, null));
        } catch (DuplicateRoleException e) {
            // Already created by an earlier test in this class - fine.
        }
    }

    private void ensureRoleTemplateExists(String templateName, Set<String> roleNames) {
        try {
            roleService.createRoleTemplate(new CreateRoleTemplateRequest(templateName, null, roleNames));
        } catch (DuplicateRoleTemplateException e) {
            // Already created by an earlier test in this class - fine.
        }
    }

    private void seedConfirmedMfa(UUID accountUuid) {
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        mfaService.confirm(accountUuid, code);
    }

    /** Bypasses {@link ApiKeyService#create} to construct a row with an {@code expiresAt} in the
     * past — T24's own {@code create} never sets an expiry, so this is the only way to exercise
     * the expiry branch of {@code exchange}. Returns the plaintext key that would exchange to it. */
    private String createRawApiKeyRow(Long accountId, String name, Instant expiresAt) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String secret = UUID.randomUUID().toString().replace("-", "") + "12345678";
        String prefix = "ck_live_" + suffix;
        String fullKey = prefix + "." + secret;
        ApiKey apiKey = ApiKey.create(accountId, prefix, Hashing.sha256(fullKey), name, List.of(), Instant.now());
        apiKeyRepository.save(apiKey);
        // expiresAt has no factory/mutator path (T23/T24 deliberately never set it) - set directly
        // via a native update, the only way to construct this state for this test.
        inOwnTransaction(() -> setExpiresAtDirectly(apiKey.getId(), expiresAt));
        return fullKey;
    }

    private void setExpiresAtDirectly(Long id, Instant expiresAt) {
        entityManager.createNativeQuery("UPDATE api_keys SET expires_at = :expiresAt WHERE id = :id")
                .setParameter("expiresAt", expiresAt)
                .setParameter("id", id)
                .executeUpdate();
    }

    private ApiKeyService.ApiKeyMetadata soleMetadata(UUID accountUuid, UUID keyUuid) {
        return apiKeyService.list(accountUuid).stream()
                .filter(metadata -> metadata.keyUuid().equals(keyUuid))
                .findFirst()
                .orElseThrow();
    }

    private String latestAuditEventType(UUID accountUuid) {
        return auditService.list(accountUuid, Pageable.unpaged()).stream()
                .max(java.util.Comparator.comparing(AuditEventResponse::occurredAt))
                .map(AuditEventResponse::eventType)
                .orElseThrow();
    }

    /** Independent RFC 4226/6238 HOTP/TOTP implementation, deliberately separate code from {@code
     * TotpVerifier} — same discipline {@code SasLoginIntegrationTest} already applies. */
    private static String referenceGenerateCode(byte[] secret, Instant now) {
        long timeCounter = Math.floorDiv(now.getEpochSecond(), 30);
        byte[] counterBytes = new byte[8];
        long counter = timeCounter;
        for (int i = 7; i >= 0; i--) {
            counterBytes[i] = (byte) (counter & 0xFF);
            counter >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(counterBytes);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
