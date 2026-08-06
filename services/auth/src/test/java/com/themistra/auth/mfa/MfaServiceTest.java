package com.themistra.auth.mfa;

import com.themistra.auth.account.AccountNotFoundException;
import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.InvalidAccountStateException;
import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.LoginView;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.common.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MfaService} — R22/R23/R28/R29, L6. Plain JUnit + Mockito, no Spring
 * context, fixed {@link Clock}, mirroring {@code LockoutServiceTest}/{@code
 * VerificationTokenServiceTest}'s established shape. Every repository/service dependency is
 * mocked; {@link TotpVerifier}'s own correctness is {@code TotpVerifierTest}'s responsibility —
 * these tests verify {@link MfaService}'s orchestration and failure-handling around it.
 */
@ExtendWith(MockitoExtension.class)
class MfaServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final Long ACCOUNT_ID = 42L;
    private static final String EMAIL = "user@example.com";

    @Mock
    private TotpGenerator totpGenerator;

    @Mock
    private MfaSeedEncryption mfaSeedEncryption;

    @Mock
    private TotpVerifier totpVerifier;

    @Mock
    private MfaEnrollmentRepository mfaEnrollmentRepository;

    @Mock
    private RecoveryCodeRepository recoveryCodeRepository;

    @Mock
    private AccountService accountService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    private MfaService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new MfaService(totpGenerator, mfaSeedEncryption, totpVerifier,
                mfaEnrollmentRepository, recoveryCodeRepository, accountService,
                passwordEncoder, auditService, fixedClock);
    }

    // ---- beginEnroll (R22) ----

    @Test
    void beginEnrollGeneratesEncryptsAndPersistsWhenNoEnrollmentExists() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.empty());
        byte[] secret = {1, 2, 3};
        byte[] encrypted = {9, 9, 9};
        when(totpGenerator.generateSecret()).thenReturn(secret);
        when(mfaSeedEncryption.encrypt(secret)).thenReturn(encrypted);
        when(totpGenerator.buildProvisioningUri(secret, ACCOUNT_UUID.toString()))
                .thenReturn("otpauth://totp/Themistra:" + ACCOUNT_UUID);

        MfaService.BeginEnrollResult result = service.beginEnroll(ACCOUNT_UUID);

        assertThat(result.secret()).isEqualTo(secret);
        assertThat(result.provisioningUri()).isEqualTo("otpauth://totp/Themistra:" + ACCOUNT_UUID);
        ArgumentCaptor<MfaEnrollment> captor = ArgumentCaptor.forClass(MfaEnrollment.class);
        verify(mfaEnrollmentRepository).save(captor.capture());
        assertThat(captor.getValue().getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(captor.getValue().getSecretEncrypted()).isEqualTo(encrypted);
        assertThat(captor.getValue().getConfirmedAt()).isNull();
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(NOW); // Phase 11 gap 4: Clock, not Instant.now()
        verify(mfaEnrollmentRepository, never()).deleteByIdIfUnconfirmed(any());
    }

    @Test // Phase 4 — PII avoidance: the provisioning label is the account UUID, never the email
    void beginEnrollUsesAccountUuidNotEmailAsProvisioningLabel() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.empty());
        byte[] secret = {1};
        when(totpGenerator.generateSecret()).thenReturn(secret);
        when(mfaSeedEncryption.encrypt(secret)).thenReturn(secret);

        service.beginEnroll(ACCOUNT_UUID);

        verify(totpGenerator).buildProvisioningUri(secret, ACCOUNT_UUID.toString());
        verify(totpGenerator, never()).buildProvisioningUri(any(), eq(EMAIL));
    }

    @Test // AC1 — a confirmed enrollment blocks re-enrollment outright
    void beginEnrollRejectsWhenConfirmedEnrollmentExists() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(confirmedEnrollment()));

        assertThatThrownBy(() -> service.beginEnroll(ACCOUNT_UUID))
                .isInstanceOf(MfaAlreadyEnrolledException.class);
        verify(mfaEnrollmentRepository, never()).deleteByIdIfUnconfirmed(any());
        verify(mfaEnrollmentRepository, never()).save(any());
    }

    @Test // Phase 4, human-confirmed — an abandoned unconfirmed enrollment is deleted and replaced
    void beginEnrollDeletesAbandonedUnconfirmedEnrollmentAndCreatesANewOne() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(unconfirmedEnrollment(7L)));
        when(mfaEnrollmentRepository.deleteByIdIfUnconfirmed(7L)).thenReturn(1);
        byte[] secret = {4, 5};
        when(totpGenerator.generateSecret()).thenReturn(secret);
        when(mfaSeedEncryption.encrypt(secret)).thenReturn(secret);

        MfaService.BeginEnrollResult result = service.beginEnroll(ACCOUNT_UUID);

        assertThat(result.secret()).isEqualTo(secret);
        verify(mfaEnrollmentRepository).deleteByIdIfUnconfirmed(7L);
        verify(mfaEnrollmentRepository).save(any());
    }

    @Test // T18 Phase 9 finding 5 — a concurrent transaction confirmed the row between this call's
          // read and its delete; the atomic delete reports 0 rows and the retry must be rejected,
          // not silently replace a now-confirmed enrollment
    void beginEnrollRejectsWhenUnconfirmedRowWasConcurrentlyConfirmedBeforeDelete() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(unconfirmedEnrollment(7L)));
        when(mfaEnrollmentRepository.deleteByIdIfUnconfirmed(7L)).thenReturn(0);

        assertThatThrownBy(() -> service.beginEnroll(ACCOUNT_UUID))
                .isInstanceOf(MfaAlreadyEnrolledException.class);
        verify(mfaEnrollmentRepository, never()).save(any());
    }

    @Test
    void beginEnrollRejectsNonActiveAccount() {
        stubAccountWithStatus(AccountStatus.LOCKED);

        assertThatThrownBy(() -> service.beginEnroll(ACCOUNT_UUID))
                .isInstanceOf(InvalidAccountStateException.class);
        verifyNoInteractions(mfaEnrollmentRepository, totpGenerator, mfaSeedEncryption);
    }

    @Test
    void beginEnrollResultToStringNeverLeaksSecretOrUri() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.empty());
        byte[] secret = {1, 2, 3};
        when(totpGenerator.generateSecret()).thenReturn(secret);
        when(mfaSeedEncryption.encrypt(secret)).thenReturn(secret);
        when(totpGenerator.buildProvisioningUri(secret, ACCOUNT_UUID.toString()))
                .thenReturn("otpauth://secret-leak-check");

        MfaService.BeginEnrollResult result = service.beginEnroll(ACCOUNT_UUID);

        assertThat(result.toString()).isEqualTo("BeginEnrollResult[REDACTED]");
        assertThat(result.toString()).doesNotContain("otpauth://secret-leak-check");
    }

    // ---- confirm (R23) ----

    @Test
    void confirmRecordsAuditAndThrowsOnWrongCodeWithoutMutating() {
        stubActiveAccount();
        MfaEnrollment unconfirmed = unconfirmedEnrollment(3L);
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(unconfirmed));
        byte[] secret = {1};
        when(mfaSeedEncryption.decrypt(unconfirmed.getSecretEncrypted())).thenReturn(secret);
        when(totpVerifier.verify(secret, "000000", NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(ACCOUNT_UUID, "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("mfa.failed");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(captor.getValue().accountUuid()).isEqualTo(ACCOUNT_UUID);
        assertThat(captor.getValue().actorUuid()).isEqualTo(ACCOUNT_UUID);
        verify(mfaEnrollmentRepository, never()).confirmIfUnconfirmed(any(), any());
        verify(recoveryCodeRepository, never()).save(any());
    }

    @Test
    void confirmThrowsWhenNoEnrollmentExists() {
        stubActiveAccount();
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(ACCOUNT_UUID, "123456"))
                .isInstanceOf(MfaNotEnrolledException.class);
        verifyNoInteractions(mfaSeedEncryption, totpVerifier, auditService);
    }

    @Test
    void confirmGeneratesTenSingleUseRecoveryCodesOnSuccess() {
        stubActiveAccount();
        MfaEnrollment unconfirmed = unconfirmedEnrollment(3L);
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(unconfirmed));
        byte[] secret = {1};
        when(mfaSeedEncryption.decrypt(unconfirmed.getSecretEncrypted())).thenReturn(secret);
        when(totpVerifier.verify(secret, "123456", NOW)).thenReturn(true);
        when(mfaEnrollmentRepository.confirmIfUnconfirmed(3L, NOW)).thenReturn(1);

        MfaService.ConfirmResult result = service.confirm(ACCOUNT_UUID, "123456");

        assertThat(result.recoveryCodes()).hasSize(10);
        assertThat(result.recoveryCodes()).doesNotHaveDuplicates();
        // Phase 11 gap 3: format/entropy, not just count/uniqueness/hash-matching
        result.recoveryCodes().forEach(code -> {
            assertThat(code).hasSize(43).matches("^[A-Za-z0-9_-]{43}$");
            assertThatCode(() -> java.util.Base64.getUrlDecoder().decode(code)).doesNotThrowAnyException();
            assertThat(java.util.Base64.getUrlDecoder().decode(code)).hasSize(32);
        });
        ArgumentCaptor<RecoveryCode> captor = ArgumentCaptor.forClass(RecoveryCode.class);
        verify(recoveryCodeRepository, times(10)).save(captor.capture());
        for (int i = 0; i < 10; i++) {
            assertThat(captor.getAllValues().get(i).getAccountId()).isEqualTo(ACCOUNT_ID);
            assertThat(captor.getAllValues().get(i).getCodeHash())
                    .isEqualTo(Hashing.sha256(result.recoveryCodes().get(i)));
            assertThat(captor.getAllValues().get(i).getCreatedAt()).isEqualTo(NOW); // Phase 11 gap 4
        }
        verify(mfaEnrollmentRepository).confirmIfUnconfirmed(3L, NOW); // Phase 11 gap 4
        verify(auditService, never()).record(any());
    }

    @Test // T18 Phase 9 findings 2 & 3 — a 0-row atomic-update result (already confirmed, whether
          // via a genuine re-confirm attempt or a race lost to a concurrent call) throws before any
          // recovery code is generated, instead of reaching MfaEnrollment.confirm's own guard
    void confirmThrowsAlreadyEnrolledWhenAtomicUpdateAffectsNoRows() {
        stubActiveAccount();
        MfaEnrollment enrollment = unconfirmedEnrollment(3L);
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(enrollment));
        byte[] secret = {1};
        when(mfaSeedEncryption.decrypt(enrollment.getSecretEncrypted())).thenReturn(secret);
        when(totpVerifier.verify(secret, "123456", NOW)).thenReturn(true);
        when(mfaEnrollmentRepository.confirmIfUnconfirmed(3L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(ACCOUNT_UUID, "123456"))
                .isInstanceOf(MfaAlreadyEnrolledException.class);
        verify(recoveryCodeRepository, never()).save(any());
    }

    @Test
    void confirmRejectsNonActiveAccount() {
        stubAccountWithStatus(AccountStatus.SUSPENDED);

        assertThatThrownBy(() -> service.confirm(ACCOUNT_UUID, "123456"))
                .isInstanceOf(InvalidAccountStateException.class);
        verifyNoInteractions(mfaEnrollmentRepository, mfaSeedEncryption, totpVerifier);
    }

    @Test
    void confirmResultToStringNeverLeaksRecoveryCodes() {
        stubActiveAccount();
        MfaEnrollment unconfirmed = unconfirmedEnrollment(3L);
        when(mfaEnrollmentRepository.findByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(unconfirmed));
        byte[] secret = {1};
        when(mfaSeedEncryption.decrypt(unconfirmed.getSecretEncrypted())).thenReturn(secret);
        when(totpVerifier.verify(secret, "123456", NOW)).thenReturn(true);
        when(mfaEnrollmentRepository.confirmIfUnconfirmed(3L, NOW)).thenReturn(1);

        MfaService.ConfirmResult result = service.confirm(ACCOUNT_UUID, "123456");

        assertThat(result.toString()).isEqualTo("ConfirmResult[REDACTED]");
        result.recoveryCodes().forEach(code -> assertThat(result.toString()).doesNotContain(code));
    }

    // ---- disable (R28) ----

    @Test
    void disableRecordsDisableFailedAuditAndThrowsOnWrongPassword() {
        stubActiveAccount();
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.disable(ACCOUNT_UUID, "wrong", "123456"))
                .isInstanceOf(MfaCurrentPasswordMismatchException.class);

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("mfa.disable_failed");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.FAILURE);
        verify(mfaEnrollmentRepository, never()).deleteByAccountIdAndType(any(), any());
        verify(recoveryCodeRepository, never()).deleteByAccountId(any());
    }

    @Test // Phase 3 finding 6 — a null login view (e.g. account deleted between the two calls)
          // folds into the same mismatch path, no distinguishing exception
    void disableTreatsMissingLoginViewAsPasswordMismatch() {
        stubActiveAccount();
        when(accountService.findLoginView(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(ACCOUNT_UUID, "whatever", "123456"))
                .isInstanceOf(MfaCurrentPasswordMismatchException.class);
    }

    @Test
    void disableThrowsWhenNoConfirmedEnrollmentExists() {
        stubActiveAccount();
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        when(mfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disable(ACCOUNT_UUID, "correct", "123456"))
                .isInstanceOf(MfaNotEnrolledException.class);
    }

    @Test
    void disableRecordsMfaFailedAndThrowsOnWrongCode() {
        stubActiveAccount();
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        MfaEnrollment confirmed = confirmedEnrollment();
        when(mfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(confirmed));
        byte[] secret = {2};
        when(mfaSeedEncryption.decrypt(confirmed.getSecretEncrypted())).thenReturn(secret);
        when(totpVerifier.verify(secret, "000000", NOW)).thenReturn(false);

        assertThatThrownBy(() -> service.disable(ACCOUNT_UUID, "correct", "000000"))
                .isInstanceOf(InvalidTotpCodeException.class);

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("mfa.failed");
        verify(mfaEnrollmentRepository, never()).deleteByAccountIdAndType(any(), any());
        verify(recoveryCodeRepository, never()).deleteByAccountId(any());
    }

    @Test
    void disableDeletesEnrollmentAndRecoveryCodesAndRecordsSuccessAudit() {
        stubActiveAccount();
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, "hash", AccountStatus.ACTIVE)));
        when(passwordEncoder.matches("correct", "hash")).thenReturn(true);
        MfaEnrollment confirmed = confirmedEnrollment();
        when(mfaEnrollmentRepository.findByAccountIdAndTypeAndConfirmedAtIsNotNull(ACCOUNT_ID, MfaEnrollment.Type.TOTP))
                .thenReturn(Optional.of(confirmed));
        byte[] secret = {2};
        when(mfaSeedEncryption.decrypt(confirmed.getSecretEncrypted())).thenReturn(secret);
        when(totpVerifier.verify(secret, "123456", NOW)).thenReturn(true);

        service.disable(ACCOUNT_UUID, "correct", "123456");

        verify(mfaEnrollmentRepository).deleteByAccountIdAndType(ACCOUNT_ID, MfaEnrollment.Type.TOTP);
        verify(recoveryCodeRepository).deleteByAccountId(ACCOUNT_ID);
        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("mfa.disabled");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void disableRejectsNonActiveAccount() {
        stubAccountWithStatus(AccountStatus.PENDING_VERIFICATION);

        assertThatThrownBy(() -> service.disable(ACCOUNT_UUID, "x", "123456"))
                .isInstanceOf(InvalidAccountStateException.class);
        verifyNoInteractions(passwordEncoder, mfaEnrollmentRepository, totpVerifier);
    }

    // ---- verifyRecoveryCode (R25/R29) ----

    @Test
    void verifyRecoveryCodeThrowsWhenCodeUnknown() {
        when(mfaEnrollmentRepository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.of(ACCOUNT_ID));
        String rawCode = "unknown-code";
        when(recoveryCodeRepository.findByAccountIdAndCodeHash(ACCOUNT_ID, Hashing.sha256(rawCode)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyRecoveryCode(ACCOUNT_UUID, rawCode))
                .isInstanceOf(InvalidRecoveryCodeException.class);

        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("mfa.failed");
        verify(recoveryCodeRepository, never()).markUsed(any(), any());
    }

    @Test
    void verifyRecoveryCodeThrowsWhenAlreadyUsed() {
        when(mfaEnrollmentRepository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.of(ACCOUNT_ID));
        String rawCode = "already-used-code";
        String hash = Hashing.sha256(rawCode);
        RecoveryCode code = withId(RecoveryCode.create(ACCOUNT_ID, hash, NOW), 5L);
        when(recoveryCodeRepository.findByAccountIdAndCodeHash(ACCOUNT_ID, hash)).thenReturn(Optional.of(code));
        when(recoveryCodeRepository.markUsed(5L, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.verifyRecoveryCode(ACCOUNT_UUID, rawCode))
                .isInstanceOf(InvalidRecoveryCodeException.class);

        // Phase 11 gap 5: R29 requires mfa.failed for an already-used code too, not just unknown
        ArgumentCaptor<RecordAuditEventRequest> captor = ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("mfa.failed");
        assertThat(captor.getValue().outcome()).isEqualTo(AuditOutcome.FAILURE);
    }

    @Test
    void verifyRecoveryCodeSucceedsAndReturnsNormally() {
        when(mfaEnrollmentRepository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.of(ACCOUNT_ID));
        String rawCode = "good-code";
        String hash = Hashing.sha256(rawCode);
        RecoveryCode code = withId(RecoveryCode.create(ACCOUNT_ID, hash, NOW), 5L);
        when(recoveryCodeRepository.findByAccountIdAndCodeHash(ACCOUNT_ID, hash)).thenReturn(Optional.of(code));
        when(recoveryCodeRepository.markUsed(5L, NOW)).thenReturn(1);

        assertThatCode(() -> service.verifyRecoveryCode(ACCOUNT_UUID, rawCode)).doesNotThrowAnyException();
        verify(auditService, never()).record(any());
    }

    @Test
    void verifyRecoveryCodeThrowsAccountNotFoundForUnknownUuid() {
        when(mfaEnrollmentRepository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyRecoveryCode(ACCOUNT_UUID, "some-code"))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(recoveryCodeRepository);
    }

    private void stubActiveAccount() {
        stubAccountWithStatus(AccountStatus.ACTIVE);
        when(mfaEnrollmentRepository.findAccountIdByUuid(ACCOUNT_UUID)).thenReturn(Optional.of(ACCOUNT_ID));
    }

    private void stubAccountWithStatus(AccountStatus status) {
        when(accountService.getByUuid(ACCOUNT_UUID))
                .thenReturn(new AccountResponse(ACCOUNT_UUID, EMAIL, true, status, NOW));
    }

    private static MfaEnrollment unconfirmedEnrollment(long id) {
        return withId(MfaEnrollment.create(ACCOUNT_ID, MfaEnrollment.Type.TOTP, new byte[]{1, 2}, NOW), id);
    }

    private static MfaEnrollment confirmedEnrollment() {
        MfaEnrollment enrollment = unconfirmedEnrollment(1L);
        enrollment.confirm(NOW);
        return enrollment;
    }

    /** Both {@link MfaEnrollment} and {@link RecoveryCode} only assign {@code id} via
     * {@code @GeneratedValue} on a real persist — reflection is used here, test-only, purely to
     * build fixtures representing rows already read back from the database (same technique as
     * {@code VerificationTokenServiceTest.setUsedAt}). */
    private static <T> T withId(T entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
