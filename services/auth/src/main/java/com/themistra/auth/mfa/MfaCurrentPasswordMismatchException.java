package com.themistra.auth.mfa;

/**
 * Thrown by {@link MfaService#disable} when the supplied current password does not match.
 * Deliberately distinct from {@code AccountService.CurrentPasswordMismatchException} (L12: this
 * module never references that class, and the two flows are audited differently — see
 * {@link MfaService#disable}).
 */
public class MfaCurrentPasswordMismatchException extends RuntimeException {

    public MfaCurrentPasswordMismatchException() {
        super("Current password does not match");
    }
}
