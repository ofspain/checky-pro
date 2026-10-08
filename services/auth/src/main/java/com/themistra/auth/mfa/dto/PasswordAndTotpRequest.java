package com.themistra.auth.mfa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code DELETE /accounts/me/mfa/totp}'s and {@code POST /accounts/me/mfa/recovery-codes}'s shared
 * request body (R28, R49) — both endpoints require the same two credentials, reused rather than
 * duplicated into two near-identical records. {@code currentPassword}'s bound mirrors R8's own
 * 128-character ceiling; the password encoder remains the sole authority on correctness, so no
 * minimum length is enforced here.
 */
public record PasswordAndTotpRequest(

        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(max = 20) String code
) {
}
