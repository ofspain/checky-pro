package com.themistra.auth.apikey;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * API key config: the public prefix every generated key starts with (L7), and the TTL of the
 * access token minted by {@code POST /api-keys/token} (T25, L8). Validated at startup, mirroring
 * {@code VerificationTokenProperties}'s bound — a missing, non-positive, or absurdly large TTL
 * fails boot instead of silently minting instantly-expired tokens.
 */
@ConfigurationProperties(prefix = "themistra.auth.api-key")
@Validated
public record ApiKeyProperties(

        @NotBlank String prefix,
        @Min(1) @Max(525_600) long tokenTtlMinutes
) {
}
