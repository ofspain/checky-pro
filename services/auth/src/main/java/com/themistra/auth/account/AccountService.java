package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.LoginView;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.event.EmailRequestedEventPayload;
import com.themistra.auth.account.event.UserLifecycleEventPayload;
import com.themistra.auth.audit.AuditOutcome;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.events.OutboxPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Account lifecycle operations. Every transition that matters to other services publishes
 * through the outbox in the same transaction (D-009) — no state change here is ever silent.
 */
@Service
@Validated
public class AccountService {

    private static final String AGGREGATE_TYPE = "account";
    private static final int SCHEMA_VERSION = 1;

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxPublisher outboxPublisher;
    private final AuditService auditService;
    private final VerificationTokenService verificationTokenService;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                          OutboxPublisher outboxPublisher, AuditService auditService,
                          VerificationTokenService verificationTokenService, Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.auditService = auditService;
        this.verificationTokenService = verificationTokenService;
        this.clock = clock;
    }

    /**
     * Registers a new account in PENDING_VERIFICATION. The unique constraint on email is the
     * real duplicate guard — the existsByEmail pre-check only provides the friendlier path;
     * a concurrent insert between check and save still surfaces as {@link DuplicateEmailException}.
     *
     * No event is published here: per target-design §9, {@code auth.user.registered} fires at
     * email-confirmation time ({@link #activateEmail}), not at initial signup. The
     * {@code auth.email.requested} event that would trigger the verification email belongs to
     * the not-yet-built verification-token flow (account module, email-verification stage).
     */
    @Transactional
    public AccountResponse register(@Valid RegisterAccountRequest request) {
        String email = normalize(request.email());

        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Account account = Account.register(email, passwordEncoder.encode(request.password()));
        Account saved;
        try {
            saved = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException();
        }
        issueAndEmitVerificationEmail(saved);
        return AccountResponse.from(saved);
    }

    /**
     * Redeems a verification token and activates the account it belongs to (R4). The status is
     * checked before {@link Account#activateEmail()} is called specifically so that an account in
     * any state other than {@code PENDING_VERIFICATION} (already active, locked, etc.) never
     * reaches that method's own guard — which throws {@link InvalidAccountStateException}, a
     * distinguishing exception mapped to a different HTTP response than R5's uniform rejection
     * would require. Every rejection reason - token not found/expired/used (T05's {@code consume}
     * already guarantees this uniformly) or wrong account status (this method's own check) -
     * surfaces identically as {@link VerificationTokenRejectedException}.
     */
    @Transactional
    public AccountResponse activateFromVerificationToken(String rawToken) {
        UUID accountUuid = verificationTokenService.consume(rawToken)
                .orElseThrow(VerificationTokenRejectedException::new);

        Account account = getAccount(accountUuid);
        if (account.getStatus() != AccountStatus.PENDING_VERIFICATION) {
            throw new VerificationTokenRejectedException();
        }

        account.activateEmail();
        publishLifecycleEvent(account, "user.registered");
        recordAudit("account.activated", account.getAccountUuid(), account.getAccountUuid());
        return AccountResponse.from(account);
    }

    /**
     * Issues a new verification token and emits {@code auth.email.requested} only when
     * {@code email} resolves to a {@code PENDING_VERIFICATION} account (R6, as modified at the
     * Phase 0 human-approval gate: public and email-identified, not authenticated). Silently a
     * no-op otherwise - no state change, no distinguishing signal for the caller to observe
     * (accepted timing/observability trade-off, T06 frozen brief Finding 4).
     */
    @Transactional
    public void resendVerificationIfPending(String email) {
        accountRepository.findByEmail(normalize(email))
                .filter(account -> account.getStatus() == AccountStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueAndEmitVerificationEmail);
    }

    /**
     * Marks the email verified and activates the account.
     *
     * <p>Interim (D-024): the intended self-service flow is a single-use, hashed, TTL'd
     * verification token emailed to the user — that flow is not yet built. Until it is, this is
     * reachable only via an authenticated ADMIN endpoint, which makes it an admin action worth
     * auditing (unlike the self-service activation this will become, {@code actorUuid} is
     * always a real admin here, never null).</p>
     */
    @Transactional
    public AccountResponse activateEmail(UUID accountUuid, UUID actorUuid) {
        Account account = getAccount(accountUuid);
        account.activateEmail();
        publishLifecycleEvent(account, "user.registered");
        recordAudit("account.activated", accountUuid, actorUuid);
        return AccountResponse.from(account);
    }

    /**
     * Suspend/reinstate/delete are audited (target-design §15) as well as published — they are
     * typically admin/compliance-initiated and security-relevant, unlike the routine self-service
     * email-verification step. {@code actorUuid} is the authenticated admin/compliance caller,
     * threaded through from the controller layer; pass {@code null} only for genuinely
     * system-initiated transitions (there are none yet).
     */
    @Transactional
    public AccountResponse suspend(UUID accountUuid, UUID actorUuid) {
        Account account = getAccount(accountUuid);
        account.suspend();
        publishLifecycleEvent(account, "user.suspended");
        recordAudit("account.suspended", accountUuid, actorUuid);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse reinstate(UUID accountUuid, UUID actorUuid) {
        Account account = getAccount(accountUuid);
        account.reinstate();
        publishLifecycleEvent(account, "user.reinstated");
        recordAudit("account.reinstated", accountUuid, actorUuid);
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse delete(UUID accountUuid, UUID actorUuid) {
        Account account = getAccount(accountUuid);
        account.markDeleted();
        publishLifecycleEvent(account, "user.deleted");
        recordAudit("account.deleted", accountUuid, actorUuid);
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByUuid(UUID accountUuid) {
        return AccountResponse.from(getAccount(accountUuid));
    }

    /**
     * Credential lookup for interactive login (authn module). Deleted accounts are
     * indistinguishable from unknown emails — both return empty.
     */
    @Transactional(readOnly = true)
    public Optional<LoginView> findLoginView(String email) {
        return accountRepository.findByEmail(normalize(email))
                .filter(account -> account.getStatus() != AccountStatus.DELETED)
                .map(account -> new LoginView(
                        account.getAccountUuid(), account.getPasswordHash(), account.getStatus()));
    }

    private void issueAndEmitVerificationEmail(Account account) {
        VerificationTokenService.VerificationTokenResult result =
                verificationTokenService.issue(account.getAccountUuid(), VerificationToken.Purpose.EMAIL_VERIFY);
        outboxPublisher.publish(
                "verification-token",
                account.getAccountUuid().toString(),
                "email.requested",
                SCHEMA_VERSION,
                new EmailRequestedEventPayload(
                        account.getAccountUuid(), "verify_email", result.rawToken(), clock.instant()));
    }

    private void recordAudit(String eventType, UUID accountUuid, UUID actorUuid) {
        auditService.record(new RecordAuditEventRequest(
                eventType, AuditOutcome.SUCCESS, accountUuid, actorUuid, null, null, null, null));
    }

    private void publishLifecycleEvent(Account account, String eventType) {
        outboxPublisher.publish(
                AGGREGATE_TYPE,
                account.getAccountUuid().toString(),
                eventType,
                SCHEMA_VERSION,
                new UserLifecycleEventPayload(account.getAccountUuid(), account.getStatus(), clock.instant()));
    }

    private Account getAccount(UUID accountUuid) {
        return accountRepository.findByAccountUuid(accountUuid)
                .orElseThrow(() -> new AccountNotFoundException(accountUuid));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The single, uniform rejection for every reason a verification token redemption can fail
     * (not found, expired, already used, or - this class's own addition - the account is not
     * {@code PENDING_VERIFICATION}). R5's enumeration-safety guarantee depends on there being
     * exactly one exception type here, mapped to exactly one HTTP response.
     */
    public static class VerificationTokenRejectedException extends RuntimeException {

        public VerificationTokenRejectedException() {
            super("Verification token is invalid, expired, or already used");
        }
    }
}
