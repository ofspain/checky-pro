package com.themistra.payment.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T03 — permanent file-content guards for this task's own AC2, AC6, AC7, AC8 (Phase 3 findings
 * 4, 5, 7, 8), which would otherwise only be checked by file reads during the review phases.
 * Plain JUnit, no Spring context, mirroring T01's own skeleton-test style.
 */
class ApplicationPropertiesContentTest {

    private static final Pattern LOOPBACK = Pattern.compile("localhost|127\\.0\\.0\\.1|\\[::1]");
    private static final Pattern ENV_OVERRIDABLE = Pattern.compile("\\$\\{[A-Z][A-Z0-9_]*:[^}]*}");

    private static final Path APPLICATION_PROPERTIES = Path.of("src/main/resources/application.properties");
    private static final Path APPLICATION_SOURCE =
            Path.of("src/main/java/com/themistra/payment/PaymentServiceApplication.java");

    /** AC3 (Phase 8 finding 3): the resource-server JWT properties are present. The @WebMvcTest
     * security slice (ResourceServerConfigTest/PublicEndpointsTest) never loads a real
     * JwtDecoder, so these two properties could be removed without breaking any behavioral test
     * even though AC3 depends on them - a source-text guard closes that gap. */
    @Test
    void resourceServerJwtPropertiesArePresent() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);
        assertThat(properties).contains("spring.security.oauth2.resourceserver.jwt.jwk-set-uri=");
        assertThat(properties).contains("spring.security.oauth2.resourceserver.jwt.issuer-uri=");
    }

    /** FU-T27-2 (open-issues sweep, 2026-10-19): the JWKS location must be overridable per
     * environment. Hard-coded to localhost, a deployed container looked for auth-service's keys
     * on its own loopback, so every token would fail validation. */
    @Test
    void jwkSetUriIsOverridableByEnvironment() throws IOException {
        assertThat(Files.readString(APPLICATION_PROPERTIES)).contains(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${AUTH_JWK_SET_URI:http://localhost:8080/oauth2/jwks}");
    }

    /** AC2: PaymentServiceApplication carries @ConfigurationPropertiesScan - also covered by
     * T01SkeletonRegressionTest's own updated assertion; kept here too since it is this task's
     * own acceptance criterion, not just a T01 regression guard. */
    @Test
    void applicationClassHasConfigurationPropertiesScan() throws IOException {
        String source = Files.readString(APPLICATION_SOURCE);
        assertThat(source).contains("@ConfigurationPropertiesScan");
    }

    /** AC6: actuator exposure, health probes, and show-details=never are all set - without the
     * exposure line, PublicEndpoints' own allowlisted paths 404 regardless of security config
     * (named lesson, confirmed in every sibling service's own application.properties). */
    @Test
    void actuatorExposureProbesAndShowDetailsAreConfigured() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);

        assertThat(properties).contains("management.endpoints.web.exposure.include=health,info,prometheus");
        assertThat(properties).contains("management.endpoint.health.probes.enabled=true");
        assertThat(properties).as("Phase 3 finding 4").contains("management.endpoint.health.show-details=never");
    }

    /** AC7 (Phase 3 finding 5): Kafka consumer group-id is set now, even though the first real
     * listener lands in a later task - an unset group-id falls back to a generated id that
     * changes across restarts, defeating consumer-group semantics. */
    @Test
    void kafkaConsumerGroupIdIsSet() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);
        assertThat(properties).contains("spring.kafka.consumer.group-id=payment-service");
    }

    /** AC8 (Phase 3 finding 8): Hikari pool settings and the search_path init SQL are set now,
     * even though no entity exists yet - omitting connection-init-sql would cause every later
     * JPA query to fail once entities land, since nothing else sets the schema search path. */
    @Test
    void datasourceHikariPoolAndSearchPathAreConfigured() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);

        assertThat(properties).contains("spring.datasource.hikari.maximum-pool-size=10");
        assertThat(properties).contains("spring.datasource.hikari.connection-timeout=5000");
        assertThat(properties).contains("spring.datasource.hikari.connection-init-sql=SET search_path TO payments, public");
        assertThat(properties).as("L13/T02 - the app must never connect as the schema-owning admin role")
                .contains("spring.datasource.username=${DB_USERNAME:payment_app}");
    }

    /** Phase 3 finding 7: no credential string beyond the documented datasource-password
     * carve-out (the same local-dev-only placeholder pattern T01/T02 already established), and no
     * YAML file exists anywhere under src/main/resources (agents.md: flat application.properties
     * only, never YAML). */
    @Test
    void noUndocumentedSecretAndNoYamlFileExists() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);

        long passwordLines = properties.lines().filter(line -> line.contains("password")).count();
        assertThat(passwordLines)
                .as("exactly one password-shaped line - the documented local-dev datasource carve-out")
                .isEqualTo(1);
        assertThat(properties).contains("spring.datasource.password=${DB_PASSWORD:payment-app-local-only}");

        // T06 (Phase 9, Kimi Phase 8 finding 2): client-secret is a second, separately-shaped
        // credential carve-out the original check (password-only) never covered - closes that
        // gap rather than widening the generic doesNotContainPattern check below, which must
        // stay a real catch-all for anything NOT already an accounted-for carve-out.
        long clientSecretLines = properties.lines().filter(line -> line.contains("client-secret")).count();
        assertThat(clientSecretLines)
                .as("exactly one client-secret-shaped line - the documented local-dev Crypto Service auth carve-out")
                .isEqualTo(1);
        assertThat(properties).contains(
                "themistra.payment.crypto.auth.client-secret=${PAYMENT_SERVICE_CLIENT_SECRET:payment-local-only}");

        assertThat(properties).doesNotContainPattern("(?i)<secret>|apiKey=|accessKey=|accessToken=");

        try (var files = Files.walk(Path.of("src/main/resources"))) {
            List<Path> yamlFiles = files
                    .filter(path -> Pattern.compile("\\.ya?ml$").matcher(path.toString()).find())
                    .toList();
            assertThat(yamlFiles).as("agents.md: flat application.properties only, never YAML").isEmpty();
        }
    }

    /** AC3 companion: server.port claims 8083, the next free value after auth's 8080 and
     * notification's already-claimed 8082 (Phase 1 resolution). */
    @Test
    void serverPortIsClaimedAndDoesNotCollideWithKnownSiblingPorts() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);
        assertThat(properties).contains("server.port=${SERVER_PORT:8083}");
    }

    /** T04 AC6 (Phase 8 finding 2; tightened at Phase 11 Gap 2): the outbox relay's own polling
     * interval must stay present with its exact env-var/default shape - without it, OutboxRelay's
     * @Scheduled(fixedDelayString = ...) falls back to the hardcoded literal default baked into
     * the annotation itself, silently decoupling runtime behavior from this file. A bare
     * `contains("...=")` check (as originally written) would pass even if the default value were
     * changed or the env-var override removed; asserting the full value closes that gap. */
    @Test
    void outboxRelayIntervalIsConfigured() throws IOException {
        String properties = Files.readString(APPLICATION_PROPERTIES);
        assertThat(properties)
                .contains("themistra.payment.outbox.relay-interval-ms=${OUTBOX_RELAY_INTERVAL_MS:2000}");
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

    private static Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(APPLICATION_PROPERTIES)) {
            properties.load(reader);
        }
        return properties;
    }
}
