package com.themistra.auth.apikey;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * API key config: the public prefix every generated key starts with (L7), and the TTL of the
 * access token minted by {@code POST /api-keys/token} (T25, L8). Validated at startup — a
 * missing or non-positive TTL fails boot instead of silently minting instantly-expired tokens.
 * The upper bound (24 hours) is deliberately much tighter than {@code VerificationTokenProperties}'s
 * one-year bound (Phase 9 gate): that precedent governs a single-use, emailed link, where a long
 * TTL is low-risk; this property governs a repeatedly reissued bearer access token — L8's own
 * default is 10 minutes — so a misconfiguration here should fail fast well before it could mint a
 * long-lived bearer credential.
 */
@ConfigurationProperties(prefix = "themistra.auth.api-key")
@Validated
public record ApiKeyProperties(

        @NotBlank String prefix,
        @Min(1) @Max(1440) long tokenTtlMinutes
) {
}
