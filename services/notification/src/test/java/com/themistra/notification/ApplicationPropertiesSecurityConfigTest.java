package com.themistra.notification;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

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

    private static final Pattern LOOPBACK = Pattern.compile("localhost|127\\.0\\.0\\.1|\\[::1]");
    private static final Pattern ENV_OVERRIDABLE = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*:[^}]*}");

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

    /** FU-T27-2 (open-issues sweep, 2026-10-19): the JWKS location must be overridable per
     * environment. Hard-coded to localhost, a deployed container looked for auth-service's keys
     * on its own loopback, so every token would fail validation. */
    @Test
    void jwkSetUriIsOverridableByEnvironment() throws IOException {
        assertThat(loadApplicationProperties().getProperty("spring.security.oauth2.resourceserver.jwt.jwk-set-uri"))
                .isEqualTo("${AUTH_JWK_SET_URI:http://localhost:8080/oauth2/jwks}");
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
        assertThat(properties.getProperty("management.endpoint.health.show-details"))
                .as("Kimi Phase 11 Gap #2: internal detail must never leak to an unauthenticated caller")
                .isEqualTo("never");
    }

    @Test
    void activeProfileDefaultsToLocal() throws IOException {
        // Kimi Phase 11 Gap #1: the entire profile-conditional LinkProperties.baseUrl strategy
        // depends on this default - if it's ever deleted, a developer with no explicit profile set
        // would hit LinkPropertiesStartupValidation's own failure unexpectedly.
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("spring.profiles.active")).isEqualTo("local");
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

    /** Kimi sweep test review, Gap 9 (2026-10-20): FU-T27-2 fixed one loopback value, the JWKS
     * URI. No other value may point at a loopback address unless an environment variable can
     * replace it: in a container, loopback is the container itself. */
    @Test
    void everyLoopbackValueIsOverridableByEnvironment() throws IOException {
        Properties properties = loadApplicationProperties();
        List<String> loopbackKeys = properties.stringPropertyNames().stream().sorted()
                .filter(key -> LOOPBACK.matcher(properties.getProperty(key)).find())
                .toList();
        assertThat(loopbackKeys).as("the check sees the JWKS URI, so it is not vacuous")
                .contains("spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        assertThat(loopbackKeys.stream()
                .filter(key -> !ENV_OVERRIDABLE.matcher(properties.getProperty(key)).matches())
                .toList())
                .as("loopback values that no environment variable can replace")
                .isEmpty();
    }
}
