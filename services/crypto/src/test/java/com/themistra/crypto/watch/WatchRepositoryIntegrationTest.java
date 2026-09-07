package com.themistra.crypto.watch;

import com.themistra.crypto.token.AddressValidator;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC7 (real, DB-enforced grants added by `V6__crypto_app_watches_grant.sql`) and the full
 * register/unregister flow, proven end-to-end against a real Postgres. Mirrors {@code
 * TokenAllowlistRepositoryIntegrationTest}'s (T11) own narrow-context pattern exactly: the datasource
 * connects as `crypto_app` (not the migration-owning role), so every repository call here already
 * exercises the real grant, not a hypothetical one.
 */
@Testcontainers
@SpringBootTest(classes = WatchRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WatchRepositoryIntegrationTest {

    private static final String VALID_EVM_ADDRESS = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";

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

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE crypto_app PASSWORD '" + CRYPTO_APP_PASSWORD + "'");
        }
    }

    @Autowired
    private WatchService watchService;

    @Autowired
    private WatchRepository watchRepository;

    @Autowired
    private ChainCursorRepository chainCursorRepository;

    @Test
    void fullRegisterThenUnregisterFlowPersistsCorrectlyAndIsIdempotent() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));

        Watch watch = watchService.register(request);
        assertThat(watchRepository.findByWatchId(watch.watchId())).isPresent();
        assertThat(chainCursorRepository.findAll())
                .anySatisfy(cursor -> {
                    assertThat(cursor.watchId()).isEqualTo(watch.watchId());
                    assertThat(cursor.chain()).isEqualTo("ETHEREUM");
                    assertThat(cursor.lastBlock()).isEqualTo(-1L);
                    assertThat(cursor.lastFinalizedBlock()).isNull();
                });

        watchService.unregister(watch.watchId());
        assertThat(watchRepository.findByWatchId(watch.watchId()).orElseThrow().status())
                .isEqualTo(WatchStatus.UNREGISTERED);

        // idempotent re-DELETE: no exception, no further state change
        watchService.unregister(watch.watchId());
        assertThat(watchRepository.findByWatchId(watch.watchId()).orElseThrow().status())
                .isEqualTo(WatchStatus.UNREGISTERED);
    }

    @Test
    void unregisterOnAnUnknownWatchIdThrowsWatchNotFoundException() {
        assertThatThrownBy(() -> watchService.unregister(UUID.randomUUID()))
                .isInstanceOf(WatchNotFoundException.class);
    }

    @Test
    void unregisterOnAManuallySeededExpiredWatchIsANoOp() throws SQLException {
        // T15 Phase 3 Finding 6 / required test: EXPIRED is never set by this task's own code, but the
        // schema allows it and a future scheduler will set it - DELETE against one must still be a
        // safe no-op, not an error.
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", Instant.now().plus(1, ChronoUnit.DAYS));
        Watch watch = watchService.register(request);

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("UPDATE chain.watches SET status = 'EXPIRED' WHERE watch_id = '"
                    + watch.watchId() + "'");
        }

        watchService.unregister(watch.watchId());

        assertThat(watchRepository.findByWatchId(watch.watchId()).orElseThrow().status())
                .isEqualTo(WatchStatus.EXPIRED);
    }

    @Test
    void cryptoAppCanInsertSelectAndUpdateButNotDeleteOnWatches() throws SQLException {
        String watchId = UUID.randomUUID().toString();
        try (Connection app = connectAsCryptoApp(); Statement statement = app.createStatement()) {
            statement.execute("INSERT INTO chain.watches "
                    + "(watch_id, invoice_uuid, chain, address, token_contract_address, expected_amount, "
                    + "expires_at, created_at) VALUES ('" + watchId + "', '" + UUID.randomUUID()
                    + "', 'ETHEREUM', '" + VALID_EVM_ADDRESS + "', '" + VALID_EVM_ADDRESS
                    + "', 1000000, now() + interval '1 day', now())");

            statement.execute("UPDATE chain.watches SET status = 'UNREGISTERED' WHERE watch_id = '"
                    + watchId + "'");

            // Phase 11 (Kimi Issue 2): an explicit raw SELECT as crypto_app - the repository-level
            // reads elsewhere in this class happen through Spring Data, which would mask a migration
            // that accidentally omitted SELECT.
            try (var resultSet = statement.executeQuery(
                    "SELECT status FROM chain.watches WHERE watch_id = '" + watchId + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("status")).isEqualTo("UNREGISTERED");
            }

            assertThatThrownBy(() -> statement.execute("DELETE FROM chain.watches WHERE watch_id = '"
                    + watchId + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        cleanUpWatchAsAdmin(watchId);
    }

    @Test
    void cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnChainCursors() throws SQLException {
        String watchId = UUID.randomUUID().toString();
        try (Connection app = connectAsCryptoApp(); Statement statement = app.createStatement()) {
            statement.execute("INSERT INTO chain.chain_cursors (chain, watch_id, last_block, updated_at) "
                    + "VALUES ('ETHEREUM', '" + watchId + "', -1, now())");

            // Phase 11 (Kimi Issue 2): explicit raw SELECT as crypto_app.
            try (var resultSet = statement.executeQuery(
                    "SELECT last_block FROM chain.chain_cursors WHERE watch_id = '" + watchId + "'")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong("last_block")).isEqualTo(-1L);
            }

            assertThatThrownBy(() -> statement.execute(
                    "UPDATE chain.chain_cursors SET last_block = 100 WHERE watch_id = '" + watchId + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute(
                    "DELETE FROM chain.chain_cursors WHERE watch_id = '" + watchId + "'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
        cleanUpChainCursorAsAdmin(watchId);
    }

    private void cleanUpWatchAsAdmin(String watchId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM chain.watches WHERE watch_id = '" + watchId + "'");
        }
    }

    private void cleanUpChainCursorAsAdmin(String watchId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM chain.chain_cursors WHERE watch_id = '" + watchId + "'");
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsCryptoApp() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Watch.class)
    @EnableJpaRepositories(basePackageClasses = WatchRepository.class)
    @Import(AddressValidator.class)
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        WatchService watchService(WatchRepository watchRepository, ChainCursorRepository chainCursorRepository,
                AddressValidator addressValidator, Clock clock) {
            return new WatchService(watchRepository, chainCursorRepository, addressValidator, clock);
        }
    }
}
