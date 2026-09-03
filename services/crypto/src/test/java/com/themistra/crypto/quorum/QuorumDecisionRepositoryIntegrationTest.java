package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC4 (counts persist exactly), AC5 (real, DB-enforced no-UPDATE/no-DELETE grant), and Amendment #7
 * (the `uq_quorum_tx_fact` unique constraint proven directly against a real Postgres, not just
 * asserted in prose). Mirrors {@code ObservationRepositoryIntegrationTest}'s (T08) exact pattern:
 * narrow hand-built {@code @Configuration}, static {@code @Container PostgreSQLContainer}, Flyway
 * migrate + {@code crypto_app} password in {@code @BeforeAll}.
 */
@Testcontainers
@SpringBootTest(classes = QuorumDecisionRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class QuorumDecisionRepositoryIntegrationTest {

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
    private QuorumDecisionRepository repository;

    private static String uniqueTxHash() {
        return "it-tx-" + UUID.randomUUID();
    }

    @Test
    void savedQuorumDecisionRoundTripsEveryFieldIncludingTheFactTypeAndOutcomeConversion() {
        String txHash = uniqueTxHash();
        QuorumDecision decision = QuorumDecision.create("ETHEREUM", txHash, FactType.CONFIRMATIONS,
                QuorumOutcome.AGREED, 3, 3, Instant.parse("2026-09-03T12:00:00Z"));

        QuorumDecision saved = repository.save(decision);

        QuorumDecision reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.chain()).isEqualTo("ETHEREUM");
        assertThat(reloaded.txHash()).isEqualTo(txHash);
        assertThat(reloaded.factType()).isEqualTo(FactType.CONFIRMATIONS);
        assertThat(reloaded.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(reloaded.agreeingCount()).isEqualTo((short) 3);
        assertThat(reloaded.providerCount()).isEqualTo((short) 3);
        assertThat(reloaded.decidedAt()).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
    }

    @Test
    void findByChainAndTxHashAndFactTypeReturnsTheMatchingDecision() {
        String txHash = uniqueTxHash();
        repository.save(QuorumDecision.create("ETHEREUM", txHash, FactType.EXISTENCE,
                QuorumOutcome.HELD, 1, 3, Instant.now()));

        var found = repository.findByChainAndTxHashAndFactType("ETHEREUM", txHash, FactType.EXISTENCE);

        assertThat(found).isPresent();
        assertThat(found.get().outcome()).isEqualTo(QuorumOutcome.HELD);
    }

    @Test
    void repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel() {
        // AC5, real enforcement: crypto_app has no UPDATE/DELETE grant on chain.quorum_decisions (T02).
        QuorumDecision saved = repository.save(QuorumDecision.create("ETHEREUM", uniqueTxHash(),
                FactType.EXISTENCE, QuorumOutcome.AGREED, 2, 3, Instant.now()));

        assertThatThrownBy(() -> {
            repository.delete(saved);
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSecondDecisionForTheSameChainTxHashFactTypeViolatesTheUniqueConstraint() {
        // Amendment #7: quorum_decisions is single-decision-per-fact for launch - proven directly
        // against the real uq_quorum_tx_fact constraint, not just documented in prose.
        String txHash = uniqueTxHash();
        repository.save(QuorumDecision.create("ETHEREUM", txHash, FactType.EXISTENCE,
                QuorumOutcome.AGREED, 2, 3, Instant.now()));

        assertThatThrownBy(() -> {
            repository.save(QuorumDecision.create("ETHEREUM", txHash, FactType.EXISTENCE,
                    QuorumOutcome.HELD, 1, 3, Instant.now()));
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = QuorumDecision.class)
    @EnableJpaRepositories(basePackageClasses = QuorumDecisionRepository.class)
    static class TestConfig {
    }
}
