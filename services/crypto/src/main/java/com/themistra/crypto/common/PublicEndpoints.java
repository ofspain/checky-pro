package com.themistra.crypto.common;

/**
 * The exhaustive list of unauthenticated paths (agents.md Security rule; frozen brief AC4). A
 * sweep test asserts no {@code permitAll} exists outside this list. Deliberately narrower than
 * {@code services/auth}'s own {@code PublicEndpoints}: no method-scoped public writes exist on this
 * service, only actuator probes/scrape and the (not-yet-built, task 22) verification-keys
 * well-known path, which must stay public the moment it exists.
 */
public final class PublicEndpoints {

    public static final String[] PATTERNS = {
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/.well-known/themistra-verification-keys"
    };

    private PublicEndpoints() {
    }
}
