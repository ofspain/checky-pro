# payment · T17 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T17/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `ReceiptServiceTest.java`, `PaymentServiceTest.java`, `InvoiceServiceTest.java`, `ChainEventConsumerTest.java`.

---

## Executive summary

- All test classes are present and green.
- Full payment suite: **372/372 passing** (348 baseline + 24 new tests: 14 `ReceiptServiceTest`, 5 `PaymentServiceTest`, 5 `InvoiceServiceTest`; the 3 strengthened `ChainEventConsumerTest` methods add assertions to already-counted tests).
- The manifest is complete: every acceptance criterion from the brief, the Phase 3 design-challenge findings, the Phase 7/8 self-review findings, and the null-reason regression are covered.
- Remaining gaps are small: no direct test for the widened `RuntimeException` catch on a real DB failure, no assertion that the `TransactionTemplate` actually rolls back on a split HELD result, and no end-to-end integration assertion for a real `BLOCKED` outcome.

---

## Test manifest verification

### `ReceiptServiceTest` (14 methods)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldIssueReceiptOnlyFromFinalizedState` | ✅ | AC1/R17 — `FINALIZED`+`PAID` triggers `attest` |
| `shouldNeverIssueReceiptBeforeFinalized` | ✅ | AC1/R18 — non-`FINALIZED` → `NotEligible`, no `attest`, no invoice lookup |
| `shouldReturnNotEligibleWhenInvoiceIsUnderpaidOrOverpaid` | ✅ | Resolved R17/R18 scope decision |
| `shouldReturnNotEligibleWhenPaymentDoesNotExist` | ✅ | Defensive boundary |
| `shouldReturnNotEligibleWhenInvoiceDoesNotExist` | ✅ | Defensive boundary |
| `shouldRequestKmsSignatureViaCryptoAttestEndpoint` | ✅ | AC2/R19 — 64-char hex digest passed |
| `shouldCarryTheSameReceiptUuidInTheSignedResult` | ✅ | AC4/Finding 5 — sealed result carries signed data |
| `shouldNotIssueReceiptWhenAttestReturnsBlocked` | ✅ | AC3/R20 — both HELD methods called with right args |
| `shouldReturnBlockedWithoutErrorWhenBothHeldUpdatesAreAlreadyNoOps` | ✅ | Already-resolved race returns `Blocked`, not `Failed` |
| `shouldThrowAndRollBackWhenOnlyPaymentHeldUpdateSucceeds` | ✅ | AC7/Finding 2 — split-result guard, payment-only direction |
| `shouldThrowAndRollBackWhenOnlyInvoiceHeldUpdateSucceeds` | ✅ | AC7/Finding 2 — split-result guard, invoice-only direction |
| `shouldReturnFailedWithoutStateChangeWhenAttestIsRefused` | ✅ | AC6/Finding 6 — 409 never triggers HELD |
| `shouldReturnFailedWithoutStateChangeWhenCryptoServiceIsUnavailable` | ✅ | AC6 — transport failure never triggers HELD |
| `issueIfEligibleShouldThrowNullPointerExceptionForNullArguments` | ✅ | Null-handling boundary |

### `PaymentServiceTest` new methods (5)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldReturnReceiptViewWhenPaymentExists` | ✅ | `findReceiptPrimitives` mapping |
| `shouldReturnEmptyReceiptViewWhenPaymentDoesNotExist` | ✅ | Empty boundary |
| `shouldMarkHeldAndRecordVerificationWhenFinalized` | ✅ | HELD transition + audit record |
| `shouldToleratesNullBlockedReasonWithoutThrowing` | ✅ | Phase 9 null-reason regression guard |
| `shouldNotRecordVerificationWhenNotFinalized` | ✅ | AC8 — no audit when atomic update is a no-op |

### `InvoiceServiceTest` new methods (5)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldReturnReceiptViewWhenInvoiceExists` | ✅ | `findReceiptPrimitives` mapping |
| `shouldReturnEmptyReceiptViewWhenInvoiceDoesNotExist` | ✅ | Empty boundary |
| `shouldMarkHeldWhenPaid` | ✅ | `PAID → HELD` success |
| `shouldNotMarkHeldWhenNotPaid` | ✅ | `PAID → HELD` no-op |
| `markHeldIfPaidShouldThrowNullPointerExceptionForNullArguments` | ✅ | Null-handling boundary |

### `ChainEventConsumerTest` strengthened methods (3)

| Manifest test | Present | Verifies |
|---|---|---|
| `shouldMarkInvoicePaidOnExactMatch` | ✅ | `receiptService.issueIfEligible` called with exact `paymentId`/`triggerEventKey` |
| `shouldMarkInvoiceUnderpaidOnUnderpayment` | ✅ | `receiptService.issueIfEligible` never called |
| `shouldMarkInvoiceOverpaidOnOverpayment` | ✅ | `receiptService.issueIfEligible` never called |

---

## Gap 1 (Low) — No direct test for the widened `RuntimeException` catch

- **Why it matters:** `ReceiptService.handleBlocked` was widened from `catch (IllegalStateException)` to `catch (RuntimeException)` in Phase 9. The split-result tests exercise the intentional `IllegalStateException` path, but a real JPA failure (`DataAccessException`, `TransactionSystemException`, etc.) would now also be swallowed and returned as `Failed`.
- **Suggested test:** Add a `ReceiptServiceTest` case where `paymentService.markHeldIfFinalized(...)` throws a non-`IllegalStateException` runtime exception (e.g., `new RuntimeException("db down")`) and assert the result is `Failed` and the HELD methods are not called again.
- **Confidence:** Low.

---

## Gap 2 (Low) — Split-result rollback is asserted by outcome, not by transaction action

- **Why it matters:** The two split-result tests are named `shouldThrowAndRollBack...`, but they only assert the returned `ReceiptIssueResult.Failed`. They do not verify that the `TransactionTemplate` actually rolled the partial update back.
- **Suggested test:** With a real (or spy) `TransactionTemplate`, verify that `status.setRollbackOnly()` is called, or verify on the mock `PlatformTransactionManager` that `rollback(...)` is invoked. This is awkward with an unstubbed mock `PlatformTransactionManager`, which is why the current tests stop at the outcome.
- **Confidence:** Low.

---

## Gap 3 (Low) — `SIGNED` path does not assert that HELD methods are never called

- **Why it matters:** `shouldIssueReceiptOnlyFromFinalizedState` and `shouldRequestKmsSignatureViaCryptoAttestEndpoint` verify `attest(...)` is called, but they do not explicitly verify that `paymentService.markHeldIfFinalized` and `invoiceService.markHeldIfPaid` are never called on a `SIGNED` result.
- **Suggested test:** Add `verify(paymentService, never()).markHeldIfFinalized(...)` and `verify(invoiceService, never()).markHeldIfPaid(...)` to the `SIGNED` tests.
- **Confidence:** Low.

---

## Gap 4 (Low) — `shouldCarryTheSameReceiptUuidInTheSignedResult` does not prove the digest's UUID matches the result's UUID

- **Why it matters:** The test asserts that the returned `receiptUuid` is non-null and that the signature is carried through, but it does not prove that the same UUID was fed into `ReceiptDigest.digest(...)`.
- **Suggested test:** Capture the digest argument in `cryptoClient.attest(...)` and re-derive the canonical JSON, or at least run the test twice and assert two different `receiptUuid`s are produced (proving it is generated per call and not a constant).
- **Confidence:** Low.

---

## Gap 5 (Low) — No integration test exercises a real `BLOCKED` outcome end-to-end

- **Why it matters:** The existing Postgres+Kafka integration test drives a payment to `FINALIZED`/`PAID` and verifies the DB state, but `CryptoClient` is mocked and `attest(...)` is not stubbed, so it returns `null` and `ReceiptService` returns `Failed`. The `BLOCKED → HELD` path and the resulting `payment.state = 'HELD'`/`invoice.state = 'HELD'` transition are not exercised against a live context.
- **Suggested test:** Stub the mock `CryptoClient` in the integration test to return `AttestationResult.Blocked(...)` for a specific payment and assert via raw JDBC that both the payment and invoice end up in `HELD`. This would close the last real end-to-end gap for T17.
- **Confidence:** Low.

---

## Positive confirmations

- Every required test from the manifest is present and named clearly.
- The split-HELD-result race is tested in both directions, not just one.
- The 409-vs-`BLOCKED` distinction is tested as two separate, clearly-named cases.
- The null-`blockedReason` regression has a dedicated test with the exact fallback text.
- `ChainEventConsumerTest` now proves the R17/R18 scope decision at the wiring level: `issueIfEligible` is called for `PAID` and never called for `UNDERPAID`/`OVERPAID`.
- `ReceiptServiceTest` is pure Mockito with no Spring context, matching the established unit-test style.
- `PaymentServiceTest` and `InvoiceServiceTest` additions stay within the existing mock-repository style.
- Full suite passes: **372/372**.

---

## Build context

- `ReceiptServiceTest`: **14/14 passing**
- `PaymentServiceTest`: **37/37 passing**
- `InvoiceServiceTest`: **29/29 passing**
- `ChainEventConsumerTest`: **27/27 passing**
- `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`: **5/5 passing**
- Full `mvn -pl services/payment -am test`: **372/372 passing**

---

## Recommended follow-up order

1. Optionally add the widened-`RuntimeException` catch test (Gap 1).
2. Optionally strengthen the split-result tests to assert rollback explicitly (Gap 2).
3. Optionally add `never()` verifications for HELD methods on the `SIGNED` path (Gap 3).
4. Optionally add a digest-capture or uniqueness assertion for `receiptUuid` (Gap 4).
5. Optionally extend the existing integration test to cover a real `BLOCKED` outcome end-to-end (Gap 5).
