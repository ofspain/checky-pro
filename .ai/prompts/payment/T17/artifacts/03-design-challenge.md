# payment · T17 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T17/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` §4a/§4c/§6, `spec/payment-service/agents.md` (L1/L2/L12/package rules), `ChainEventConsumer.java`, `PaymentService.java`, `InvoiceService.java`, `PaymentRepository.java`, `InvoiceRepository.java`, `PaymentStateMachine.java`, `FinalizationOutcome.java`, `InvoiceWatchView.java`, `ReceiptPayload.java`, `ReceiptDigest.java`, `CryptoClient.java`, `AttestationResult.java`, `VerificationRecordService.java`.

---

## Executive summary

The brief is directionally correct: `ReceiptService` in a new `receipt/` module, no entity imports, the `attest()` call outside `onChainTxFinalized`'s main transaction, and `BLOCKED` moving both aggregates to `HELD`. The two big holes are **how `ReceiptService` reads the 12 receipt fields without violating L12** (the brief does not specify the cross-module read API) and **how the two `HELD` updates stay atomic** so a race cannot leave the payment HELD while the invoice remains PAID, or vice versa. Several smaller gaps (audit record for the HELD transition, return type for the SIGNED path, explicit 409-vs-BLOCKED distinction) should be locked before implementation.

---

## Finding 1 (High) — `ReceiptService` has no defined way to read the 12 receipt fields without breaking L12

- **Issue:** The brief says `ReceiptService` takes only a `Long paymentId` (or equivalent primitive) and must import neither `Payment` nor `Invoice`. To build a `ReceiptPayload` it needs `paymentUuid`, `invoiceUuid`, `merchantAccountUuid`, `chain`, `txHash`, `tokenContractAddress`, `tokenDecimals`, `amount`, `payToAddress`, `fromAddress`, and `finalizedAt`. Those fields are split across the `Payment` and `Invoice` tables, and both repositories are package-private interfaces in other feature modules. The existing cross-module projection (`InvoiceWatchView`) lacks `merchantAccountUuid`, `tokenDecimals`, `paymentUuid`, `fromAddress`, and `finalizedAt`.
- **Impact:** An implementer will either (a) import `Payment`/`Invoice` entities or repositories directly, failing `ArchitectureTest`, or (b) discover mid-implementation that new service methods are required and have to change the brief retroactively.
- **Evidence:**
  - `PaymentRepository` is `interface PaymentRepository` with default (package) visibility in `com.themistra.payment.payment`.
  - `InvoiceRepository` is likewise package-private in `com.themistra.payment.invoice`.
  - `ArchitectureTest.FEATURE_MODULES` and `shouldPreventCrossModuleEntityImports` enforce L12 at build time.
  - `ReceiptPayload` needs `merchantAccountUuid` and `tokenDecimals`, neither of which is in `InvoiceWatchView`.
- **Recommendation:** Add two focused, read-only cross-module service methods returning plain records (never entities):
  - `PaymentService.findReceiptPrimitives(Long paymentId)` → a `PaymentReceiptView` record containing at least `paymentUuid`, `invoiceId`, `chain`, `txHash`, `tokenContractAddress`, `amount`, `fromAddress`, `finalizedAt`, and `state`.
  - `InvoiceService.findReceiptPrimitives(Long invoiceId)` → an `InvoiceReceiptView` record containing at least `invoiceUuid`, `merchantAccountUuid`, `tokenDecimals`, `payToAddress`, and `state`.
  
  Both methods should be `@Transactional(readOnly = true)` and use scalar projections or in-module entity-to-record mapping so the entity never leaves its own module. `ReceiptService` then constructs `ReceiptPayload` from these two records plus a fresh `UUID.receiptUuid()`.
- **Confidence:** High.

---

## Finding 2 (High) — The two `HELD` updates must be all-or-nothing

- **Issue:** The brief proposes a second `TransactionTemplate.execute(...)` scoped to "just the two HELD updates" and mentions new repository methods `markHeldIfFinalized` and `markHeldIfPaid`. It does not say what happens if one update succeeds and the other does not.
- **Impact:** A narrow race (e.g., a concurrent reorg moves the payment out of `FINALIZED` between the `attest()` call and the HELD write) could leave the system in a split state: payment `HELD` / invoice `PAID`, or payment `FINALIZED` / invoice `HELD`. That violates R20's semantics and complicates ops recovery.
- **Evidence:**
  - `PaymentStateMachine` only permits `FINALIZED → HELD`; an atomic `UPDATE ... WHERE state = 'FINALIZED'` is the only enforcement.
  - `InvoiceRepository.updateStateIfCurrentState` already exists as a generic atomic updater, but the brief does not state that the HELD transition must use it conditionally.
  - `ChainEventConsumer.onChainTxReorged` shows the accepted pattern: a committed DB step, then a best-effort post-commit step. Here the post-commit step itself writes to two tables.
- **Recommendation:** Inside the second `TransactionTemplate.execute(...)`:
  1. Call `PaymentService.markHeldIfFinalized(paymentId, blockedReason, now)` and `InvoiceService.markHeldIfPaid(invoiceId, now)`.
  2. If **both** return `true`, commit.
  3. If **both** return `false`, log at `WARN` (already moved or race made ineligible) and commit the no-op.
  4. If **exactly one** returns `true`, throw an `IllegalStateException` so the transaction rolls back both partial updates. This is a data-inconsistency bug, not a business outcome.
  
  The exception should be caught by `ReceiptService` outside the `TransactionTemplate` and logged at `ERROR` without propagating to `ChainEventConsumer` (so Kafka offset commit is not blocked by an already-committed first transaction).
- **Confidence:** High.

---

## Finding 3 (Medium) — The pre-`attest()` guard must re-read current state; the first transaction's outcome is stale after it commits

- **Issue:** The brief says `ReceiptService` guards on `FINALIZED` + `PAID`, but the first transaction only guarantees those states at the moment it commits. Between that commit and the `attest()` call, a `chain.tx.reorged` can move the payment back to `SEEN`/`WATCHING`, or (theoretically) an invoice sweep could move the invoice to `EXPIRED`. The first transaction's `FinalizationOutcome` does not protect the post-commit window.
- **Impact:** A payment that is no longer `FINALIZED` could still be sent to `/attest`, wasting the call and producing a signature for a reorged transaction. Worse, a BLOCKED result could then attempt the HELD transition on a non-`FINALIZED` payment.
- **Evidence:**
  - `PaymentService.applyReorged` is a separate Kafka listener; there is no ordering guarantee relative to the post-commit step of `onChainTxFinalized`.
  - The atomic `UPDATE ... WHERE state = 'FINALIZED'` in `markHeldIfFinalized` is the *only* enforcement if the re-read is skipped.
- **Recommendation:** `ReceiptService.issueIfEligible` should call `PaymentService.findReceiptPrimitives(paymentId)` and `InvoiceService.findReceiptPrimitives(invoiceId)` *before* calling `attest()`, and return `ReceiptIssueResult.NotEligible` unless `payment.state == FINALIZED` and `invoice.state == PAID`. This is a cheap read-only guard. The atomic HELD updates remain the final enforcement.
- **Confidence:** Medium.

---

## Finding 4 (Medium) — The HELD transition should leave an audit trail

- **Issue:** Every other payment state transition (`SEEN`, `CONFIRMING`, `FINALIZED`, reorg reversals) writes a `VerificationRecord`. The brief does not mention one for `FINALIZED → HELD`. R20 says no event is emitted, but `VerificationRecord` is internal audit, not an external event.
- **Impact:** A compliance/ops investigator has no record of *why* a payment moved to `HELD` or what the `BLOCKED` reason was, other than an application log line that may have rotated away.
- **Evidence:**
  - `VerificationRecordService.record` already supports a `detail` map and a `triggerEventKey`.
  - `PaymentService.applyFinalized` records `FINALIZED` and publishes `payment.finalized` through the outbox.
  - `VerificationRecord` is payment-scoped, so the invoice `PAID → HELD` transition cannot be recorded there; that is a separate limitation already true for all invoice state changes.
- **Recommendation:** Record a `VerificationRecord` for the payment's `FINALIZED → HELD` transition, with `detail` containing the `BLOCKED` reason and the original `chain.tx.finalized` idempotency key. The most natural owner is `PaymentService.markHeldIfFinalized(..., String blockedReason, String triggerEventKey, Instant now)`, mirroring how `applyFinalized` records its own transition. This keeps `VerificationRecordService` inside `payment/` and avoids forcing `ReceiptService` to import a third feature module.
- **Confidence:** Medium.

---

## Finding 5 (Medium) — The return type should carry the `SIGNED` result and a stable `receiptUuid`

- **Issue:** The brief says the `SIGNED` path does not persist anything in this task and leaves the exact return type as "void or a sealed result the caller logs." If `issueIfEligible` returns `void`, T18/T19 will have no access to the `receiptUuid`, signature, `kmsKeyId`, and `signedAt` produced here without re-invoking `/attest` or changing the interface.
- **Impact:** A void return now creates a breaking refactor later and loses the only in-memory copy of the signed data before T18/T19 exist.
- **Evidence:**
  - `AttestationResult.Signed` already carries `signature`, `kmsKeyId`, and `signedAt`.
  - `ReceiptPayload.receiptUuid` must be the same UUID used for the digest and for the eventual `Receipt` row.
  - `CryptoClient.attest` is not idempotent across calls: each call produces a fresh signature for the digest it receives.
- **Recommendation:** Return a sealed `ReceiptIssueResult`:
  - `Signed(UUID receiptUuid, AttestationResult.Signed signed)`
  - `Blocked(String reason)`
  - `NotEligible`
  - `Failed(String reason)`
  
  `ChainEventConsumer` can log and ignore it for now; T18/T19 will consume `Signed` directly.
- **Confidence:** Medium.

---

## Finding 6 (Low/Medium) — `AttestationRefusedException` (409) must not be confused with `BLOCKED`

- **Issue:** The brief groups "transport/refused failure (`CryptoServiceUnavailableException`/`AttestationRefusedException`)" together and says both are logged and swallowed. The `/attest` contract distinguishes a `200 BLOCKED` (compliance hit → move to `HELD`) from a `409` (upstream signing gate not satisfied → do *not* move to `HELD`).
- **Impact:** If a 409 is accidentally handled like a `BLOCKED` outcome, payments would be moved to `HELD` for transient or precondition failures, violating R20 and `PaymentStateMachine` semantics.
- **Evidence:**
  - `CryptoClient.attest` throws `AttestationRefusedException` on `HttpClientErrorException.Conflict` (409).
  - It returns `AttestationResult.Blocked` only for a `200` body with `"outcome": "BLOCKED"`.
- **Recommendation:** Catch both exceptions, but handle them differently:
  - `AttestationResult.Blocked` → perform the `HELD` transition.
  - `AttestationRefusedException` (409) → log at `WARN` and return `ReceiptIssueResult.Failed`; no state change.
  - `CryptoServiceUnavailableException` → log at `ERROR` and return `ReceiptIssueResult.Failed`; no state change.
  
  Update AC6 wording to make this explicit.
- **Confidence:** Medium.

---

## Finding 7 (Low) — `onChainTxFinalized` restructuring must keep invoice resolution and outcome update inside the same transaction

- **Issue:** The brief mirrors `onChainTxReorged`'s `TransactionTemplate` split but does not explicitly state that `resolveInvoiceForFinalization`, `applyFinalized`, and `markOutcomeIfOpenOrExpired` must all run inside the same `TransactionTemplate.execute(...)` block.
- **Impact:** If `resolveInvoiceForFinalization` is left outside the block, it would execute even for duplicate events (wasting work) and would not share the same transactional snapshot as the updates.
- **Evidence:**
  - The current `onChainTxFinalized` is `@Transactional` at the method level; the entire body shares one transaction.
  - `dedupe(...)` must be the first operation inside the new `TransactionTemplate` block, before any reads, so a duplicate short-circuits cleanly.
- **Recommendation:** The new `TransactionTemplate` block should contain, in order: `dedupe`, `resolveInvoiceForFinalization`, `paymentService.applyFinalized`, `invoiceService.markOutcomeIfOpenOrExpired`. Return `Optional<FinalizationOutcome>` (empty for duplicate/stale). After the block commits, call `receiptService.issueIfEligible(paymentId)` only when `amountDiscrepancy == null`.
- **Confidence:** Low.

---

## Finding 8 (Low) — `ReceiptService` should own a `Clock`, not rely on `Instant.now()`

- **Issue:** The brief does not mention time for the HELD updates, but the new repository methods will need an `Instant now` for `updated_at`. Using `Instant.now()` directly would make unit tests non-deterministic.
- **Impact:** Tests would have to mock `Clock` indirectly or ignore timestamps; the codebase already uses an injected `Clock` everywhere else.
- **Evidence:** Every existing service (`PaymentService`, `InvoiceService`, `VerificationRecordService`, `ChainEventConsumer`) injects `Clock`.
- **Recommendation:** Inject `java.time.Clock` into `ReceiptService` and pass `clock.instant()` to the HELD update methods. Tests can then use a fixed clock.
- **Confidence:** Low.

---

## Finding 9 (Low) — The new repository methods should not silently become generic catch-alls

- **Issue:** `InvoiceRepository` already has a generic `updateStateIfCurrentState` method. It is tempting to reuse it for `PAID → HELD`, but doing so without an explicit service method obscures the receipt-specific business meaning.
- **Impact:** Future readers will not know that `PAID → HELD` is the receipt-blocked path unless they trace the caller.
- **Evidence:** `InvoiceRepository.updateStateIfCurrentState` is generic and used by `expireInvoice` and `markOutcomeIfOpenOrExpired` for different purposes.
- **Recommendation:** Add named methods even if they delegate:
  - `PaymentRepository.markHeldIfFinalized(Long id, Instant now)` — `UPDATE ... SET state = 'HELD', updated_at = :now WHERE id = :id AND state = 'FINALIZED'`.
  - `InvoiceRepository.markHeldIfPaid(Long id, Instant now)` — can delegate to `updateStateIfCurrentState(id, PAID, HELD, now)` or have its own named JPQL query.
  
  Wrap them in `PaymentService.markHeldIfFinalized(...)` and `InvoiceService.markHeldIfPaid(...)` so `ReceiptService` never touches repositories directly.
- **Confidence:** Low.

---

## Finding 10 (Low) — The post-commit step still blocks the Kafka consumer thread

- **Issue:** The brief correctly removes the DB transaction from around `attest()`, but the `attest()` call and any HELD write still run synchronously in the listener thread before the method returns and Kafka commits the offset.
- **Impact:** A slow or unavailable Crypto Service increases consumer lag for `chain.tx.finalized`. This is the same trade-off already accepted for `onChainTxReorged`'s replacement-watch registration (Phase 9 Finding 6), so it is not new.
- **Evidence:** `ChainEventConsumer` Javadoc explicitly accepts this for `registerReplacementWatch` and notes it should be revisited only if load testing shows real impact.
- **Recommendation:** Accept the synchronous design for T17; do not introduce an async executor or out-of-order processing that would break per-payment ordering. Document the same accepted trade-off in `onChainTxFinalized`'s updated Javadoc.
- **Confidence:** Low.

---

## Positive confirmations

- Keeping `ReceiptService` in `receipt/` with no `Payment`/`Invoice` entity imports respects L12 and matches the `receipt/` package map in `design.md` §6.
- Splitting `onChainTxFinalized` into a transactional dedupe/finalization block and a post-commit `attest()` step satisfies L2 (receipt only from `FINALIZED`) without holding a DB transaction across the network call.
- `PaymentStateMachine` already permits `FINALIZED → HELD`; no state-machine change is needed.
- Using atomic, conditional `UPDATE ... WHERE state = :expected` queries for the HELD transition preserves the lost-update-avoidance pattern established in T08/T11/T12.
- Treating `UNDERPAID`/`OVERPAID` as no-op for receipts (resolved R17/R18 scope) is consistent with `design.md` O2 and the `amount_discrepancy` model.
- `AttestationResult` is a sealed type, so `ReceiptService` cannot accidentally read `signature` from a `Blocked` outcome.
- `ReceiptDigest`/`ReceiptPayload` from T16 provide the exact digest shape and field set needed for `/attest`.
- The disclosed degradation — no retry for a failed `attest()` call — is correctly identified as out of scope, because no task in the current breakdown adds a retry job and inventing one would violate the "don't invent unspecified mechanisms" rule.

---

## Disposition summary

| Finding | Severity | Disposition |
|---|---|---|
| 1. Cross-module read API for `ReceiptService` | High | Add `PaymentReceiptView` + `InvoiceReceiptView` and service read methods |
| 2. Atomic HELD transition | High | Make the second transaction all-or-nothing; throw + rollback on a split result |
| 3. Re-read state before `attest()` | Medium | Guard in `issueIfEligible`; atomic updates remain final enforcement |
| 4. Audit record for `FINALIZED → HELD` | Medium | Record a `VerificationRecord` inside `PaymentService.markHeldIfFinalized` |
| 5. Return type carrying SIGNED result | Medium | Return sealed `ReceiptIssueResult` |
| 6. Distinguish 409 from BLOCKED | Medium | Catch separately; only BLOCKED triggers HELD |
| 7. Keep resolution/update inside one transaction | Low | Restructure `onChainTxFinalized` block explicitly |
| 8. Inject `Clock` into `ReceiptService` | Low | Use injected `Clock` for HELD updates |
| 9. Named HELD repository/service methods | Low | Add `markHeldIfFinalized`/`markHeldIfPaid` |
| 10. Synchronous post-commit step | Low | Accept; document same trade-off as reorg path |

---

## Recommended next step

Update the brief to:
1. Define `PaymentReceiptView`/`InvoiceReceiptView` and the `PaymentService`/`InvoiceService` read + HELD-transition methods.
2. Specify the all-or-nothing HELD transaction behavior and exception handling.
3. Adopt the sealed `ReceiptIssueResult` return type.
4. Clarify that 409 (`AttestationRefusedException`) is swallowed with no state change, while `200 BLOCKED` triggers HELD.
5. Add the `VerificationRecord` audit requirement for the payment's HELD transition.

Then freeze and proceed to Phase 5 implementation. Findings 1 and 2 are load-bearing; the rest are clarity/correctness improvements.
