package com.themistra.notification.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Email transport config (design.md §4c). The vendor (SES/SendGrid/SMTP, O2/Q2) is unresolved, so
 * {@code transport} stays a generic, unconstrained string — no enum restricting it to a known set
 * (Kimi Phase 3 Finding #4): the task that implements {@code EmailChannel} decides the allowed
 * values, including whatever test-only value its own capturing fake transport needs.
 */
@ConfigurationProperties(prefix = "themistra.notification.email")
@Validated
public record EmailProperties(
        @NotBlank String from,
        @NotBlank String transport
) {
}
