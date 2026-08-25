package com.themistra.crypto;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T02 — real Testcontainers proof that V1/V2 do what the frozen brief's AC1-AC3 claim, not just
 * that the SQL text looks right. Runs both migrations via the Flyway Java API (runtime Flyway is
 * disabled in application.properties by design, so there is no Spring-context auto-migration path
 * to piggyback on here) against a real Postgres 16 container, then connects as crypto_app over
 * real TCP/JDBC - the same auth path Phase 9 confirmed enforces real password authentication,
 * unlike a `docker exec` shell connection, which silently bypasses it via Postgres's own loopback
 * trust rule.
 */
@Testcontainers
class ChainBaselineMigrationIntegrationTest {

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
    void allTenBaselineTablesExist() throws SQLException {
        List<String> expected = List.of("watches", "observations", "quorum_decisions", "provider_health",
                "chain_cursors", "token_allowlist", "screening_results", "attestations", "outbox", "shedlock");

        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'chain'")) {
            List<String> actual = new java.util.ArrayList<>();
            while (resultSet.next()) {
                actual.add(resultSet.getString(1));
            }
            assertThat(actual).containsAll(expected);
        }
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
            for (String table : List.of("observations", "attestations", "quorum_decisions")) {
                assertInsertSucceedsUpdateAndDeleteAreDenied(app, table);
            }
        }
    }

    @Test
    void cryptoAppHasNoAccessToTablesOutsideAc3Scope() throws SQLException {
        try (Connection app = connectAsCryptoApp(CRYPTO_APP_PASSWORD);
             Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM chain.watches"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    private void assertInsertSucceedsUpdateAndDeleteAreDenied(Connection app, String table) throws SQLException {
        String txHash = "0xit-" + table;
        try (Statement statement = app.createStatement()) {
            statement.execute(insertStatementFor(table, txHash));

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
