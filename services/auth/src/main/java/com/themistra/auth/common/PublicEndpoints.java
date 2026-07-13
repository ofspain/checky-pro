package com.themistra.auth.common;

import org.springframework.http.HttpMethod;

import java.util.List;

/**
 * The exhaustive list of unauthenticated paths (target-design §4). A future sweep test asserts
 * no permitAll exists outside this list — the reference project shipped a "testing only"
 * whitelist exposing role administration; this constant is that lesson, enforced.
 *
 * SAS protocol endpoints (/oauth2/*, /.well-known/*, /login) are governed by their own filter
 * chain and are intentionally not listed here.
 */
public final class PublicEndpoints {

    /** Any-method public paths — safe because nothing sensitive lives at these exact paths. */
    public static final String[] PATTERNS = {
            "/actuator/health/**",   // K8s probes
            "/actuator/info",
            "/actuator/prometheus"   // in-cluster scrape only; NetworkPolicy restricts callers
    };

    /**
     * Method-scoped public endpoints — used where only one HTTP method on a path is safe to
     * expose (e.g. self-registration is POST-only; nothing else on {@code /accounts} is public).
     */
    public static final List<MethodScoped> METHOD_SCOPED = List.of(
            new MethodScoped(HttpMethod.POST, "/accounts")   // self-registration (enumeration-safe response)
    );

    private PublicEndpoints() {
    }

    public record MethodScoped(HttpMethod method, String pattern) {
    }
}
