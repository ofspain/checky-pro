package com.themistra.notification.delivery;

import com.themistra.notification.channel.EmailDeliveryException;
import com.themistra.notification.channel.EmailMessage;
import com.themistra.notification.channel.EmailTransport;
import com.themistra.notification.channel.FakeEmailTransport;
import com.themistra.notification.preference.ContactProjectionUpdater;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T19 - proves the dispute-grade property of {@code delivery_log} (R11, R12, R13, R10; L3, L9)
 * against a real database, a real grant, and the real delivery chain. No production code is changed.
 *
 * <p>Retry replay uses {@link RetryScheduler#processOne} on a real {@code delivery_retry} row, not
 * {@code sweep()}, because {@code sweep()} is ShedLock-guarded and can be silently skipped inside the
 * lock's minimum-hold window. The background scheduler is pushed to {@code 999999} as well.
 */
@Testcontainers
@SpringBootTest
@Import(DeliveryLogDisputeGradeIntegrationTest.ControllableEmailTransportConfig.class)
class DeliveryLogDisputeGradeIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @TestConfiguration
    static class ControllableEmailTransportConfig {
        @Bean
        @Primary
        ControllableEmailTransport controllableEmailTransport(FakeEmailTransport delegate) {
            return new ControllableEmailTransport(delegate);
        }
    }

    static class ControllableEmailTransport implements EmailTransport {
        private final FakeEmailTransport delegate;
        private final AtomicInteger failuresRemaining = new AtomicInteger(0);

        ControllableEmailTransport(FakeEmailTransport delegate) {
            this.delegate = delegate;
        }

        @Override
        public String send(EmailMessage message) {
            if (failuresRemaining.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new EmailDeliveryException("simulated transient transport failure");
            }
            return delegate.send(message);
        }

        void failNextAttempts(int count) {
            failuresRemaining.set(count);
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notification_app");
        registry.add("spring.datasource.password", () -> NOTIFICATION_APP_PASSWORD);
        registry.add("themistra.notification.retry.scheduler-interval-seconds", () -> "999999");
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
    private DeliveryOrchestrator orchestrator;

    @Autowired
    private RetryScheduler retryScheduler;

    @Autowired
    private DeliveryRetryRepository deliveryRetryRepository;

    @Autowired
    private ContactProjectionUpdater contactProjectionUpdater;

    @Autowired
    private FakeEmailTransport fakeEmailTransport;

    @Autowired
    private ControllableEmailTransport controllableEmailTransport;

    @BeforeEach
    void resetSharedState() {
        fakeEmailTransport.clear();
        controllableEmailTransport.failNextAttempts(0);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection notificationAppConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "notification_app", NOTIFICATION_APP_PASSWORD);
    }

    private record LogRow(long id, UUID accountUuid, String channel, String outcome, short attempt,
                          Integer templateVersion, String recipient, String sourceEventKey, Instant createdAt) {}

    private static List<LogRow> deliveryLogRowsFor(String sourceEventKey) throws SQLException {
        try (Connection admin = adminConnection();
             PreparedStatement statement = admin.prepareStatement(
                     "SELECT id, account_uuid, channel, outcome, attempt, template_version, recipient,"
                             + " source_event_key, created_at FROM notifications.delivery_log"
                             + " WHERE source_event_key = ? ORDER BY id")) {
            statement.setString(1, sourceEventKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LogRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    Object templateVersion = resultSet.getObject("template_version");
                    rows.add(new LogRow(
                            resultSet.getLong("id"),
                            resultSet.getObject("account_uuid", UUID.class),
                            resultSet.getString("channel"),
                            resultSet.getString("outcome"),
                            resultSet.getShort("attempt"),
                            templateVersion == null ? null : ((Number) templateVersion).intValue(),
                            resultSet.getString("recipient"),
                            resultSet.getString("source_event_key"),
                            resultSet.getTimestamp("created_at").toInstant()));
                }
                return rows;
            }
        }
    }

    private static void insertChannelPreference(UUID accountUuid, String category, String channel, boolean enabled)
            throws SQLException {
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
    void deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String sourceEventKey = "dispute-ac1-" + accountUuid;
        contactProjectionUpdater.upsertEmail(accountUuid, "ac1@example.com", Instant.now());
        orchestrator.dispatch(accountUuid, "verify_email",
                Map.of("token", "tok-ac1", "sourceEventKey", sourceEventKey));
        LogRow row = deliveryLogRowsFor(sourceEventKey).get(0);

        try (Connection app = notificationAppConnection();
             PreparedStatement update = app.prepareStatement(
                     "UPDATE notifications.delivery_log SET outcome = 'FAILED' WHERE id = ?")) {
            update.setLong(1, row.id());
            assertThatThrownBy(update::executeUpdate)
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo("42501");
        }

        try (Connection app = notificationAppConnection();
             PreparedStatement delete = app.prepareStatement(
                     "DELETE FROM notifications.delivery_log WHERE id = ?")) {
            delete.setLong(1, row.id());
            assertThatThrownBy(delete::executeUpdate)
                    .isInstanceOf(PSQLException.class)
                    .extracting(e -> ((PSQLException) e).getSQLState())
                    .isEqualTo("42501");
        }

        assertThat(deliveryLogRowsFor(sourceEventKey))
                .as("the rejected UPDATE and DELETE must leave the row exactly as written")
                .hasSize(2)
                .anySatisfy(r -> {
                    assertThat(r.id()).isEqualTo(row.id());
                    assertThat(r.outcome()).isEqualTo(row.outcome());
                });
    }

    @Test
    void retryChainAppendsMultipleRowsForTheSameSourceEventKey() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String sourceEventKey = "dispute-ac2-" + accountUuid;
        contactProjectionUpdater.upsertEmail(accountUuid, "ac2@example.com", Instant.now());

        controllableEmailTransport.failNextAttempts(1);
        orchestrator.dispatch(accountUuid, "verify_email",
                Map.of("token", "tok-ac2", "sourceEventKey", sourceEventKey));

        DeliveryRetry retry = deliveryRetryRepository.findAll().stream()
                .filter(r -> sourceEventKey.equals(r.getSourceEventKey()) && "EMAIL".equals(r.getChannel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("a transient EMAIL failure must schedule a delivery_retry row"));
        retryScheduler.processOne(retry);

        List<LogRow> rows = deliveryLogRowsFor(sourceEventKey);
        List<LogRow> emailRows = rows.stream().filter(r -> "EMAIL".equals(r.channel())).toList();

        assertThat(rows)
                .as("every row for one source event must carry that event's key")
                .allSatisfy(r -> assertThat(r.sourceEventKey()).isEqualTo(sourceEventKey));
        assertThat(emailRows)
                .as("the failed first attempt and the successful replay must both be recorded")
                .hasSize(2)
                .extracting(LogRow::outcome)
                .containsExactlyInAnyOrder("FAILED", "SENT");
        assertThat(emailRows.stream().map(LogRow::attempt).distinct().count())
                .as("the two EMAIL attempts must be distinguishable by attempt number")
                .isEqualTo(2);
        assertThat(emailRows)
                .as("each attempt must carry recipient, timestamp and, because a message was rendered, a template version")
                .allSatisfy(r -> {
                    assertThat(r.recipient()).isEqualTo("ac2@example.com");
                    assertThat(r.createdAt()).isNotNull();
                    assertThat(r.templateVersion()).isNotNull();
                });
    }

    @Test
    void suppressedChannelLeavesASuppressedDeliveryLogRow() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String sourceEventKey = "dispute-ac4-" + accountUuid;
        insertChannelPreference(accountUuid, "PAYMENT", "EMAIL", false);

        orchestrator.dispatch(accountUuid, "invoice.created", Map.of("sourceEventKey", sourceEventKey));

        List<LogRow> rows = deliveryLogRowsFor(sourceEventKey);
        assertThat(rows)
                .as("the opted-out EMAIL channel must leave a SUPPRESSED row, not silence")
                .anySatisfy(r -> {
                    assertThat(r.channel()).isEqualTo("EMAIL");
                    assertThat(r.outcome()).isEqualTo("SUPPRESSED");
                });
        assertThat(rows)
                .as("the opted-in IN_APP channel must still produce its own row")
                .anySatisfy(r -> assertThat(r.channel()).isEqualTo("IN_APP"));
        assertThat(fakeEmailTransport.sentMessages())
                .as("a suppressed channel must not send")
                .noneMatch(m -> accountUuid.equals(m.accountUuid()));
    }

    @Test
    void noApplicationCodeUpdatesOrDeletesDeliveryLogRows() throws IOException {
        Pattern rawMutation = Pattern.compile(
                "(?i)\\b(update|delete\\s+from)\\s+(notifications\\.)?delivery_log\\b");
        Pattern repoMutation = Pattern.compile("deliveryLogRepository\\s*\\.\\s*delete\\w*\\s*\\(");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (rawMutation.matcher(source).find() || repoMutation.matcher(source).find()) {
                    offenders.add(file.toString());
                }
            }
        }
        assertThat(offenders)
                .as("no production code may issue UPDATE/DELETE against delivery_log or delete through its repository")
                .isEmpty();
    }
}
