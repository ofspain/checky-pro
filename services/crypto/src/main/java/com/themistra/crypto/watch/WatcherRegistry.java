package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.common.config.WatcherProperties;
import com.themistra.crypto.finality.FinalityPolicy;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.reorg.ReorgDetector;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * O5 multi-replica assignment (task 16 - explicit author approval, confirmed at Phase 4, beyond the
 * normal Phase 4 gate every other task in this pipeline has used). A watch's shard is {@code
 * Math.floorMod(watchId.hashCode(), shardCount)} - every replica computes the same mapping
 * independently, with no coordination needed beyond ShedLock itself (T16 Phase 3 Finding 8).
 *
 * <p>Uses ShedLock's programmatic {@link LockProvider} API, not the declarative {@code @SchedulerLock}
 * annotation (verified directly against the pinned {@code 7.7.0} library before relying on it): {@code
 * @SchedulerLock} names one fixed lock for one scheduled method, and cannot acquire {@code shardCount}
 * dynamically-named locks within a single reconciliation pass. Once a shard's lock is held, each
 * subsequent reconciliation tick renews it via {@link SimpleLock#extend}, verified to be a real,
 * non-throwing implementation on the JDBC-backed provider (not merely declared on the interface) before
 * being relied on - a healthy replica keeps its shards indefinitely; it never has to re-acquire them,
 * and a failed renewal (lock genuinely lost) triggers stopping that shard's watchers immediately rather
 * than leaving them running unowned.</p>
 */
@Component
public class WatcherRegistry {

    private static final Logger log = LoggerFactory.getLogger(WatcherRegistry.class);
    private static final String LOCK_NAME_PREFIX = "watcher-shard-";

    private final WatchRepository watchRepository;
    private final ProviderSet providerSet;
    private final ObservationLog observationLog;
    private final QuorumDecisionService quorumDecisionService;
    private final ProviderHealthTracker providerHealthTracker;
    private final ChainCursorRepository chainCursorRepository;
    private final WatcherProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final LockProvider lockProvider;
    private final ObjectMapper objectMapper;
    private final TxLifecyclePublisher txLifecyclePublisher;
    private final List<FinalityPolicy> finalityPolicies;
    private final ReorgDetector reorgDetector;

    private final Map<Integer, SimpleLock> heldShardLocks = new ConcurrentHashMap<>();
    private final Map<UUID, Watcher> runningWatchers = new ConcurrentHashMap<>();

    public WatcherRegistry(WatchRepository watchRepository, ProviderSet providerSet,
            ObservationLog observationLog, QuorumDecisionService quorumDecisionService,
            ProviderHealthTracker providerHealthTracker, ChainCursorRepository chainCursorRepository,
            WatcherProperties properties, MeterRegistry meterRegistry, Clock clock,
            LockProvider lockProvider, ObjectMapper objectMapper, TxLifecyclePublisher txLifecyclePublisher,
            List<FinalityPolicy> finalityPolicies, ReorgDetector reorgDetector) {
        this.watchRepository = watchRepository;
        this.providerSet = providerSet;
        this.observationLog = observationLog;
        this.quorumDecisionService = quorumDecisionService;
        this.providerHealthTracker = providerHealthTracker;
        this.chainCursorRepository = chainCursorRepository;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.lockProvider = lockProvider;
        this.objectMapper = objectMapper;
        this.txLifecyclePublisher = txLifecyclePublisher;
        this.finalityPolicies = List.copyOf(finalityPolicies);
        this.reorgDetector = reorgDetector;
    }

    /** T16 Phase 8 Finding 6: stops every running {@code Watcher} (cancelling its subscriptions and
     * shutting down its own private scheduler, per {@link Watcher#stop}) on application shutdown.
     * Verified directly (Phase 6) that {@code Thread.ofVirtual().factory()}-backed threads are daemon
     * by default, so this is not needed to prevent the JVM hanging on exit - it is needed so a planned
     * shutdown stops watchers cleanly (no writes racing a closing connection pool) and releases this
     * replica's shard locks immediately, rather than making another replica wait out {@code
     * lockAtMostFor} before taking over. */
    @PreDestroy
    public void shutdown() {
        for (UUID watchId : List.copyOf(runningWatchers.keySet())) {
            stopWatcher(watchId);
        }
        for (SimpleLock lock : heldShardLocks.values()) {
            lock.unlock();
        }
        heldShardLocks.clear();
    }

    @Scheduled(fixedDelayString = "${themistra.crypto.watcher.reconciliation-interval-ms}")
    public void reconcile() {
        List<Watch> registeredWatches = watchRepository.findByStatus(WatchStatus.REGISTERED);
        for (int shardIndex = 0; shardIndex < properties.shardCount(); shardIndex++) {
            reconcileShard(shardIndex, registeredWatches);
        }
    }

    private void reconcileShard(int shardIndex, List<Watch> registeredWatches) {
        if (!ownsShard(shardIndex)) {
            return; // another replica holds this shard, or we failed to acquire/renew it this tick
        }

        List<Watch> shardWatches = registeredWatches.stream()
                .filter(watch -> shardOf(watch.watchId()) == shardIndex)
                .toList();
        startNewWatchers(shardWatches);
        stopWatchersNoLongerRegistered(shardIndex, shardWatches);
    }

    /** Acquires the shard's lock if not already held, or renews it if it is. Returns {@code false} (and
     * stops every {@code Watcher} this replica was running for the shard) if the lock is held elsewhere
     * or a renewal is refused - a shard this replica does not currently, verifiably own must never keep
     * driving watches, since a different replica may now also believe it owns the same shard. */
    private boolean ownsShard(int shardIndex) {
        SimpleLock currentLock = heldShardLocks.get(shardIndex);
        Optional<SimpleLock> result = currentLock == null
                ? lockProvider.lock(newLockConfiguration(shardIndex))
                : currentLock.extend(lockAtMostFor(), lockAtLeastFor());

        if (result.isEmpty()) {
            heldShardLocks.remove(shardIndex);
            stopAllWatchersInShard(shardIndex);
            return false;
        }
        heldShardLocks.put(shardIndex, result.get());
        return true;
    }

    private void startNewWatchers(List<Watch> shardWatches) {
        for (Watch watch : shardWatches) {
            runningWatchers.computeIfAbsent(watch.watchId(), id -> {
                Watcher watcher = new Watcher(watch, providerSet.adaptersFor(Chain.valueOf(watch.chain())),
                        observationLog, quorumDecisionService, providerHealthTracker,
                        chainCursorRepository, properties.correlationWindowMs(), meterRegistry, clock,
                        objectMapper, txLifecyclePublisher, finalityPolicies,
                        properties.finalityPollIntervalMs(), reorgDetector);
                watcher.start();
                return watcher;
            });
        }
    }

    private void stopWatchersNoLongerRegistered(int shardIndex, List<Watch> shardWatches) {
        Set<UUID> stillRegistered = shardWatches.stream().map(Watch::watchId).collect(Collectors.toSet());
        for (UUID watchId : List.copyOf(runningWatchers.keySet())) {
            if (shardOf(watchId) == shardIndex && !stillRegistered.contains(watchId)) {
                stopWatcher(watchId);
            }
        }
    }

    private void stopAllWatchersInShard(int shardIndex) {
        for (UUID watchId : List.copyOf(runningWatchers.keySet())) {
            if (shardOf(watchId) == shardIndex) {
                stopWatcher(watchId);
            }
        }
    }

    private void stopWatcher(UUID watchId) {
        Watcher watcher = runningWatchers.remove(watchId);
        if (watcher != null) {
            watcher.stop();
        }
    }

    private LockConfiguration newLockConfiguration(int shardIndex) {
        return new LockConfiguration(clock.instant(), LOCK_NAME_PREFIX + shardIndex,
                lockAtMostFor(), lockAtLeastFor());
    }

    private Duration lockAtMostFor() {
        return Duration.ofMillis(properties.lockAtMostForMs());
    }

    private Duration lockAtLeastFor() {
        return Duration.ofMillis(properties.lockAtLeastForMs());
    }

    private int shardOf(UUID watchId) {
        return Math.floorMod(watchId.hashCode(), properties.shardCount());
    }
}
