# payment · T13 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T13/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `PaymentServiceTest.java`, `InvoiceServiceTest.java`, `ChainEventConsumerTest.java`.

---

## Executive summary

- `PaymentServiceTest` — **32/32 passing** (7 new T13 tests). Covers discrepancy classification, address-poisoning surfacing, `FinalizationOutcome.invoiceId`, token mismatch, and the stable invoice-id lookup.
- `InvoiceServiceTest` — **24/24 passing** (5 new T13 tests). Covers `OPEN`→outcome, `EXPIRED` fallback, no update for other states, null args, and invalid-state rejection.
- `ChainEventConsumerTest` — **27/27 passing** (7 new T13 tests). Covers unknown payment, missing invoice, empty outcome, paid/under/over mapping, and the "already moved on" log path.
- Full non-Docker suite — **265/265 passing**.
- Remaining gaps are integration-level: real repository query execution and end-to-end state changes, which remain deferred due to Docker unavailability.

---

## Gap 1 — `amount_discrepancy` column mapping is not exercised against a real database

- **Why it matters:** `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` verifies the repository method is called with the correct discrepancy string, but the actual JPQL `p.amountDiscrepancy = :amountDiscrepancy` and the column mapping have never executed against Hibernate/Postgres. A typo in the property name or a scale/type mismatch would only surface at runtime.
- **Suggested test:** Add a Testcontainers test that seeds an invoice and a `SEEN`/`CONFIRMING` payment, calls `PaymentService.applyFinalized` with an under/over/exact amount, and asserts the persisted `amount_discrepancy` value matches.
- **Confidence:** Medium.

---

## Gap 2 — `markOutcomeIfOpenOrExpired`'s `updatedAt` and exact SQL are not verified at runtime

- **Why it matters:** The unit tests mock `InvoiceRepository.updateStateIfCurrentState`. The real atomic update sets `state` and `updated_at` in one query. A regression that dropped `updatedAt` or changed the expected-state parameter would not be caught.
- **Suggested test:** Add a Testcontainers test that creates an `OPEN` invoice, calls `markOutcomeIfOpenOrExpired`, and asserts the row now has the expected state and a non-null `updated_at`. Repeat from `EXPIRED` to verify the fallback path.
- **Confidence:** Medium.

---

## Gap 3 — No end-to-end test through the consumer for a real finalized event

- **Why it matters:** The consumer tests mock `paymentService.applyFinalized` and return a pre-canned `FinalizationOutcome`. They prove mapping and dispatch, but they do not prove that a real `ChainTxFinalized` JSON payload with a non-matching amount actually results in `InvoiceState.UNDERPAID`.
- **Suggested test:** Once Docker is available, add a Testcontainers + Kafka integration test that creates an invoice, emits `chain.tx.seen` + `chain.tx.finalized` (with amount < invoice amount), and asserts both the payment row and the invoice row end in the expected states.
- **Confidence:** Medium.

---

## Gap 4 — `shouldLogWhenInvoiceOutcomeIsNotApplied` only asserts no exception

- **Why it matters:** The test verifies the method does not throw when `markOutcomeIfOpenOrExpired` returns `false`, but it does not verify the expected warning log message is emitted. A silent no-op would also pass.
- **Suggested test:** Capture logs with `@ExtendWith(OutputCaptureExtension.class)` or a `ListAppender` and assert the log contains the invoice id and a phrase like "was not OPEN or EXPIRED".
- **Confidence:** Low.

---

## Gap 5 — No negative test for `toInvoiceState`'s default throw

- **Why it matters:** The `switch` default throws `IllegalStateException` for an unexpected discrepancy string. Because `classifyDiscrepancy` only produces three values, this path is unreachable today, but a future refactor that introduces a new discrepancy value without updating the consumer would hit it at runtime.
- **Suggested test:** Add a package-private or reflection-based test that invokes `ChainEventConsumer.toInvoiceState("UNKNOWN")` and asserts `IllegalStateException`.
- **Confidence:** Low.

---

## Gap 6 — `findInvoiceIdByChainAndTxHash` dedupe-skip path is not tested for finalized

- **Why it matters:** For `chain.tx.reorged`, there is an explicit dedupe test. For `chain.tx.finalized`, the consumer test suite does not include a test that proves `findInvoiceIdByChainAndTxHash` is skipped when `dedupe` returns 0. The pattern is identical, but an explicit test would guard against a future regression in this listener.
- **Suggested test:** Add `shouldDedupeDuplicateChainEventsByEventKeyOnFinalized` mirroring the existing reorg test.
- **Confidence:** Low.

---

## Positive confirmations

- `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` directly asserts `UNDERPAID`, `OVERPAID`, and exact-match (`null`) in one test.
- `shouldSurfaceAddressPoisoningFlagFromObservation` verifies both persistence and outbox surfacing of the flag.
- `shouldCarryTheInvoiceIdOnFinalizationOutcome` proves the stable foreign key flows to the consumer.
- `shouldIgnoreFinalizedEventWhenTokenContractAddressMismatchesTheStoredPayment` closes Phase 9 Finding 3.
- `findInvoiceIdByChainAndTxHashShouldReturnTheInvoiceIdOfAKnownPayment`/`...EmptyForAnUnknownPayment` cover the stable lookup passthrough.
- `shouldMarkOutcomeFromExpiredWhenOpenAttemptFails` directly covers the Phase 3 Finding 1 widening.
- `markOutcomeIfOpenOrExpiredShouldRejectAnyStateNotPaidUnderpaidOrOverpaid` covers the defensive guard.
- `shouldMarkInvoicePaidOnExactMatch`/`...Underpaid...`/`...Overpaid...` cover the consumer-side three-way mapping.
- The full non-Docker suite passes at 265/265.

---

## Recommended follow-up order

1. Add the missing dedupe-skip test for finalized (Gap 6) and the log-capture test (Gap 4).
2. Add the `toInvoiceState` default-throw test (Gap 5).
3. Add Testcontainers integration tests for `amount_discrepancy` persistence and `markOutcomeIfOpenOrExpired` SQL (Gaps 1 and 2) once Docker is available.
4. Add an end-to-end Kafka + Postgres integration test for the finalized → invoice-outcome flow (Gap 3).
5. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
