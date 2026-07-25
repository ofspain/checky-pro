package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.event.EmailRequestedEventPayload;
import com.themistra.auth.account.event.UserLifecycleEventPayload;
import com.themistra.auth.audit.AuditService;
import com.themistra.auth.audit.RecordAuditEventRequest;
import com.themistra.auth.events.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    private AccountService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountService(
                accountRepository, passwordEncoder, outboxPublisher, auditService,
                verificationTokenService, fixed);
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
        when(verificationTokenService.consume("a-valid-token")).thenReturn(Optional.of(account.getAccountUuid()));
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
        when(verificationTokenService.consume("bad-token")).thenReturn(Optional.empty());

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
        Account account = Account.register("already-active@example.com", ENCODED);
        account.activateEmail();
        when(verificationTokenService.consume("stale-token")).thenReturn(Optional.of(account.getAccountUuid()));
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.activateFromVerificationToken("stale-token"))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class)
                .isNotInstanceOf(InvalidAccountStateException.class);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
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
}
