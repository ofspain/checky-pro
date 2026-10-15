# payment · T17 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T17/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `ReceiptService.java`, `ReceiptIssueResult.java`, `PaymentService.java`, `InvoiceService.java`, `PaymentRepository.java`, `InvoiceRepository.java`, `ChainEventConsumer.java`, `PaymentReceiptView.java`, `InvoiceReceiptView.java`, `AttestResponse.java`, plus full-suite test run.

---

## Executive summary

- T17 implementation compiles and the full suite is **348/348 green**.
- The design-challenge recommendations were adopted well: cross-module `PaymentReceiptView`/`InvoiceReceiptView`, atomic HELD transition with split-state rollback, fresh state re-read before `attest()`, sealed `ReceiptIssueResult`, and distinct handling of 409 vs `BLOCKED`.
- The self-review's two findings are both real and should be fixed in Phase 9.
- The largest practical gap is **test coverage**: there are no unit tests for `ReceiptService` and the existing `ChainEventConsumerTest` never verifies that `ReceiptService.issueIfEligible` is called for a `PAID` outcome or skipped for `UNDERPAID`/`OVERPAID`.
- One new correctness issue: `PaymentService.markHeldIfFinalized` will throw a `NullPointerException` if the Crypto Service returns a `BLOCKED` outcome with a null `reason`, because `Map.of` rejects null values.

---

## Confirming self-review Finding 1 (Medium) — `handleBlocked` only catches `IllegalStateException`

- **Independent assessment:** Confirmed. `ReceiptService.handleBlocked` intentionally throws `IllegalStateException` when the two HELD updates split, and the `catch (IllegalStateException e)` correctly converts that to `ReceiptIssueResult.Failed`. However, any other runtime failure from the JPA layer (`DataAccessException`, `TransactionSystemException`, constraint violations, etc.) propagates uncaught out of `handleBlocked`, out of `issueIfEligible`, and out of `onChainTxFinalized`.
- **Impact:** Because `dedupe()` already consumed the event's idempotency key in the committed first transaction, a Kafka redelivery cannot retry this step. An uncaught exception in the listener may trigger the container's error handler repeatedly for a guaranteed no-op retry, or simply leave the offset uncommitted.
- **Recommendation:** Widen the catch in `handleBlocked` to `RuntimeException` (the same shape used in `registerReplacementWatch`) and return `ReceiptIssueResult.Failed` consistently. The intentional `IllegalStateException` is still swallowed and logged; it just shares the same catch path as real infrastructure failures.
- **Confidence:** High.

---

## Confirming self-review Finding 2 (Low) — crash-before-`issueIfEligible` sub-case is not named

- **Independent assessment:** Confirmed. `ReceiptService`'s class Javadoc discloses the "failed `attest()` call" gap but does not explicitly name the narrower window: crash after the first `TransactionTemplate` block commits but before/during `receiptService.issueIfEligible(...)`. `ChainEventConsumer.onChainTxReorged`'s own Javadoc names this exact window explicitly for `registerReplacementWatch`.
- **Impact:** Low — the end behavior (no receipt, no retry) is the same; this is documentation clarity.
- **Recommendation:** Add one sentence to `ReceiptService`'s class Javadoc naming the crash-window case, mirroring the reorg Javadoc.
- **Confidence:** Low.

---

## New Finding 1 (High) — No unit tests for `ReceiptService` and no `ChainEventConsumer` wiring tests for T17

- **Issue:** A complete `ReceiptService` implementation exists but there is no `ReceiptServiceTest`. `ChainEventConsumerTest` has extensive T11/T12/T13 tests but no T17 assertions: it never verifies that `receiptService.issueIfEligible` is invoked when the finalization outcome is `PAID`, nor that it is **not** invoked for `UNDERPAID`/`OVERPAID`.
- **Evidence:**
  - `grep` for `issueIfEligible`, `findReceiptPrimitives`, `markHeldIfFinalized`, `markHeldIfPaid`, and `ReceiptIssueResult` in `src/test/java` returns zero matches.
  - `ChainEventConsumerTest.shouldMarkInvoicePaidOnExactMatch` ends after asserting `markOutcomeIfOpenOrExpired(..., PAID, ...)`; it does not verify the subsequent `ReceiptService` call.
  - `ChainEventConsumerTest.shouldMarkInvoiceUnderpaidOnUnderpayment` and `shouldMarkInvoiceOverpaidOnOverpayment` do not assert that `receiptService.issueIfEligible` was never called.
- **Impact:** The core T17 behavior (guard, digest, `attest()` call, HELD transition, exception swallowing) is unverified at the unit level. The full suite passing only proves it does not break existing code.
- **Recommendation:** In Phase 10 add:
  - A `ReceiptServiceTest` with mocked `PaymentService`, `InvoiceService`, `CryptoClient`, and a fake/fixed `TransactionTemplate`, covering:
    - eligibility guard (non-`FINALIZED` payment, non-`PAID` invoice → `NotEligible`, no `attest()`);
    - `SIGNED` outcome → `ReceiptIssueResult.Signed` carrying the same `receiptUuid`;
    - `BLOCKED` outcome → both HELD updates called, `Blocked` returned;
    - split HELD result → transaction rolled back, `Failed` returned;
    - `AttestationRefusedException` and `CryptoServiceUnavailableException` → `Failed`, no HELD updates.
  - `ChainEventConsumerTest` assertions that `receiptService.issueIfEligible` is called exactly once for `PAID` and never for `UNDERPAID`/`OVERPAID`.
- **Confidence:** High.

---

## New Finding 2 (Medium) — `BLOCKED` with a null `reason` crashes the HELD transition

- **Issue:** `PaymentService.markHeldIfFinalized` records the HELD transition with:
  ```java
  Map.of("blockedReason", blockedReason)
  ```
  `java.util.Map.of` does not permit null values. The Crypto Service's `AttestResponse` explicitly documents `reason` as nullable, and `CryptoClient` constructs `new AttestationResult.Blocked(response.reason())` without fallback. If the upstream service returns `{"outcome":"BLOCKED"}` with no `reason` field, `ReceiptService` will propagate that null into `PaymentService.markHeldIfFinalized`, which then throws `NullPointerException` inside the HELD transaction.
- **Impact:** A compliance block without a reason string (a valid contract response) causes the HELD transition to fail and roll back, leaving the payment `FINALIZED`/invoice `PAID`. Because the catch in `handleBlocked` is currently `IllegalStateException` only, the `NullPointerException` also propagates uncaught to the Kafka listener.
- **Recommendation:** Use a null-tolerant map construction in `PaymentService.markHeldIfFinalized`, e.g.:
  ```java
  Map.of("blockedReason", blockedReason == null ? "" : blockedReason)
  ```
  or a mutable `HashMap` if null values are intentionally wanted in JSONB. Also consider making `AttestationResult.Blocked` compact-constructor guard against null if the domain intends reason to always be present; but the wire contract is nullable, so the storage layer must tolerate it.
- **Confidence:** Medium.

---

## New Finding 3 (Low) — `ChainEventConsumer`'s class-level Javadoc is stale

- **Issue:** The top-of-class Javadoc still says "one `@Transactional` listener method per topic" and describes `onChainTxReorged` as "the one exception" to that rule. After T17, `onChainTxFinalized` is also not `@Transactional`.
- **Impact:** Misleading documentation for future readers; the method-level Javadoc for `onChainTxFinalized` is correct, but the class-level summary is not.
- **Recommendation:** Update the class-level Javadoc to state that two listeners (`chain.tx.finalized` and `chain.tx.reorged`) use explicit `TransactionTemplate` splits because they each need a post-commit best-effort step.
- **Confidence:** Low.

---

## New Finding 4 (Low) — `ChainEventConsumer` ignores the `ReceiptIssueResult` it receives

- **Issue:** `onChainTxFinalized` calls `receiptService.issueIfEligible(...)` and discards the returned `ReceiptIssueResult`. For `SIGNED`, `BLOCKED`, `NotEligible`, and `Failed`, nothing is logged at the consumer level.
- **Impact:** A `BLOCKED` compliance hit or a `Failed` attest is only visible via `ReceiptService`'s own logs. The consumer method that triggered the work gives no observability at its own boundary.
- **Recommendation:** Log the result at the consumer level, e.g.:
  ```java
  ReceiptIssueResult result = receiptService.issueIfEligible(o.paymentId(), event.idempotencyKey());
  log.info("Receipt issuance for paymentId={} completed with {}", o.paymentId(), result.getClass().getSimpleName());
  ```
  This is optional but consistent with how the other listeners log their outcomes.
- **Confidence:** Low.

---

## New Finding 5 (Low) — `PaymentRepository.markHeldIfFinalized` uses string literals for states

- **Issue:** The JPQL query hard-codes `'HELD'` and `'FINALIZED'` string literals instead of binding the `PaymentState` enum values. This matches the existing `InvoiceRepository.updateWatchIdIfOpen` literal pattern, so it is consistent, but it is less type-safe than parameterizing the enum.
- **Impact:** A typo would only be caught at runtime by tests; no compile-time protection. The current strings are correct.
- **Recommendation:** Optional: bind `PaymentState.FINALIZED` and `PaymentState.HELD` as parameters for compile-time safety. Not required if the project prefers the literal pattern already established in `InvoiceRepository`.
- **Confidence:** Low.

---

## Positive confirmations

- `ReceiptService` lives in `receipt/`, imports no `Payment`/`Invoice` entities, and uses the cross-module view records correctly.
- `PaymentReceiptView` and `InvoiceReceiptView` expose exactly the fields needed for `ReceiptPayload`; entities never leave their own modules.
- `PaymentStateMachine` already permits `FINALIZED → HELD`; no state-machine change was needed.
- The first `TransactionTemplate` block in `onChainTxFinalized` contains `dedupe`, resolution, `applyFinalized`, and `markOutcomeIfOpenOrExpired` in the correct order.
- `ReceiptService.issueIfEligible` re-reads fresh states before `attest()` and uses atomic conditional updates for the HELD transition.
- The split-HELD guard throws inside the `TransactionTemplate` callback, so a partial update is rolled back before it can be observed.
- `AttestationResult.Blocked` is the only outcome that reaches `handleBlocked`; `AttestationRefusedException` (409) and `CryptoServiceUnavailableException` are both caught separately and mapped to `Failed`.
- `Clock` is injected into `ReceiptService` and captured once per HELD transaction.
- `PaymentService.markHeldIfFinalized` records a `VerificationRecord` for the `FINALIZED → HELD` transition, closing the audit gap identified in Phase 3.
- Full suite passes: **348/348**.

---

## Build context

- `mvn -pl services/payment -am test`: **348/348 passing**.
- No new T17-specific unit tests are present yet; coverage is deferred to Phase 10.

---

## Recommended follow-up order

1. **Phase 9 fixes:**
   1. Widen `handleBlocked`'s catch to `RuntimeException` (self-review Finding 1).
   2. Make `PaymentService.markHeldIfFinalized` tolerate a null `blockedReason` (New Finding 2).
   3. Add the crash-window sentence to `ReceiptService`'s class Javadoc (self-review Finding 2).
   4. Update `ChainEventConsumer`'s class-level Javadoc (New Finding 3).
   5. Optionally log the `ReceiptIssueResult` in `ChainEventConsumer` (New Finding 4).
2. **Phase 10 tests:** add `ReceiptServiceTest` and the missing `ChainEventConsumerTest` assertions for T17 wiring (New Finding 1).
