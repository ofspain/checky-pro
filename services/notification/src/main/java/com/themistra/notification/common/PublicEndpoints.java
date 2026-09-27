package com.themistra.notification.common;

/**
 * The exhaustive list of unauthenticated paths (L8, `agents.md` Security rule; frozen brief AC4). A
 * sweep test asserts no {@code permitAll} exists outside this list. Narrower than
 * {@code services/crypto}'s own {@code PublicEndpoints}: no internal/well-known path exists on this
 * service - only actuator health/info/prometheus.
 */
public final class PublicEndpoints {

    public static final String[] PATTERNS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus"
    };

    private PublicEndpoints() {
    }
}
