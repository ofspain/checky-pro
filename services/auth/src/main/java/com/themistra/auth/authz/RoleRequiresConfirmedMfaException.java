package com.themistra.auth.authz;

/**
 * Thrown by {@link RoleService#assignRole}/{@link RoleService#assignRoleTemplate} when the
 * target account would end up holding {@code MERCHANT} or {@code ADMIN} (directly or via a
 * template) without a confirmed TOTP enrollment (R24: SAS refuses an unenrolled MERCHANT/ADMIN at
 * login with the same error as a wrong password, and does not enroll them — granting the role
 * first would lock the account out of login entirely, with no path back in except an admin
 * removing the role again). The correct order is: enroll while the account still holds only
 * {@code USER} (self-service, T19, no MFA required there), then grant the gated role — see
 * auth-decisions.md D-031.
 */
public class RoleRequiresConfirmedMfaException extends RuntimeException {

    public RoleRequiresConfirmedMfaException(String roleName) {
        super("Account must have a confirmed TOTP enrollment before it can hold " + roleName);
    }
}
