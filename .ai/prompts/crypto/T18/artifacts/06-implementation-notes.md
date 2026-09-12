# crypto · T18 · Phase 6 — Implementation Notes

## What changed

**Created:**
- `reorg/ReorgDetector.java` — `reorg(UUID watchId, UUID invoiceUuid, String chain, String txHash, String tokenContractAddress)`, primitive-typed (no `watch/` entity import, L15), mirrors `TxLifecyclePublisher`'s pattern exactly: idempotency key, payload, `OutboxPublisher.publish`, catching `DataIntegrityViolationException` as benign.
- `reorg/ReorgModuleBoundaryTest.java` — source-scan test for the new `reorg/` package; every feature-module prefix except a narrow `events.OutboxPublisher` allowance is fully forbidden (verified: `ReorgDetector` needs nothing else).

**Modified:**
- `watch/ChainCursor.java` — new `invalidate(Instant now)`: resets `lastBlock` to `UNSTARTED_SENTINEL`, `lastFinalizedBlock` to `null`, and the transaction snapshot to `null`.
- `watch/Watcher.java` — new constructor param `ReorgDetector reorgDetector`; `pollFinality()` now calls a new `checkForReorg()` first, on every tick. `checkForReorg()` looks up the watch's `ChainCursor`; if it holds a `txHash`, re-queries `ChainAdapter.getTx(txHash)` across all 3 configured providers (a synchronous pull, unaffected by each adapter's own forward-only scan position); on a fresh majority of `exists=false`, flags the dissenting minority via the existing `recordDisagreementsIfAny`, invalidates and saves the cursor, removes the `txHash` from `pendingFinality` if present, and calls `ReorgDetector.reorg(...)`. A tick with fewer than 3 real answers declares nothing.
- `watch/WatcherRegistry.java` — threads `ReorgDetector` into `Watcher`'s constructor.
- `watch/WatchModuleBoundaryTest.java` (ripple, required for correctness) — allow-list extended for `reorg.ReorgDetector`.
- `watch/WatcherTest.java`, `watch/WatcherRegistryTest.java` (ripple only — new constructor param threaded through existing fixtures; no new test scenarios, per this phase's own rule).

## Mapping to the plan and acceptance criteria

Matches Phase 5's plan exactly. AC1/AC2 (reorg detection + walk-back): `checkForReorg` + `ChainCursor.invalidate`. AC3 (seen/confirmed/finalized timing) and AC5/AC7/AC8/AC9 are Phase 10's test job, not exercised by new tests in this phase (per this phase's own "do not write tests" rule) — but the underlying mechanism (`checkForReorg` keyed off `ChainCursor.txHash()` rather than `pendingFinality`) already satisfies AC3's "after finalized" case by construction, since it doesn't depend on finality-poll state at all. AC4 (idempotency key format): `ReorgDetector`'s key construction, identical shape to T17's. AC6 (module boundaries): `ReorgModuleBoundaryTest`, passing.

## Deviations from the plan, forced by reality

None. The frozen brief (Phase 4) already absorbed the one major design correction (the push-to-pull architecture pivot) before implementation began, so Phase 6 itself was a direct, uneventful translation of the frozen design into code.

One ripple not explicitly called out in the Phase 5 plan: `checkForReorg`'s own `providerHealthTracker.recordHealthy(...)` calls on a successful `getTx` (consistent with the existing, established pattern of recording health on every successful provider poll response — `pollFinalityFor` already does the same for `getFinalityStatus`) increased the total `recordHealthy` call counts in two pre-existing T17 `WatcherTest` scenarios that assert an exact count after multiple `pollFinality()` ticks. Both assertions were updated with the corrected counts and an inline explanation of the new arithmetic — not a behavior change to defend, just a more precise test now that `pollFinality()` does more per tick than it used to.

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly. `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest,ReorgModuleBoundaryTest` — 78/78 pass (all pre-existing scenarios still green after the ripple fixes). Full module regression (`mvn -pl services/crypto -am test`): same 4 pre-existing failing files, same pre-existing failure count, zero regressions.
