package com.themistra.crypto.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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

    public record Entry(
            @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
            @NotBlank String contractAddress,
            @NotBlank String symbol,
            @Min(0) int decimals,
            @Positive int version,
            @NotBlank String signature) {
    }
}
