package com.themistra.payment.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The outbound REST client to the Crypto Service (design.md §4c, L3): {@code POST /attest} for
 * the receipt signature, {@code POST}/{@code DELETE /watches} for watch registration. {@code
 * baseUrl}'s own default ({@code http://crypto-service}) is a legitimate, usable k8s-DNS-style
 * value, not a placeholder (Phase 3 finding 2) — none of these fields is forced-required.
 */
@ConfigurationProperties(prefix = "themistra.payment.crypto")
@Validated
public record CryptoClientProperties(
        @NotBlank String baseUrl,
        @NotBlank String attestPath,
        @NotBlank String watchPath,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs
) {
}
