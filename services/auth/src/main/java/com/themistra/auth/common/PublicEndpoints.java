package com.themistra.auth.common;

/**
 * The exhaustive list of unauthenticated paths (target-design §4). The Testing stage adds a
 * sweep test asserting no permitAll exists outside this list — the reference project shipped a
 * "testing only" whitelist exposing role administration; this constant is that lesson, enforced.
 *
 * SAS protocol endpoints (/oauth2/*, /.well-known/*, /login) are governed by their own filter
 * chain and are intentionally not listed here.
 */
public final class PublicEndpoints {

    public static final String[] PATTERNS = {
            "/actuator/health/**",   // K8s probes
            "/actuator/info",
            "/actuator/prometheus"   // in-cluster scrape only; NetworkPolicy restricts callers
    };

    private PublicEndpoints() {
    }
}
