package com.themistra.payment.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Receipt WORM storage (design.md §4c, L9): the S3 bucket for signed receipts and the Crypto
 * Service's published verification-key endpoint. Every field's own {@code ${VAR:}} fallback in
 * {@code application.properties} is a local-only fake value, not a real default — so all four
 * are genuinely required (L13) and fail startup in any non-local profile when missing or blank.
 *
 * <p>{@code prefix} and {@code region} were added at T18 (Phase 3/4, Kimi Findings 6/7) — the
 * {@code S3Client} this task's {@code ReceiptStore} needs requires a region, and a key prefix
 * keeps receipt objects out of the bucket root, mirroring {@code crypto-service}'s own
 * {@code SnapshotProperties(bucket, prefix, region)} shape exactly.</p>
 */
@ConfigurationProperties(prefix = "themistra.payment.receipt")
@Validated
public record ReceiptProperties(
        @NotBlank String bucket,
        @NotBlank String prefix,
        @NotBlank String region,
        @NotBlank String kmsPublicKeysUrl
) {
}
