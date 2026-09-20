package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.LoginView;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

/**
 * Bridges accounts into the SAS interactive login. The user types an email; the returned
 * principal name is the account UUID, so it — never the email — becomes the token 'sub'
 * (target-design §6). The MFA step (D-014) joins this flow in a later stage; roles arrive with
 * the RBAC stage.
 *
 * <p>{@code accountLocked} is computed from {@link LockoutService#isCurrentlyLocked}, not from
 * {@code AccountStatus.LOCKED} alone (T13, R18) — {@code Account.status} only flips back to
 * {@code ACTIVE} via a successful login, password reset, or admin unlock, but Spring's
 * {@code DaoAuthenticationProvider} evaluates this flag *before* password verification. Without
 * this distinction, an account whose lockout interval has already elapsed could never complete
 * the very login attempt that would unlock it — Spring would keep rejecting it at this
 * pre-authentication gate forever, regardless of elapsed time.</p>
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountService accountService;
    private final LockoutService lockoutService;
    private final Clock clock;

    public AccountUserDetailsService(AccountService accountService, LockoutService lockoutService, Clock clock) {
        this.accountService = accountService;
        this.lockoutService = lockoutService;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LoginView view = accountService.findLoginView(email)
                // uniform message: unknown and deleted are indistinguishable (enumeration defense)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        boolean stillLocked = view.status() == AccountStatus.LOCKED
                && lockoutService.isCurrentlyLocked(view.accountUuid(), clock.instant());

        return User.withUsername(view.accountUuid().toString())
                .password(view.passwordHash())
                .disabled(view.status() == AccountStatus.PENDING_VERIFICATION
                        || view.status() == AccountStatus.SUSPENDED)
                .accountLocked(stillLocked)
                .authorities(List.of())
                .build();
    }
}
