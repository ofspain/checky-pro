package com.themistra.crypto.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Which chains have finality checking enabled at launch. Finality itself is a per-chain
 * <b>policy object</b>, never a configurable global constant (design.md L4) — this class
 * deliberately holds only the enabled-chain list; no confirmation-count or threshold field belongs
 * here. Each chain's actual policy (beacon {@code finalized} checkpoint for Ethereum, solidified
 * block for Tron) is hardcoded in its {@code FinalityPolicy} implementation (design.md task 14).
 * Constrained to the launch scope fixed by design.md §2 (ETHEREUM/TRON) so a typo fails fast.
 */
@ConfigurationProperties(prefix = "themistra.crypto.finality")
@Validated
public record FinalityProperties(
        @NotEmpty List<@NotBlank @Pattern(regexp = "ETHEREUM|TRON") String> enabledChains
) {
}
