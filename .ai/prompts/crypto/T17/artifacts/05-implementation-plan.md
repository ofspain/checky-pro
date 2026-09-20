# crypto · T17 · Phase 5 — Implementation Plan

No code below — signatures and structure only, planning execution for Phase 6.

## Files to Create

1. `services/crypto/src/main/resources/db/migration/V8__crypto_chain_cursors_tx_snapshot.sql`
2. `services/crypto/src/main/java/com/themistra/crypto/watch/TxLifecyclePublisher.java`
3. `services/crypto/src/test/java/com/themistra/crypto/watch/TxLifecyclePublisherTest.java`

## Files to Modify

1. `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`
2. `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
3. `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
4. `services/crypto/src/main/java/com/themistra/crypto/common/config/WatcherProperties.java`
5. `services/crypto/src/main/resources/application.properties`
6. `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`
7. `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherRegistryTest.java` (ripple: `WatcherProperties` constructor arity change)
8. `services/crypto/src/test/java/com/themistra/crypto/watch/ChainCursorTest.java`
9. `services/crypto/src/test/java/com/themistra/crypto/watch/WatchModuleBoundaryTest.java` (extend allowed imports for `finality`)

## Migration (V8)

```
ALTER TABLE chain.chain_cursors
    ADD COLUMN tx_hash VARCHAR(128),
    ADD COLUMN amount NUMERIC(78, 0),        -- matches watches.expected_amount's precision (V1)
    ADD COLUMN from_address VARCHAR(128),
    ADD COLUMN to_address VARCHAR(128);
```
No grant statement — `crypto_app`'s existing `UPDATE` on `chain_cursors` (`V7`) already covers new columns on the same table.

## Public methods (signatures)

**`ChainCursor` (modified):**
- `void recordSeenTransaction(String txHash, java.math.BigDecimal amount, String fromAddress, String toAddress, Instant now)` — write-once: no-op if `this.txHash` is already set (disclosed limitation: a watch that legitimately observes a second, distinct `txHash` after the first keeps only the first snapshot — acceptable for this task's scope, flagged for review).
- `void advanceFinalizedTo(long finalizedBlockNumber, Instant now)` — forward-only, mirrors `advanceTo`.
- New accessors: `String txHash()`, `BigDecimal amount()`, `String fromAddress()`, `String toAddress()`.

**`TxLifecyclePublisher` (new, `@Component`):**
- `TxLifecyclePublisher(OutboxPublisher outboxPublisher, Clock clock)`
- `void seen(Watch watch, String txHash)`
- `void confirmed(Watch watch, String txHash, int confirmations)`
- `void finalized(Watch watch, ChainCursor cursor)`

**`WatcherRegistry` (modified constructor):**
- `WatcherRegistry(WatchRepository, ProviderSet, ObservationLog, QuorumDecisionService, ProviderHealthTracker, ChainCursorRepository, WatcherProperties, MeterRegistry, Clock, LockProvider, TxLifecyclePublisher, List<FinalityPolicy>)` — two new trailing params.

**`WatcherProperties` (modified record):**
- `record WatcherProperties(int shardCount, long reconciliationIntervalMs, long correlationWindowMs, long lockAtLeastForMs, long lockAtMostForMs, long finalityPollIntervalMs)` — new trailing field.

## Private/package-private methods (signatures)

**`Watcher` (modified):**
- Constructor gains `TxLifecyclePublisher txLifecyclePublisher, List<FinalityPolicy> finalityPolicies` params; resolves `this.finalityPolicy = finalityPolicies.stream().collect(toMap(FinalityPolicy::chain, identity())).get(Chain.valueOf(watch.chain()))` once, stores only the single resolved policy (not the map).
- New field: `private final Set<String> pendingFinality = ConcurrentHashMap.newKeySet();`
- New field: `private volatile ScheduledFuture<?> finalityPollFuture;`
- `start()` — additionally schedules `finalityPollFuture = sweepScheduler.scheduleWithFixedDelay(this::pollFinality, finalityPollIntervalMs, finalityPollIntervalMs, MILLISECONDS)` (same shared scheduler, not a second thread pool).
- `stop()` — additionally cancels `finalityPollFuture` (same `sweepScheduler.shutdownNow()` already covers eventual cleanup).
- `evaluateFact(...)` — return type changes `void` → `QuorumDecision` (nullable: null when not decided this call, whether from the already-evaluated-this-tick guard, an insufficient-answers skip, or a caught duplicate-decision `IllegalStateException`). Behavior otherwise unchanged.
- `recordAnswerAndMaybeEvaluate(...)` — captures the `EXISTENCE` and `CONFIRMATIONS` `evaluateFact` return values; after all four fact evaluations, calls the two new handlers below.
- `private void handleSeenIfAgreed(String txHash, QuorumDecision existenceDecision, Map<String, TxResult> answers)` — no-op unless `existenceDecision != null && existenceDecision.outcome() == AGREED`; computes the majority `exists` value via the new shared `majorityValue` helper; returns (no event, Finding #10) if that value is `false`; otherwise picks one `exists=true` answer as the source for `amount`/`fromAddress`/`toAddress`, calls `chainCursorRepository.findByWatchId(...).ifPresent(cursor -> { cursor.recordSeenTransaction(...); chainCursorRepository.save(cursor); })`, adds `txHash` to `pendingFinality`, calls `txLifecyclePublisher.seen(watch, txHash)`.
- `private void handleConfirmedIfAgreed(String txHash, QuorumDecision confirmationsDecision, Map<String, TxResult> answers)` — no-op unless `AGREED`; computes majority confirmations count via `majorityValue`; calls `txLifecyclePublisher.confirmed(watch, txHash, count)`.
- `private static <T> T majorityValue(Collection<T> values)` — extracted from `recordDisagreementsIfAny`'s existing inline logic (behavior-preserving refactor; `recordDisagreementsIfAny` calls this too instead of duplicating it).
- `void pollFinality()` — package-private (mirrors `sweepStaleCorrelations`'s own testability convention); iterates `List.copyOf(pendingFinality)`, calls `pollFinalityFor` per `txHash`.
- `private void pollFinalityFor(String txHash)` — for each configured provider: calls `getFinalityStatus(txHash)` inside a try/catch (transport failure → `recordUnhealthy(..., LAGGING)`, excluded from this tick's answers, mirrors existing provider-failure handling); on success, logs the raw `FinalityStatus` verbatim via `observationLog.record(..., FactType.FINALITY, rawJson)` **before** evaluating (L3, Finding #3), evaluates `finalityPolicy.isFinal(status)`, and collects the boolean. If fewer than 3 real answers resulted, returns without calling `evaluate` (Finding #6). Otherwise calls `quorumDecisionService.evaluate(chain, txHash, FINALITY, providerAnswers)`, `recordDisagreementsIfAny`, and on `AGREED true`: `chainCursorRepository.findByWatchId(...).ifPresent(cursor -> { cursor.advanceFinalizedTo(status.finalizedBlockNumber(), now); chainCursorRepository.save(cursor); txLifecyclePublisher.finalized(watch, cursor); })`, then removes `txHash` from `pendingFinality`. On `AGREED false` or `HELD`, also removes from `pendingFinality` (a decided-but-not-final outcome is permanent per the one-shot design, Finding #10's same reasoning extended to `FINALITY` — no further polling can ever change it).
- A small `private String toRawJson(FinalityStatus status)` helper (Jackson-serialized, mirrors `EthereumAdapter`/`TronAdapter`'s own `toRawJson` convention) — requires injecting `ObjectMapper` into `Watcher`'s constructor (new param) or building the JSON inline via a `Map.of(...)` + the already-injected... **note:** `Watcher` does not currently have an `ObjectMapper`; plan adds one as a new constructor parameter, threaded from `WatcherRegistry`.

**`TxLifecyclePublisher` (new, private):**
- `private void publish(String aggregateType, Watch watch, String eventType, String txHash, Object payload)` — builds `idempotencyKey = watch.chain() + ":" + txHash + ":" + eventType-suffix`, calls `outboxPublisher.publish(aggregateType, watch.watchId().toString(), "chain." + aggregateType-ish, idempotencyKey, payload)` (exact literal strings finalized in Phase 6, per `EventTopics`'s existing `"tx-seen"→"chain.tx.seen"` etc. mapping), wrapped in `try { ... } catch (DataIntegrityViolationException e) { log.debug(...); }` (Finding #12).
- Payload records: `SeenPayload`, `ConfirmedPayload`, `FinalizedPayload` — fields per the frozen brief's field-sourcing map.

## Entities used

`ChainCursor` (modified), `Watch` (read-only), `OutboxEvent` (via `OutboxPublisher`, unmodified), `Observation` (via `ObservationLog`, unmodified), `QuorumDecision` (via `QuorumDecisionService`, unmodified).

## Repositories used

`ChainCursorRepository` (existing `findByWatchId`/`save`, no new methods needed), `OutboxEventRepository` (transitively, via `OutboxPublisher`, unmodified).

## Services used

`QuorumDecisionService`, `ObservationLog`, `ProviderHealthTracker`, `OutboxPublisher`, `FinalityPolicy` implementations — all existing, called not modified.

## Unit/integration tests required

- `TxLifecyclePublisherTest` (new) — mirrors `ProviderDegradedPublisherTest`'s mocked-`OutboxPublisher` style: asserts `aggregateType`/`aggregateId`/`eventType`/`idempotencyKey`/payload for each of `seen`/`confirmed`/`finalized`; asserts a `DataIntegrityViolationException` from `outboxPublisher.publish` is swallowed, not propagated (Finding #12); asserts `watchId` used as `aggregateId` (AC5).
- `ChainCursorTest` (extend) — `recordSeenTransaction` writes once, second call with a different `txHash` is a no-op (disclosed limitation, still verified); `advanceFinalizedTo` forward-only (mirrors the existing `advanceTo` test pair).
- `WatcherTest` (extend, mirrors T16's existing structure and its `MutableClock`/`FakeChainAdapter` fixtures):
  - `shouldEmitChainTxSeenOnQuorumAgreedFirstSighting` (named test, R8).
  - a test confirming no `seen` on `EXISTENCE` `HELD`.
  - a test confirming no `seen` on `EXISTENCE` `AGREED false` (Finding #10).
  - `shouldEmitChainTxConfirmedWithConfirmationCount` (named test, R9) — confirms exactly-once emission.
  - `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` (named test, R10) — using a `FakeChainAdapter`-style stub returning controllable `FinalityStatus`/`FinalityPolicy` (or a hand-rolled test `FinalityPolicy`), confirms no `finalized` while `isFinal` is false across repeated poll ticks, then emission once it becomes true.
  - a test confirming the finality poll never starts before a `ChainCursor` snapshot exists (i.e., before `SEEN`).
  - a test confirming a finality-poll tick with fewer than 3 real answers never calls `QuorumDecisionService.evaluate` for `FINALITY` (Finding #6).
  - a test confirming the raw `FinalityStatus` is logged via `ObservationLog.record(..., FINALITY, ...)` before `QuorumDecisionService.evaluate` is called (call-order verification, mirrors the existing EXISTENCE ordering test, Finding #3).
  - `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent` (named test, R12/L5) — asserted at the `TxLifecyclePublisherTest` level primarily; a `Watcher`-level test confirms the right `(chain, txHash)` pair reaches the publisher for each event type.
  - a test confirming `pendingFinality` no longer polls a `txHash` after `FINALITY` reaches any decided outcome (`AGREED true`, `AGREED false`, or `HELD`).
- `WatcherRegistryTest` (fix ripple only) — update both `WatcherProperties(...)` factory methods for the new trailing field; no new scenarios required by this task's own scope.
- `WatchModuleBoundaryTest` (extend) — add `finality` to the allowed-imports map for `watch/`'s files (`FinalityPolicy` import).

## Execution order

1. `V8__crypto_chain_cursors_tx_snapshot.sql` (schema first).
2. `ChainCursor.java` — new columns, `recordSeenTransaction`, `advanceFinalizedTo`, accessors. Unit-test immediately (`ChainCursorTest`).
3. `TxLifecyclePublisher.java` — new component. Unit-test immediately (`TxLifecyclePublisherTest`), following `ProviderDegradedPublisherTest`'s established pattern, before wiring it into `Watcher`.
4. `WatcherProperties.java` + `application.properties` — new config field, so `Watcher`/`WatcherRegistry` changes below compile against a settled properties shape.
5. `Watcher.java` — `evaluateFact` return-type change and the two new `handle*IfAgreed` hooks first (smaller, testable in isolation against existing `WatcherTest` scenarios); then the finality-poll mechanism (`pollFinality`/`pollFinalityFor`, new fields, `start()`/`stop()` wiring). Test incrementally against the existing `WatcherTest` suite (regression) before adding the new T17-specific tests.
6. `WatcherRegistry.java` — thread the two new constructor params through; fix the ripple in `WatcherRegistryTest`'s `WatcherProperties(...)` fixtures.
7. `WatchModuleBoundaryTest.java` — extend the allowed-imports map.
8. Full new `WatcherTest` T17 scenarios (seen/confirmed/finalized/ordering/skip-tick/pending-finality-cleanup).
9. Full module regression (`mvn -pl services/crypto -am test`) to confirm the pre-existing 6-failure baseline is unchanged and the new test count matches expectations.
