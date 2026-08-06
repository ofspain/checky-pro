package com.themistra.auth.mfa;

/**
 * Thrown by {@link MfaService#verifyRecoveryCode} when the submitted code is unknown or already
 * used (R29). Message carries no code material.
 */
public class InvalidRecoveryCodeException extends RuntimeException {

    public InvalidRecoveryCodeException() {
        super("The submitted recovery code is invalid or already used");
    }
}
