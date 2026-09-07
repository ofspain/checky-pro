# crypto · T16 · Phase 11 — Test Review Findings

Reviewed: `watch/WatcherTest.java`, `adapter/ProviderSetTest.java`,
`watch/WatcherRegistryTest.java`, `artifacts/10-test-generation.md`, and the frozen brief's Required
Tests.

The test suite covers all ACs and every Phase 7/8 finding. 24/24 pass. The gaps below are strengthening
opportunities rather than missing AC coverage.

---

### 1. `Watcher.stop()` cancellation of the sweep future is not directly asserted

**Gap:** `removesItsOwnLagGaugeOnStop` verifies the gauge is deregistered, but no test verifies that the
sweep task itself is actually cancelled or that the private scheduler is shut down. A regression that
removed the `sweepFuture.cancel(false)` or `sweepScheduler.shutdownNow()` calls would still pass the
current tests.

**Why it matters:** Phase 7/8 Finding 1 identified the sweep-task leak as a real, unbounded resource leak
for any long-lived deployment. The fix is in production code but not locked in by an executable test.

**Suggested test:** Add a test that starts a `Watcher`, stops it, and asserts that `sweepScheduler` has no
remaining pending/scheduled tasks and that invoking `sweepStaleCorrelations()` after `stop()` does not
re-queue anything. Since the scheduler is private, this can be done indirectly by providing a
`ScheduledExecutorService` spy to the `Watcher` constructor (or by inspecting the registry's internal
state if the test is co-located).

---

### 2. No test for the generic exception catch-all in `Watcher.handleObservation`

**Gap:** `swallowsADuplicateDecisionExceptionRatherThanPropagatingIt` tests the specific
`IllegalStateException` path from `QuorumDecisionService.evaluate`, but the broader `catch
(RuntimeException e)` added to protect the adapter's polling loop from failures in `logObservation` or
`advanceCursorIfNeeded` is not exercised.

**Why it matters:** Phase 8 Finding 4 identified that an exception from watcher internals could propagate
back to `EthereumAdapter.pollOnce` and permanently cancel the provider's subscription. The fix is in
production code but only the duplicate-decision branch is tested.

**Suggested test:** Add a test where `observationLog.record` throws an arbitrary `RuntimeException` (e.g.,
`IllegalStateException("S3 failed")`) on the first provider callback, then deliver the remaining two
answers and assert that (a) no exception escapes `deliver`, (b) the other two observations are still
processed, and (c) quorum evaluation still fires once all three answers are in.

---

### 3. `WatcherRegistry.shutdown()` does not have a test that asserts all watchers are stopped

**Gap:** `shutdownReleasesTheShardLockSoAnotherReplicaCanImmediatelyTakeOver` verifies lock release and
indirectly proves shutdown by showing another replica can acquire the lock. It uses a mocked `ProviderSet`
that returns no adapters, so no real `Watcher` is ever constructed or started. The test therefore does not
verify that `shutdown()` calls `watcher.stop()` for every running watcher.

**Why it matters:** A regression that removed the `stopWatcher` loop from `shutdown()` but kept the lock
release would still pass the existing test, yet would leave subscriptions and sweep schedulers running
after context shutdown.

**Suggested test:** Add a test with a real `ProviderSet` (or a spy that returns at least one
`FakeChainAdapter`) and one `REGISTERED` watch. Start the registry, verify a `Watcher` is created, then call
`shutdown()` and assert the watcher's subscriptions are cancelled and its gauge is removed (using the
same meter-registry assertions `WatcherTest` already uses).

---

### 4. No test for lock-loss / renewal-failure path

**Gap:** `onlyOneOfTwoReplicasStartsAWatcherForTheSameRegisteredWatch` tests initial acquisition, but no
test simulates the case where a replica that already owns a shard loses the lock (renewal fails) and must
stop its watchers.

**Why it matters:** AC9 requires that a lost lease is picked up by another replica, which implies the
losing replica must stop its watchers. `ownsShard` implements this, but it is not exercised.

**Suggested test:** Use a mocked `LockProvider` whose `extend()` returns `Optional.empty()` on the second
call. Reconcile twice with the same registry and assert that the previously-started watcher is stopped and
`runningWatchers` is empty after the second reconcile.

---

### 5. All `WatcherRegistryTest` cases use `shardCount = 1`

**Gap:** Every registry test uses a single shard, so the shard-assignment function
(`Math.floorMod(watchId.hashCode(), shardCount)`) and the multi-shard stop/start logic are not tested.

**Why it matters:** AC9's guarantee is about arbitrary sharding. A bug in `stopWatchersNoLongerRegistered`
or `stopAllWatchersInShard` that only manifests when a shard owns only a subset of watches would not be
caught.

**Suggested test:** Add a test with `shardCount = 2` and two `REGISTERED` watches whose `watchId` hash codes
place them in different shards. Reconcile, assert that only the watches in the acquired shard(s) start a
`Watcher`, and that the other shard's watch is not started.

---

### 6. No test exercises the new `chain_cursors` `UPDATE` grant for `crypto_app`

**Gap:** `V7__crypto_app_watcher_grants.sql` adds `UPDATE` on `chain.chain_cursors` to `crypto_app`, but no
test in the manifest verifies it. `WatcherTest` mocks `ChainCursorRepository`; `WatcherRegistryTest` also
mocks it. The `WatchRepositoryIntegrationTest` from T15 only asserted `INSERT, SELECT` on
`chain_cursors`.

**Why it matters:** AC6 depends on the watcher being able to advance `lastBlock` as the `crypto_app` role.
A missing or mis-scoped grant would only surface in production or a full integration test, not in the
unit/registry suites.

**Suggested test:** Extend `WatchRepositoryIntegrationTest` (or add a dedicated grant test) to connect as
`crypto_app` and assert that `UPDATE chain.chain_cursors SET last_block = ... WHERE watch_id = ...`
succeeds and that `DELETE FROM chain.chain_cursors` still fails with "permission denied".

---

### 7. No test for cursor non-regression

**Gap:** `advancesTheCursorAfterObservationAndQuorumWritesComplete` asserts that `lastBlock` moves forward
from the `-1` sentinel to `555L`, but it does not assert that a subsequent observation with a lower block
number does not regress the cursor.

**Why it matters:** AC6 explicitly requires `ChainCursor.lastBlock` to only ever increase. The `advanceTo`
implementation guards this, but a regression in `Watcher.advanceCursorIfNeeded` (e.g., passing a wrong
block number or bypassing the guard) would not be caught.

**Suggested test:** Seed a cursor with `lastBlock = 1000L`, deliver an observation with `blockNumber =
500L`, and assert `lastBlock` remains `1000L`.

---

### 8. No test for a `REGISTERED` → `UNREGISTERED` transition stopping an existing watcher

**Gap:** `onlyRegisteredWatchesAreEverAssignedToAShard` tests the case where no watches are registered at
all. There is no test for the transition where a watch that was `REGISTERED` (and running) becomes
`UNREGISTERED` between reconciliation ticks.

**Why it matters:** `WatcherRegistry.stopWatchersNoLongerRegistered` is the production code path that
handles unregister; it is not directly exercised.

**Suggested test:** Reconcile once with a `REGISTERED` watch (assert watcher starts), then reconcile again
with `watchRepository.findByStatus(REGISTERED)` returning an empty list and assert the watcher is stopped
and removed from `runningWatchers`.

---

### 9. No test for duplicate observation delivery from the same provider

**Gap:** A provider's polling loop can deliver the same transaction more than once (e.g., overlapping
ranges or re-emission). The watcher overwrites the correlation entry, guards re-evaluation with
`evaluatedFacts`, and logs a duplicate observation, but none of this is tested.

**Why it matters:** This is a realistic edge case for polling-based subscriptions. A bug that accidentally
triggered a second `QuorumDecisionService.evaluate` call would throw, and the catch-all added for Finding
4 would hide a real correctness problem.

**Suggested test:** Deliver the same transaction from `providerA` twice, then deliver it once each from
`providerB` and `providerC`. Assert that `quorumDecisionService.evaluate` is called exactly once per fact
and that `observationLog.record` is called twice for `EXISTENCE` (or four times total if `exists=true`).
