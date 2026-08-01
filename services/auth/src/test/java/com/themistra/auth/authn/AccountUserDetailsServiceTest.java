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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Mock
    private AccountService accountService;

    @Mock
    private LockoutService lockoutService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private AccountUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new AccountUserDetailsService(accountService, lockoutService, clock);
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

        UserDetails details = service.loadUserByUsername(EMAIL);
        assertThat(details.isEnabled()).isFalse();
        // Phase 11 Gap 7: the whole point of T13's fix is separating accountLocked from raw
        // status - a non-LOCKED account must never be rejected via the locked gate.
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void suspendedMapsToDisabled() {
        accountWithStatus(AccountStatus.SUSPENDED);

        UserDetails details = service.loadUserByUsername(EMAIL);
        assertThat(details.isEnabled()).isFalse();
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void stillLockedMapsToAccountLocked() {
        // AC6 (regression guard): a genuinely-still-locked account remains rejected, unchanged
        // from before T13.
        accountWithStatus(AccountStatus.LOCKED);
        when(lockoutService.isCurrentlyLocked(ACCOUNT_UUID, NOW)).thenReturn(true);

        assertThat(service.loadUserByUsername(EMAIL).isAccountNonLocked()).isFalse();
    }

    @Test
    void expiredLockDoesNotMapToAccountLocked() {
        // AC5, the core fix: Account.status is still LOCKED (nothing has flipped it back yet),
        // but the lockout interval has already elapsed - Spring's pre-authentication gate must
        // not reject the attempt, or R18's "allow the next authentication attempt" could never
        // be satisfied through the real login flow.
        accountWithStatus(AccountStatus.LOCKED);
        when(lockoutService.isCurrentlyLocked(ACCOUNT_UUID, NOW)).thenReturn(false);

        assertThat(service.loadUserByUsername(EMAIL).isAccountNonLocked()).isTrue();
    }

    @Test
    void unknownEmailFailsWithUniformMessage() {
        when(accountService.findLoginView("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("Bad credentials");   // no enumeration hint
    }
}
