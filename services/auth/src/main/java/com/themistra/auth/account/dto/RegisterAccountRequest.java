package com.themistra.auth.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Password rules follow NIST 800-63B (D-006): length 12–128, no composition rules.
 * Breached-password screening is a separate policy check in the authn module, not bean validation.
 */
public record RegisterAccountRequest(

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Size(min = 12, max = 128)
        String password
) {
}
