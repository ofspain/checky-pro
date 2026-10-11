package com.themistra.payment.common;

/**
 * The exhaustive list of unauthenticated paths (agents.md Security rule; frozen brief AC3). A
 * sweep test asserts no {@code permitAll} exists outside this list. Narrower than {@code
 * services/crypto}'s own {@code PublicEndpoints}: no well-known key-publishing path exists on
 * this service — only actuator probes/scrape.
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
