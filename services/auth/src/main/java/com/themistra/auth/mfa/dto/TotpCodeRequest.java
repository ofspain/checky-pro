package com.themistra.auth.mfa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /accounts/me/mfa/totp/confirm}'s request body (R23). Bounds validated here so a
 * blank or garbage-length code 400s via the framework's existing
 * {@code MethodArgumentNotValidException} handling, rather than reaching {@code MfaService} at
 * all; the TOTP verifier itself remains the sole authority on whether a code is actually valid.
 */
public record TotpCodeRequest(

        @NotBlank @Size(max = 20) String code
) {
}
