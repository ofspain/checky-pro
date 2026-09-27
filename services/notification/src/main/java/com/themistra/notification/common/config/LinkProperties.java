package com.themistra.notification.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email link base URL (design.md §4c, Q4 unresolved). Deliberately carries no {@code @NotBlank} —
 * the spec's own VERBATIM default ({@code ${AUTH_EMAIL_LINK_BASE_URL:}}) binds this blank in the
 * {@code local} profile, so an unconditional non-blank constraint here would break local startup.
 * The non-blank requirement outside {@code local} is enforced separately, by
 * {@link LinkPropertiesStartupValidation} (Kimi Phase 3 Finding #1) - not by this record.
 */
@ConfigurationProperties(prefix = "themistra.notification.link")
@Validated
public record LinkProperties(
        String baseUrl
) {
}
