package com.themistra.notification.template;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB counterpart of {@link TemplateRendererTest} - proves the properties only a genuine
 * Postgres round-trip against the real seeded {@code V3} rows can: rendering the actual
 * `email.verify` content, versioned lookup against a second real row, and a non-blank,
 * trailing-slash {@code baseUrl} normalizing correctly (Kimi Phase 8 Finding #5) - a permanent
 * version of the deleted Phase 7 self-review scratch test.
 */
@Testcontainers
@SpringBootTest(properties = "themistra.notification.link.base-url=https://checky.pro/")
class TemplateRendererIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notification_app");
        registry.add("spring.datasource.password", () -> NOTIFICATION_APP_PASSWORD);
    }

    @BeforeAll
    static void migrateAndProvisionPassword() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS notifications");
            statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("notifications")
                .load()
                .migrate();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE notification_app PASSWORD '" + NOTIFICATION_APP_PASSWORD + "'");
        }
    }

    @Autowired
    private TemplateRenderer renderer;

    @Autowired
    private TemplateRepository repository;

    @Test
    void rendersTheRealSeededEmailVerifyTemplateWithATrailingSlashBaseUrlNormalized() {
        TemplateRenderer.RenderedMessage message = renderer.render("email.verify", "EMAIL",
                Map.of("displayName", "Ada", "token", "abc123"));

        assertThat(message.subject()).isEqualTo("Verify your Themistra account");
        assertThat(message.body()).contains("Hi Ada,");
        assertThat(message.body())
                .as("a single slash at the join point, not the double slash a raw trailing-slash baseUrl would produce")
                .contains("https://checky.pro/verify-email?token=abc123")
                .doesNotContain("checky.pro//verify-email");
        assertThat(message.version()).isEqualTo(1);
    }

    @Test
    void tokenWithReservedUrlCharactersIsUrlEncodedInTheComputedLink() {
        TemplateRenderer.RenderedMessage message = renderer.render("email.password_reset", "EMAIL",
                Map.of("displayName", "Ada", "token", "a&b=c d"));

        assertThat(message.body()).contains("token=a%26b%3Dc+d");
        assertThat(message.body()).doesNotContain("token=a&b=c d");
    }

    @Test
    void inAppChannelHasNoSubjectRow() {
        TemplateRenderer.RenderedMessage message = renderer.render("user.verify", "IN_APP",
                Map.of("token", "tok"));

        assertThat(message.subject()).isNull();
        assertThat(message.body()).contains("https://checky.pro/verify-email?token=tok");
    }

    /** Kimi Phase 8 Finding #4/AC2: a dedicated, non-seeded name so this test's own inserted second
     * version never interferes with any other test's own rendering of the real seeded rows (Phase
     * 7's own self-review already caught exactly this class of test-order bug once). */
    @Test
    void versionedLookupUsesTheHighestVersionWhenTwoExistForTheSamePair() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("INSERT INTO notifications.templates (name, channel, version, subject, body) "
                    + "VALUES ('it.versioned', 'EMAIL', 1, 'v1 subject', 'v1 body')");
            statement.execute("INSERT INTO notifications.templates (name, channel, version, subject, body) "
                    + "VALUES ('it.versioned', 'EMAIL', 2, 'v2 subject', 'v2 body {{displayName}}')");
        }

        TemplateRenderer.RenderedMessage message = renderer.render("it.versioned", "EMAIL",
                Map.of("displayName", "Ada"));

        assertThat(message.version()).isEqualTo(2);
        assertThat(message.subject()).isEqualTo("v2 subject");
        assertThat(message.body()).isEqualTo("v2 body Ada");
    }

    /** Kimi Phase 11 Gap #2: the only integration test exercising {@code getStartedLink} - every
     * other computed link is exercised by another test method, but none renders
     * {@code user.welcome}, the sole seeded template that references it. */
    @Test
    void userWelcomeRendersTheComputedGetStartedLink() {
        TemplateRenderer.RenderedMessage message = renderer.render("user.welcome", "EMAIL",
                Map.of("displayName", "Ada"));

        assertThat(message.body()).contains("https://checky.pro");
        assertThat(message.body()).doesNotContain("{{getStartedLink}}");
    }

    /** Kimi Phase 11 Gap #8: asserts {@link Template}'s own column mapping directly via the
     * repository, not only indirectly through {@link TemplateRenderer}'s own rendered output. */
    @Test
    void templateEntityMapsAllSixColumnsCorrectly() {
        Template template = repository.findTopByNameAndChannelOrderByVersionDesc("email.verify", "EMAIL")
                .orElseThrow();

        assertThat(template.getId()).isNotNull();
        assertThat(template.getName()).isEqualTo("email.verify");
        assertThat(template.getChannel()).isEqualTo("EMAIL");
        assertThat(template.getVersion()).isEqualTo(1);
        assertThat(template.getSubject()).isEqualTo("Verify your Themistra account");
        assertThat(template.getBody()).contains("{{displayName}}");
        assertThat(template.getCreatedAt()).isNotNull();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
