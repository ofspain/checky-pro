# payment · T12 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T12/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `PaymentServiceTest.java`, `ChainEventConsumerTest.java`, `ChainEventDtoDeserializationTest.java`.

---

## Executive summary

- `PaymentServiceTest` — **24/24 passing** (7 new T12 tests). Covers both reversal edges, `ReorgOutcome`, stale/unknown/mismatch cases, and null handling.
- `ChainEventConsumerTest` — **20/20 passing** (10 new T12 tests). Covers dedupe, malformed payload, no-watch for `SEEN`, invoice-state short-circuit, orphaned-watch cleanup, and both failure-swallow paths.
- `ChainEventDtoDeserializationTest` — **5/5 passing** (1 new T12 test + 1 new unknown-property rejection test from Phase 11 Gap 5).
- Full non-Docker suite — **245/245 passing**.
- The main remaining gap is that the T12-specific repository/entity integration paths are mocked, not run against a real database.

---

## Gap 1 — The reorged-`WATCHING` guard refinement is not directly tested

- **Why it matters:** The Phase 3 Finding 1 interaction between reorg and T11's existing-payment guard is the single most load-bearing design change in T12. The current tests prove that active non-terminal payments block a new `chain.tx.seen`, but they do not prove that a `WATCHING` row with a non-null `reorgedAt` **allows** a new `chain.tx.seen`. A regression that accidentally reverted `applySeen` back to the old broad guard would not fail any existing test.
- **Suggested test:** Add `shouldAllowANewSeenWhenTheOnlyNonTerminalPaymentIsAReorgedWatchingRow` to `PaymentServiceTest`. Stub `existsByInvoiceIdAndStateIn(..., ACTIVE_NON_TERMINAL_STATES)` to return `false` and `existsByInvoiceIdAndStateInAndReorgedAtIsNull(..., WATCHING_ONLY)` to return `false`, then assert `applySeen` succeeds.
- **Confidence:** High.

---

## Gap 2 — `reorgedAt` is not asserted anywhere

- **Why it matters:** The atomic reversal queries set `reorgedAt = :now`, but the unit tests only verify the returned `ReorgOutcome` and `VerificationRecord` interactions. A regression that dropped the `reorgedAt = :now` assignment from `markSeenIfConfirming`/`markWatchingIfSeen` would not be caught, and Gap 1's guard depends on that column being populated.
- **Suggested test:** Add an integration test (or a Mockito verification of the JPQL update arguments if the method signatures exposed them) that asserts `reorgedAt` is set on reversal. The cleanest path is a Testcontainers test.
- **Confidence:** Medium.

---

## Gap 3 — `existsByInvoiceIdAndStateInAndReorgedAtIsNull` is only verified via `PartTree`, not a runtime query

- **Why it matters:** The self-review verified Spring Data can parse the derived method name, but the actual SQL (especially the `reorged_at IS NULL` predicate) has never executed against Postgres. A subtle mismatch between the Java property name and the column name would only surface at runtime.
- **Suggested test:** Once Docker is available, add a repository integration test that inserts a `WATCHING` row with `reorged_at` set and another with `reorged_at` null, then asserts the derived query returns only the null one.
- **Confidence:** Medium.

---

## Gap 4 — `InvoiceService.findByIdAsWatchView` and `updateWatchIdIfOpen` are mocked, not integrated

- **Why it matters:** These are new cross-module methods introduced for T12. Their JPQL and `InvoiceWatchView` mapping are straightforward, but the mapping of `amount`/`expiresAt`/`chain`/`tokenContractAddress` has never been exercised end-to-end.
- **Suggested test:** Add a Testcontainers test that saves an `Invoice`, calls `findByIdAsWatchView`, and calls `updateWatchIdIfOpen`, asserting the returned view and the updated row.
- **Confidence:** Low/Medium.

---

## Gap 5 — No test exercises a missing required field in a reorg payload

- **Why it matters:** The test generation explicitly decided not to add missing-required-field tests because the DTOs are plain records without `@JsonProperty(required = true)`. This is a documented trade-off, but it means a producer omitting `watchId` or `txHash` would deserialize to null/primitive defaults and likely fail later with a less clear error.
- **Suggested test:** If the project later adds Bean Validation or JSON-Schema contract tests (task 24), include this case. For now, the unknown-property test (added) is the strongest guard.
- **Confidence:** Low.

---

## Gap 6 — The crash-after-commit window is not covered by a test

- **Why it matters:** The Javadoc now documents it, but there is no automated test that proves a redelivery after a committed reversal skips `applyReorged` due to dedupe. This is hard to test at the unit level because it requires simulating a crash between `transactionTemplate.execute(...)` and `registerReplacementWatch`.
- **Suggested test:** Add an integration test with an embedded Kafka/Testcontainers that processes a `chain.tx.reorged`, crashes the consumer mid-method (or mocks `registerReplacementWatch` to throw), and verifies the redelivery is a no-op.
- **Confidence:** Low — complex, low ROI.

---

## Positive confirmations

- `ReorgOutcome` carries `invoiceId`, and `shouldRegisterReplacementWatchAfterWalkingBackToWatching` proves the consumer uses it instead of the event's `watchId`.
- `shouldNotRegisterReplacementWatchWhenReorgWalksBackToSeen` proves R11's no-watch action.
- `shouldNotRegisterReplacementWatchWhenReorgIsStaleOrUnknown` proves the stale/unknown path never reaches the invoice lookup.
- `shouldSkipRegistrationWhenInvoiceIsNoLongerOpen` closes Phase 9 Finding 7.
- `shouldUnregisterOrphanedWatchWhenUpdateWatchIdFailsAfterSuccessfulRegister` closes Phase 9 Finding 3.
- `shouldSwallowACryptoServiceFailureDuringReplacementWatchRegistrationWithoutPropagating` closes Phase 9 Finding 3/AC5.
- `shouldSwallowAnUnexpectedRuntimeExceptionFromInvoiceLookupWithoutPropagating` caught a real implementation bug during test writing and now closes Phase 9 Finding 2.
- `shouldIgnoreReorgWhenTokenContractAddressMismatchesTheStoredPayment` directly exercises the only reachable mismatch clause.
- `shouldRejectAnUnrecognizedPropertyNotInTheRealSchema` closes Phase 11 Gap 5 for unknown fields.
- The full non-Docker suite passes at 245/245.

---

## Recommended follow-up order

1. Add the reorged-`WATCHING` guard-refinement test (Gap 1).
2. Add `reorgedAt` assertions (Gap 2) and a runtime query test for `existsByInvoiceIdAndStateInAndReorgedAtIsNull` (Gap 3) once Docker is available.
3. Add `InvoiceService.findByIdAsWatchView`/`updateWatchIdIfOpen` integration tests (Gap 4).
4. Keep the missing-required-field case on the task-24 contract-test backlog (Gap 5).
5. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
