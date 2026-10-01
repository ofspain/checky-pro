package com.themistra.notification.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Bounded retry/backoff policy (design.md §4c, L7, Q6 - confirmed at T14). Compact-constructor
 * cross-field check (Kimi Phase 3 Finding #5, mirrors {@code ScreeningProperties}'s own style):
 * {@code maxBackoffSeconds} below {@code initialBackoffSeconds} is a real misconfiguration
 * {@code @Min} alone cannot catch, since each field is independently valid in isolation.
 *
 * <p>{@code schedulerIntervalSeconds} (T14) governs {@code RetryScheduler}'s own poll frequency -
 * a separate concern from the backoff-between-attempts the other three fields express.</p>
 */
@ConfigurationProperties(prefix = "themistra.notification.retry")
@Validated
public record RetryProperties(
        @Min(1) int maxAttempts,
        @Min(1) int initialBackoffSeconds,
        @Min(1) int maxBackoffSeconds,
        @Min(1) int schedulerIntervalSeconds
) {

    public RetryProperties {
        if (maxBackoffSeconds < initialBackoffSeconds) {
            throw new IllegalStateException(
                    "themistra.notification.retry.max-backoff-seconds must be >= "
                            + "themistra.notification.retry.initial-backoff-seconds");
        }
    }
}
