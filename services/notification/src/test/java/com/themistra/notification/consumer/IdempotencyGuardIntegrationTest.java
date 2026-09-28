package com.themistra.notification.consumer;

import com.themistra.notification.preference.PreferenceResolver;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The first test class in this module needing a real Spring context (not just the Flyway-Java-API
 * style {@code NotificationBaselineMigrationIntegrationTest} uses), since proving
 * {@code @Transactional}'s real join-caller's-transaction behavior needs Spring's own transactional
 * proxy. Mirrors the uncommitted Phase 6 scratch verification that first caught the real design
 * flaws {@link IdempotencyGuard}'s own Javadoc documents, now made permanent and expanded per Kimi
 * Phase 8 Findings #1/#4/#5.
 */
@Testcontainers
@SpringBootTest
@Import(IdempotencyGuardIntegrationTest.FixedClockConfig.class)
class IdempotencyGuardIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    /** Kimi Phase 11 Gap #1: overrides {@code ClockConfig}'s own {@code Clock.systemUTC()} bean so
     * the round-trip test below can assert exact equality against a known instant, not just "close
     * to wall-clock time" - the latter would still pass even if {@link IdempotencyGuard} used
     * {@code Instant.now()} directly instead of the injected {@code Clock}. */
    @TestConfiguration
    static class FixedClockConfig {

        static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");

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
    private IdempotencyGuard guard;

    @Autowired
    private ProcessedEventRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Autowired
    private PreferenceResolver preferenceResolver;

    /** Kimi Phase 11 Gap #8: this class is the one place in the suite that boots a real Spring
     * context without overriding {@link NotificationDispatcher} with a test spy/throwing bean
     * (unlike {@code AuthEventConsumerIntegrationTest} and
     * {@code AuthEventConsumerTransactionRollbackIntegrationTest}) - so it is the only place that
     * can prove the real production bean is what actually gets component-scanned and wired. */
    @Test
    void theRealNoOpDispatcherIsTheResolvedSpringBean() {
        assertThat(notificationDispatcher).isInstanceOf(NoOpNotificationDispatcher.class);
    }

    /** T08 Kimi Phase 8 Finding #7: proves {@link PreferenceResolver} is actually component-scanned
     * and wireable, not just compiling - a dedicated fast check for a silent bean-scan regression,
     * mirroring the dispatcher proof above exactly. */
    @Test
    void preferenceResolverIsAResolvedSpringBean() {
        assertThat(preferenceResolver).isNotNull();
    }

    @Test
    void recordIfNewJoinsAnExternallyOpenedTransactionAndRollsBackWithIt() {
        String eventKey = "it-rollback-" + System.nanoTime();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(guard.recordIfNew(eventKey, "test.event")).isTrue();
            status.setRollbackOnly();
        });

        assertThat(repository.existsById(eventKey))
                .as("recordIfNew must join the caller's transaction, not commit independently (L1, AC3)")
                .isFalse();
    }

    @Test
    void recordIfNewCommitsWhenAnExternallyOpenedTransactionCommits() {
        String eventKey = "it-commit-" + System.nanoTime();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> assertThat(guard.recordIfNew(eventKey, "test.event")).isTrue());

        assertThat(repository.existsById(eventKey)).isTrue();
    }

    @Test
    void secondCallWithSameKeyReturnsFalseSerially() {
        String eventKey = "it-serial-" + System.nanoTime();

        assertThat(guard.recordIfNew(eventKey, "test.event")).isTrue();
        assertThat(guard.recordIfNew(eventKey, "test.event")).isFalse();
    }

    /** Kimi Phase 8 Finding #1's own required concurrent-call proof, mirroring the uncommitted
     * Phase 6 scratch test that originally caught the real design flaws documented in
     * {@link IdempotencyGuard}'s own Javadoc (a plain catch-and-return-false under default
     * {@code REQUIRED} propagation threw {@code UnexpectedRollbackException} for 7 of 8 threads;
     * {@code Propagation.NESTED} then failed with {@code NestedTransactionNotSupportedException}). */
    @Test
    void concurrentCallsWithSameKeyResolveToExactlyOneTrue() throws InterruptedException, SQLException {
        String eventKey = "it-concurrent-" + System.nanoTime();
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Boolean> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                results.add(guard.recordIfNew(eventKey, "test.event"));
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        boolean finishedCleanly = pool.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(finishedCleanly).as("all threads must finish without hanging").isTrue();
        assertThat(results)
                .as("no unchecked exception may escape recordIfNew - every thread must have recorded a result")
                .hasSize(threadCount);
        assertThat(results.stream().filter(r -> r).count()).as("exactly one true").isEqualTo(1);
        assertThat(results.stream().filter(r -> !r).count()).as("all others false").isEqualTo(threadCount - 1);

        // Kimi Phase 11 Gap #3: the boolean returns alone don't prove the DB itself ended up with
        // exactly one row - a pathological bug could return the right booleans while writing zero
        // or multiple rows for the same key.
        assertThat(repository.findById(eventKey)).isPresent();
        assertThat(countRowsWithEventKey(eventKey)).as("exactly one row for this key").isEqualTo(1);
    }

    /** Kimi Phase 8 Finding #4 / self-review Finding #3, tightened per Kimi Phase 11 Gaps #1/#6:
     * proves the persisted value is exactly the fixed clock's instant (not merely "recent"), and
     * that eventKey/eventType round-trip correctly too - a wall-clock range assertion would still
     * pass even if {@link IdempotencyGuard} used {@code Instant.now()} directly instead of the
     * injected {@code Clock}; {@link FixedClockConfig} closes that gap by making the injected clock
     * itself a known, fixed value. */
    @Test
    void processedAtRoundTripsFromTheInjectedClockThroughToAReadableRow() {
        String eventKey = "it-roundtrip-" + System.nanoTime();

        assertThat(guard.recordIfNew(eventKey, "test.event")).isTrue();

        ProcessedEvent stored = repository.findById(eventKey).orElseThrow();
        assertThat(stored.getEventKey()).isEqualTo(eventKey);
        assertThat(stored.getEventType()).isEqualTo("test.event");
        assertThat(stored.getProcessedAt()).isEqualTo(FixedClockConfig.FIXED_INSTANT);
    }

    /** Kimi Phase 11 Gaps #2/#7: a permanent, cheap static guard for the exact SQL shape the entire
     * concurrent-deduplication guarantee rests on - a future edit that reverts to a plain
     * {@code INSERT} would otherwise only be caught by re-running the Phase 10 manual mutation test
     * by hand. */
    @Test
    void insertIfNewUsesOnConflictDoNothing() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/themistra/notification/consumer/ProcessedEventRepository.java"));

        assertThat(source).contains("ON CONFLICT");
        assertThat(source).contains("DO NOTHING");
    }

    private long countRowsWithEventKey(String eventKey) throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT count(*) FROM notifications.processed_events WHERE event_key = '"
                             + eventKey + "'")) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
