# notification · T07 · Phase 4 — Frozen Task Brief

Consumes:
- `artifacts/02-task-implementation-brief.md`
- `artifacts/03-design-challenge.md` (Kimi)

This artifact contains no code; it is documentation only. Once frozen, this brief is the only specification Phases 5–13 use.

## Human decisions (2026-10-19)

| # | Question | Decision |
|---|---|---|
| D1 | Q7 / OQ1 — recipients (Kimi F1, F8) | **The merchant only, disclosed.** This is the human's Q7 decision. It deviates from `design.md` §4c's "merchant + payer" and is recorded here, not by editing `spec/`. A payer has no account or contact anywhere on the platform, so payer notification is follow-up **FU-T07-1**: it needs a payer contact collected upstream. The §8 name `shouldNotifyBothPartiesOnInvoiceCreated` is kept literally, with a Javadoc saying it asserts merchant-only delivery. |
| D2 | OQ2 / OQ5 — merchant for `payment.seen` / `payment.finalized` (Kimi F4, F6) | **An event-fed projection, as an explicit L2 interpretation granted by the human.** L2's purpose is no live cross-service call on the delivery path. A local routing projection fed **only by consumed payment events** keeps that purpose, exactly as `contact_projection` is fed only by consumed auth events. L2's literal "the only permitted projection" is widened to "contact projection plus payment-routing projection" for this task. Both events notify the merchant. |
| D3 | OQ4 — the receipt link (Kimi F5) | **No link until it works.** The `version=2` `receipt.issued` templates say the receipt is ready and give its ID, with no `{{receiptLink}}`. R5's "include the receipt link" is **partial**, tied to payment FU-T29-1. The link comes back in a later template version once a receipt read endpoint exists. |
| D4 | Kimi F2, F3, F7, F9 | **Accept all four.** See below. |

**F2 — DTOs.** The DTOs are hand-written records, following the T06/T15 precedent:
- Each one gets a schema-conformance contract test against its `contracts/events/payments/*.v1.schema.json`, mirroring `EmailRequestedEventContractTest`.
- `ConsumedEventSchemaConformanceTest` is extended to cover them.
- `agents.md:38` ("generated, never hand-written") is breached the same way across the whole platform, so it stays the platform-wide follow-up already logged in payment T24.

**F3 — `invoiceId`.** `{{invoiceId}}` is the `invoiceUuid` string.

**F7 — templates.** A `version=2` template seed is part of the baseline, not conditional. It drops `{{currency}}` (no source exists) and any placeholder the event can't fill.

**F9 — logging.** Consumer log lines carry only the topic, the dedup event key and aggregate UUIDs. They never carry payloads, wallet addresses, emails or amounts.

**Kimi accuracy.** F7's "the renderer will fail" is wrong: `TemplateRenderer` treats a missing key as empty (`:72`). Several cited paths are also wrong: `spec/payment-service/.../*Payload.java` actually lives under `services/payment/src/main/java`. The substance of every finding still holds.

### Engineering addition (disclosed, for Kimi Phase 8 to challenge)

Every payment contract defines `amount` as **whole token base units** (`invoice-created.v1.schema.json:14`), so `"1000000"` means 1.0 of a 6-decimal token. Rendering base units would mislead the merchant.
- `invoice.created` carries `tokenDecimals`, so the routing projection stores it too.
- Messages render `amount / 10^tokenDecimals` (exact `BigDecimal`, `stripTrailingZeros().toPlainString()`, never a floating-point type).
- When the decimals are unknown, the raw value is rendered with the label "base units".

## Task

Implement `PaymentEventConsumer` for `payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized` and `payments.receipt.issued`. It notifies the invoice's merchant through the existing `NotificationDispatcher`, resolving the merchant from the event or from a new routing projection fed by these same events.

## Scope

**In:**
1. **DTO records.** `consumer/dto/InvoiceCreatedEvent`, `PaymentSeenEvent`, `PaymentFinalizedEvent`, `ReceiptIssuedEvent`. They ignore unknown fields, and nullable optional fields are boxed.
2. **Routing projection.** A new `routing` feature package (or within `consumer/`, Phase 5 decides under L11). It has entities, repositories and an updater for two tables:
   - `invoice_routes(invoice_uuid PK, merchant_account_uuid, token_decimals, created_at)`
   - `payment_routes(payment_uuid PK, invoice_uuid, created_at)`

   Rows are written with insert-if-absent; the first writer wins, because the routing facts never change.
3. **Migrations.** `V12__payment_routing_projection.sql` creates the tables in `notifications`. `V13__notification_app_payment_routing_grant.sql` grants INSERT and SELECT only, with no UPDATE or DELETE.
4. **Consumer.** `PaymentEventConsumer` has four `@KafkaListener @Transactional` methods. Each dedupes, then refreshes routing, then dispatches:

   | Event | Routing it records | Merchant comes from | Kind dispatched |
   |---|---|---|---|
   | `invoice.created` | `invoice_routes` | the event | `invoice.created` |
   | `payment.seen` | `payment_routes` | `invoice_routes` | `payment.seen` |
   | `payment.finalized` | — | `payment_routes` → `invoice_routes` | `payment.finalized` |
   | `receipt.issued` | both routes, from its `invoiceUuid`, `paymentUuid` and `merchantAccountUuid` | `merchantAccountUuid`, else `invoice_routes` | `receipt.issued` |

5. **Mappings.** `DeliveryOrchestrator.NOTIFICATION_MAPPINGS` gains the four kinds, each mapped to the same-named EMAIL and IN_APP templates, category `PAYMENT`.
6. **Template seed.** `V14__seed_payment_templates_v2.sql` adds `version=2` rows for the four names × two channels. They use no `{{currency}}`, and `receipt.issued` has no `{{receiptLink}}`. Wording is merchant-facing ("a payment", not "your payment").
7. **Tests.** See below.

**Out:**
- payment-service and `contracts/` changes
- `spec/` edits (the §4c deviation is recorded here, D1)
- `V1`–`V11`
- `AuthEventConsumer`
- `TemplateRenderer`'s link convention
- a receipt read endpoint
- payer notification (FU-T07-1)
- T20

## Business Rules

- **R3:** `invoice.created` notifies the merchant.
- **R4:** `payment.seen` notifies the merchant.
- **R5:** `receipt.issued` notifies the merchant. **Partial:** the merchant only (D1) and no link (D3).
- **Task-only:** `payment.finalized` notifies the merchant.

## Locked Decisions

- **L1:** dedupe in the same transaction as the delivery-log append. The key is `<eventType>:<aggregateUuid>`.
- **L2:** honoured as interpreted in D2: no live call, and both projections are event-fed.
- **L3, L6:** via the existing orchestrator.
- **L4:** `receiptUri` and addresses are never rendered or logged.
- **L9:** a new `version=2` seed; `V3` is untouched.
- **L11:** the routing package exposes only a service or updater seam; `consumer` imports no routing entity.

## Acceptance Criteria

| AC | Criterion |
|---|---|
| AC1 (R3) | `invoice.created` → one dispatch, kind `invoice.created`, recipient = `merchantAccountUuid`. `eventData` has `invoiceUuid` and `invoiceId` (= `invoiceUuid`) and the human-formatted `amount`. An `invoice_routes` row is recorded. |
| AC2 (R4) | `payment.seen` → `payment_routes` row recorded. Dispatch `payment.seen` to the invoice's merchant, with `invoiceId` and `txHash`. If the invoice route is unknown, there is no dispatch (AC6). |
| AC3 (R5, partial) | `receipt.issued` → dispatch `receipt.issued` to `merchantAccountUuid`, or to the routed merchant when it is absent, with `invoiceId` and `receiptId`. `receiptUri` is never in `eventData`, and the rendered v2 body has no link. |
| AC4 | `payment.finalized` → dispatch `payment.finalized` to the merchant resolved via `payment_routes` → `invoice_routes`, with `invoiceId` and the formatted `amount`. |
| AC5 (L1) | A redelivered event (same key) dispatches nothing and writes no second route row. |
| AC6 | An unresolvable merchant (the route is not yet known, for example consumer lag across topics) → the event is dedup-recorded, an INFO log is written with the key and aggregate UUID only, and nothing is dispatched or thrown. **Disclosed limitation:** no deferred retry. |
| AC7 | DTOs conform to their v1 schemas, through contract tests, and ignore unknown fields. |
| AC8 | Amount formatting is exact `BigDecimal` division by `10^tokenDecimals`. It falls back to the raw value with the "base units" label when the decimals are unknown. No floating-point type. |
| AC9 | The `version=2` templates are what render. Each payment kind resolves to its v2 EMAIL and IN_APP templates, with no unfilled placeholder for the data the consumer supplies. |

## Required Tests

**Named (`package.md` §8):**
- `shouldNotifyBothPartiesOnInvoiceCreated` (merchant-only, disclosed per D1)
- `shouldNotifyOnPaymentSeen`
- `shouldNotifyWithReceiptLinkOnReceiptIssued`, whose Javadoc discloses D3: it asserts that `receiptId` is present and that no link is rendered

**Additional:**
- `payment.finalized` routing
- duplicate → no dispatch
- unresolved route → no dispatch
- `receipt.issued` without `merchantAccountUuid` → routed fallback
- `receiptUri` exclusion
- amount formatting, including the fallback
- DTO contract tests, plus `ConsumedEventSchemaConformanceTest` extended
- a V12/V13 migration and grant integration test (INSERT and SELECT allowed; UPDATE and DELETE denied)
- a v2 template render test
- one Testcontainers Kafka→`delivery_log` integration test

## Constraints

- **Logging:** no PII in logs (F9, `SecretSafeLogging`).
- **Transactions:** `@Transactional` listeners.
- **Routing writes:** insert-if-absent, using `INSERT … ON CONFLICT DO NOTHING` or an existence check, compatible with an INSERT+SELECT-only grant.
- **Module boundaries:** L11.
- **No floating-point money.**

## Follow-ups (logged, out of scope)

| ID | Follow-up |
|---|---|
| FU-T07-1 | Payer notification. Needs a payer contact upstream. |
| FU-T07-2 | Restore `{{receiptLink}}` once a receipt read endpoint exists (payment FU-T29-1). |
| FU-T07-3 | Spec alignment for T20/human: `design.md` §4c recipients, L2 wording, and R5's link. |
| FU-T07-4 | A deferred retry for events whose route arrives late. |
| Existing | The platform-wide generated-DTO gap (`agents.md:38`). |

## Open Questions

No blockers.
