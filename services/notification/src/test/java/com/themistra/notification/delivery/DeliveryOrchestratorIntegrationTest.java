package com.themistra.notification.delivery;

import com.themistra.notification.channel.NotificationChannel;
import com.themistra.notification.preference.ContactProjectionUpdater;
import com.themistra.notification.template.TemplateRenderer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres (Testcontainers), real Spring context, real collaborators - no mocks. Mirrors the
 * uncommitted Phase 6/7 scratch verification (5 end-to-end scenarios, all passed) now made
 * permanent, plus the R10/R11 named tests (`package.md` §8) and AC10's own transaction-join proof
 * (mirrors {@code IdempotencyGuardIntegrationTest}/{@code ContactProjectionUpdaterIntegrationTest}'s
 * identical pattern). Placed in {@code delivery} (Kimi Phase 8 Finding #3, rejected as stated -
 * {@link DeliveryLogRepository} stays package-private, matching every sibling repository; this test
 * lives in the same package instead, exactly like every other module's own integration test).
 */
@Testcontainers
@SpringBootTest
@Import(DeliveryOrchestratorIntegrationTest.FixedClockConfig.class)
class DeliveryOrchestratorIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @TestConfiguration
    static class FixedClockConfig {
        static final Instant FIXED_INSTANT = Instant.parse("2026-02-01T00:00:00Z");

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
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
    private DeliveryOrchestrator orchestrator;

    @Autowired
    private DeliveryLogRepository deliveryLogRepository;

    @Autowired
    private ContactProjectionUpdater contactProjectionUpdater;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private List<NotificationChannel> notificationChannels;

    @Autowired
    private TemplateRenderer templateRenderer;

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

    private static void setDisplayName(UUID accountUuid, String displayName) throws SQLException {
        try (Connection admin = adminConnection();
             PreparedStatement update = admin.prepareStatement(
                     "UPDATE notifications.contact_projection SET display_name = ? WHERE account_uuid = ?::uuid")) {
            update.setString(1, displayName);
            update.setObject(2, accountUuid.toString());
            update.execute();
        }
    }

    private List<DeliveryLog> rowsFor(UUID accountUuid) {
        return deliveryLogRepository.findAll().stream()
                .filter(row -> accountUuid.equals(row.getAccountUuid()))
                .toList();
    }

    private DeliveryLog rowFor(UUID accountUuid, String channel) {
        return rowsFor(accountUuid).stream()
                .filter(row -> channel.equals(row.getChannel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no delivery_log row for channel=" + channel));
    }

    /** R11 named test - proves every required field lands in a real, persisted row via a real
     * end-to-end render (real seeded {@code email.verify}/{@code user.verify} templates, T02's own
     * {@code V3__seed_launch_templates.sql}). */
    @Test
    void shouldRecordEveryDeliveryAttemptAndOutcomeInLog() {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "real@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("token", "tok-real", "sourceEventKey", "real-key-1"));

        DeliveryLog emailRow = rowFor(accountUuid, "EMAIL");
        assertThat(emailRow.getRecipient()).isEqualTo("real@example.com");
        assertThat(emailRow.getSourceEventKey()).isEqualTo("real-key-1");
        assertThat(emailRow.getTemplateName()).isEqualTo("email.verify");
        assertThat(emailRow.getTemplateVersion()).isEqualTo(1);
        assertThat(emailRow.getOutcome()).isEqualTo("SENT");
        assertThat(emailRow.getErrorDetail()).isNull();
        assertThat(emailRow.getAttempt()).isEqualTo((short) 1);
        assertThat(emailRow.getCreatedAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);

        DeliveryLog inAppRow = rowFor(accountUuid, "IN_APP");
        assertThat(inAppRow.getRecipient()).isEqualTo(accountUuid.toString());
        assertThat(inAppRow.getTemplateName()).isEqualTo("user.verify");
        assertThat(inAppRow.getTemplateVersion()).isEqualTo(1);
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");
    }

    /** R10 named test - a stored opt-out reaches the delivery log, not merely
     * {@code PreferenceResolver}'s own return value. Uses {@code invoice.created}/{@code PAYMENT},
     * not {@code verify_email}/{@code SECURITY} - {@code SECURITY}+{@code EMAIL} is design.md §4c's
     * own hard floor and can never be suppressed. */
    @Test
    void shouldSuppressChannelWhenRecipientOptedOut() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "real2@example.com", Instant.parse("2026-01-01T00:00:00Z"));
        insertChannelPreference(accountUuid, "PAYMENT", "EMAIL", false);

        orchestrator.dispatch(accountUuid, "invoice.created", Map.of("sourceEventKey", "real-key-2"));

        DeliveryLog emailRow = rowFor(accountUuid, "EMAIL");
        assertThat(emailRow.getOutcome()).isEqualTo("SUPPRESSED");
        assertThat(emailRow.getTemplateName()).isNull();
        assertThat(emailRow.getTemplateVersion()).isNull();

        DeliveryLog inAppRow = rowFor(accountUuid, "IN_APP");
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");
    }

    @Test
    void missingContactProjectionFailsEmailChannelButInAppStillProceeds() {
        UUID accountUuid = UUID.randomUUID();

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "real-key-3"));

        DeliveryLog emailRow = rowFor(accountUuid, "EMAIL");
        assertThat(emailRow.getOutcome()).isEqualTo("FAILED");
        assertThat(emailRow.getRecipient()).isNull();
        assertThat(emailRow.getErrorDetail()).isEqualTo("no recipient email on file");

        DeliveryLog inAppRow = rowFor(accountUuid, "IN_APP");
        assertThat(inAppRow.getOutcome()).isEqualTo("SENT");
        assertThat(inAppRow.getRecipient()).isEqualTo(accountUuid.toString());
    }

    @Test
    void unknownNotificationKindWritesNoRowsInARealDatabase() {
        UUID accountUuid = UUID.randomUUID();

        orchestrator.dispatch(accountUuid, "does.not.exist", Map.of("sourceEventKey", "real-key-4"));

        assertThat(rowsFor(accountUuid)).isEmpty();
    }

    /** Kimi Phase 8 Finding #2's own resolution, end-to-end: a real {@code display_name} value
     * (set directly via JDBC - no application write path sets it today) round-trips through
     * {@code ContactProjectionUpdater.findDisplayName} and {@code DeliveryOrchestrator}'s own merge
     * into the real {@link com.themistra.notification.template.TemplateRenderer#render} call
     * without error. */
    @Test
    void dispatchSucceedsEndToEndWhenDisplayNameIsPopulated() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "real5@example.com", Instant.parse("2026-01-01T00:00:00Z"));
        setDisplayName(accountUuid, "Ada Lovelace");

        orchestrator.dispatch(accountUuid, "user.registered", Map.of("sourceEventKey", "real-key-5"));

        assertThat(rowFor(accountUuid, "EMAIL").getOutcome()).isEqualTo("SENT");
        assertThat(rowFor(accountUuid, "IN_APP").getOutcome()).isEqualTo("SENT");
    }

    /** AC10: joins the external caller's own transaction - a rollback there rolls back every
     * {@code delivery_log} row {@code dispatch} wrote, mirroring
     * {@code IdempotencyGuardIntegrationTest}'s identical pattern exactly. */
    @Test
    void deliveryLogRowsRollBackIfTheExternalCallersTransactionRollsBack() {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "real6@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "real-key-6"));
            status.setRollbackOnly();
        });

        assertThat(rowsFor(accountUuid))
                .as("a rolled-back external transaction must leave no delivery_log row behind")
                .isEmpty();
    }

    @Test
    void deliveryLogRowsCommitWhenTheExternalCallersTransactionCommits() {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "real7@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "real-key-7")));

        assertThat(rowsFor(accountUuid)).hasSize(2);
    }

    /** Kimi Phase 11 Gap #2 (item 3): the whole task depends on exactly two {@code NotificationChannel}
     * beans being collected into {@code DeliveryOrchestrator}'s own {@code channelsByName} map - a
     * silent component-scan regression (e.g. a missing {@code @Component}) would otherwise only
     * surface as a confusing "no channel bean registered" delivery_log row, not a test failure. */
    @Test
    void exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames() {
        assertThat(notificationChannels).hasSize(2);
        assertThat(notificationChannels.stream().map(NotificationChannel::channel))
                .containsExactlyInAnyOrder("EMAIL", "IN_APP");
    }

    /** Kimi Phase 11 Gap #6: {@code delivery_log} never persists the rendered body, so
     * {@code dispatchSucceedsEndToEndWhenDisplayNameIsPopulated} above only proves the pipeline
     * completes without error, not that the placeholder was actually substituted. This test
     * replicates {@code DeliveryOrchestrator}'s own real {@code findDisplayName}-then-merge step
     * against the real {@link TemplateRenderer} bean it uses internally, proving the substitution
     * itself really happens - a real, disclosed scope narrowing (it does not call {@code dispatch}
     * itself), not a full substitute for it. */
    @Test
    void displayNameActuallySubstitutesIntoARealRenderedBody() throws SQLException {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "realbody@example.com", Instant.parse("2026-01-01T00:00:00Z"));
        setDisplayName(accountUuid, "Ada Lovelace");

        String displayName = contactProjectionUpdater.findDisplayName(accountUuid).orElseThrow();
        Map<String, String> renderData = new java.util.HashMap<>(Map.of("sourceEventKey", "real-key-8"));
        renderData.put("displayName", displayName);

        TemplateRenderer.RenderedMessage rendered = templateRenderer.render("user.welcome", "EMAIL", renderData);

        assertThat(rendered.body()).contains("Ada Lovelace");
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
