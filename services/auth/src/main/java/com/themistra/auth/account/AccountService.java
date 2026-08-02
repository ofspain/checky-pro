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
import com.themistra.auth.token.RefreshTokenTracker;
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
    private final RefreshTokenTracker refreshTokenTracker;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
                          OutboxPublisher outboxPublisher, AuditService auditService,
                          VerificationTokenService verificationTokenService,
                          RefreshTokenTracker refreshTokenTracker, PasswordPolicy passwordPolicy,
                          Clock clock) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.outboxPublisher = outboxPublisher;
        this.auditService = auditService;
        this.verificationTokenService = verificationTokenService;
        this.refreshTokenTracker = refreshTokenTracker;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
    }

    /**
     * Registers a new account in PENDING_VERIFICATION. The unique constraint on email is the
     * real duplicate guard — the existsByEmail pre-check only provides the friendlier path;
     * a concurrent insert between check and save still surfaces as {@link DuplicateEmailException}.
     *
     * <p>On success, issues a verification token and emits {@code auth.email.requested} (purpose
     * {@code verify_email}) in this same transaction (R3) via {@link #issueAndEmitVerificationEmail}.
     * {@code auth.user.registered} still only fires at email-confirmation time — either
     * self-service ({@link #activateFromVerificationToken}) or the admin stand-in
     * ({@link #activateEmail}) — never at initial signup.</p>
     *
     * <p>{@code passwordPolicy.validate} (T09, R8-R10) runs before the {@code existsByEmail}
     * check, not after — required for enumeration safety (L5). If the duplicate check ran first,
     * a policy-violating password would return the uniform {@code 202} for an already-registered
     * email but a distinguishing {@code 400} for a new one, letting a caller infer whether an
     * email is registered from nothing but the content of the password they submit. Running the
     * policy check first means it fails identically regardless of email existence. The account
     * (and its UUID) is constructed before this check specifically so validation has a real,
     * persistable UUID to record against — one BCrypt encode is spent even on a
     * later-duplicate-rejected registration as a result; a deliberate, human-approved trade-off
     * (Phase 4) over widening {@link Account#register}'s signature for this alone.</p>
     */
    @Transactional
    public AccountResponse register(@Valid RegisterAccountRequest request) {
        String email = normalize(request.email());

        Account account = Account.register(email, passwordEncoder.encode(request.password()));
        passwordPolicy.validate(request.password(), account.getAccountUuid(), account.getAccountUuid());

        if (accountRepository.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }

        Account saved;
        try {
            saved = accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEmailException();
        }
        issueAndEmitVerificationEmail(saved, VerificationToken.Purpose.EMAIL_VERIFY, "verify_email");
        return AccountResponse.from(saved);
    }

    /**
     * Redeems a verification token and activates the account it belongs to (R4). The status is
     * checked before {@link Account#activateEmail()} is called specifically so that an account in
     * any state other than {@code PENDING_VERIFICATION} (already active, locked, etc.) never
     * reaches that method's own guard — which throws {@link InvalidAccountStateException}, a
     * distinguishing exception mapped to a different HTTP response than R5's uniform rejection
     * would require. Uses {@code consumeForPurpose(rawToken, EMAIL_VERIFY)} (T07), not the
     * purpose-blind {@code consume} — a {@code PASSWORD_RESET} token must never activate an
     * account. Every rejection reason - token not found/expired/used/wrong-purpose (T05/T07's
     * {@code consumeForPurpose} already guarantees this uniformly), the resolved account no longer existing (defensive; not
     * reachable today given {@code verification_tokens}'s cascading FK), or wrong account status
     * (this method's own check) - surfaces identically as {@link VerificationTokenRejectedException}.
     */
    @Transactional
    public AccountResponse activateFromVerificationToken(String rawToken) {
        UUID accountUuid = verificationTokenService
                .consumeForPurpose(rawToken, VerificationToken.Purpose.EMAIL_VERIFY)
                .orElseThrow(VerificationTokenRejectedException::new);

        // findByAccountUuid (not the shared getAccount helper) deliberately: a missing account
        // here must fall into the same uniform rejection as every other reason, not
        // AccountNotFoundException's distinguishing 404 (Phase 8/11 finding). Unreachable today
        // given verification_tokens.account_id's ON DELETE CASCADE, but this method's R5 contract
        // shouldn't depend on that constraint never changing.
        Account account = accountRepository.findByAccountUuid(accountUuid)
                .orElseThrow(VerificationTokenRejectedException::new);
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
                .ifPresent(account -> issueAndEmitVerificationEmail(
                        account, VerificationToken.Purpose.EMAIL_VERIFY, "verify_email"));
    }

    /**
     * Issues a password-reset token and emits {@code auth.email.requested} only when
     * {@code email} resolves to an {@code ACTIVE} or {@code LOCKED} account (R13) — deliberately
     * the opposite filter from {@link #resendVerificationIfPending}'s {@code PENDING_VERIFICATION}
     * -only check. {@code LOCKED} is included on purpose (R13, human-confirmed at Phase 3/4): email
     * possession is at least as strong an identity proof as a successful login, which already
     * clears lockout (R18), so a locked-out user needs this as a working self-service recovery
     * path. Silently a no-op otherwise - no state change, no distinguishing signal (same accepted
     * trade-off as T06 Finding 4).
     */
    @Transactional
    public void requestPasswordReset(String email) {
        accountRepository.findByEmail(normalize(email))
                .filter(this::isPasswordResetEligible)
                .ifPresent(account -> issueAndEmitVerificationEmail(
                        account, VerificationToken.Purpose.PASSWORD_RESET, "password_reset"));
    }

    /**
     * Redeems a password-reset token and updates the account's password (R14). Mirrors
     * {@link #activateFromVerificationToken}'s shape: purpose-checked consume, a fresh account
     * read, then a status pre-check *before* any mutation so no distinguishing exception can
     * leak (R15) - {@code Account.changePasswordHash}'s own {@code DELETED}-only guard is never
     * reached, since this method's own check already excludes anything but {@code ACTIVE}/
     * {@code LOCKED}. A {@code LOCKED} account is unlocked as part of a successful reset
     * (Finding 8, human-confirmed) - the reset itself is proof-of-ownership strong enough to also
     * clear the lockout, not just the credential.
     *
     * <p>{@code passwordPolicy.validate} (T09, R8-R10) runs after the eligibility check but
     * before any mutation ({@code unlock}, hash update, family revocation, audit) - consistent
     * with this method's own established pattern of never mutating state until every gate has
     * passed. A policy-violating password submitted with an otherwise-valid, unused token still
     * yields a response distinguishable from an invalid-token rejection ({@code
     * PasswordPolicyViolationException} vs. {@link VerificationTokenRejectedException}) - the
     * token itself is not durably consumed, since {@code consumeForPurpose}'s mutation and this
     * method's own later throw both occur inside the same {@code @Transactional} boundary, so a
     * thrown exception rolls the whole transaction back and the token remains usable for a retry.
     * This residual response-type signal was reviewed and accepted (Phase 3/4): a caller who
     * already holds a valid raw reset token gains nothing by probing with a bad password first,
     * since they could submit a compliant one and complete the reset outright.</p>
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UUID accountUuid = verificationTokenService
                .consumeForPurpose(rawToken, VerificationToken.Purpose.PASSWORD_RESET)
                .orElseThrow(VerificationTokenRejectedException::new);

        Account account = accountRepository.findByAccountUuid(accountUuid)
                .orElseThrow(VerificationTokenRejectedException::new);
        if (!isPasswordResetEligible(account)) {
            throw new VerificationTokenRejectedException();
        }
        passwordPolicy.validate(newPassword, accountUuid, accountUuid);

        if (account.getStatus() == AccountStatus.LOCKED) {
            account.unlock();
        }
        account.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenTracker.revokeAllForPrincipal(account.getAccountUuid().toString(), "PASSWORD_RESET");
        recordAudit("password.reset", account.getAccountUuid(), account.getAccountUuid());
    }

    private boolean isPasswordResetEligible(Account account) {
        return account.getStatus() == AccountStatus.ACTIVE || account.getStatus() == AccountStatus.LOCKED;
    }

    /**
     * Changes the caller's own password (T08, R11). Order is deliberate and fixed: account-status
     * check, then current-password verification, then new-password policy validation, then the
     * mutation itself - each step only runs if every prior one passed.
     *
     * <p>The status check runs before {@code passwordEncoder.matches} specifically to avoid
     * calling it against a non-{@code ACTIVE} account's password hash (a {@code DELETED}
     * account's hash is {@code null}). The policy check runs after the current-password check so a
     * caller who doesn't even know their current password never triggers a breach-check network
     * call for a request that was always going to be rejected.</p>
     *
     * <p>No refresh-token family revocation and no outbox event - R11 names neither, unlike R14's
     * password-reset (Phase 3/4, human-confirmed: change-password isn't a compromise-recovery
     * flow, the caller is already using a valid, currently-authenticated session).</p>
     */
    @Transactional
    public void changePassword(UUID accountUuid, String currentPassword, String newPassword) {
        Account account = getAccount(accountUuid);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException(accountUuid, account.getStatus(), "change password");
        }
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new CurrentPasswordMismatchException();
        }
        passwordPolicy.validate(newPassword, accountUuid, accountUuid);

        account.changePasswordHash(passwordEncoder.encode(newPassword));
        recordAudit("password.changed", accountUuid, accountUuid);
    }

    /**
     * Marks the email verified and activates the account — the admin-initiated path, reachable
     * only via the authenticated admin endpoint, always audited with a real admin
     * {@code actorUuid} (never null). {@link #activateFromVerificationToken} is the self-service
     * counterpart added by T06 (actor = the account's own UUID); this method is not a stand-in
     * for it and is kept intentionally distinct, not merged, so admin-initiated activation
     * remains separately auditable.
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
     * Guarded, idempotent lock — a no-op unless the account is currently {@code ACTIVE}. Called
     * by {@code LockoutService} (T12), the only sanctioned path from the {@code authn} module to
     * this entity (L12). The guard exists specifically because {@code LockoutService} may decide
     * to "lock" an account that is already {@code LOCKED} (T11's escalating re-lock behavior,
     * AC7): {@code Account.lock()} itself would throw in that case, since it requires {@code
     * ACTIVE}. {@code lockout_state} still updates on that path; only the redundant {@code
     * Account}-side transition is skipped here.
     */
    @Transactional
    public void lock(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        if (account.getStatus() == AccountStatus.ACTIVE) {
            account.lock();
        }
    }

    /** Guarded, idempotent unlock — a no-op unless the account is currently {@code LOCKED}. */
    @Transactional
    public void unlock(UUID accountUuid) {
        Account account = getAccount(accountUuid);
        if (account.getStatus() == AccountStatus.LOCKED) {
            account.unlock();
        }
    }

    /**
     * Admin/compliance-initiated unlock (T14, R20) — the counterpart to {@link
     * #suspend}/{@link #reinstate}/{@link #delete}. Reuses {@link #unlock(UUID)}'s own guard
     * (status-transition idempotent: a no-op on {@code Account.status} if the account isn't
     * currently {@code LOCKED}), but the lifecycle event and audit record are unconditional —
     * every successful call appends a new audit row and lifecycle event regardless of whether the
     * status actually changed, so this method is not idempotent in the side-effect sense, only in
     * the state-transition sense. {@code actorUuid} is the authenticated admin/compliance caller,
     * never defaulted to {@code accountUuid} (unlike every self-service call site in this class).
     */
    @Transactional
    public AccountResponse adminUnlock(UUID accountUuid, UUID actorUuid) {
        unlock(accountUuid);
        Account account = getAccount(accountUuid);
        publishLifecycleEvent(account, "user.unlocked");
        recordAudit("account.unlocked", accountUuid, actorUuid);
        return AccountResponse.from(account);
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

    /**
     * Issues a token for the given purpose and emits {@code auth.email.requested} accordingly.
     * Generalized (T07) from an {@code EMAIL_VERIFY}-only helper — {@code purposeLabel} is the
     * plain-string discriminator {@link EmailRequestedEventPayload} carries (R3/R13), decoupled
     * from the internal {@link VerificationToken.Purpose} enum.
     */
    private void issueAndEmitVerificationEmail(
            Account account, VerificationToken.Purpose purpose, String purposeLabel) {
        VerificationTokenService.VerificationTokenResult result =
                verificationTokenService.issue(account.getAccountUuid(), purpose);
        outboxPublisher.publish(
                "verification-token",
                account.getAccountUuid().toString(),
                "email.requested",
                SCHEMA_VERSION,
                new EmailRequestedEventPayload(
                        account.getAccountUuid(), purposeLabel, result.rawToken(), clock.instant()));
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

    /** Thrown by {@link #changePassword} when the supplied current password does not match. */
    public static class CurrentPasswordMismatchException extends RuntimeException {

        public CurrentPasswordMismatchException() {
            super("Current password does not match");
        }
    }
}
