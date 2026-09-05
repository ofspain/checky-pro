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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T04 — real Testcontainers proof that V3 does what AC9/AC12 claim: {@code crypto_app} can INSERT
 * and UPDATE {@code chain.outbox} (but still not DELETE), and the DB-generated {@code id} binds as
 * a {@code Long}. Mirrors {@link ChainBaselineMigrationIntegrationTest}'s real-TCP-connection
 * technique exactly (a {@code docker exec} shell connection would bypass real password auth).
 */
@Testcontainers
class OutboxGrantMigrationIntegrationTest {

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

    /** Phase 11 Gap 9: mirrors {@code ChainBaselineMigrationIntegrationTest.v2RoleCreationGuardIsIdempotentUnderARealReRun} —
     * V3 is grant-only (no CREATE ROLE), so no explicit guard was added (frozen brief amendment #9);
     * this proves that holds under a genuine re-run, not just in theory. */
    @Test
    void v3GrantIsIdempotentUnderARealReRunAndPrivilegesAreUnchanged() throws SQLException {
        assertThatCode(() -> Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("chain")
                .load()
                .migrate())
                .doesNotThrowAnyException();

        try (Connection app = connectAsCryptoApp();
             Statement statement = app.createStatement()) {
            statement.execute(
                    "INSERT INTO chain.outbox (aggregate_type, aggregate_id, event_type, idempotency_key, payload) "
                            + "VALUES ('tx-seen', 'it-watch-rerun', 'chain.tx.seen', 'it-idempotency-key-rerun', '{}'::jsonb)");
            statement.execute(
                    "UPDATE chain.outbox SET published_at = now() WHERE idempotency_key = 'it-idempotency-key-rerun'");
            assertThatThrownBy(() -> statement.execute(
                    "DELETE FROM chain.outbox WHERE idempotency_key = 'it-idempotency-key-rerun'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        cleanUp("it-idempotency-key-rerun");
    }

    @Test
    void cryptoAppCanInsertIntoOutboxAndTheGeneratedIdIsALong() throws SQLException {
        try (Connection app = connectAsCryptoApp();
             Statement statement = app.createStatement()) {
            statement.execute(
                    "INSERT INTO chain.outbox (aggregate_type, aggregate_id, event_type, idempotency_key, payload) "
                            + "VALUES ('tx-seen', 'it-watch-1', 'chain.tx.seen', 'it-idempotency-key-1', '{}'::jsonb)");

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT id FROM chain.outbox WHERE idempotency_key = 'it-idempotency-key-1'")) {
                assertThat(resultSet.next()).isTrue();
                long id = resultSet.getLong("id");
                assertThat(id).isPositive();
                assertThat(resultSet.getObject("id")).isInstanceOf(Long.class);
            }
        }
        cleanUp("it-idempotency-key-1");
    }

    @Test
    void cryptoAppCanUpdatePublishedAtOnItsOwnRow() throws SQLException {
        try (Connection app = connectAsCryptoApp();
             Statement statement = app.createStatement()) {
            statement.execute(
                    "INSERT INTO chain.outbox (aggregate_type, aggregate_id, event_type, idempotency_key, payload) "
                            + "VALUES ('tx-seen', 'it-watch-2', 'chain.tx.seen', 'it-idempotency-key-2', '{}'::jsonb)");

            statement.execute(
                    "UPDATE chain.outbox SET published_at = now() WHERE idempotency_key = 'it-idempotency-key-2'");

            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT published_at FROM chain.outbox WHERE idempotency_key = 'it-idempotency-key-2'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getTimestamp("published_at")).isNotNull();
            }
        }
        cleanUp("it-idempotency-key-2");
    }

    @Test
    void cryptoAppStillCannotDeleteFromOutbox() throws SQLException {
        try (Connection app = connectAsCryptoApp();
             Statement statement = app.createStatement()) {
            statement.execute(
                    "INSERT INTO chain.outbox (aggregate_type, aggregate_id, event_type, idempotency_key, payload) "
                            + "VALUES ('tx-seen', 'it-watch-3', 'chain.tx.seen', 'it-idempotency-key-3', '{}'::jsonb)");

            assertThatThrownBy(() -> statement.execute(
                    "DELETE FROM chain.outbox WHERE idempotency_key = 'it-idempotency-key-3'"))
                    .as("DELETE on outbox must still be denied for crypto_app - only INSERT/SELECT/UPDATE were granted")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        cleanUp("it-idempotency-key-3");
    }

    private static void cleanUp(String idempotencyKey) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM chain.outbox WHERE idempotency_key = '" + idempotencyKey + "'");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsCryptoApp() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
    }
}
