package com.themistra.crypto.provider;

/**
 * Why a provider transitioned to unhealthy (R5), carried in the {@code chain.provider.degraded} event
 * payload only - {@code provider_health} has no column for it (Phase 3 Kimi Issue 3); the reason
 * remains queryable directly via {@code chain.outbox.payload} even before Kafka relay.
 */
public enum DegradationReason {
    UNHEALTHY,
    LAGGING,
    REPEATED_DISAGREEMENT
}
