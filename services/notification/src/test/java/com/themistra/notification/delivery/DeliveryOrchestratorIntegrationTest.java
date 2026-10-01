package com.themistra.notification.delivery;

import com.themistra.notification.channel.EmailDeliveryException;
import com.themistra.notification.channel.EmailMessage;
import com.themistra.notification.channel.EmailTransport;
import com.themistra.notification.channel.FakeEmailTransport;
import com.themistra.notification.channel.NotificationChannel;
import com.themistra.notification.common.config.EmailProperties;
import com.themistra.notification.preference.ContactProjectionUpdater;
import com.themistra.notification.template.TemplateRenderer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.atomic.AtomicInteger;

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
@Import({DeliveryOrchestratorIntegrationTest.FixedClockConfig.class,
        DeliveryOrchestratorIntegrationTest.ControllableEmailTransportConfig.class})
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

    /** T14: a controllable {@link EmailTransport}, {@code @Primary} over the real
     * {@code FakeEmailTransport} bean (a single-injection-point override into {@code EmailChannel}'s
     * own constructor, not a competing bean in a {@code List<NotificationChannel>} collection -
     * mirrors {@code InAppChannelIntegrationTest}'s own {@code InappStreamRegistry} spy-override
     * precedent, T13) - lets {@link #shouldMarkDeliveryFailedAndScheduleRetryOnTransientError} and
     * {@link #shouldStopRetryingAndDeadLetterAfterMaxAttempts} deterministically fail a real send
     * exactly N times before succeeding/never succeeding, without modifying
     * {@code EmailChannel}/{@code FakeEmailTransport} (both out of this task's own authorized
     * "Files NOT to Modify"). On a non-forced-failure call, delegates to the real
     * {@code FakeEmailTransport} bean, so every pre-existing test's own assertions against
     * {@code fakeEmailTransport.sentMessages()} are completely unaffected. */
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

    @Autowired
    private FakeEmailTransport fakeEmailTransport;

    @Autowired
    private EmailProperties emailProperties;

    @Autowired
    private ControllableEmailTransport controllableEmailTransport;

    @Autowired
    private DeliveryRetryRepository deliveryRetryRepository;

    @Autowired
    private RetryScheduler retryScheduler;

    @Autowired
    private com.themistra.notification.common.config.RetryProperties retryProperties;

    /** Kimi Phase 11 Gap #1: {@code FakeEmailTransport} is a Spring singleton shared across every
     * test in this class - without clearing it, an assertion on its own captured messages would be
     * polluted by whichever earlier test happened to run first (JUnit does not reset Spring's own
     * singleton beans between test methods). T14: {@code controllableEmailTransport}'s own
     * {@code failuresRemaining} must reset to zero for the same reason. */
    @BeforeEach
    void clearFakeEmailTransport() {
        fakeEmailTransport.clear();
        controllableEmailTransport.failNextAttempts(0);
    }

    private DeliveryRetry retryRowFor(UUID accountUuid, String channel) {
        return deliveryRetryRepository.findAll().stream()
                .filter(row -> accountUuid.equals(row.getAccountUuid()) && channel.equals(row.getChannel()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no delivery_retry row for channel=" + channel));
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

    /** Kimi Phase 11 Gap #1: the single most load-bearing T12 scenario - proving a real dispatched
     * event results in a real (fake-sent) captured email, not merely a {@code SENT} delivery_log
     * row. Every other test in this class already exercises this same path implicitly, but none of
     * them inspected {@code FakeEmailTransport.sentMessages()} until now. */
    @Test
    void dispatchCapturesARealSentEmailWithCorrectFields() {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "captured@example.com", Instant.parse("2026-01-01T00:00:00Z"));

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("token", "tok-captured", "sourceEventKey", "real-key-9"));

        assertThat(fakeEmailTransport.sentMessages()).hasSize(1);
        var captured = fakeEmailTransport.sentMessages().get(0);
        assertThat(captured.accountUuid()).isEqualTo(accountUuid);
        assertThat(captured.to()).isEqualTo("captured@example.com");
        assertThat(captured.from()).isEqualTo(emailProperties.from());
        assertThat(captured.subject()).isEqualTo("Verify your Themistra account");
        assertThat(captured.body()).contains("token=tok-captured");
    }

    /** Kimi Phase 11 Gap #9: no test booted the real context and asserted {@code EmailProperties}
     * binds from the real {@code application.properties} file's own actual values - every other
     * proof was either a synthetic {@code ApplicationContextRunner} value (T03's own
     * {@code EmailPropertiesTest}) or only implicit (the fact this whole class's own context boots
     * at all already requires a valid {@code transport} value to exist, per
     * {@code EmailTransportStartupValidation}). This names the real, current default explicitly. */
    @Test
    void emailPropertiesBindsFromTheRealApplicationPropertiesFile() {
        assertThat(emailProperties.from()).isEqualTo("no-reply@checky.pro");
        assertThat(emailProperties.transport()).isEqualTo("fake");
    }

    /** R12 named test - a real transient EMAIL transport failure (the controllable transport's own
     * first forced failure) writes a FAILED row and schedules a real {@code delivery_retry} row;
     * directly invoking {@code retryScheduler.processOne} (same package, not waiting on
     * {@code @Scheduled}'s own real timing) then replays it successfully, proving the full
     * FAILED-then-SENT lifecycle against a real Postgres transaction, not mocked collaborators. */
    @Test
    void shouldMarkDeliveryFailedAndScheduleRetryOnTransientError() {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "retry1@example.com", Instant.parse("2026-01-01T00:00:00Z"));
        controllableEmailTransport.failNextAttempts(1);

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("token", "tok-r1", "sourceEventKey", "retry-key-1"));

        DeliveryLog firstAttempt = rowFor(accountUuid, "EMAIL");
        assertThat(firstAttempt.getOutcome()).isEqualTo("FAILED");
        assertThat(firstAttempt.getAttempt()).isEqualTo((short) 1);

        DeliveryRetry retry = retryRowFor(accountUuid, "EMAIL");
        assertThat(retry.getAttempt()).isEqualTo((short) 1);
        assertThat(retry.getNextAttemptAt())
                .isEqualTo(FixedClockConfig.FIXED_INSTANT.plusSeconds(retryProperties.initialBackoffSeconds()));
        assertThat(retry.getNotificationKind()).isEqualTo("verify_email");

        retryScheduler.processOne(retry);

        List<DeliveryLog> emailRows = rowsFor(accountUuid).stream()
                .filter(row -> "EMAIL".equals(row.getChannel())).toList();
        assertThat(emailRows).hasSize(2);
        DeliveryLog secondAttempt = emailRows.stream().filter(r -> r.getAttempt() == (short) 2).findFirst().orElseThrow();
        assertThat(secondAttempt.getOutcome()).isEqualTo("SENT");
        assertThat(deliveryRetryRepository.findAll().stream().anyMatch(r -> accountUuid.equals(r.getAccountUuid())))
                .as("the retry row must be gone once the replay succeeds")
                .isFalse();
    }

    /** R13 named test - a transport that always fails exhausts the real, configured
     * {@code retryProperties.maxAttempts()} and dead-letters on the final one, never scheduling
     * another retry past it. Drives {@code retryScheduler.processOne} directly, the same number of
     * times a real scheduler sweep would across real elapsed time. */
    @Test
    void shouldStopRetryingAndDeadLetterAfterMaxAttempts() {
        UUID accountUuid = UUID.randomUUID();
        contactProjectionUpdater.upsertEmail(accountUuid, "retry2@example.com", Instant.parse("2026-01-01T00:00:00Z"));
        controllableEmailTransport.failNextAttempts(Integer.MAX_VALUE);

        orchestrator.dispatch(accountUuid, "verify_email", Map.of("sourceEventKey", "retry-key-2"));

        int maxAttempts = retryProperties.maxAttempts();
        for (int attemptsAlreadyMade = 1; attemptsAlreadyMade < maxAttempts; attemptsAlreadyMade++) {
            DeliveryRetry retry = retryRowFor(accountUuid, "EMAIL");
            assertThat(retry.getAttempt()).isEqualTo((short) attemptsAlreadyMade);
            retryScheduler.processOne(retry);
        }

        List<DeliveryLog> emailRows = rowsFor(accountUuid).stream()
                .filter(row -> "EMAIL".equals(row.getChannel())).toList();
        assertThat(emailRows).as("exactly maxAttempts rows: the original plus every replay").hasSize(maxAttempts);
        assertThat(emailRows.stream().filter(r -> "FAILED".equals(r.getOutcome()))).hasSize(maxAttempts - 1);
        DeliveryLog finalRow = emailRows.stream().filter(r -> r.getAttempt() == (short) maxAttempts).findFirst().orElseThrow();
        assertThat(finalRow.getOutcome()).isEqualTo("DEAD_LETTERED");
        assertThat(deliveryRetryRepository.findAll().stream().anyMatch(r -> accountUuid.equals(r.getAccountUuid())))
                .as("no retry row must survive past exhaustion")
                .isFalse();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
