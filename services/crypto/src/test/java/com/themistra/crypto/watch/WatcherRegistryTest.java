package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.FakeChainAdapter;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.common.config.WatcherProperties;
import com.themistra.crypto.finality.FinalityPolicy;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.reorg.ReorgDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
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
        return new WatcherProperties(1, 10_000, 30_000, 1_000, 60_000, 3_600_000);
    }

    /** {@code lockAtLeastFor} is a genuine minimum hold duration - ShedLock honors it even across an
     * explicit {@code unlock()}, to guard against a lock flapping faster than clock skew between nodes
     * could tolerate. {@link #properties()}'s 1000ms floor is correct for production but would make
     * {@code shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver} flaky/slow (it would
     * need to wait out that floor for real), so that test alone uses a negligible one to isolate {@code
     * shutdown()}'s own release behavior from the (separately correct, untested-here) floor guarantee. */
    private static WatcherProperties propertiesWithNegligibleLockAtLeastFor() {
        return new WatcherProperties(1, 10_000, 30_000, 1, 60_000, 3_600_000);
    }

    private static Watch registeredWatch(String chain) {
        return Watch.register(UUID.randomUUID(), UUID.randomUUID(), chain, "0xaddress", "0xtoken",
                BigDecimal.TEN, NOW.plus(1, ChronoUnit.DAYS), NOW);
    }

    /** The shard formula ({@code Math.floorMod(watchId.hashCode(), shardCount)}) is a stable, spec-level
     * contract (frozen brief, AC9), not an internal implementation detail - safe for a test fixture to
     * replicate in order to construct a watch that is guaranteed to land in a specific shard. */
    private static Watch registeredWatchInShard(String chain, int shardCount, int targetShard) {
        UUID watchId;
        do {
            watchId = UUID.randomUUID();
        } while (Math.floorMod(watchId.hashCode(), shardCount) != targetShard);
        return Watch.register(watchId, UUID.randomUUID(), chain, "0xaddress", "0xtoken",
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
                Clock.systemUTC(), lockProvider, new ObjectMapper(), mock(TxLifecyclePublisher.class),
                List.of(ethereumFinalityPolicy()), mock(ReorgDetector.class));
    }

    /** T17 Phase 9 (self-review Finding 2 / Kimi Finding 4): {@code Watcher} now fails fast in its
     * constructor if no policy is configured for its chain - every fixture here uses ETHEREUM. */
    private static FinalityPolicy ethereumFinalityPolicy() {
        FinalityPolicy policy = mock(FinalityPolicy.class);
        when(policy.chain()).thenReturn(Chain.ETHEREUM);
        return policy;
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

    @Test
    void shutdownStopsARunningWatcherIncludingItsSubscriptionAndGauge() {
        // Phase 11 Finding 3: every other test mocks ProviderSet to return no adapters, so no real
        // Watcher is ever constructed - shutdown()'s call to stop() on each running Watcher was never
        // actually exercised. A FakeChainAdapter-backed ProviderSet lets a real Watcher start, and its
        // lag gauge (registered on start(), removed on stop(), per WatcherTest's own established
        // technique) is the observable proof that shutdown() reached it.
        LockProvider lockProvider = new JdbcTemplateLockProvider(cryptoAppDataSource());
        Watch watch = registeredWatch("ETHEREUM");
        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of(watch));
        FakeChainAdapter fakeAdapter = new FakeChainAdapter(Chain.ETHEREUM, "fake-provider");
        ProviderSet providerSet = mock(ProviderSet.class);
        when(providerSet.adaptersFor(any()))
                .thenReturn(List.of(new ProviderSet.NamedAdapter("fake-provider", fakeAdapter)));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        WatcherRegistry registry = new WatcherRegistry(watchRepository, providerSet, mock(ObservationLog.class),
                mock(QuorumDecisionService.class), mock(ProviderHealthTracker.class),
                mock(ChainCursorRepository.class), properties(), meterRegistry, Clock.systemUTC(), lockProvider,
                new ObjectMapper(), mock(TxLifecyclePublisher.class), List.of(ethereumFinalityPolicy()),
                mock(ReorgDetector.class));

        registry.reconcile();
        assertThat(meterRegistry.find("crypto.watcher.lag.seconds")
                .tag("watchId", watch.watchId().toString()).gauge()).isNotNull();

        registry.shutdown();

        assertThat(meterRegistry.find("crypto.watcher.lag.seconds")
                .tag("watchId", watch.watchId().toString()).gauge()).isNull();
    }

    @Test
    void aReplicaThatLosesItsShardLockStopsItsRunningWatchers() {
        // Phase 11 Finding 4: ownsShard()'s renewal-failure branch (a healthy replica's lock is lost,
        // e.g. another replica somehow also acquired it, or a long GC pause exceeded lockAtMostFor) was
        // never exercised - only initial acquisition was. A mocked LockProvider/SimpleLock deterministically
        // simulates the second reconciliation tick's renewal failing.
        Watch watch = registeredWatch("ETHEREUM");
        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of(watch));
        ProviderSet providerSet = mock(ProviderSet.class);
        when(providerSet.adaptersFor(any())).thenReturn(List.of());

        SimpleLock acquiredLock = mock(SimpleLock.class);
        when(acquiredLock.extend(any(), any())).thenReturn(Optional.empty());
        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(any())).thenReturn(Optional.of(acquiredLock));

        WatcherRegistry registry = newRegistry(watchRepository, providerSet, lockProvider);

        registry.reconcile();
        verify(providerSet, times(1)).adaptersFor(any());

        registry.reconcile();

        // the renewal failure must not have started a second watcher, nor re-acquired via lock() again
        verify(providerSet, times(1)).adaptersFor(any());
        verify(lockProvider, times(1)).lock(any());
    }

    @Test
    void onlyWatchesInAnOwnedShardAreEverStarted() {
        // Phase 11 Finding 5: every other test uses shardCount=1, so the multi-shard split in
        // reconcile()/reconcileShard() - owning some shards but not others in the same tick - was never
        // exercised.
        Watch watchInShard0 = registeredWatchInShard("ETHEREUM", 2, 0);
        Watch watchInShard1 = registeredWatchInShard("ETHEREUM", 2, 1);
        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED))
                .thenReturn(List.of(watchInShard0, watchInShard1));
        ProviderSet providerSet = mock(ProviderSet.class);
        when(providerSet.adaptersFor(any())).thenReturn(List.of());

        LockProvider lockProvider = mock(LockProvider.class);
        when(lockProvider.lock(argThat(config -> config != null && config.getName().equals("watcher-shard-0"))))
                .thenReturn(Optional.of(mock(SimpleLock.class)));
        when(lockProvider.lock(argThat(config -> config != null && config.getName().equals("watcher-shard-1"))))
                .thenReturn(Optional.empty());

        WatcherProperties twoShardProperties = new WatcherProperties(2, 10_000, 30_000, 1_000, 60_000, 3_600_000);
        WatcherRegistry registry = newRegistry(watchRepository, providerSet, lockProvider, twoShardProperties);

        registry.reconcile();

        // only watchInShard0's shard was acquired - its watcher starts; watchInShard1's does not.
        verify(providerSet, times(1)).adaptersFor(any());
    }

    @Test
    void aWatchNoLongerReturnedAsRegisteredHasItsWatcherStoppedOnTheNextReconciliation() {
        // Phase 11 Finding 8: stopWatchersNoLongerRegistered - the REGISTERED-to-not-REGISTERED
        // transition - was never directly exercised; onlyRegisteredWatchesAreEverAssignedToAShard only
        // covers the case where no watch was ever registered at all.
        Watch watch = registeredWatch("ETHEREUM");
        WatchRepository watchRepository = mock(WatchRepository.class);
        when(watchRepository.findByStatus(WatchStatus.REGISTERED)).thenReturn(List.of(watch), List.of());
        ProviderSet providerSet = mock(ProviderSet.class);
        when(providerSet.adaptersFor(any())).thenReturn(List.of());
        LockProvider lockProvider = new JdbcTemplateLockProvider(cryptoAppDataSource());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        WatcherRegistry registry = new WatcherRegistry(watchRepository, providerSet, mock(ObservationLog.class),
                mock(QuorumDecisionService.class), mock(ProviderHealthTracker.class),
                mock(ChainCursorRepository.class), properties(), meterRegistry, Clock.systemUTC(), lockProvider,
                new ObjectMapper(), mock(TxLifecyclePublisher.class), List.of(ethereumFinalityPolicy()),
                mock(ReorgDetector.class));

        registry.reconcile();
        assertThat(meterRegistry.find("crypto.watcher.lag.seconds")
                .tag("watchId", watch.watchId().toString()).gauge()).isNotNull();

        registry.reconcile();

        assertThat(meterRegistry.find("crypto.watcher.lag.seconds")
                .tag("watchId", watch.watchId().toString()).gauge()).isNull();
    }
}
