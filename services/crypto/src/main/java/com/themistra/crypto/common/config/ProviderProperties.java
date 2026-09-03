package com.themistra.crypto.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Per-chain RPC provider list feeding the quorum fan-out (design.md O1). The provider set and
 * exact N/threshold are unresolved (package.md §11 Q1) so provider names/URLs stay generic,
 * vendor-name-agnostic — {@code local} profile values are placeholders, never real provider
 * URLs/keys (L13). {@code chain} is constrained to the launch scope fixed by design.md §2
 * (ETHEREUM/TRON) — that set, unlike the vendor names, isn't open, so a typo fails fast.
 */
@ConfigurationProperties(prefix = "themistra.crypto.providers")
@Validated
public record ProviderProperties(
        @NotEmpty @Valid List<ChainProviders> chains,
        @Min(1) int quorumThreshold
) {

    public ProviderProperties {
        // Null-guarded so an incomplete binding (chains/providers absent) falls through to the
        // @NotEmpty/@NotNull validation pass with a clean message, rather than an NPE here.
        if (chains != null) {
            for (ChainProviders chainProviders : chains) {
                List<ProviderEntry> providers = chainProviders == null ? null : chainProviders.providers();
                int providerCount = providers == null ? 0 : providers.size();
                if (providerCount < quorumThreshold) {
                    throw new IllegalStateException(
                            "themistra.crypto.providers.quorum-threshold (" + quorumThreshold
                                    + ") exceeds the number of configured providers (" + providerCount
                                    + ") for chain " + (chainProviders == null ? "?" : chainProviders.chain())
                                    + " - quorum could never be reached");
                }
            }
        }
    }

    public record ChainProviders(
            @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
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
