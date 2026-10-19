# payment · T24 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

The review covers the Phase 6 diff (commit `361b82ba`), checked against the frozen brief and
`agents.md`. It only reports findings; any fixes are Phase 9's job.

## Checks performed

| Check | Result |
|---|---|
| **Production code** | Untouched: the diff covers only `contracts/`, `pom.xml`, and 3 test files. |
| **Thread-safety, transactions, idempotency** | Don't apply: there is no runtime code. |
| **Module boundaries** | Both test classes only read existing public records and controllers. No `@Entity` is imported, so `ArchitectureTest`'s cross-module entity rule doesn't apply. |
| **Money types (L6)** | Every `amount` in all 5 contract files is `type: string`. This is enforced by `assertTypeMatches` and was confirmed by the Phase 6 mutation check. |
| **Secrets** | None. The test fixtures use obviously fake values: `0xtoken`, `s3://bucket/key`, and a zeroed KMS ARN. |
| **YAML validity** | The multi-line flow sequences in `required: [...]` parse correctly, which the green `componentSchemas*` and `everyComponentSchema*` tests prove. |
| **Claims in descriptions** | Each one was re-checked against code: ATTESTED-only export and `finalizedAt` range (`TransactionExportRepository.java:46-48`), 404-not-403 (`InvoiceController.java:80-82`), CSV column order (`ExportController.java:102-103`), and `Content-Disposition` format (`ExportController.java:61`). |
| **Suite** | 483/483 green. |

## Findings

### F1. The contract describes `amount` as a "positive decimal string", but the service only accepts whole base units

- **Severity:** Medium. A client that conforms to the contract can send `"10.5"`, and the server
  rejects it with a 400.
- **Evidence:**
  - `contracts/api/payments.yaml`, `CreateInvoiceRequest.amount` description.
  - `InvoiceService.java:204-216` rejects any value with a fractional part (R4 AC2).
  - The column is `NUMERIC(78, 0)` (`V1__payments_baseline.sql:15`).
- **Recommendation:**
  - State "a whole number of token base units" in every `amount` description: the 3 OpenAPI
    schemas and the 3 event schemas that carry it.
  - Add `pattern: '^[1-9][0-9]*$'` to `CreateInvoiceRequest.amount`.
  - Decide in Phase 9 whether to add `pattern: '^[0-9]+$'` to the outbound `amount` fields as
    well. Those values come from `toPlainString()` of `NUMERIC(78,0)` and observed chain
    amounts, so they are always integral.

### F2. The AC6 test uses an invalid business value

- **Severity:** Low (readability, could mislead).
- **Evidence:** `PaymentsOpenApiContractTest.createInvoiceRequestAcceptsADecimalStringAmount`
  deserializes `"amount":"10.5"`. That value parses, but the real service rejects it (see F1).
- **Recommendation:** Use a valid base-unit value such as `"1000000"`. The test still proves
  that a string deserializes into `BigDecimal`, and it no longer implies fractional amounts are
  accepted.

### F3. OpenAPI 3.0 ignores a header parameter named `Accept`

- **Severity:** Low.
- **Evidence:**
  - `payments.yaml` declares `Accept` as `in: header` on `exportTransactions`.
  - OpenAPI 3.0.3 §4.7.12.1 says header parameters named `Accept`, `Content-Type` or
    `Authorization` "SHALL be ignored", so code generators and Swagger UI will drop it.
  - The selection rule is also already written in the operation `description`, which tooling
    does keep.
- **Recommendation:** This one needs the human's call at Phase 9, because Phase 4 accepted
  Finding 9 as "Accept is a documented optional header param". There are two options:
  - (a) Keep it, as frozen. It is harmless, and the operation description covers tooling.
  - (b) Drop the parameter and keep only the description and the two response content types.
    `exportEndpointDocumentsItsQueryParametersAndAcceptHeader` would then be adjusted.

  I lean (a), to avoid reopening a frozen decision over something harmless.

### F4. The `Problem` schema omits the extension members the handlers actually emit

- **Severity:** Low.
- **Evidence:**
  - `ApiExceptionHandler.java:55` and `:67` set `violations`.
  - `ApiExceptionHandler.java:131` sets `trace_id`.
  - `components.schemas.Problem` doesn't declare either one. It doesn't forbid them either
    (`additionalProperties` defaults to `true`), so this is a gap in the documentation, not a
    contradiction.
- **Recommendation:** Add optional `violations` (an array; check its item shape at
  `ApiExceptionHandler.java:50-55`) and `trace_id` (string) to `Problem`. Make sure this stays
  within G3's "minimal" scope: no per-route error docs.

### F5. A renamed or removed path makes some tests fail with an NPE instead of a clear message

- **Severity:** Low (diagnostics only; the suite still goes red).
- **Evidence:**
  - In the Phase 6 mutation check, renaming `{invoiceUuid}` produced 2 errors, not failures.
  - Both came from `operation(contract, route)` (`PaymentsOpenApiContractTest.java`), which
    chains `.get(path).get(method)` with no null check. It is used by
    `invoiceUuidPathParameterIsDeclared` and `everyOperationResponseReferencesTheExpectedSchema`.
- **Recommendation:** Have `operation()` assert that the path and method exist, with a message
  naming the route, so the failure points at the real cause.

### F6. The partition-key check (AC10) only verifies the description text, not the key the code uses

- **Severity:** Low. This matches the frozen AC10 exactly, but it is a weak guard: if someone
  changes an `outboxPublisher.publish(...)` key, nothing goes red.
- **Evidence:**
  - `PaymentEventPayloadContractTest.assertMatches` asserts on the `description` string.
  - The real keys are the second argument at `InvoiceService.java:72`,
    `PaymentService.java:121` and `:262`, and `ReceiptService.java:193`.
- **Recommendation:** Leave it as is for T24. Closing it properly would mean capturing the
  publish arguments behind a mocked outbox, a heavier test that exceeds AC10. Note it next to
  the G2 partition-key follow-up so the eventual fix there adds the real guard.

## Not findings (considered and dismissed)

- **The `format` enum `[csv, json]` while the server is case-insensitive.** The contract
  documents the canonical values, and the description says case-insensitive. A stricter client
  is still compatible.
- **The `assertTypeMatches` helper is duplicated across the two classes.** This is deliberate
  (Phase 5): the brief authorizes no third file.
- **`receipt-issued` leaves 5 always-present fields optional.** This follows `design.md` §4c's
  pinned schema exactly (G1).
- **The `T01SkeletonRegressionTest` flip.** It is already disclosed as Phase 6 deviation 0, and
  it follows the T08 ShedLock precedent. It's for Kimi to review at Phase 8.
