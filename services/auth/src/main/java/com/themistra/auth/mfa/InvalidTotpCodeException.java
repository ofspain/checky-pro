package com.themistra.auth.mfa;

/**
 * Thrown when a submitted TOTP code fails {@link TotpVerifier#verify} (R29) — during
 * {@link MfaService#confirm}, {@link MfaService#disable}, or a future login-time check (task 20).
 * Message carries no code or secret material.
 */
public class InvalidTotpCodeException extends RuntimeException {

    public InvalidTotpCodeException() {
        super("The submitted TOTP code is invalid");
    }
}
