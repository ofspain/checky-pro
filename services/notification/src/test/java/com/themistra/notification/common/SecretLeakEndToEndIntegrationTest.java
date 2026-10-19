package com.themistra.notification.common;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import com.themistra.notification.channel.EmailMessage;
import com.themistra.notification.channel.FakeEmailTransport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * {@code package.md} §8's named R15 test, added at T20. A real password-reset event goes through
 * Kafka, the real orchestrator, the real renderer and both real channels (email through the
 * capturing fake transport).
 *
 * <p>The raw reset token must appear only once, inside the intended one-time link in the email
 * body (R15's single permitted place). It must appear nowhere else: not in the in-app message, not
 * in the delivery log, and not in any log line this service writes, including DEBUG lines and
 * exception text.
 *
 * <p>This aggregates the narrower guards from T10 ({@link SecretSafeLoggingTest},
 * {@link SecretSafeLoggingIntegrationTest}) and T12 (recipient PII). It runs its own Kafka
 * container, so it never depends on the shared local broker.
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.consumer.group-id=secret-leak-e2e-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "themistra.notification.link.base-url=https://app.example.test",
        "themistra.notification.retry.scheduler-interval-seconds=999999"})
class SecretLeakEndToEndIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";
    private static final String RAW_TOKEN = "r15RawResetToken7f3a9c";
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

    /** Thread-safe capture: listener threads append while the test thread reads. */
    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {
        final List<String> lines = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            String throwable = event.getThrowableProxy() == null ? ""
                    : ThrowableProxyUtil.asString(event.getThrowableProxy());
            lines.add(event.getLoggerName() + " " + event.getFormattedMessage() + " " + throwable);
        }
    }

    private final CapturingAppender appender = new CapturingAppender();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger("com.themistra");
    private Level previousServiceLevel;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private FakeEmailTransport fakeEmailTransport;

    @BeforeEach
    void captureEveryLogLineIncludingDebug() {
        previousServiceLevel = serviceLogger.getLevel();
        serviceLogger.setLevel(Level.DEBUG);
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void stopCapturing() {
        rootLogger.detachAppender(appender);
        appender.stop();
        serviceLogger.setLevel(previousServiceLevel);
    }

    @Test
    void shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"password_reset\","
                + "\"token\":\"" + RAW_TOKEN + "\",\"email\":\"r15@example.com\","
                + "\"occurredAt\":\"2026-10-19T12:00:00Z\"}";

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(deliveryOutcomes(accountUuid)).containsExactlyInAnyOrder("EMAIL:SENT", "IN_APP:SENT"));

        List<EmailMessage> emails = fakeEmailTransport.sentMessages().stream()
                .filter(message -> accountUuid.equals(message.accountUuid())).toList();
        assertThat(emails).hasSize(1);
        String body = emails.get(0).body();
        assertThat(body).contains("https://app.example.test/reset-password?token=" + RAW_TOKEN);
        assertThat(occurrences(body, RAW_TOKEN)).as("the token appears only inside its one link").isEqualTo(1);
        assertThat(emails.get(0).subject()).doesNotContain(RAW_TOKEN);

        assertThat(inAppTexts(accountUuid)).isNotEmpty().allSatisfy(text -> assertThat(text).doesNotContain(RAW_TOKEN));
        assertThat(errorDetails(accountUuid)).allSatisfy(detail -> assertThat(detail).doesNotContain(RAW_TOKEN));

        List<String> logLines = new ArrayList<>(appender.lines);
        assertThat(logLines).as("the capture saw this service's own log output")
                .anySatisfy(line -> assertThat(line).contains("Email sent: accountUuid=" + accountUuid));
        assertThat(logLines).allSatisfy(line -> assertThat(line).doesNotContain(RAW_TOKEN));
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + 1)) {
            count++;
        }
        return count;
    }

    private static List<String> deliveryOutcomes(UUID accountUuid) throws SQLException {
        return queryStrings("SELECT channel || ':' || outcome FROM notifications.delivery_log WHERE account_uuid = ?",
                accountUuid);
    }

    private static List<String> errorDetails(UUID accountUuid) throws SQLException {
        return queryStrings("SELECT coalesce(error_detail, '') FROM notifications.delivery_log WHERE account_uuid = ?",
                accountUuid);
    }

    private static List<String> inAppTexts(UUID accountUuid) throws SQLException {
        return queryStrings("SELECT title || ' ' || body || ' ' || coalesce(link, '') "
                + "FROM notifications.inapp_notifications WHERE account_uuid = ?", accountUuid);
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
