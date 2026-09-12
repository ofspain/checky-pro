# crypto · T18 · Phase 10 — Test Generation

Test manifest mapping each new test to the acceptance criterion / requirement it verifies. No production
code changed in this phase.

## New test files

| File | Tests | Purpose |
|---|---|---|
| `reorg/ReorgDetectorTest.java` | 11 | `ReorgDetector`'s aggregate type/id, idempotency key format, payload shape, duplicate-key swallowing, and the new null-checks — mirrors `TxLifecyclePublisherTest`'s established style. |
| `watch/ChainCursorTest.java` (extended) | +3 (13 total) | `invalidate`'s full-reset behavior from a populated state, its no-op-on-a-fresh-placeholder case, and that a transaction can be recorded again after invalidation. |
| `watch/WatcherTest.java` (extended) | +11 (54 total) | Every T18 acceptance criterion at the `Watcher` level: reorg detection/walk-back, timing across seen/confirmed/finalized, the still-exists no-op, the below-3-answers no-op, dissenting-minority flagging, post-invalidation no-op, verbatim-log ordering, exception-guard self-healing, and the fresh-cursor race-mitigation abort. |

## Traceability matrix

| Test | AC / Requirement | What it proves |
|---|---|---|
| `ReorgDetectorTest.reorgPublishesWithTheTxReorgedAggregateTypeAndWatchIdAsAggregateId` | AC6 | `watchId` used as `aggregateId`. |
| `ReorgDetectorTest.reorgBuildsTheDocumentedPayloadShape` | Field-sourcing map | `ReorgedPayload`'s fields. |
| `ReorgDetectorTest.reorgUsesTheExactDeterministicIdempotencyKeyFormat` | AC4 (R12/L5) | `{chain}:{txHash}:reorged` exact format. |
| `ReorgDetectorTest.repeatedCallsForTheSameTransactionProduceTheIdenticalIdempotencyKey` | L5 | Deterministic, not randomized. |
| `ReorgDetectorTest.aDuplicateKeyViolationIsSwallowedRatherThanPropagated` | Phase 3 Finding #10 | Duplicate-key exception swallowed. |
| `ReorgDetectorTest.aNonDuplicateKeyRuntimeExceptionIsNotSwallowed` | (negative) | A different exception is not masked. |
| `ReorgDetectorTest.reorgRejectsANull*` (5 tests) | Phase 9 Kimi Finding #9 | Each of the five parameters is null-checked. |
| `ChainCursorTest.invalidateResetsEveryForwardDerivedFieldFromAFullyPopulatedState` | AC2 (L6) | Full reset from a seen+finalized state. |
| `ChainCursorTest.invalidateOnAFreshPlaceholderIsANoOpBeyondUpdatingTheTimestamp` | AC2 | No exception on an already-empty cursor. |
| `ChainCursorTest.aTransactionCanBeRecordedAgainAfterInvalidate` | Scope | `recordSeenTransaction`'s write-once guard is lifted by `invalidate`. |
| `WatcherTest.shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` | R11 (named test) | Core detection + full cursor walk-back. |
| `WatcherTest.reorgDiscoveredAfterSeenAloneTriggersReorged` | AC3 | Reorg timing case 1. |
| `WatcherTest.reorgDiscoveredAfterConfirmedTriggersReorged` | AC3 | Reorg timing case 2. |
| `WatcherTest.reorgDiscoveredAfterFinalizedTriggersReorgedEvenThoughFinalityWasAlreadyDecided` | AC3 | Reorg timing case 3 — proves detection is keyed off the cursor, not `pendingFinality`. |
| `WatcherTest.aFreshMajorityStillExistsTrueDoesNotTriggerReorgOrAlterTheCursor` | AC5 | No-op on a still-true majority. |
| `WatcherTest.checkForReorgDeclaresNothingWithFewerThanThreeRealAnswers` | AC7 | No-op below 3 real answers. |
| `WatcherTest.checkForReorgFlagsTheDissentingMinorityProviderStillReportingExistsTrue` | AC8 | Minority disagreement flagged. |
| `WatcherTest.checkForReorgIsANoOpOnTheTickAfterTheCursorIsAlreadyInvalidated` | AC9 | Idempotent re-check, no further adapter calls. |
| `WatcherTest.checkForReorgLogsTheRawResponseVerbatimBeforePublishingTheReorgEvent` | Phase 9 Finding #1 (regression) | L3 ordering: log before publish. |
| `WatcherTest.anExceptionFromReorgDetectorDoesNotPropagateAndTheNextTickSelfHeals` | Phase 9 Finding #2/#3 (regression) | Exception guard + publish-before-invalidate self-healing retry. |
| `WatcherTest.checkForReorgAbortsIfTheCursorMovedOnToADifferentTransactionBeforeActing` | Phase 9 Finding #4/#10 (regression) | Fresh-cursor re-check aborts on a mismatch. |

## Verification run

- `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest,ReorgModuleBoundaryTest,ReorgDetectorTest` — 103/103 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 611 tests, same 4 pre-existing, disclosed,
  unrelated failing files (`ObservationRepositoryIntegrationTest`, `ProviderHealthRepositoryIntegrationTest`,
  `QuorumDecisionRepositoryIntegrationTest`, `TokenAllowlistRepositoryIntegrationTest`). Zero regressions.

## Test-design note

Several tests (`seenCursorFor` helper) directly populate a `ChainCursor` via `recordSeenTransaction`
rather than driving the full observation-delivery flow `handleSeenIfAgreed` already exhaustively covers
in T17's own tests — these tests are about `checkForReorg`'s own logic in isolation, not re-proving how
a transaction becomes seen. One test
(`reorgDiscoveredAfterFinalizedTriggersReorgedEvenThoughFinalityWasAlreadyDecided`) needed
`pendingFinality` populated too (to exercise the real `pollFinalityFor`/finalization path before the
reorg), seeded directly via reflection — mirrors this file's own established
`stopShutsDownItsOwnPrivateSweepScheduler` precedent for reaching a private field with no public
accessor.
