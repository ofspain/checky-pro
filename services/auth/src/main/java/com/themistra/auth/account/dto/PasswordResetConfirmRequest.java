package com.themistra.auth.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The raw password-reset token and the new plaintext password (R14/R15). {@code toString()} is
 * overridden to omit {@code newPassword} — records auto-generate a {@code toString()} that would
 * otherwise print it verbatim (the same leak class {@code EmailRequestedEventPayload} and
 * {@code VerificationTokenResult} guard against). {@code token} is not redacted, consistent with
 * {@code VerifyEmailRequest}'s existing precedent.
 */
public record PasswordResetConfirmRequest(

        @NotBlank
        String token,

        @NotBlank
        String newPassword
) {

    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=" + token + "]";
    }
}
