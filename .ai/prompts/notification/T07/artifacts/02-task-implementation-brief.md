# notification · T07 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`. This artifact contains no code; it is documentation only.

## Task

Implement `PaymentEventConsumer` for the four `payments.*` topics. Each event is mapped to its seeded template and dispatched through the existing `NotificationDispatcher` to recipients resolved per §4c / Q7. **Q7 must be confirmed at Phase 4. The scope below is the minimal L2-safe baseline, pending OQ1–OQ5.**

## Purpose

Payment notifications are the second half of this service's launch scope, after auth (`AuthEventConsumer`, T06). Phase 1 of the product needs a merchant to hear about invoices, payments and receipts.

## Scope

**In (baseline):**
- **Consumer and DTOs.** `PaymentEventConsumer` with four `@KafkaListener` methods, plus four DTO records.
- **Mappings.** `NOTIFICATION_MAPPINGS` gains four `PAYMENT` kinds.
- **Merchant dispatch.** The merchant is notified where the event names one: `invoice.created`, and `receipt.issued` when `merchantAccountUuid` is present.
- **Dedupe.** Every event is deduped.

**Conditional on Phase 4:**

| Item | Question |
|---|---|
| Payer notification | OQ1 |
| Merchant resolution for `payment.seen` / `payment.finalized` | OQ2, L2 conflict |
| Template changes (`version=2` seed) | OQ3 |
| Receipt link handling | OQ4 |
| `payment.finalized` | OQ5 |

**Out:**
- Any change to payment-service or `contracts/`.
- `spec/` edits.
- The receipt read endpoint (payment FU-T29-1).
- T20.

## Business Rules

- **R3:** `invoice.created` → notify the invoice's recipients.
- **R4:** `payment.seen` → notify the configured recipients.
- **R5:** `receipt.issued` → notify both parties, with the receipt link.
- **Task-only:** `payment.finalized`.

## Locked Decisions

- **L1:** dedupe in the same transaction.
- **L2:** no live call; the contact projection is the only projection. **This constrains OQ2.**
- **L3:** append-only log, via the orchestrator.
- **L4:** never render `receiptUri`.
- **L6:** preference default.
- **L9:** versioned templates; no edit to `V3`.
- **L11:** module boundaries.

## Dependencies

- `IdempotencyGuard`
- `NotificationDispatcher` / `DeliveryOrchestrator`
- `TemplateRenderer` (+ `LinkProperties`)
- `PreferenceResolver`
- `ContactProjectionRepository`
- `contracts/events/payments/*.v1.schema.json`

## Inputs

Kafka JSON payloads on `payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized` and `payments.receipt.issued`, as their v1 schemas define them.

## Outputs

- **Rows.** `processed_events` rows, plus whatever `delivery_log`, `inapp_notifications` and `delivery_retry` rows the existing orchestrator writes.
- **Delivery.** Emails and in-app messages for the resolved recipients.

## State Changes

- **Baseline:** none beyond the existing `processed_events` / `delivery_log` writes.
- **If OQ2(a) is granted:** a new projection table.

## Files to Create

- `consumer/PaymentEventConsumer.java`
- `consumer/dto/InvoiceCreatedEvent.java`, `PaymentSeenEvent.java`, `PaymentFinalizedEvent.java`, `ReceiptIssuedEvent.java`
- `PaymentEventConsumerTest` (unit) and an integration test, following the T06 / T11 precedent
- **Conditional:**
  - `V12`+ migrations and grant (OQ2a)
  - a template seed migration (OQ3a)

## Files to Modify

`delivery/DeliveryOrchestrator.java`, for `NOTIFICATION_MAPPINGS` only.

## Files NOT to Modify

- `V1`–`V11`, including `V3`
- `AuthEventConsumer`
- `TemplateRenderer`'s link convention (Q4 is owned elsewhere)
- `spec/`, `contracts/` and other services
- the untracked `prompt` file

## Acceptance Criteria

| AC | Criterion |
|---|---|
| AC1 (R3) | `invoice.created` → kind `invoice.created`, to `merchantAccountUuid`, with `invoiceUuid`, `invoiceId` and `amount` in `eventData`. |
| AC2 (R4) | `payment.seen` → kind `payment.seen`, to the recipients OQ1/OQ2 decide. |
| AC3 (R5) | `receipt.issued` → kind `receipt.issued`, to the merchant, with `receiptUuid` (so the renderer computes `receiptLink`) and `invoiceId`. `receiptUri` is never passed on. |
| AC4 | `payment.finalized` → per OQ5. |
| AC5 (L1) | A redelivered event dispatches nothing. |
| AC6 | An event with no resolvable recipient is dedup-recorded, logged at INFO with no PII, and not dispatched. It is never thrown, which would otherwise mean an infinite redelivery. |
| AC7 | Each DTO accepts every field in its v1 schema and ignores unknown ones, so a later producer field doesn't break consumption. |

## Required Tests

**Named:**
- `shouldNotifyBothPartiesOnInvoiceCreated`. **Its name assumes a payer (OQ1).** If only the merchant is notified, the name is kept per §8 and the limit is disclosed.
- `shouldNotifyOnPaymentSeen`
- `shouldNotifyWithReceiptLinkOnReceiptIssued`

**Additional:**
- duplicate → no dispatch
- missing `merchantAccountUuid` on `receipt.issued` → no dispatch
- DTOs parse each contract's example payload
- each payment kind resolves to seeded EMAIL and IN_APP templates
- `receiptUri` never reaches `eventData`
- one Testcontainers integration test: Kafka → consumer → `delivery_log` row

## Constraints

- **Security:** no PII (email, `fromAddress`) in INFO logs (L4, `SecretSafeLogging`).
- **Transactions:** each listener is `@Transactional`; dedupe and dispatch share it (L1).
- **Module boundaries:** `consumer` uses only the dispatcher seam and DTOs (L11).
- **Null handling:** optional schema fields are nullable in the DTOs.
- **Thread safety:** stateless listeners.

## Open Questions

These block parts of the scope. Each is a product or LOCKED-decision call for Phase 4:

| # | Question | Recommendation |
|---|---|---|
| OQ1 (Q7) | Who is the payer? | Merchant only in Phase 1; disclose payer notification as deferred. |
| OQ2 | Merchant for `payment.seen` / `payment.finalized`? Option (a), a local event-fed projection, needs an L2 interpretation from the human. | Either grant (a) explicitly, or (b) skip those two events' merchant notification and log them as follow-ups. |
| OQ3 | `{{currency}}` / `{{amount}}` have no source. | Seed `version=2` templates that drop them. |
| OQ4 | The receipt link resolves to nothing (FU-T29-1). | Render the provisional link and disclose it. |
| OQ5 | `payment.finalized` has no R-number. | Follows OQ2. |
