# crypto · T17 · Phase 6 — Implementation Notes

## What changed

**Created:**
- `db/migration/V8__crypto_chain_cursors_tx_snapshot.sql` — `ALTER TABLE chain.chain_cursors ADD COLUMN tx_hash, amount, from_address, to_address`. No grant statement (already covered by T16's `V7`).
- `watch/TxLifecyclePublisher.java` — `seen(Watch, String)`, `confirmed(Watch, String, int)`, `finalized(Watch, ChainCursor)`, each building the deterministic idempotency key and calling `OutboxPublisher.publish`, catching `DataIntegrityViolationException` as benign.

**Modified:**
- `watch/ChainCursor.java` — four new nullable columns/fields + accessors; `recordSeenTransaction(...)` (write-once); `advanceFinalizedTo(...)` (forward-only, mirrors `advanceTo`); stale class Javadoc corrected (previously claimed T16 would populate `lastFinalizedBlock` — it didn't, by design, and this task now does).
- `watch/Watcher.java` — the bulk of this task's logic:
  - New constructor params: `ObjectMapper`, `TxLifecyclePublisher`, `List<FinalityPolicy>` (resolved to a single per-chain `FinalityPolicy` field once, in the constructor), `finalityPollIntervalMs`.
  - `evaluateFact`'s return type changed `void` → `QuorumDecision` (nullable) so callers can react to a freshly-made `AGREED` decision — `QuorumDecision` itself carries no value, so the actual agreed value is still read from the in-hand `answers` map via a new `majorityValue` helper (extracted from `recordDisagreementsIfAny`'s pre-existing inline logic, behavior-preserving).
  - `recordAnswerAndMaybeEvaluate` now captures the `EXISTENCE`/`CONFIRMATIONS` decisions and calls the two new handlers, `handleSeenIfAgreed`/`handleConfirmedIfAgreed`, before pruning the correlation.
  - `handleSeenIfAgreed`: no-op unless `EXISTENCE` was freshly decided `AGREED`; no-op if the agreed value is `false` (AC1/Finding #10); otherwise writes the `ChainCursor` snapshot, adds the `txHash` to `pendingFinality`, publishes `seen`.
  - `handleConfirmedIfAgreed`: no-op unless `CONFIRMATIONS` was freshly decided `AGREED`; publishes `confirmed` with the majority count.
  - New finality poll: `start()`/`stop()` schedule/cancel `pollFinality` on the same shared `sweepScheduler` (no second thread pool). `pollFinality()` (package-private, testable) iterates `pendingFinality`; `pollFinalityFor(txHash)` polls every configured provider's `getFinalityStatus`, logs each raw response verbatim via `ObservationLog.record(..., FINALITY, ...)` *before* evaluating (Finding #3), skips the tick entirely if fewer than 3 real answers resulted (Finding #6), quorum-evaluates `FINALITY`, and on `AGREED true` advances `ChainCursor.lastFinalizedBlock` and publishes `finalized`; any decided outcome (`AGREED true`, `AGREED false`, `HELD`→still pending) is handled per the frozen brief (`AGREED false` removes it permanently, `HELD` leaves it pending for a future tick).
- `watch/WatcherRegistry.java` — threads `ObjectMapper`, `TxLifecyclePublisher`, `List<FinalityPolicy>` through to `Watcher`'s constructor; new constructor params.
- `common/config/WatcherProperties.java` — new `finalityPollIntervalMs` field.
- `application.properties` — new `themistra.crypto.watcher.finality-poll-interval-ms` key.
- `watch/WatchModuleBoundaryTest.java` (ripple, required for compilation/correctness, not a new test scenario) — extended the allowed-imports map for `finality.FinalityPolicy`, `adapter.model.FinalityStatus`, `quorum.QuorumDecision`/`QuorumOutcome`, and `events.OutboxPublisher` (the last needed by the new `TxLifecyclePublisher`, mirroring `ProviderDegradedPublisher`'s identical, already-established need); `events` is no longer fully forbidden for `watch/` as a package (only `TxLifecyclePublisher` actually imports it).
- `watch/WatcherTest.java`, `watch/WatcherRegistryTest.java` (ripple only — new constructor params threaded through existing fixtures/helpers; no new test scenarios, per this phase's own rule that new tests are Phase 10's job).
- `ChainBaselineMigrationIntegrationTest.java` (ripple — extended the hardcoded Flyway version-list assertion to include `"8"`, mirroring the identical ripple T16 made for `"7"`).

## Mapping to the plan and acceptance criteria

Every file matches Phase 5's plan exactly, with one addition not explicitly named there: `ChainBaselineMigrationIntegrationTest.java`'s ripple fix (the plan didn't call it out, but it's the same class of "existing hardcoded assertion made stale by a new migration" ripple T16 already established as necessary, not scope creep).

- AC1/AC4 (R8, seen + idempotency key): `handleSeenIfAgreed` + `TxLifecyclePublisher.seen`.
- AC2 (R9, one-shot confirmed): `handleConfirmedIfAgreed` + `TxLifecyclePublisher.confirmed`.
- AC3 (R10, finality): `pollFinality`/`pollFinalityFor` + `TxLifecyclePublisher.finalized`.
- AC5 (`watchId` as `aggregateId`): `TxLifecyclePublisher.publish` passes `watch.watchId().toString()`.
- AC6 (duplicate publish never propagates): `TxLifecyclePublisher.publish`'s `catch (DataIntegrityViolationException)`.
- AC7 (skip tick with <3 answers): `pollFinalityFor`'s `if (answers.size() != 3) return;`.
- AC8 (`lastFinalizedBlock` forward-only): `ChainCursor.advanceFinalizedTo`.
- AC9 (module boundaries): `WatchModuleBoundaryTest`'s extended allow-list (verified passing).

## Deviations from the plan, forced by reality

1. **Finality-poll's finalized-block-number sourcing.** The plan's method-signature sketch didn't specify exactly how `pollFinalityFor` would obtain the finalized block number to pass to `advanceFinalizedTo`. An initial draft called `getFinalityStatus` a *second* time per provider after the quorum decision to fetch it — recognized during writing as wasteful (doubles adapter calls) and a genuine correctness risk (the chain's head could advance between the first and second call, making the two poll rounds inconsistent). Fixed by retaining each provider's `FinalityStatus` from the single poll loop already run (a `Map<String, FinalityStatus>` alongside the boolean-answers map) and computing the majority `finalizedBlockNumber` from those already-in-hand values via the same `majorityValue` helper — no second network call.
2. **`TxLifecyclePublisher`'s duplicate-key catch scope.** The frozen brief's Constraints section asked that the catch "must not mask any other `DataIntegrityViolationException` cause beyond the idempotency-key conflict," implicitly suggesting a `TokenAllowlistSeeder`-style re-query-to-confirm pattern (verified as the established precedent for this exact concern). Implemented as a broader, undiscriminating catch instead, with the narrower reasoning documented directly in the class Javadoc: `idempotency_key` is `outbox`'s only unique constraint, every other column is null/blank-validated by `OutboxPublisher` itself before any database call, and `TxLifecyclePublisher` is the only caller ever constructing a key of this exact shape — so no other cause can plausibly reach this catch. A re-query implementation would have required either a new public method on `OutboxPublisher` (explicitly listed as "Files NOT to Modify" in the frozen brief) or direct `OutboxEventRepository` access (package-private, inaccessible from `watch/`). Flagged here for Phase 7/8 scrutiny rather than silently narrowed.
3. **`WatchModuleBoundaryTest`'s `events` allowance is package-wide, not file-scoped.** The test's own established style constrains imports per package-prefix, not per source file — so allow-listing `events.OutboxPublisher` for `finality`-adjacent reasons technically permits any file in `watch/` to import it, not just `TxLifecyclePublisher.java`. This matches the test's pre-existing looseness for every other allowance already in the map (e.g. any file could import `ProviderHealthTracker`) and required no new mechanism.

## Verification

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly. `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest` — 37/37 pass (all pre-existing, unmodified-in-scenario tests still green after the constructor-signature ripple). Full module regression (`mvn -pl services/crypto -am test`): 538 tests, 6 failures — the same pre-existing, disclosed, unrelated set (JSON-spacing assertion, three DB-permission-vs-Hibernate-exception-wrapping mismatches, one `TokenAllowlistRepositoryIntegrationTest` pre-existing gap). One transient regression was caught and fixed during this phase: `ChainBaselineMigrationIntegrationTest`'s hardcoded Flyway version list needed extending to include `"8"` — found via the full regression run, not assumed.
