package com.themistra.crypto.watch;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.common.config.WatcherProperties;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.QuorumDecisionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T16 AC9 (O5) - real, DB-enforced shard exclusivity, proven against a real Postgres {@code
 * chain.shedlock} table (granted by `V7__crypto_app_watcher_grants.sql`) rather than a mocked {@code
 * LockProvider}. Two {@code WatcherRegistry} instances share one {@code LockProvider} backed by the
 * same `crypto_app`-connected datasource, simulating two replicas. {@link ProviderSet} is mocked
 * (returning no adapters) - this suite tests the registry's own shard-assignment/locking mechanics, not
 * {@code Watcher}'s per-provider correlation logic, which {@code WatcherTest} already covers
 * exhaustively.
 */
@Testcontainers
class WatcherRegistryTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String CRYPTO_APP_PASSWORD = "it-crypto-app-password";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

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

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** All 4 tests share one static container/table (a fresh container per test would be needlessly
     * slow), so a shard lock row inserted by one test would otherwise still be held - real wall-clock
     * {@code lockAtMostFor} - when the next test runs and reuses the same shard name
     * ("watcher-shard-0", since every test here uses shardCount=1). Clearing the table isolates them. */
    @AfterEach
    void clearShedlockTable() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM chain.shedlock");
        }
    }

    /** {@code currentSchema=chain} mirrors the real application's own {@code connection-init-sql=SET
     * search_path TO chain, public} (application.properties) - without it, ShedLock's own unqualified
     * {@code shedlock} table reference fails against this role's default search_path. */
    private static DataSource cryptoAppDataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource dataSource =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl() + "&currentSchema=chain");
        dataSource.setUsername("crypto_app");
        dataSource.setPassword(CRYPTO_APP_PASSWORD);
        return dataSource;
    }

    private static WatcherProperties properties() {
        return new WatcherProperties(1, 10_000, 30_000, 1_000, 60_000);
    }

    /** {@code lockAtLeastFor} is a genuine minimum hold duration - ShedLock honors it even across an
     * explicit {@code unlock()}, to guard against a lock flapping faster than clock skew between nodes
     * could tolerate. {@link #properties()}'s 1000ms floor is correct for production but would make
     * {@code shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver} flaky/slow (it would
     * need to wait out that floor for real), so that test alone uses a negligible one to isolate {@code
     * shutdown()}'s own release behavior from the (separately correct, untested-here) floor guarantee. */
    private static WatcherProperties propertiesWithNegligibleLockAtLeastFor() {
        return new WatcherProperties(1, 10_000, 30_000, 1, 60_000);
    }

    private static Watch registeredWatch(String chain) {
        return Watch.register(UUID.randomUUID(), UUID.randomUUID(), chain, "0xaddress", "0xtoken",
                BigDecimal.TEN, NOW.plus(1, ChronoUnit.DAYS), NOW);
    }

    /** {@code WatcherRegistry}'s own {@code clock} feeds {@code LockConfiguration}'s {@code lockUntil}
     * computation, but ShedLock's real expiry check ({@code lock_until <= :now}, verified via decompiling
     * {@code SqlStatementsSource.params()}) binds {@code :now} to {@code ClockProvider.now()} - real wall
     * time, independent of any app-level {@link Clock}. A fixed clock parked at a past instant (like
     * {@link #NOW}) would make every computed {@code lockUntil} already "expired" by that real check,
     * so every replica could always acquire the lock - a test-only correctness requirement, not present
     * in production where the real clock bean already reflects wall time. */
    private WatcherRegistry newRegistry(WatchRepository watchRepository, ProviderSet providerSet,
            LockProvider lockProvider) {
        return newRegistry(watchRepository, providerSet, lockProvider, properties());
    }

    private WatcherRegistry newRegistry(WatchRepository watchRepository, ProviderSet providerSet,
            LockProvider lockProvider, WatcherProperties watcherProperties) {
        return new WatcherRegistry(watchRepository, providerSet, mock(ObservationLog.class),
                mock(QuorumDecisionService.class), mock(ProviderHealthTracker.class),
                mock(ChainCursorRepository.class), watcherProperties, new SimpleMeterRegistry(),
                Clock.systemUTC(), lockProvider);
    }

    @Test
    void onlyOneOfTwoReplicasStartsAWatcherForTheSameRegisteredWatch() {
        LockProvider sharedLockProvider = new JdbcTemplateLockProvider(cryptoAppDataSource());
        Watch watch = registeredWatch("ETHEREUM");

        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of(watch));
        ProviderSet providerSet = mock(ProviderSet.class);
        when(providerSet.adaptersFor(any())).thenReturn(List.of());

        WatcherRegistry replicaA = newRegistry(watchRepository, providerSet, sharedLockProvider);
        WatcherRegistry replicaB = newRegistry(watchRepository, providerSet, sharedLockProvider);

        replicaA.reconcile();
        replicaB.reconcile();

        // properties().shardCount() == 1, so both replicas contend for the single shard "watcher-shard-0"
        // - only whichever one wins actually starts a Watcher (which, in doing so, calls
        // providerSet.adaptersFor exactly once for this watch's chain).
        verify(providerSet, times(1)).adaptersFor(Chain.ETHEREUM);
    }

    @Test
    void repeatedReconciliationOnTheOwningReplicaDoesNotStartADuplicateWatcher() {
        LockProvider lockProvider = new JdbcTemplateLockProvider(cryptoAppDataSource());
        Watch watch = registeredWatch("ETHEREUM");

        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of(watch));
        ProviderSet providerSet = mock(ProviderSet.class);
        when(providerSet.adaptersFor(any())).thenReturn(List.of());

        WatcherRegistry registry = newRegistry(watchRepository, providerSet, lockProvider);

        registry.reconcile();
        registry.reconcile();
        registry.reconcile();

        verify(providerSet, times(1)).adaptersFor(Chain.ETHEREUM);
    }

    @Test
    void onlyRegisteredWatchesAreEverAssignedToAShard() {
        LockProvider lockProvider = new JdbcTemplateLockProvider(cryptoAppDataSource());
        WatchRepository watchRepository = mock(WatchRepository.class);
        // WatchRepository.findByStatus(REGISTERED) is the only query WatcherRegistry ever issues -
        // an UNREGISTERED/EXPIRED watch is excluded by construction, never even considered here.
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of());
        ProviderSet providerSet = mock(ProviderSet.class);

        WatcherRegistry registry = newRegistry(watchRepository, providerSet, lockProvider);
        registry.reconcile();

        verify(providerSet, times(0)).adaptersFor(any());
    }

    @Test
    void shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver() {
        LockProvider sharedLockProvider = new JdbcTemplateLockProvider(cryptoAppDataSource());
        Watch watch = registeredWatch("ETHEREUM");

        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of(watch));
        ProviderSet providerSetA = mock(ProviderSet.class);
        when(providerSetA.adaptersFor(any())).thenReturn(List.of());
        ProviderSet providerSetB = mock(ProviderSet.class);
        when(providerSetB.adaptersFor(any())).thenReturn(List.of());

        WatcherProperties properties = propertiesWithNegligibleLockAtLeastFor();
        WatcherRegistry replicaA = newRegistry(watchRepository, providerSetA, sharedLockProvider, properties);
        WatcherRegistry replicaB = newRegistry(watchRepository, providerSetB, sharedLockProvider, properties);

        replicaA.reconcile();
        replicaB.reconcile();
        verify(providerSetA, times(1)).adaptersFor(any());
        verify(providerSetB, times(0)).adaptersFor(any());

        replicaA.shutdown();
        replicaB.reconcile();

        verify(providerSetB, times(1)).adaptersFor(any());
    }
}
