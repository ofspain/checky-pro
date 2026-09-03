package com.themistra.crypto.provider;

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
 * AC1 (upsert), AC4 (real, DB-enforced `INSERT, SELECT, UPDATE`-but-not-`DELETE` grant, added by
 * `V4__crypto_app_provider_health_grant.sql`) - the first integration test in this service proving an
 * `UPDATE` succeeds, not fails, the inverse of every prior append-only entity's own integration test
 * (`ObservationRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`). Mirrors their
 * exact pattern otherwise: narrow hand-built `@Configuration`, static `@Container PostgreSQLContainer`,
 * Flyway migrate + `crypto_app` password in `@BeforeAll`.
 */
@Testcontainers
@SpringBootTest(classes = ProviderHealthRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProviderHealthRepositoryIntegrationTest {

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
    private ProviderHealthRepository repository;

    private static String uniqueProvider() {
        return "it-provider-" + UUID.randomUUID();
    }

    @Test
    void savedProviderHealthRoundTripsEveryField() {
        String provider = uniqueProvider();
        ProviderHealth health = ProviderHealth.create("ETHEREUM", provider, Instant.parse("2026-09-03T12:00:00Z"));

        ProviderHealth saved = repository.save(health);

        ProviderHealth reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.chain()).isEqualTo("ETHEREUM");
        assertThat(reloaded.provider()).isEqualTo(provider);
        assertThat(reloaded.healthy()).isTrue();
        assertThat(reloaded.updatedAt()).isEqualTo(Instant.parse("2026-09-03T12:00:00Z"));
    }

    @Test
    void findByChainAndProviderReturnsTheMatchingRowAndEmptyWhenNoneExists() {
        String provider = uniqueProvider();
        repository.save(ProviderHealth.create("TRON", provider, Instant.now()));

        assertThat(repository.findByChainAndProvider("TRON", provider)).isPresent();
        assertThat(repository.findByChainAndProvider("TRON", uniqueProvider())).isEmpty();
    }

    @Test
    void anUpdateToAnAlreadyPersistedRowSucceeds() {
        // AC4: unlike every prior append-only entity's integration test, this proves UPDATE
        // succeeds against the real crypto_app grant added by V4.
        String provider = uniqueProvider();
        ProviderHealth saved = repository.save(ProviderHealth.create("ETHEREUM", provider, Instant.now()));

        ProviderHealth fetched = repository.findById(saved.id()).orElseThrow();
        Instant transitionAt = Instant.parse("2026-09-03T13:00:00Z");
        fetched.markUnhealthy(transitionAt);
        repository.save(fetched);

        ProviderHealth reloaded = repository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.healthy()).isFalse();
        assertThat(reloaded.updatedAt()).isEqualTo(transitionAt);
    }

    @Test
    void aSecondRowForTheSameChainAndProviderViolatesTheUniqueConstraint() {
        // Phase 11 Gap 6: proves UNIQUE (chain, provider) directly, at the DB layer - the upsert
        // semantics ProviderHealthTracker relies on depend on this constraint actually existing.
        String provider = uniqueProvider();
        repository.save(ProviderHealth.create("ETHEREUM", provider, Instant.now()));

        assertThatThrownBy(() -> {
            repository.save(ProviderHealth.create("ETHEREUM", provider, Instant.now()));
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deleteStillFailsAtTheDatabaseLevel() {
        // V4 grants INSERT, SELECT, UPDATE only - no DELETE.
        ProviderHealth saved = repository.save(ProviderHealth.create("ETHEREUM", uniqueProvider(), Instant.now()));

        assertThatThrownBy(() -> {
            repository.delete(saved);
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ProviderHealth.class)
    @EnableJpaRepositories(basePackageClasses = ProviderHealthRepository.class)
    static class TestConfig {
    }
}
