package com.themistra.crypto.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

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
                .as("Phase 9 Finding: required for JwtDecoder autoconfiguration")
                .isNotBlank();
        assertThat(properties.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"))
                .as("Phase 9 Finding 3: required for JwtIssuerValidator to be registered")
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
                .as("Phase 9 Finding 1: without this, /actuator/info and /actuator/prometheus 404")
                .isEqualTo("health,info,prometheus");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .as("Phase 9 Finding 1: without this, /actuator/health/liveness|readiness 404 outside k8s")
                .isEqualTo("true");
    }

    /** Kimi sweep test review, Gap 9 (2026-10-20): FU-T27-2 fixed one loopback value, the JWKS
     * URI. No other value may point at a loopback address unless an environment variable can
     * replace it: in a container, loopback is the container itself.
     *
     * <p>One exemption: the chain provider URLs are placeholders for fake local providers
     * (package.md Q1, the real set is unresolved). A deployment supplies its own list. The exemption
     * holds only while the provider's name starts with {@code fake-}, so a real provider on
     * loopback is not exempt.</p> */
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
                .filter(key -> !isFakeLocalProviderUrl(properties, key))
                .toList())
                .as("loopback values that no environment variable can replace")
                .isEmpty();
    }

    private static final Pattern PROVIDER_URL_KEY =
            Pattern.compile("(themistra\\.crypto\\.providers\\.chains\\[\\d+]\\.providers\\[\\d+])\\.url");

    private static boolean isFakeLocalProviderUrl(Properties properties, String key) {
        java.util.regex.Matcher matcher = PROVIDER_URL_KEY.matcher(key);
        return matcher.matches()
                && properties.getProperty(matcher.group(1) + ".name", "").startsWith("fake-");
    }
}
