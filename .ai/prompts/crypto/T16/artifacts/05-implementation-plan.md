# crypto · T16 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (STATUS: FROZEN). No code written in this phase.

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/adapter/ProviderSet.java`
2. `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java`
3. `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
4. `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
5. `services/crypto/src/main/resources/db/migration/V7__crypto_app_watcher_grants.sql`

No new file beyond these five and the brief's own five — the raw-JSON payload each adapter builds is a
`Map<String, Object>` serialized inline via the already-available `ObjectMapper`, not a new named type,
so nothing the brief didn't authorize is added.

## Files to modify

1. `adapter/ObservationSink.java` — signature change.
2. `adapter/eth/EthereumAdapter.java` — constructor gains `ObjectMapper`; every `sink.onObservation(...)`
   call site updated.
3. `adapter/tron/TronAdapter.java` — same.
4. `adapter/eth/EthereumAdapterConfig.java`, `adapter/tron/TronAdapterConfig.java` — pass an
   `ObjectMapper` bean into the adapter constructor.
5. `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` — new
   `providerName` field (constructor overload defaulting it, to minimize ripple to existing
   `new FakeChainAdapter(Chain)` call sites across T06-T15's own tests); `onObservation` call site
   updated to the 3-arg shape.
6. `FakeChainAdapterTest.java` and any other test using `received::add`-style
   `ObservationSink` lambdas/method-references — updated to the 3-arg shape.
7. `provider/ProviderHealthTracker.java` — `MeterRegistry` constructor param; a counter increment
   inside `recordDisagreement`.
8. `watch/WatchRepository.java` — add `List<Watch> findByStatus(WatchStatus status)`.
9. `watch/ChainCursorRepository.java` — add `Optional<ChainCursor> findByWatchId(UUID watchId)`.
10. `application.properties` — new `themistra.crypto.watcher.*` keys.

## Public methods (signatures)

**`ProviderSet` (`@Component`, package `adapter`):**
```java
public ProviderSet(List<EthereumAdapter> ethereumAdapters, List<TronAdapter> tronAdapters);
public List<ChainAdapter> adaptersFor(Chain chain);  // empty list if none configured for that chain
```

**`ObservationSink` (interface, modified):**
```java
void onObservation(String provider, TxResult result, String rawResponseJson);
```

**`WatcherProperties` (`@ConfigurationProperties(prefix = "themistra.crypto.watcher")`, record):**
```java
public record WatcherProperties(
        @Min(1) int shardCount,
        @Min(1) long reconciliationIntervalMs,
        @Min(1) long correlationWindowMs,
        @Min(1) long lockAtLeastForMs,
        @Min(1) long lockAtMostForMs) {
}
```

**`Watcher` (package-private constructor — instantiated only by `WatcherRegistry`, not a Spring bean
itself; one instance per actively-watched `Watch`):**
```java
Watcher(Watch watch, List<ChainAdapter> adapters, ObservationLog observationLog,
        QuorumDecisionService quorumDecisionService, ProviderHealthTracker providerHealthTracker,
        ChainCursorRepository chainCursorRepository, WatcherProperties properties,
        MeterRegistry meterRegistry, Clock clock, ScheduledExecutorService sweepScheduler);

void start();      // subscribes to every adapter in `adapters` for watch.address()
void stop();       // sets running = false, cancels every Subscription
UUID watchId();    // for WatcherRegistry's own bookkeeping (start/stop tracking)
```

**`WatcherRegistry` (`@Component`):**
```java
public WatcherRegistry(WatchRepository watchRepository, ProviderSet providerSet,
        ObservationLog observationLog, QuorumDecisionService quorumDecisionService,
        ProviderHealthTracker providerHealthTracker, ChainCursorRepository chainCursorRepository,
        WatcherProperties properties, MeterRegistry meterRegistry, Clock clock,
        LockProvider lockProvider, ScheduledExecutorService sweepScheduler);

@Scheduled(fixedDelayString = "${themistra.crypto.watcher.reconciliation-interval-ms}")
void reconcile();
```

## Private methods

**`ProviderSet`:** none — a two-line lookup.

**`Watcher`:**
```java
private void handleObservation(String provider, TxResult result, String rawResponseJson);
private void logObservation(String provider, TxResult result, String rawResponseJson);  // AC2: called before recordAnswerAndMaybeEvaluate
private void recordAnswerAndMaybeEvaluate(String provider, TxResult result);            // updates the per-txHash correlation, evaluates once complete
private void evaluateFact(String txHash, FactType factType, Map<String, TxResult> answers, Function<TxResult, ? extends Comparable<?>> extractor, boolean requireExists);
private void advanceCursorIfNeeded(long blockNumber);   // forward-only, after the above writes
void sweepStaleCorrelations();                          // package-private (not private) so tests can invoke it directly instead of waiting on the real scheduler; marks providers absent past correlationWindowMs as LAGGING
```

**`EthereumAdapter`/`TronAdapter` (each, mirrored):**
```java
private String toRawJson(TxResult result);   // Map.of(...) of the result's own fields (amount via toPlainString()), serialized via the injected ObjectMapper
```

**`WatcherRegistry`:**
```java
private void reconcileShard(int shardIndex, List<Watch> registeredWatches);  // attempts the shard's ShedLock, starts/stops Watcher instances it owns
private int shardOf(UUID watchId);   // Math.floorMod(watchId.hashCode(), properties.shardCount())
```

## Entities used

- `Watch` (read-only — `findByStatus(REGISTERED)`).
- `ChainCursor` (read via `findByWatchId`, updated in place — no new mutator needed beyond a plain
  `lastBlock` field write, since `ChainCursorRepository.save` on a re-fetched managed entity is
  sufficient; no `@Modifying` query needed here since there's no race-safety requirement analogous to
  T15's `DELETE` — only this watch's own single `Watcher` instance ever updates its own cursor row).

## Repositories used

- `WatchRepository` (new method), `ChainCursorRepository` (new method) — both `watch/`, both extended,
  not replaced.

## Services used

- `ObservationLog`, `QuorumDecisionService`, `ProviderHealthTracker` (all existing, T08-T10, unmodified
  in logic — `ProviderHealthTracker` gains only the metrics constructor param/counter).
- `ProviderSet` (new, this task).

## Implementation notes carried into Phase 6 (verify, don't assume)

- **ShedLock's per-shard locking must use the programmatic `LockProvider`/`LockConfiguration` API, not
  the declarative `@SchedulerLock` annotation.** `@SchedulerLock` names a single, statically-fixed lock
  for one scheduled method — it cannot acquire `shardCount` dynamically-named locks within one
  reconciliation pass. Phase 6 must confirm `net.javacrumbs.shedlock.core.LockProvider`'s exact
  programmatic API (`lock(LockConfiguration)` returning `Optional<SimpleLock>`) by direct inspection of
  the pinned `7.7.0` library, not from memory, before wiring `reconcileShard`.
- **Raw-JSON fidelity is adapter-specific and must be verified, not assumed equivalent to a true wire
  capture** — per the frozen brief's own disclosed limitation.

## Unit/integration tests required

(Test files are not created in this phase — Phase 6 defers all test-writing to Phase 10; listed here only
to confirm every planned test traces to the frozen brief's Required Tests.)

- **`WatcherTest`** (unit, `FakeChainAdapter` instances as the 3 configured providers, fixed `Clock`,
  mocked `ObservationLog`/`QuorumDecisionService`/`ProviderHealthTracker`):
  - Quorum evaluated only once exactly 3 real answers exist (AC1).
  - Lagging-provider scenario: 2 answer, 1 times out (simulated via `sweepStaleCorrelations()` after the
    window) → `recordUnhealthy(..., LAGGING)`, no `evaluate` call, no exception (AC1, corrected Finding 9).
  - `ObservationLog.record` called before `QuorumDecisionService.evaluate` for the same fact, for every
    provider (AC2, call-order verification).
  - `recordHealthy` for an on-time provider; `recordDisagreement` (plus the new counter) for a provider
    whose answer differs from an `AGREED` fact (AC3).
  - A provider reporting `exists=false` contributes only to `EXISTENCE`, never `AMOUNT`/`TOKEN`/
    `CONFIRMATIONS` (AC4).
  - A duplicate `evaluate` attempt (mocked to throw the real `IllegalStateException` shape) is caught,
    not propagated (AC5).
  - `ChainCursor.lastBlock` only increases, and only after the observation/quorum writes for that tick
    (AC6, verified via mock call order).
  - A callback delivered after `stop()` performs no log/correlation/evaluation work (Finding 10).
  - `crypto.watcher.lag.seconds` reflects time since the last processed observation (AC10).
- **`WatcherRegistryTest`** (integration, real Postgres `shedlock` table via Testcontainers, two
  `WatcherRegistry` instances sharing one datasource):
  - The two instances never claim the same shard simultaneously (AC9).
  - A shard whose owning instance stops reconciling (simulating a crash) is picked up by the other
    instance within one reconciliation interval plus `lockAtMostForMs` (AC9).
  - Only `REGISTERED` watches are assigned to a shard/watched at all.
- **`WatcherModuleBoundaryTest`** / extended `ProviderModuleBoundaryTest`-style scan covering
  `adapter/ProviderSet.java`'s own import list (AC8).
- Updated `FakeChainAdapterTest` (existing file, adjusted for the new signature) confirmed green.
- A grant-migration test extension (mirrors T10/T11/T15 precedent) confirming `crypto_app` has `UPDATE`
  on `chain_cursors` and full DML on `chain.shedlock`.

## Execution order

1. `V7__crypto_app_watcher_grants.sql` — schema/grant first.
2. `adapter/ObservationSink.java` — the interface change everything else depends on.
3. `adapter/eth/EthereumAdapter.java`, `adapter/eth/EthereumAdapterConfig.java`,
   `adapter/tron/TronAdapter.java`, `adapter/tron/TronAdapterConfig.java` — updated call sites.
4. `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` and
   `FakeChainAdapterTest.java` — ripple fix, confirmed compiling before any new T16 code is written.
5. `adapter/ProviderSet.java` — depends on 3.
6. `common/config/WatcherProperties.java` — no dependency on the above.
7. `watch/WatchRepository.java`, `watch/ChainCursorRepository.java` — new query methods.
8. `provider/ProviderHealthTracker.java` — metrics addition.
9. `watch/Watcher.java` — depends on 5, 6, 7, 8, and the existing `ObservationLog`/
   `QuorumDecisionService`.
10. `watch/WatcherRegistry.java` — depends on 9 and everything else.
11. `mvn -pl services/crypto compile` — confirm the full set compiles cleanly, including the ripple-fixed
    test fixture.
12. (Phase 7 self-review, then Phase 10) test files, in the same dependency order as their production
    counterparts.
