# payment · T24 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Author `contracts/api/payments.yaml` and the four `contracts/events/payments/*.v1.schema.json`
files, documenting this service's four real HTTP routes and four real published events exactly
as already built; add contract-conformance tests mirroring `auth`'s/`notification`'s own
established patterns (R31).

## Purpose

Closes the last standing-rule gap in `agents.md`'s own list this service hadn't yet satisfied:
every sibling service already has a real, tested OpenAPI contract and event schemas; this
service's own equivalents don't exist yet, despite every route/payload they'd document having
already been built and shipped (T06, T07, T22; T04, T08/T11, T19).

## Scope

**In:** `contracts/api/payments.yaml`; four event schema files; one new OpenAPI contract test
class; one new event-payload contract test class; the one new `jackson-dataformat-yaml`
test-scope dependency.

**Out:** Any production code change (every route/payload already exists, unchanged); error
response documentation (explicitly out of scope, mirrors every sibling contract's own R47
precedent — re-confirmed directly, not revisited, even though `ProblemTypes`, T23, now makes
this a real catalogue rather than a placeholder); any contract for `contracts/api/crypto-internal.yaml`
(already exists, owned by `crypto-service`, read-only to this service).

## Business Rules

- **R31.**

## Locked Decisions

- **L6/L8/L10/L12 (continuity)** — see Phase 1.
- **New, resolved directly (low-risk structural choices, no `AskUserQuestion` needed):**
  - **One combined event-payload contract test class** (`PaymentEventPayloadContractTest`, one
    method per payload, four total), not four separate files. `auth-service`'s own split
    (`UserLifecycleEventPayloadContractTest`, `EmailRequestedEventPayloadContractTest`) reflects
    two payloads published by genuinely different modules at different times; this service's
    four payloads are closely related (one topic family, one task authoring all four schemas at
    once) — one class with four clearly-named methods is less duplication for the identical
    required/declared-field-matching logic, with no loss of per-payload granularity in failure
    reporting (JUnit still reports each method independently).
  - **One OpenAPI contract test class** (`PaymentsOpenApiContractTest`), mirroring
    `NotificationOpenApiContractTest`'s own exact method set (route completeness both
    directions, per-component schema-shape matching, security-scheme check, per-route expected-
    schema map with its own self-guard) — the closest size-analog (2 routes there, 4 here).
  - **Date-range query params documented as `type: string, format: date-time`**, not
    `format: date` — matches `ExportController`'s own real `Instant`-typed `@RequestParam`
    exactly (T22), not a calendar-date convention this service never actually uses.

## Dependencies

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (new, test-scope) — added exactly
  where the pom's own standing comment already said it would land.
- No other new dependency.

## Inputs / Outputs / State Changes

None — this task produces static documentation files and test classes; no runtime input/output/
state change anywhere.

## Files to Create

- `contracts/api/payments.yaml`.
- `contracts/events/payments/invoice-created.v1.schema.json`.
- `contracts/events/payments/payment-seen.v1.schema.json`.
- `contracts/events/payments/payment-finalized.v1.schema.json`.
- `contracts/events/payments/receipt-issued.v1.schema.json`.
- `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`.
- `services/payment/src/test/java/com/themistra/payment/events/PaymentEventPayloadContractTest.java`.

## Files to Modify

- `services/payment/pom.xml` — add the one new test-scope dependency.

## Files NOT to Modify

Any of the four payload records, `InvoiceController`/`ExportController`, any DTO, any other
production class; `spec/`.

## Acceptance Criteria

- **AC1.** `payments.yaml` documents all four real routes with request/response schemas matching
  `CreateInvoiceRequest`/`InvoiceResponse`/`TransactionExportRow`'s own real serialized shape
  field-for-field (component schemas: `CreateInvoiceRequest`, `InvoiceResponse`,
  `TransactionExportRow`).
- **AC2.** Each of the four event schema files matches its real payload record's actual
  serialized shape field-for-field, `additionalProperties: false`, every field in `required`
  (none of the four payload records has an optional/nullable field, confirmed by direct read).
- **AC3.** Every monetary field (`amount` in `CreateInvoiceRequest`/`InvoiceResponse`/
  `TransactionExportRow`/`InvoiceCreatedPayload`/`PaymentFinalizedPayload`/`ReceiptIssuedPayload`)
  is `type: string` in both the OpenAPI file and the event schemas, never `type: number`.
- **AC4.** `PaymentsOpenApiContractTest.shouldConformToPaymentsOpenApiContract` and
  `PaymentEventPayloadContractTest` both pass against the real code, and both fail if a route/
  field is added without a matching contract update (the self-guard pattern).
- **AC5.** `pom.xml` gains exactly the one new dependency; full suite stays green.

## Required Tests

- **Named test:** `shouldConformToPaymentsOpenApiContract` (R31).
- Four payload-shape methods in `PaymentEventPayloadContractTest`, one per event.
- Bidirectional route-completeness, security-scheme presence, and the
  `expectedResponseSchemasCoverEveryControllerRoute`-style self-guard — all mirrored directly
  from `NotificationOpenApiContractTest`'s own established shape.

## Constraints

- **Performance/thread-safety/transaction/null-handling:** n/a — no runtime code.
- **Security:** none of this task's own new code touches authentication/authorization; the
  contract file itself documents `bearerAuth` + `ROLE_MERCHANT` scoping already enforced in code
  (not re-implemented here).
- **Module boundaries:** both new test classes live in `common`/`events` respectively, read-only
  consumers of the four payload records and two controllers — no new cross-module dependency
  direction introduced.

## Open Questions

No blockers. Both Phase 1 structural questions resolved directly above.
