package com.themistra.notification.consumer;

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
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
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
class IdempotencyGuardIntegrationTest {

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
    private IdempotencyGuard guard;

    @Autowired
    private ProcessedEventRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    void concurrentCallsWithSameKeyResolveToExactlyOneTrue() throws InterruptedException {
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
    }

    /** Kimi Phase 8 Finding #4 / self-review Finding #3: proves the persisted value round-trips
     * through the native query's parameter binding, not just that some non-null value exists. */
    @Test
    void processedAtRoundTripsFromTheInjectedClockThroughToAReadableRow() {
        String eventKey = "it-roundtrip-" + System.nanoTime();
        Instant before = Instant.now();

        assertThat(guard.recordIfNew(eventKey, "test.event")).isTrue();

        Instant after = Instant.now();
        Instant stored = repository.findById(eventKey).orElseThrow().getProcessedAt();

        assertThat(stored).isBetween(before.minusSeconds(1), after.plusSeconds(1));
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
