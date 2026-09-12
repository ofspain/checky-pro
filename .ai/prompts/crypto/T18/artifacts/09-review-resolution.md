# crypto · T18 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-12, all findings as recommended. Self-review (Phase 7, 4
findings) and Kimi's independent review (Phase 8, 10 findings) are consolidated below — Kimi's Findings
1-4 each independently confirmed a self-review finding.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review #1 / Kimi #1 — `checkForReorg`'s `getTx` responses are never logged verbatim, violating L3 | **ACCEPTED** | `watch/Watcher.java`: each provider's `TxResult` is now logged via `observationLog.record(..., FactType.EXISTENCE, toRawJson(result))` before being used in the majority computation. New `toRawJson(TxResult)` helper added, mirroring the adapters' own raw-capture shape. |
| 2 | Self-review #2 / Kimi #2 — cursor invalidated/saved *before* the reorg event is published, so a publish failure loses the event forever | **ACCEPTED** | `watch/Watcher.java`: reordered — `reorgDetector.reorg(...)` is now called first; the cursor is invalidated and saved only after. A publish failure now leaves the cursor untouched, so the next tick naturally retries; a retried-but-already-successful publish is a benign, swallowed duplicate (deterministic idempotency key). |
| 3 | Self-review #3 / Kimi #3 — an uncaught exception in `checkForReorg` permanently kills both reorg-checking and finality-polling | **ACCEPTED** | `watch/Watcher.java`: `pollFinality()` now wraps the `checkForReorg()` call and each `pollFinalityFor(txHash)` call in its own `try/catch (RuntimeException)`, logging and continuing — mirrors `handleObservation`'s own established guard. A failure in one half no longer silences the other or permanently cancels the scheduled task. |
| 4 | Self-review #4 / Kimi #4 — cross-thread race between `handleSeenIfAgreed` and `checkForReorg` on the same `ChainCursor` row | **ACCEPTED, partially mitigated, remainder documented** | `watch/Watcher.java`: `checkForReorg` now re-reads the cursor immediately before acting and aborts if `txHash` no longer matches what it started with (narrows the race window significantly). The underlying race itself is not fully closed — that would need a `@Version` optimistic-locking column (a schema change), disclosed in `checkForReorg`'s own Javadoc as an accepted, out-of-scope limitation for this review-resolution phase. |
| 8 | Kimi #8 (new) — no optimistic-locking guard on `ChainCursor` to even detect the race in Finding 4 | **ACCEPTED, documented only, folded into #4** | Same disposition and same Javadoc disclosure as #4 — a `@Version` column is a schema change outside this phase's scope. |
| 5 | Kimi #5 (new) — `recordDisagreementsIfAny` can flag the same dissenting provider more than once if reorg detection retries before the cursor is successfully invalidated | **ACCEPTED, documented only** | No code change — low real-world frequency (requires a persistence failure between detecting and invalidating), affects only a health-tracking counter, same category as T17's own already-accepted analogous limitation. Disclosed directly in `checkForReorg`'s Javadoc. |
| 6 | Kimi #6 (new) — sequential `getTx` calls block the shared scheduler thread, same as `pollFinalityFor`'s `getFinalityStatus` calls | **ACCEPTED, documented only** | No code change — identical disposition to T17 Phase 9 Finding #9's own already-settled decision (reusing one thread was the frozen brief's explicit choice; parallelizing would be exactly the "optimization" this phase's rules exclude). Disclosed directly in `checkForReorg`'s Javadoc. |
| 7 | Kimi #7 (new) — `checkForReorg` never verifies `TxResult.txHash()` matches the queried hash | **ACCEPTED, documented only, no code change** | Rejected as a *code* change on consistency grounds: `pollFinalityFor`'s identical `getFinalityStatus` call (T17) places the same trust in its response without cross-checking, so adding the check only here would introduce an asymmetry between sibling methods. Documented instead as a deliberate, consistent trust boundary in `checkForReorg`'s own Javadoc. |
| 9 | Kimi #9 (new) — `ReorgDetector.reorg` does not null-check its arguments | **ACCEPTED** | `reorg/ReorgDetector.java`: added `Objects.requireNonNull` guards for all five parameters, matching `ChainCursor.placeholder`'s established defensive style. |
| 10 | Kimi #10 (new) — no re-check that the cursor still points at the queried transaction after the `getTx` loop, given the race in Finding 4 | **ACCEPTED** | Implemented together with Finding #4's fix: `checkForReorg` re-reads the cursor and aborts on a `txHash` mismatch immediately before recording disagreement/publishing/invalidating. |

## Summary

14 distinct findings after merging duplicates (4 self-review + 10 independent, 4 overlapping pairs plus
6 genuinely new from Kimi, one of which — #8 — folded into #4's identical disposition). 5 fixed in code
(#1, #2, #3, #4/#10 combined, #9); 4 accepted as documented, disclosed limitations with no code change
(#4's residual race /#8, #5, #6, #7); no findings rejected outright this round — every finding was either
fixed or accepted with an explicit, verified justification for leaving it as a documented limitation.

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly. `mvn -pl services/crypto test
-Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest,ReorgModuleBoundaryTest`
— 78/78 pass. Full module regression (`mvn -pl services/crypto -am test`): same 4 pre-existing failing
files, same pre-existing failure count. Zero regressions.

Files changed in this phase: `watch/Watcher.java` (`pollFinality`'s per-call exception guards,
`checkForReorg`'s verbatim logging + publish-before-invalidate reordering + fresh-cursor re-check, new
`toRawJson(TxResult)` helper), `reorg/ReorgDetector.java` (`reorg`'s null-checks). No public API outside
this task's own new-this-task types changed; no class renamed; no refactor beyond what each accepted
finding required.
