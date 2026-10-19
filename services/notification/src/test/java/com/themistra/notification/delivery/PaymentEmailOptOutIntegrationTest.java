package com.themistra.notification.delivery;

import com.themistra.notification.channel.FakeEmailTransport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T20: the buildable half of T17's deferred payment scenario, end to end. "Opt a recipient out of
 * PAYMENT email → that channel is {@code SUPPRESSED}, in-app still delivered" (R10, L6).
 *
 * <p>A real {@code payments.invoice.created} goes through Kafka, {@code PaymentEventConsumer}, the
 * real orchestrator, the preference resolver and both channels. The merchant has a contact and an
 * explicit {@code PAYMENT:EMAIL} opt-out.
 *
 * <p>T17's "both parties" part can't be built: there is no payer identity (T07 D1, FU-T07-1). Its
 * "with a receipt link" part has been built since 2026-10-19 (FU-T07-2) and is tested in
 * {@code PaymentEventConsumerIntegrationTest}.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.consumer.group-id=payment-email-opt-out-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "themistra.notification.link.base-url=https://app.example.test",
        "themistra.notification.retry.scheduler-interval-seconds=999999"})
class PaymentEmailOptOutIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";
    private static final List<String> TOPICS = List.of(
            "auth.email.requested", "auth.user.lifecycle", "payments.invoice.created",
            "payments.payment.seen", "payments.payment.finalized", "payments.receipt.issued");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notification_app");
        registry.add("spring.datasource.password", () -> NOTIFICATION_APP_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void migrateProvisionPasswordAndCreateTopics()
            throws SQLException, InterruptedException, ExecutionException, TimeoutException {
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
        try (AdminClient kafkaAdmin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            kafkaAdmin.createTopics(TOPICS.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
                    .all().get(30, TimeUnit.SECONDS);
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private FakeEmailTransport fakeEmailTransport;

    @Test
    void shouldSuppressOptedOutPaymentEmailButStillDeliverInApp() throws SQLException {
        UUID merchant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("INSERT INTO notifications.contact_projection (account_uuid, email) VALUES ('"
                    + merchant + "', 'merchant-optout@example.com')");
            statement.execute("INSERT INTO notifications.channel_preferences (account_uuid, category, channel, enabled) "
                    + "VALUES ('" + merchant + "', 'PAYMENT', 'EMAIL', false)");
        }

        kafkaTemplate.send("payments.invoice.created", invoice.toString(), "{\"invoiceUuid\":\"" + invoice
                + "\",\"merchantAccountUuid\":\"" + merchant + "\",\"chain\":\"ethereum\","
                + "\"tokenContractAddress\":\"0x" + "11".repeat(20) + "\",\"tokenDecimals\":6,"
                + "\"amount\":\"1500000\",\"payToAddress\":\"0x" + "22".repeat(20)
                + "\",\"reference\":null,\"expiresAt\":\"2026-11-01T00:00:00Z\"}");

        String sourceEventKey = "invoice.created:" + invoice;
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(queryStrings("SELECT channel || ':' || outcome || ':' || source_event_key "
                        + "FROM notifications.delivery_log WHERE account_uuid = ?", merchant))
                        .containsExactlyInAnyOrder(
                                "EMAIL:SUPPRESSED:" + sourceEventKey,
                                "IN_APP:SENT:" + sourceEventKey));

        assertThat(fakeEmailTransport.sentMessages())
                .as("an opted-out PAYMENT email is never handed to the transport")
                .noneMatch(message -> merchant.equals(message.accountUuid()));
        assertThat(queryStrings("SELECT category || ':' || body FROM notifications.inapp_notifications "
                + "WHERE account_uuid = ?", merchant))
                .singleElement()
                .satisfies(row -> assertThat(row).startsWith("PAYMENT:").contains("1.5"));
    }

    private static List<String> queryStrings(String sql, UUID accountUuid) throws SQLException {
        try (Connection admin = adminConnection(); PreparedStatement statement = admin.prepareStatement(sql)) {
            statement.setObject(1, accountUuid);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (resultSet.next()) {
                    values.add(resultSet.getString(1));
                }
                return values;
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
