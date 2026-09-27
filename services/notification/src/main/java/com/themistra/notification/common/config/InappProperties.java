package com.themistra.notification.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * In-app transport config (design.md §4c, O3/Q3). {@code transport} stays an unconstrained string
 * (Kimi Phase 3 Finding #4) - SSE vs WebSocket is the spec's own recommendation, not yet a live
 * wiring decision (task 13), so no enum restricts the allowed value here.
 */
@ConfigurationProperties(prefix = "themistra.notification.inapp")
@Validated
public record InappProperties(
        @NotBlank String transport
) {
}
