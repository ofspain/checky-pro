# crypto · T18 · Phase 5 — Implementation Plan

No code below — signatures and structure only, planning execution for Phase 6.

## Files to Create

1. `services/crypto/src/main/java/com/themistra/crypto/reorg/ReorgDetector.java`
2. `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgDetectorTest.java`
3. `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgModuleBoundaryTest.java`

## Files to Modify

1. `services/crypto/src/main/java/com/themistra/crypto/watch/Watcher.java`
2. `services/crypto/src/main/java/com/themistra/crypto/watch/WatcherRegistry.java`
3. `services/crypto/src/main/java/com/themistra/crypto/watch/ChainCursor.java`
4. `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherTest.java`
5. `services/crypto/src/test/java/com/themistra/crypto/watch/WatcherRegistryTest.java` (ripple: constructor arity)
6. `services/crypto/src/test/java/com/themistra/crypto/watch/ChainCursorTest.java`
7. `services/crypto/src/test/java/com/themistra/crypto/watch/WatchModuleBoundaryTest.java`

No migration — no new tables, columns, or grants (frozen brief).

## Public methods (signatures)

**`ReorgDetector` (new, `@Component`, package `reorg`):**
- `ReorgDetector(OutboxPublisher outboxPublisher, Clock clock)`
- `void reorg(UUID watchId, UUID invoiceUuid, String chain, String txHash, String tokenContractAddress)`

**`ChainCursor` (modified):**
- `void invalidate(Instant now)` — resets `lastBlock` to `UNSTARTED_SENTINEL`, `lastFinalizedBlock` to
  `null`, `txHash`/`amount`/`fromAddress`/`toAddress` to `null`, updates `updatedAt`.

**`WatcherRegistry` (modified constructor):**
- Gains one trailing param: `..., ReorgDetector reorgDetector)`.

## Private/package-private methods (signatures)

**`Watcher` (modified):**
- Constructor gains one trailing param: `ReorgDetector reorgDetector`; new field `private final
  ReorgDetector reorgDetector;`.
- `void pollFinality()` — modified body: calls `checkForReorg()` first, then the existing
  `pendingFinality` iteration unchanged.
- `private void checkForReorg()` (new) — looks up `chainCursorRepository.findByWatchId(watch.watchId())`;
  returns immediately if absent or `cursor.txHash() == null`. Otherwise, for each configured
  `ProviderSet.NamedAdapter`, calls `adapter().getTx(txHash)` inside a `try/catch (RuntimeException)`
  (transport failure → `providerHealthTracker.recordUnhealthy(..., LAGGING)`, excluded from this tick's
  answers, mirrors the finality poll's own established pattern); if fewer than 3 real answers resulted,
  returns without declaring anything (mirrors the exactly-3 discipline). Otherwise computes
  `majorityValue(existsAnswers.values())`; if still `true`, returns (no reorg). If `false`: builds
  `List<ProviderAnswer<Boolean>>` from the answers, calls the existing `recordDisagreementsIfAny` (flags
  the dissenting minority still reporting `true`), calls `cursor.invalidate(clock.instant())` +
  `chainCursorRepository.save(cursor)`, removes `txHash` from `pendingFinality` (if present — a no-op
  otherwise), and calls `reorgDetector.reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), txHash,
  watch.tokenContractAddress())`.

**`ReorgDetector` (private):**
- `private String idempotencyKey(String chain, String txHash)` — `"{chain}:{txHash}:reorged"`.
- Payload record: `ReorgedPayload(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain, String txHash, String tokenContractAddress, Instant occurredAt)`.

## Entities used

`ChainCursor` (modified, via `chainCursorRepository`), `Watch` (read-only, via `Watcher`'s own field —
never passed into `reorg/`), `OutboxEvent` (via `OutboxPublisher`, unmodified).

## Repositories used

`ChainCursorRepository` (existing `findByWatchId`/`save`, no new methods needed).

## Services used

`OutboxPublisher`, `ProviderHealthTracker` (existing `recordDisagreement`/`recordUnhealthy`, unmodified).

## Unit/integration tests required

- `ReorgDetectorTest` (new) — mirrors `TxLifecyclePublisherTest`'s mocked-`OutboxPublisher` style:
  asserts `aggregateType="tx-reorged"`, `aggregateId=watchId.toString()`, `eventType="chain.tx.reorged"`,
  exact idempotency-key format, full payload shape, and that a `DataIntegrityViolationException` is
  swallowed while a different exception propagates.
- `ChainCursorTest` (extend) — `invalidate` resets every field from a populated (seen + finalized) state
  back to the placeholder shape.
- `WatcherTest` (extend, reusing the `seenCursor()`/`FakeChainAdapter.scriptTx(...)` fixtures already
  established):
  - `shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` (named test).
  - A reorg discovered right after `seen` alone (no `CONFIRMATIONS`/`FINALITY` decided yet).
  - A reorg discovered after `confirmed` was already emitted.
  - A reorg discovered after `finalized` was already emitted — proves `checkForReorg` keys off the
    cursor's `txHash`, not `pendingFinality` membership (AC3).
  - A tick where the fresh `getTx` majority is still `exists=true` — no reorg, cursor untouched (AC5).
  - A tick with only 2 real `getTx` answers (third provider throws) — no reorg declared (AC7).
  - The dissenting minority provider in a 2-1 reorg-detecting split is flagged via
    `providerHealthTracker.recordDisagreement` (AC8).
  - A second `pollFinality()` tick after the cursor is already invalidated — no exception, no further
    `getTx` calls (AC9), verified via `verify(providerHealthTracker, never())...` or an interaction count
    that doesn't increase between the two ticks.
- `WatcherRegistryTest` (fix ripple only) — thread a mocked `ReorgDetector` through the existing
  `newRegistry`/direct-construction call sites; no new scenarios required by this task's own scope.
- `ReorgModuleBoundaryTest` (new) — source-scan `reorg/`'s own files, asserting no import from `watch/`
  or `adapter/` beyond what the frozen brief's dependency list allows (none — `ReorgDetector` only needs
  `events.OutboxPublisher` and JDK types).
- `WatchModuleBoundaryTest` (extend) — add `reorg.ReorgDetector` to the allowed-imports map.

## Execution order

1. `ChainCursor.java` — `invalidate(Instant now)`. Unit-test immediately (`ChainCursorTest`).
2. `ReorgDetector.java` — new component. Unit-test immediately (`ReorgDetectorTest`), following
   `TxLifecyclePublisherTest`'s established pattern, before wiring it into `Watcher`.
3. `Watcher.java` — `checkForReorg()` and the `pollFinality()` wiring. Run the existing `WatcherTest`
   suite as a regression check before adding the new T18 scenarios.
4. `WatcherRegistry.java` — thread `ReorgDetector` through; fix the `WatcherRegistryTest` ripple.
5. `ReorgModuleBoundaryTest.java`, `WatchModuleBoundaryTest.java` extension.
6. Full new `WatcherTest` T18 scenarios (seen/confirmed/finalized reorg timing, majority-still-true,
   below-3-answers, disagreement-flagging, post-invalidation no-op).
7. Full module regression (`mvn -pl services/crypto -am test`) to confirm the pre-existing 6-failure
   baseline is unchanged and the new test count matches expectations.
