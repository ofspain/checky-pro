package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.LoginView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountUserDetailsServiceTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();
    private static final String EMAIL = "merchant@example.com";
    private static final String HASH = "{bcrypt}hash";

    @Mock
    private AccountService accountService;

    private AccountUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AccountUserDetailsService(accountService);
    }

    private void accountWithStatus(AccountStatus status) {
        when(accountService.findLoginView(EMAIL))
                .thenReturn(Optional.of(new LoginView(ACCOUNT_UUID, HASH, status)));
    }

    @Test
    void activeAccountMapsToEnabledPrincipalNamedByUuid() {
        accountWithStatus(AccountStatus.ACTIVE);

        UserDetails details = service.loadUserByUsername(EMAIL);

        // the principal (future token 'sub') is the UUID — the email never becomes the subject
        assertThat(details.getUsername()).isEqualTo(ACCOUNT_UUID.toString());
        assertThat(details.getPassword()).isEqualTo(HASH);
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void pendingVerificationMapsToDisabled() {
        accountWithStatus(AccountStatus.PENDING_VERIFICATION);

        assertThat(service.loadUserByUsername(EMAIL).isEnabled()).isFalse();
    }

    @Test
    void suspendedMapsToDisabled() {
        accountWithStatus(AccountStatus.SUSPENDED);

        assertThat(service.loadUserByUsername(EMAIL).isEnabled()).isFalse();
    }

    @Test
    void lockedMapsToAccountLocked() {
        accountWithStatus(AccountStatus.LOCKED);

        assertThat(service.loadUserByUsername(EMAIL).isAccountNonLocked()).isFalse();
    }

    @Test
    void unknownEmailFailsWithUniformMessage() {
        when(accountService.findLoginView("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Bad credentials");   // no enumeration hint
    }
}
