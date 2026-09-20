package com.themistra.auth.account.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The raw verification token as presented by the caller (R4/R5) — never the hash, never logged.
 */
public record VerifyEmailRequest(

        @NotBlank
        String token
) {
}
