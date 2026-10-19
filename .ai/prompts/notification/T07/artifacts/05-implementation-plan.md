# notification · T07 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md`. This artifact contains no code; it is documentation only.

## Correction to Phases 0–4

`DeliveryOrchestrator.NOTIFICATION_MAPPINGS` **already** maps all four payment kinds to the same-named templates, category `PAYMENT` (`:97`–`:104`, added in T11). Phase 0 said it held only the three auth kinds; that was wrong. Scope item 5 of the frozen brief therefore needs no change, and `DeliveryOrchestrator` is not modified.

## Steps

### 1. Routing feature package `com.themistra.notification.routing`

This is a new top-level module, so it is added to `ArchitectureTest.FEATURE_MODULES`.

**Entities** `InvoiceRoute` and `PaymentRoute`, mapped to these tables in schema `notifications`:
- `invoice_routes(invoice_uuid UUID PK, merchant_account_uuid UUID NOT NULL, token_decimals SMALLINT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now())`
- `payment_routes(payment_uuid UUID PK, invoice_uuid UUID NOT NULL, created_at …)`

The entities are getter-only. Note: `token_decimals` must be nullable, because `receipt.issued` can record a route without decimals.

**Repositories** are package-private. Writes are native `INSERT … ON CONFLICT (pk) DO NOTHING`, which needs only INSERT (the `ContactProjectionRepository` style).

**`PaymentRouting`** is a public `@Service`, the only seam `consumer` uses (L11):
- `recordInvoice(invoiceUuid, merchant, tokenDecimals)`
- `recordPayment(paymentUuid, invoiceUuid)`
- `findMerchantForInvoice(invoiceUuid)` → `Optional<UUID>`
- `findInvoiceForPayment(paymentUuid)` → `Optional<UUID>`
- `findTokenDecimals(invoiceUuid)` → `Optional<Integer>`

It returns only UUIDs and ints, never entities.

### 2. Migrations

- **`V12__payment_routing_projection.sql`:** the two tables, with a comment citing D2.
- **`V13__notification_app_payment_routing_grant.sql`:** `GRANT INSERT, SELECT ON` both tables.
- **`V14__seed_payment_templates_v2.sql`:** eight `version=2` rows.
  - They contain no `{{currency}}`, no `{{receiptLink}}` and no `{{displayName}}`. `displayName` is always null platform-wide (a T05/T11 disclosure), which leaves "Hi ," greetings. Dropping it follows F7's "drop placeholders nothing fills".
  - The wording is merchant-facing. Each row's text uses only the keys the consumer supplies:

| Template | Keys |
|---|---|
| `invoice.created` | `amount`, `chain`, `invoiceId`, `invoiceLink` |
| `payment.seen` | `invoiceId`, `chain`, `txHash` |
| `payment.finalized` | `amount`, `invoiceId`, `chain`, `txHash` |
| `receipt.issued` | `invoiceId`, `receiptId` |

`invoiceLink` is computed by the renderer from `invoiceUuid`. It depends on the frontend route, exactly as the auth emails' links do.

### 3. DTOs in `consumer/dto`

Four records, annotated `@JsonIgnoreProperties(ignoreUnknown = true)`, with fields exactly as in each v1 schema. Amounts are `String` (L6).

### 4. `consumer/PaymentEventConsumer`

Four listeners, each `@KafkaListener(topics="payments.<x>", groupId="${spring.kafka.consumer.group-id}") @Transactional`, using `ObjectMapper.readValue`.

- **Dedup key:** `<eventType>:<aggregateUuid>`, so `invoice.created:<invoiceUuid>`, `payment.seen:<paymentUuid>`, `payment.finalized:<paymentUuid>` and `receipt.issued:<receiptUuid>`.
- **Order:** dedupe first, then record routing, then resolve the merchant.
  - When the merchant is absent: an INFO log (`topic`, key, aggregate UUID) and return.
  - Otherwise: `dispatch(merchant, kind, eventData)`, where `eventData` holds `sourceEventKey`, `invoiceUuid`, `invoiceId`, `chain`, plus whichever of `txHash`, `amount` and `receiptId` the event has.
- **`receiptUri`** is never read into `eventData`.
- **`AmountFormatter`** is a package-private static helper in `consumer`. It computes `new BigDecimal(raw).movePointLeft(decimals).stripTrailingZeros().toPlainString()`. When the decimals are unknown, it returns `raw + " base units"`. An unparseable value is returned raw.

### 5. Tests

**`PaymentEventConsumerTest`** (Mockito, with a real `ObjectMapper`):
- The three named tests.
- `payment.finalized` routing.
- Duplicate → no dispatch.
- Unresolved route → no dispatch.
- `receipt.issued` fallback to the routed merchant.
- `receiptUri` exclusion.
- Routing is recorded even when dispatch is skipped.

**`AmountFormatterTest`.**

**DTO contract tests** for the four DTOs, mirroring `EmailRequestedEventContractTest`, plus `ConsumedEventSchemaConformanceTest` extended.

**Migration, grant and template tests:**
- `NotificationBaselineMigrationIntegrationTest` is updated so the table list includes the two routing tables.
- A new grant test: INSERT and SELECT allowed; UPDATE and DELETE denied on both tables.
- A v2 template test: the renderer picks version 2 for each payment template, and no `{{` remains after rendering with the consumer's keys.

**`PaymentEventConsumerIntegrationTest`:**
- A Testcontainers Postgres plus **its own Kafka container** (`apache/kafka:3.8.0`), with topics pre-created and the offset reset set to `earliest`. It does not use the shared `localhost:9094` broker.
- It uses the spy dispatcher pattern from `AuthEventConsumerIntegrationTest`.
- Flow: invoice.created → payment.seen → payment.finalized → receipt.issued. All four dispatch to the merchant, routes are persisted, and a redelivery is deduped.

**`ArchitectureTest.FEATURE_MODULES`** gains `"routing"`.

### 6. Verification

- `mvn -o -pl services/notification verify` from the repo root.
- Mutations, each restored and confirmed with `cmp`:
  - drop the dedupe check
  - resolve the wrong merchant
  - put `receiptUri` back into `eventData`
  - drop the `ON CONFLICT` clause
  - use floating-point decimals
  - drop the routing-before-resolve order for `receipt.issued`

## Risks

- **Fixed schema lists.** Existing tests that pin the exact table set, the template count, or the Flyway history count will need updating. The plan is to update each test's expectation and record why, never weakening an assertion.
- **Kafka container startup** in the new integration test. It follows payment E2E's pattern.
