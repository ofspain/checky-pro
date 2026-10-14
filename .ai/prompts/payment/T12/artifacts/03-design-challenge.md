# payment · T12 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T12/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` L1/L11/L14, `spec/payment-service/requirements.md` R11/R12/R14, `spec/payment-service/tasks.md` T12, `contracts/events/chain/tx-reorged.v1.schema.json`, `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`, `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`, `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceWatchView.java`, `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`.

---

## 1. Reorg to `WATCHING` collides with T11's existing-non-terminal-payment guard

- **Issue:** Decision 1 correctly registers a fresh watch after `SEEN → WATCHING` so a replacement transaction can be detected. However, T11's `PaymentService.applySeen` refuses to create a new `Payment` whenever **any** non-terminal payment row already exists for the invoice (`existsByInvoiceIdAndStateIn(..., {CREATED, WATCHING, SEEN, CONFIRMING})`). After a reorg to `WATCHING`, the existing reorged payment row is itself in `WATCHING`, so the next legitimate `chain.tx.seen` for the replacement transaction will be dropped. The invoice is then deadlocked: it has a `WATCHING` payment that will never finalize, the sweep excludes `WATCHING` payments from expiry, and no new payment can be created.
- **Severity:** Critical — Decision 1's stated purpose (giving the invoice a real chance to resolve) is defeated by the guard it does not account for.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:21-30` — rationale for re-registering a watch
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:76-112` — T11's existing-payment guard checks all four non-terminal states
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java:31-40` — sweep excludes any invoice with a `WATCHING` payment
- **Recommended brief amendment:** Distinguish "active" non-terminal payments from "reorged-back" ones:
  1. Add `reorged_at TIMESTAMPTZ` population to the `SEEN → WATCHING` reversal.
  2. Change the `applySeen` guard to block new payments only when an active non-terminal row exists: `state IN (CREATED, SEEN, CONFIRMING)` **OR** `state = WATCHING AND reorged_at IS NULL`.
  3. Decide whether the expiry sweep should treat a `WATCHING` row with non-null `reorged_at` as in-flight (keep invoice OPEN for replacement) or as eligible for expiry after the original `expires_at` passes. If the latter, update `findExpiredCandidatesWithoutInFlightPayment` accordingly.
- **Confidence:** High.

---

## 2. The `reorged_at` column exists but is not used

- **Issue:** `payments.payments.reorged_at` is in the baseline schema but the brief never mentions setting it. Beyond being useful audit data, it is likely necessary to implement the guard refinement recommended in Finding 1.
- **Severity:** Medium — functional gap now, and a prerequisite for fixing Finding 1.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:47` — `reorged_at TIMESTAMPTZ`
  - `artifacts/02-task-implementation-brief.md:96-97` — state changes listed do not include `reorged_at`
- **Recommended brief amendment:** Update both reversal queries (`markSeenIfConfirming` and `markWatchingIfSeen`) to set `reorged_at = :now`. Add a test asserting `payment.getReorgedAt()` is non-null after reversal.
- **Confidence:** High.

---

## 3. `registerWatch` failure leaves the invoice stuck with no recovery path

- **Issue:** Decision 2 deliberately does not roll back the DB reversal if re-registration fails. This is correct for preserving the reorg fact, but it leaves the payment in `WATCHING` with no active watch. Because of Finding 1, even if the guard is refined, a re-registration failure means no replacement can be detected. The payment (and invoice) is permanently stuck unless an operator intervenes.
- **Severity:** Medium — operational risk.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:44-55` — registerWatch failure logged and swallowed
  - `artifacts/02-task-implementation-brief.md:32-37` — original `expires_at` may already be in the past, causing registerWatch rejection
- **Recommended brief amendment:** Make this an explicit product decision in the brief. Options:
  1. **Accept stuck state + alert:** document that a re-registration failure requires manual intervention; emit a metric/alert.
  2. **Transition invoice to `EXPIRED`:** if registerWatch fails because `expiresAt` is in the past, the invoice's lifetime is over; expire it. This would require updating the payment/invoice state, which conflicts with "don't roll back the reversal." A cleaner variant: transition the payment to `HELD` (L1 allows `any -> HELD` per design.md, though the T09 brief narrowed it) and emit an ops alert.
  3. **Extend expiry:** not recommended (Q3 fixed 60 minutes).
  Pick one and add the corresponding test.
- **Confidence:** Medium.

---

## 4. Layering: `registerWatch` should live in `ChainEventConsumer`, not `PaymentService`

- **Issue:** The brief lists `CryptoClient.registerWatch` as a dependency of this task but does not state which layer calls it. If `PaymentService.applyReorged` invokes `CryptoClient`, the `payment` module gains a dependency on an outbound REST client and on `InvoiceService.updateWatchId`, making the service responsible for both DB state and external side effects. Keeping the side effect in the consumer mirrors T11's design (where `unregisterWatch` is called by the consumer after `applySeen`) and leaves `PaymentService` as a pure payment-state coordinator.
- **Severity:** Low/Medium — architectural consistency.
- **Evidence:**
  - T11 pattern: `ChainEventConsumer.onChainTxSeen` calls `paymentService.applySeen(...)` and then `cryptoClient.unregisterWatch(...)`.
  - `artifacts/02-task-implementation-brief.md:106-107` — files to modify include both `ChainEventConsumer` and `PaymentService`.
- **Recommended brief amendment:** Have `PaymentService.applyReorged(...)` return a result indicating whether the reversal happened and which transition occurred (e.g., `ReorgResult{paymentId, invoiceId, newState, reorged}`). `ChainEventConsumer` then decides whether to call `registerWatch` and `invoiceService.updateWatchId`. This keeps the DB reversal in one transaction and the best-effort REST call in the consumer.
- **Confidence:** Medium.

---

## 5. `InvoiceService.updateWatchId` should be an atomic, conditional update

- **Issue:** The brief says to update `Invoice.watchId` after a successful `registerWatch`. If this is done via a naive entity load/save or an unconditional `UPDATE`, it could overwrite a concurrent invoice-state change (e.g., the expiry sweep transitioning the invoice to `EXPIRED` in the same moment).
- **Severity:** Low — same lost-update category T08 fixed.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java:42-53` — T08 established atomic conditional updates for `Invoice`
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:36-42` — no state mutators; all updates are atomic
- **Recommended brief amendment:** Implement `updateWatchIdIfOpen(Long invoiceId, UUID newWatchId)` as `UPDATE invoices SET watch_id = :newWatchId WHERE id = :id AND state = 'OPEN'`. Return rows updated; if zero, log a warning and do not commit the new watchId (the invoice is no longer eligible).
- **Confidence:** High.

---

## 6. No validation that the reorg event matches the invoice/payment

- **Issue:** The reorg event carries `chain` and `tokenContractAddress`. The consumer should at minimum verify these match the invoice/payment before reversing state. A mismatched event (producer bug or malicious message) could reverse the wrong payment.
- **Severity:** Low — trust-boundary hardening.
- **Evidence:**
  - `contracts/events/chain/tx-reorged.v1.schema.json:7-16`
  - `services/payment/src/main/java/com/themistra/payment/payment/Payment.java:62-78` — `Payment` already stores `chain` and `tokenContractAddress`
- **Recommended brief amendment:** In `PaymentService.applyReorged`, after finding the payment by `(chain, txHash)`, assert `payment.getChain().equals(event.chain())` and `payment.getTokenContractAddress().equals(event.tokenContractAddress())`; if not, log `ERROR` and treat as stale/ignored. The `InvoiceWatchView` widening should include `chain` and `tokenContractAddress` for the same check on the invoice side.
- **Confidence:** Low.

---

## Positive confirmations

- Both reversal edges (`CONFIRMING → SEEN`, `SEEN → WATCHING`) already exist in `PaymentStateMachine` (L1) and do not need to be reimplemented.
- Dedupe-first inside the same transaction is the right continuation of L5/R13.
- Correlating the reorg event by `watchId` is consistent with the other three consumed event schemas.
- Reusing the invoice's original `expires_at` for re-registration is consistent with the fixed 60-minute TTL decision.
- Widening `InvoiceWatchView` instead of passing the real `Invoice` entity preserves L12.
- L11 is trivially satisfied because no receipt mechanism exists yet; a comment/test documenting this is sufficient.
