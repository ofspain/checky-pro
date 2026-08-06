package com.themistra.auth.mfa;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real Postgres (Testcontainers) — verifies what {@link MfaEnrollmentTest}'s
 * and {@link RecoveryCodeTest}'s plain-JUnit entity tests structurally cannot: that both
 * repositories' native/derived queries actually work against the real schema, and that the DB's
 * own {@code UNIQUE(account_id, type)} constraint is what really enforces "one confirmed
 * enrollment per account" (frozen brief, Finding #1's resolution).
 *
 * <p>{@code breach-check.enabled=false}: {@code AccountService.register} calls {@code
 * PasswordPolicy.validate} before the account is saved; a live Have I Been Pwned call (which this
 * sandbox cannot reach) fails and its fail-open audit-logging path then references an
 * account_uuid that doesn't exist in the DB yet, violating {@code auth_audit}'s FK and corrupting
 * the whole transaction. That's a real, separate pre-existing defect (see
 * docker-testcontainers-handshake-issue memory) - disabling the live network call here avoids it
 * without masking it, since tests shouldn't depend on a third-party API for setup regardless.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "themistra.auth.password.breach-check.enabled=false")
class MfaPersistenceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private MfaEnrollmentRepository mfaEnrollmentRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test // AC1
    void mfaEnrollmentMapsAllColumnsAndPersistsUnconfirmed() {
        Long accountId = registerAndResolveAccountId("mfa-enroll@example.com");
        byte[] secret = {1, 2, 3, 4};

        MfaEnrollment saved = mfaEnrollmentRepository.save(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, secret, NOW));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConfirmedAt()).isNull();
        assertThat(saved.getLastUsedAt()).isNull();
        assertThat(saved.getSecretEncrypted()).isEqualTo(secret);
    }

    @Test // Phase 11 gap #4: prove every mapped column survives a genuine reload from the DB, not
          // just that the in-memory `save()` return value looks right
    void mfaEnrollmentReloadsAllColumnsFromDb() {
        Long accountId = registerAndResolveAccountId("mfa-reload@example.com");
        byte[] secret = {5, 6, 7, 8};
        Long id = mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, secret, NOW)).getId();
        entityManager.clear();

        MfaEnrollment reloaded = mfaEnrollmentRepository.findById(id).orElseThrow();

        assertThat(reloaded.getAccountId()).isEqualTo(accountId);
        assertThat(reloaded.getType()).isEqualTo(MfaEnrollment.Type.TOTP);
        assertThat(reloaded.getSecretEncrypted()).isEqualTo(secret);
        assertThat(reloaded.getCreatedAt()).isEqualTo(NOW);
    }

    @Test // Phase 11 gap #2: AC5's "queryable" contract - the finder must return the real row,
          // not just correctly report non-existence (already proven by the unique-constraint test)
    void findByAccountIdAndTypeReturnsSavedEnrollment() {
        Long accountId = registerAndResolveAccountId("mfa-find-saved@example.com");
        byte[] secret = {9, 9, 9};
        MfaEnrollment saved = mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, secret, NOW));

        Optional<MfaEnrollment> found = mfaEnrollmentRepository.findByAccountIdAndType(
                accountId, MfaEnrollment.Type.TOTP);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getAccountId()).isEqualTo(accountId);
        assertThat(found.get().getType()).isEqualTo(MfaEnrollment.Type.TOTP);
        assertThat(found.get().getSecretEncrypted()).isEqualTo(secret);
    }

    @Test // AC2
    void confirmPersistsInPlaceViaDirtyChecking() {
        Long accountId = registerAndResolveAccountId("mfa-confirm@example.com");
        MfaEnrollment saved = mfaEnrollmentRepository.save(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));
        Long id = saved.getId();

        MfaEnrollment loaded = mfaEnrollmentRepository.findById(id).orElseThrow();
        loaded.confirm(NOW.plusSeconds(60));
        mfaEnrollmentRepository.save(loaded);

        MfaEnrollment reloaded = mfaEnrollmentRepository.findById(id).orElseThrow();
        assertThat(reloaded.getConfirmedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test // T18 Phase 11 gap 8: confirmIfUnconfirmed's 0-vs-1 semantics, which MfaService.confirm
          // relies on to close the concurrent-double-confirm race (T18 Phase 9 findings 2 & 3)
    void confirmIfUnconfirmedSucceedsOnceThenReturnsZero() {
        Long accountId = registerAndResolveAccountId("mfa-confirm-atomic@example.com");
        MfaEnrollment saved = mfaEnrollmentRepository.save(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));

        int first = mfaEnrollmentRepository.confirmIfUnconfirmed(saved.getId(), NOW.plusSeconds(60));
        int second = mfaEnrollmentRepository.confirmIfUnconfirmed(saved.getId(), NOW.plusSeconds(90));

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
        MfaEnrollment reloaded = mfaEnrollmentRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getConfirmedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    @Test // T18 Phase 11 gap 8: deleteByIdIfUnconfirmed's 0-vs-1 semantics, which
          // MfaService.beginEnroll relies on to close the confirmed-row-deleted-by-a-race finding
          // (T18 Phase 9 finding 5)
    void deleteByIdIfUnconfirmedSucceedsOnceThenReturnsZero() {
        Long accountId = registerAndResolveAccountId("mfa-delete-atomic@example.com");
        MfaEnrollment saved = mfaEnrollmentRepository.save(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));

        int first = mfaEnrollmentRepository.deleteByIdIfUnconfirmed(saved.getId());

        assertThat(first).isEqualTo(1);
        assertThat(mfaEnrollmentRepository.findById(saved.getId())).isEmpty();
    }

    @Test // T18 Phase 11 gap 8: a confirmed row must never be removed by the unconfirmed-only delete
    void deleteByIdIfUnconfirmedReturnsZeroForAConfirmedRow() {
        Long accountId = registerAndResolveAccountId("mfa-delete-confirmed@example.com");
        MfaEnrollment saved = mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));
        mfaEnrollmentRepository.confirmIfUnconfirmed(saved.getId(), NOW.plusSeconds(60));

        int result = mfaEnrollmentRepository.deleteByIdIfUnconfirmed(saved.getId());

        assertThat(result).isEqualTo(0);
        assertThat(mfaEnrollmentRepository.findById(saved.getId())).isPresent();
    }

    @Test // AC5, frozen brief Finding #1's resolution: the DB constraint is the real enforcement
    void secondEnrollmentForSameAccountAndTypeViolatesUniqueConstraint() {
        Long accountId = registerAndResolveAccountId("mfa-duplicate@example.com");
        mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));

        assertThatThrownBy(() -> mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{2}, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test // AC6
    void findAccountIdByUuidReturnsEmptyForUnknownUuid() {
        Optional<Long> accountId = mfaEnrollmentRepository.findAccountIdByUuid(UUID.randomUUID());

        assertThat(accountId).isEmpty();
    }

    @Test // Phase 11 gap #1: the positive path was untested - only "unknown UUID" was covered
    void findAccountIdByUuidResolvesExistingAccount() {
        AccountResponse registered = accountService.register(
                new RegisterAccountRequest("mfa-resolve-uuid@example.com", "correct-horse-battery"));

        Optional<Long> accountId = mfaEnrollmentRepository.findAccountIdByUuid(registered.accountUuid());

        assertThat(accountId).isPresent();
    }

    @Test // AC9: the enum persists as the literal string, not an ordinal
    void enrollmentTypePersistsAsLiteralStringNotOrdinal() {
        Long accountId = registerAndResolveAccountId("mfa-enum@example.com");
        MfaEnrollment saved = mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));

        Object rawType = entityManager.createNativeQuery(
                        "SELECT type FROM mfa_enrollments WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(rawType).isEqualTo("TOTP");
    }

    @Test // Phase 8/9 fix: mandatory-MFA enforcement needs to distinguish confirmed from not
    void findByAccountIdAndTypeAndConfirmedAtIsNotNullDistinguishesConfirmedFromUnconfirmed() {
        Long accountId = registerAndResolveAccountId("mfa-confirmed-finder@example.com");
        MfaEnrollment saved = mfaEnrollmentRepository.save(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));

        assertThat(mfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull(
                accountId, MfaEnrollment.Type.TOTP)).isEmpty();

        MfaEnrollment loaded = mfaEnrollmentRepository.findById(saved.getId()).orElseThrow();
        loaded.confirm(NOW.plusSeconds(60));
        mfaEnrollmentRepository.save(loaded);

        assertThat(mfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull(
                accountId, MfaEnrollment.Type.TOTP)).isPresent();
    }

    @Test // Phase 8/9 fix: MFA disable (R28) removes the enrollment
    void deleteByAccountIdAndTypeRemovesTheEnrollment() {
        Long accountId = registerAndResolveAccountId("mfa-delete@example.com");
        mfaEnrollmentRepository.saveAndFlush(
                MfaEnrollment.create(accountId, MfaEnrollment.Type.TOTP, new byte[]{1}, NOW));

        mfaEnrollmentRepository.deleteByAccountIdAndType(accountId, MfaEnrollment.Type.TOTP);

        assertThat(mfaEnrollmentRepository.findByAccountIdAndType(accountId, MfaEnrollment.Type.TOTP))
                .isEmpty();
    }

    @Test // AC3
    void recoveryCodeMapsAllColumnsAndMultipleRowsPersistPerAccount() {
        Long accountId = registerAndResolveAccountId("recovery-multi@example.com");

        for (int i = 0; i < 10; i++) {
            recoveryCodeRepository.save(RecoveryCode.create(accountId, "%064d".formatted(i), NOW));
        }

        List<RecoveryCode> codes = recoveryCodeRepository.findByAccountId(accountId);
        assertThat(codes).hasSize(10);
        assertThat(codes).allSatisfy(code -> assertThat(code.getUsedAt()).isNull());
    }

    @Test // Phase 11 gap #4: prove every mapped column survives a genuine reload from the DB
    void recoveryCodeReloadsAllColumnsFromDb() {
        Long accountId = registerAndResolveAccountId("recovery-reload@example.com");
        String codeHash = "c".repeat(64);
        Long id = recoveryCodeRepository.saveAndFlush(RecoveryCode.create(accountId, codeHash, NOW)).getId();
        entityManager.clear();

        RecoveryCode reloaded = recoveryCodeRepository.findById(id).orElseThrow();

        assertThat(reloaded.getAccountId()).isEqualTo(accountId);
        assertThat(reloaded.getCodeHash()).isEqualTo(codeHash);
        assertThat(reloaded.getCreatedAt()).isEqualTo(NOW);
        assertThat(reloaded.getUsedAt()).isNull();
    }

    @Test // L6, Phase 11 gap #7: a corrupted mapping that relaxed the column to accept a longer
          // value should fail loudly, not silently persist an invalid hash
    void persistRejectsCodeHashLongerThan64Characters() {
        Long accountId = registerAndResolveAccountId("recovery-toolong@example.com");

        assertThatThrownBy(() -> recoveryCodeRepository.saveAndFlush(
                RecoveryCode.create(accountId, "d".repeat(65), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test // AC7 - the crux of Finding #3's fix: proves the atomic conditional update is real
    void markUsedIsAtomicAndSucceedsOnlyOnce() {
        Long accountId = registerAndResolveAccountId("recovery-markused@example.com");
        RecoveryCode saved = recoveryCodeRepository.save(RecoveryCode.create(accountId, "a".repeat(64), NOW));

        int first = recoveryCodeRepository.markUsed(saved.getId(), NOW.plusSeconds(10));
        int second = recoveryCodeRepository.markUsed(saved.getId(), NOW.plusSeconds(20));

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);
        RecoveryCode reloaded = recoveryCodeRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getUsedAt()).isEqualTo(NOW.plusSeconds(10));
    }

    @Test // AC8
    void findByAccountIdAndUsedAtIsNullExcludesUsedCodes() {
        Long accountId = registerAndResolveAccountId("recovery-unused@example.com");
        RecoveryCode used = recoveryCodeRepository.save(RecoveryCode.create(accountId, "a".repeat(64), NOW));
        recoveryCodeRepository.save(RecoveryCode.create(accountId, "b".repeat(64), NOW));
        recoveryCodeRepository.markUsed(used.getId(), NOW.plusSeconds(10));

        List<RecoveryCode> unused = recoveryCodeRepository.findByAccountIdAndUsedAtIsNull(accountId);

        assertThat(unused).hasSize(1);
        assertThat(unused.getFirst().getCodeHash()).isEqualTo("b".repeat(64));
    }

    @Test // Phase 8/9 fix: R25 verification needs to find a specific submitted code
    void findByAccountIdAndCodeHashFindsTheSpecificCode() {
        Long accountId = registerAndResolveAccountId("recovery-findhash@example.com");
        recoveryCodeRepository.save(RecoveryCode.create(accountId, "a".repeat(64), NOW));
        recoveryCodeRepository.save(RecoveryCode.create(accountId, "b".repeat(64), NOW));

        Optional<RecoveryCode> found = recoveryCodeRepository.findByAccountIdAndCodeHash(accountId, "b".repeat(64));

        assertThat(found).isPresent();
        assertThat(found.get().getCodeHash()).isEqualTo("b".repeat(64));
    }

    @Test // Phase 11 gap #3: prove the finder actually filters on both columns, not just one
    void findByAccountIdAndCodeHashReturnsEmptyForWrongHashOrOtherAccount() {
        Long accountOneId = registerAndResolveAccountId("recovery-isolation-1@example.com");
        Long accountTwoId = registerAndResolveAccountId("recovery-isolation-2@example.com");
        recoveryCodeRepository.save(RecoveryCode.create(accountOneId, "e".repeat(64), NOW));
        recoveryCodeRepository.save(RecoveryCode.create(accountTwoId, "f".repeat(64), NOW));

        assertThat(recoveryCodeRepository.findByAccountIdAndCodeHash(accountOneId, "f".repeat(64)))
                .as("account one's hash lookup must not return account two's code")
                .isEmpty();
        assertThat(recoveryCodeRepository.findByAccountIdAndCodeHash(accountTwoId, "e".repeat(64)))
                .as("account two's hash lookup must not return account one's code")
                .isEmpty();
        assertThat(recoveryCodeRepository.findByAccountIdAndCodeHash(accountOneId, "g".repeat(64)))
                .as("a hash nobody has must not match anything")
                .isEmpty();
    }

    @Test // AC7, Phase 11 gap #6: proves the atomic update is race-safe under real concurrency,
          // not just correct when called sequentially
    void markUsedIsAtomicUnderConcurrentRedemption() throws Exception {
        Long accountId = registerAndResolveAccountId("recovery-concurrent@example.com");
        RecoveryCode saved = recoveryCodeRepository.save(RecoveryCode.create(accountId, "h".repeat(64), NOW));
        Long id = saved.getId();

        java.util.concurrent.CountDownLatch bothReady = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> attempt = () -> {
            bothReady.countDown();
            go.await();
            return recoveryCodeRepository.markUsed(id, Instant.now());
        };

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        int first;
        int second;
        try {
            java.util.concurrent.Future<Integer> a = executor.submit(attempt);
            java.util.concurrent.Future<Integer> b = executor.submit(attempt);
            bothReady.await();
            go.countDown();
            first = a.get();
            second = b.get();
        } finally {
            executor.shutdown();
        }

        assertThat(first + second).isEqualTo(1);
        RecoveryCode reloaded = recoveryCodeRepository.findById(id).orElseThrow();
        assertThat(reloaded.getUsedAt()).isNotNull();
    }

    private Long registerAndResolveAccountId(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, "correct-horse-battery"));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return mfaEnrollmentRepository.findAccountIdByUuid(registered.accountUuid()).orElseThrow();
    }
}
