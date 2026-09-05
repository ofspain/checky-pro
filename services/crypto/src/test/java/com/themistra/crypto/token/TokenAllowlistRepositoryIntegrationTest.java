package com.themistra.crypto.token;

import com.themistra.crypto.common.config.TokenAllowlistProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC4 (real, DB-enforced `INSERT, SELECT`-but-not-`DELETE` grant, added by
 * `V5__crypto_app_token_allowlist_grant.sql`), AC5 (the seeder's four real, config-declared entries),
 * and the Phase 9 per-chain current-version fix (Kimi Phase 8 Issues 1/3), proven end-to-end against a
 * real Postgres. Unlike the narrow `@EntityScan`/`@EnableJpaRepositories`-only configs every prior
 * integration test in this service uses, this one also enables `TokenAllowlistProperties` and imports
 * {@link TokenAllowlistSeeder} so the seeder actually runs against the real
 * `application.properties`-declared entries on context startup - the datasource itself connects as
 * `crypto_app` (not the migration-owning role), so every `repository.save(...)` call in this class
 * already exercises the real `INSERT`/`SELECT` grant, not just a hypothetical one.
 */
@Testcontainers
@SpringBootTest(classes = TokenAllowlistRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TokenAllowlistRepositoryIntegrationTest {

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
    private TokenAllowlistRepository repository;

    @Autowired
    private TokenAllowlistSeeder seeder;

    @Test
    void seederPopulatesAllFourRealConfiguredEntriesOnStartup() {
        assertThat(repository.findByChainAndContractAddressAndVersion(
                "ETHEREUM", "0x1111111111111111111111111111111111111a", 1)).isPresent();
        assertThat(repository.findByChainAndContractAddressAndVersion(
                "ETHEREUM", "0x2222222222222222222222222222222222222b", 1)).isPresent();
        assertThat(repository.findByChainAndContractAddressAndVersion(
                "TRON", "TFakeUSDTPlaceholder0000000000001", 1)).isPresent();
        assertThat(repository.findByChainAndContractAddressAndVersion(
                "TRON", "TFakeUSDCPlaceholder0000000000002", 1)).isPresent();
    }

    @Test
    void reRunningTheSeederIsIdempotent() {
        long countBefore = repository.count();

        seeder.run(new DefaultApplicationArguments());

        assertThat(repository.count()).isEqualTo(countBefore);
    }

    @Test
    void findCurrentVersionEntryScopesToPerChainMaxVersionIndependently() {
        // Phase 9 (Kimi Phase 8 Issues 1/3): bump ONLY Ethereum to version 2. Tron, still at version
        // 1 (the seeder's own startup-seeded rows), must remain fully valid - a version bump on one
        // chain must never affect another.
        repository.save(TokenAllowlist.create("ETHEREUM", "0xnew-v2-token", "NEWTOKEN", 18, 2, "sig",
                Instant.now()));

        Optional<TokenAllowlist> tronStillCurrent = repository.findCurrentVersionEntry(
                "TRON", "TFakeUSDTPlaceholder0000000000001");
        assertThat(tronStillCurrent).as("Tron's version-1 entries must remain current - unaffected by Ethereum's bump")
                .isPresent();

        Optional<TokenAllowlist> ethereumSuperseded = repository.findCurrentVersionEntry(
                "ETHEREUM", "0x1111111111111111111111111111111111111a");
        assertThat(ethereumSuperseded).as("Ethereum's old version-1 USDT entry is superseded by version 2")
                .isEmpty();

        Optional<TokenAllowlist> ethereumNewCurrent = repository.findCurrentVersionEntry(
                "ETHEREUM", "0xnew-v2-token");
        assertThat(ethereumNewCurrent).isPresent();
    }

    @Test
    void deleteFailsAtTheDatabaseLevel() {
        // V5 grants INSERT, SELECT only - no DELETE.
        TokenAllowlist saved = repository.save(TokenAllowlist.create("ETHEREUM", "0xdelete-test", "TEST", 6,
                999, "sig", Instant.now()));

        assertThatThrownBy(() -> {
            repository.delete(saved);
            repository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TokenAllowlist.class)
    @EnableJpaRepositories(basePackageClasses = TokenAllowlistRepository.class)
    @EnableConfigurationProperties(TokenAllowlistProperties.class)
    @Import(TokenAllowlistSeeder.class)
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
