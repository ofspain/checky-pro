package com.themistra.crypto.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC11 (frozen brief, amendment #5) — proves {@link OutboxPublisher#publish} joins the caller's
 * own transaction rather than starting its own: a rollback in the caller's {@code @Transactional}
 * method must leave no {@code outbox} row behind. Also covers Phase 11 Gaps 1, 6, 11 (duplicate
 * idempotency key, row-content assertions, the {@code @PrePersist} fallback guard). Deliberately a
 * narrow, hand-built Spring context (plain {@code @Configuration}, no component scanning) rather
 * than the full {@code CryptoServiceApplication} — this avoids booting {@link OutboxRelay}/{@link
 * KafkaProducerConfig}'s {@code @Scheduled}/Kafka machinery, which this test has no need of and no
 * real broker to reach.
 *
 * <p>Every test looks up its own row by a unique idempotency key (never by table-wide count/size)
 * because the Postgres container and its {@code outbox} table are shared across all test methods in
 * this class (a single static {@code @Container}), so rows committed by one test remain visible to
 * later ones in any execution order.</p>
 */
@Testcontainers
@SpringBootTest(classes = OutboxTransactionIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OutboxTransactionIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String CRYPTO_APP_PASSWORD = "it-crypto-app-password";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "crypto_app");
        registry.add("spring.datasource.password", () -> CRYPTO_APP_PASSWORD);
    }

    @BeforeAll
    static void migrateAndProvisionLocalPassword() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("chain")
                .load()
                .migrate();

        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE crypto_app PASSWORD '" + CRYPTO_APP_PASSWORD + "'");
        }
    }

    @Autowired
    private TransactionalPublishHarness harness;

    @Autowired
    private OutboxEventRepository repository;

    private static String uniqueKey() {
        return "it-key-" + UUID.randomUUID();
    }

    @Test
    void publishInsideARolledBackTransactionPersistsNoRow() {
        String key = uniqueKey();

        assertThatThrownBy(() -> harness.publishThenRollback(key))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced rollback");

        assertThat(repository.findByIdempotencyKey(key)).isEmpty();
    }

    @Test
    void publishInsideACommittedTransactionPersistsTheRowWithTheGivenContent() {
        String key = uniqueKey();

        harness.publishThenCommit(key, "it-watch-commit", "tx-seen", "chain.tx.seen");

        Optional<OutboxEvent> saved = repository.findByIdempotencyKey(key);
        assertThat(saved).isPresent();
        assertThat(saved.get().getAggregateType()).isEqualTo("tx-seen");
        assertThat(saved.get().getAggregateId()).isEqualTo("it-watch-commit");
        assertThat(saved.get().getEventType()).isEqualTo("chain.tx.seen");
        assertThat(saved.get().getIdempotencyKey()).isEqualTo(key);
        assertThat(saved.get().isPublished()).isFalse();
    }

    @Test
    void publishingTheSameIdempotencyKeyTwiceInSeparateTransactionsThrowsOnTheSecondAttempt() {
        String key = uniqueKey();

        harness.publishThenCommit(key, "it-watch-first", "tx-seen", "chain.tx.seen");

        assertThatThrownBy(() -> harness.publishThenCommit(key, "it-watch-second", "tx-seen", "chain.tx.seen"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // the first, successfully committed publish is untouched by the second attempt's failure
        Optional<OutboxEvent> saved = repository.findByIdempotencyKey(key);
        assertThat(saved).isPresent();
        assertThat(saved.get().getAggregateId()).isEqualTo("it-watch-first");
    }

    @Test
    void prePersistFallbackGuardOnlyFillsCreatedAtWhenAbsent() {
        // Bypasses OutboxPublisher entirely (which always sets createdAt itself) to exercise
        // OutboxEvent's @PrePersist fallback guard directly, on both sides of its null check.
        Instant explicitCreatedAt = Instant.parse("2020-01-01T00:00:00Z");
        String keyWithExplicitCreatedAt = uniqueKey();
        OutboxEvent withExplicitCreatedAt = OutboxEvent.create("tx-seen", "it-watch-a", "chain.tx.seen",
                keyWithExplicitCreatedAt, "{}", explicitCreatedAt);
        repository.save(withExplicitCreatedAt);

        String keyWithNullCreatedAt = uniqueKey();
        OutboxEvent withNullCreatedAt = OutboxEvent.create("tx-seen", "it-watch-b", "chain.tx.seen",
                keyWithNullCreatedAt, "{}", null);
        repository.save(withNullCreatedAt);

        assertThat(repository.findByIdempotencyKey(keyWithExplicitCreatedAt).orElseThrow().getCreatedAt())
                .as("@PrePersist must not override an already-set createdAt")
                .isEqualTo(explicitCreatedAt);
        assertThat(repository.findByIdempotencyKey(keyWithNullCreatedAt).orElseThrow().getCreatedAt())
                .as("@PrePersist fallback must fill a null createdAt")
                .isNotNull();
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = OutboxEvent.class)
    @EnableJpaRepositories(basePackageClasses = OutboxEventRepository.class)
    static class TestConfig {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        OutboxPublisher outboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
            return new OutboxPublisher(repository, objectMapper, clock);
        }

        @Bean
        TransactionalPublishHarness transactionalPublishHarness(OutboxPublisher publisher) {
            return new TransactionalPublishHarness(publisher);
        }
    }

    /** Test-only stand-in for a real feature-module service calling {@link OutboxPublisher}. */
    static class TransactionalPublishHarness {

        private final OutboxPublisher publisher;

        TransactionalPublishHarness(OutboxPublisher publisher) {
            this.publisher = publisher;
        }

        @Transactional
        void publishThenRollback(String idempotencyKey) {
            publisher.publish("tx-seen", "it-watch-rollback", "chain.tx.seen", idempotencyKey, Map.of("a", "b"));
            throw new RuntimeException("forced rollback");
        }

        @Transactional
        void publishThenCommit(String idempotencyKey, String aggregateId, String aggregateType, String eventType) {
            publisher.publish(aggregateType, aggregateId, eventType, idempotencyKey, Map.of("a", "b"));
        }
    }
}
