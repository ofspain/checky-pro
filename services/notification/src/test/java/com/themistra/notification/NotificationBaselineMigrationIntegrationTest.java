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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    // T02's own literal scope grants only delivery_log; T04's own V4 additionally grants
    // processed_events, T05's own V5 grants contact_projection, and T08's own V6 grants
    // channel_preferences (all three tested separately below - none fit
    // assertInsertAndSelectSucceedUpdateAndDeleteAreDenied's own delivery_log-shaped assumptions,
    // and channel_preferences' own grant is SELECT-only, unlike the other two). The remaining three
    // baseline tables and shedlock each get their own grant migration in the task that first needs
    // runtime access to them (mirroring crypto-service's own incremental-grant pattern) - they are
    // therefore expected to remain fully inaccessible to notification_app as of this task.
    private static final List<String> UNGRANTED_TABLES = List.of(
            "templates", "inapp_notifications", "delivery_retry", "shedlock");

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

            // Kimi Phase 11 Gap #5: confirms the pre-step actually landed citext where V1's own
            // narrowed search_path needs it, rather than trusting the CREATE EXTENSION call silently.
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT n.nspname FROM pg_extension e "
                            + "JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname = 'citext'")) {
                assertThat(resultSet.next())
                        .as("citext extension must be installed for this pre-step to have done anything")
                        .isTrue();
                assertThat(resultSet.getString("nspname"))
                        .as("citext must live in notifications - V1's SET search_path TO notifications "
                                + "makes any other schema's install invisible to CREATE TABLE")
                        .isEqualTo("notifications");
            }
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
        String fence = block.toString();
        // Kimi Phase 8 Finding #6: pins fence-selection to V1's own header comment, so a future
        // design.md edit that adds another sql fence before this one fails loudly here instead of
        // silently comparing V1 against the wrong block.
        assertThat(fence)
                .as("the extracted fence must be V1's own baseline block")
                .startsWith("-- Notification Service baseline (notifications schema).");
        return fence;
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
            assertThat(succeededVersions).containsExactly("1", "2", "3", "4", "5", "6");
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

    /** Kimi Phase 11 Gap #2: the test above only proves Flyway's own bookkeeping skips an
     * already-applied version - it never re-executes V2's SQL, so it can't catch a real regression
     * in V2's own {@code IF NOT EXISTS} guard or its GRANT statements' idempotency. This test
     * re-executes V2's actual file content directly against the admin connection a second time (V2
     * already ran once via the {@code @BeforeAll} migration), then re-proves the grant is still
     * exactly as scoped afterward. */
    @Test
    void v2sOwnSqlIsIdempotentWhenReExecutedDirectlyNotJustSkippedByFlyway() throws IOException, SQLException {
        String v2Sql = Files.readString(Path.of("src/main/resources/db/migration/V2__notification_app_role_and_grants.sql"));

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            assertThatCode(() -> statement.execute(v2Sql))
                    .as("V2's own CREATE ROLE guard and GRANT statements must tolerate a direct re-run")
                    .doesNotThrowAnyException();
        }

        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD)) {
            assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app, "delivery_log");
        }
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

    /** T04's own V4 grant. Not folded into {@link #assertInsertAndSelectSucceedUpdateAndDeleteAreDenied}
     * (the shared {@code GRANTED_TABLES} helper) - that helper's SELECT/UPDATE probes are hardcoded
     * around {@code delivery_log}'s own {@code source_event_key}/{@code outcome} columns, which
     * {@code processed_events} doesn't have (its own natural key, {@code event_key}, IS the row
     * identifier - there is no separate correlator column). Self-contained here instead. */
    @Test
    void notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnProcessedEvents() throws SQLException {
        String eventKey = "it-processed-events";
        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD)) {
            try (PreparedStatement insert = app.prepareStatement(
                    "INSERT INTO notifications.processed_events (event_key, event_type) VALUES (?, 'test.event')")) {
                insert.setString(1, eventKey);
                insert.execute();
            }

            try (PreparedStatement select = app.prepareStatement(
                    "SELECT count(*) FROM notifications.processed_events WHERE event_key = ?")) {
                select.setString(1, eventKey);
                try (ResultSet resultSet = select.executeQuery()) {
                    resultSet.next();
                    assertThat(resultSet.getInt(1))
                            .as("SELECT must see the row notification_app just inserted")
                            .isEqualTo(1);
                }
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement update = app.prepareStatement(
                        "UPDATE notifications.processed_events SET event_type = 'other' WHERE event_key = ?")) {
                    update.setString(1, eventKey);
                    update.execute();
                }
            })
                    .as("UPDATE on processed_events must be denied for notification_app")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> {
                try (PreparedStatement delete = app.prepareStatement(
                        "DELETE FROM notifications.processed_events WHERE event_key = ?")) {
                    delete.setString(1, eventKey);
                    delete.execute();
                }
            })
                    .as("DELETE on processed_events must be denied for notification_app")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }

        try (Connection admin = adminConnection();
             PreparedStatement cleanup = admin.prepareStatement(
                     "DELETE FROM notifications.processed_events WHERE event_key = ?")) {
            cleanup.setString(1, eventKey);
            cleanup.execute();
        }
    }

    /** T05's own V5 grant (Kimi Phase 3 Finding #3). Self-contained, same reasoning as the
     * {@code processed_events} test above - {@code contact_projection}'s own natural key,
     * {@code account_uuid}, IS the row identifier, and this table also needs a genuine
     * UPDATE-succeeds proof the shared {@code GRANTED_TABLES} helper (built around
     * {@code delivery_log}'s own insert-only grant) never had to make. */
    @Test
    void notificationAppCanInsertSelectAndUpdateButNotDeleteOnContactProjection() throws SQLException {
        String accountUuid = "11111111-1111-1111-1111-111111111111";
        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD)) {
            try (PreparedStatement insert = app.prepareStatement(
                    "INSERT INTO notifications.contact_projection (account_uuid, email, updated_at) "
                            + "VALUES (?::uuid, 'it-contact-projection@example.com', now())")) {
                insert.setString(1, accountUuid);
                insert.execute();
            }

            try (PreparedStatement select = app.prepareStatement(
                    "SELECT count(*) FROM notifications.contact_projection WHERE account_uuid = ?::uuid")) {
                select.setString(1, accountUuid);
                try (ResultSet resultSet = select.executeQuery()) {
                    resultSet.next();
                    assertThat(resultSet.getInt(1))
                            .as("SELECT must see the row notification_app just inserted")
                            .isEqualTo(1);
                }
            }

            try (PreparedStatement update = app.prepareStatement(
                    "UPDATE notifications.contact_projection SET email = 'updated@example.com' "
                            + "WHERE account_uuid = ?::uuid")) {
                update.setString(1, accountUuid);
                assertThatCode(update::execute)
                        .as("UPDATE on contact_projection must be permitted for notification_app")
                        .doesNotThrowAnyException();
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement delete = app.prepareStatement(
                        "DELETE FROM notifications.contact_projection WHERE account_uuid = ?::uuid")) {
                    delete.setString(1, accountUuid);
                    delete.execute();
                }
            })
                    .as("DELETE on contact_projection must be denied for notification_app")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }

        try (Connection admin = adminConnection();
             PreparedStatement cleanup = admin.prepareStatement(
                     "DELETE FROM notifications.contact_projection WHERE account_uuid = ?::uuid")) {
            cleanup.setString(1, accountUuid);
            cleanup.execute();
        }
    }

    /** T08's own V6 grant (Kimi Phase 3 Finding #5). Unlike {@code processed_events}/
     * {@code contact_projection}, this table's own grant is {@code SELECT} only - nothing in this
     * codebase ever writes to {@code channel_preferences} (no write API exists anywhere in this
     * spec), so the row this test selects is inserted as the admin role, not {@code notification_app}. */
    @Test
    void notificationAppCanSelectButNotInsertUpdateOrDeleteOnChannelPreferences() throws SQLException {
        String accountUuid = "22222222-2222-2222-2222-222222222222";
        try (Connection admin = adminConnection();
             PreparedStatement insert = admin.prepareStatement(
                     "INSERT INTO notifications.channel_preferences (account_uuid, category, channel, enabled) "
                             + "VALUES (?::uuid, 'MARKETING', 'EMAIL', false)")) {
            insert.setString(1, accountUuid);
            insert.execute();
        }

        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD)) {
            try (PreparedStatement select = app.prepareStatement(
                    "SELECT enabled FROM notifications.channel_preferences WHERE account_uuid = ?::uuid")) {
                select.setString(1, accountUuid);
                try (ResultSet resultSet = select.executeQuery()) {
                    resultSet.next();
                    assertThat(resultSet.getBoolean("enabled"))
                            .as("SELECT must see the row the admin role just inserted")
                            .isFalse();
                }
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement insert = app.prepareStatement(
                        "INSERT INTO notifications.channel_preferences (account_uuid, category, channel) "
                                + "VALUES (?::uuid, 'PAYMENT', 'EMAIL')")) {
                    insert.setString(1, accountUuid);
                    insert.execute();
                }
            })
                    .as("INSERT on channel_preferences must be denied for notification_app")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> {
                try (PreparedStatement update = app.prepareStatement(
                        "UPDATE notifications.channel_preferences SET enabled = true WHERE account_uuid = ?::uuid")) {
                    update.setString(1, accountUuid);
                    update.execute();
                }
            })
                    .as("UPDATE on channel_preferences must be denied for notification_app")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> {
                try (PreparedStatement delete = app.prepareStatement(
                        "DELETE FROM notifications.channel_preferences WHERE account_uuid = ?::uuid")) {
                    delete.setString(1, accountUuid);
                    delete.execute();
                }
            })
                    .as("DELETE on channel_preferences must be denied for notification_app")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }

        try (Connection admin = adminConnection();
             PreparedStatement cleanup = admin.prepareStatement(
                     "DELETE FROM notifications.channel_preferences WHERE account_uuid = ?::uuid")) {
            cleanup.setString(1, accountUuid);
            cleanup.execute();
        }
    }

    @Test
    void notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope() throws SQLException {
        try (Connection app = connectAsNotificationApp(NOTIFICATION_APP_PASSWORD)) {
            for (String table : UNGRANTED_TABLES) {
                try (Statement statement = app.createStatement()) {
                    assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM notifications." + table))
                            .as("SELECT on %s must be denied for notification_app (not AC2's one named table)", table)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                }
                // Kimi Phase 8 Finding #2: SELECT-only denial doesn't prove "no access at all" - a
                // regression granting INSERT (but not SELECT) on an ungranted table would pass the
                // check above while violating least-privilege. Proves INSERT is denied too.
                try (Statement statement = app.createStatement()) {
                    assertThatThrownBy(() -> statement.execute(minimalInsertFixtureFor(table)))
                            .as("INSERT on %s must be denied for notification_app (not AC2's one named table)", table)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                }
                // Kimi Phase 11 Gap #3: SELECT+INSERT denial doesn't prove UPDATE/DELETE are denied
                // too - a regression granting only those (without SELECT/INSERT) would still pass
                // the two checks above. The WHERE predicate targets a row that doesn't exist; Postgres
                // checks table-level privilege before any row is matched, so denial fires regardless.
                try (Statement statement = app.createStatement()) {
                    assertThatThrownBy(() -> statement.execute(noWhereUpdateStatementFor(table)))
                            .as("UPDATE on %s must be denied for notification_app (not AC2's one named table)", table)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                }
                try (Statement statement = app.createStatement()) {
                    assertThatThrownBy(() -> statement.execute(noWhereDeleteStatementFor(table)))
                            .as("DELETE on %s must be denied for notification_app (not AC2's one named table)", table)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("permission denied");
                }
            }
        }
    }

    // Deliberately no WHERE clause: an UPDATE/DELETE predicate referencing a column would be denied
    // as soon as Postgres needs SELECT to evaluate it, even if UPDATE/DELETE were mistakenly granted
    // on the table - masking exactly the regression this check exists to catch. A mutation test
    // (temporarily granting UPDATE on notifications.templates in V2, confirming this test then fails
    // with no exception thrown, then reverting) caught this the first time these fixtures had a
    // WHERE clause and proved the fix; both revert cleanly since these run against an ephemeral
    // Testcontainers instance discarded after the test.
    private static String noWhereUpdateStatementFor(String table) {
        return switch (table) {
            case "templates" -> "UPDATE notifications.templates SET body = 'x'";
            case "inapp_notifications" -> "UPDATE notifications.inapp_notifications SET title = 'x'";
            case "delivery_retry" -> "UPDATE notifications.delivery_retry SET attempt = 2";
            case "shedlock" -> "UPDATE notifications.shedlock SET locked_by = 'x'";
            default -> throw new IllegalArgumentException("no UPDATE fixture for " + table);
        };
    }

    private static String noWhereDeleteStatementFor(String table) {
        return switch (table) {
            case "templates" -> "DELETE FROM notifications.templates";
            case "inapp_notifications" -> "DELETE FROM notifications.inapp_notifications";
            case "delivery_retry" -> "DELETE FROM notifications.delivery_retry";
            case "shedlock" -> "DELETE FROM notifications.shedlock";
            default -> throw new IllegalArgumentException("no DELETE fixture for " + table);
        };
    }

    private static String minimalInsertFixtureFor(String table) {
        return switch (table) {
            case "templates" -> "INSERT INTO notifications.templates (name, channel, version, body) "
                    + "VALUES ('it-denied', 'EMAIL', 1, 'body')";
            case "inapp_notifications" -> "INSERT INTO notifications.inapp_notifications "
                    + "(notification_uuid, account_uuid, category, title, body) VALUES "
                    + "('00000000-0000-0000-0000-000000000002', "
                    + "'00000000-0000-0000-0000-000000000001', 'SECURITY', 't', 'b')";
            case "delivery_retry" -> "INSERT INTO notifications.delivery_retry "
                    + "(source_event_key, channel, attempt, next_attempt_at) "
                    + "VALUES ('it-denied', 'EMAIL', 1, now())";
            case "shedlock" -> "INSERT INTO notifications.shedlock "
                    + "(name, lock_until, locked_at, locked_by) "
                    + "VALUES ('it-denied', now(), now(), 'it')";
            default -> throw new IllegalArgumentException("no INSERT fixture for " + table);
        };
    }

    /** Kimi Phase 11 Gap #1: {@code T01SkeletonRegressionTest}'s own
     * {@code finalNameAndFlywayPluginMirrorTheSiblingConvention} already guards {@code <schemas>}
     * and the no-{@code <executions>} rule, but not the connection details AC4's real
     * {@code mvn flyway:migrate} actually depends on - not modified here since T02's own scope
     * excludes touching T01's test file; this is T02's own copy of that same guard, scoped to what
     * this task's AC4 depends on. */
    @Test
    void flywayPluginConnectionDetailsMatchTheDocumentedLocalDevSetup() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        String flywayPlugin = pluginBlock(pom, "flyway-maven-plugin");

        assertThat(flywayPlugin).contains("<url>jdbc:postgresql://localhost:5432/checky</url>");
        assertThat(flywayPlugin).contains("<user>checky</user>");
        assertThat(flywayPlugin).contains("<password>checky-local-only</password>");
        assertThat(flywayPlugin).contains("<schemas>notifications</schemas>");
    }

    private static String pluginBlock(String pomContent, String artifactId) {
        String pattern = "(?s)<plugin>\\s*<groupId>[^<]*</groupId>\\s*<artifactId>"
                + Pattern.quote(artifactId) + "</artifactId>.*?</plugin>";
        Matcher matcher = Pattern.compile(pattern).matcher(pomContent);
        if (!matcher.find()) {
            throw new AssertionError("no <plugin> block found for artifactId " + artifactId);
        }
        return matcher.group();
    }

    /** Kimi Phase 11 Gap #4: V2's own header comment documents the secrets-discipline rules
     * (no committed password, no hardcoded database name, dynamic {@code current_database()}) in
     * prose, but nothing failed if a future edit violated them. Comments are stripped first, since
     * the header comment legitimately mentions {@code PASSWORD} as documentation of the one-time
     * local-dev step (README/Phase 9), not as executable SQL. */
    @Test
    void v2ContainsNoCommittedPasswordOrHardcodedDatabaseName() throws IOException {
        String v2Sql = Files.readString(Path.of("src/main/resources/db/migration/V2__notification_app_role_and_grants.sql"));
        String codeOnly = v2Sql.replaceAll("(?m)^\\s*--.*$", "");

        assertThat(codeOnly).as("no committed password in V2's own executable SQL")
                .doesNotContainIgnoringCase("PASSWORD");
        assertThat(codeOnly).as("no hardcoded database name - must target current_database() dynamically")
                .doesNotContain("checky");
        assertThat(codeOnly).as("GRANT CONNECT must target current_database(), not a literal name")
                .contains("current_database()");
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
                     "SELECT name, channel, version, subject FROM notifications.templates")) {
            List<String> actualPairs = new ArrayList<>();
            Set<String> distinctNames = new HashSet<>();
            int rowCount = 0;
            while (resultSet.next()) {
                rowCount++;
                String name = resultSet.getString("name");
                String channel = resultSet.getString("channel");
                assertThat(resultSet.getInt("version")).as("every seeded template must be version 1").isEqualTo(1);
                actualPairs.add(name + ":" + channel);
                distinctNames.add(name);

                // Kimi Phase 8 Finding #5: guards V3's own EMAIL-has-subject / IN_APP-has-no-subject
                // convention, without asserting exact copy (which stays revisable).
                String subject = resultSet.getString("subject");
                if ("EMAIL".equals(channel)) {
                    assertThat(subject).as("EMAIL row %s must have a non-null subject", name).isNotNull();
                } else {
                    assertThat(subject).as("IN_APP row %s must have a null subject", name).isNull();
                }
            }
            assertThat(rowCount).as("14 rows: 7 mappings x 2 channels").isEqualTo(14);
            assertThat(distinctNames)
                    .as("9 distinct names - 5 mappings share one name across channels, 2 use a distinct name per channel")
                    .hasSize(9);
            assertThat(actualPairs).as("exact (name, channel) pairs")
                    .containsExactlyInAnyOrderElementsOf(expectedNameChannelPairs);
        }
    }

    // Kimi Phase 8 Finding #4: sourceEventKey is bound via PreparedStatement rather than
    // concatenated, so this proven grant-test pattern isn't the one future maintainers copy for
    // genuinely user-controlled input. Table names stay concatenated (a fixed internal constant
    // list, not user input - JDBC can't parameterize identifiers anyway).
    private void assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(Connection app, String table) throws SQLException {
        String sourceEventKey = "it-" + table;
        try (PreparedStatement insert = insertStatementFor(app, table, sourceEventKey)) {
            insert.execute();
        }

        try (PreparedStatement select = app.prepareStatement(
                "SELECT count(*) FROM notifications." + table + " WHERE source_event_key = ?")) {
            select.setString(1, sourceEventKey);
            try (ResultSet resultSet = select.executeQuery()) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).as("SELECT on %s must see the row notification_app just inserted", table).isEqualTo(1);
            }
        }

        assertThatThrownBy(() -> {
            try (PreparedStatement update = app.prepareStatement(
                    "UPDATE notifications." + table + " SET outcome = 'FAILED' WHERE source_event_key = ?")) {
                update.setString(1, sourceEventKey);
                update.execute();
            }
        })
                .as("UPDATE on %s must be denied for notification_app", table)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");

        assertThatThrownBy(() -> {
            try (PreparedStatement delete = app.prepareStatement(
                    "DELETE FROM notifications." + table + " WHERE source_event_key = ?")) {
                delete.setString(1, sourceEventKey);
                delete.execute();
            }
        })
                .as("DELETE on %s must be denied for notification_app", table)
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("permission denied");

        cleanUpAsAdmin(table, sourceEventKey);
    }

    private static PreparedStatement insertStatementFor(Connection app, String table, String sourceEventKey) throws SQLException {
        return switch (table) {
            case "delivery_log" -> {
                PreparedStatement statement = app.prepareStatement(
                        "INSERT INTO notifications.delivery_log (channel, source_event_key, outcome) VALUES ('EMAIL', ?, 'SENT')");
                statement.setString(1, sourceEventKey);
                yield statement;
            }
            default -> throw new IllegalArgumentException("no INSERT fixture for " + table);
        };
    }

    private void cleanUpAsAdmin(String table, String sourceEventKey) throws SQLException {
        try (Connection admin = adminConnection();
             PreparedStatement statement = admin.prepareStatement(
                     "DELETE FROM notifications." + table + " WHERE source_event_key = ?")) {
            statement.setString(1, sourceEventKey);
            statement.execute();
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsNotificationApp(String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "notification_app", password);
    }
}
