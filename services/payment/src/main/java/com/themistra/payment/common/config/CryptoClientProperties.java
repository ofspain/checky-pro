package com.themistra.payment.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The outbound REST client to the Crypto Service (design.md §4c, L3): {@code POST /attest} for
 * the receipt signature, {@code POST}/{@code DELETE /watches} for watch registration. {@code
 * baseUrl}'s own default ({@code http://crypto-service}) is a legitimate, usable k8s-DNS-style
 * value, not a placeholder (Phase 3 finding 2) — none of these fields is forced-required.
 *
 * <p>{@code auth} (T06) — the {@code client_credentials} token this client needs to satisfy
 * {@code contracts/api/crypto-internal.yaml}'s own {@code bearerAuth: [internal.crypto:write]}
 * requirement. Reuses the same {@code PAYMENT_SERVICE_CLIENT_SECRET} env var auth-service's own
 * client registration reads, so there is only one source of truth for this secret.</p>
 */
@ConfigurationProperties(prefix = "themistra.payment.crypto")
@Validated
public record CryptoClientProperties(
        @NotBlank String baseUrl,
        @NotBlank String attestPath,
        @NotBlank String watchPath,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs,
        @NotNull @Valid Auth auth
) {

    public record Auth(
            @NotBlank String tokenUri,
            @NotBlank String clientId,
            @NotBlank String clientSecret
    ) {
    }
}
