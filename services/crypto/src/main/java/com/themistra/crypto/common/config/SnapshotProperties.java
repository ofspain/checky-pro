package com.themistra.crypto.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * S3 WORM snapshot config for the verbatim observation log (design.md L3, task 8's
 * {@code ObservationSnapshotStore}). No access credentials here — those are injected by External
 * Secrets Operator via the AWS SDK's own credential chain, never as a Spring property (L13).
 */
@ConfigurationProperties(prefix = "themistra.crypto.snapshot")
@Validated
public record SnapshotProperties(
        @NotBlank String bucket,
        @NotBlank String prefix,
        @NotBlank String region
) {
}
