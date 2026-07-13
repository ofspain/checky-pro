package com.themistra.auth.authn;

import com.themistra.auth.account.AccountService;
import com.themistra.auth.account.AccountStatus;
import com.themistra.auth.account.dto.LoginView;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges accounts into the SAS interactive login. The user types an email; the returned
 * principal name is the account UUID, so it — never the email — becomes the token 'sub'
 * (target-design §6). Lockout counting and the MFA step (D-014) join this flow in later stages;
 * roles arrive with the RBAC stage.
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountService accountService;

    public AccountUserDetailsService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        LoginView view = accountService.findLoginView(email)
                // uniform message: unknown and deleted are indistinguishable (enumeration defense)
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        return User.withUsername(view.accountUuid().toString())
                .password(view.passwordHash())
                .disabled(view.status() == AccountStatus.PENDING_VERIFICATION
                        || view.status() == AccountStatus.SUSPENDED)
                .accountLocked(view.status() == AccountStatus.LOCKED)
                .authorities(List.of())
                .build();
    }
}
