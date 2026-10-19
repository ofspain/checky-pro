# payment · T24 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R31.** Where `contracts/api/payments.yaml` is authored, service responses and generated
  client models SHALL conform to it, and every published event SHALL conform to its schema
  under `contracts/events/payments/`.
- **R27/R28 (continuity).** The export and invoice-read endpoints this task documents are
  merchant-scoped; the contract itself documents request/response shape, not the authorization
  rule (already enforced in code, not re-specified here).

## Locked Decisions

- **L6 (continuity).** Every documented monetary field (`amount` in `InvoiceCreatedPayload`,
  `PaymentFinalizedPayload`, `ReceiptIssuedPayload`, and `TransactionExportRow`) is a decimal
  string in the schema, never `type: number`.
- **L8 (continuity).** The four events this task documents (`invoice.created`, `payment.seen`,
  `payment.finalized`, `receipt.issued`) are exactly the four L8 already names as outbox-
  published — no fifth event exists to document, no event here is missing from L8's own list.
- **L10/L12 (continuity).** No change — this task adds documentation and tests only, touching no
  production security/module-boundary code.

## Files involved

**New (none exist yet):**
- `contracts/api/payments.yaml`.
- `contracts/events/payments/invoice-created.v1.schema.json`.
- `contracts/events/payments/payment-seen.v1.schema.json`.
- `contracts/events/payments/payment-finalized.v1.schema.json`.
- `contracts/events/payments/receipt-issued.v1.schema.json`.
- At least one new test class under `services/payment/src/test/java/com/themistra/payment/`
  (exact class shape — Phase 2).

**Existing, read-only (the real shapes this task must document accurately, not invent):**
- `invoice/InvoiceController.java`, `export/ExportController.java` — the four real HTTP routes.
- `invoice/dto/{CreateInvoiceRequest,InvoiceResponse}.java` — request/response DTOs for the
  invoice endpoints.
- `export/TransactionExportRow.java` — the export response row shape (T22).
- `invoice/event/InvoiceCreatedPayload.java`, `payment/event/{PaymentSeenPayload,
  PaymentFinalizedPayload}.java`, `receipt/event/ReceiptIssuedPayload.java` — the four real
  payload shapes.
- `common/ProblemTypes.java` (T23) — not directly embedded in the OpenAPI file (error responses
  are explicitly out of scope, per every sibling contract's own R47 precedent), but worth
  re-confirming this exclusion is still the right call at Phase 2 given it's now a genuinely
  real, named catalogue, not a placeholder.

## Dependencies

- `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` — new, test-scope, explicitly
  pre-planned for this exact task (confirmed via the standing `pom.xml` comment, Phase 0).
- No other new dependency, config key, or production code change.

## Acceptance Criteria

- **AC1.** `contracts/api/payments.yaml` documents all four real routes
  (`POST /api/v1/invoices`, `GET /api/v1/invoices`, `GET /api/v1/invoices/{invoiceUuid}`,
  `GET /api/v1/exports/transactions`) with request/response schemas matching their real DTOs'
  actual serialized shape field-for-field.
- **AC2.** Each of the four `contracts/events/payments/*.v1.schema.json` files matches its real
  payload record's actual serialized shape field-for-field, with `additionalProperties: false`
  and every non-nullable field in `required`.
- **AC3.** Every monetary field in both the OpenAPI file and the four event schemas is
  `type: string`, never `type: number` (L6).
- **AC4.** A reflection/Jackson-based contract test (no Spring context, no Testcontainers,
  mirroring the three existing sibling patterns exactly) proves AC1/AC2 hold against the real
  code, not merely against hand-written examples — and fails if a future route/field is added to
  the code without a matching contract update (the `expectedResponseSchemasCoverEveryControllerRoute`-
  style self-guard).
- **AC5.** `services/payment/pom.xml` gains exactly one new test-scope dependency
  (`jackson-dataformat-yaml`), nothing else.

## Tests required

- **Named test:** `shouldConformToPaymentsOpenApiContract` (R31) — mirrors
  `shouldConformToNotificationsOpenApiContract`'s own umbrella-method shape, calling each
  narrower assertion method in sequence.
- Four payload-shape contract tests (one per event), mirroring
  `UserLifecycleEventPayloadContractTest`'s own pattern — exact class grouping (one combined
  class vs. four separate ones) decided at Phase 2.
- Boundary tests implied by the established sibling pattern, not separately named: bidirectional
  route completeness (every controller route documented, every documented route real), per-
  schema required/declared-field matching, security-scheme presence, and the self-guard against
  silently forgetting to update the expected-schema map when a route is added.

## Open Questions

No genuine blockers. Both Phase 0 "known gaps" (combined vs. separate event-test classes;
operationId/naming conventions) are low-risk, pure-engineering choices resolvable directly at
Phase 2 — consistent with this session's established pattern for this class of decision.
`package.md` §11 was re-checked; nothing scoped to T24.
