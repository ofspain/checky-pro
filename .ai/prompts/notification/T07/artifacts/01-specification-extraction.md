# notification · T07 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`. This artifact contains no code; it is documentation only.

## Business Rules

| ID | Rule |
|---|---|
| R3 | Consuming `payments.invoice.created` notifies "the invoice's recipients (per the §4c matrix / Q7)" that an invoice was created. |
| R4 | Consuming `payments.payment.seen` notifies "the configured recipients" that a payment was seen on-chain. |
| R5 | Consuming `payments.receipt.issued` notifies "both parties" and includes the receipt link. |
| Task-only (no R-number) | The task text and §4c also cover `payments.payment.finalized` → template `payment.finalized`. |
| R7, R8 (via L1) | A duplicate delivery never double-sends. |
| R9, R10, R18 (via L6) | Preferences apply, with the `PAYMENT` default when none is set. |

## Locked Decisions

| ID | What it requires |
|---|---|
| **L1** | Dedupe on the event's stable key in `processed_events`, in the same transaction as the delivery-log append. |
| **L2** | Consume-only, with no synchronous cross-service call on the delivery path. **"The only permitted projection read is the recipient-contact projection (O1)."** |
| **L3** | Append-only delivery log. Already handled by `DeliveryOrchestrator`. |
| **L4** | No secrets or tokens in messages. `receiptUri` (an `s3://` URI) is internal, not a secret, but it shouldn't be rendered. |
| **L6** | Preference resolution with the safe default. |
| **L9** | Templates are versioned. A template change means a new seed migration with a higher `version`, never an edit to `V3`. |
| **L11** | Module boundaries; `consumer/` must not import `delivery` entities. |

## Files involved

**New:**
- `consumer/PaymentEventConsumer.java`
- `consumer/dto/{InvoiceCreatedEvent, PaymentSeenEvent, PaymentFinalizedEvent, ReceiptIssuedEvent}.java`
- the tests

**Modified:** `delivery/DeliveryOrchestrator.java`. `NOTIFICATION_MAPPINGS` gains `invoice.created`, `payment.seen`, `payment.finalized` and `receipt.issued`, each mapped to the same-named EMAIL and IN_APP templates with category `PAYMENT`.

**Possibly new, depending on the Q7 decision:** a projection table, its migration (`V12`) and its grant (`V13`). See OQ2; this conflicts with L2.

**Possibly new:** a `V12`/`V14` seed migration with `version=2` templates. See OQ3.

## Dependencies

- `IdempotencyGuard`
- `NotificationDispatcher` / `DeliveryOrchestrator`
- `TemplateRenderer` (link computation)
- `PreferenceResolver`
- `contact_projection`
- `LinkProperties`
- `contracts/events/payments/*.v1.schema.json`
- Kafka topics `payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized` and `payments.receipt.issued`
- The config key `spring.kafka.consumer.group-id`, which already exists

## Acceptance Criteria (derived)

| AC | Criterion |
|---|---|
| AC1 (R3) | `invoice.created` dispatches kind `invoice.created` to the merchant (`merchantAccountUuid`). |
| AC2 (R4) | `payment.seen` dispatches kind `payment.seen` to the configured recipients. **Who those are depends on OQ1/OQ2.** |
| AC3 (R5) | `receipt.issued` dispatches kind `receipt.issued` to the merchant, carrying `receiptUuid` so the renderer computes `receiptLink`. **Whether that link resolves depends on OQ4.** |
| AC4 | `payment.finalized` is handled per OQ5. |
| AC5 (L1) | Each event is deduped before any side effect, and a redelivery dispatches nothing. |
| AC6 | An event that can't be dispatched (an unknown recipient, or a missing merchant on the optional `receipt.issued` field) is dedup-recorded, not dispatched, and logged, never thrown. This follows `AuthEventConsumer`'s pattern for unknown values. |

## Tests required

**Named (`package.md` §8):**
- `shouldNotifyBothPartiesOnInvoiceCreated` — the name assumes two parties; OQ1.
- `shouldNotifyOnPaymentSeen`
- `shouldNotifyWithReceiptLinkOnReceiptIssued`

**Implied:**
- duplicate event → no second dispatch
- each DTO deserialises its contract's example
- `receipt.issued` without `merchantAccountUuid`
- `payment.finalized` behaviour
- a mapping test: every payment kind resolves to seeded EMAIL and IN_APP templates

## Open Questions (genuine blockers; §11 Q7 is the root)

**OQ1 — Q7: who is the "payer"?**
- No payment event carries a payer account or contact; the payer is an anonymous wallet, `fromAddress`.
- §4c's "merchant + payer" can't be met. R5's "both parties" and the named test `shouldNotifyBothPartiesOnInvoiceCreated` both assume it.
- Options: (a) notify the merchant only in Phase 1 and disclose payer notification as deferred; (b) add a payer contact upstream (payment-service), which is out of scope.

**OQ2 — the merchant for `payment.seen` and `payment.finalized`.**
- `payment.seen` carries only `invoiceUuid`. `payment.finalized` carries only `paymentUuid`.
- Resolving the merchant needs a local `invoiceUuid→merchant` / `paymentUuid→invoiceUuid` projection fed by those consumed events. **L2 forbids that as written:** "the only permitted projection read is the recipient-contact projection".
- Options:
  - (a) Treat a consumed-event-fed projection as consistent with L2's intent (no live call). That is a LOCKED-decision interpretation only the human can grant.
  - (b) Skip merchant notification for those two events in Phase 1.
  - (c) Add `merchantAccountUuid` to both payment contracts. That is a cross-service change to payment-service, out of scope here.

**OQ3 — template values.**
- `{{currency}}` (all four) and `{{amount}}` (`payment.seen`) have no source. The renderer would substitute them as empty, giving text such as "a payment of   on-chain".
- Options: (a) seed `version=2` templates without them; (b) accept empty substitution; (c) show the token contract address instead.

**OQ4 — the receipt link (R5, Q4).**
- `receiptLink` points at `<base-url>/receipts/<uuid>`, which nothing serves (payment FU-T29-1).
- Options: (a) render it anyway and disclose it as provisional; (b) omit the link until the endpoint exists. R5 then becomes partial.

**OQ5 — `payment.finalized`.** It has no R-number. Should it be implemented (template already seeded), or skipped?

Not a blocker: the dedup key. Use `<eventType>:<aggregateUuid>`. Each payment event fires once per aggregate, and redeliveries are byte-identical (payment E2E).
