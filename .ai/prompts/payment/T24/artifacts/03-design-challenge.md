# payment · T24 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/requirements.md` (R31), `spec/payment-service/design.md` §4c, the existing sibling contracts, and the real payload/DTO classes.

---

## Finding 1 — `CreateInvoiceRequest.amount` is `BigDecimal`, but the contract (and L6) says decimal string

- **Issue:** `CreateInvoiceRequest.amount` is typed `BigDecimal`. Jackson serializes `BigDecimal` as a JSON number by default, yet L6 says "monetary values are decimal strings, never JSON numbers" and AC3 requires every monetary field in the contract be `type: string`. A field-name-only contract test would pass, but the contract would not describe the wire format the service actually emits if a client mirrored the DTO, and a generated client would send/receive a string while the Java DTO expects a number-shaped value.
- **Severity:** Medium — a real inconsistency between code and contract.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/invoice/dto/CreateInvoiceRequest.java` line 24; TIB AC3.
- **Recommended brief amendment:** Either (a) add `@JsonFormat(shape = JsonFormat.Shape.STRING)` to `CreateInvoiceRequest.amount` (a tiny production-code change), or (b) change the DTO field to `String amount` (mirroring `InvoiceResponse`/`TransactionExportRow` and L6 exactly). If neither is acceptable, explicitly document the inconsistency in the contract with a note that the request body accepts both string and number representations.

---

## Finding 2 — Some event payload fields can be null at runtime, contradicting "all fields required"

- **Issue:** The TIB states that none of the four event payload records has an optional/nullable field and that every field should be in `required`. That is not true for at least `InvoiceCreatedPayload.reference` (the request DTO's `reference` is `@Size` but not `@NotNull`, so it can be null) and `PaymentFinalizedPayload.fromAddress` (the `payments.from_address` column is nullable, and `Payment.createSeen` initializes it to null). A schema with `additionalProperties: false` and `required: [..., "reference", ...]` would reject real, valid events.
- **Severity:** High — this would cause contract-test failures against real data and could break downstream consumers.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/invoice/dto/CreateInvoiceRequest.java` line 26; `services/payment/src/main/java/com/themistra/payment/invoice/event/InvoiceCreatedPayload.java`; `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (`payments.from_address` nullable); `services/payment/src/main/java/com/themistra/payment/payment/event/PaymentFinalizedPayload.java`.
- **Recommended brief amendment:** Mark `reference` and `fromAddress` as not required in their respective event schemas (and add `type: ["string", "null"]` if using JSON Schema 2020-12), or change production code to emit empty strings instead of nulls. Do not assert "all fields required" for these two payloads.

---

## Finding 3 — The CSV response for the export endpoint is not addressed

- **Issue:** `GET /api/v1/exports/transactions` returns either JSON or CSV depending on the `format`/`Accept` values. The TIB only mentions component schema `TransactionExportRow` and does not say how the CSV response is documented in `payments.yaml`.
- **Severity:** Medium — the contract will be incomplete for one of the two real response formats.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` lines 56-59; TIB AC1.
- **Recommended brief amendment:** Document the CSV response under `responses.200.content.text/csv` with a `schema: { type: string }` and an explanatory description, or explicitly decide that CSV is out of scope for the OpenAPI contract (with a rationale). Either way, freeze the choice.

---

## Finding 4 — `$id` base URI and OpenAPI metadata are not frozen

- **Issue:** The TIB does not specify the JSON Schema `$id` base for the four event files or the OpenAPI `info.title/version`. Sibling services use `https://checky.pro/contracts/events/<service>/...` and a consistent version string. A wrong `$id` would make schema resolution and cross-service references inconsistent.
- **Severity:** Low — easy to fix, but should be frozen.
- **Evidence:** `contracts/events/auth/user-lifecycle.v1.schema.json` line 3; `contracts/api/auth.yaml` lines 1-8.
- **Recommended brief amendment:** Pin `$id = "https://checky.pro/contracts/events/payments/<event>.v1.schema.json"` for each event file, and `payments.yaml` `info.title = "payment-service API"`, `info.version = "1.0"`, matching sibling conventions.

---

## Finding 5 — No test plan verifies monetary fields are `type: string` in the contract files

- **Issue:** AC3 requires monetary fields to be `type: string` in both OpenAPI and event schemas. The TIB's test approach mirrors the notification contract test, which only checks required/declared field presence, not schema type. A contract that accidentally declared `amount` as `number` would pass the proposed tests.
- **Severity:** Medium — AC3 is not verifiable with the proposed test shape alone.
- **Evidence:** `services/notification/src/test/java/com/themistra/notification/common/NotificationOpenApiContractTest.java` lines 83-108; `services/notification/src/test/java/com/themistra/notification/consumer/dto/UserLifecycleEventContractTest.java` lines 33-51.
- **Recommended brief amendment:** Add explicit assertions that each `amount` property in `payments.yaml` and the four event schemas has `"type": "string"`.

---

## Finding 6 — No test plan verifies `additionalProperties: false` on event schemas

- **Issue:** AC2 requires each event schema to set `additionalProperties: false`. The proposed field-presence test does not assert this flag is present or true; a schema missing it would pass.
- **Severity:** Low — easy to add.
- **Evidence:** TIB AC2; notification event contract test does not assert `additionalProperties`.
- **Recommended brief amendment:** Add `assertThat(schema.get("additionalProperties").asBoolean()).isFalse()` to each event payload contract test.

---

## Finding 7 — `ROLE_MERCHANT` scoping is not reflected in the contract security declarations

- **Issue:** The TIB says the contract documents `bearerAuth` + `ROLE_MERCHANT` scoping, but it does not say how. OpenAPI can express required scopes under a security requirement (e.g., `bearerAuth: [MERCHANT]`). Without this, a generated client or documentation reader cannot tell that `/api/v1/invoices` and `/api/v1/exports/transactions` require the merchant role, not just any authenticated caller.
- **Severity:** Low — the enforcement is already in code, but the contract would be under-specified.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceController.java` lines 52, 72, 84; `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` line 43.
- **Recommended brief amendment:** Freeze the security scheme as `bearerAuth` (HTTP bearer) and set each merchant endpoint's `security` to `bearerAuth: [MERCHANT]`. Add a contract test that verifies every route except public ones carries this requirement.

---

## Finding 8 — Format/pattern constraints (UUID, date-time, enums) are not required

- **Issue:** The TIB only requires field-for-field matching. A minimally useful contract should also specify `format: uuid` for UUID fields, `format: date-time` for `Instant` fields, and enums for `chain` (`ETHEREUM`, `TRON`) and `InvoiceState`. Without these, the contract is weaker than the code it documents.
- **Severity:** Low — not a correctness bug, but a contract quality gap.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/invoice/dto/CreateInvoiceRequest.java` line 21; `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceState.java`; existing sibling schemas use `format` and `enum`.
- **Recommended brief amendment:** Add an AC or constraint requiring `format`/`enum` constraints where the code has them (UUID, date-time, chain enum, state enum), and extend the contract tests to verify at least the UUID and date-time formats serialize/deserialize correctly.

---

## Finding 9 — The export endpoint's query parameters are not explicitly documented

- **Issue:** `GET /api/v1/exports/transactions` requires `from` and `to` (date-time strings), accepts an optional `format` (`csv`|`json`), and respects the `Accept` header. The TIB mentions `format: date-time` for the date params but does not explicitly require documenting all params and the header-driven format negotiation.
- **Severity:** Low — completeness gap.
- **Evidence:** TIB Locked Decisions (date-range paragraph); `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` lines 42-47.
- **Recommended brief amendment:** Add an acceptance criterion that `payments.yaml` documents `from`, `to`, and `format` query parameters, and notes that `Accept: text/csv` is an alternative format selector.

---

## Summary

The T24 brief correctly scopes the contract work as documentation-only. The most important amendments before freezing are (1) resolving the `CreateInvoiceRequest.amount` number-vs-string mismatch, (2) making nullable event fields optional in schemas, and (3) deciding how to document the CSV response. Gaps 4–9 are smaller completeness/clarity items that should be dispositioned at the Phase 4 gate.
