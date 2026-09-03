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
import org.springframework.data.domain.PageRequest;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC11 (frozen brief, amendment #5) — proves {@link OutboxPublisher#publish} joins the caller's
 * own transaction rather than starting its own: a rollback in the caller's {@code @Transactional}
 * method must leave no {@code outbox} row behind. Deliberately a narrow, hand-built Spring context
 * (plain {@code @Configuration}, no component scanning) rather than the full
 * {@code CryptoServiceApplication} — this avoids booting {@link OutboxRelay}/{@link
 * KafkaProducerConfig}'s {@code @Scheduled}/Kafka machinery, which this test has no need of and no
 * real broker to reach.
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

    @Test
    void publishInsideARolledBackTransactionPersistsNoRow() {
        assertThatThrownBy(harness::publishThenRollback)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("forced rollback");

        assertThat(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    void publishInsideACommittedTransactionPersistsTheRow() {
        harness.publishThenCommit();

        assertThat(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 10))).hasSize(1);
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
        void publishThenRollback() {
            publisher.publish("tx-seen", "it-watch-rollback", "chain.tx.seen",
                    "it-idempotency-key-rollback", Map.of("a", "b"));
            throw new RuntimeException("forced rollback");
        }

        @Transactional
        void publishThenCommit() {
            publisher.publish("tx-seen", "it-watch-commit", "chain.tx.seen",
                    "it-idempotency-key-commit", Map.of("a", "b"));
        }
    }
}
