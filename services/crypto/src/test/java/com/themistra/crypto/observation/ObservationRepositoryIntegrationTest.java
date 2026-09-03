package com.themistra.crypto.observation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 11 Gaps 4 and 5, merged into one integration test class: (4) proves {@link Observation}'s
 * JPA mapping — the {@link FactType.DbConverter} and {@code @JdbcTypeCode(SqlTypes.JSON)} on {@code
 * rawResponse} — round-trips correctly against a real Postgres, not just plain-JUnit assumptions; (5)
 * exercises {@link ObservationLog#record}'s DB-write half against that same real repository, proving
 * AC3 (no {@code UPDATE}/{@code DELETE} — {@code crypto_app}'s actual grant) with real enforcement,
 * not just the reflection-based unit test.
 *
 * <p>Deliberately does not also stand up a real S3/LocalStack container alongside Postgres here
 * (scoped down from Kimi's literal Gap 5 suggestion): {@code ObservationSnapshotStore} is mocked
 * instead. The real S3 round-trip is already proven in isolation by {@code
 * ObservationSnapshotStoreLocalStackIntegrationTest}, and the S3-before-Postgres *ordering* is already
 * proven against mocks in {@code ObservationLogTest}; combining two different Testcontainers
 * frameworks in one test class would add real complexity for marginal additional confidence over what
 * these three tests already prove separately.</p>
 */
@Testcontainers
@SpringBootTest(classes = ObservationRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ObservationRepositoryIntegrationTest {

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
    private ObservationRepository repository;

    @Autowired
    private ObservationLog observationLog;

    @Autowired
    private ObservationSnapshotStore mockSnapshotStore;

    private static String uniqueTxHash() {
        return "it-tx-" + UUID.randomUUID();
    }

    @Test
    void savedObservationRoundTripsEveryFieldIncludingTheJsonPayloadAndTheConvertedFactType() {
        String txHash = uniqueTxHash();
        String rawJson = "{\"exists\":true,\"blockNumber\":100}";
        Observation observation = Observation.create("ETHEREUM", txHash, "alchemy", FactType.CONFIRMATIONS,
                rawJson, "chain-observations/it-key.json", java.time.Instant.parse("2026-09-03T12:00:00Z"));

        Observation saved = repository.save(observation);

        Observation reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.chain()).isEqualTo("ETHEREUM");
        assertThat(reloaded.txHash()).isEqualTo(txHash);
        assertThat(reloaded.provider()).isEqualTo("alchemy");
        assertThat(reloaded.factType()).isEqualTo(FactType.CONFIRMATIONS);
        assertThat(reloaded.rawResponse()).isEqualTo(rawJson);
        assertThat(reloaded.s3SnapshotKey()).isEqualTo("chain-observations/it-key.json");
        assertThat(reloaded.observedAt()).isEqualTo(java.time.Instant.parse("2026-09-03T12:00:00Z"));
    }

    @Test
    void findByChainAndTxHashAndFactTypeReturnsOnlyMatchingRows() {
        String txHash = uniqueTxHash();
        repository.save(Observation.create("ETHEREUM", txHash, "alchemy", FactType.EXISTENCE, "{}", null,
                java.time.Instant.now()));
        repository.save(Observation.create("ETHEREUM", txHash, "quicknode", FactType.EXISTENCE, "{}", null,
                java.time.Instant.now()));
        repository.save(Observation.create("ETHEREUM", txHash, "alchemy", FactType.AMOUNT, "{}", null,
                java.time.Instant.now()));

        List<Observation> matches = repository.findByChainAndTxHashAndFactType("ETHEREUM", txHash,
                FactType.EXISTENCE);

        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(Observation::provider).containsExactlyInAnyOrder("alchemy", "quicknode");
    }

    @Test
    void repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel() {
        // AC3, real enforcement (not just the reflection-based ObservationTest unit test): the
        // crypto_app role itself has no UPDATE/DELETE grant on chain.observations (T02). Attempting
        // to delete via the repository must fail at the database layer.
        Observation saved = repository.save(Observation.create("ETHEREUM", uniqueTxHash(), "alchemy",
                FactType.EXISTENCE, "{}", null, java.time.Instant.now()));

        assertThatThrownBy(() -> {
            repository.delete(saved);
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void observationLogRecordPersistsARealRowWhenTheMockedSnapshotStoreSucceeds() {
        String txHash = uniqueTxHash();
        when(mockSnapshotStore.store(any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of("chain-observations/real-db-key.json"));

        Observation result = observationLog.record("ETHEREUM", txHash, "alchemy", FactType.EXISTENCE,
                "{\"exists\":true}");

        Observation reloaded = repository.findById(result.id()).orElseThrow();
        assertThat(reloaded.s3SnapshotKey()).isEqualTo("chain-observations/real-db-key.json");
    }

    @Test
    void observationLogRecordPersistsARealRowWithNullKeyWhenTheMockedSnapshotStoreFails() {
        String txHash = uniqueTxHash();
        when(mockSnapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        Observation result = observationLog.record("ETHEREUM", txHash, "alchemy", FactType.EXISTENCE,
                "{\"exists\":true}");

        Observation reloaded = repository.findById(result.id()).orElseThrow();
        assertThat(reloaded.s3SnapshotKey()).isNull();
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Observation.class)
    @EnableJpaRepositories(basePackageClasses = ObservationRepository.class)
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
        ObservationSnapshotStore observationSnapshotStore() {
            return Mockito.mock(ObservationSnapshotStore.class);
        }

        @Bean
        ObservationLog observationLog(ObservationSnapshotStore snapshotStore, ObservationRepository repository,
                                       ObjectMapper objectMapper, Clock clock) {
            return new ObservationLog(snapshotStore, repository, objectMapper, clock);
        }
    }
}
