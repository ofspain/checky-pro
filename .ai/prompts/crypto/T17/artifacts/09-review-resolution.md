# crypto · T17 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-12, all findings as recommended. Self-review (Phase 7, 8
findings) and Kimi's independent review (Phase 8, 14 findings) are consolidated below — Kimi's Findings
4/5/7/8/9/10/12/13 each independently confirmed a self-review finding.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Self-review #1 / Kimi #5 — `handleSeenIfAgreed` sources `amount`/`fromAddress`/`toAddress` from an arbitrary agreeing provider, not the quorum-majority value | **ACCEPTED** | `watch/Watcher.java`: `handleSeenIfAgreed` now computes `agreedAmount` via `majorityValue` over the `exists=true` answers' amounts, and picks the representative provider as whichever one actually supplied that majority amount — no longer an arbitrary `findFirst()`. |
| 2 | Self-review #2 / Kimi #4 — a chain with no configured `FinalityPolicy` fails silently forever, disguised as provider lagging | **ACCEPTED** | `watch/Watcher.java`: constructor now throws `IllegalStateException` immediately if no policy resolves for the watch's chain, mirroring `ProviderSet`'s own eager validation (T16). Ripple: `WatcherRegistryTest`'s fixtures (all ETHEREUM) now supply a mocked `FinalityPolicy` instead of an empty list. |
| 3 | Kimi #1 (new) — `pollFinalityFor` publishes `finalized` using `cursor.txHash()` rather than the `txHash` actually being polled; for a watch that ever sees a second distinct transaction, this would emit `chain.tx.finalized` citing the wrong transaction | **ACCEPTED** | `watch/Watcher.java`'s finality-agreed branch now verifies `txHash.equals(cursor.txHash())` before publishing; on mismatch it logs a warning and withholds the event rather than emitting a misleading one. The underlying write-once-snapshot limitation for multi-tx watches remains disclosed, unfixed (out of this task's proportionate scope, per the frozen brief) — this only prevents it from producing an actively wrong event. |
| 4 | Self-review #3 / Kimi #7 — a missing `ChainCursor` row silently drops the snapshot and strands finality forever, no log | **ACCEPTED** | `watch/Watcher.java`: both `handleSeenIfAgreed` and the finality-agreed branch now `log.warn` when `findByWatchId` returns empty. `handleSeenIfAgreed` additionally skips adding the `txHash` to `pendingFinality` in that case (polling a transaction whose snapshot can never be written back is pointless). |
| 5 | Self-review #4 / Kimi #8 — `pendingFinality.add` happens before `chain.tx.seen` is published, risking `finalized`'s outbox row preceding `seen`'s under adversarial scheduling | **ACCEPTED** | `watch/Watcher.java`: `txLifecyclePublisher.seen(...)` is now called before `pendingFinality.add(txHash)`. |
| 6 | Kimi #6 (new) — `SeenPayload`/`FinalizedPayload` omit `confirmations`, contradicting the frozen brief's own field-sourcing map | **ACCEPTED for `seen`, REVISED for `finalized`** | `TxLifecyclePublisher.seen` gained a `confirmations` parameter (majority count among `exists=true` answers, computed at the `handleSeenIfAgreed` call site) and `SeenPayload` now carries it. `FinalizedPayload` still omits it: no durable source exists for it by finality time (`QuorumDecision` never stores the agreed value, and adding a new `chain_cursors` column for an optional field is outside this review-resolution phase's proportionate scope) — documented directly in `TxLifecyclePublisher.finalized`'s Javadoc rather than left unexplained. |
| 7 | Self-review #5 / Kimi #9 — finality polling shares `Watcher`'s single-thread scheduler and polls providers sequentially, so a slow provider delays the correlation sweep too | **ACCEPTED, documented only** | No code change — reusing one thread was the frozen brief's own explicit choice ("no new thread pool"), and dispatching per-provider calls onto virtual threads would be the kind of optimization this phase's own rules ("do not optimize") exclude. Recorded here as a known, accepted characteristic for a future task to revisit if finality-poll latency becomes operationally significant. |
| 8 | Kimi #3 (new) — `recordDisagreementsIfAny`'s one-shot-per-provider guard is vacuous for `FINALITY` (`correlations.get(txHash)` is always `null` by the time finality is polled, since the original correlation was pruned long before), so a provider that already disagreed on an earlier fact of the same transaction can be double-counted if it also disagrees on `FINALITY` | **ACCEPTED, documented only** | No code change. Corrected from Kimi's own framing during triage: `recordDisagreementsIfAny` cannot fire repeatedly for `FINALITY` (`QuorumDecisionService`'s one-decision-ever guarantee means `evaluate` succeeds at most once, so the method runs at most once for this fact) — the real, narrower defect is a possible one-time double-count across two *different* facts of the same transaction, not a per-tick repeat. This affects only a health-tracking counter (not money-correctness or event content) and requires the same provider to disagree twice on the same transaction across two separate points in time — low real-world frequency. Fixing it would require extending `pendingFinality` into a stateful per-`txHash` structure purely to track this, which Phase 9's own "do not refactor" instruction weighs against for a narrow, low-severity gap. |
| 9 | Kimi #2 (new) — `pendingFinality` is in-memory only; a restart between `SEEN` and `FINALIZED` permanently strands that transaction's finality polling, and re-delivery cannot repopulate it (`EXISTENCE` re-evaluation throws the duplicate-decision exception, so `handleSeenIfAgreed` is a no-op) | **ACCEPTED, documented only** | No code change — durable persistence of pending-finality state would need new schema (a `finalized_at IS NULL AND tx_hash IS NOT NULL`-style reconciliation query plus startup wiring), a genuine scope expansion beyond this task's frozen brief, not a review-resolution-phase fix. Matches this pipeline's own precedent (T16 Phase 9 Finding 2's disclosed, unfixed correlation-TTL gap) of disclosing a real reliability limitation rather than silently expanding scope to close it. |
| 10 | Self-review #6 / Kimi #13 — `toRawJson(FinalityStatus)` omits `txHash`, unlike the adapters' own verbatim-capture convention | **ACCEPTED** | `watch/Watcher.java`: `toRawJson` now takes the `txHash` as a parameter and includes it as the first serialized field. |
| 11 | Self-review #7 / Kimi #12 — `FinalizedPayload.amount` can be `null` despite the schema marking it required | **ACCEPTED, documented only** | No code change — trusts the existing `TxResult` contract (exists=true implies meaningful fields), the same trust boundary already established at every other `Watcher`/adapter call site in this codebase. |
| 12 | Self-review #8 / Kimi #10 — the duplicate-outbox-key catch is broader than the frozen brief's "must not mask any other cause" wording | **REJECTED — no change** | Already justified in the Phase 6 implementation notes: `idempotency_key` is `outbox`'s only unique constraint, every other column is validated before any database call, and `TxLifecyclePublisher` is the sole caller ever constructing a key of this shape. Neither review offered a reason to revisit that reasoning. |
| 13 | Kimi #11 (new) — "required T17 unit tests are missing" | **REJECTED — factually incorrect and not a defect** | Verified directly: the Phase 6 implementation notes never claimed `TxLifecyclePublisherTest` was created (Kimi's premise is wrong). More fundamentally, writing new tests is explicitly Phase 10's job, not Phase 6's ("Do NOT write tests here" is Phase 6's own rule) — their absence at this point in the pipeline is by design, not a gap. |
| 14 | Kimi #14 (new) — `ChainCursor.advanceFinalizedTo` is forward-only but not write-once, diverging from the frozen brief's "set only once" framing | **ACCEPTED, documented only** | No code change — intentionally mirrors `advanceTo`'s own established forward-only-not-write-once shape (per the frozen brief's own instruction to mirror it "exactly"). In practice it is called at most once anyway, since the underlying `FINALITY` quorum decision is itself one-shot. |

## Summary

14 distinct findings after merging duplicates (8 self-review + 14 independent, 8 overlapping pairs plus
6 genuinely new from Kimi). 6 fixed in code (#1, #2, #3, #4, #5, #6-partial, #10 — seven code changes
across six numbered rows, one row split seen/finalized); 6 accepted as documented, disclosed limitations
with no code change (#7, #8, #9, #11, #14, and #6's finalized-side); 2 rejected (#12 no change needed,
#13 factually incorrect premise).

One finding (#8, the `recordDisagreementsIfAny`/`FINALITY` interaction) was accepted with a **corrected**
understanding during triage: Kimi's own framing ("re-flagged on every poll tick") was verified against
`QuorumDecisionService`'s one-decision-ever guarantee and found inaccurate — the real, narrower defect is
a possible one-time double-count across two different facts of the same transaction, not a per-tick
repeat.

`mvn -pl services/crypto compile` and `test-compile` succeed cleanly. `mvn -pl services/crypto test
-Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest` — 37/37
pass. Full module regression (`mvn -pl services/crypto -am test`): 538 tests, 6 failures — the same
pre-existing, disclosed, unrelated set. Zero regressions.

Files changed in this phase: `watch/Watcher.java` (constructor validation, `handleSeenIfAgreed`
rewritten, finality-agreed branch's cursor/mismatch handling, `toRawJson` signature), `watch/
TxLifecyclePublisher.java` (`seen`'s new `confirmations` parameter, `SeenPayload`'s new field,
`finalized`'s Javadoc), `watch/WatcherRegistryTest.java` (ripple: `FinalityPolicy` fixture for the new
fail-fast validation). No public API outside `watch/`'s own new-this-task types changed; no class
renamed; no refactor beyond what each accepted finding required.
