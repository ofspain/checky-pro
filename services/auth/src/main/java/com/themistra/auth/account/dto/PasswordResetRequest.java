package com.themistra.auth.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Public, email-identified request (R12/R13) — no authentication, mirroring
 * {@code ResendVerificationRequest}'s shape. A blank or malformed email is a validation error
 * (400), intentionally distinguishable from the uniform acknowledgement — that distinction
 * reveals nothing about any specific account.
 */
public record PasswordResetRequest(

        @NotBlank
        @Email
        String email
) {
}
