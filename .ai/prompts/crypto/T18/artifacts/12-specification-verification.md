# crypto · T18 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R11 — a previously observed transaction invalidated by a reorg walks the cursor backward and emits `chain.tx.reorged` | Yes | `Watcher.checkForReorg` `watch/Watcher.java:488-556`; `ReorgDetector.reorg` `reorg/ReorgDetector.java:61-75` | `WatcherTest.shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` (named) + 9 supporting scenarios | No | **Architecture pivot (Phase 3/4, human-approved):** the original push-based detection design (waiting for `subscribeAddress` to re-deliver an already-seen `txHash`) was verified, by reading `EthereumAdapter`/`TronAdapter` source directly, to be structurally unreachable — neither adapter ever re-reports a transaction it already scanned. Replaced with a `ChainAdapter.getTx` pull, folded into the existing finality-poll tick. |
| L6 — no forward-derived state survives an invalidating reorg | Yes | `ChainCursor.invalidate` `watch/ChainCursor.java:138-146` | `ChainCursorTest.invalidateResetsEveryForwardDerivedFieldFromAFullyPopulatedState`, `.invalidateOnAFreshPlaceholderIsANoOpBeyondUpdatingTheTimestamp`, `.aTransactionCanBeRecordedAgainAfterInvalidate` | No | Full reset, not a targeted per-block rewind — disclosed as acceptable only under T17's own one-transaction-per-watch design (Phase 3 Finding #8, human-approved). |
| AC3 (task statement) — reorg tested after `seen`, after `confirmed`, and after `finalized` | Yes | `Watcher.checkForReorg` keys off `ChainCursor.txHash()` directly, independent of `pendingFinality` membership (`watch/Watcher.java:488-495`) | `WatcherTest.reorgDiscoveredAfterSeenAloneTriggersReorged`, `.reorgDiscoveredAfterConfirmedTriggersReorged`, `.reorgDiscoveredAfterFinalizedTriggersReorgedEvenThoughFinalityWasAlreadyDecided` | No | None — the "after finalized" case (package.md's own highest-severity threat #3 framing) is covered even though it wasn't in the task statement's literal two examples, since the mechanism naturally extends there. |
| AC4 (L5/R12) — idempotency key `{chain}:{txHash}:reorged` | Yes | `reorg/ReorgDetector.java` (private `idempotencyKey` construction) | `ReorgDetectorTest.reorgUsesTheExactDeterministicIdempotencyKeyFormat`, `.repeatedCallsForTheSameTransactionProduceTheIdenticalIdempotencyKey` | No | Literal format preserved exactly, even though this permits only one `chain.tx.reorged` ever per `(chain, txHash)` — consistent with T17's identical treatment of the other three `chain.tx.*` events. |
| AC5 (scope) — a still-`true` majority triggers nothing, cursor untouched | Yes | `checkForReorg`'s `if (stillExists) return;` `watch/Watcher.java:521-523` | `WatcherTest.aFreshMajorityStillExistsTrueDoesNotTriggerReorgOrAlterTheCursor`, `.twoTrueOneFalseMajorityStillExistsTrueDoesNotTriggerReorg` | No | None. |
| AC6 (L15) — `reorg/` imports no `watch/`/`adapter/` entity | Yes | `ReorgDetector.reorg`'s primitive-typed signature `reorg/ReorgDetector.java:61` (no `Watch`/`ChainCursor` import anywhere in `reorg/`) | `ReorgModuleBoundaryTest.noMainSourceFileInReorgImportsBeyondItsAllowedEventsTypeOrAnyForbiddenPackage`; `WatchModuleBoundaryTest` (extended allow-list) | No | None. |
| AC7 (scope) — a tick with <3 real `getTx` answers declares nothing | Yes | `checkForReorg`'s `if (existsAnswers.size() != 3) return;` `watch/Watcher.java:515-519` | `WatcherTest.checkForReorgDeclaresNothingWithFewerThanThreeRealAnswers` | No | None. |
| AC8 (scope) — dissenting minority flagged via `recordDisagreementsIfAny` | Yes | `watch/Watcher.java:545` | `WatcherTest.checkForReorgFlagsTheDissentingMinorityProviderStillReportingExistsTrue` | No | Reuses the pre-existing helper's own known limitation (no live `TxCorrelation` to one-shot-guard against here) — disclosed, not fixed (Phase 9). |
| AC9 (scope) — idempotent re-check after invalidation is a no-op | Yes | `checkForReorg`'s leading guard `watch/Watcher.java:489-492` | `WatcherTest.checkForReorgIsANoOpOnTheTickAfterTheCursorIsAlreadyInvalidated`, `.checkForReorgIsSkippedWhenNoChainCursorRowExistsAtAll` | No | None. |
| L3 — verbatim log before use | Yes | `watch/Watcher.java:497-499` (`observationLog.record(..., FactType.EXISTENCE, toRawJson(result))`, before the answer is used in the majority computation) | `WatcherTest.checkForReorgLogsTheRawResponseVerbatimBeforePublishingTheReorgEvent`, `.toRawJsonSerializesAmountAsADecimalStringNeverAJsonNumber` | No | Added at Phase 9 after self-review and Kimi's independent review both caught its absence in the initial Phase 6 implementation. |
| Phase 9 fix — publish before invalidate (self-healing on failure) | Yes | `watch/Watcher.java:549-554` | `WatcherTest.anExceptionFromReorgDetectorDoesNotPropagateAndTheNextTickSelfHeals`, `.aCursorSaveFailureAfterASuccessfulPublishSelfHealsOnTheNextTick` | No | None. |
| Phase 9 fix — per-call exception guards in `pollFinality` | Yes | `watch/Watcher.java:439-458` | `WatcherTest.anExceptionFromReorgDetectorDoesNotPropagateAndTheNextTickSelfHeals` (checkForReorg half), `.anExceptionFromPollFinalityForDoesNotPreventCheckForReorgOnTheNextTick` (pollFinalityFor half) | No | None. |
| Phase 9 fix — fresh-cursor re-check before acting | Yes | `watch/Watcher.java:525-529` | `WatcherTest.checkForReorgAbortsIfTheCursorMovedOnToADifferentTransactionBeforeActing`, `.checkForReorgAbortsIfTheFreshCursorReadIsEmpty` | No | Narrows, does not close, the cross-thread race with `handleSeenIfAgreed` — a `@Version` optimistic-locking column would close it fully but is a schema change disclosed as out of this task's proportionate scope (Phase 9 Findings #4/#8). |
| Disclosed, unfixed limitation — cross-thread race on `ChainCursor` between `handleSeenIfAgreed` and `checkForReorg` | N/A (accepted at Phase 9, not fixed) | Documented in `checkForReorg`'s own Javadoc `watch/Watcher.java` | None (a characterization test would only lock in a race's *absence* of a guarantee, not a behavior worth pinning) | Yes — deliberately, disclosed | Would require a `@Version` column (schema change) and retry logic in two call sites; judged out of scope for a review-resolution phase. |
| Disclosed, unfixed limitation — no recovery if a reorged transaction is later re-included on-chain | N/A (accepted at Phase 3/4, not fixed) | `reorg/ReorgDetector.java` class Javadoc | None | Yes — deliberately, disclosed | A genuine "episode" concept across multiple frozen schemas (`quorum_decisions`, `outbox`) is a scope expansion beyond this task. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file named in the Phase 4 frozen brief exists and is wired
in (`reorg/ReorgDetector.java`, `reorg/ReorgModuleBoundaryTest.java`, and the modifications to
`Watcher.java`, `WatcherRegistry.java`, `ChainCursor.java`, `WatchModuleBoundaryTest.java`); every
required test from the frozen brief and the one named test from `package.md` §8 has a corresponding,
passing test. Phase 9's 14 consolidated review findings and Phase 11's 12 test-review findings are both
closed, with one finding rejected in each phase only after direct verification showed the rejection was
correct (Phase 9: none rejected outright, several accepted as documented limitations with explicit
justification; Phase 11: the one rejected finding's suggested test was verified to contradict the
current, intentional implementation).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 (folded into R11 above) through AC9 all
have implementation evidence and test coverage per the matrix, including the negative criteria (AC5,
AC7, AC9) which are the correct verification shape for "must not" requirements.

**(3) Does it violate any LOCKED decision?** No. L6's "no forward-derived state survives" is honored by
`ChainCursor.invalidate`'s full reset; L5/R12's idempotency-key format is preserved literally; L1's
2-of-3 rule is computed identically whether persisted via `QuorumDecisionService` or checked locally
(verified mathematically during Phase 3: a Boolean fact can never produce `HELD`, so the local
computation and a hypothetical persisted decision would always agree). No frozen file from a prior task
(`adapter/`, `observation/`, `quorum/`, `TxLifecyclePublisher.java`, `V1`-`V8` migrations) was modified.

**(4) Remaining risks?**
- The cross-thread race between `handleSeenIfAgreed` and `checkForReorg` on the same `ChainCursor` row
  is narrowed (a fresh re-check immediately before acting) but not closed — a genuinely concurrent
  mutation in the narrow window between the re-check and the save could still be lost. Disclosed, not
  fixed, at Phase 9.
- A transaction that is reorged out and later re-included on the canonical chain produces no further
  lifecycle events of any kind (the deterministic idempotency key and the one-decision-ever quorum
  constraint both permit only one occurrence each). Disclosed at Phase 3/4 as out of scope.
- `recordDisagreementsIfAny`'s reuse for reorg detection has no live correlation to one-shot-guard
  against, so a provider could in principle be double-counted in the health-tracking disagreement
  counter across a reorg and an earlier fact for the same transaction — low real-world frequency,
  affects only an operational signal, not money-correctness or emitted event content.
- Contract files (`contracts/events/chain/tx-reorged.v1.schema.json`) remain unauthored, deferred to
  task 23 per the same precedent every prior task in this package has already established.

## Verdict

**PASS** — every in-scope requirement, acceptance criterion, and LOCKED decision is implemented, tested,
and traced to evidence; the one critical defect this task's own Phase 3 review surfaced (the
push-based-detection design being structurally unreachable) was caught before implementation and
corrected via a verified, human-approved architecture pivot; the full module regression (619 tests)
shows zero regressions and only the same 4 pre-existing, disclosed, unrelated failing files.
