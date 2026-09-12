# crypto · T17 · Phase 10 — Test Generation

Test manifest mapping each new test to the acceptance criterion / requirement it verifies. No production
code changed in this phase **except** one correctness fix forced by a bug the test-writing itself
surfaced — disclosed in full below, not hidden.

## Critical mid-phase finding: `pollFinalityFor` could never actually emit `chain.tx.finalized`

While writing `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` (a realistic "poll while not yet final,
then poll again once it is" sequence), the test failed with a `NullPointerException`, revealing that
`pollFinalityFor` called `QuorumDecisionService.evaluate(...)` for `FINALITY` **unconditionally** on every
poll tick with 3 real answers — including the very first tick, long before a transaction is actually
final. Because `evaluate` can only ever succeed once per `(chain, txHash, factType)` (correct for the
other four one-shot facts, but not for `FINALITY`, which starts false and only becomes true later), the
first qualifying tick would almost always persist a premature `AGREED false` — permanently, since no
further `evaluate` attempt for that tuple can ever succeed. `chain.tx.finalized` was therefore
effectively unreachable in practice. Neither Phase 7 (self-review) nor Phase 8 (Kimi's independent
review) caught this — both examined downstream consequences without tracing what the very first poll
tick's own quorum call does.

Per explicit user direction, this was fixed immediately rather than deferred: `pollFinalityFor` now
computes the majority `isFinal` value **locally** from the 3 raw answers first (no persistence), and only
calls `QuorumDecisionService.evaluate` once that local majority is already `true` — the one persisted
decision this produces is therefore always `AGREED true` (a Boolean fact can never produce `HELD`: three
booleans always have a 2-of-3 majority for one value). A "not yet final" tick now persists nothing and
simply waits for the next tick. `watch/Watcher.java`'s `pollFinalityFor` is the only file touched beyond
what this phase's own test files required; `finalityPollNeverPersistsADecisionWhileTheLocalMajorityIsNotYetFinal`
is the regression test for this specific fix.

## New test files

| File | Tests | Purpose |
|---|---|---|
| `watch/TxLifecyclePublisherTest.java` | 10 | `TxLifecyclePublisher`'s aggregate type/id, idempotency key format, payload shape for all three event types, and duplicate-key swallowing — mirrors `ProviderDegradedPublisherTest`'s established style. |
| `watch/ChainCursorTest.java` (extended) | +5 (10 total) | `recordSeenTransaction`'s capture and write-once behavior, `advanceFinalizedTo`'s forward-only guard. |
| `watch/WatcherTest.java` (extended) | +14 (33 total) | Every T17 acceptance criterion at the `Watcher` level: seen/confirmed/finalized triggering, the majority-sourcing fix, the missing-cursor and cursor-mismatch fixes, fail-fast construction, and the finality-poll correctness fix. |

## Traceability matrix

| Test | AC / Requirement | What it proves |
|---|---|---|
| `TxLifecyclePublisherTest.seenPublishesWithTheTxSeenAggregateTypeAndWatchIdAsAggregateId` | AC5 | `watchId` used as `aggregateId` for `chain.tx.seen`. |
| `TxLifecyclePublisherTest.seenBuildsTheDocumentedPayloadShape` | AC1 (R8), field-sourcing map | `SeenPayload`'s fields, including `confirmations` (Phase 9 fix). |
| `TxLifecyclePublisherTest.confirmedPublishesWithTheTxConfirmedAggregateTypeAndTheAgreedCount` | AC2 (R9) | `ConfirmedPayload` carries the agreed count. |
| `TxLifecyclePublisherTest.finalizedPublishesWithTheTxFinalizedAggregateTypeAndTheCursorSnapshot` | AC3 (R10) | `FinalizedPayload` sourced from the `ChainCursor` snapshot. |
| `TxLifecyclePublisherTest.finalizedSerializesAmountAsADecimalStringNeverAJsonNumber` | agents.md (money types) | `amount` is a decimal string, not a JSON number. |
| `TxLifecyclePublisherTest.finalizedToleratesANullAmountOnTheCursor` | Phase 9 Finding #11 (disclosed) | No exception when the upstream `TxResult` contract is (hypothetically) violated. |
| `TxLifecyclePublisherTest.everyEventTypeCarriesTheExactDeterministicIdempotencyKeyFormat` | `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent` (R12/L5) | Exact `{chain}:{txHash}:{eventtype}` format for all three types. |
| `TxLifecyclePublisherTest.repeatedCallsForTheSameEventTypeProduceTheIdenticalIdempotencyKey` | L5 | Deterministic, not randomized (unlike `ProviderDegradedPublisher`). |
| `TxLifecyclePublisherTest.aDuplicateKeyViolationIsSwallowedRatherThanPropagated` | AC6, Phase 9 Finding #12 | Duplicate-key `DataIntegrityViolationException` swallowed. |
| `TxLifecyclePublisherTest.aNonDuplicateKeyRuntimeExceptionIsNotSwallowed` | AC6 (negative) | A different, non-idempotency exception is not masked. |
| `ChainCursorTest.recordSeenTransactionCapturesTheTxHashAmountAndAddresses` | AC1 precondition | Snapshot fields captured correctly. |
| `ChainCursorTest.recordSeenTransactionIsWriteOnce` | Frozen brief disclosed limitation | Second, distinct transaction does not overwrite the first snapshot. |
| `ChainCursorTest.recordSeenTransactionRejectsANullTxHash` | Null-safety | `Objects.requireNonNull` guard. |
| `ChainCursorTest.advanceFinalizedToSetsTheFinalizedBlockFromTheNullSentinel` | AC8 (R10) | First finalization sets the block number. |
| `ChainCursorTest.advanceFinalizedToIsANoOpWhenGivenABlockNumberAtOrBelowTheCurrentValue` | AC8 | Forward-only guard. |
| `WatcherTest.shouldEmitChainTxSeenOnQuorumAgreedFirstSighting` | R8 (named test) | `seen` emitted with the majority amount/from/to snapshot recorded. |
| `WatcherTest.doesNotEmitSeenWhenExistenceIsHeld` | AC1 | No event on `HELD`. |
| `WatcherTest.doesNotEmitSeenWhenExistenceAgreesFalse` | AC1, Phase 9 Finding #10 | No event on `AGREED false`. |
| `WatcherTest.seenSourcesAmountFromTheQuorumMajorityNotAnArbitraryProvider` | Phase 9 Finding #1/#5 (regression) | Minority provider's amount is never persisted. |
| `WatcherTest.logsAWarningAndSkipsFinalityPollingWhenNoChainCursorExistsAtSeenTime` | Phase 9 Finding #3/#7 (regression) | Missing cursor doesn't crash; nothing added to `pendingFinality`. |
| `WatcherTest.shouldEmitChainTxConfirmedWithConfirmationCount` | R9 (named test) | `confirmed` emitted with the agreed count. |
| `WatcherTest.doesNotEmitConfirmedWhenConfirmationsIsHeld` | AC2 | No event on `HELD`. |
| `WatcherTest.shouldEmitChainTxFinalizedOnlyAtPerChainFinality` | R10 (named test) | No event while not-yet-final; emitted once local majority agrees final and quorum persists `AGREED true`; `lastFinalizedBlock` advanced. |
| `WatcherTest.finalityPollSkipsTheTickWhenFewerThanThreeRealAnswersExist` | AC7, Phase 3 Finding #6 | A tick with 2 real answers never calls `evaluate`. |
| `WatcherTest.finalityPollLogsTheRawResponseVerbatimBeforeEvaluatingQuorum` | L3, Phase 3 Finding #3 | Call-order: observation logged before quorum evaluated, for `FINALITY`. |
| `WatcherTest.finalityRawObservationIncludesTheTxHash` | Phase 9 Finding #6/#13 (regression) | `toRawJson` embeds `txHash`. |
| `WatcherTest.finalityPollNeverPersistsADecisionWhileTheLocalMajorityIsNotYetFinal` | R10 (critical regression, this phase) | The finality bug fix: no `evaluate` call, no event, while not yet final. |
| `WatcherTest.finalizedIsWithheldWhenTheCursorSnapshotBelongsToADifferentTransaction` | Phase 9 Finding #1 (Kimi, regression) | The mismatch guard withholds a misleading event. |
| `WatcherTest.constructorFailsFastWhenNoFinalityPolicyIsConfiguredForTheWatchsChain` | Phase 9 Finding #2/#4 (regression) | `IllegalStateException` at construction, not silent NPE-as-lagging. |

## Verification run

- `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest` — 66/66 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 6 failures, the same pre-existing,
  disclosed, unrelated set across the same 4 files (`ObservationRepositoryIntegrationTest`,
  `ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`,
  `TokenAllowlistRepositoryIntegrationTest`). Zero regressions.

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into the test suite. Kimi raised 15 findings; 13 were verified genuine gaps
and fixed with new or extended tests, 2 were rejected.

| Finding | Disposition | Test added/extended |
|---|---|---|
| 1. No ordering proof that `seen` precedes `finalized` | **ACCEPTED** | `WatcherTest.finalizedIsNeverPublishedBeforeSeenIsPublished` (`InOrder`). |
| 2. Missing-cursor test's assertion was weak | **ACCEPTED** | `logsAWarningAndSkipsFinalityPollingWhenNoChainCursorExistsAtSeenTime` extended with a direct `observationLog` never-called-for-FINALITY assertion. |
| 3. No duplicate-observation suppression test for `seen`/`confirmed` | **ACCEPTED** | `seenIsEmittedExactlyOnceEvenIfTheAgreeingObservationsAreRedelivered`, `confirmedIsEmittedExactlyOnceEvenIfTheAgreeingObservationsAreRedelivered`. |
| 4. No test for `confirmed` withheld when `EXISTENCE` has a minority `false` | **ACCEPTED** | `doesNotEmitConfirmedWhenExistenceHasAMinorityFalseAnswer`. |
| 5. No 2-of-3 finality majority + disagreement test | **ACCEPTED** | `finalityReachesAgreementUnderTwoOfThreeMajorityAndFlagsTheDisagreeingProvider`. |
| 6. No proof of the actual booleans passed to `evaluate` for `FINALITY` | **ACCEPTED** | `finalityPollPassesEachProvidersActualIsFinalValueToQuorumEvaluation` (`ArgumentCaptor`). |
| 7. No test for restart-recovery of `pendingFinality` | **ACCEPTED, reframed** | Kimi's own suggested test would have asserted behavior contradicting Phase 9's already-accepted, disclosed limitation (in-memory-only, not fixed). Added `aFreshWatcherInstanceDoesNotResumePollingASeenButNotFinalizedCursor` instead — a characterization test locking in the current, intentional behavior so a future change can't silently alter it. |
| 8. Null-amount test didn't inspect the payload | **ACCEPTED** | `finalizedToleratesANullAmountOnTheCursor` extended to assert `payload.amount()` is `null`. |
| 9. No test proving the duplicate-key catch is scoped to only that cause | **REJECTED** | Already litigated and settled at Phase 9 (broad catch is intentional and justified there); Kimi's suggested test would assert behavior contrary to that accepted design. No new information changes the Phase 9 reasoning. |
| 10. No `confirmed` majority-count test with disagreement | **ACCEPTED** | `confirmedUsesTheMajorityConfirmationCountWhenProvidersDisagree`. |
| 11. No test for the `FINALITY` duplicate-decision catch path | **ACCEPTED** | `finalityPollRemovesFromPendingWhenADecisionAlreadyExists`. |
| 12. Mismatch-guard test didn't assert `lastFinalizedBlock` stayed unchanged | **ACCEPTED** | `finalizedIsWithheldWhenTheCursorSnapshotBelongsToADifferentTransaction` extended. |
| 13. No full payload-shape test for `confirmed` | **ACCEPTED** | `TxLifecyclePublisherTest.confirmedBuildsTheDocumentedPayloadShape`. |
| 14. No full payload-shape test for `finalized` (`invoiceUuid`/`occurredAt`) | **ACCEPTED** | `finalizedPublishesWithTheTxFinalizedAggregateTypeAndTheCursorSnapshot` extended. |
| 15. No 2-false-1-true `EXISTENCE` majority-recomputation test | **ACCEPTED** | `doesNotEmitSeenWhenExistenceMajorityIsFalseDespiteAMinorityTrueAnswer`. |

**Verification run (Phase 11):** `mvn -pl services/crypto test -Dtest=WatcherTest,WatcherRegistryTest,ChainCursorTest,ProviderSetTest,WatchModuleBoundaryTest,TxLifecyclePublisherTest` — 77/77 pass (43+8+10+4+1+11). Full module regression: same 6 pre-existing unrelated failures, zero regressions.
