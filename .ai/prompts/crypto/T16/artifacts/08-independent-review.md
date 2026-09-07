# crypto · T16 · Phase 8 — Independent Code Review Findings

Reviewed: `watch/Watcher.java`, `watch/WatcherRegistry.java`, `adapter/ProviderSet.java`,
`common/config/WatcherProperties.java`, `provider/ProviderHealthTracker.java`,
`adapter/eth/EthereumAdapter.java`, `adapter/tron/TronAdapter.java`, `adapter/ObservationSink.java`,
`watch/ChainCursor.java`, `watch/WatchRepository.java`, `watch/ChainCursorRepository.java`,
`db/migration/V7__crypto_app_watcher_grants.sql`, `artifacts/07-self-review.md`, and
`artifacts/04-frozen-task-brief.md`.

The architecture pivot to a correlation buffer is correctly implemented: `ObservationSink` carries
provider + raw JSON, `ProviderSet` lives in `adapter/`, the `chain.shedlock` grant is present, the
`exists=false` exclusion is honored, duplicate-decision exceptions are swallowed, and cursor advancement
is forward-only and ordered after observation/quorum/health writes. The findings below are resource leaks,
exception-propagation risks, and subtler correctness gaps in the new code.

---

### 1. `Watcher.stop()` leaks its sweep task and its Micrometer gauge

**Issue:** `Watcher.start()` schedules a recurring `sweepStaleCorrelations` task on the shared scheduler
but discards the returned `ScheduledFuture`. `Watcher.stop()` cancels the provider subscriptions but never
cancels the sweep future. The gauge registered in the constructor is also never removed from the registry.
Because `WatcherRegistry` calls `watcher.stop()` on every unregister, shard loss, or reassignment, every
stopped watch leaves behind a permanently-running sweep task and a stale gauge.

**Evidence:** `watch/Watcher.java:81-86` (gauge registration); `:93-107` (`start()`/`stop()`).

**Recommendation:** Capture the `ScheduledFuture` returned by `scheduleWithFixedDelay` and cancel it in
`stop()`. Capture the `Gauge`/`Meter.Id` from `Gauge.builder(...).register(...)` and call
`meterRegistry.remove(...)` in `stop()`. Add a test that starts and stops a `Watcher` and asserts the
scheduler has no remaining delayed tasks for that watcher and that no gauge with its tags remains.

**Confidence:** High.

---

### 2. `correlations` and `evaluatedFacts` are never pruned

**Issue:** Every observed transaction adds an entry to `correlations` and (transiently or permanently)
to `evaluatedFacts`. Once all four facts for a tx are decided — or once it is clear some facts can never
reach 3 real answers because one or more providers reported `exists=false` — the entries are never
removed. A long-lived watch observing many transactions will grow these maps without bound.

**Evidence:** `watch/Watcher.java:61-62`; `:136-151`; `:223-236`.

**Recommendation:** Prune a `txHash` from `correlations` and its four `evaluatedFacts` keys once all
configured providers have answered (i.e., `correlation.answers().size() == adapters.size()`), regardless
of whether every fact reached a quorum decision. At that point no new answers can ever arrive for that
transaction, so the correlation has no further purpose.

**Confidence:** High.

---

### 3. `recordDisagreementsIfAny` misidentifies "the majority" in a genuine 3-way split

**Issue:** For a `HELD` outcome where all three providers give distinct answers, the code uses
`counts.entrySet().stream().max(Map.Entry.comparingByValue())` to pick one arbitrary entry and then flags
the other two as disagreeing. With three counts of 1, the choice is arbitrary, producing a misleading,
asymmetric health signal for the scenario where accurate signaling matters most.

**Evidence:** `watch/Watcher.java:191-208`.

**Recommendation:** Only attribute disagreement when a genuine majority exists (`agreeingCount >= 2`, i.e.
an `AGREED` outcome). For a 3-way `HELD` split, either flag all three providers or none; the
`HeldFactAlerter` inside `QuorumDecisionService.evaluate` already alerts ops for every `HELD` outcome.

**Confidence:** High.

---

### 4. Exceptions thrown from `Watcher.handleObservation` can kill an Ethereum subscription forever

**Issue:** `Watcher.handleObservation` has no catch-all. Any runtime exception from `logObservation`
(e.g., S3 snapshot failure), `recordAnswerAndMaybeEvaluate` (e.g., unexpected `QuorumDecisionService`
exception), or `advanceCursorIfNeeded` (e.g., database connectivity) propagates back to the adapter's
`pollOnce` method. `TronAdapter.pollOnce` wraps `pollOnceUnguarded` in a try/catch (Phase 9 finding), but
`EthereumAdapter.pollOnce` does **not** catch unchecked exceptions. An unchecked exception there causes
`ScheduledExecutorService.scheduleWithFixedDelay` to cancel all future executions of that subscription
silently.

**Evidence:** `watch/Watcher.java:109-120`; `adapter/eth/EthereumAdapter.java:190-216`;
`adapter/tron/TronAdapter.java:234-244`.

**Recommendation:** Add a broad `catch (RuntimeException e)` around the body of `handleObservation` that
logs the error and returns without rethrowing. The watcher must not let its own internal failures destroy
the adapter's polling loop. Alternatively, add the same defensive catch to `EthereumAdapter.pollOnce` that
`TronAdapter` already has — but the watcher-level guard is the more robust fix because it protects against
future adapter implementations that may also lack such a catch.

**Confidence:** High.

---

### 5. Gauge `crypto.watcher.lag.seconds` can collide when two watches share chain + address

**Issue:** The gauge is registered with tags `chain` and `address` only. The frozen brief (and T15) allows
a retried `POST` to create a second, independent watch for the same invoice/address, so two active watches
can legitimately share the same `(chain, address)` pair. Micrometer will either throw a duplicate-meter
exception or return an ambiguous shared gauge when the second `Watcher` starts.

**Evidence:** `watch/Watcher.java:81-86`.

**Recommendation:** Include `watchId` as a tag on the gauge so each `Watcher` has a unique meter identity.
If high cardinality is a concern, instead ensure only one `Watcher` is ever started per `(chain, address)`
(e.g., have `WatcherRegistry` key active watchers by address rather than watchId) — but that is a larger
change; adding `watchId` is the minimal, safe fix.

**Confidence:** High.

---

### 6. `WatcherRegistry` has no graceful-shutdown path; its shared scheduler leaks non-daemon threads

**Issue:** `WatcherRegistry` creates a single `ScheduledExecutorService` (`sweepScheduler`) with a virtual-
thread factory and shares it across all `Watcher` instances. There is no `@PreDestroy` or `DisposableBean`
method to stop the running watchers or shut down this scheduler. The threads are non-daemon, so on
application context shutdown the JVM may hang until the OS kills the process.

**Evidence:** `watch/WatcherRegistry.java:62`; `:67-83`; no shutdown method exists.

**Recommendation:** Add a `@PreDestroy` method that calls `stopWatcher` for every entry in
`runningWatchers`, clears the map, and then calls `sweepScheduler.shutdown()` (with a short await
termination). Add an integration test verifying the scheduler terminates after context close.

**Confidence:** High.

---

### 7. Watcher silently deadlocks if the configured provider count is not exactly 3

**Issue:** `QuorumEvaluator` hard-requires exactly 3 answers. `Watcher.recordAnswerAndMaybeEvaluate` waits
until `correlation.answers().size() >= adapters.size()`, then builds `providerAnswers` and evaluates only
if `providerAnswers.size() == 3`. If `adapters.size()` is 2 or 4, the fact can never reach size 3 through
the normal correlation path, so quorum decisions are never created. `ProviderProperties` currently only
enforces `providerCount >= quorumThreshold` (2), not exactly 3.

**Evidence:** `watch/Watcher.java:142-176`; `common/config/ProviderProperties.java:27-43`;
`quorum/QuorumEvaluator.java:46-50`.

**Recommendation:** Either (a) update `ProviderProperties` validation to require exactly 3 providers per
chain (matching the 2-of-3 quorum design and the hard-coded `QuorumEvaluator`), failing startup otherwise,
or (b) have `Watcher` log a fatal error and refuse to start when `adapters.size() != 3`. Option (a) is
preferred because it fails fast at config-binding time.

**Confidence:** High.

---

### 8. `recordDisagreement` is invoked once per disagreeing fact, not once per disagreeing transaction

**Issue:** `recordDisagreementsIfAny` is called separately for each of the four fact types. If a provider's
answers for `AMOUNT`, `TOKEN`, and `CONFIRMATIONS` all diverge from the majority in a single transaction,
`ProviderHealthTracker.recordDisagreement` is called three times for that provider, incrementing the
non-persisted consecutive-disagreement counter by 3. With the default threshold of 3, a single
three-fact disagreement immediately degrades the provider, even though the intent of "repeatedly
disagreeing" is a pattern across multiple transactions or polls.

**Evidence:** `watch/Watcher.java:146-150`; `:191-208`; `provider/ProviderHealthTracker.java:102-135`.

**Recommendation:** Track disagreement per provider **per transaction** in `Watcher`, and call
`providerHealthTracker.recordDisagreement` at most once per provider per transaction — regardless of how
many facts diverged. This preserves the counter's meaning as "how many transactions has this provider
disagreed on" rather than "how many fact fields has this provider disagreed on."

**Confidence:** Medium.

---

### 9. Shared single-thread sweep scheduler can become a bottleneck

**Issue:** `WatcherRegistry` creates one `newScheduledThreadPool(1, Thread.ofVirtual().factory())` and
passes it to every `Watcher`. All `sweepStaleCorrelations` tasks across all active watches are therefore
serialized on a single virtual thread. If one watcher has accumulated a large number of stale correlations
(because of Finding 2), it can delay sweeps for every other watch.

**Evidence:** `watch/WatcherRegistry.java:62`; `watch/Watcher.java:93-102`.

**Recommendation:** Either (a) give each `Watcher` its own single-thread virtual scheduler, or (b) size the
shared pool by `Runtime.getRuntime().availableProcessors()` and assign watcher sweeps round-robin. Given
Finding 2 (unpruned correlations), option (a) is safer because it isolates a slow watcher from others.

**Confidence:** Low.

---

### 10. `sweepStaleCorrelations` repeatedly calls `recordUnhealthy` for already-unhealthy lagging providers

**Issue:** Once a provider has been marked unhealthy for `LAGGING`, every subsequent sweep of the same
open correlation calls `recordUnhealthy(..., LAGGING)` again. The method is a no-op for the state
transition after the first call, but it still executes repository lookup and logic on every sweep window
for every stale correlation.

**Evidence:** `watch/Watcher.java:223-235`; `provider/ProviderHealthTracker.java:90-100`.

**Recommendation:** Track which providers have already been marked lagging within a correlation (e.g., a
`Set<ProviderKey>` inside `TxCorrelation`) and skip the `recordUnhealthy` call on subsequent sweeps. This
reduces noise and unnecessary DB reads.

**Confidence:** Low.
