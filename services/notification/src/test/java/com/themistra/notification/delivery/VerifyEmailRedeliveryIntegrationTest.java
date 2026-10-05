package com.themistra.notification.delivery;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.themistra.notification.channel.FakeEmailTransport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T17 (scoped to the {@code auth.email.requested(verify_email)} scenario only, per the Phase 0
 * user decision - {@code payments.receipt.issued} remains out of scope). Proves the one real gap
 * no existing test closes: {@link com.themistra.notification.consumer.AuthEventConsumerIntegrationTest}
 * proves Kafka-to-consumer wiring but replaces the real {@link DeliveryOrchestrator} (the production
 * {@code NotificationDispatcher}) with a spy; {@link DeliveryOrchestratorIntegrationTest} proves
 * {@code DeliveryOrchestrator} onward but calls {@code dispatch(...)} directly, never through Kafka.
 * This class imports no spy configuration - the real, unspied production wiring runs end to end.
 *
 * <p>No {@code channel_preference} row is inserted: {@code PreferenceResolver}'s own {@code DEFAULTS}
 * resolve both {@code SECURITY:EMAIL} and {@code SECURITY:IN_APP} to {@code true} with no stored row
 * at all, and {@code verify_email}'s category is {@code SECURITY} - so the simplest possible state
 * already produces the real two-channel dispatch ({@code email.verify} + {@code user.verify})
 * confirmed directly against {@code DeliveryOrchestrator}'s own {@code NotificationMapping}.</p>
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.consumer.group-id=verify-email-redelivery-it")
class VerifyEmailRedeliveryIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private FakeEmailTransport fakeEmailTransport;

    @BeforeEach
    void clearFakeEmailTransport() {
        fakeEmailTransport.clear();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static boolean processedEventExists(String eventKey) throws SQLException {
        try (Connection admin = adminConnection();
             PreparedStatement statement = admin.prepareStatement(
                     "SELECT 1 FROM notifications.processed_events WHERE event_key = ?")) {
            statement.setString(1, eventKey);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private record DeliveryLogRow(String channel, String outcome) {}

    private static List<DeliveryLogRow> deliveryLogRowsFor(UUID accountUuid) throws SQLException {
        try (Connection admin = adminConnection();
             PreparedStatement statement = admin.prepareStatement(
                     "SELECT channel, outcome FROM notifications.delivery_log WHERE account_uuid = ?")) {
            statement.setObject(1, accountUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DeliveryLogRow> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(new DeliveryLogRow(resultSet.getString("channel"), resultSet.getString("outcome")));
                }
                return rows;
            }
        }
    }

    @Test
    void verifyEmailRedeliveryProducesExactlyOneEmailAndNoDuplicateDeliveryLogRows() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-01T00:00:00Z").toString();
        String eventKey = accountUuid + ":verify_email:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"verify_email\","
                + "\"token\":\"raw-token-e2e\",\"email\":\"e2e@example.com\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventExists(eventKey)).isTrue());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(fakeEmailTransport.sentMessages().stream()
                        .filter(message -> accountUuid.equals(message.accountUuid()))
                        .count())
                        .isEqualTo(1));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(deliveryLogRowsFor(accountUuid))
                        .as("verify_email must write one EMAIL and one IN_APP row, both SENT")
                        .hasSize(2)
                        .allSatisfy(row -> assertThat(row.outcome()).isEqualTo("SENT"))
                        .extracting(DeliveryLogRow::channel)
                        .containsExactlyInAnyOrder("EMAIL", "IN_APP"));

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(fakeEmailTransport.sentMessages().stream()
                    .filter(message -> accountUuid.equals(message.accountUuid()))
                    .count())
                    .as("redelivery of the same event must not send a second email")
                    .isEqualTo(1);
            assertThat(deliveryLogRowsFor(accountUuid))
                    .as("redelivery of the same event must not write additional delivery_log rows")
                    .hasSize(2);
        });
    }
}
