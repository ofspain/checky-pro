package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String RAW_PASSWORD = "correct-horse-battery";
    private static final String ENCODED = "{bcrypt}encoded";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService service;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, passwordEncoder);
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
    void activateEmailTransitionsThroughAggregate() {
        Account account = Account.register("user@example.com", ENCODED);
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        AccountResponse response = service.activateEmail(account.getAccountUuid());

        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.emailVerified()).isTrue();
    }

    @Test
    void unknownAccountSurfacesAsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(accountRepository.findByAccountUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByUuid(unknown))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void suspendAndReinstateRoundTrip() {
        Account account = Account.register("user@example.com", ENCODED);
        account.activateEmail();
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        assertThat(service.suspend(account.getAccountUuid()).status())
                .isEqualTo(AccountStatus.SUSPENDED);
        assertThat(service.reinstate(account.getAccountUuid()).status())
                .isEqualTo(AccountStatus.ACTIVE);
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
    void illegalTransitionPropagatesInvalidState() {
        Account account = Account.register("user@example.com", ENCODED);
        when(accountRepository.findByAccountUuid(account.getAccountUuid()))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.reinstate(account.getAccountUuid()))
                .isInstanceOf(InvalidAccountStateException.class);
    }
}
