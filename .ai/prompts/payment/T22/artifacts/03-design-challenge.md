# payment · T22 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/design.md` (especially O5), the current codebase, and the T22 task statement.

---

## Finding 1 — CSV escaping is assumed rather than enforced

- **Issue:** The TIB rejects a generic CSV writer because every O5 column is "constitutionally comma-free and newline-free." That is true for correctly-formed data, but the export is a tax artifact read by accountants/tools. A single malformed or tampered row (e.g., a raw-SQL insert that puts a comma in `pay_to_address` or a newline in a future column) would silently shift columns or rows, producing an invalid tax export with no error returned to the merchant.
- **Severity:** Medium — correctness depends on a data invariant that the export itself does not control.
- **Evidence:** TIB Locked Decisions (CSV paragraph); `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` (adjacent reporting surface) shows how easily a normalization assumption can be wrong.
- **Recommended brief amendment:** Either add a minimal RFC 4180 writer/escaper (quote fields containing `,`, `"`, or newline), or explicitly accept the risk and add a CI test that asserts no export column can ever contain those characters plus a class-level comment documenting the invariant.

---

## Finding 2 — Source of `chain`, `tokenContractAddress`, `txHash`, `amount`, and `payToAddress` is not frozen

- **Issue:** The `TransactionExportRow` fields exist, but the TIB does not say whether they come from `invoices` (expected values) or `payments` (observed on-chain values). A tax export must reflect what actually happened on chain. `payments` carries the observed `chain`, `tx_hash`, `token_contract_address`, `amount`, and `to_address`; `invoices` carries the expected ones. If the implementation joins through `invoices` for these columns, a mismatched/tampered payment could be misreported.
- **Severity:** High — R27 asks for "finalized/attested payment history," not "invoice expectations."
- **Evidence:** `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (duplicate column names on `invoices` and `payments`); TIB Outputs (`TransactionExportRow` fields).
- **Recommended brief amendment:** Pin the column sources: `invoiceUuid` and `tokenDecimals` come from `invoices`; `paymentUuid`, `chain`, `txHash`, `tokenContractAddress`, `amount`, `payToAddress`, `finalizedAt`, and `attestedAt` come from `payments`; `receiptUuid` and `receiptUri` come from `receipts`.

---

## Finding 3 — `receiptUri` format and object type are not frozen

- **Issue:** `ReceiptStore` writes both a JSON object (`{prefix}.json`) and a PDF (`{prefix}.pdf`). The TIB says the export row carries a single `receiptUri` but does not say which object it points to or the exact URI shape. An implementer might emit the PDF URI, the prefix alone, or an HTTPS pre-signed URL.
- **Severity:** Medium — tax/audit consumers need an unambiguous, machine-verifiable pointer.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` lines 223-226 shows the canonical JSON URI is `s3://{bucket}/{prefix}.json`; `ReceiptStore.store(...)` creates both `.json` and `.pdf` keys.
- **Recommended brief amendment:** Freeze `receiptUri = "s3://" + receipts.s3_bucket + "/" + receipts.s3_key + ".json"` and document that this points at the canonical machine-verifiable JSON artifact (matching `ReceiptService.receiptUri`).

---

## Finding 4 — No deterministic result ordering

- **Issue:** The TIB says output order is an "implementer's choice" and only requires "some stable order" in a boundary test. A tax export should be reproducible; the same request should return rows in the same order every time. Without an explicit `ORDER BY`, the database may return rows in different orders depending on the plan.
- **Severity:** Low — does not break correctness, but hurts usability and test determinism.
- **Evidence:** TIB Acceptance Criteria (last bullet under Required Tests); O5 says "one row per attested payment in the requested date range (by `finalizedAt`)."
- **Recommended brief amendment:** Freeze ordering as `ORDER BY p.finalized_at ASC, p.payment_uuid ASC` (or `DESC`) and add it as an explicit acceptance criterion.

---

## Finding 5 — Invalid `format` parameter behavior is undefined

- **Issue:** The TIB says the optional `format` query param (`csv` or `json`) takes precedence over the `Accept` header, but it does not say what happens when `format` is present with a value other than `csv` or `json` (e.g., `format=xml`). Silent fallback to the header could surprise a caller; a typed 400 is safer.
- **Severity:** Low — edge case, but easy to make consistent.
- **Evidence:** TIB Inputs (`format` paragraph); R29 (typed 4xx responses).
- **Recommended brief amendment:** State that an unknown `format` value produces a typed 400 with a stable problem type, and that valid values are exactly `csv` and `json`.

---

## Finding 6 — No per-module exception handler is listed

- **Issue:** The TIB creates `InvalidExportRequestException` for missing/malformed inputs and `from > to`, but the Files to Create list does not include an `ExportExceptionHandler`. `ApiExceptionHandler` in `common` does not know about this exception and should not import feature-module exceptions. Without a handler, the new domain 400 would fall through to the generic 500 path, violating R29.
- **Severity:** Medium — a core acceptance criterion (AC7) would return the wrong status.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` (no `InvalidExportRequestException` handler); `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java` (the precedent the brief already mirrors for the exception class).
- **Recommended brief amendment:** Add `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java` to Files to Create, mirroring `InvoiceExceptionHandler`'s shape (`@RestControllerAdvice`, `@Order(HIGHEST_PRECEDENCE)`, map `InvalidExportRequestException` to RFC 9457 400).

---

## Finding 7 — Malformed `from`/`to` Instant values are not explicitly mapped

- **Issue:** The TIB covers missing `from`/`to` and `from > to` via `InvalidExportRequestException`, but not parse failures. If Spring cannot bind a malformed RFC3339 string to `Instant`, it throws `MethodArgumentTypeMismatchException`; the global handler maps that to 400, but the detail message may leak the raw value and the `title` is "Malformed request parameter," not "Invalid export request." A dedicated parser in the controller would give a consistent typed 400.
- **Severity:** Low — still 400, but inconsistent error shape.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` lines 98-105; TIB AC7.
- **Recommended brief amendment:** Accept either `DateTimeParseException`/`MethodArgumentTypeMismatchException` as acceptable 400s, or explicitly parse `from`/`to` inside the controller and throw `InvalidExportRequestException` with a uniform message.

---

## Finding 8 — Unbounded result set with no cap or streaming

- **Issue:** The TIB deliberately excludes pagination/streaming, mirroring T20. A tax export over a multi-year range for a high-volume merchant could return tens of thousands of rows, materializing the full list in memory as a JSON array or CSV string. This risks OOM and gateway timeouts, and there is no safety valve.
- **Severity:** Low — the TIB already documents the "no premature optimization" stance, but tax use cases are predictable batch workloads.
- **Evidence:** TIB Scope/Out and Constraints.
- **Recommended brief amendment:** Either add a configurable hard row cap (e.g., 10,000 rows) that returns a typed 400/413 when exceeded, or document the maximum supported range (e.g., one calendar year) in the API docs and revisit criterion.

---

## Finding 9 — Export query has no supporting index

- **Issue:** The planned query filters `i.merchant_account_uuid = ? AND p.state = 'ATTESTED' AND p.finalized_at BETWEEN ? AND ?`. Existing indexes are `idx_invoices_merchant(merchant_account_uuid)`, `idx_payments_invoice(invoice_id)`, and `idx_payments_state(state)`. A high-volume merchant may scan a large portion of `payments` or `invoices` because the predicate is not index-leading on the combination.
- **Severity:** Low — performance, not correctness; the TIB says revisit optimization later.
- **Evidence:** `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` indexes; TIB Constraints.
- **Recommended brief amendment:** Add a migration `V<n>__payment_app_export_index.sql` creating an index such as `CREATE INDEX idx_payments_export ON payments(state, finalized_at, invoice_id)` (or a covering index joining through `invoices`) and list it as a performance acceptance test target.

---

## Summary

The TIB correctly scopes T22 as a read-only reporting surface and aligns with `ROLE_MERCHANT` JWT scoping. The most important amendments before freezing are (1) pinning observed-vs-expected column sources, (2) freezing the CSV escaping rule or the invariant that justifies omitting it, (3) freezing the `receiptUri` shape, and (4) adding the missing `ExportExceptionHandler`. Gaps 4–9 are lower severity but should be dispositioned at the Phase 4 gate.
