package com.themistra.auth.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimi Phase 11 Gap 8 (T07): L11 requires both new password-reset paths registered in
 * {@link PublicEndpoints#METHOD_SCOPED} — a future accidental removal must fail CI, not just be
 * caught by re-reading the source.
 */
class PublicEndpointsTest {

    @Test
    void methodScopedContainsBothPasswordResetEndpoints() {
        assertThat(PublicEndpoints.METHOD_SCOPED)
                .contains(new PublicEndpoints.MethodScoped(HttpMethod.POST, "/accounts/password-reset-request"))
                .contains(new PublicEndpoints.MethodScoped(HttpMethod.POST, "/accounts/password-reset"));
    }
}
