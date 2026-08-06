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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Begin-enroll, confirm, disable, and recovery-code verification for TOTP MFA (R22/R23/R28/R29,
 * L6). Orchestrates T16's crypto primitives ({@link TotpGenerator}, {@link MfaSeedEncryption},
 * {@link TotpVerifier}) and T17's persistence ({@link MfaEnrollment}/{@link RecoveryCode}). No
 * {@code Account} entity import (L12) — password verification and account-status checks compose
 * only {@link AccountService}'s existing public methods.
 */
@Service
public class MfaService {

    private static final int RECOVERY_CODE_BYTES = 32;
    private static final int RECOVERY_CODE_COUNT = 10;

    private final TotpGenerator totpGenerator;
    private final MfaSeedEncryption mfaSeedEncryption;
    private final TotpVerifier totpVerifier;
    private final MfaEnrollmentRepository mfaEnrollmentRepository;
    private final RecoveryCodeRepository recoveryCodeRepository;
    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public MfaService(TotpGenerator totpGenerator, MfaSeedEncryption mfaSeedEncryption,
                      TotpVerifier totpVerifier, MfaEnrollmentRepository mfaEnrollmentRepository,
                      RecoveryCodeRepository recoveryCodeRepository, AccountService accountService,
                      PasswordEncoder passwordEncoder, AuditService auditService, Clock clock) {
        this.totpGenerator = totpGenerator;
        this.mfaSeedEncryption = mfaSeedEncryption;
        this.totpVerifier = totpVerifier;
        this.mfaEnrollmentRepository = mfaEnrollmentRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.accountService = accountService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Begins TOTP enrollment (R22). A confirmed enrollment already existing rejects outright
     * ({@link MfaAlreadyEnrolledException}); an abandoned unconfirmed one is deleted and replaced
     * so the caller can retry (Phase 4, human-confirmed) — both deletion and the new insert happen
     * in this one transaction. {@code accountLabel} passed to the provisioning URI is the account
     * UUID, never the email (PII avoidance, Phase 4).
     *
     * <p>The unconfirmed-row delete uses {@link MfaEnrollmentRepository#deleteByIdIfUnconfirmed},
     * not a plain delete (T18 Phase 9 finding): a concurrent transaction may confirm this exact
     * row between this method's read and its delete. A {@code 0}-row result means that race
     * occurred, and this call must reject rather than silently replace a now-confirmed
     * enrollment.</p>
     */
    @Transactional
    public BeginEnrollResult beginEnroll(UUID accountUuid) {
        requireActiveAccount(accountUuid, "begin MFA enrollment");
        Long accountId = resolveAccountId(accountUuid);

        Optional<MfaEnrollment> existing =
                mfaEnrollmentRepository.findByAccountIdAndType(accountId, MfaEnrollment.Type.TOTP);
        if (existing.isPresent()) {
            MfaEnrollment enrollment = existing.get();
            if (enrollment.getConfirmedAt() != null
                    || mfaEnrollmentRepository.deleteByIdIfUnconfirmed(enrollment.getId()) == 0) {
                throw new MfaAlreadyEnrolledException();
            }
        }

        byte[] secret = totpGenerator.generateSecret();
        byte[] secretEncrypted = mfaSeedEncryption.encrypt(secret);
        MfaEnrollment enrollment = MfaEnrollment.create(
                accountId, MfaEnrollment.Type.TOTP, secretEncrypted, clock.instant());
        mfaEnrollmentRepository.save(enrollment);

        String provisioningUri = totpGenerator.buildProvisioningUri(secret, accountUuid.toString());
        return new BeginEnrollResult(secret, provisioningUri);
    }

    /**
     * Confirms a pending TOTP enrollment (R23). A wrong code records {@code mfa.failed} (R29) and
     * mutates nothing. A correct code confirms the enrollment and generates 10 single-use recovery
     * codes — their only appearance in plaintext, ever.
     *
     * <p>Confirmation uses {@link MfaEnrollmentRepository#confirmIfUnconfirmed}, an atomic
     * conditional update, rather than the plain {@link MfaEnrollment#confirm} mutator (T18 Phase 9
     * finding): two concurrent calls loading the same unconfirmed row would otherwise both pass
     * verification and both generate/persist a full set of recovery codes. A {@code 0}-row result
     * — whether from a genuine re-confirm attempt or a race lost to a concurrent call — throws
     * {@link MfaAlreadyEnrolledException} before any recovery code is generated.</p>
     */
    @Transactional
    public ConfirmResult confirm(UUID accountUuid, String submittedCode) {
        requireActiveAccount(accountUuid, "confirm MFA enrollment");
        Long accountId = resolveAccountId(accountUuid);

        MfaEnrollment enrollment = mfaEnrollmentRepository
                .findByAccountIdAndType(accountId, MfaEnrollment.Type.TOTP)
                .orElseThrow(MfaNotEnrolledException::new);

        byte[] secret = mfaSeedEncryption.decrypt(enrollment.getSecretEncrypted());
        if (!totpVerifier.verify(secret, submittedCode, clock.instant())) {
            recordAudit("mfa.failed", AuditOutcome.FAILURE, accountUuid);
            throw new InvalidTotpCodeException();
        }

        Instant now = clock.instant();
        if (mfaEnrollmentRepository.confirmIfUnconfirmed(enrollment.getId(), now) == 0) {
            throw new MfaAlreadyEnrolledException();
        }

        List<String> rawCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            String rawCode = generateRawRecoveryCode();
            recoveryCodeRepository.save(RecoveryCode.create(accountId, Hashing.sha256(rawCode), now));
            rawCodes.add(rawCode);
        }
        return new ConfirmResult(List.copyOf(rawCodes));
    }

    /**
     * Disables TOTP MFA (R28): requires both the current password and a valid TOTP code. A wrong
     * password records {@code mfa.disable_failed} (broader than R29's TOTP/recovery-code-specific
     * {@code mfa.failed}, added per {@code agents.md}'s general audit mandate, Phase 4). A wrong
     * code records {@code mfa.failed} (R29). Success removes the enrollment and every recovery
     * code (account-scoped — no enrollment FK on {@link RecoveryCode}, per T17 Phase 4) and records
     * {@code mfa.disabled}.
     */
    @Transactional
    public void disable(UUID accountUuid, String currentPassword, String submittedCode) {
        AccountResponse account = requireActiveAccount(accountUuid, "disable MFA");
        Long accountId = resolveAccountId(accountUuid);

        LoginView loginView = accountService.findLoginView(account.email()).orElse(null);
        if (loginView == null || !passwordEncoder.matches(currentPassword, loginView.passwordHash())) {
            recordAudit("mfa.disable_failed", AuditOutcome.FAILURE, accountUuid);
            throw new MfaCurrentPasswordMismatchException();
        }

        MfaEnrollment enrollment = mfaEnrollmentRepository
                .findByAccountIdAndTypeAndConfirmedAtIsNotNull(accountId, MfaEnrollment.Type.TOTP)
                .orElseThrow(MfaNotEnrolledException::new);

        byte[] secret = mfaSeedEncryption.decrypt(enrollment.getSecretEncrypted());
        if (!totpVerifier.verify(secret, submittedCode, clock.instant())) {
            recordAudit("mfa.failed", AuditOutcome.FAILURE, accountUuid);
            throw new InvalidTotpCodeException();
        }

        mfaEnrollmentRepository.deleteByAccountIdAndType(accountId, MfaEnrollment.Type.TOTP);
        recoveryCodeRepository.deleteByAccountId(accountId);
        recordAudit("mfa.disabled", AuditOutcome.SUCCESS, accountUuid);
    }

    /**
     * Redeems a single-use recovery code (R25/R29), for task 20's login-time use. No
     * account-status precondition here (unlike begin/confirm/disable) — the caller (a future login
     * flow) already establishes account usability before this is ever reached. Returns normally on
     * success; throws on any failure so callers cannot mistake an unchecked boolean for success.
     */
    @Transactional
    public void verifyRecoveryCode(UUID accountUuid, String rawCode) {
        Long accountId = resolveAccountId(accountUuid);
        String codeHash = Hashing.sha256(rawCode);

        RecoveryCode code = recoveryCodeRepository.findByAccountIdAndCodeHash(accountId, codeHash)
                .orElse(null);
        if (code == null || recoveryCodeRepository.markUsed(code.getId(), clock.instant()) == 0) {
            recordAudit("mfa.failed", AuditOutcome.FAILURE, accountUuid);
            throw new InvalidRecoveryCodeException();
        }
    }

    private AccountResponse requireActiveAccount(UUID accountUuid, String action) {
        AccountResponse account = accountService.getByUuid(accountUuid);
        if (account.status() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException(accountUuid, account.status(), action);
        }
        return account;
    }

    private Long resolveAccountId(UUID accountUuid) {
        return mfaEnrollmentRepository.findAccountIdByUuid(accountUuid)
                .orElseThrow(() -> new AccountNotFoundException(accountUuid));
    }

    private String generateRawRecoveryCode() {
        byte[] bytes = new byte[RECOVERY_CODE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void recordAudit(String eventType, AuditOutcome outcome, UUID accountUuid) {
        auditService.record(new RecordAuditEventRequest(
                eventType, outcome, accountUuid, accountUuid, null, null, null, null));
    }

    /**
     * @param secret          the raw TOTP secret; render as a QR code, never log or persist as-is.
     * @param provisioningUri the {@code otpauth://} URI (carries the same secret, Base32-encoded).
     */
    public record BeginEnrollResult(byte[] secret, String provisioningUri) {

        /** Overridden so neither the secret nor the URI can leak via a default record {@code toString()}. */
        @Override
        public String toString() {
            return "BeginEnrollResult[REDACTED]";
        }
    }

    /** @param recoveryCodes the 10 raw codes; returned exactly once, never re-derivable afterward. */
    public record ConfirmResult(List<String> recoveryCodes) {

        /** Overridden so the raw codes can never leak via a default record {@code toString()}. */
        @Override
        public String toString() {
            return "ConfirmResult[REDACTED]";
        }
    }
}
