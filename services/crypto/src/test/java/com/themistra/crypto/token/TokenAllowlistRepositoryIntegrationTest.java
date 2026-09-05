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
import java.util.UUID;

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
        // Phase 11 Gap 7: field values, not just row presence - a typo/mapping drift in
        // application.properties or the seeder's own field mapping would still pass a presence-only
        // check.
        TokenAllowlist ethereumUsdt = repository.findByChainAndContractAddressAndVersion(
                "ETHEREUM", "0x1111111111111111111111111111111111111a", 1).orElseThrow();
        assertThat(ethereumUsdt.symbol()).isEqualTo("USDT");
        assertThat(ethereumUsdt.decimals()).isEqualTo((short) 6);
        assertThat(ethereumUsdt.signature()).isEqualTo("local-only-unsigned-placeholder");

        TokenAllowlist ethereumUsdc = repository.findByChainAndContractAddressAndVersion(
                "ETHEREUM", "0x2222222222222222222222222222222222222b", 1).orElseThrow();
        assertThat(ethereumUsdc.symbol()).isEqualTo("USDC");
        assertThat(ethereumUsdc.decimals()).isEqualTo((short) 6);

        TokenAllowlist tronUsdt = repository.findByChainAndContractAddressAndVersion(
                "TRON", "TFakeUSDTPlaceholder0000000000001", 1).orElseThrow();
        assertThat(tronUsdt.symbol()).isEqualTo("USDT");
        assertThat(tronUsdt.decimals()).isEqualTo((short) 6);

        TokenAllowlist tronUsdc = repository.findByChainAndContractAddressAndVersion(
                "TRON", "TFakeUSDCPlaceholder0000000000002", 1).orElseThrow();
        assertThat(tronUsdc.symbol()).isEqualTo("USDC");
        assertThat(tronUsdc.decimals()).isEqualTo((short) 6);
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

    @Test
    void updateFailsAtTheDatabaseLevel() throws SQLException {
        // Phase 11 Gap 2: TokenAllowlist has no mutator at all, so no JPA-entity code path can even
        // attempt an UPDATE - raw JDBC is the only way to exercise this, mirroring
        // ChainBaselineMigrationIntegrationTest's own established pattern for exactly this situation.
        repository.save(TokenAllowlist.create("ETHEREUM", "0xupdate-test", "TEST", 6, 998, "sig", Instant.now()));

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
             Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE chain.token_allowlist SET symbol = 'HACKED' WHERE contract_address = '0xupdate-test'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void findCurrentVersionEntryOnAnEntirelyEmptyChainReturnsEmpty() {
        // Phase 11 Gap 8: a chain with zero rows anywhere (unlike "known chain, unknown address",
        // already covered elsewhere) exercises MAX(version) over zero rows (SQL NULL), proving the
        // outer query correctly returns empty rather than throwing or matching unexpectedly.
        Optional<TokenAllowlist> result = repository.findCurrentVersionEntry(
                "SOLANA-" + UUID.randomUUID(), "0xanything");

        assertThat(result).isEmpty();
    }

    @Test
    void sameTokenAcrossVersionsResolvesToTheLatestVersion() {
        // Phase 11 Gap 3: the same (chain, contractAddress) present at both v1 and v2 must resolve to
        // the v2 row specifically, not just "some" row. A synthetic, per-test-unique chain value keeps
        // this isolated from every other test's own version numbers for the real ETHEREUM/TRON chains
        // (current version is scoped per chain across the whole table, not per test).
        String chain = "TC-" + UUID.randomUUID().toString().substring(0, 8);
        String address = "0xspans-versions";
        repository.save(TokenAllowlist.create(chain, address, "OLDNAME", 6, 1, "sig-v1", Instant.now()));
        repository.save(TokenAllowlist.create(chain, address, "NEWNAME", 6, 2, "sig-v2", Instant.now()));

        Optional<TokenAllowlist> current = repository.findCurrentVersionEntry(chain, address);

        assertThat(current).isPresent();
        assertThat(current.get().version()).isEqualTo(2);
        assertThat(current.get().symbol()).isEqualTo("NEWNAME");
    }

    @Test
    void mixedVersionsOnTheSameChainForDifferentTokensOnlyTheHigherVersionsTokenIsCurrent() {
        // Phase 11 Gap 10: a config footgun made explicit - two different tokens on the SAME chain
        // declared at different versions leave the lower-version-only token as UNKNOWN_TOKEN, since
        // "current version" is the chain's own single highest version, not per-token. A synthetic,
        // per-test-unique chain value keeps this isolated from other tests' own version numbers.
        String chain = "TC-" + UUID.randomUUID().toString().substring(0, 8);
        String olderAddress = "0xmixed-versions-old";
        String newerAddress = "0xmixed-versions-new";
        repository.save(TokenAllowlist.create(chain, olderAddress, "OLDTOKEN", 6, 1, "sig", Instant.now()));
        repository.save(TokenAllowlist.create(chain, newerAddress, "NEWTOKEN", 6, 2, "sig", Instant.now()));

        assertThat(repository.findCurrentVersionEntry(chain, olderAddress))
                .as("the lower-version-only token becomes UNKNOWN_TOKEN once a higher version exists for the chain")
                .isEmpty();
        assertThat(repository.findCurrentVersionEntry(chain, newerAddress)).isPresent();
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
