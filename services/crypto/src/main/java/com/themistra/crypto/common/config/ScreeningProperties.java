package com.themistra.crypto.common.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Counterparty screening client config (design.md L12, O4). The vendor (Chainalysis/TRM/Elliptic) is
 * unresolved (package.md §11 Q2), so fields stay generic — no vendor-specific request/response
 * shape. {@code enabled=false} is the valid {@code local}-profile shape (fake providers only, per
 * agents.md); when {@code enabled=true}, {@code baseUrl} and {@code apiKeySecretName} become
 * required, enforced below rather than via {@code @NotBlank} since they are conditionally required.
 */
@ConfigurationProperties(prefix = "themistra.crypto.screening")
@Validated
public record ScreeningProperties(
        boolean enabled,
        String baseUrl,
        @Min(1) int connectTimeoutSeconds,
        @Min(1) int readTimeoutSeconds,
        @Min(0) int retryMaxAttempts,
        String apiKeySecretName
) {

    public ScreeningProperties {
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalStateException(
                    "themistra.crypto.screening.base-url is required when themistra.crypto.screening.enabled=true");
        }
        if (enabled && (apiKeySecretName == null || apiKeySecretName.isBlank())) {
            throw new IllegalStateException(
                    "themistra.crypto.screening.api-key-secret-name is required when themistra.crypto.screening.enabled=true");
        }
    }
}
