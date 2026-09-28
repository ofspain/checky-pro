package com.themistra.notification.consumer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kimi Phase 8 Findings #2/#3: a committed, permanent version of the Phase 6/7 scratch test that
 * first proved {@link AuthEventConsumer}'s real listener wiring - a real message, produced to the
 * shared local Kafka broker (this module's own established convention, per
 * {@link IdempotencyGuardIntegrationTest}; no Testcontainers Kafka module is used anywhere in this
 * service), is consumed, deduped, projected, and dispatched (or not) exactly as
 * {@link AuthEventConsumerTest}'s mocked-collaborator tests already prove in isolation - this class
 * proves the real wiring reaches the same outcome. Uses its own {@code group-id}, distinct from the
 * default and from {@link AuthEventConsumerTransactionRollbackIntegrationTest}'s own, so it never
 * contends with another test class's consumer group.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.consumer.group-id=auth-event-consumer-it")
@Import(AuthEventConsumerIntegrationTest.SpyDispatcherConfig.class)
class AuthEventConsumerIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    record DispatchCall(UUID accountUuid, String notificationKind, Map<String, String> eventData) {}

    @TestConfiguration
    static class SpyDispatcherConfig {
        static final CopyOnWriteArrayList<DispatchCall> CALLS = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        NotificationDispatcher spyDispatcher() {
            return (accountUuid, notificationKind, eventData) ->
                    CALLS.add(new DispatchCall(accountUuid, notificationKind, eventData));
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
    private ProcessedEventRepository processedEventRepository;

    private static String emailForAccount(UUID accountUuid) throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT email FROM notifications.contact_projection WHERE account_uuid = '"
                             + accountUuid + "'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static boolean contactProjectionExists(UUID accountUuid) throws SQLException {
        return emailForAccount(accountUuid) != null;
    }

    @Test
    void emailRequestedVerifyEmailIsConsumedDedupedProjectedAndDispatched() {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-01T00:00:00Z").toString();
        String eventKey = accountUuid + ":verify_email:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"verify_email\","
                + "\"token\":\"raw-token-xyz\",\"email\":\"scratch@example.com\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventKey)).isTrue());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(emailForAccount(accountUuid)).isEqualTo("scratch@example.com"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(SpyDispatcherConfig.CALLS.stream()
                        .anyMatch(c -> c.accountUuid().equals(accountUuid)
                                && c.notificationKind().equals("verify_email")
                                && "raw-token-xyz".equals(c.eventData().get("token"))))
                        .isTrue());

        int callsBeforeRedelivery = SpyDispatcherConfig.CALLS.size();
        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(SpyDispatcherConfig.CALLS.size())
                        .as("redelivery of the same event must not re-dispatch")
                        .isEqualTo(callsBeforeRedelivery));
    }

    @Test
    void unknownPurposeIsDedupedAndProjectedButNotDispatched() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-02T00:00:00Z").toString();
        String eventKey = accountUuid + ":mystery_purpose:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"mystery_purpose\","
                + "\"token\":\"raw-token-abc\",\"email\":\"scratch2@example.com\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventKey)).isTrue());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(contactProjectionExists(accountUuid)).isTrue());
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(SpyDispatcherConfig.CALLS.stream()
                        .noneMatch(c -> c.accountUuid().equals(accountUuid)))
                        .isTrue());
    }

    @Test
    void userRegisteredLifecycleEventIsConsumedDedupedProjectedAndDispatched() {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-03T00:00:00Z").toString();
        String eventKey = accountUuid + ":user.registered:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"status\":\"ACTIVE\","
                + "\"email\":\"scratch3@example.com\",\"eventType\":\"user.registered\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.user.lifecycle", accountUuid.toString(), json);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventKey)).isTrue());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(emailForAccount(accountUuid)).isEqualTo("scratch3@example.com"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(SpyDispatcherConfig.CALLS.stream()
                        .anyMatch(c -> c.accountUuid().equals(accountUuid)
                                && c.notificationKind().equals("user.registered")
                                && c.eventData().isEmpty()))
                        .isTrue());
    }

    @Test
    void nonRegisteredLifecycleEventTypeIsDedupedAndProjectedButNotDispatched() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-04T00:00:00Z").toString();
        String eventKey = accountUuid + ":user.locked:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"status\":\"LOCKED\","
                + "\"email\":\"scratch4@example.com\",\"eventType\":\"user.locked\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.user.lifecycle", accountUuid.toString(), json);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(eventKey)).isTrue());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(contactProjectionExists(accountUuid)).isTrue());
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(SpyDispatcherConfig.CALLS.stream()
                        .noneMatch(c -> c.accountUuid().equals(accountUuid)))
                        .isTrue());
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
