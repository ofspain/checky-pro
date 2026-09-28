package com.themistra.notification.preference;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The second test class in this module needing a real Spring context (after T04's own
 * {@code IdempotencyGuardIntegrationTest}, which this mirrors structurally). Also the first real
 * proof that Hibernate's {@code ddl-auto=validate} accepts {@link ContactProjection}'s
 * {@code citext} mapping (Kimi Phase 3 Finding #1) via a committed test, not just the uncommitted
 * Phase 6/7 scratch checks.
 */
@Testcontainers
@SpringBootTest
class ContactProjectionUpdaterIntegrationTest {

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
    private ContactProjectionUpdater updater;

    @Autowired
    private ContactProjectionRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    /** Kimi Phase 8 Finding #3 / frozen brief Finding #5: display_name must be null - no data
     * source exists anywhere in auth's own domain (AC4). */
    @Test
    void firstCallCreatesARowWithNullDisplayName() {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        boolean result = updater.upsertEmail(accountUuid, "first@example.com", occurredAt);

        assertThat(result).isTrue();
        ContactProjection row = repository.findById(accountUuid).orElseThrow();
        assertThat(row.getEmail()).isEqualTo("first@example.com");
        assertThat(row.getUpdatedAt()).isEqualTo(occurredAt);
        assertThat(row.getDisplayName()).as("no data source for display_name exists (AC4)").isNull();
    }

    @Test
    void laterCallUpdatesEmailAndUpdatedAt() {
        UUID accountUuid = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2026-01-02T00:00:00Z");

        updater.upsertEmail(accountUuid, "first@example.com", t1);
        boolean result = updater.upsertEmail(accountUuid, "second@example.com", t2);

        assertThat(result).isTrue();
        ContactProjection row = repository.findById(accountUuid).orElseThrow();
        assertThat(row.getEmail()).isEqualTo("second@example.com");
        assertThat(row.getUpdatedAt()).isEqualTo(t2);
        assertThat(row.getDisplayName()).isNull();
    }

    /** Kimi Phase 8 Finding #1's own required out-of-order proof (AC3, frozen brief Finding #2) -
     * auth.email.requested and auth.user.lifecycle are different Kafka topics with no cross-topic
     * ordering guarantee, so a stale, out-of-order write must never overwrite a newer projection. */
    @Test
    void olderCallDoesNotOverwriteANewerProjection() {
        UUID accountUuid = UUID.randomUUID();
        Instant newer = Instant.parse("2026-01-02T00:00:00Z");
        Instant older = Instant.parse("2026-01-01T00:00:00Z");

        updater.upsertEmail(accountUuid, "current@example.com", newer);
        boolean result = updater.upsertEmail(accountUuid, "stale@example.com", older);

        assertThat(result).as("the out-of-order guard must reject a stale write").isFalse();
        ContactProjection row = repository.findById(accountUuid).orElseThrow();
        assertThat(row.getEmail()).as("must still be the newer value").isEqualTo("current@example.com");
        assertThat(row.getUpdatedAt()).isEqualTo(newer);
    }

    /** Kimi Phase 8 Finding #4's own required transaction-join proof (AC3's own "same transaction"
     * requirement, mirrors IdempotencyGuardIntegrationTest's identical pattern). */
    @Test
    void upsertEmailJoinsAnExternallyOpenedTransactionAndRollsBackWithIt() {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(updater.upsertEmail(accountUuid, "rollback@example.com", occurredAt)).isTrue();
            status.setRollbackOnly();
        });

        assertThat(repository.existsById(accountUuid))
                .as("upsertEmail must join the caller's transaction, not commit independently")
                .isFalse();
    }

    @Test
    void upsertEmailCommitsWhenAnExternallyOpenedTransactionCommits() {
        UUID accountUuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-01-01T00:00:00Z");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(updater.upsertEmail(accountUuid, "commit@example.com", occurredAt)).isTrue());

        assertThat(repository.existsById(accountUuid)).isTrue();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
