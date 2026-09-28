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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Kimi Phase 8 Finding #4: proves the atomicity of {@link AuthEventConsumer}'s own
 * {@code @Transactional} listener methods - a failing {@code dispatch} must roll back the
 * preceding {@link IdempotencyGuard#recordIfNew} and
 * {@code ContactProjectionUpdater#upsertEmail} writes too, per Kimi Phase 3 Finding #3's own
 * frozen resolution. A separate test class (its own Spring context, its own {@code group-id}) so
 * its always-throwing {@link NotificationDispatcher} bean never collides with
 * {@link AuthEventConsumerIntegrationTest}'s own spy bean.
 */
@Testcontainers
@SpringBootTest(properties = "spring.kafka.consumer.group-id=auth-event-consumer-rollback-it")
@Import(AuthEventConsumerTransactionRollbackIntegrationTest.ThrowingDispatcherConfig.class)
class AuthEventConsumerTransactionRollbackIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @TestConfiguration
    static class ThrowingDispatcherConfig {
        @Bean
        @Primary
        NotificationDispatcher throwingDispatcher() {
            return (accountUuid, notificationKind, eventData) -> {
                throw new RuntimeException("simulated dispatch failure");
            };
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

    private static boolean contactProjectionExists(UUID accountUuid) throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT email FROM notifications.contact_projection WHERE account_uuid = '"
                             + accountUuid + "'")) {
            return resultSet.next();
        }
    }

    @Test
    void failingDispatchRollsBackTheIdempotencyRecordAndProjectionUpdate() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-05T00:00:00Z").toString();
        String eventKey = accountUuid + ":verify_email:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"verify_email\","
                + "\"token\":\"raw-token-rollback\",\"email\":\"scratch5@example.com\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        // The listener container's own default error handling (Spring Kafka's own retry/backoff,
        // frozen brief Finding #7) retries the poisoned record for a while before giving up - give
        // it room, then assert neither write survived the rolled-back transaction(s).
        await().pollDelay(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(processedEventRepository.existsById(eventKey))
                    .as("a rolled-back transaction must not leave an idempotency record")
                    .isFalse();
            assertThat(contactProjectionExists(accountUuid))
                    .as("a rolled-back transaction must not leave a projection row")
                    .isFalse();
        });
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
