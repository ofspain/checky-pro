# crypto · T18 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T18 — Reorg detector |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the Phase 6 implementation and Phase 7 self-review. Findings only; no rewrites.

---

## 1. Fresh `getTx` responses used for reorg detection are never logged verbatim, violating L3

- **Issue:** `checkForReorg` calls `ChainAdapter.getTx(txHash)` for every configured provider and uses `result.exists()` to compute the reorg signal, but it never persists the raw response to the observation log. L3 requires every provider response used in a determination to be logged verbatim before the determination is made. This is the same gap T17 closed for `getFinalityStatus`.
- **Evidence:** `watch/Watcher.java:462-474` (`checkForReorg` loop has no `observationLog.record` call); `spec/crypto-service/agents.md` service-specific rule 3 (verbatim observation log before quorum decision); `watch/Watcher.java:507-521` (`pollFinalityFor`'s counter-example, which does log `FINALITY` responses). Matches Phase 7 Self-Review Finding 1.
- **Recommendation:** Call `observationLog.record(watch.chain(), txHash, provider, FactType.EXISTENCE, rawJson)` for each successful `getTx` response before using it in the majority computation. The `observations` table is append-only, so a second `EXISTENCE` row for the same `(chain, txHash, provider)` later in time is expected and defensible.
- **Confidence:** High

## 2. Cursor is invalidated and saved before the reorg event is published, so a publish failure loses the event forever

- **Issue:** `checkForReorg` mutates and saves the cursor (resetting `txHash` to `null`) before calling `reorgDetector.reorg(...)`. `ReorgDetector.reorg` only catches `DataIntegrityViolationException`; any other runtime exception (serialization failure, transient outbox write error) propagates. Because the cursor now has `txHash() == null`, the next scheduler tick short-circuits and never retries reorg detection for this transaction — `chain.tx.reorged` is permanently lost.
- **Evidence:** `watch/Watcher.java:497-501` (`invalidate`/`save` at lines 497-498, `reorgDetector.reorg` at lines 500-501). Matches Phase 7 Self-Review Finding 2.
- **Recommendation:** Reorder: call `reorgDetector.reorg(...)` first, then invalidate/save/remove-from-pendingFinality. If the publish succeeds and the subsequent save fails, the next tick will detect the same reorg, publish again (duplicate-key violation caught and swallowed), and retry the save — self-healing without event loss.
- **Confidence:** High

## 3. An uncaught exception in `checkForReorg` permanently kills both reorg-checking and finality-polling for the watch

- **Issue:** `pollFinality()` is the `Runnable` submitted to `sweepScheduler.scheduleWithFixedDelay`. An uncaught `RuntimeException` from any call inside it cancels all future executions of that scheduled task (the same class of bug T16 fixed for adapter polling loops). `checkForReorg`'s `chainCursorRepository.save(...)` and `reorgDetector.reorg(...)` are unguarded, and because `checkForReorg` now runs at the top of `pollFinality()`, a failure there silences finality-polling as well as reorg-checking.
- **Evidence:** `watch/Watcher.java:439-444` (`pollFinality()` body); `watch/Watcher.java:497-501` (unguarded save/reorg calls). The pre-existing T17 `pollFinalityFor` has the identical gap for its own save/finalized calls. Matches Phase 7 Self-Review Finding 3.
- **Recommendation:** Add a top-level `catch (RuntimeException e)` inside `pollFinality()` that logs the error and continues, mirroring `handleObservation`'s guard, so a failure in either half never silences the other.
- **Confidence:** Medium

## 4. Cross-thread race between `handleSeenIfAgreed` and `checkForReorg` on the same `ChainCursor` row

- **Issue:** `handleSeenIfAgreed` reads, mutates, and saves the watch's `ChainCursor` from an adapter observation-callback thread; `checkForReorg` does the same from the scheduler thread. Neither path holds a lock or uses optimistic locking. A new transaction being recorded as `seen` at the same moment a reorg invalidates the cursor can result in one thread's save silently overwriting the other's.
- **Evidence:** `watch/Watcher.java:322-334` (`handleSeenIfAgreed` read-modify-save); `watch/Watcher.java:454-498` (`checkForReorg` read-modify-save). Matches Phase 7 Self-Review Finding 4.
- **Recommendation:** Either (a) add a `@Version` optimistic-locking column to `ChainCursor` and retry on `OptimisticLockException`, or (b) move both mutations onto a single thread/queue. If neither is in scope, document the race as an accepted limitation.
- **Confidence:** Medium

## 5. `recordDisagreementsIfAny` can flag the dissenting minority provider multiple times if reorg detection retries

- **Issue:** `checkForReorg` reuses `recordDisagreementsIfAny` to flag the provider still reporting `exists=true`. For reorg checks, `correlations.get(txHash)` is always `null` (the correlation was pruned when the transaction was first seen), so the helper's one-shot-per-transaction guard is bypassed. If `checkForReorg` detects a reorg but fails before invalidating the cursor (e.g., DB save throws), the next tick will detect the same reorg and flag the same dissenting provider again.
- **Evidence:** `watch/Watcher.java:492-495` (reorg path calls `recordDisagreementsIfAny`); `watch/Watcher.java:379-385` (helper checks `correlation == null`, which is true here, so it records disagreement unconditionally).
- **Recommendation:** Track reorg-specific disagreement flagging in a per-`txHash` set inside `Watcher` (e.g., `Set<String> reorgDisagreementFlagged`), or skip disagreement tracking for reorg if it is not required by the brief.
- **Confidence:** Low

## 6. Synchronous, sequential `getTx` loop blocks the shared finality-poll scheduler

- **Issue:** `checkForReorg` calls `getTx` for all 3 providers in a plain sequential `for` loop on the same single-thread `sweepScheduler` that runs `pollFinalityFor`. Each slow or hanging adapter now delays not only reorg detection but also finality polling for this watch. The task already had this pattern for `getFinalityStatus`; adding 3 more `getTx` calls per tick increases the scheduler-blocking surface without mitigation.
- **Evidence:** `watch/Watcher.java:439-444` (`pollFinality` runs `checkForReorg` then the finality loop); `watch/Watcher.java:462-474` (sequential `getTx` loop).
- **Recommendation:** Document the trade-off explicitly, or dispatch each `getTx` call onto its own virtual thread and join before computing the majority (while staying within the no-new-thread-pool constraint by using `Thread.ofVirtual().start(...)`).
- **Confidence:** Medium

## 7. `checkForReorg` does not verify that the returned `TxResult.txHash()` matches the queried hash

- **Issue:** `checkForReorg` calls `adapter.getTx(txHash)` and immediately reads `result.exists()`. It never asserts that `result.txHash()` equals the queried `txHash`. A buggy adapter returning a different transaction's result could cause a false reorg signal (or false all-clear).
- **Evidence:** `watch/Watcher.java:465-466` (`TxResult result = namedAdapter.adapter().getTx(txHash); existsAnswers.put(..., result.exists());`).
- **Recommendation:** Add a defensive check and log a warning if the returned `txHash` does not match; treat that provider's answer as a transport failure for this tick (exclude it and mark `LAGGING`).
- **Confidence:** Low

## 8. `ChainCursor.invalidate` has no optimistic-locking guard, so the race in Finding 4 has no detection mechanism

- **Issue:** Even if the cross-thread race described in Finding 4 is accepted as low-probability, `ChainCursor` currently has no `@Version` column or other optimistic-locking mechanism. Two threads loading the same row, mutating it, and saving it would produce a last-write-wins anomaly with no exception or log.
- **Evidence:** `watch/ChainCursor.java:31-187` (no `@Version` field); `watch/Watcher.java:322-334` and `454-498` (concurrent mutators).
- **Recommendation:** Add a `@Version` column to `ChainCursor` and handle `OptimisticLockException` in both `handleSeenIfAgreed` and `checkForReorg` (e.g., reload and retry, or log and skip the tick). If this is out of scope, document it as a known limitation.
- **Confidence:** Medium

## 9. `ReorgDetector.reorg` does not null-check its arguments before constructing the idempotency key

- **Issue:** `ReorgDetector.reorg` concatenates `chain + ":" + txHash + ":reorged"` without validating that `chain` or `txHash` are non-null. A null `txHash` would produce an idempotency key of `"ETHEREUM:null:reorged"` and a `ReorgedPayload` with a null `txHash`. `OutboxPublisher`'s own validation might catch this, but the failure mode is clearer if `ReorgDetector` rejects nulls up front.
- **Evidence:** `reorg/ReorgDetector.java:55-59`.
- **Recommendation:** Add `Objects.requireNonNull` guards for `watchId`, `chain`, and `txHash` (and ideally `tokenContractAddress`) at the start of `reorg`, matching `ChainCursor.placeholder`'s defensive style.
- **Confidence:** Low

## 10. No explicit handling for the case where `getTx` returns `exists=false` for the queried txHash but the cursor's snapshot belongs to a different transaction

- **Issue:** T17 already handles the analogous mismatch for finality (`finalizedIsWithheldWhenTheCursorSnapshotBelongsToADifferentTransaction`). `checkForReorg` keys off `cursor.txHash()` and queries that exact hash, so this specific mismatch cannot occur through the normal call path. However, a race between a new `handleSeenIfAgreed` recording a *new* txHash and a stale `checkForReorg` that loaded the cursor before the update could, in principle, query the old hash while the cursor now holds the new one. The current code does not re-check `cursor.txHash()` after the `getTx` loop.
- **Evidence:** `watch/Watcher.java:454-501` (cursor is loaded once at the start; no re-check after the synchronous loop).
- **Recommendation:** After the `getTx` loop, re-verify that `cursor.txHash()` still equals the queried `txHash`; if not, abort the reorg handling for this tick. This is a cheap defense against the race in Finding 4.
- **Confidence:** Low

---

(End of independent review. Findings are for human fold-in during Phase 9.)
