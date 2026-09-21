package com.themistra.crypto.quorum;

import java.util.Objects;

/**
 * One provider's answer for a single fact, paired with the provider's identity. {@link
 * QuorumDecisionService} uses the identity to reject duplicate-provider input (Phase 3 Kimi Issue 6)
 * and to give {@link HeldFactAlerter} enough detail to name which providers disagreed and with what
 * values; {@link QuorumEvaluator} itself never sees this type, only the extracted values, since it
 * has no need to know provider identity for its own pure comparison logic.
 */
public record ProviderAnswer<T>(String provider, T value) {

    public ProviderAnswer {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(value, "value");
    }
}
