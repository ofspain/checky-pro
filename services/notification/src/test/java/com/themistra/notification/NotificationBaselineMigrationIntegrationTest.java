package com.themistra.notification;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T02 — real Testcontainers proof that V1/V2/V3 do what the frozen brief's AC1-AC5 claim, not just
 * that the SQL text looks right. Runs all three migrations via the Flyway Java API (no
 * application.properties exists yet - task 3's own scope - so there is no Spring-context
 * auto-migration path to piggyback on or disable here) against a real Postgres 16 container, then
 * connects as notification_app over real TCP/JDBC. Mirrors
 * {@code services/crypto}'s own {@code ChainBaselineMigrationIntegrationTest} structure exactly.
 */
@Testcontainers
class NotificationBaselineMigrationIntegrationTest {

    private static final List<String> GRANTED_TABLES = List.of("delivery_log");
    // T02's own literal scope grants only delivery_log. The other six baseline tables and shedlock
    // each get their own grant migration in the task that first needs runtime access to them
    // (mirroring crypto-service's own incremental-grant pattern) - all seven are therefore expected
    // to remain fully inaccessible to notification_app as of this task.
    private static final List<String> UNGRANTED_TABLES = List.of("contact_projection",
            "channel_preferences", "templates", "processed_events", "inapp_notifications",
            "delivery_retry", "shedlock");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    /** Kimi Phase 3 Finding #1, corrected during implementation: this Testcontainers instance is a
     * fresh Postgres 16 container, isolated from the shared local-dev Postgres - {@code citext}
     * isn't enabled here by default regardless of whether auth's own migrations have run anywhere
     * else. The originally-planned fix (install {@code citext} into {@code public} before migrating)
     * was insufficient and proven wrong by actually running this test: V1's own VERBATIM
     * {@code SET search_path TO notifications;} line replaces the search path entirely rather than
     * appending to it, so a {@code public}-schema extension type becomes invisible the moment that
     * line executes - unlike auth's own {@code V1__auth_baseline_schema.sql}, which has no
     * {@code SET search_path} at all and so never hits this. Since V1 is immutable VERBATIM, the fix
     * has to live entirely in this setup step: pre-create the {@code notifications} schema (a
     * harmless no-op once V1's own idempotent {@code CREATE SCHEMA IF NOT EXISTS} runs) and install
     * {@code citext} directly into it, so it is already visible the moment search_path narrows to
     * that one schema. This is a real defect notification's own real local {@code flyway:migrate}
     * attempt would hit too, not just this test - flagged in Phase 6's own implementation notes. */
    @BeforeAll
    static void migrateAndProvisionLocalPassword() throws SQLException {
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

    @Test
    void allEightBaselineTablesExistAndNoOthers() throws SQLException {
        List<String> expected = List.of("contact_projection", "channel_preferences", "templates",
                "processed_events", "delivery_log", "inapp_notifications", "delivery_retry", "shedlock");

        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = 'notifications' AND table_name != 'flyway_schema_history'")) {
            List<String> actual = new ArrayList<>();
            while (resultSet.next()) {
                actual.add(resultSet.getString(1));
            }
            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    /** AC1's own "byte-for-byte" claim, automated: the strongest possible regression guard for the
     * verbatim artifact, stronger than any column/constraint introspection could be. */
    @Test
    void v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock() throws IOException {
        Path designDoc = Path.of("../../spec/notification-service/design.md");
        Path v1Migration = Path.of("src/main/resources/db/migration/V1__notifications_baseline.sql");

        String verbatimBlock = extractFirstSqlFence(Files.readString(designDoc));
        String migrationContent = Files.readString(v1Migration);

        assertThat(migrationContent).isEqualTo(verbatimBlock);
    }

    private static String extractFirstSqlFence(String designDocContent) {
        String[] lines = designDocContent.split("\n", -1);
        StringBuilder block = new StringBuilder();
        boolean inFence = false;
        for (String line : lines) {
            if (!inFence && line.equals("```sql")) {
                inFence = true;
                continue;
            }
            if (inFence && line.equals("```")) {
                break;
            }
            if (inFence) {
                block.append(line).append('\n');
            }
        }
        return block.toString();
    }

    @Test
    void allMigrationsAreRecordedAsSuccessfulInFlywayHistory() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, success FROM notifications.flyway_schema_history "
                             + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            List<String> succeededVersions = new ArrayList<>();
            while (resultSet.next()) {
                assertThat(resultSet.getBoolean("success")).as("version %s must have succeeded", resultSet.getString("version")).isTrue();
                succeededVersions.add(resultSet.getString("version"));
            }
            assertThat(succeededVersions).containsExactly("1", "2", "3");
        }
    }

    @Test
    void v2RoleCreationGuardIsIdempotentUnderARealReRun() {
        assertThatCode(() -> Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("notifications")
                .load()
                .migrate())
                .doesNotThrowAnyException();
    }

    @Test
    void notificationAppRoleRequiresItsProvisionedPassword() {
        assertThatThrownBy(() -> connectAsNotificationApp("definitely-wrong-password"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("password authentication failed");

        assertThatCode(() -> connectAsNotificationApp(NOTIFICATION_APP_PASSWORD).close())
                .doesNotThrowAnyException();
    }

    @Test
    void notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnDeliveryLog() throws SQLException {
        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD)) {
            for (String table : GRANTED_TABLES) {
                assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app, table);
            }
        }
    }

    @Test
    void notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope() throws SQLException {
        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD); Statement statement = app.createStatement()) {
            for (String table : UNGRANTED_TABLES) {
                assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM notifications." + table))
                        .as("SELECT on %s must be denied for notification_app (not AC2's one named table)", table)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    void notificationAppCannotPerformDdlInTheNotificationsSchema() throws SQLException {
        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD); Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE notifications.evil_test(id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute("DROP TABLE notifications.delivery_log"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("must be owner of table delivery_log");
        }
    }

    @Test
    void baselineTablesAreOwnedByTheMigrationRoleNeverByNotificationApp() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'notifications'")) {
            while (resultSet.next()) {
                assertThat(resultSet.getString("tableowner"))
                        .as("notifications.%s must not be owned by notification_app", resultSet.getString("tablename"))
                        .isNotEqualTo("notification_app");
            }
        }
    }

    /** Kimi Phase 3 Finding #4, corrected at Phase 5 (own-work verification): 14 rows across 7
     * mappings, but 2 of those mappings use distinct EMAIL/IN_APP names
     * (email.verify/user.verify, email.password_reset/user.password_reset) rather than sharing one -
     * the real distinct-name count is 9, not the 7 Kimi's own finding suggested (which was
     * inconsistent with its own Finding #3 naming table). Content (subject/body) is deliberately not
     * asserted here, since it's disclosed as provisional pending O6 (the template engine, task 9). */
    @Test
    void launchTemplatesAreSeededWithVersionOne() throws SQLException {
        Set<String> expectedNameChannelPairs = Set.of(
                "email.verify:EMAIL", "user.verify:IN_APP",
                "email.password_reset:EMAIL", "user.password_reset:IN_APP",
                "user.welcome:EMAIL", "user.welcome:IN_APP",
                "invoice.created:EMAIL", "invoice.created:IN_APP",
                "payment.seen:EMAIL", "payment.seen:IN_APP",
                "payment.finalized:EMAIL", "payment.finalized:IN_APP",
                "receipt.issued:EMAIL", "receipt.issued:IN_APP");

        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT name, channel, version FROM notifications.templates")) {
            List<String> actualPairs = new ArrayList<>();
            Set<String> distinctNames = new HashSet<>();
            int rowCount = 0;
            while (resultSet.next()) {
                rowCount++;
                assertThat(resultSet.getInt("version")).as("every seeded template must be version 1").isEqualTo(1);
                actualPairs.add(resultSet.getString("name") + ":" + resultSet.getString("channel"));
                distinctNames.add(resultSet.getString("name"));
            }
            assertThat(rowCount).as("14 rows: 7 mappings x 2 channels").isEqualTo(14);
            assertThat(distinctNames)
                    .as("9 distinct names - 5 mappings share one name across channels, 2 use a distinct name per channel")
                    .hasSize(9);
            assertThat(actualPairs).as("exact (name, channel) pairs")
                    .containsExactlyInAnyOrderElementsOf(expectedNameChannelPairs);
        }
    }

    private void assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(Connection app, String table) throws SQLException {
        String sourceEventKey = "it-" + table;
        try (Statement statement = app.createStatement()) {
            statement.execute(insertStatementFor(table, sourceEventKey));

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT count(*) FROM notifications." + table + " WHERE source_event_key = '" + sourceEventKey + "'")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).as("SELECT on %s must see the row notification_app just inserted", table).isEqualTo(1);
            }

            assertThatThrownBy(() -> statement.execute("UPDATE notifications." + table + " SET outcome = 'FAILED' WHERE source_event_key = '" + sourceEventKey + "'"))
                    .as("UPDATE on %s must be denied for notification_app", table)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute("DELETE FROM notifications." + table + " WHERE source_event_key = '" + sourceEventKey + "'"))
                    .as("DELETE on %s must be denied for notification_app", table)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        cleanUpAsAdmin(table, sourceEventKey);
    }

    private static String insertStatementFor(String table, String sourceEventKey) {
        return switch (table) {
            case "delivery_log" -> "INSERT INTO notifications.delivery_log (channel, source_event_key, outcome) "
                    + "VALUES ('EMAIL', '" + sourceEventKey + "', 'SENT')";
            default -> throw new IllegalArgumentException("no INSERT fixture for " + table);
        };
    }

    private void cleanUpAsAdmin(String table, String sourceEventKey) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM notifications." + table + " WHERE source_event_key = '" + sourceEventKey + "'");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsNotificationApp(String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "notification_app", password);
    }
}
