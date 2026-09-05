package com.themistra.auth.authn;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Brute-force lockout config (L4): 5 failed attempts / 30-minute inactivity decay / 15-minute
 * base lock. Validated at startup — a missing or non-positive value fails boot instead of
 * silently defaulting, matching {@code PasswordPolicyProperties}'s established pattern.
 */
@ConfigurationProperties(prefix = "themistra.auth.lockout")
@Validated
public record LockoutProperties(

        @Min(1) int maxAttempts,
        @Min(1) int windowMinutes,
        @Min(1) int baseLockMinutes
) {
}
