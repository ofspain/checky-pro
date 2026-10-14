# payment · T13 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T13/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` O2/L6/L8/L12, `spec/payment-service/requirements.md` R15/R16, `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java`, `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceState.java`, `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`, `services/payment/src/main/java/com/themistra/payment/consumer/dto/ChainTxFinalized.java`.

---

## 1. `markOutcomeIfOpen` leaves post-expiry invoices in `EXPIRED` even after successful finalization

- **Issue:** `design.md` O2 (Q3) explicitly decided: "Post-expiry finalization: attest anyway, never `HELD` for timing." The brief's `InvoiceService.markOutcomeIfOpen` is conditional on `state = OPEN`; if the invoice was already `EXPIRED` by the time `chain.tx.finalized` arrives, the invoice stays `EXPIRED`. That leaves a `FINALIZED` (and soon `ATTESTED`) payment attached to an `EXPIRED` invoice — a confusing merchant-visible inconsistency, and arguably a violation of the "attest anyway" intent.
- **Severity:** Medium — product/state inconsistency.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:66-76` — `markOutcomeIfOpen` only updates if `OPEN`
  - `spec/payment-service/design.md:44-52` (O2) — post-expiry finalization should be attested
  - `spec/payment-service/requirements.md:11` (R5 revised) — sweep leaves in-flight invoices `OPEN`
- **Recommended brief amendment:** Decide explicitly:
  1. **Transition from `EXPIRED` too:** allow `markOutcomeIfOpen` to update from either `OPEN` or `EXPIRED` (but not `HELD` or terminal paid states). This matches the product-owner decision that post-expiry finalization is real and should complete normally.
  2. **Document the intentional inconsistency:** if the choice is to leave `EXPIRED` invoices as-is, state that the payment/receipt is the source of truth and the invoice state reflects only the expiry-sweep's last view.
- **Confidence:** High.

---

## 2. Correlating `chain.tx.finalized` by `watchId` is unnecessary and adds a fragile dependency

- **Issue:** Decision 1 argues that `watchId` cannot rotate while the payment is in `SEEN`/`CONFIRMING`, so the lookup is safe. That reasoning is likely correct today, but it is still a coupling between the consumer and the watch-rotation lifecycle. The `Payment` row found by `findByChainAndTxHash` already carries `invoiceId` (a stable foreign key). Looking up the invoice by `payment.invoiceId` via `InvoiceService.findByIdAsWatchView` avoids the watchId-staleness question entirely and removes the need for the safety argument.
- **Severity:** Low/Medium — architectural simplification and future-proofing.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:16-36` — Decision 1 and its safety rationale
  - `services/payment/src/main/java/com/themistra/payment/payment/Payment.java:59-63` — `Payment` has `invoiceId`
  - T12 introduced `InvoiceService.findByIdAsWatchView(Long)` for exactly this stable-lookup purpose
- **Recommended brief amendment:** Change `ChainEventConsumer.onChainTxFinalized` to first call `paymentService.applyFinalized(event)` (or a pre-lookup to verify the payment exists), obtain the returned `FinalizationOutcome.invoiceId()`, and use that to load the `InvoiceWatchView`. This mirrors T12's `ReorgOutcome` pattern more directly and eliminates the `watchId` lookup.
- **Confidence:** Medium.

---

## 3. `amountDiscrepancy` is modeled as a `String` rather than a small enum

- **Issue:** `FinalizationOutcome` and the `Payment.amount_discrepancy` column use the bare strings `"UNDERPAID"`/`"OVERPAID"`/null. A typo in the repository query, the consumer mapping helper, or a future caller can silently produce an invalid value. The schema already constrains it to `NULL | 'UNDERPAID' | 'OVERPAID'`, so the runtime will fail on a bad value, but a compile-time type would be safer.
- **Severity:** Low — easily testable, but a latent footgun.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:41-53` — `FinalizationOutcome` record uses `String amountDiscrepancy`
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:42` — `amount_discrepancy VARCHAR(16)`
- **Recommended brief amendment:** Introduce a small enum `AmountDiscrepancy { UNDERPAID, OVERPAID }` in the `payment` package. `FinalizationOutcome` carries `Optional<AmountDiscrepancy>` or the enum (null for exact match). The repository query maps the enum name to the column value. The consumer maps `AmountDiscrepancy` to `InvoiceState` in a type-safe switch.
- **Confidence:** Low.

---

## 4. No outbox event for the invoice-state transition

- **Issue:** The brief correctly notes that `design.md` L8's event list does not include an "invoice paid" event. However, transitioning `Invoice.state` to `PAID`/`UNDERPAID`/`OVERPAID` is a material domain event that downstream systems (tax exports, merchant notifications, reporting) may need to observe. The decision not to emit one should be explicit, not assumed.
- **Severity:** Low — scope question.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:95-96` — "No new outbox event"
  - `spec/payment-service/design.md:152-161` (L8) — event list
- **Recommended brief amendment:** Add a one-line locked note: "No outbox event is emitted for the invoice-state transition; downstream consumers must infer it from `payment.finalized` plus their own read model." If a future task needs it, that task will add it explicitly.
- **Confidence:** Low.

---

## 5. The `InvoiceService.markOutcomeIfOpen` guard should also reject `null`

- **Issue:** The brief says the method guards that `outcome` is one of `PAID`/`UNDERPAID`/`OVERPAID`. A `null` outcome is not in that set and would silently do nothing if passed; an explicit `Objects.requireNonNull` or `IllegalArgumentException` is safer for an internal API.
- **Severity:** Low — defensive programming.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:71-75`
- **Recommendation:** Add `Objects.requireNonNull(outcome, "outcome must not be null")` at the start of `markOutcomeIfOpen`, and add a unit test for the null case.
- **Confidence:** High.

---

## 6. Consider surfacing `amountDiscrepancy` in `PaymentFinalizedPayload`

- **Issue:** `payment.finalized` is the event downstream consumers receive. If the invoice state transition has no outbox event (Finding 4), the only way downstream can know whether the payment was under/overpaid is to read the payment after the fact or to duplicate the comparison themselves. The payload already carries `amount`; adding `amountDiscrepancy` would make the event self-contained.
- **Severity:** Low — forward-compatibility/consumer ergonomics.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/event/PaymentFinalizedPayload.java:17-28`
- **Recommendation:** Add `String amountDiscrepancy` (or enum) to `PaymentFinalizedPayload`. This does not require a new outbox event and keeps the finalized event self-describing. Since the contract schemas are task 24's scope, note this as a suggested input for that task rather than a T13 code change.
- **Confidence:** Low.

---

## Positive confirmations

- Computing the discrepancy exactly once and reusing it across both `tryFinalizeFrom` attempts is correct and avoids drift.
- Keeping the invoice-state update in the same `@Transactional` boundary as the payment finalization is the right call (no outbound HTTP call, unlike T12).
- Reusing `InvoiceRepository.updateStateIfCurrentState` from T08 avoids new repository code and inherits its atomic, lost-update-safe shape.
- `PaymentService` still never calls `InvoiceService`/`CryptoClient`; `FinalizationOutcome` preserves L12.
- `InvoiceWatchView` already carries `amount`, so no new cross-module data is needed for the comparison.
- No migration is needed because `amount_discrepancy` and all invoice states already exist.
- R16 genuinely needs no new production code; a named regression test is the right T13 deliverable.
