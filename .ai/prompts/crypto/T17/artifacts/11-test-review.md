# crypto · T17 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T17 — Seen/confirmed/finalized emission |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the Phase 10 test suite against the frozen brief, acceptance criteria, and named tests.

---

## 1. No `Watcher`-level test proves `chain.tx.finalized` is never emitted before `chain.tx.seen`

- **Gap:** `WatcherTest.shouldEmitChainTxFinalizedOnlyAtPerChainFinality` drives the happy path, and `finalityPollNeverPersistsADecisionWhileTheLocalMajorityIsNotYetFinal` proves the not-yet-final case, but neither asserts the causal ordering of the two outbox inserts. The implementation fix (publish `seen` before `pendingFinality.add`) is present in `Watcher.java:338-341`, yet the ordering is only documented, not verified.
- **Why it matters:** AC3 says `chain.tx.finalized` is emitted "never before" finality, but the requirement pipeline also expects `chain.tx.seen` to precede `chain.tx.finalized` for the same transaction. A regression that re-orders `pendingFinality.add` and `txLifecyclePublisher.seen()` would pass the existing tests.
- **Suggested test:** Add `finalizedIsNeverPublishedBeforeSeenOutboxRowExists`: mock `TxLifecyclePublisher` to capture call order, deliver a transaction so `seen` is emitted, and assert `seen(watch, txHash, ...)` is invoked before any `finalized(...)` invocation for the same `txHash`.

## 2. Missing-cursor test has a weak assertion: it does not prove finality polling is actually skipped

- **Gap:** `logsAWarningAndSkipsFinalityPollingWhenNoChainCursorExistsAtSeenTime` asserts that `providerHealthTracker.recordUnhealthy(..., LAGGING)` is never called. It never asserts that `pendingFinality` remains empty or that `getFinalityStatus` is not invoked. If a future change populated `pendingFinality` and the adapters returned non-throwing default statuses, the test would still pass.
- **Why it matters:** Phase 8 Finding 7 / Phase 9 self-review Finding 3 explicitly required that a missing cursor must not strand finality silently. The current test gives a false sense of coverage.
- **Suggested test:** Assert directly that `pendingFinality` is empty after `seen` emission (via reflection or a package-private accessor), or verify that `pollFinality()` performs zero adapter calls when the cursor was missing.

## 3. No `Watcher`-level test covers duplicate-observation suppression for `seen` and `confirmed`

- **Gap:** `TxLifecyclePublisherTest` verifies that the publisher swallows a duplicate-key exception, but `WatcherTest` does not verify that the `Watcher` itself will not invoke `txLifecyclePublisher.seen` or `confirmed` a second time when the same observation is re-delivered or when a second watch shares the address.
- **Why it matters:** AC1/AC2 require exactly-once emission. A regression in the `evaluatedFacts` guard or in `handleSeenIfAgreed` could cause duplicate publisher calls; the publisher catch would hide the symptom at the outbox level, but the business-level guarantee would be violated.
- **Suggested test:** Deliver the same 3-provider observation twice and verify `txLifecyclePublisher.seen(...)` and `confirmed(...)` are each invoked exactly once.

## 4. No test proves `chain.tx.confirmed` is withheld when `EXISTENCE` is `HELD`

- **Gap:** `doesNotEmitConfirmedWhenConfirmationsIsHeld` uses three providers that all report `exists=true` but disagree on confirmation count. There is no test where `EXISTENCE` itself is `HELD` (e.g., two providers report `exists=true`, one reports `exists=false`) and the code therefore cannot/should not emit `confirmed`.
- **Why it matters:** AC2 says `chain.tx.confirmed` is emitted "only after `EXISTENCE` has itself agreed `true`." The structural argument (fewer than 3 `exists=true` answers means `CONFIRMATIONS` is not evaluated) is correct but untested.
- **Suggested test:** Stub `quorumDecisionService.evaluate` to return `HELD` for `EXISTENCE` and `AGREED` for `CONFIRMATIONS` is impossible because `CONFIRMATIONS` will not have 3 answers — instead, deliver two `exists=true` answers and one `exists=false` answer, and assert `txLifecyclePublisher.confirmed(...)` is never called.

## 5. No test exercises a 2-of-3 finality majority (one provider reports not-yet-final)

- **Gap:** `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` and `finalityPollNeverPersistsADecisionWhileTheLocalMajorityIsNotYetFinal` use unanimous provider answers. The local-majority logic and the subsequent `recordDisagreementsIfAny` path for a minority finality provider are not exercised.
- **Why it matters:** R10 and R5 together require that finality be reached under 2-of-3 quorum while the disagreeing provider is flagged as disagreeing. The code supports this path but it is untested.
- **Suggested test:** Script providers A and B with `isFinal=true` and provider C with `isFinal=false`; assert `txLifecyclePublisher.finalized(...)` is emitted and `providerHealthTracker.recordDisagreement("ETHEREUM", "provider-c")` is called.

## 6. No test asserts the boolean values passed to `QuorumDecisionService.evaluate` for `FINALITY`

- **Gap:** `WatcherTest` verifies that `evaluate` is called for `FactType.FINALITY` with `anyList()`, but no test captures the `ProviderAnswer<Boolean>` values to ensure they reflect each provider's `FinalityPolicy.isFinal(status)` result.
- **Why it matters:** A bug that inverted the boolean mapping or passed `true` for all providers regardless of policy would still satisfy the existing mock-based assertions.
- **Suggested test:** Use an `ArgumentCaptor<List<ProviderAnswer<Boolean>>>` in `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` and assert the captured list contains the expected `true`/`false` values per provider.

## 7. No test covers restart recovery of the in-memory pending-finality set

- **Gap:** `pendingFinality` is purely in-memory. Phase 8 Finding 2 identified that a restart after `SEEN` but before `FINALIZED` strands the transaction forever. The Phase 10 tests do not address this.
- **Why it matters:** R10 is not satisfied for transactions that were seen before a process restart. This is a durable-state gap, not just an edge case.
- **Suggested test:** This likely requires a production-code change (persist pending-finality state or reconcile from cursor on startup), so the test gap should be recorded as blocked on that change. A minimal test: simulate a fresh `Watcher` instance with a cursor that already has a `txHash` snapshot but no `lastFinalizedBlock`, and assert `pollFinality()` begins polling that `txHash`.

## 8. `TxLifecyclePublisherTest.finalizedToleratesANullAmountOnTheCursor` does not inspect the resulting payload

- **Gap:** The test only asserts that `finalized(...)` does not throw when `cursor.amount()` is `null`. It does not assert that `FinalizedPayload.amount()` is actually `null`.
- **Why it matters:** A future change that silently replaced a null amount with `"0"` or threw-and-caught internally would pass this test while violating the documented behavior (and the schema, where `amount` is required).
- **Suggested test:** Capture the published payload and assert `payload.amount()` is `null`.

## 9. No test proves the duplicate-key catch in `TxLifecyclePublisher` only swallows idempotency conflicts

- **Gap:** The frozen brief's Constraints section requires that the catch "must not mask any other `DataIntegrityViolationException` cause beyond the idempotency-key conflict." `aDuplicateKeyViolationIsSwallowedRatherThanPropagated` throws a generic `DataIntegrityViolationException` with message "duplicate key"; `aNonDuplicateKeyRuntimeExceptionIsNotSwallowed` uses `IllegalStateException`, not a different `DataIntegrityViolationException` subtype.
- **Why it matters:** The implementation catches the `DataIntegrityViolationException` superclass, so the constraint is unverified. A real non-unique constraint violation on `outbox` (should one ever be added) would be swallowed.
- **Suggested test:** If feasible, throw a `DataIntegrityViolationException` whose root cause is *not* a unique-key violation (e.g., a not-null constraint) and assert it is propagated. If `outbox` truly cannot produce such an exception today, document the assumption explicitly in the test name/Javadoc and consider narrowing the catch or re-querying.

## 10. No test covers `confirmed` majority confirmation count when providers disagree

- **Gap:** `shouldEmitChainTxConfirmedWithConfirmationCount` uses three identical confirmation counts. There is no test where two providers agree on one count and a third reports a different count.
- **Why it matters:** R9 says the event carries "the count" — implicitly the quorum-agreed count. The implementation uses `majorityValue`, but the majority-selection path is not exercised for `CONFIRMATIONS`.
- **Suggested test:** Deliver answers with confirmations `[42, 42, 99]` and assert `txLifecyclePublisher.confirmed(watch, txHash, 42)` is called.

## 11. No test covers the `FINALITY` duplicate-decision path

- **Gap:** `pollFinalityFor` catches `IllegalStateException` from a pre-existing `FINALITY` decision and removes the `txHash` from `pendingFinality`. This path is not exercised.
- **Why it matters:** A regression in the catch block (e.g., failing to remove from `pendingFinality`) would cause infinite poll ticks that throw on every subsequent evaluation.
- **Suggested test:** Pre-seed a `FINALITY` decision in `quorumDecisionService` (stub it to throw `IllegalStateException`), add the `txHash` to `pendingFinality` via reflection or by first emitting `seen`, then call `pollFinality()` and assert the `txHash` is removed from `pendingFinality` and no further adapter calls occur.

## 12. `finalizedIsWithheldWhenTheCursorSnapshotBelongsToADifferentTransaction` does not assert `lastFinalizedBlock` is unchanged

- **Gap:** The test verifies that `txLifecyclePublisher.finalized(...)` is not called and that the cursor's `txHash` remains the earlier transaction. It does not assert that `cursor.lastFinalizedBlock()` is still `null`.
- **Why it matters:** A bug that advanced `lastFinalizedBlock` even while withholding the event would corrupt the cursor and violate AC8.
- **Suggested test:** Assert `cursor.lastFinalizedBlock()` is still `null` after `pollFinality()` returns without emitting.

## 13. No test proves `TxLifecyclePublisher.confirmed` carries the full documented payload shape

- **Gap:** `confirmedPublishesWithTheTxConfirmedAggregateTypeAndTheAgreedCount` asserts `confirmations`, `chain`, and `txHash` only. It does not assert `watchId`, `invoiceUuid`, `tokenContractAddress`, `idempotencyKey`, or `occurredAt`.
- **Why it matters:** The frozen brief field-sourcing map and the deferred contract require all envelope fields. A regression that dropped `invoiceUuid` would pass the current test.
- **Suggested test:** Mirror `seenBuildsTheDocumentedPayloadShape` for `confirmed`, asserting every field.

## 14. No test proves `TxLifecyclePublisher.finalized` carries `invoiceUuid` and `occurredAt`

- **Gap:** `finalizedPublishesWithTheTxFinalizedAggregateTypeAndTheCursorSnapshot` asserts `txHash`, `tokenContractAddress`, `amount`, `fromAddress`, and `toAddress`, but not `invoiceUuid` or `occurredAt`.
- **Why it matters:** Same as Gap 13 — incomplete payload-shape coverage for `finalized`.
- **Suggested test:** Extend the existing test to assert `payload.invoiceUuid()` equals `watch.invoiceUuid()` and `payload.occurredAt()` equals the fixed clock instant.

## 15. No test covers the `HELD`/`AGREED false` path for `EXISTENCE` with a 2-false-1-true split

- **Gap:** `doesNotEmitSeenWhenExistenceAgreesFalse` uses three `exists=false` answers. It does not exercise the majority-recomputation path where two providers agree `false` and one reports `true`.
- **Why it matters:** AC1 says "never on an `AGREED false`." The implementation recomputes the majority via `majorityValue(...)` rather than trusting `existenceDecision.outcome()` alone; this path should be verified.
- **Suggested test:** Stub `quorumDecisionService.evaluate` to return `AGREED` and deliver two `exists=false` answers plus one `exists=true` answer; assert `txLifecyclePublisher.seen(...)` is never called.

---

(End of test review. Gaps are for human fold-in or Phase 12 test augmentation.)
