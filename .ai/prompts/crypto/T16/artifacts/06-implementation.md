# crypto · T16 · Phase 6 — Implementation Notes

Implemented the frozen brief in full, including the Finding 1 architecture pivot and its ripple through
T06/T07/T15's own files. No test files written (Phase 6's own directive defers all test-writing to
Phase 10) beyond the minimal, disclosed fixes to *existing* tests that the `ObservationSink` signature
change and new grants broke outright (a ripple fix, not new test authorship).

## Files created

1. `db/migration/V7__crypto_app_watcher_grants.sql` — `UPDATE` on `chain_cursors`; `INSERT, SELECT,
   UPDATE, DELETE` on `chain.shedlock` (the only table in this schema `crypto_app` may `DELETE` from —
   ShedLock's own row churn, not application data).
2. `adapter/ProviderSet.java` — `Chain -> List<NamedAdapter>`. **Deviation forced by reality, not in the
   Phase 5 plan:** `adaptersFor` returns `List<ProviderSet.NamedAdapter>` (a new nested record pairing a
   provider name with its `ChainAdapter`), not the plain `List<ChainAdapter>` the plan specified. Reason:
   implementing `Watcher.sweepStaleCorrelations` (identifying which *configured* provider hasn't
   answered yet, for the lagging signal) requires knowing every configured provider's name up front —
   but `ChainAdapter` itself exposes no provider-identity accessor, and adding one to the VERBATIM
   interface was rejected for the same reason T14 rejected extending it for a current-block method.
   `ProviderSet` is the one place that legitimately holds the concrete `EthereumAdapter`/`TronAdapter`
   types (to call their new `providerName()` accessors — themselves new, non-VERBATIM additions to those
   two concrete classes only) while still handing every other caller the abstract, implementation-
   agnostic view (L14/AC7 preserved).
3. `common/config/WatcherProperties.java` — as planned.
4. `watch/Watcher.java` — as planned, with the `NamedAdapter`-based constructor above.
5. `watch/WatcherRegistry.java` — as planned, with one correctness addition beyond the Phase 5 plan: shard
   locks are **renewed** via `SimpleLock.extend(...)` on every reconciliation tick a replica already
   holds them, not merely acquired once. Verified directly against the pinned ShedLock `7.7.0` library
   (via `javap`) that `extend` is a real, non-throwing implementation on the JDBC-backed provider before
   relying on it — without renewal, a healthy replica would silently lose a shard to `lockAtMostFor`
   expiry while still alive, which the frozen brief's own AC9 ("no watch driven by more than one replica
   simultaneously") would then risk violating in the *other* direction (both replicas running it after
   an unwarranted handover).
6. `common/ShedLockConfig.java` — **new file, not in the Phase 4/5 Files-to-Create list**, the same
   "functionally necessary, not spec-named" situation `ObservationLog`/`QuorumDecisionService`/
   `ProviderHealthTracker` were each in for their own tasks: `WatcherRegistry` cannot be wired without a
   `LockProvider` bean existing somewhere, and `design.md` §6 names no such file. One `@Bean
   LockProvider` using `JdbcTemplateLockProvider(DataSource)`, the default `"shedlock"` table name
   (matching the already-provisioned table exactly).

## Files modified

1. `adapter/ObservationSink.java` — signature change (Finding 1), documented in its own Javadoc.
2. `adapter/eth/EthereumAdapter.java` — added `objectMapper` field/constructor param, `toRawJson`
   helper, and a new public `providerName()` accessor (see `ProviderSet` above); the one
   `sink.onObservation(...)` call site updated.
3. `adapter/tron/TronAdapter.java` — same three additions, mirrored.
4. `adapter/eth/EthereumAdapterConfig.java`, `adapter/tron/TronAdapterConfig.java` — pass an
   `ObjectMapper` bean into the adapter constructor.
5. `provider/ProviderHealthTracker.java` — `MeterRegistry` constructor param; a
   `crypto.provider.disagreements` counter increment inside `recordDisagreement`.
6. `watch/WatchRepository.java` — `findByStatus(WatchStatus)`.
7. `watch/ChainCursorRepository.java` — `findByWatchId(UUID)`.
8. `watch/ChainCursor.java` — **new mutator not in the Phase 5 plan**: `advanceTo(long, Instant)`,
   forward-only (silent no-op if the given block is not greater than the current one), mirroring
   `ProviderHealth`'s own "no raw setters, named mutators" convention. The plan assumed a plain
   re-fetch-and-`save` would suffice with no entity-level guard; implementing `Watcher`'s cursor-advance
   call site made clear the forward-only invariant (AC6) belongs on the entity itself, not scattered
   across every caller.
9. `application.properties` — new `themistra.crypto.watcher.*` keys.
10. `CryptoServiceApplication.java` — updated a now-stale class Javadoc comment (it previously said "no
    multi-replica-coordinated job exists yet"), clarifying why `@EnableSchedulerLock` still isn't needed
    despite `WatcherRegistry`'s arrival (programmatic `LockProvider` API, not the declarative
    annotation).

## Ripple fixes (disclosed, not scope creep — the `ObservationSink` signature change and new grants
break real, already-passing tests otherwise)

- `services/crypto/src/test/java/com/themistra/crypto/adapter/FakeChainAdapter.java` — added a
  `providerName` field and a `FakeChainAdapter(Chain, String)` overload; the existing
  `FakeChainAdapter(Chain)` constructor now delegates to it with a default name, so every pre-T16
  `new FakeChainAdapter(Chain)` call site across T06-T15's own tests keeps compiling unchanged. The one
  `onObservation` call site updated to the 3-arg shape.
- `FakeChainAdapterTest.java`, `EthereumAdapterTest.java`, `TronAdapterTest.java` — every
  `received::add`/`result -> { }` `ObservationSink` lambda/method-reference updated to the 3-arg shape
  (~35 call sites across the three files); `EthereumAdapterTest`/`TronAdapterTest`'s own direct
  `new EthereumAdapter(...)`/`new TronAdapter(...)` constructions updated with a real `ObjectMapper`.
- `TronAdapterConfigTest.java` — every direct `.tronAdapters(properties, environment, 3000L)` call
  updated to pass a real `ObjectMapper`; both narrow `ApplicationContextRunner`-based `TestConfig`
  classes (`EthereumAdapterConfigTest`, `TronAdapterConfigTest`) gained an `@Bean ObjectMapper` (neither
  slice auto-configures Jackson the way a full `@SpringBootTest` would).
- `ProviderHealthTrackerTest.java` — `new ProviderHealthTracker(...)` gained a real
  `SimpleMeterRegistry` (Micrometer's own lightweight, real implementation — not mocked; simplest and
  most realistic choice for a counter that previously didn't exist).
- `ChainBaselineMigrationIntegrationTest.java` — Flyway version-list assertion extended to `"7"`;
  `shedlock` removed from `UNGRANTED_TABLES` (now granted); explanatory comments updated.
- `WatchRepositoryIntegrationTest.java` (T15's own file) —
  `cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnChainCursors` renamed to
  `cryptoAppCanInsertSelectAndUpdateButNotDeleteOnChainCursors` and its `UPDATE`-denied assertion
  flipped to `UPDATE`-succeeds, since T16's `V7` migration changed what was, at the time T15 wrote that
  test, a correct assertion.
- `WatchModuleBoundaryTest.java` (T15's own file) — redesigned from a flat
  fully-forbidden-plus-one-allow-listed-package shape to a per-package allow-list map, since T16 gives
  `watch/` its first real, legitimate dependencies on `observation`/`provider`/`quorum`/`adapter`
  (previously fully forbidden) alongside the pre-existing `token` allow-list.

## Verification performed before relying on library/framework behavior (not assumed)

- **ShedLock 7.7.0**: `LockProvider.lock(LockConfiguration)` returns `Optional<SimpleLock>`;
  `JdbcTemplateLockProvider(DataSource)` constructor exists; `AbstractSimpleLock.extend(...)` (which
  `StorageBasedLockProvider$StorageLock`, `JdbcTemplateLockProvider`'s superclass, actually implements
  via `doExtend`) is a real, non-throwing implementation — all confirmed via direct `javap` inspection of
  the pinned jars, not memory.
- **Micrometer 1.15.0**: `Gauge.builder(String, T, ToDoubleFunction<T>)` and `Counter.builder(String)`
  signatures confirmed via `javap` before use.
- **web3j 6.0.0**: `Log.getLogIndex()` returns `BigInteger`, confirmed via `javap`, used in
  `EthereumAdapter.toRawJson`.

## Mapping to acceptance criteria

- **AC1** (exactly-3, corrected): `Watcher.evaluateFact` only proceeds past `providerAnswers.size() !=
  3`; never calls `QuorumDecisionService.evaluate` otherwise.
- **AC2** (log before decide): `handleObservation` calls `logObservation` before
  `recordAnswerAndMaybeEvaluate`.
- **AC3** (health signals): `recordAnswerAndMaybeEvaluate` calls `recordHealthy` on every answer;
  `sweepStaleCorrelations` calls `recordUnhealthy(..., LAGGING)` for a non-responder;
  `recordDisagreementsIfAny` calls `recordDisagreement` for a minority answer.
- **AC4** (exists=false exclusion): `logObservation` and `evaluateFact`'s `requireExists` parameter both
  gate `AMOUNT`/`TOKEN`/`CONFIRMATIONS` on `result.exists()`; `EXISTENCE` never requires it.
- **AC5** (evaluate-once, duplicate-safe): `evaluatedFacts` guards the primary path; the
  `IllegalStateException` catch is the disclosed, restart-survivability backstop.
- **AC6** (cursor forward-only): `ChainCursor.advanceTo` itself enforces this, not the caller.
- **AC7/AC8** (L14/L15): confirmed by inspection of every new/modified file's imports; `Watcher`/
  `WatcherRegistry` touch no concrete `ChainAdapter` implementation, only the interface and `ProviderSet`.
- **AC9** (O5, shard safety): `WatcherRegistry.ownsShard`'s acquire-or-renew-or-release logic.
- **AC10** (metrics): `crypto.watcher.lag.seconds` (Watcher constructor `Gauge`),
  `crypto.provider.disagreements` (`ProviderHealthTracker.recordDisagreement`).

## Build verification

- `mvn -pl services/crypto compile` and `test-compile` — both succeed cleanly, zero new warnings.
- `mvn -pl services/crypto -am test` (full module regression) — 505 tests, 499 passing, 6 failures, the
  same pre-existing, disclosed set (`ObservationRepositoryIntegrationTest`,
  `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
  `TokenAllowlistRepositoryIntegrationTest` — T08-T11's own work) — zero regressions, zero new failures,
  after the disclosed ripple fixes above.
