package com.themistra.auth.mfa;

/**
 * Thrown by {@link MfaSeedEncryption#decrypt} when an envelope's format-version byte is not one
 * ADR-0003 defines ({@code 0x00}/{@code 0x01}), or when the underlying KMS {@code Decrypt} call
 * fails (wrong/rotated key) — never a silent fallthrough or a wrong plaintext.
 */
public class MfaEncryptionException extends RuntimeException {

    public MfaEncryptionException(String message) {
        super(message);
    }

    public MfaEncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
