package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.event.EmailRequestedEventPayload;
import com.themistra.auth.account.event.UserLifecycleEventPayload;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.events.OutboxPublisher;
import com.themistra.auth.token.RefreshTokenTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String RAW_PASSWORD = "correct-horse-battery";
    private static final String ENCODED = "{bcrypt}encoded";
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID ACTOR_UUID = UUID.randomUUID();

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private AuditService auditService;

    @Mock
    private VerificationTokenService verificationTokenService;

    @Mock
    private RefreshTokenTracker refreshTokenTracker;

    @Mock
    private PasswordPolicy passwordPolicy;

    private AccountService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountService(
                accountRepository, passwordEncoder, outboxPublisher, auditService,
                verificationTokenService, refreshTokenTracker, passwordPolicy, fixed);
        // Shared, lenient: every test that reaches register()'s success path needs a non-null
        // issue(...) result (register now always issues+emits a verification token, R3); tests
        // that never reach that path (duplicate-email, constraint-race) simply don't use this stub.
        lenient().when(verificationTokenService.issue(any(UUID.class), eq(VerificationToken.Purpose.EMAIL_VERIFY)))
                .thenAnswer(invocation -> {
                    UUID accountUuid = invocation.getArgument(0);
                    VerificationToken token = VerificationToken.create(
                            1L, VerificationToken.Purpose.EMAIL_VERIFY, "token-hash", NOW, NOW.plusSeconds(1800));
                    return new VerificationTokenService.VerificationTokenResult(
                            "raw-verification-token", token, accountUuid, VerificationToken.Purpose.EMAIL_VERIFY);
                });
    }

    @Test
    void registerHashesPasswordNormalizesEmailAndReturnsView() {
        when(accountRepository.existsByEmail("merchant@example.com")).thenReturn(false);
        when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn(ENCODED);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response =
                service.register(new RegisterAccountRequest("  Merchant@Example.COM ", RAW_PASSWORD));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("merchant@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo(ENCODED);
        assertThat(saved.getValue().getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        assertThat(response.email()).isEqualTo("merchant@example.com");
        assertThat(response.status()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
    }

    @Test
    void shouldEmitVerifyEmailEventOnRegistration() {
        // auth.user.registered still only fires at email confirmation (activateFromVerificationToken/
        // activateEmail), not at initial signup - but registration now emits auth.email.requested
        // with purpose verify_email, in the same transaction, per R3 (T06).
        when(accountRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(ENCODED);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response =
                service.register(new RegisterAccountRequest("merchant@example.com", RAW_PASSWORD));

        verify(verificationTokenService)
                .issue(eq(response.accountUuid()), eq(VerificationToken.Purpose.EMAIL_VERIFY));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(
                eq("verification-token"), eq(response.accountUuid().toString()),
                eq("email.requested"), eq(1), payload.capture());
        var event = (EmailRequestedEventPayload) payload.getValue();
        assertThat(event.accountUuid()).isEqualTo(response.accountUuid());
        assertThat(event.purpose()).isEqualTo("verify_email");
        assertThat(event.token()).isEqualTo("raw-verification-token");
        assertThat(event.occurredAt()).isEqualTo(NOW);
        // Finding 1's mitigation: the raw token must never leak via a default toString().
        assertThat(event.toString()).doesNotContain("raw-verification-token");

        // auth.user.registered (a *different* event) is still not published at signup time.
        verify(outboxPublisher, never()).publish(eq("account"), any(), eq("user.registered"), anyInt(), any());
    }

    @Test
    void registerRejectsKnownDuplicateWithoutTouchingEncoder() {
        when(accountRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                service.register(new RegisterAccountRequest("taken@example.com", RAW_PASSWORD)))
                .isInstanceOf(DuplicateEmailException.class);

        verify(passwordEncoder, never()).encode(anyString());
        verify(accountRepository, never()).saveAndFlush(any());
        verify(verificationTokenService, never()).issue(any(), any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
    }

    @Test
    void registerMapsConstraintRaceToDuplicateEmail() {
        when(accountRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(ENCODED);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException("uq email"));

        assertThatThrownBy(() ->
                service.register(new RegisterAccountRequest("raced@example.com", RAW_PASSWORD)))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void activateEmailTransitionsThroughAggregatePublishesAndAudits() {
        Account account = Account.register("user@example.com", ENCODED);
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        AccountResponse response = service.activateEmail(account.getAccountUuid(), ACTOR_UUID);

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.emailVerified()).isTrue();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(
                eq("account"), eq(account.getAccountUuid().toString()), eq("user.registered"),
                eq(1), payload.capture());
        var event = (UserLifecycleEventPayload) payload.getValue();
        assertThat(event.accountUuid()).isEqualTo(account.getAccountUuid());
        assertThat(event.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(event.occurredAt()).isEqualTo(NOW);

        // activation is now an admin-only stopgap (D-024), so it IS audited, with the real actor
        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("account.activated");
        assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(ACTOR_UUID);
    }

    @Test
    void shouldActivateAccountWithValidVerificationToken() {
        Account account = Account.register("user@example.com", ENCODED);
        when(verificationTokenService.consumeForPurpose("a-valid-token", VerificationToken.Purpose.EMAIL_VERIFY))
                .thenReturn(Optional.of(account.getAccountUuid()));
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        AccountResponse response = service.activateFromVerificationToken("a-valid-token");

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.emailVerified()).isTrue();

        verify(outboxPublisher).publish(
                eq("account"), eq(account.getAccountUuid().toString()), eq("user.registered"), eq(1), any());

        // Self-service activation audits with the account's own UUID as actor (Finding 5) -
        // distinct from the admin path's real-admin actorUuid, tested above.
        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("account.activated");
        assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(account.getAccountUuid());
        assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(account.getAccountUuid());
    }

    @Test
    void shouldRejectVerificationWhenTokenConsumeReturnsEmpty() {
        when(verificationTokenService.consumeForPurpose("bad-token", VerificationToken.Purpose.EMAIL_VERIFY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateFromVerificationToken("bad-token"))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class);

        verify(accountRepository, never()).findByAccountUuid(any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldRejectVerificationWhenAccountIsNotPendingVerification() {
        // T06 frozen brief Finding 2: the status check must happen before activateEmail() is
        // called, so InvalidAccountStateException (a distinguishing exception) never leaks here.
        // A spy (not a plain instance) lets this test prove activateEmail() is genuinely never
        // invoked during the rejected attempt, not just that no event/audit followed from it
        // (Phase 11 Gap 6) - the setup call below is the only recorded invocation.
        Account account = org.mockito.Mockito.spy(Account.register("already-active@example.com", ENCODED));
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(verificationTokenService.consumeForPurpose("stale-token", VerificationToken.Purpose.EMAIL_VERIFY))
                .thenReturn(Optional.of(accountUuid));
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.activateFromVerificationToken("stale-token"))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class)
                .isNotInstanceOf(InvalidAccountStateException.class);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(account, org.mockito.Mockito.times(1)).activateEmail(); // only the setup call above
        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldRejectVerificationWhenAccountDisappearsAfterConsume() {
        // Phase 8/11 finding: activateFromVerificationToken must never let AccountNotFoundException
        // (a distinguishing 404) escape, even in this defensive, normally-unreachable case.
        UUID accountUuid = UUID.randomUUID();
        when(verificationTokenService.consumeForPurpose("orphaned-token", VerificationToken.Purpose.EMAIL_VERIFY))
                .thenReturn(Optional.of(accountUuid));
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateFromVerificationToken("orphaned-token"))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class)
                .isNotInstanceOf(AccountNotFoundException.class);

        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(auditService, never()).record(any());
    }

    @org.junit.jupiter.api.DisplayName("shouldResendVerificationOnlyForPending accounts")
    @Test
    void shouldResendVerificationOnlyForPendingAccounts() {
        Account pending = Account.register("pending@example.com", ENCODED);
        Account active = Account.register("active@example.com", ENCODED);
        active.activateEmail();
        when(accountRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(accountRepository.findByEmail("active@example.com")).thenReturn(Optional.of(active));
        when(accountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        service.resendVerificationIfPending("pending@example.com");
        service.resendVerificationIfPending("active@example.com");
        service.resendVerificationIfPending("unknown@example.com");

        verify(verificationTokenService)
                .issue(eq(pending.getAccountUuid()), eq(VerificationToken.Purpose.EMAIL_VERIFY));
        verify(verificationTokenService, never())
                .issue(eq(active.getAccountUuid()), any());
        verify(verificationTokenService, org.mockito.Mockito.times(1)).issue(any(), any());
        verify(outboxPublisher, org.mockito.Mockito.times(1))
                .publish(eq("verification-token"), any(), any(), anyInt(), any());
    }

    @Test
    void resendVerificationNormalizesEmailBeforeLookup() {
        Account pending = Account.register("normalized@example.com", ENCODED);
        when(accountRepository.findByEmail("normalized@example.com")).thenReturn(Optional.of(pending));

        service.resendVerificationIfPending("  Normalized@Example.COM ");

        verify(accountRepository).findByEmail("normalized@example.com");
        verify(verificationTokenService)
                .issue(eq(pending.getAccountUuid()), eq(VerificationToken.Purpose.EMAIL_VERIFY));
    }

    @Test
    void unknownAccountSurfacesAsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(accountRepository.findByAccountUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByUuid(unknown))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void suspendAndReinstateRoundTripPublishBothEvents() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        assertThat(service.suspend(account.getAccountUuid(), ACTOR_UUID).status())
                .isEqualTo(AccountStatus.SUSPENDED);
        assertThat(service.reinstate(account.getAccountUuid(), ACTOR_UUID).status())
                .isEqualTo(AccountStatus.ACTIVE);

        verify(outboxPublisher).publish(eq("account"), anyString(), eq("user.suspended"), eq(1), any());
        verify(outboxPublisher).publish(eq("account"), anyString(), eq("user.reinstated"), eq(1), any());

        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService, org.mockito.Mockito.times(2)).record(auditCaptor.capture());
        assertThat(auditCaptor.getAllValues())
                .extracting(RecordAuditEventRequest::eventType)
                .containsExactly("account.suspended", "account.reinstated");
        assertThat(auditCaptor.getAllValues())
                .allSatisfy(req -> {
                    assertThat(req.accountUuid()).isEqualTo(account.getAccountUuid());
                    assertThat(req.actorUuid()).isEqualTo(ACTOR_UUID);
                });
    }

    @Test
    void deletePublishesDeletedEventAndAuditsIt() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        AccountResponse response = service.delete(account.getAccountUuid(), ACTOR_UUID);

        assertThat(response.status()).isEqualTo(AccountStatus.DELETED);
        verify(outboxPublisher).publish(eq("account"), anyString(), eq("user.deleted"), eq(1), any());

        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("account.deleted");
        assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(account.getAccountUuid());
        assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(ACTOR_UUID);
    }

    @Test
    void loginViewNormalizesEmailAndCarriesCredential() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(account));

        var view = service.findLoginView("  User@Example.COM ");

        assertThat(view).isPresent();
        assertThat(view.get().accountUuid()).isEqualTo(account.getAccountUuid());
        assertThat(view.get().passwordHash()).isEqualTo(ENCODED);
        assertThat(view.get().status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void loginViewHidesDeletedAccountsLikeUnknownEmails() {
        Account account = Account.register("gone@example.com", ENCODED);
        account.activateEmail();
        account.markDeleted();
        when(accountRepository.findByEmail("gone@example.com")).thenReturn(Optional.of(account));

        assertThat(service.findLoginView("gone@example.com")).isEmpty();
    }

    @Test
    void illegalTransitionPropagatesInvalidStateWithoutPublishing() {
        Account account = Account.register("user@example.com", ENCODED);
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.reinstate(account.getAccountUuid(), ACTOR_UUID))
                .isInstanceOf(InvalidAccountStateException.class);

        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldEmitPasswordResetEventOnlyWhenEmailExists() {
        // R13: deliberately the opposite filter from resendVerification's PENDING_VERIFICATION-only
        // check - ACTIVE and LOCKED are both eligible; PENDING_VERIFICATION/DELETED/SUSPENDED and an
        // unknown email are all a silent no-op, same enumeration-safety trade-off as T06 Finding 4.
        Account active = Account.register("active@example.com", ENCODED);
        active.activateEmail();
        Account locked = Account.register("locked@example.com", ENCODED);
        locked.activateEmail();
        locked.lock();
        Account pending = Account.register("pending@example.com", ENCODED);
        Account suspended = Account.register("suspended@example.com", ENCODED);
        suspended.activateEmail();
        suspended.suspend();
        Account deleted = Account.register("deleted@example.com", ENCODED);
        deleted.activateEmail();
        deleted.markDeleted();

        when(accountRepository.findByEmail("active@example.com")).thenReturn(Optional.of(active));
        when(accountRepository.findByEmail("locked@example.com")).thenReturn(Optional.of(locked));
        when(accountRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(pending));
        when(accountRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(suspended));
        when(accountRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(deleted));
        when(accountRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        lenient().when(verificationTokenService.issue(any(UUID.class), eq(VerificationToken.Purpose.PASSWORD_RESET)))
                .thenAnswer(invocation -> passwordResetTokenResult(invocation.getArgument(0)));

        service.requestPasswordReset("active@example.com");
        service.requestPasswordReset("locked@example.com");
        service.requestPasswordReset("pending@example.com");
        service.requestPasswordReset("suspended@example.com");
        service.requestPasswordReset("deleted@example.com");
        service.requestPasswordReset("unknown@example.com");

        verify(verificationTokenService)
                .issue(eq(active.getAccountUuid()), eq(VerificationToken.Purpose.PASSWORD_RESET));
        verify(verificationTokenService)
                .issue(eq(locked.getAccountUuid()), eq(VerificationToken.Purpose.PASSWORD_RESET));
        verify(verificationTokenService, org.mockito.Mockito.times(2))
                .issue(any(), eq(VerificationToken.Purpose.PASSWORD_RESET));
        verify(outboxPublisher, org.mockito.Mockito.times(2))
                .publish(eq("verification-token"), any(), eq("email.requested"), anyInt(), any());
        // Kimi Phase 11 Gap 7: the reset-request path must never accidentally emit an "account"
        // aggregate lifecycle event - only the verification-token/email.requested pair.
        verify(outboxPublisher, never()).publish(eq("account"), any(), any(), anyInt(), any());
    }

    @Test
    void shouldEmitPasswordResetEventWithCorrectPurposeLabelAndToken() {
        Account account = Account.register("reset-target@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByEmail("reset-target@example.com")).thenReturn(Optional.of(account));
        when(verificationTokenService.issue(eq(account.getAccountUuid()), eq(VerificationToken.Purpose.PASSWORD_RESET)))
                .thenReturn(passwordResetTokenResult(account.getAccountUuid()));

        service.requestPasswordReset("reset-target@example.com");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(
                eq("verification-token"), eq(account.getAccountUuid().toString()),
                eq("email.requested"), eq(1), payload.capture());
        var event = (EmailRequestedEventPayload) payload.getValue();
        assertThat(event.accountUuid()).isEqualTo(account.getAccountUuid());
        assertThat(event.purpose()).isEqualTo("password_reset");
        assertThat(event.token()).isEqualTo("raw-reset-token");
    }

    @Test
    void requestPasswordResetNormalizesEmailBeforeLookup() {
        Account account = Account.register("normalized-reset@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByEmail("normalized-reset@example.com")).thenReturn(Optional.of(account));
        when(verificationTokenService.issue(eq(account.getAccountUuid()), eq(VerificationToken.Purpose.PASSWORD_RESET)))
                .thenReturn(passwordResetTokenResult(account.getAccountUuid()));

        service.requestPasswordReset("  Normalized-Reset@Example.COM ");

        verify(accountRepository).findByEmail("normalized-reset@example.com");
    }

    @Test
    void shouldResetPasswordAndRevokeAllFamiliesWithValidToken() {
        Account account = Account.register("reset-me@example.com", ENCODED);
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(verificationTokenService.consumeForPurpose("valid-reset-token", VerificationToken.Purpose.PASSWORD_RESET))
                .thenReturn(Optional.of(accountUuid));
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode("new-correct-horse")).thenReturn("{bcrypt}new-encoded");

        service.resetPassword("valid-reset-token", "new-correct-horse");

        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}new-encoded");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        // Kimi Phase 11 Gap 6: prove the encoder actually received the raw new password, not just
        // that the account ended up holding whatever the (separately stubbed) encoder returned.
        verify(passwordEncoder).encode("new-correct-horse");
        verify(refreshTokenTracker).revokeAllForPrincipal(accountUuid.toString(), "PASSWORD_RESET");

        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("password.reset");
        assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(accountUuid);
        assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(accountUuid);
    }

    @Test
    void shouldUnlockAccountOnSuccessfulPasswordReset() {
        // Finding 8, human-confirmed: a successful reset is proof-of-ownership strong enough to
        // also clear a LOCKED lockout, not just replace the credential.
        Account account = Account.register("locked-reset@example.com", ENCODED);
        account.activateEmail();
        account.lock();
        UUID accountUuid = account.getAccountUuid();
        when(verificationTokenService.consumeForPurpose("locked-reset-token", VerificationToken.Purpose.PASSWORD_RESET))
                .thenReturn(Optional.of(accountUuid));
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.encode(anyString())).thenReturn(ENCODED);

        service.resetPassword("locked-reset-token", "new-password");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(refreshTokenTracker).revokeAllForPrincipal(accountUuid.toString(), "PASSWORD_RESET");
    }

    @Test
    void shouldRejectPasswordResetWhenTokenConsumeReturnsEmpty() {
        when(verificationTokenService.consumeForPurpose("bad-reset-token", VerificationToken.Purpose.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("bad-reset-token", "new-password"))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class);

        verify(accountRepository, never()).findByAccountUuid(any());
        verify(refreshTokenTracker, never()).revokeAllForPrincipal(any(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldRejectPasswordResetWhenAccountDisappearsAfterConsume() {
        // Same defensive guarantee as activateFromVerificationToken's mirror test: a missing
        // account here must fall into the uniform rejection, never AccountNotFoundException's
        // distinguishing 404.
        UUID accountUuid = UUID.randomUUID();
        when(verificationTokenService.consumeForPurpose("orphaned-reset-token", VerificationToken.Purpose.PASSWORD_RESET))
                .thenReturn(Optional.of(accountUuid));
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("orphaned-reset-token", "new-password"))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class)
                .isNotInstanceOf(AccountNotFoundException.class);

        verify(refreshTokenTracker, never()).revokeAllForPrincipal(any(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldRejectPasswordResetForIneligibleAccountStatuses() {
        // R15: PENDING_VERIFICATION, DELETED, and SUSPENDED accounts must all uniformly reject a
        // password-reset confirm, per resetPassword's own isPasswordResetEligible gate - and never
        // let Account.changePasswordHash's own DELETED-only guard (InvalidAccountStateException)
        // leak, since that gate is structurally unreachable from this call path.
        Account pending = Account.register("pending-reset@example.com", ENCODED);
        Account deleted = Account.register("deleted-reset@example.com", ENCODED);
        deleted.activateEmail();
        deleted.markDeleted();
        Account suspended = Account.register("suspended-reset@example.com", ENCODED);
        suspended.activateEmail();
        suspended.suspend();

        for (Account ineligible : java.util.List.of(pending, deleted, suspended)) {
            String originalPasswordHash = ineligible.getPasswordHash();
            String rawToken = "reset-token-" + ineligible.getAccountUuid();
            when(verificationTokenService.consumeForPurpose(rawToken, VerificationToken.Purpose.PASSWORD_RESET))
                    .thenReturn(Optional.of(ineligible.getAccountUuid()));
            when(accountRepository.findByAccountUuid(ineligible.getAccountUuid()))
                    .thenReturn(Optional.of(ineligible));

            assertThatThrownBy(() -> service.resetPassword(rawToken, "new-password"))
                    .isInstanceOf(AccountService.VerificationTokenRejectedException.class)
                    .isNotInstanceOf(InvalidAccountStateException.class);

            // Kimi Phase 11 Gap 2: a rejected attempt must be a genuine no-op, not just silent on
            // the audit/revoke side - the password must never even reach the encoder.
            assertThat(ineligible.getPasswordHash()).isEqualTo(originalPasswordHash);
        }

        verify(passwordEncoder, never()).encode(anyString());
        verify(refreshTokenTracker, never()).revokeAllForPrincipal(any(), any());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-password", ENCODED)).thenReturn(true);
        when(passwordEncoder.encode("new-correct-horse")).thenReturn("{bcrypt}new-encoded");

        service.changePassword(accountUuid, "current-password", "new-correct-horse");

        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}new-encoded");
        // Kimi Phase 11 Gap 1: prove encode() received the raw new password, not just that the
        // account ended up holding whatever the (separately stubbed) encoder returned.
        verify(passwordEncoder).encode("new-correct-horse");
        verify(passwordPolicy).validate("new-correct-horse", accountUuid, accountUuid);

        // Kimi Phase 11 Gap 2: prove the frozen brief's fixed gate order actually held - a
        // regression that reordered the policy check and the encode call would pass every other
        // assertion here but fail this one.
        InOrder inOrder = inOrder(passwordEncoder, passwordPolicy);
        inOrder.verify(passwordEncoder).matches("current-password", ENCODED);
        inOrder.verify(passwordPolicy).validate("new-correct-horse", accountUuid, accountUuid);
        inOrder.verify(passwordEncoder).encode("new-correct-horse");

        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("password.changed");
        assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(accountUuid);
        assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(accountUuid);
    }

    @Test
    void shouldRejectChangePasswordWhenCurrentPasswordDoesNotMatch() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("wrong-password", ENCODED)).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(accountUuid, "wrong-password", "new-password"))
                .isInstanceOf(AccountService.CurrentPasswordMismatchException.class);

        verify(passwordPolicy, never()).validate(any(), any(), any());
        verify(passwordEncoder, never()).encode(anyString());
        assertThat(account.getPasswordHash()).isEqualTo(ENCODED);
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldRejectChangePasswordWhenNewPasswordViolatesPolicy() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("current-password", ENCODED)).thenReturn(true);
        org.mockito.Mockito.doThrow(new PasswordPolicy.PasswordPolicyViolationException("too short"))
                .when(passwordPolicy).validate("short", accountUuid, accountUuid);

        assertThatThrownBy(() -> service.changePassword(accountUuid, "current-password", "short"))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);

        // Kimi Phase 11 Gap 2 (rejection-path ordering): matches() must genuinely run before
        // validate() throws - not that validate() short-circuits it - and encode() is never
        // reached once validate() rejects.
        InOrder inOrder = inOrder(passwordEncoder, passwordPolicy);
        inOrder.verify(passwordEncoder).matches("current-password", ENCODED);
        inOrder.verify(passwordPolicy).validate("short", accountUuid, accountUuid);
        verify(passwordEncoder, never()).encode(anyString());
        assertThat(account.getPasswordHash()).isEqualTo(ENCODED);
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldRejectChangePasswordForEveryNonActiveAccountStatus() {
        Account pending = Account.register("pending@example.com", ENCODED);
        Account locked = Account.register("locked@example.com", ENCODED);
        locked.activateEmail();
        locked.lock();
        Account suspended = Account.register("suspended@example.com", ENCODED);
        suspended.activateEmail();
        suspended.suspend();
        Account deleted = Account.register("deleted@example.com", ENCODED);
        deleted.activateEmail();
        deleted.markDeleted();

        for (Account ineligible : java.util.List.of(pending, locked, suspended, deleted)) {
            UUID accountUuid = ineligible.getAccountUuid();
            when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(ineligible));

            assertThatThrownBy(() -> service.changePassword(accountUuid, "current-password", "new-password"))
                    .isInstanceOf(InvalidAccountStateException.class);
        }

        // The status gate runs before the current-password check (Finding 4) - proves the NPE
        // risk on a DELETED account's null passwordHash is genuinely avoided, not just masked.
        verify(passwordEncoder, never()).matches(any(), any());
        verify(passwordEncoder, never()).encode(anyString());
        verify(auditService, never()).record(any());
    }

    @Test
    void shouldNotRevokeRefreshTokenFamiliesOnSuccessfulPasswordChange() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches(anyString(), eq(ENCODED))).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("{bcrypt}new-encoded");

        service.changePassword(accountUuid, "current-password", "new-password");

        // Deliberate, tested trade-off (frozen brief decision 3) - not a silent omission.
        verify(refreshTokenTracker, never()).revokeAllForPrincipal(any(), any());
    }

    @Test
    void shouldAllowNewPasswordIdenticalToCurrentPassword() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        UUID accountUuid = account.getAccountUuid();
        when(accountRepository.findByAccountUuid(accountUuid)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("same-password", ENCODED)).thenReturn(true);
        when(passwordEncoder.encode("same-password")).thenReturn("{bcrypt}re-encoded");

        service.changePassword(accountUuid, "same-password", "same-password");

        assertThat(account.getPasswordHash()).isEqualTo("{bcrypt}re-encoded");
        verify(passwordPolicy).validate("same-password", accountUuid, accountUuid);

        // Kimi Phase 11 Gap 4: AC9 (allowed) doesn't excuse AC6 (audited) - this scenario still
        // must go through the same success-path audit as any other valid change.
        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("password.changed");
        assertThat(auditCaptor.getValue().actorUuid()).isEqualTo(accountUuid);
        assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(accountUuid);
    }

    /** Shared fixture for a PASSWORD_RESET-purposed issue(...) result - mirrors setUp()'s EMAIL_VERIFY stub. */
    private VerificationTokenService.VerificationTokenResult passwordResetTokenResult(UUID accountUuid) {
        VerificationToken token = VerificationToken.create(
                1L, VerificationToken.Purpose.PASSWORD_RESET, "reset-token-hash", NOW, NOW.plusSeconds(1800));
        return new VerificationTokenService.VerificationTokenResult(
                "raw-reset-token", token, accountUuid, VerificationToken.Purpose.PASSWORD_RESET);
    }
}
