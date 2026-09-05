package com.themistra.crypto.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The signed, versioned canonical-token allowlist (L7), config-driven rather than a Flyway DML
 * migration (Phase 3 Kimi Issue 1) - agents.md's "Flyway, DDL-only migrations" rule forbids seeding
 * reference data via migration DML. {@link com.themistra.crypto.token.TokenAllowlistSeeder} reads
 * this at startup and idempotently upserts into {@code chain.token_allowlist}. Mirrors {@link
 * ProviderProperties}'s own already-proven nested-list shape.
 *
 * <p>{@code local} profile values are clearly-fake, syntactically-shaped placeholders - never real
 * mainnet contract addresses this codebase has no way to verify from memory, and never a real
 * signature (no signing key/algorithm is specified anywhere in this spec, L13/L7 scoping).</p>
 */
@ConfigurationProperties(prefix = "themistra.crypto.token-allowlist")
@Validated
public record TokenAllowlistProperties(@NotEmpty @Valid List<Entry> entries) {

    public TokenAllowlistProperties {
        // Phase 9 (Kimi Phase 8 Issue 6): a duplicate (chain, contractAddress, version) tuple would
        // otherwise be silently skipped by TokenAllowlistSeeder's own idempotent skip-if-exists logic
        // - config authors need a clear failure, not a silent drop of one entry's intended
        // symbol/decimals/signature in favor of whichever entry happened to seed first.
        if (entries != null) {
            Set<String> seen = new HashSet<>();
            for (Entry entry : entries) {
                if (entry == null) {
                    continue;
                }
                String key = entry.chain() + ":" + entry.contractAddress() + ":" + entry.version();
                if (!seen.add(key)) {
                    throw new IllegalStateException(
                            "Duplicate token-allowlist entry for chain=" + entry.chain()
                                    + " contractAddress=" + entry.contractAddress()
                                    + " version=" + entry.version());
                }
            }
        }
    }

    public record Entry(
            @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
            @NotBlank String contractAddress,
            @NotBlank String symbol,
            @Min(0) @Max(30) int decimals,
            @Positive int version,
            @NotBlank String signature) {
    }
}
