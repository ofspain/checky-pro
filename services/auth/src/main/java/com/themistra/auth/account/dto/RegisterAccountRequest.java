package com.themistra.auth.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password rules follow NIST 800-63B (D-006): length 12–128, no composition rules. All content
 * enforcement — length and breached-password screening — is {@link
 * com.themistra.auth.account.PasswordPolicy}'s job (T09), not bean validation; this layer only
 * rejects a blank password.
 */
public record RegisterAccountRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        String password
) {
}
