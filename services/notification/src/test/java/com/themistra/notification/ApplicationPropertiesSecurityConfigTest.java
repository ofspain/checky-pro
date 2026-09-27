package com.themistra.notification;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC2 (Kimi Phase 8 Finding #5) - {@link com.themistra.notification.common.PublicEndpointsTest} and
 * {@link com.themistra.notification.common.ResourceServerConfigIntegrationTest} never construct a
 * real {@code JwtDecoder} ({@code .with(jwt())} bypasses it), so neither would catch a typo'd or
 * deleted resource-server/actuator key in the real committed {@code application.properties}. Reads
 * the actual file from the classpath directly - no Spring context, no Docker. Mirrors
 * {@code services/crypto}'s own {@code ApplicationPropertiesSecurityConfigTest}.
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
                .as("required for JwtDecoder autoconfiguration")
                .isNotBlank();
        assertThat(properties.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .as("required for JwtIssuerValidator to be registered")
                .isNotBlank();
    }

    @Test
    void exposesExactlyHealthInfoAndPrometheusOverActuator() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .as("without this, /actuator/info and /actuator/prometheus 404")
                .isEqualTo("health,info,prometheus");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .as("without this, /actuator/health/liveness|readiness 404 outside k8s")
                .isEqualTo("true");
    }

    @Test
    void declaresAllFourNotificationConfigGroupKeys() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("themistra.notification.email.from")).isNotBlank();
        assertThat(properties.getProperty("themistra.notification.email.transport")).isNotBlank();
        assertThat(properties.getProperty("themistra.notification.link.base-url"))
                .as("blank is the correct local-profile default - presence, not non-blankness, is asserted here")
                .isNotNull();
        assertThat(properties.getProperty("themistra.notification.retry.max-attempts")).isNotBlank();
        assertThat(properties.getProperty("themistra.notification.retry.initial-backoff-seconds")).isNotBlank();
        assertThat(properties.getProperty("themistra.notification.retry.max-backoff-seconds")).isNotBlank();
        assertThat(properties.getProperty("themistra.notification.inapp.transport")).isNotBlank();
    }

    @Test
    void declaresAFreeServerPortAwayFromAuthServices8080() throws IOException {
        // Kimi Phase 8 Finding #6.
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("server.port"))
                .as("must not collide with services/auth's own server.port=8080")
                .isNotBlank()
                .isNotEqualTo("8080");
    }
}
