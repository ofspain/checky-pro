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

    private Long registerAndResolveAccountId(String email) {
        AccountResponse registered = accountService.register(new RegisterAccountRequest(email, "correct-horse-battery"));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return mfaEnrollmentRepository.findAccountIdByUuid(registered.accountUuid()).orElseThrow();
    }
}
