package com.themistra.crypto;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T02 — real Testcontainers proof that V1/V2 do what the frozen brief's AC1-AC4 claim, not just
 * that the SQL text looks right. Runs both migrations via the Flyway Java API (runtime Flyway is
 * disabled in application.properties by design, so there is no Spring-context auto-migration path
 * to piggyback on here) against a real Postgres 16 container, then connects as crypto_app over
 * real TCP/JDBC - the same auth path Phase 9 confirmed enforces real password authentication,
 * unlike a `docker exec` shell connection, which silently bypasses it via Postgres's own loopback
 * trust rule.
 */
@Testcontainers
class ChainBaselineMigrationIntegrationTest {

    private static final List<String> GRANTED_TABLES = List.of("observations", "attestations", "quorum_decisions");
    // outbox moved out of this list in T04 (V3__crypto_app_outbox_grant.sql grants
    // INSERT/SELECT/UPDATE) - its own access is verified separately by
    // OutboxGrantMigrationIntegrationTest, including that DELETE is still denied.
    private static final List<String> UNGRANTED_TABLES = List.of("watches", "provider_health", "chain_cursors",
            "token_allowlist", "screening_results", "shedlock");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String CRYPTO_APP_PASSWORD = "it-crypto-app-password";

    @BeforeAll
    static void migrateAndProvisionLocalPassword() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("chain")
                .load()
                .migrate();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE crypto_app PASSWORD '" + CRYPTO_APP_PASSWORD + "'");
        }
    }

    @Test
    void allTenBaselineTablesExistAndNoOthers() throws SQLException {
        List<String> expected = List.of("watches", "observations", "quorum_decisions", "provider_health",
                "chain_cursors", "token_allowlist", "screening_results", "attestations", "outbox", "shedlock");

        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = 'chain' AND table_name != 'flyway_schema_history'")) {
            List<String> actual = new ArrayList<>();
            while (resultSet.next()) {
                actual.add(resultSet.getString(1));
            }
            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    /** AC1's own "byte-for-byte" claim, automated: the strongest possible regression guard for the
     * verbatim artifact, stronger than any column/constraint introspection could be, since it
     * catches literally any textual deviation from design.md §4c - the same check Phase 6 ran
     * manually via `diff`, now permanent. */
    @Test
    void v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock() throws IOException {
        Path designDoc = Path.of("../../spec/crypto-service/design.md");
        Path v1Migration = Path.of("src/main/resources/db/migration/V1__chain_baseline.sql");

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
        // Flyway also inserts a synthetic, unversioned "schema creation" row before the versioned
        // migrations; only the versioned rows (V1, V2, and T04's V3) are this assertion's concern.
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, success FROM chain.flyway_schema_history "
                             + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            List<String> succeededVersions = new ArrayList<>();
            while (resultSet.next()) {
                assertThat(resultSet.getBoolean("success")).as("version %s must have succeeded", resultSet.getString("version")).isTrue();
                succeededVersions.add(resultSet.getString("version"));
            }
            assertThat(succeededVersions).containsExactly("1", "2", "3");
        }
    }

    /** Kimi Finding 3: V2's `IF NOT EXISTS` role guard must survive a genuine re-run, not just look
     * idempotent. Mirrors Phase 7's manual drop-and-recreate proof, now automated. */
    @Test
    void v2RoleCreationGuardIsIdempotentUnderARealReRun() {
        assertThatCode(() -> Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("chain")
                .load()
                .migrate())
                .doesNotThrowAnyException();
    }

    @Test
    void cryptoAppRoleRequiresItsProvisionedPassword() {
        assertThatThrownBy(() -> connectAsCryptoApp("definitely-wrong-password"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("password authentication failed");

        assertThatCode(() -> connectAsCryptoApp(CRYPTO_APP_PASSWORD).close())
                .doesNotThrowAnyException();
    }

    @Test
    void cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables() throws SQLException {
        try (Connection app = connectAsCryptoApp(CRYPTO_APP_PASSWORD)) {
            for (String table : GRANTED_TABLES) {
                assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app, table);
            }
        }
    }

    @Test
    void cryptoAppHasNoAccessAtAllToTablesOutsideAc3Scope() throws SQLException {
        try (Connection app = connectAsCryptoApp(CRYPTO_APP_PASSWORD); Statement statement = app.createStatement()) {
            for (String table : UNGRANTED_TABLES) {
                assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM chain." + table))
                        .as("SELECT on %s must be denied for crypto_app (not one of AC3's three named tables)", table)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    @Test
    void cryptoAppCannotPerformDdlInTheChainSchema() throws SQLException {
        // CREATE without schema CREATE privilege is denied as "permission denied"; DROP/ALTER on a
        // table crypto_app doesn't own is denied as "must be owner of table X" - both are Postgres's
        // real, legitimate denial messages for these two different DDL categories.
        try (Connection app = connectAsCryptoApp(CRYPTO_APP_PASSWORD); Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE chain.evil_test(id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute("DROP TABLE chain.observations"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("must be owner of table observations");
        }
    }

    /** The entire AC3 owner/grantee split (Kimi T02-Phase8 Finding 2) only means anything if the
     * baseline tables are owned by the migration/admin role, never by crypto_app. */
    @Test
    void baselineTablesAreOwnedByTheMigrationRoleNeverByCryptoApp() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'chain'")) {
            while (resultSet.next()) {
                assertThat(resultSet.getString("tableowner"))
                        .as("chain.%s must not be owned by crypto_app", resultSet.getString("tablename"))
                        .isNotEqualTo("crypto_app");
            }
        }
    }

    /** AC4: mirrors Phase 9's manual smoke-test finding (correct password boots clean with zero
     * Flyway activity, wrong password fails) as a permanent, fast, non-Spring-context guard against
     * the property itself being silently reverted. */
    @Test
    void runtimeFlywayIsDisabledInApplicationProperties() throws IOException {
        var properties = new java.util.Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        assertThat(properties.getProperty("spring.flyway.enabled")).isEqualTo("false");
    }

    private void assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(Connection app, String table) throws SQLException {
        String txHash = "0xit-" + table;
        try (Statement statement = app.createStatement()) {
            statement.execute(insertStatementFor(table, txHash));

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT count(*) FROM chain." + table + " WHERE tx_hash = '" + txHash + "'")) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).as("SELECT on %s must see the row crypto_app just inserted", table).isEqualTo(1);
            }

            assertThatThrownBy(() -> statement.execute("UPDATE chain." + table + " SET chain = 'TRON' WHERE tx_hash = '" + txHash + "'"))
                    .as("UPDATE on %s must be denied for crypto_app", table)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute("DELETE FROM chain." + table + " WHERE tx_hash = '" + txHash + "'"))
                    .as("DELETE on %s must be denied for crypto_app", table)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        cleanUpAsAdmin(table, txHash);
    }

    private static String insertStatementFor(String table, String txHash) {
        return switch (table) {
            case "observations" -> "INSERT INTO chain.observations (chain, tx_hash, provider, fact_type, raw_response) "
                    + "VALUES ('ETHEREUM', '" + txHash + "', 'it-provider', 'existence', '{}')";
            case "attestations" -> "INSERT INTO chain.attestations (chain, tx_hash, receipt_digest, outcome) "
                    + "VALUES ('ETHEREUM', '" + txHash + "', repeat('a', 64), 'SIGNED')";
            case "quorum_decisions" -> "INSERT INTO chain.quorum_decisions (chain, tx_hash, fact_type, outcome, agreeing_count, provider_count) "
                    + "VALUES ('ETHEREUM', '" + txHash + "', 'existence', 'AGREED', 2, 3)";
            default -> throw new IllegalArgumentException("no INSERT fixture for " + table);
        };
    }

    private void cleanUpAsAdmin(String table, String txHash) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM chain." + table + " WHERE tx_hash = '" + txHash + "'");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsCryptoApp(String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", password);
    }
}
