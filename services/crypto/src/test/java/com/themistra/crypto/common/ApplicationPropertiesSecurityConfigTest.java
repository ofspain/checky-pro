package com.themistra.crypto.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gaps 4/8: {@link PublicEndpointsTest}'s positive cases legitimately 404 (no real
 * actuator handler in a {@code @WebMvcTest} slice), and {@link ResourceServerConfigIntegrationTest}
 * never constructs a real {@code JwtDecoder} ({@code .with(jwt())} bypasses it) - so neither test
 * would catch a typo'd or deleted property key in the committed {@code application.properties}.
 * This reads the actual file from the classpath and asserts the specific keys Phase 9 added are
 * still present with the expected values, closing that specific gap cheaply (no Spring context,
 * no Docker).
 */
class ApplicationPropertiesSecurityConfigTest {

    private Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(in).as("application.properties must be on the test classpath").isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    void declaresJwkSetUriAndIssuerUri() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                .as("Phase 9 Finding: required for JwtDecoder autoconfiguration")
                .isNotBlank();
        assertThat(properties.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .as("Phase 9 Finding 3: required for JwtIssuerValidator to be registered")
                .isNotBlank();
    }

    @Test
    void exposesExactlyHealthInfoAndPrometheusOverActuator() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .as("Phase 9 Finding 1: without this, /actuator/info and /actuator/prometheus 404")
                .isEqualTo("health,info,prometheus");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .as("Phase 9 Finding 1: without this, /actuator/health/liveness|readiness 404 outside k8s")
                .isEqualTo("true");
    }
}
