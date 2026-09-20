package com.themistra.auth.ratelimit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-account request-rate thresholds (R41/R42, T31) — a backstop behind ingress-level IP
 * limiting, not the primary defense. Values are proposed defaults confirmed at the Phase 4 gate
 * (`design.md` §4b-O2 / `package.md` §11 Q2 left the exact thresholds to the implementer to
 * propose); validated at startup so a zero/negative threshold fails fast rather than silently
 * either blocking everyone or limiting no one.
 */
@ConfigurationProperties(prefix = "themistra.auth.rate-limit")
@Validated
public record RateLimitProperties(

        @Min(1) int loginPerMinute,
        @Min(1) int passwordResetPerMinute,
        @Min(1) int oauthTokenPerMinute
) {
}
