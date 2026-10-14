# payment · T13 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T13/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `PaymentService.java`, `PaymentRepository.java`, `ChainEventConsumer.java`, `FinalizationOutcome.java`, `InvoiceService.java`, `InvoiceState.java`.

---

## Executive summary

- T13 production code correctly implements the amount-discrepancy computation, persists it in the same atomic `FINALIZED` update, transitions `Invoice.state` from `OPEN` or `EXPIRED` to `PAID`/`UNDERPAID`/`OVERPAID`, and preserves L12 by keeping `PaymentService` free of `InvoiceService`/`CryptoClient` dependencies.
- The non-Docker suite is green (**246/246**). The unrelated T08 `InvoiceRepositoryIntegrationTest` still fails when included.
- Findings are low severity: readability, a missing defensive validation, and a documentation note about the first-level cache.

---

## 1. `onChainTxFinalized` is dense and could be flattened with a helper

- **Issue:** Confirms self-review Finding 1. The method now performs four logical steps inline: deserialize/dedupe, resolve `invoiceId`, resolve `InvoiceWatchView`, call `applyFinalized`, call `markOutcomeIfOpenOrExpired`. Each step has its own early-return/log block. This is correct but harder to scan than the other listeners.
- **Severity:** Low — readability only.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:138-168`
- **Recommendation:** Extract a small private method such as `Optional<InvoiceWatchView> resolveInvoiceForFinalization(ChainTxFinalized event)` that encapsulates the `findInvoiceIdByChainAndTxHash` + `findByIdAsWatchView` + logging. The listener body then becomes a flat 3-step pipeline: resolve invoice → apply finalized → mark outcome.
- **Confidence:** High.

---

## 2. `findInvoiceIdByChainAndTxHash` should document the first-level-cache behavior

- **Issue:** Confirms self-review Finding 2. The two `(chain, txHash)` lookups inside the same transaction issue two SQL round-trips but return the same managed `Payment` instance. This is safe for `invoiceId` (immutable), but a future reader might assume the second read refreshes mutable fields.
- **Severity:** Low — documentation only.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:179-184`
- **Recommendation:** Add a one-line comment on `findInvoiceIdByChainAndTxHash` noting that `applyFinalized`'s subsequent `findByChainAndTxHash` will return the same managed instance, and that this is safe only because `invoiceId` is immutable.
- **Confidence:** High.

---

## 3. No validation that the finalized event's token matches the invoice's token

- **Issue:** `applyFinalized` now receives the full `InvoiceWatchView`, including `tokenContractAddress`, but it only uses `invoice.amount()` to compute the discrepancy. It does not verify that `event.tokenContractAddress()` equals `invoice.tokenContractAddress()`. A producer bug that sent a finalized event for the wrong token (same chain/address) would compute a discrepancy against the invoice's token amount while the actual transferred token was different. The payment row's `tokenContractAddress` is never updated after `createSeen`, so the row would remain internally inconsistent.
- **Severity:** Low — trust-boundary hardening.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:205-215`
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceWatchView.java:22-23`
- **Recommendation:** Add a defensive check in `applyFinalized` after loading the payment:
  ```java
  if (!invoice.tokenContractAddress().equals(payment.getTokenContractAddress())) {
      log.error(...);
      return Optional.empty();
  }
  ```
  This treats a token mismatch as a stale/malicious event, mirroring the T12 reorg token-mismatch guard.
- **Confidence:** Medium.

---

## 4. `markOutcomeIfOpenOrExpired` widened to `EXPIRED` closes the post-expiry gap correctly

- **Issue:** Not a finding — positive confirmation. The Phase 4 brief widened `markOutcomeIfOpen` to `markOutcomeIfOpenOrExpired`, trying `OPEN` then `EXPIRED`. This directly addresses the Phase 3 Finding 1 concern about post-expiry finalization leaving invoices stuck in `EXPIRED`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:178-189`
- **Confidence:** High.

---

## 5. `amountDiscrepancy` remains a bare `String`

- **Issue:** Not a new finding — Phase 3 Finding 3 was raised and evidently not adopted. The code is consistent and well-tested, but a small enum would still be safer for future maintainers.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/FinalizationOutcome.java:10-14`
  - `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:173-182`
- **Recommendation:** Consider introducing an `AmountDiscrepancy` enum in a future refactoring pass. Not required for correctness.
- **Confidence:** Low.

---

## 6. `PaymentFinalizedPayload` still does not carry `amountDiscrepancy`

- **Issue:** Not a new finding — Phase 3 Finding 6. Downstream consumers of `payment.finalized` still cannot tell from the event alone whether the payment was under/overpaid; they would have to read the payment row. Since the contract schemas are task 24's scope, this is a deferred design input, not a T13 bug.
- **Severity:** Low.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/event/PaymentFinalizedPayload.java:17-28`
- **Recommendation:** Add `amountDiscrepancy` to `PaymentFinalizedPayload` (and the eventual contract schema) when task 24 is implemented.
- **Confidence:** Low.

---

## 7. Build context — unrelated T08 integration test is still failing

- **Issue:** `InvoiceRepositoryIntegrationTest` fails with `TransactionRequiredException` because it calls a `@Modifying` repository method outside a transaction. This is a T08 test defect, not a T13 production defect.
- **Severity:** Medium — CI is red.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceRepositoryIntegrationTest.java`
- **Recommendation:** Add `@Transactional` to the test class or call the repository through a transactional helper.
- **Confidence:** High.

---

## Positive confirmations

- `classifyDiscrepancy` uses `BigDecimal.compareTo`, which is correct regardless of scale.
- `amountDiscrepancy` is computed once and reused across both `tryFinalizeFrom` attempts.
- `markFinalizedIfCurrentState` sets `amountDiscrepancy` atomically alongside the rest of the finalization fields.
- `PaymentService` still has no `InvoiceService` or `CryptoClient` dependency; L12 is preserved structurally.
- `FinalizationOutcome` mirrors T12's `ReorgOutcome` pattern and carries only primitives/a `String`.
- `markOutcomeIfOpenOrExpired` rejects null/non-payment outcomes with `IllegalArgumentException`.
- The invoice-state transition is in the same `@Transactional` boundary as the payment finalization.
- Correlation uses the stable `invoiceId` from the `Payment` row, not the reorg-vulnerable `watchId`.
- `ArchitectureTest` still passes with the new code.
- The non-Docker suite passes at 246/246.

---

## Recommended follow-up order

1. Extract an invoice-resolution helper in `ChainEventConsumer` (Finding 1).
2. Add the first-level-cache comment on `findInvoiceIdByChainAndTxHash` (Finding 2).
3. Consider adding the token-contract-address mismatch guard in `applyFinalized` (Finding 3).
4. Consider introducing an `AmountDiscrepancy` enum and surfacing it in `PaymentFinalizedPayload` in a future task.
5. Fix the unrelated T08 `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
