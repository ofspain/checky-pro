package com.themistra.payment.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 finding 1: {@code PublicEndpoints.PATTERNS} is exactly the 3 declared paths, and
 * nothing else is {@code permitAll}. Mirrors {@code notification-service}'s own
 * {@code PublicEndpointsTest} exactly. The positive cases may still 404 (no real actuator
 * handler is registered in this {@code @WebMvcTest} slice) - the assertion is exclusively about
 * the security layer not blocking with 401/403, not about handler presence.
 */
@WebMvcTest(controllers = ResourceServerTestController.class)
@Import(ResourceServerConfig.class)
class PublicEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void patternsListExactlyTheThreeDeclaredPaths() {
        assertThat(PublicEndpoints.PATTERNS).containsExactlyInAnyOrder(
                "/actuator/health/**",
                "/actuator/info",
                "/actuator/prometheus");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info",
            "/actuator/prometheus"
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
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void nonPublicApplicationPathIsNotPublic() throws Exception {
        mockMvc.perform(get("/v1/invoices/test-only")).andExpect(status().isUnauthorized());
    }

    /** A future edit adding a second {@code .permitAll()} call anywhere in
     * {@code ResourceServerConfig} (e.g. for a new, forgotten-to-restrict path) fails this test
     * even if that new path is never added to the behavioral test lists above. */
    @Test
    void resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/payment/common/ResourceServerConfig.java"));
        List<String> permitAllLines = source.lines()
                .filter(line -> line.contains(".permitAll()"))
                .toList();

        assertThat(permitAllLines)
                .as("exactly one permitAll() call is allowed, guarding PublicEndpoints.PATTERNS only")
                .hasSize(1);
        assertThat(permitAllLines.get(0))
                .as("the one permitAll() call must be scoped to PublicEndpoints.PATTERNS")
                .contains("PublicEndpoints.PATTERNS");
    }
}
