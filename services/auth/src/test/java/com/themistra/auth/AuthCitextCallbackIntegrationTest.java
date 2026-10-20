package com.themistra.auth;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Kimi citext review, Findings 1 and 2 (2026-10-20): auth's side of the shared-database conflict.
 * If notification-service migrated first into a shared database, its callback put citext in the
 * notifications schema; auth's V1 then skipped its own install and failed with "type citext does
 * not exist", creating nothing. {@code db/migration/beforeMigrate.sql} now refuses that database
 * with a clear reason, and still lets an already-migrated database start.
 *
 * <p>Plain Flyway against real Postgres, configured as the application configures it (schema
 * {@code auth}, migrations from the classpath). Each test uses its own database.</p>
 */
@Testcontainers
class AuthCitextCallbackIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Test
    void aFreshDatabaseMigratesWithCitextInTheAuthSchema() throws SQLException {
        String url = newDatabase("fresh");

        migrate(url);

        assertThat(citextSchema(url)).isEqualTo("auth");
        assertThat(query(url, "SELECT to_regclass('auth.accounts') IS NOT NULL")).isEqualTo("t");
    }

    @Test
    void aDatabaseWhereNotificationAlreadyInstalledCitextIsRefusedBeforeV1() throws SQLException {
        String url = newDatabase("shared_notification_first");
        execute(url, "CREATE SCHEMA notifications", "CREATE EXTENSION citext SCHEMA notifications");

        assertThatThrownBy(() -> migrate(url))
                .hasStackTraceContaining("citext is installed in schema notifications")
                .hasStackTraceContaining("Run auth-service in its own database");

        assertThat(query(url, "SELECT to_regclass('auth.accounts') IS NULL")).as("V1 never ran").isEqualTo("t");
        assertThat(query(url, "SELECT count(*) FROM auth.flyway_schema_history WHERE version IS NOT NULL"))
                .as("no versioned migration was applied").isEqualTo("0");
    }

    /** An administrator installed citext into auth before the first migrate: V1 can see it there. */
    @Test
    void citextPreinstalledIntoTheAuthSchemaIsAccepted() throws SQLException {
        String url = newDatabase("preinstalled_in_auth");
        execute(url, "CREATE SCHEMA auth", "CREATE EXTENSION citext SCHEMA auth");

        assertThat(migrate(url).migrationsExecuted).isPositive();
        assertThat(query(url, "SELECT to_regclass('auth.accounts') IS NOT NULL")).isEqualTo("t");
    }

    /** The local shared database: auth fully migrated, then citext moved into notifications by hand
     * (notification T02). Auth must keep starting there, and its citext column must still work. */
    @Test
    void anAlreadyMigratedDatabaseStillMigratesAfterCitextWasMovedAway() throws SQLException {
        String url = newDatabase("migrated_then_moved");
        migrate(url);
        execute(url, "CREATE SCHEMA notifications", "ALTER EXTENSION citext SET SCHEMA notifications");

        MigrateResult second = migrate(url);

        assertThat(second.migrationsExecuted).as("nothing left to apply").isZero();
        assertThat(query(url, "SELECT count(*) FROM auth.accounts WHERE email = 'Someone@Example.COM'"))
                .as("the citext column still compares").isEqualTo("0");
    }

    @Test
    void aSecondMigrateOnAMigratedDatabaseIsClean() throws SQLException {
        String url = newDatabase("rerun");
        migrate(url);

        assertThat(migrate(url).migrationsExecuted).isZero();
        assertThat(citextSchema(url)).isEqualTo("auth");
    }

    private static MigrateResult migrate(String url) {
        return Flyway.configure()
                .dataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("auth")
                .defaultSchema("auth")
                .createSchemas(true)
                .load()
                .migrate();
    }

    private static String newDatabase(String name) throws SQLException {
        execute(POSTGRES.getJdbcUrl(), "CREATE DATABASE " + name);
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + name);
    }

    private static String citextSchema(String url) throws SQLException {
        return query(url, "SELECT n.nspname FROM pg_extension e "
                + "JOIN pg_namespace n ON n.oid = e.extnamespace WHERE e.extname = 'citext'");
    }

    private static void execute(String url, String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static String query(String url, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getString(1);
        }
    }
}
