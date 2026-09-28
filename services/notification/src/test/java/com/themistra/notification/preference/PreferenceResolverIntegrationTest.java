package com.themistra.notification.preference;

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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real-DB counterpart of {@link PreferenceResolverTest} - proves the properties only a genuine
 * Postgres round-trip can: a row stored as uppercase is actually matched by a case-normalized
 * lowercase query (Kimi Phase 8 Finding #5), and the {@code SECURITY}+{@code EMAIL} hard floor
 * holds against a real, adversarial stored row (Kimi Phase 8 Finding #2) - a permanent version of
 * the deleted Phase 7 self-review scratch test.
 */
@Testcontainers
@SpringBootTest
class PreferenceResolverIntegrationTest {

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
    private PreferenceResolver resolver;

    @Autowired
    private ChannelPreferenceRepository repository;

    private static void insertRow(UUID accountUuid, String category, String channel, boolean enabled) throws SQLException {
        try (Connection admin = adminConnection();
             PreparedStatement insert = admin.prepareStatement(
                     "INSERT INTO notifications.channel_preferences (account_uuid, category, channel, enabled) "
                             + "VALUES (?::uuid, ?, ?, ?)")) {
            insert.setString(1, accountUuid.toString());
            insert.setString(2, category);
            insert.setString(3, channel);
            insert.setBoolean(4, enabled);
            insert.execute();
        }
    }

    @Test
    void storedRowTakesPrecedenceOverTheDocumentedDefault() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        insertRow(accountUuid, "PAYMENT", "EMAIL", false);

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "EMAIL")).isFalse();
    }

    @Test
    void noRowFallsBackToTheDocumentedDefault() {
        UUID accountUuid = UUID.randomUUID();

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "EMAIL")).isTrue();
        assertThat(resolver.resolve(accountUuid, "MARKETING", "IN_APP")).isFalse();
    }

    /** Kimi Phase 8 Finding #2: the hard floor holds against a real, adversarial stored row - not
     * merely the "no row exists" case the default table alone would cover. */
    @Test
    void securityEmailIsTrueEvenWithARealStoredFalseRow() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        insertRow(accountUuid, "SECURITY", "EMAIL", false);

        assertThat(resolver.resolve(accountUuid, "SECURITY", "EMAIL")).isTrue();
    }

    @Test
    void webhookAndPushResolveFalseWithNoRow() {
        UUID accountUuid = UUID.randomUUID();

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "WEBHOOK")).isFalse();
        assertThat(resolver.resolve(accountUuid, "SECURITY", "PUSH")).isFalse();
        assertThat(resolver.resolve(accountUuid, "MARKETING", "WEBHOOK")).isFalse();
    }

    @Test
    void unknownCategoryResolvesFalseWithNoRow() {
        UUID accountUuid = UUID.randomUUID();

        assertThat(resolver.resolve(accountUuid, "COMPLIANCE", "EMAIL")).isFalse();
    }

    /** Kimi Phase 8 Finding #5: proves a row stored as uppercase (the DB's own real value space) is
     * actually matched by a lowercase query argument - a unit test with a mocked repository can
     * only prove the argument passed to the repository was uppercased, not that a real row is
     * actually found by it. */
    @Test
    void lowercaseQueryArgumentsMatchAnUppercaseStoredRow() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        insertRow(accountUuid, "PAYMENT", "EMAIL", false);

        assertThat(resolver.resolve(accountUuid, "payment", "email")).isFalse();
    }

    /** Kimi Phase 11 Gap #3: "stored row takes precedence" is not conditional on the pair being one
     * of the 6 documented defaults - a stored row on an unsupported channel still wins over its own
     * missing default, exactly like any other pair. */
    @Test
    void storedRowOnAnUnsupportedChannelTakesPrecedenceOverItsMissingDefault() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        insertRow(accountUuid, "PAYMENT", "WEBHOOK", true);

        assertThat(resolver.resolve(accountUuid, "PAYMENT", "WEBHOOK")).isTrue();
    }

    /** Kimi Phase 11 Gap #5: asserts {@link ChannelPreference}'s own column mapping directly via
     * the repository, not only indirectly through {@link PreferenceResolver}'s own boolean return -
     * a `@Column` name drift on `category`/`channel`/`updatedAt` would otherwise only surface as a
     * confusing resolver-level failure. */
    @Test
    void channelPreferenceEntityMapsAllSixColumnsCorrectly() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        insertRow(accountUuid, "PAYMENT", "IN_APP", true);

        ChannelPreference stored = repository
                .findByAccountUuidAndCategoryAndChannel(accountUuid, "PAYMENT", "IN_APP")
                .orElseThrow();

        assertThat(stored.getId()).isNotNull();
        assertThat(stored.getAccountUuid()).isEqualTo(accountUuid);
        assertThat(stored.getCategory()).isEqualTo("PAYMENT");
        assertThat(stored.getChannel()).isEqualTo("IN_APP");
        assertThat(stored.isEnabled()).isTrue();
        assertThat(stored.getUpdatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
