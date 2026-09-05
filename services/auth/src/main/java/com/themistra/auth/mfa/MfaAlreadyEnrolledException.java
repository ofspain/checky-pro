package com.themistra.auth.mfa;

/** Thrown by {@link MfaService#beginEnroll} when a confirmed TOTP enrollment already exists. */
public class MfaAlreadyEnrolledException extends RuntimeException {

    public MfaAlreadyEnrolledException() {
        super("MFA is already enrolled and confirmed for this account");
    }
}
