# payment · T24 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, `spec/payment-service/requirements.md` (R31), and the real controllers/DTOs/payload records.

---

## Finding 1 — `CreateInvoiceRequest.amount` description contradicts itself and L6

- **Issue:** The contract describes `amount` as a "positive decimal string" and adds "A JSON number is also accepted for compatibility." That directly conflicts with `agents.md` L6 ("monetary values are decimal strings, never JSON numbers") and with the service's own `InvoiceService` validation, which rejects fractional base-unit amounts. A contract-conforming client that sends `"10.5"` (a positive decimal string) receives a 400.
- **Evidence:** `contracts/api/payments.yaml` lines 160-166; `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java` lines 204-216 (rejects non-whole base units); `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` line 15 (`NUMERIC(78, 0)`).
- **Recommendation:** Change the description to "whole number of token base units as a decimal string", remove the JSON-number acceptance note, and add `pattern: '^[1-9][0-9]*$'`. Update the contract test to use a whole-number value (see Finding 2).
- **Confidence:** High — the contract text and code are in direct tension.

---

## Finding 2 — `createInvoiceRequestAcceptsADecimalStringAmount` uses an invalid business value

- **Issue:** The test deserializes `"amount":"10.5"` to prove a string amount is accepted. That value is a decimal string, but the service rejects it at runtime. The test proves Jackson binding, not service acceptance, and could mislead a reader into thinking fractional base units are valid.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java` lines 252-258.
- **Recommendation:** Use `"1000000"` (a valid whole base-unit value). The test still proves string-to-BigDecimal deserialization.
- **Confidence:** High.

---

## Finding 3 — The `Problem` schema omits the extension members the handlers actually emit

- **Issue:** The `Problem` schema documents `type`, `title`, `status`, `detail`, and `instance`, but `ApiExceptionHandler` also emits `violations` (validation errors) and `trace_id` (internal errors). These are real response fields that a generated client will not know about unless they are declared. `additionalProperties` defaults to `true` in OpenAPI, so this is a documentation gap, not a contradiction.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` lines 55, 67, 131; `contracts/api/payments.yaml` lines 249-277.
- **Recommendation:** Add optional `violations` (array of objects with `field` and `message` strings) and `trace_id` (string) to the `Problem` schema, mirroring the actual handler payloads.
- **Confidence:** Medium — the schema is not wrong, but it is incomplete.

---

## Finding 4 — OpenAPI 3.0 ignores the documented `Accept` header parameter

- **Issue:** `payments.yaml` declares `Accept` as a header parameter on `GET /api/v1/exports/transactions`, but OpenAPI 3.0.3 §4.7.12.1 says parameters named `Accept`, `Content-Type`, or `Authorization` "SHALL be ignored" by tooling. The format-negotiation rule is only in the operation description, which tooling does keep, but a generated client may not expose the header.
- **Evidence:** `contracts/api/payments.yaml` lines 108-113.
- **Recommendation:** Either drop the `Accept` parameter and rely on the description plus the two response content types, or keep it with a comment acknowledging that it is documentation-only. The Phase 4 freeze accepted this, so the disposition should be explicit.
- **Confidence:** Low — the implementation works; only contract tooling behavior is affected.

---

## Finding 5 — `PaymentsOpenApiContractTest.operation()` fails with NPE on missing routes

- **Issue:** The helper chains `.get(path).get(method)` without null checks. If a route is renamed or removed, the resulting NPE makes the failure point at the test helper rather than the missing route.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java` lines 406-408; Phase 6 mutation check produced errors rather than failures when `{invoiceUuid}` was renamed.
- **Recommendation:** Add explicit assertions before chaining: `assertThat(contract.get("paths").has(path)).as("contract missing path %s", path).isTrue();` and similarly for the method.
- **Confidence:** Low — diagnostic quality only.

---

## Finding 6 — Contract tests do not verify declared validation constraints

- **Issue:** `CreateInvoiceRequest` carries validation annotations (`@Pattern` on `chain`, `@Min/@Max` on `tokenDecimals`, `@Size(max=140)` on `reference`). The OpenAPI contract documents the chain enum, tokenDecimals min/max, and reference maxLength, but `PaymentsOpenApiContractTest.assertTypeMatches` only checks `type`/`format`/`nullable`. A regression that removed `maxLength: 140` or the chain enum would not be caught.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/invoice/dto/CreateInvoiceRequest.java`; `contracts/api/payments.yaml` lines 151-171; `PaymentsOpenApiContractTest.java` lines 353-383.
- **Recommendation:** Extend `assertTypeMatches` to compare enum/min/max/maxLength/pattern constraints against the actual annotations on the DTO record components, or add separate tests for the validated fields.
- **Confidence:** Low — the contract is correct now, but the test suite is weaker than it could be.

---

## Finding 7 — No test verifies that response schemas reject extra properties

- **Issue:** The event-schema tests assert `additionalProperties: false`, but the OpenAPI response/request component schemas do not set `additionalProperties: false` and no test checks it. A future change that accidentally adds an undeclared field to a response would still pass the current contract tests (which only check that declared fields exist).
- **Evidence:** `contracts/api/payments.yaml` component schemas; `PaymentsOpenApiContractTest.java` `everyComponentSchemaMatchesItsRealRecordShape`.
- **Recommendation:** Add `additionalProperties: false` to the three DTO component schemas and assert it in the contract test, matching the stricter event-schema convention.
- **Confidence:** Low — API responses are controlled by the server, so extra fields are unlikely, but the asymmetry with event schemas is worth noting.

---

## Summary

The T24 implementation is a thorough, well-tested contract close-out. The most important fixes before considering it complete are Finding 1 (the `amount` description/pattern) and Finding 2 (the invalid test value). Findings 3–7 are polish, documentation completeness, or test-depth gaps that should be dispositioned at the Phase 9 gate. No production code changes are needed.
