# crypto · T17 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R8 — first quorum-agreed sighting emits `chain.tx.seen` | Yes | `Watcher.handleSeenIfAgreed` `watch/Watcher.java:297-341`; `TxLifecyclePublisher.seen` `watch/TxLifecyclePublisher.java:63-69` | `WatcherTest.shouldEmitChainTxSeenOnQuorumAgreedFirstSighting` + 6 supporting tests (HELD/AGREED-false/majority-recomputation/redelivery/missing-cursor) | No | None. |
| R9 — confirmations gained emits `chain.tx.confirmed` with the count | Yes, one-shot (frozen brief) | `Watcher.handleConfirmedIfAgreed` `watch/Watcher.java:346-356`; `TxLifecyclePublisher.confirmed` `watch/TxLifecyclePublisher.java:73-79` | `WatcherTest.shouldEmitChainTxConfirmedWithConfirmationCount` + 4 supporting tests (HELD/EXISTENCE-minority-false/majority-count/redelivery) | No | **Disclosed at Phase 4**: emits once, at `CONFIRMATIONS`'s single existing quorum decision, not repeatedly as confirmations accumulate — the schema's own `uq_quorum_tx_fact` constraint (frozen, T02) makes a literal repeated-emission reading impossible without a schema change outside this task's scope. Human-approved. |
| R10 — finality emits `chain.tx.finalized`, never before | Yes | `Watcher.pollFinality`/`pollFinalityFor` `watch/Watcher.java:436-536`; `TxLifecyclePublisher.finalized` `watch/TxLifecyclePublisher.java:90-100` | `WatcherTest.shouldEmitChainTxFinalizedOnlyAtPerChainFinality` + 8 supporting tests (skip-<3-answers, verbatim-log-ordering, txHash-in-raw-JSON, local-majority-gate, ordering-vs-seen, 2-of-3-majority-with-disagreement, boolean-mapping-fidelity, duplicate-decision-path, cursor-mismatch-guard, restart-limitation characterization) | No | None remaining — see Phase 10's own disclosed mid-phase finding below. |
| R12/L5 — every event carries `chain:txhash:eventtype` | Yes | `TxLifecyclePublisher.idempotencyKey` `watch/TxLifecyclePublisher.java` (private static helper, called from all three public methods and `publish`) | `TxLifecyclePublisherTest.everyEventTypeCarriesTheExactDeterministicIdempotencyKeyFormat`, `.repeatedCallsForTheSameEventTypeProduceTheIdenticalIdempotencyKey` | No | None — literal format preserved exactly as LOCKED, even though this permits at most one event per `(chain, txHash, eventtype)` system-wide if two watches share an address (Phase 9 Finding #1, documented, not a deviation from L5 itself). |
| AC5 (scope) — `watchId` as `aggregateId` | Yes | `TxLifecyclePublisher.publish` `watch/TxLifecyclePublisher.java:105-113` (`watch.watchId().toString()`) | `TxLifecyclePublisherTest.seenPublishesWithTheTxSeenAggregateTypeAndWatchIdAsAggregateId` (and equivalent assertions for confirmed/finalized) | No | None. |
| AC6 (scope) — duplicate publish never propagates or double-inserts | Yes | `TxLifecyclePublisher.publish`'s `catch (DataIntegrityViolationException)` `watch/TxLifecyclePublisher.java:111-115` | `TxLifecyclePublisherTest.aDuplicateKeyViolationIsSwallowedRatherThanPropagated`, `.aNonDuplicateKeyRuntimeExceptionIsNotSwallowed` | No | Catches the exception's superclass broadly rather than re-querying to confirm the specific idempotency-key cause (Phase 9 Finding #12/#10, litigated and accepted — `outbox`'s only unique constraint is `idempotency_key`, and every other column is validated before any database call). |
| AC7 (scope) — a finality-poll tick with <3 real answers never calls `evaluate` | Yes | `pollFinalityFor` `watch/Watcher.java:463-467` | `WatcherTest.finalityPollSkipsTheTickWhenFewerThanThreeRealAnswersExist` | No | None. |
| AC8 (scope) — `lastFinalizedBlock` forward-only, set only on `FINALITY` `AGREED true` | Yes | `ChainCursor.advanceFinalizedTo` `watch/ChainCursor.java:124-130`; called from `pollFinalityFor` `watch/Watcher.java:533` | `ChainCursorTest.advanceFinalizedToSetsTheFinalizedBlockFromTheNullSentinel`, `.advanceFinalizedToIsANoOpWhenGivenABlockNumberAtOrBelowTheCurrentValue`; `WatcherTest.shouldEmitChainTxFinalizedOnlyAtPerChainFinality` (`assertThat(cursor.lastFinalizedBlock())...`) | No | None. |
| AC9 (L15) — `watch/`'s modified files import no feature-module entity outside the allow-listed set | Yes | `WatchModuleBoundaryTest`'s extended `ALLOWED_IMPORTS_BY_PREFIX` (adds `finality.FinalityPolicy`, `adapter.model.FinalityStatus`, `quorum.QuorumDecision`/`QuorumOutcome`, `events.OutboxPublisher`) | `WatchModuleBoundaryTest.noMainSourceFileInWatchImportsBeyondItsAllowedTypesOrAnyForbiddenPackage` | No | None. |
| Phase 4 Finding #1 — a chain with no `FinalityPolicy` fails fast, not silently | Yes | `Watcher` constructor `watch/Watcher.java:114-127` | `WatcherTest.constructorFailsFastWhenNoFinalityPolicyIsConfiguredForTheWatchsChain` | No | None. |
| Phase 4/9 Finding — missing `ChainCursor` row logs a warning rather than silently stranding finality | Yes | `handleSeenIfAgreed` `watch/Watcher.java:325-330`; `pollFinalityFor` `watch/Watcher.java:515-518` | `WatcherTest.logsAWarningAndSkipsFinalityPollingWhenNoChainCursorExistsAtSeenTime` | No | None. |
| Kimi Phase 8 Finding #1 — `pollFinalityFor` must never publish `finalized` citing the wrong `txHash` | Yes | mismatch guard `watch/Watcher.java:520-529` | `WatcherTest.finalizedIsWithheldWhenTheCursorSnapshotBelongsToADifferentTransaction` | No | None — the underlying write-once-snapshot limitation for a watch that observes more than one distinct transaction remains disclosed, unfixed (out of this task's proportionate scope per the frozen brief); this guard only prevents it from producing an actively misleading event. |
| Critical Phase 10 finding — `pollFinalityFor` must never persist a `FINALITY` decision before the local majority is actually final | Yes (fixed mid-Phase-10, with explicit user approval to step outside the phase's own "no production code" rule) | local-majority gate `watch/Watcher.java:481-484`, before the `evaluate` call | `WatcherTest.finalityPollNeverPersistsADecisionWhileTheLocalMajorityIsNotYetFinal` | No | Documented in full in `artifacts/10-test-generation.md`'s own dedicated section — the original Phase 6 implementation called `evaluate` unconditionally on every tick, which would have made `chain.tx.finalized` unreachable in practice; neither Phase 7 nor Phase 8 review caught this. |
| Disclosed, unfixed limitation — `pendingFinality` is in-memory only, does not survive a restart between `SEEN` and `FINALIZED` | N/A (accepted at Phase 9, not fixed) | — | `WatcherTest.aFreshWatcherInstanceDoesNotResumePollingASeenButNotFinalizedCursor` (characterization test, documents current behavior) | Yes — deliberately, disclosed | A genuinely repeated/incremental durable-state fix was judged out of this task's proportionate scope (Phase 9), matching the precedent of T16's own disclosed, unfixed correlation-TTL gap. |
| Disclosed, unfixed limitation — a provider that disagrees on two separate facts of the same transaction (one original fact, one `FINALITY`) can be double-counted in the disagreement counter | N/A (accepted at Phase 9, not fixed) | `recordDisagreementsIfAny` `watch/Watcher.java` (unchanged from T16, reused for `FINALITY` without correlation-scoped dedup) | None (explicitly not tested — a documented limitation, not a behavior to lock in) | Yes — deliberately, disclosed | Low real-world frequency (requires the same provider to disagree twice on the same transaction across two separate points in time); affects only a health-tracking counter, not money-correctness or event content. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file named in the Phase 4 frozen brief's "Files to
Create"/"Files to Modify" exists and is wired in (`TxLifecyclePublisher.java`,
`V8__crypto_chain_cursors_tx_snapshot.sql`, and the modifications to `Watcher.java`,
`WatcherRegistry.java`, `ChainCursor.java`, `WatcherProperties.java`, `application.properties`); every
required test from the frozen brief and every named test from `package.md` §8 has a corresponding,
passing test. Phase 9's 14 consolidated review findings and Phase 11's 15 test-review findings are both
closed, with two findings in each phase rejected only after direct verification showed the rejection was
correct (Phase 9: the duplicate-key-catch scope was already justified; Phase 11: one finding's factual
premise was wrong, another's suggested test would have contradicted an already-accepted design decision).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC9 (numbering per the frozen brief) all
have implementation evidence and test coverage per the matrix above, including the AC7/AC8 negative
criteria (skip-tick, forward-only) which are the correct verification shape for a "must not" requirement.

**(3) Does it violate any LOCKED decision?** No. L5/R12's idempotency-key format is preserved literally,
even in the edge case where doing so means at most one event fires system-wide for two watches sharing
an address — a disclosed consequence of respecting L5 exactly, not a violation of it. No other task's
frozen files were touched (`adapter/`, `observation/ObservationLog.java`, `quorum/QuorumDecisionService.java`,
`quorum/QuorumEvaluator.java`, `finality/` are called, never modified; `V1`-`V7` migrations are untouched,
only an additive `V8` was introduced).

**(4) Remaining risks?**
- `pendingFinality`'s in-memory-only nature means a process restart between a transaction being `SEEN`
  and reaching `FINALITY` permanently strands that transaction's finality tracking — disclosed at Phase 9,
  not fixed, now also locked in by a characterization test so a future accidental change doesn't silently
  alter the behavior without review.
- A `ChainCursor`'s write-once transaction snapshot means a watch that legitimately observes a second,
  distinct transaction after its first only ever tracks the first one's finality — disclosed at Phase 4,
  and Phase 9 added a guard specifically preventing this from producing a *misleading* event (citing the
  wrong `txHash`), but does not make the second transaction's own finality trackable.
- A provider disagreeing on two separate facts of the same transaction (one of the original four facts,
  plus `FINALITY`) can be double-counted in the disagreement health counter — low real-world frequency,
  affects only an operational health signal, not money-correctness or emitted event content.
- `package.md` Q4 (Tron confirmation-count basis) remains an unresolved upstream ambiguity; this task
  passes through whatever count `CONFIRMATIONS`'s quorum decision already carries, unchanged.
- Contract files (`contracts/events/chain/*.v1.schema.json`) remain unauthored, deferred to task 23 per
  the same precedent `ProviderDegradedPublisher` (T10) already established.

## Verdict

**PASS** — every in-scope requirement, acceptance criterion, and LOCKED decision is implemented, tested,
and traced to evidence; the one critical defect this task's own test-writing surfaced (the finality
quorum-evaluation ordering bug) was fixed and is now covered by a dedicated regression test; the full
module regression (585 tests) shows zero regressions and only the same 6 pre-existing, disclosed,
unrelated failures.
