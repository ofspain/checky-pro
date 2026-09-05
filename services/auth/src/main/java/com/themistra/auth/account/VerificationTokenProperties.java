package com.themistra.auth.account;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TTL for single-use verification tokens (email verification and password reset). Validated at
 * startup — a missing, non-positive, or absurdly large value fails boot instead of silently
 * minting instantly-expired tokens or overflowing {@code Instant} arithmetic.
 */
@ConfigurationProperties(prefix = "themistra.auth.verification-token")
@Validated
public record VerificationTokenProperties(

        @Min(1) @Max(525_600) long ttlMinutes
) {
}
