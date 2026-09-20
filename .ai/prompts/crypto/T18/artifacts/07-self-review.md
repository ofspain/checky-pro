# crypto · T18 · Phase 7 — Self-Review

Findings only, ranked most severe first. No fixes applied here (Phase 9).

## 1. `checkForReorg`'s `getTx` responses are never logged verbatim, violating L3

- **Issue:** L3 ("Observation log is verbatim and written first... so any past attestation can be re-derived and defended") is a LOCKED decision this codebase has honored for every other provider response used to make a determination — `logObservation` for `EXISTENCE`/`AMOUNT`/`TOKEN`/`CONFIRMATIONS` (T16) and `pollFinalityFor`'s own explicit `observationLog.record(...)` call for `FINALITY` (T17, added specifically to close an identical gap Kimi found in that task). `checkForReorg` extracts and acts on each provider's fresh `TxResult.exists()` answer without ever calling `ObservationLog.record` for it — the one raw response this task's entire detection logic depends on is never durably captured.
- **Severity:** High
- **Evidence:** `watch/Watcher.java:462-474` (`checkForReorg`'s per-provider loop — no `observationLog.record` call anywhere in it, unlike the otherwise-parallel loop in `pollFinalityFor` at `watch/Watcher.java:507-521`).
- **Recommendation:** Log each provider's raw `TxResult` via `observationLog.record(watch.chain(), txHash, provider, FactType.EXISTENCE, rawJson)` before using it to compute the majority — `observations` is append-only (T08), so a second `EXISTENCE` row for the same `(chain, txHash, provider)` later in time is architecturally normal, not a conflict.

## 2. Publishing the reorg event after invalidating the cursor means a publish failure permanently loses the event

- **Issue:** `checkForReorg` invalidates and saves the cursor *before* calling `reorgDetector.reorg(...)`. `ReorgDetector.reorg` only catches `DataIntegrityViolationException` (the expected duplicate-key case); any other exception from it (serialization failure, a transient outbox-write error) propagates uncaught. Since `checkForReorg`'s own early-return guard is `cursor.txHash() == null`, and the cursor has already been invalidated by the time the publish is attempted, the very next tick will see `txHash() == null` and skip re-checking entirely — there is no retry path, and `chain.tx.reorged` is silently lost forever for that transaction.
- **Severity:** High
- **Evidence:** `watch/Watcher.java:497-501` (`cursor.invalidate`/`save` run before `reorgDetector.reorg`).
- **Recommendation:** Reorder: call `reorgDetector.reorg(...)` first, then invalidate and save the cursor. If the publish fails, the cursor is untouched and the very next tick naturally retries — and if the publish actually *did* succeed but the retry's second attempt reaches `OutboxPublisher` again, the deterministic idempotency key's unique-constraint violation is already-handled, benign duplicate suppression (the same self-healing property `TxLifecyclePublisher`'s equivalent ordering choices rely on elsewhere).

## 3. `checkForReorg`'s unguarded `save`/`reorg` calls can permanently kill the scheduled poll task

- **Issue:** `pollFinality()` is the `Runnable` given to `sweepScheduler.scheduleWithFixedDelay`; an uncaught `RuntimeException` from any call inside it permanently cancels all future executions of that scheduled task (the same class of bug T16 already fixed for `EthereumAdapter.pollOnce`/`TronAdapter`'s own polling loops). `checkForReorg`'s `chainCursorRepository.save(cursor)` and `reorgDetector.reorg(...)` calls are both unguarded — a transient DB or serialization failure from either would now kill *both* reorg-checking and finality-polling for this watch forever, not just the finality half as before this task.
- **Severity:** Medium (pre-existing pattern — `pollFinalityFor`'s own `save`/`finalized` calls have the identical gap already — but this task both adds new unguarded call sites and worsens the blast radius, since a single uncaught exception now takes down two concerns instead of one).
- **Evidence:** `watch/Watcher.java:497-501`; the pre-existing, identically-unguarded `chainCursorRepository.save(cursor)`/`txLifecyclePublisher.finalized(...)` calls at `watch/Watcher.java:552-556` area (T17).
- **Recommendation:** Not necessarily this task's sole responsibility to fix (the pattern predates it), but worth deciding at Phase 9 whether `pollFinality()`'s own top-level body should get a single outer `catch (RuntimeException)` (mirroring `handleObservation`'s own established guard) so a failure in either half never silences the other.

## 4. Cross-thread race between `handleSeenIfAgreed` and `checkForReorg` on the same `ChainCursor` row

- **Issue:** `handleSeenIfAgreed` (T17) reads, mutates, and saves the watch's `ChainCursor` from the adapter's own observation-callback thread; `checkForReorg` (T18) does the same from the scheduler thread. Neither path holds any lock or uses an optimistic-locking column. If a reorg invalidates the cursor's current transaction at the same moment a *new* transaction is being recorded as seen, both threads load independent copies of the same row, mutate them differently, and whichever `save()` runs last silently overwrites the other's write.
- **Severity:** Medium (the window is narrow — `ChainCursor` only ever tracks one transaction at a time, T17's own write-once design — but the race is real and unaddressed).
- **Evidence:** `watch/Watcher.java:454-460` (`checkForReorg`'s own read); the analogous read-modify-write in `handleSeenIfAgreed` (T17, unchanged by this task).
- **Recommendation:** Out of this task's likely proportionate scope to fully fix (would need optimistic locking or moving both mutations onto the same thread/queue) — at minimum, disclose it explicitly as a known, accepted limitation if Phase 9 doesn't address it.
