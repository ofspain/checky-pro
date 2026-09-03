package com.themistra.crypto.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Per-chain RPC provider list feeding the quorum fan-out (design.md O1). The provider set and
 * exact N/threshold are unresolved (package.md §11 Q1) so this stays a generic, vendor-name-agnostic
 * shape — {@code local} profile values are placeholders, never real provider URLs/keys (L13).
 */
@ConfigurationProperties(prefix = "themistra.crypto.providers")
@Validated
public record ProviderProperties(
        @NotEmpty @Valid List<ChainProviders> chains,
        @Min(1) int quorumThreshold
) {

    public record ChainProviders(
            @NotBlank String chain,
            @NotEmpty @Valid List<ProviderEntry> providers
    ) {
    }

    public record ProviderEntry(
            @NotBlank String name,
            @NotBlank String url,
            @Min(1) int timeoutSeconds,
            @NotBlank String apiKeySecretName
    ) {
    }
}
