# notification · T07 · Phase 13 — PR Preparation

Consumes: `artifacts/12-specification-verification.md`. Verdict: **PASS**, so this phase proceeds.

## Branch note

The work is committed on the working branch `spec/service-specs-and-ai-framework`, phase by phase. That is this pipeline's established practice, used for T01–T19 and every payment task. The branch has not been merged into `main`, so `main` stays deployable.

For a dedicated PR, branch off `main` and cherry-pick the T07 code commits:
- `f484251f` (Phase 6)
- `51f73a71` (Phase 9)
- `bc8a75c3` (Phase 10)
- `077d7b59` (Phase 11)

## Commit title

`notification: consume payment events and notify the merchant (T07)`

## Commit message

```
notification: consume payment events and notify the merchant (T07)

Add PaymentEventConsumer for payments.invoice.created, payment.seen,
payment.finalized and receipt.issued. Each @Transactional listener dedupes
on <eventType>:<aggregateUuid>, records an event-fed routing projection
(invoice -> merchant + token decimals, payment -> invoice), and dispatches
to the invoice's merchant through the existing NotificationDispatcher.

- routing/: insert-once invoice_routes and payment_routes (V12), with an
  INSERT+SELECT-only grant (V13); PaymentRouting is the only seam
- V14 seeds version-2 payment templates without the placeholders no
  event fills (currency, displayName, receiptLink)
- AmountFormatter renders whole base units as exact BigDecimal amounts,
  or "<raw> base units" when decimals are unknown
- receiptUri, KMS key and wallet addresses never reach templates or logs

Notifies the merchant only and gives no receipt link yet (decided at T07
Phase 4: no payer identity exists, and no receipt read endpoint exists).
Follow-ups FU-T07-1..4.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

The prompt template names an older model in its trailer. The trailer above follows the repo's current commit attribution.

## Files changed

22 files, +1566 / −19, all under `services/notification/`.

**Production (new):**
- `consumer/PaymentEventConsumer.java`
- `consumer/AmountFormatter.java`
- `consumer/dto/InvoiceCreatedEvent.java`, `PaymentSeenEvent.java`, `PaymentFinalizedEvent.java`, `ReceiptIssuedEvent.java`
- `routing/InvoiceRoute.java`, `PaymentRoute.java`, `InvoiceRouteRepository.java`, `PaymentRouteRepository.java`, `PaymentRouting.java`

**Migrations (new):**
- `V12__payment_routing_projection.sql`
- `V13__notification_app_payment_routing_grant.sql`
- `V14__seed_payment_templates_v2.sql`

**Tests (new):**
- `consumer/PaymentEventConsumerTest.java`
- `consumer/PaymentEventConsumerIntegrationTest.java`
- `consumer/AmountFormatterTest.java`
- `consumer/dto/PaymentEventContractTest.java`

**Tests (updated):**
- `NotificationBaselineMigrationIntegrationTest.java`
- `consumer/dto/ConsumedEventSchemaConformanceTest.java`
- `T01SkeletonRegressionTest.java`
- `ArchitectureTest.java`

**Not changed:**
- `DeliveryOrchestrator` (its payment mappings were already present from T11)
- V1–V11
- `spec/`, `contracts/` and other services

## Summary

- **Notifications.** Payment notifications now reach the merchant for all four payment events.
- **Merchant lookup.** `payment.seen` and `payment.finalized` don't carry the merchant, so it is looked up in a local projection built only from consumed payment events. There is no live call to payment-service; this is the human-granted L2 interpretation.
- **Amounts.** Amounts render as exact decimals (`1500000` with 6 decimals shows as `1.5`).
- **Unresolved or malformed events.** An unresolved or malformed event is logged at INFO with only the topic, key and UUIDs, and never loops.

**Known limits:**

| ID | Limit |
|---|---|
| FU-T07-1 | No payer notification; no payer contact exists |
| FU-T07-2 | No receipt link until payment FU-T29-1 adds a receipt read endpoint |
| FU-T07-3 | Spec alignment for §4c recipients, L2 wording and R5, in T20 |
| FU-T07-4 | During catch-up, an event processed before its route exists is recorded but never notified (no retry) |
| S2 | A receipt processed before its invoice stores no token decimals |

## Testing performed

- **Full suite:** `mvn -o -pl services/notification verify` → **413/413, BUILD SUCCESS** (2026-10-19).
- **Unit tests:**
  - `PaymentEventConsumerTest` (18 tests, including the three §8 named tests)
  - `AmountFormatterTest` (5)
  - the DTO contract and schema-conformance tests
- **Integration tests:**
  - `PaymentEventConsumerIntegrationTest` runs Testcontainers Postgres plus its own `apache/kafka:3.8.0` broker. It covers the full lifecycle with redelivery, renders through the real `TemplateRenderer` (version 2, no `{{`, no link), proves first-writer-wins routing, and checks the pinned catch-up race.
  - `NotificationBaselineMigrationIntegrationTest` covers the V12–V14 grants and the template seed.
- **Mutation testing:** 11 deliberate code breaks (M1–M11), covering dedupe, wrong merchant, `receiptUri` leak, `ON CONFLICT` removal on both tables, floating-point amounts, route repair, null-check order, merchant precedence, a hard-coded group ID and a silent drop. All were killed, and each file was restored byte-identical.
- **Reviews:**
  - Kimi independent review: 5 of 8 findings accepted, 3 rejected with source evidence.
  - Kimi test review: 4 of 8 gaps accepted, 4 rejected as already covered or wrong premise.

## Specification references

| Reference | IDs |
|---|---|
| Task | `spec/notification-service/tasks.md` task 7 |
| Requirements | R3, R4 and R5 (partial: merchant only, no link); R7/R8 (via L1); R15 (via L4) |
| LOCKED decisions | L1, L2 (as interpreted, D2), L3, L4, L6, L9, L11 |
| Open question | Q7, decided as merchant-only at Phase 4 (D1) |
| Named tests | `shouldNotifyBothPartiesOnInvoiceCreated`, `shouldNotifyOnPaymentSeen`, `shouldNotifyWithReceiptLinkOnReceiptIssued` |
| Contracts | `contracts/events/payments/{invoice-created,payment-seen,payment-finalized,receipt-issued}.v1.schema.json` |
