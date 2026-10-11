package com.themistra.payment.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Receipt WORM storage (design.md §4c, L9): the S3 bucket for signed receipts and the Crypto
 * Service's published verification-key endpoint. Both fields' own {@code ${VAR:}} fallback in
 * {@code application.properties} is deliberately empty — a real placeholder meaning "fill this
 * in" (Phase 3 finding 2) — so both are genuinely required (L13) and fail startup in any
 * non-local profile when missing or blank.
 */
@ConfigurationProperties(prefix = "themistra.payment.receipt")
@Validated
public record ReceiptProperties(
        @NotBlank String bucket,
        @NotBlank String kmsPublicKeysUrl
) {
}
