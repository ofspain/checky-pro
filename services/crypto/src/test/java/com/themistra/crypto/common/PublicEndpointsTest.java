package com.themistra.crypto.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AC4 — {@code PublicEndpoints.PATTERNS} is exactly the 4 declared paths, and nothing else is
 * {@code permitAll}. The positive cases below may still 404 (no real actuator/well-known handler
 * is registered in this {@code @WebMvcTest} slice) - the assertion is exclusively about the
 * security layer not blocking with 401/403, not about handler presence.
 */
@WebMvcTest(controllers = InternalTestController.class)
@Import(ResourceServerConfig.class)
class PublicEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void patternsListExactlyTheFourDeclaredPaths() {
        assertThat(PublicEndpoints.PATTERNS).containsExactlyInAnyOrder(
                "/actuator/health/**",
                "/actuator/info",
                "/actuator/prometheus",
                "/.well-known/themistra-verification-keys");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus",
            "/.well-known/themistra-verification-keys"
    })
    void declaredPublicPathsAreNotBlockedBySecurity(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();
        assertThat(result.getResponse().getStatus())
                .as("security layer must not block %s with 401/403", path)
                .isNotIn(401, 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/env",
            "/actuator/beans",
            "/actuator/configprops",
            "/actuator/loggers",
            "/actuator/heapdump",
            "/actuator/threaddump"
    })
    void sensitiveActuatorPathsAreNotPublic(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
}
