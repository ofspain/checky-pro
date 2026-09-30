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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
     * ordering guarantee, so a stale, out-of-order write must never overwrite a newer projection.
     * Kimi Phase 11 Gap #5: also asserts directly via JDBC, not just the Hibernate read path, so
     * this proof isn't coupled to the persistence layer's own correctness. Kimi Phase 11 Gap #6:
     * also re-asserts display_name stays null after the rejected write. */
    @Test
    void olderCallDoesNotOverwriteANewerProjection() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        Instant newer = Instant.parse("2026-01-02T00:00:00Z");
        Instant older = Instant.parse("2026-01-01T00:00:00Z");

        updater.upsertEmail(accountUuid, "current@example.com", newer);
        boolean result = updater.upsertEmail(accountUuid, "stale@example.com", older);

        assertThat(result).as("the out-of-order guard must reject a stale write").isFalse();
        ContactProjection row = repository.findById(accountUuid).orElseThrow();
        assertThat(row.getEmail()).as("must still be the newer value").isEqualTo("current@example.com");
        assertThat(row.getUpdatedAt()).isEqualTo(newer);
        assertThat(row.getDisplayName()).as("display_name must stay null after a rejected write too").isNull();

        try (Connection admin = adminConnection();
             PreparedStatement select = admin.prepareStatement(
                     "SELECT email, updated_at FROM notifications.contact_projection WHERE account_uuid = ?")) {
            select.setObject(1, accountUuid);
            try (ResultSet resultSet = select.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("email"))
                        .as("JDBC-direct proof, independent of Hibernate's own read path")
                        .isEqualTo("current@example.com");
                assertThat(resultSet.getObject("updated_at", java.time.OffsetDateTime.class).toInstant())
                        .isEqualTo(newer);
            }
        }
    }

    /** Kimi Phase 11 Gap #2: the frozen brief explicitly chose {@code <=} over {@code <} so two
     * events with genuinely equal {@code occurredAt} resolve to the later-processed call winning -
     * an intentional, subtle semantic decision that no other test exercises at its own boundary. */
    @Test
    void equalOccurredAtTiesResolveToTheLaterProcessedCallWinning() {
        UUID accountUuid = UUID.randomUUID();
        Instant sameInstant = Instant.parse("2026-01-01T00:00:00Z");

        updater.upsertEmail(accountUuid, "first@example.com", sameInstant);
        boolean result = updater.upsertEmail(accountUuid, "second@example.com", sameInstant);

        assertThat(result).as("<= means a tie (equal occurredAt) is accepted, not rejected").isTrue();
        ContactProjection row = repository.findById(accountUuid).orElseThrow();
        assertThat(row.getEmail()).isEqualTo("second@example.com");
    }

    /** Kimi Phase 11 Gap #3: proves the affected-row-count semantics come from the native query
     * itself, not from {@code ContactProjectionUpdater}'s own boolean mapping - a change to the
     * repository's return type or Spring Data's own native-query row-count binding could otherwise
     * go undetected if the updater derived its result from a different signal. Calling the
     * repository directly (not through {@code ContactProjectionUpdater}, which supplies its own
     * {@code @Transactional}) needs an explicit {@code TransactionTemplate} - discovered empirically:
     * the first attempt at this test failed with {@code InvalidDataAccessApiUsage: No EntityManager
     * with actual transaction available}, since {@code flushAutomatically = true} requires an open
     * transaction to flush against. */
    @Test
    void repositoryUpsertEmailReturnsAffectedRowCountDirectly() {
        UUID accountUuid = UUID.randomUUID();
        Instant newer = Instant.parse("2026-01-02T00:00:00Z");
        Instant older = Instant.parse("2026-01-01T00:00:00Z");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(repository.upsertEmail(accountUuid, "current@example.com", newer)).isEqualTo(1));
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(repository.upsertEmail(accountUuid, "stale@example.com", older)).isEqualTo(0));
    }

    /** Kimi Phase 11 Gap #4: {@code CITEXT} is case-insensitive for comparison but must still
     * preserve the original case as stored - a misconfigured mapping/binding that silently
     * lowercased the value would not be caught by the lowercase-only emails every other test uses. */
    @Test
    void citextPreservesOriginalCaseOnReadBack() {
        UUID accountUuid = UUID.randomUUID();
        String mixedCaseEmail = "Owner@Example.COM";

        updater.upsertEmail(accountUuid, mixedCaseEmail, Instant.parse("2026-01-01T00:00:00Z"));

        ContactProjection row = repository.findById(accountUuid).orElseThrow();
        assertThat(row.getEmail())
                .as("citext must preserve original case, not lowercase it")
                .isEqualTo(mixedCaseEmail);
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

    /** T11's own first read path onto this projection - proves the absent case against a real
     * table, not just {@code Optional.empty()} by inspection. */
    @Test
    void findEmailReturnsEmptyWhenNoRowExists() {
        assertThat(updater.findEmail(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findEmailReturnsTheStoredEmailWhenPresent() {
        UUID accountUuid = UUID.randomUUID();
        updater.upsertEmail(accountUuid, "found@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(updater.findEmail(accountUuid)).contains("found@example.com");
    }

    /** T11 Kimi Phase 8 Finding #2: {@code findDisplayName} is a real read path even though no
     * application write path ever populates the column - set directly via JDBC here, the only way
     * to exercise a non-null value against this codebase's own current write surface. */
    @Test
    void findDisplayNameReturnsEmptyWhenNoRowExists() {
        assertThat(updater.findDisplayName(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findDisplayNameReturnsEmptyWhenARowExistsButDisplayNameIsStillNull() {
        UUID accountUuid = UUID.randomUUID();
        updater.upsertEmail(accountUuid, "noname@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        assertThat(updater.findDisplayName(accountUuid)).isEmpty();
    }

    @Test
    void findDisplayNameReturnsTheStoredValueWhenPresent() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        updater.upsertEmail(accountUuid, "named@example.com", Instant.parse("2026-01-01T00:00:00Z"));
        try (Connection admin = adminConnection();
             PreparedStatement update = admin.prepareStatement(
                     "UPDATE notifications.contact_projection SET display_name = ? WHERE account_uuid = ?::uuid")) {
            update.setString(1, "Ada Lovelace");
            update.setObject(2, accountUuid.toString());
            update.execute();
        }

        assertThat(updater.findDisplayName(accountUuid)).contains("Ada Lovelace");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
