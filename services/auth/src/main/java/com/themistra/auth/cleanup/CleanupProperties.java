package com.themistra.auth.cleanup;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Scheduled cleanup job config (T30, R40): the cron expression the job runs on, and the
 * retention window (in days) for expired verification tokens and old revoked refresh-token
 * families. Validated at startup — a missing cron or a non-positive retention would otherwise
 * only surface as a silent no-op or an immediate mass-delete the first time the job actually runs.
 */
@ConfigurationProperties(prefix = "themistra.auth.cleanup")
@Validated
public record CleanupProperties(

        @NotBlank String cron,
        /** Also governs stale {@code shedlock} row retention (deliberate reuse, Phase 2 OQ2 -
         * ShedLock rows are low-cardinality, so a dedicated fourth property wasn't worth adding). */
        @Min(1) int tokenRetentionDays,
        @Min(1) int familyRetentionDays
) {
}
