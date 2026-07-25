package com.themistra.auth.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Public, email-identified request (R6, as modified at the Phase 0 human-approval gate — no
 * authentication, since a PENDING_VERIFICATION account cannot yet obtain a token). A blank or
 * malformed email is a validation error (400), intentionally distinguishable from the uniform
 * acknowledgement — that distinction reveals nothing about any specific account.
 */
public record ResendVerificationRequest(

        @NotBlank
        @Email
        String email
) {
}
