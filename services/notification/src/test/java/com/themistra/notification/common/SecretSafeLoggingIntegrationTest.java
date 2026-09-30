package com.themistra.notification.common;

import com.themistra.notification.template.TemplateRenderer;
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
 * Kimi Phase 3 Finding #5 / Phase 8 Finding #5: the single most load-bearing scenario for
 * {@link SecretSafeLogging} - closes the loop between T09's real {@link TemplateRenderer} output
 * and this task's own redaction utility. A permanent version of the deleted Phase 7 self-review
 * scratch test.
 */
@Testcontainers
@SpringBootTest(properties = "themistra.notification.link.base-url=https://checky.pro")
class SecretSafeLoggingIntegrationTest {

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

    @Test
    void redactsTheRealTokenFromARealRenderedPasswordResetBody() {
        TemplateRenderer.RenderedMessage message = renderer.render("email.password_reset", "EMAIL",
                Map.of("displayName", "Ada", "token", "super-secret-reset-token-xyz"));
        assertThat(message.body())
                .as("sanity check: the fixture itself must actually embed the raw token before redaction")
                .contains("super-secret-reset-token-xyz");

        String redacted = SecretSafeLogging.redact(message.body());

        assertThat(redacted).doesNotContain("super-secret-reset-token-xyz");
        assertThat(redacted).contains("token=***");
    }

    @Test
    void redactsTheRealTokenFromARealRenderedVerificationBody() {
        TemplateRenderer.RenderedMessage message = renderer.render("email.verify", "EMAIL",
                Map.of("displayName", "Ada", "token", "another-real-secret-token"));

        String redacted = SecretSafeLogging.redact(message.body());

        assertThat(redacted).doesNotContain("another-real-secret-token");
        assertThat(redacted).contains("token=***");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
