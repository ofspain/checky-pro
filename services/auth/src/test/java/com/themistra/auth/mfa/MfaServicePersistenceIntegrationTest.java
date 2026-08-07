package com.themistra.auth.mfa;

import com.themistra.auth.TestcontainersConfiguration;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.InvalidAccountStateException;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end against real Postgres (Testcontainers) — proves the full {@code beginEnroll} /
 * {@code confirm} / {@code disable} chain against the real schema and a real {@link
 * AccountService}, complementing {@link MfaServiceTest}'s mocked-repository unit coverage.
 * Mirrors {@link MfaPersistenceIntegrationTest}'s structure and {@code breach-check.enabled=false}
 * workaround for the same pre-existing, out-of-scope defect.
 *
 * <p><strong>Known status (T18 Phase 5/10, inherited from T17):</strong> this suite combines
 * {@code Account} and {@code mfa} entities in one persistence unit — exactly the combination
 * {@link MfaPersistenceIntegrationTest} is already blocked on by an undiagnosed Hibernate
 * {@code existsByEmail} byte-array conversion defect (see the
 * {@code docker-testcontainers-handshake-issue} follow-up). This class is written and intended to
 * pass once that defect is fixed; it is not expected to run green today, and that is a
 * pre-existing, explicitly out-of-scope condition, not a defect introduced by this task.</p>
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "themistra.auth.password.breach-check.enabled=false")
class MfaServicePersistenceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private AccountService accountService;

    @Autowired
    private MfaService mfaService;

    @Autowired
    private MfaEnrollmentRepository mfaEnrollmentRepository;

    @Autowired
    private RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private AuditService auditService;

    @Test // AC1/AC2/AC7 — full happy path: begin, confirm with an independently-computed TOTP
          // code, exactly 10 recovery codes returned and persisted with matching hashes
    void beginEnrollThenConfirmPersistsConfirmedEnrollmentAndTenRecoveryCodes() {
        UUID accountUuid = registerAndActivate("mfa-e2e-confirm@example.com");

        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        MfaService.ConfirmResult confirmed = mfaService.confirm(accountUuid, code);

        assertThat(confirmed.recoveryCodes()).hasSize(10);
        assertThat(confirmed.recoveryCodes()).doesNotHaveDuplicates();

        Long accountId = mfaEnrollmentRepository.findAccountIdByUuid(accountUuid).orElseThrow();
        MfaEnrollment enrollment = mfaEnrollmentRepository
                .findByAccountIdAndTypeAndConfirmedAtIsNotNull(accountId, MfaEnrollment.Type.TOTP)
                .orElseThrow();
        assertThat(enrollment.getConfirmedAt()).isNotNull();
        assertThat(recoveryCodeRepository.findByAccountId(accountId)).hasSize(10);
    }

    @Test // R28 — full disable happy path: enrollment and all recovery codes removed, mfa.disabled audited
    void disableRemovesEnrollmentAndAllRecoveryCodesAndAudits() {
        UUID accountUuid = registerAndActivate("mfa-e2e-disable@example.com");
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        mfaService.confirm(accountUuid, referenceGenerateCode(begun.secret(), Instant.now()));
        Long accountId = mfaEnrollmentRepository.findAccountIdByUuid(accountUuid).orElseThrow();

        String disableCode = referenceGenerateCode(begun.secret(), Instant.now());
        mfaService.disable(accountUuid, "correct-horse-battery", disableCode);

        assertThat(mfaEnrollmentRepository.findByAccountIdAndType(accountId, MfaEnrollment.Type.TOTP)).isEmpty();
        assertThat(recoveryCodeRepository.findByAccountId(accountId)).isEmpty();
        assertThat(auditService.list(accountUuid, PageRequest.of(0, 50)).getContent())
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("mfa.disabled"));
    }

    @Test // R22/precondition — a non-ACTIVE account (still PENDING_VERIFICATION) is rejected
    void beginEnrollRejectsAccountThatIsNotYetActive() {
        AccountResponse registered = accountService.register(
                new RegisterAccountRequest("mfa-e2e-pending@example.com", "correct-horse-battery"));

        assertThatThrownBy(() -> mfaService.beginEnroll(registered.accountUuid()))
                .isInstanceOf(InvalidAccountStateException.class);
    }

    @Test // T18 Phase 9 finding 5's fix — beginEnroll's retry deletes an abandoned unconfirmed
          // enrollment and replaces it with a genuinely new one (new secret, new row)
    void beginEnrollRetryReplacesAbandonedUnconfirmedEnrollmentWithANewSecret() {
        UUID accountUuid = registerAndActivate("mfa-e2e-retry@example.com");
        MfaService.BeginEnrollResult first = mfaService.beginEnroll(accountUuid);

        MfaService.BeginEnrollResult second = mfaService.beginEnroll(accountUuid);

        assertThat(second.secret()).isNotEqualTo(first.secret());
        // the old secret's code must no longer confirm the (now-replaced) enrollment
        String oldCode = referenceGenerateCode(first.secret(), Instant.now());
        assertThatThrownBy(() -> mfaService.confirm(accountUuid, oldCode))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test // T18 Phase 11 gap 1: proves the T18 Phase 9 AuditService.record REQUIRES_NEW fix
          // actually works — a failure audit written moments before confirm's own exception must
          // still be visible afterward, not rolled back along with the enclosing transaction
    void confirmRecordsFailureAuditThatSurvivesTheRollback() {
        UUID accountUuid = registerAndActivate("mfa-e2e-audit-survives@example.com");
        mfaService.beginEnroll(accountUuid);

        assertThatThrownBy(() -> mfaService.confirm(accountUuid, "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);

        assertThat(auditService.list(accountUuid, PageRequest.of(0, 50)).getContent())
                .as("the mfa.failed audit must persist even though confirm's own transaction rolled back")
                .anySatisfy(event -> assertThat(event.eventType()).isEqualTo("mfa.failed"));
    }

    @Test // T18 Phase 11 gap 2: proves the T18 Phase 9 confirmIfUnconfirmed atomic-update fix
          // actually closes the concurrent-double-confirm race under real concurrent transactions,
          // not just as an interpreted mock contract (mirrors MfaPersistenceIntegrationTest's own
          // markUsedIsAtomicUnderConcurrentRedemption for RecoveryCodeRepository.markUsed)
    void concurrentConfirmCallsResultInExactlyOneSuccessAndTenRecoveryCodes() throws Exception {
        UUID accountUuid = registerAndActivate("mfa-e2e-concurrent-confirm@example.com");
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());

        java.util.concurrent.CountDownLatch bothReady = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> attempt = () -> {
            bothReady.countDown();
            go.await();
            try {
                mfaService.confirm(accountUuid, code);
                return true;
            } catch (MfaAlreadyEnrolledException e) {
                return false;
            }
        };

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        boolean first;
        boolean second;
        try {
            java.util.concurrent.Future<Boolean> a = executor.submit(attempt);
            java.util.concurrent.Future<Boolean> b = executor.submit(attempt);
            bothReady.await();
            go.countDown();
            first = a.get();
            second = b.get();
        } finally {
            executor.shutdown();
        }

        assertThat(first ^ second).as("exactly one of the two concurrent confirms must succeed").isTrue();
        Long accountId = mfaEnrollmentRepository.findAccountIdByUuid(accountUuid).orElseThrow();
        assertThat(recoveryCodeRepository.findByAccountId(accountId)).hasSize(10);
    }

    @Test // T20 — R25/R29, full-stack against a real DB: verifyTotpCodeForLogin accepts a correct
          // code and rejects the same code replayed immediately after
    void verifyTotpCodeForLoginAcceptsOnceThenRejectsAnImmediateReplay() {
        UUID accountUuid = registerAndActivate("mfa-e2e-login-verify@example.com");
        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        String code = referenceGenerateCode(begun.secret(), Instant.now());
        mfaService.confirm(accountUuid, code);

        String loginCode = referenceGenerateCode(begun.secret(), Instant.now());
        assertThatCode(() -> mfaService.verifyTotpCodeForLogin(accountUuid, loginCode))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> mfaService.verifyTotpCodeForLogin(accountUuid, loginCode))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    @Test // T20 — R24: hasConfirmedTotpEnrollment against a real DB, both states
    void hasConfirmedTotpEnrollmentReflectsRealPersistedState() {
        UUID accountUuid = registerAndActivate("mfa-e2e-has-enrollment@example.com");

        assertThat(mfaService.hasConfirmedTotpEnrollment(accountUuid)).isFalse();

        MfaService.BeginEnrollResult begun = mfaService.beginEnroll(accountUuid);
        assertThat(mfaService.hasConfirmedTotpEnrollment(accountUuid))
                .as("an unconfirmed enrollment must not count")
                .isFalse();

        mfaService.confirm(accountUuid, referenceGenerateCode(begun.secret(), Instant.now()));
        assertThat(mfaService.hasConfirmedTotpEnrollment(accountUuid)).isTrue();
    }

    private UUID registerAndActivate(String email) {
        AccountResponse registered = accountService.register(
                new RegisterAccountRequest(email, "correct-horse-battery"));
        accountService.activateEmail(registered.accountUuid(), registered.accountUuid());
        return registered.accountUuid();
    }

    /** Independent RFC 4226/6238 HOTP/TOTP implementation, deliberately separate code from {@link
     * TotpVerifier} — see {@code TotpVerifierTest} for the same discipline applied at unit level. */
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
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
