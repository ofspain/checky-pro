# payment · T24 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`.
Approved by the human via `AskUserQuestion`, 2026-10-19 (all three decisions: "Recommended").

## Disposition of Kimi's 9 Phase 3 findings

Each finding was checked against the cited source before disposition.

| # | Finding | Disposition | Reason |
|---|---|---|---|
| 1 | `CreateInvoiceRequest.amount` is `BigDecimal`, contract says string | **ACCEPTED, partial** | Confirmed `CreateInvoiceRequest.java:24`. However, Jackson's default scalar coercion already deserializes a JSON string `"10.5"` into `BigDecimal`, and no `spring.jackson.*` property disables it. Existing `InvoiceControllerTest` sends numbers, which still work. **No DTO change.** The contract declares `amount: type: string` (L6), and the contract test proves a string `amount` deserializes into `CreateInvoiceRequest` (AC6). |
| 2 | Some payload fields are nullable, contradicting "all fields required" | **ACCEPTED, widened** | Confirmed: `InvoiceCreatedPayload.reference` comes from `@Size`-only `CreateInvoiceRequest.reference` (`V1` column `reference VARCHAR(140)` is nullable). `PaymentFinalizedPayload.fromAddress` and **`toAddress`** both come from `ChainTxFinalized`, and neither is in `contracts/events/chain/tx-finalized.v1.schema.json`'s `required`. Kimi missed `toAddress`. Likewise `InvoiceResponse.reference` in the OpenAPI file. Those fields are `type: ["string","null"]` and omitted from `required` in the event schemas, and `nullable: true` and not required in OpenAPI 3.0.3 (sibling precedent, `notifications.yaml:75`). `TransactionExportRow` stays all-required: `TransactionExportRepository.java:44-45` inner-joins `invoices` and `receipts`. |
| 3 | CSV response not addressed | **ACCEPTED** | `GET /api/v1/exports/transactions` `200` documents both `application/json` (array of `TransactionExportRow`) and `text/csv` (`schema: {type: string}`). The CSV description names the 12-column header in `ExportController.toCsv` order and the `Content-Disposition: attachment` header. |
| 4 | `$id` / OpenAPI `info` not frozen | **ACCEPTED** | `$id = https://checky.pro/contracts/events/payments/<event>.v1.schema.json`, `$schema` draft 2020-12, `title = "payments.<event> (schema_version 1)"`, matching `design.md` §4c's pinned receipt schema and `auth/user-lifecycle.v1.schema.json`. `payments.yaml`: `openapi: 3.0.3`, `info.title: payment-service API`, `info.version: "1.0"`, `servers: [{url: /}]`, matching `auth.yaml:1-10`. |
| 5 | No test asserts monetary fields are `type: string` | **ACCEPTED, folded into one mechanism** | Covered by AC7's Java-type → schema-type/format check (`BigDecimal`/money `String` → `string`), not by a separate per-field assertion. |
| 6 | No test asserts `additionalProperties: false` | **ACCEPTED** | Each event-schema test asserts `additionalProperties` is present and `false`. |
| 7 | `ROLE_MERCHANT` not reflected in security | **ACCEPTED** | Every route's `security` is `- bearerAuth: [MERCHANT]`, following `auth.yaml:241`'s existing `bearerAuth: [ADMIN]` precedent. `bearerAuth` is `type: http, scheme: bearer, bearerFormat: JWT`. The test asserts every documented operation carries `bearerAuth: [MERCHANT]`. No public routes exist in this service. |
| 8 | No `format`/`enum` constraints | **ACCEPTED, partial** | `format: uuid` for every `UUID` field, `format: date-time` for every `Instant` field, `enum: [ETHEREUM, TRON]` on `CreateInvoiceRequest.chain` (its `@Pattern`), and `enum` of all six `InvoiceState` values on `InvoiceResponse.state`. Event-schema `chain` stays plain `string`, matching `design.md`'s pinned receipt schema. Verified by AC7's type/format check plus an `InvoiceState.values()` ↔ schema-enum equality assertion. **Rejected part:** separate UUID/date-time serialize/deserialize round-trip tests. They would re-test Jackson, not the contract. |
| 9 | Export query params not explicit | **ACCEPTED** | `from`, `to` (required, `string`/`date-time`), `format` (optional, `enum: [csv, json]`, case-insensitive per description) as query params. `Accept` is a documented optional header param noting `text/csv` selects CSV when `format` is absent; anything else selects JSON. |

## Gate-level additions (found during verification, decided by the human)

- **G1 — `receipt-issued` follows `design.md` §4c's pinned schema, not the "all required" rule.**
  The spec pins `required` = `invoiceUuid, paymentUuid, receiptUuid, chain, txHash, occurredAt`
  (6 of 11). The file reproduces the pinned schema field-for-field. The one exception is G2's
  description sentence. The spec is authoritative over Phase 2's AC2.
- **G2 — Partition key: document what the code actually uses, and log the drift.** `design.md`
  (§4c, line 335 and line 355) says every payments event is keyed by `invoiceUuid`. The code
  (`OutboxRelay.java:76` sends `aggregateId` as the Kafka key) keys `invoice.created` by
  `invoiceUuid`, `payment.seen`/`payment.finalized` by `paymentUuid`, and `receipt.issued` by
  `receiptUuid`. Each schema's `description` states its real key. No production change in T24.
  The drift is logged under Open Questions.
- **G3 — Honour T23's forward commitment with a minimal `Problem` schema.** T23's frozen
  constraint requires `ProblemTypes` to be referenced from `payments.yaml` once T24 authors it.
  Add one `components.schemas.Problem` (RFC 9457: `type`, `title`, `status`, `detail`,
  `instance`). Its `type` is `format: uri` with an `enum` of exactly the 11 `ProblemTypes` URIs.
  Per-route 4xx/5xx response documentation stays out of scope (R47 precedent, unchanged).

## Task

Author `contracts/api/payments.yaml` and the four `contracts/events/payments/*.v1.schema.json`.
They document this service's four real HTTP routes and four real published events exactly as
built, plus the `Problem` schema (G3). Add contract-conformance tests mirroring the
`NotificationOpenApiContractTest` and `UserLifecycleEventPayloadContractTest` patterns (R31).

## Scope

**In:** `payments.yaml`, the four event schemas, the two test classes below, and one new
test-scope `pom.xml` dependency.

**Out:** Any production code change: no DTO, payload, controller, or outbox key change (Findings
1 and 8; G2). Per-route error response documentation (G3, R47). `contracts/api/crypto-internal.yaml`
(owned by `crypto-service`, read-only). Fixing the partition-key drift (G2 follow-up).

## Business Rules

- **R31.**

## Locked Decisions

- **L6, L8, L10, L12 (continuity)**, as in Phase 1.
- Phase 2's three structural decisions stand. One combined `PaymentEventPayloadContractTest`.
  One `PaymentsOpenApiContractTest` mirroring `NotificationOpenApiContractTest`. Export date
  params documented as `string`/`date-time` (matches `Instant`).
- Frozen conventions in the disposition table: Finding 4 (URIs/metadata), Finding 7 (security),
  Finding 2 (nullability encoding), G1, G2, G3.

## Dependencies

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`, test scope. No other.

## Inputs / Outputs / State Changes

None. Static contract files and test classes only.

## Files to Create

- `contracts/api/payments.yaml`
- `contracts/events/payments/invoice-created.v1.schema.json`
- `contracts/events/payments/payment-seen.v1.schema.json`
- `contracts/events/payments/payment-finalized.v1.schema.json`
- `contracts/events/payments/receipt-issued.v1.schema.json`
- `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`
- `services/payment/src/test/java/com/themistra/payment/events/PaymentEventPayloadContractTest.java`

## Files to Modify

- `services/payment/pom.xml`: add the one test-scope dependency.

## Files NOT to Modify

Do not modify any of these:

- the four payload records
- `CreateInvoiceRequest`, `InvoiceResponse`, `TransactionExportRow`
- `InvoiceController`, `ExportController`
- `PaymentService`, `ReceiptService`, `OutboxPublisher`/`OutboxRelay` (G2)
- `ProblemTypes`
- any other production class
- `contracts/api/crypto-internal.yaml`
- anything under `spec/`

## Acceptance Criteria

- **AC1.** `payments.yaml` documents exactly the four real routes. The listed component schemas
  must match their records field-for-field in names and in required/optional status (Finding 2):
  `CreateInvoiceRequest`, `InvoiceResponse`, `TransactionExportRow`, and `Problem`. The CSV
  variant is documented (Finding 3), as are all export params and the header (Finding 9).
- **AC2.** `invoice-created`, `payment-seen` and `payment-finalized` match their payload records
  field-for-field. Each has `additionalProperties: false`. `required` = every field except the
  nullable ones from Finding 2 (`reference`; `fromAddress`, `toAddress`). `receipt-issued`
  matches `design.md` §4c exactly (G1), except for the G2 description sentence.
- **AC3.** Every monetary field (`amount` in all three DTOs and in the three payloads that carry
  it) is `type: string` in both the OpenAPI file and the event schemas, never `number` (L6).
- **AC4.** `shouldConformToPaymentsOpenApiContract` and every `PaymentEventPayloadContractTest`
  method pass against the real code. They fail if a route or field is added without a matching
  contract update (both-direction completeness plus the expected-schema-map self-guard).
- **AC5.** `pom.xml` gains exactly the one dependency. The full suite stays green.
- **AC6 (Finding 1).** A test deserializes `{"amount":"10.5", ...}` into `CreateInvoiceRequest`
  with a plain `ObjectMapper` and gets `new BigDecimal("10.5")`.
- **AC7 (Findings 5/8).** For every documented field, the contract test maps the record
  component's Java type to the expected schema type/format. `UUID` → `string`/`uuid`. `Instant`
  → `string`/`date-time`. `BigDecimal` and `String` → `string`. `short`/`int` → `integer`.
  `boolean` → `boolean`. The `InvoiceState` schema `enum` equals `InvoiceState.values()`.
- **AC8 (Finding 7).** Every documented operation carries `security: [{bearerAuth: [MERCHANT]}]`.
  `bearerAuth` is HTTP bearer/JWT.
- **AC9 (G3).** The `Problem.type` `enum` equals, as a set, the 11 `public static final URI`
  values on `ProblemTypes`, read by reflection.
- **AC10 (G2).** Each event schema's `description` names its real partition key:
  `invoiceUuid`, `paymentUuid`, `paymentUuid`, `receiptUuid`.

## Required Tests

- **Named:** `shouldConformToPaymentsOpenApiContract` (R31), an umbrella method in the
  `NotificationOpenApiContractTest` shape. It covers route completeness in both directions,
  per-schema field matching, the AC7 type/format check, the AC8 security check, the AC9
  `Problem` enum check, and the expected-schema-map self-guard.
- `PaymentEventPayloadContractTest`: four methods, one per event. Each covers field matching,
  required/optional status, `additionalProperties: false` (Finding 6), and the AC7 type/format
  check.
- One AC6 string-amount deserialization test.
- All tests are plain JUnit with Jackson and reflection: no Spring context, no Testcontainers.

## Constraints

- No runtime code, so performance, thread-safety, transaction and null-handling constraints
  don't apply.
- Security: the contract documents existing `@PreAuthorize("hasRole('MERCHANT')")` enforcement.
  It does not re-implement it.
- Module boundaries: both test classes are read-only consumers of existing classes. They add no
  new production dependency direction.
- T23 governance (continuity): a new problem type goes into `ProblemTypes`, and the AC9 test
  then forces the `Problem.type` enum to be updated too.

## Open Questions

No blockers for T24.

- **Follow-up (owner: human, out of T24 scope): partition-key drift (G2).** `design.md` §4c
  says all payments events are keyed by `invoiceUuid`. The code keys `payment.seen` and
  `payment.finalized` by `paymentUuid` and `receipt.issued` by `receiptUuid`.
  `PaymentFinalizedPayload` has no `invoiceUuid`. Decide later between two options: change the
  spec to match the code, or re-key the code. Re-keying would need an invoice lookup in
  `PaymentService.applyFinalized` and would change per-invoice ordering guarantees for
  consumers.
