# crypto · T18 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T18 — Reorg detector |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the Phase 10 test suite against the frozen brief, acceptance criteria, and named tests.

---

## 1. No test asserts the actual content of the verbatim `getTx` observation logged for reorg detection

- **Gap:** `checkForReorgLogsTheRawResponseVerbatimBeforePublishingTheReorgEvent` verifies call order and that three `EXISTENCE` rows are recorded, but it uses `anyString()` for the raw JSON payload. It does not verify that the logged JSON contains the queried `txHash`, `exists=false`, or any other field.
- **Why it matters:** L3 requires the observation to be verbatim and self-describing. A regression that logged an empty JSON object or omitted `txHash` would pass this test.
- **Suggested test:** Capture the JSON strings passed to `observationLog.record` and assert each contains `TX_HASH` and `"exists":false` (or equivalent), mirroring `finalityRawObservationIncludesTheTxHash`.

## 2. No test exercises a 2-true-1-false majority-still-exists split

- **Gap:** `aFreshMajorityStillExistsTrueDoesNotTriggerReorgOrAlterTheCursor` uses three unanimous `exists=true` answers. There is no test where two providers report `exists=true` and one reports `exists=false`, even though this is the mirror image of AC8's dissenting-minority case.
- **Why it matters:** AC5 says a majority still `true` triggers nothing. The 2-true-1-false shape is the realistic boundary case and exercises the full boolean-majority path.
- **Suggested test:** Script providers A/B with `exists=true` and provider C with `exists=false`; assert no `reorgDetector.reorg(...)` call and that the cursor is unchanged.

## 3. No test verifies that a 3-of-3 `exists=false` reorg does not flag any provider as disagreeing

- **Gap:** `shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` uses three unanimous `exists=false` answers and asserts the cursor is reset, but it does not assert that `providerHealthTracker.recordDisagreement(...)` is never called.
- **Why it matters:** `recordDisagreementsIfAny` returns early on unanimous answers, but a regression that removed that early-return would flag every provider on a reorg.
- **Suggested test:** Add `verify(providerHealthTracker, never()).recordDisagreement(anyString(), anyString())` to the unanimous-false reorg test.

## 4. No test covers a successful reorg publish followed by a cursor-save failure

- **Gap:** `anExceptionFromReorgDetectorDoesNotPropagateAndTheNextTickSelfHeals` covers the case where `reorgDetector.reorg` throws first (publish fails). There is no test for the inverse: publish succeeds, but `chainCursorRepository.save` throws.
- **Why it matters:** The publish-before-invalidate design is meant to be self-healing on retry. If save fails, the next tick should detect the same reorg, hit the duplicate-key catch (benign), and retry the save. This path is untested.
- **Suggested test:** Stub `chainCursorRepository.save(cursor)` to throw on the first call and succeed thereafter; assert that `reorgDetector.reorg(...)` is called twice and the cursor is invalidated after the second tick.

## 5. No test directly verifies `checkForReorg` short-circuits when the cursor row is missing

- **Gap:** The early-return path at `watch/Watcher.java:490-492` (cursor absent or `txHash == null`) is only partially covered. `checkForReorgIsANoOpOnTheTickAfterTheCursorIsAlreadyInvalidated` tests the post-invalidation `txHash == null` case indirectly via interaction counts.
- **Why it matters:** A missing cursor row (the T17 warning path) should never cause a reorg check or any adapter call. The current test does not isolate this branch.
- **Suggested test:** With `chainCursorRepository.findByWatchId(...)` returning `Optional.empty()`, call `watcher.pollFinality()` and verify no `getTx` calls occur and no `reorgDetector.reorg(...)` is invoked.

## 6. No test asserts `pendingFinality` is actually cleared on reorg

- **Gap:** `reorgDiscoveredAfterFinalizedTriggersReorgedEvenThoughFinalityWasAlreadyDecided` seeds `pendingFinality` via reflection to exercise the post-finalized path, but it does not assert that the `txHash` is removed from `pendingFinality` after the reorg.
- **Why it matters:** AC1 requires "one cursor walk-back" as part of the reorg handling; leaving the `txHash` in `pendingFinality` would cause useless (and potentially misleading) finality-poll attempts on an invalidated transaction.
- **Suggested test:** After triggering the reorg, read `pendingFinality` via reflection and assert it does not contain `TX_HASH`.

## 7. No test verifies the `toRawJson(TxResult)` output shape

- **Gap:** `checkForReorgLogsTheRawResponseVerbatimBeforePublishingTheReorgEvent` treats the raw JSON as an opaque string. There is no test for the `TxResult`-to-JSON mapping itself (e.g., `amount` serialized as a decimal string, not a JSON number).
- **Why it matters:** `agents.md` forbids money values as JSON numbers. A regression in `toRawJson(TxResult)` that serialized `amount` as a bare number would violate the standing rule.
- **Suggested test:** Capture the raw JSON logged for a `TxResult` with a large `amount` and assert the `amount` field is a quoted decimal string.

## 8. No test covers the empty-fresh-cursor abort branch

- **Gap:** `checkForReorgAbortsIfTheCursorMovedOnToADifferentTransactionBeforeActing` tests the case where the fresh cursor holds a *different* `txHash`. It does not test the branch where the fresh cursor is empty (`freshCursorOpt.isEmpty()`).
- **Why it matters:** Both branches share the same abort logic; the empty-cursor case (e.g., another thread invalidated between the initial read and the re-check) should also cancel the reorg handling without publishing.
- **Suggested test:** Return `Optional.of(staleCursor)` on the first `findByWatchId` call and `Optional.empty()` on the second; assert `reorgDetector.reorg(...)` is never called.

## 9. No test proves `pollFinality` catches exceptions from `pollFinalityFor` independently

- **Gap:** `anExceptionFromReorgDetectorDoesNotPropagateAndTheNextTickSelfHeals` exercises the new outer guard around `checkForReorg`. There is no test that an exception from `pollFinalityFor` is also caught and does not silence `checkForReorg` on the next tick.
- **Why it matters:** Phase 9 added independent guards for both halves; the test suite only verifies one half.
- **Suggested test:** Stub `quorumDecisionService.evaluate` for `FINALITY` to throw a `RuntimeException` on the first tick, then set up normal finality on the second tick; assert `checkForReorg` still runs and `finalized` is emitted on the second tick.

## 10. No test covers `ReorgDetector.reorg` with a `DataIntegrityViolationException` that is not a duplicate-key violation

- **Gap:** `aDuplicateKeyViolationIsSwallowedRatherThanPropagated` throws a generic `DataIntegrityViolationException("duplicate key")`; `aNonDuplicateKeyRuntimeExceptionIsNotSwallowed` uses `IllegalStateException`. There is no test that a `DataIntegrityViolationException` whose root cause is *not* a unique-key violation is propagated.
- **Why it matters:** The implementation swallows the entire `DataIntegrityViolationException` superclass. The frozen brief (and T17's identical constraint) says the catch must not mask other causes.
- **Suggested test:** Throw a `DataIntegrityViolationException` wrapping a non-unique-key constraint violation and assert it is propagated. If `outbox` cannot produce such an exception today, document the assumption in the test's Javadoc.

## 11. No test verifies `checkForReorg` records `recordHealthy` on successful `getTx`

- **Gap:** `checkForReorgIsANoOpOnTheTickAfterTheCursorIsAlreadyInvalidated` uses `recordHealthy` counts as a proxy for "no further work," but no test explicitly asserts that a successful `getTx` during reorg checking marks the provider healthy.
- **Why it matters:** The implementation explicitly calls `providerHealthTracker.recordHealthy(...)` after a successful `getTx`, matching the finality-poll pattern. This behavior is untested in isolation.
- **Suggested test:** In the happy-path still-exists case, verify `providerHealthTracker.recordHealthy("ETHEREUM", "provider-a")` is called.

## 12. No test exercises `checkForReorg` with a provider returning the wrong `txHash`

- **Gap:** The production code's Javadoc (T18 Phase 9) documents that `getTx(txHash)`'s returned `TxResult.txHash()` is trusted without cross-check. There is no test asserting this trust boundary or the absence of a defensive check.
- **Why it matters:** A defensive-check regression (or its absence) is part of the method's contract and should be locked in by a test, even if the current behavior is to trust the adapter.
- **Suggested test:** Either (a) assert that a `TxResult` with a mismatched `txHash` is used as-is, or (b) if a defensive check is added, assert it treats that provider as failed for the tick.

---

(End of test review. Gaps are for human fold-in or Phase 12 test augmentation.)
