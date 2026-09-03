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
 * The reverse direction is guarded too (Phase 9 Finding, widened Phase 11): either {@code base-url}
 * or {@code apiKeySecretName} configured with {@code enabled} left {@code false} (or unset, which
 * binds {@code false}) fails fast rather than silently no-op'ing screening — the boolean has no
 * other way to catch a forgotten flag.
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
        if (!enabled && baseUrl != null && !baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "themistra.crypto.screening.base-url is set but themistra.crypto.screening.enabled=false"
                            + " - set enabled=true, or remove base-url for a genuinely disabled local profile");
        }
        if (!enabled && apiKeySecretName != null && !apiKeySecretName.isBlank()) {
            throw new IllegalStateException(
                    "themistra.crypto.screening.api-key-secret-name is set but themistra.crypto.screening.enabled=false"
                            + " - set enabled=true, or remove api-key-secret-name for a genuinely disabled local profile");
        }
    }
}
