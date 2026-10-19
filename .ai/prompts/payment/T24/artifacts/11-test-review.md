# payment · T24 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the actual updated contract tests against the frozen brief's acceptance criteria.

---

## Gap 1 — CSV column-order test only checks a substring

- **Why it matters:** `csvDescriptionListsTheExportColumnsInRecordOrder` proves the description contains the component names in order, but it does not prove the description lists *only* those columns or that no extra/missing column appears. A description that adds an extra word or omits a column could still contain the substring.
- **Suggested test:** Parse the claimed column list from the description (e.g., extract the comma-separated words after "columns in this order:") and assert it equals the exact ordered list of `TransactionExportRow` record components.

---

## Gap 2 — `Content-Disposition` filename format is not verified against the controller

- **Why it matters:** The contract documents `attachment; filename="transactions-<fromEpochSeconds>-<toEpochSeconds>.csv"`. The actual controller builds the filename from `from.getEpochSecond()` and `to.getEpochSecond()`. A drift between the documented template and the code would not be caught.
- **Suggested test:** Add a test that reads the documented header description and asserts it contains the exact pattern the controller uses, or add a MockMvc test for the CSV response that asserts the real header matches the documented template.

---

## Gap 3 — Partition-key check still only reads the schema description

- **Why it matters:** Each event schema's description says "Partition key = X", but nothing verifies that X is the key actually passed to `outboxPublisher.publish(...)`. A refactor that changed the publish key (e.g., from `paymentUuid` to `invoiceUuid`) would leave the description correct but the behavior wrong.
- **Suggested test:** Either mock `OutboxPublisher` in a focused test and assert the second argument of each `publish(...)` call, or add a reflection map from event class to expected key and assert it matches the code's call sites.

---

## Gap 4 — No validation that `payments.yaml` is a valid OpenAPI 3.0.3 document

- **Why it matters:** `YAMLMapper` proves the file is valid YAML and that the tests' expected paths exist, but it does not prove the file is a valid OpenAPI spec. A malformed security scheme, an invalid `$ref`, or a missing required OpenAPI field could pass the current suite.
- **Suggested test:** Add a dependency-free structural check (e.g., assert `openapi` starts with "3.0", `paths` is an object, every operation has `responses`, every `$ref` resolves inside the file) or introduce a test-scope OpenAPI parser such as `swagger-parser`.

---

## Gap 5 — Response/request component schemas do not assert `additionalProperties: false`

- **Why it matters:** Event schemas enforce `additionalProperties: false`, but the OpenAPI DTO component schemas do not, and no test checks it. A future change that adds an undeclared field to `InvoiceResponse` or `TransactionExportRow` would still pass the contract tests.
- **Suggested test:** Add `additionalProperties: false` to `CreateInvoiceRequest`, `InvoiceResponse`, and `TransactionExportRow`, and assert it in `everyComponentSchemaMatchesItsRealRecordShape`.

---

## Gap 6 — Outbound monetary amount fields have no pattern or whole-unit assertion

- **Why it matters:** `CreateInvoiceRequest.amount` now has a pattern test, but `InvoiceResponse.amount` and `TransactionExportRow.amount` are only checked to be `type: string`. They are always whole base units in practice, yet the contract does not document or test that.
- **Suggested test:** Add `pattern: '^[0-9]+$'` to the two outbound amount fields and test it, or at minimum assert their descriptions state "whole number of token base units."

---

## Gap 7 — No test verifies the `format` parameter precedence description

- **Why it matters:** The export operation description explains that `format` takes precedence over `Accept`. There is no test that the contract text actually says this, and no behavioral test at the contract layer.
- **Suggested test:** Assert that the export operation's `description` contains the phrases "format query parameter" and "takes precedence over Accept" (or similar), or rely on the existing `ExportControllerTest` and simply reference it in the test manifest.

---

## Summary

The Phase 10 suite is now comprehensive for T24: route completeness, security scoping, component shape, type/format mapping, monetary-string type, validation limits, the amount pattern, Problem extensions, and event schema strictness are all covered. The highest-value remaining additions are Gap 3 (real partition-key guard) and Gap 4 (OpenAPI structural validity). Gaps 1, 2, 5, 6, and 7 are lower-priority polish.
