package com.themistra.auth.mfa;

/**
 * Thrown by {@link MfaService#confirm} when no unconfirmed TOTP enrollment exists, and by
 * {@link MfaService#disable} when no confirmed one exists.
 */
public class MfaNotEnrolledException extends RuntimeException {

    public MfaNotEnrolledException() {
        super("No matching MFA enrollment exists for this account");
    }
}
