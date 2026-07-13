package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String RAW_PASSWORD = "correct-horse-battery";
    private static final String ENCODED = "{bcrypt}encoded";
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private AuditService auditService;

    private AccountService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AccountService(
                accountRepository, passwordEncoder, outboxPublisher, auditService, fixed);
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
    void registerNeverPublishesAnEvent() {
        // auth.user.registered fires at email confirmation, not at initial signup (target-design §9)
        when(accountRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn(ENCODED);
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(new RegisterAccountRequest("merchant@example.com", RAW_PASSWORD));

        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
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
    void activateEmailTransitionsThroughAggregateAndPublishesRegistered() {
        Account account = Account.register("user@example.com", ENCODED);
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        AccountResponse response = service.activateEmail(account.getAccountUuid());

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

        // routine self-service email verification is not itself a security audit event
        verify(auditService, never()).record(any());
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

        assertThat(service.suspend(account.getAccountUuid()).status())
                .isEqualTo(AccountStatus.SUSPENDED);
        assertThat(service.reinstate(account.getAccountUuid()).status())
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
                .allSatisfy(req -> assertThat(req.accountUuid()).isEqualTo(account.getAccountUuid()));
    }

    @Test
    void deletePublishesDeletedEventAndAuditsIt() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        AccountResponse response = service.delete(account.getAccountUuid());

        assertThat(response.status()).isEqualTo(AccountStatus.DELETED);
        verify(outboxPublisher).publish(eq("account"), anyString(), eq("user.deleted"), eq(1), any());

        ArgumentCaptor<RecordAuditEventRequest> auditCaptor =
                ArgumentCaptor.forClass(RecordAuditEventRequest.class);
        verify(auditService).record(auditCaptor.capture());
        assertThat(auditCaptor.getValue().eventType()).isEqualTo("account.deleted");
        assertThat(auditCaptor.getValue().accountUuid()).isEqualTo(account.getAccountUuid());
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

        assertThatThrownBy(() -> service.reinstate(account.getAccountUuid()))
                .isInstanceOf(InvalidAccountStateException.class);

        verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any());
        verify(auditService, never()).record(any());
    }
}
