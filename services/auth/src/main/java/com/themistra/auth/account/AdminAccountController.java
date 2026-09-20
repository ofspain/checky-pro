package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.authn.LockoutService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Operator endpoints. Every method carries its own {@code @PreAuthorize} (not a class-level
 * annotation — deliberately, to avoid depending on unverified class-level method-security
 * behavior across Spring Security versions).
 *
 * {@code activate} is a stopgap (D-024): the intended self-service, token-based email
 * verification flow is not yet built, so this admin action stands in for it until it is.
 *
 * {@code unlock} (T14, R20) is the first method here to depend on a service from another module
 * ({@code LockoutService}, {@code authn}) — the same shape {@code AccountUserDetailsService}
 * already established (T12), not a new precedent. {@code LockoutService.resetLockout} clears
 * {@code lockout_state}; {@code AccountService.adminUnlock} handles the {@code Account}-side
 * transition, lifecycle event, and audit — L12 stays intact since {@code AccountService} itself
 * gains no dependency on {@code authn}.
 */
@RestController
@RequestMapping("/admin/accounts")
public class AdminAccountController {

    private final AccountService accountService;
    private final LockoutService lockoutService;

    public AdminAccountController(AccountService accountService, LockoutService lockoutService) {
        this.accountService = accountService;
        this.lockoutService = lockoutService;
    }

    @GetMapping("/{accountUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse get(@PathVariable UUID accountUuid) {
        return accountService.getByUuid(accountUuid);
    }

    @PostMapping("/{accountUuid}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse activate(@PathVariable UUID accountUuid, Authentication authentication) {
        return accountService.activateEmail(accountUuid, actorUuid(authentication));
    }

    @PostMapping("/{accountUuid}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public AccountResponse suspend(@PathVariable UUID accountUuid, Authentication authentication) {
        return accountService.suspend(accountUuid, actorUuid(authentication));
    }

    @PostMapping("/{accountUuid}/reinstate")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public AccountResponse reinstate(@PathVariable UUID accountUuid, Authentication authentication) {
        return accountService.reinstate(accountUuid, actorUuid(authentication));
    }

    @DeleteMapping("/{accountUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountResponse delete(@PathVariable UUID accountUuid, Authentication authentication) {
        return accountService.delete(accountUuid, actorUuid(authentication));
    }

    @PostMapping("/{accountUuid}/unlock")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPLIANCE')")
    public AccountResponse unlock(@PathVariable UUID accountUuid, Authentication authentication) {
        lockoutService.resetLockout(accountUuid);
        return accountService.adminUnlock(accountUuid, actorUuid(authentication));
    }

    private static UUID actorUuid(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
