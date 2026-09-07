# crypto · T16 · Phase 7 — Self-Review

Reviewed the diff (Phase 6) against the frozen brief and `agents.md`: correctness, boundary conditions,
null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types,
enumeration-safety/secret-handling, readability, complexity. Findings only — no fixes applied here.

---

### 1. `Watcher.stop()` does not cancel its own sweep task or deregister its Micrometer gauge - both
leak for the life of the JVM, not just the life of the watch

- **Severity:** High
- **Evidence:** `watch/Watcher.java:93-106` (`start()`/`stop()`) - `start()` calls
  `sweepScheduler.scheduleWithFixedDelay(this::sweepStaleCorrelations, ...)` at line 100 but never
  captures the returned `ScheduledFuture`; `stop()` (line 104) only cancels `subscriptions`, never that
  future. `watch/Watcher.java:81-86` - the `Gauge.builder(...).register(meterRegistry)` call in the
  constructor is likewise never paired with a `meterRegistry.remove(...)` anywhere.
- **Recommendation:** Capture the `ScheduledFuture` from `scheduleWithFixedDelay` and call
  `future.cancel(false)` in `stop()`, mirroring `EthereumAdapter`/`TronAdapter`'s own established
  `Subscription.cancel()` pattern for exactly this kind of handle. Capture the `Gauge` (or its `Meter.Id`)
  returned by `.register(...)` and call `meterRegistry.remove(...)` in `stop()`. Without both fixes, every
  watch that is ever unregistered (or reassigned across shards by `WatcherRegistry`) leaves a permanently
  running sweep task and a stale, unremovable gauge behind - a real, unbounded resource leak for any
  long-lived deployment, not a theoretical one (`WatcherRegistry.stopWatchersNoLongerRegistered`/
  `stopAllWatchersInShard` will call `stop()` routinely as watches expire or shards rebalance).

---

### 2. `correlations` and `evaluatedFacts` are never pruned - unbounded memory growth over a watch's
lifetime

- **Severity:** Medium
- **Evidence:** `watch/Watcher.java:61-62` (the two maps); no removal from `correlations` appears
  anywhere except the already-guarded re-check at `:174`, and `evaluatedFacts` entries are only ever
  added or (on the "not yet 3 answers" path) removed, never cleared once a fact is genuinely decided.
- **Recommendation:** Once all four facts for a `txHash` have been decided (or once `sweepStaleCorrelations`
  has already flagged every non-responder as lagging and no further resolution is expected soon), remove
  that `txHash`'s entry from `correlations` and its four `evaluatedFacts` keys. A watch that observes many
  transactions over its lifetime (which, per the frozen brief, can be long - a watch is only stopped on
  unregister/expiry) will otherwise accumulate these entries without bound.

---

### 3. `recordDisagreementsIfAny`'s majority-detection is incorrect for a genuine 3-way split (HELD,
`agreeingCount == 1`)

- **Severity:** Medium
- **Evidence:** `watch/Watcher.java:191-208` - `counts.entrySet().stream().max(Map.Entry.comparingByValue())`
  picks *some* entry with the highest count; with three distinct provider answers (each count 1), this
  picks one arbitrarily and treats it as "the majority," then flags the other two as disagreeing (line
  204). But with no true majority, none of the three answers is more "correct" than another - all three
  mutually disagree.
- **Recommendation:** Only attribute disagreement when a genuine majority exists (i.e. `agreeingCount >=
  2`, which `QuorumEvaluator`'s own 2-of-3 semantics guarantee whenever the outcome is `AGREED`). For a
  `HELD` three-way split, either flag all three providers as disagreeing, or don't attempt to single out
  "the disagreeing one(s)" at all and rely on `HeldFactAlerter`'s own ops-alert path (already triggered
  inside `QuorumDecisionService.evaluate` for every `HELD` outcome) as the sole signal for that case. As
  written, the current logic produces a misleading, arbitrarily-asymmetric health signal for exactly the
  scenario (a genuine 3-way disagreement) where accurate signaling matters most.

---

No correctness, null-safety, transaction-boundary, module-boundary, money-type, or secret-handling
defects found beyond the three items above. The exactly-3-answers design (AC1), the log-before-decide
ordering (AC2), the `exists=false` exclusion (AC4), the duplicate-decision catch (AC5), and the
forward-only cursor advance (AC6) are all correctly implemented and match the frozen brief. The ShedLock
lease-acquire-and-renew logic in `WatcherRegistry` is race-safe as designed (verified against the real
library behavior in Phase 6, not assumed).
