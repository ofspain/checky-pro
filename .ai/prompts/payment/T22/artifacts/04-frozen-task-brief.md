# payment · T22 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`.
Approved by the human via `AskUserQuestion`, 2026-10-17 ("Approve as proposed").

## Disposition of Kimi's 9 Phase 3 findings

| # | Finding | Disposition | Reason |
|---|---|---|---|
| 1 | CSV escaping assumed, not enforced | **ACCEPTED** | Verified: no DB constraint prevents a comma/newline reaching an export column via a raw admin insert (same malformed-row threat class T20's own Phase 8 already accepted a defense for). Minimal RFC 4180 quoting added, not a full CSV library. |
| 2 | Column source (invoices vs. payments) not frozen | **ACCEPTED, confirmed by source** | `V1__payments_baseline.sql` confirms `invoices.amount` is commented "expected amount," `payments.amount` "observed amount" — duplicate column names on both tables, genuinely ambiguous in the TIB. R27 asks for payment *history* (what happened), not invoice expectations. **Genuine naming trap also caught here**: the export column is named `payToAddress` (O5), but the correct on-chain-observed source is `payments.to_address`, not `invoices.pay_to_address` — the similarly-named invoice column is the wrong one. |
| 3 | `receiptUri` format/object not frozen | **ACCEPTED** | Already the intended behavior (Phase 2 Locked Decisions), now pinned as a literal formula rather than prose: confirmed against `ReceiptService.receiptUri`/`Receipt.create` call site that `receipts.s3_key` stores the *prefix* (no `.json` suffix) — `receiptUri = "s3://" + receipts.s3_bucket + "/" + receipts.s3_key + ".json"`. |
| 4 | No deterministic ordering | **ACCEPTED** | `ORDER BY p.finalized_at ASC, p.payment_uuid ASC` — cheap, matches O5's own "by `finalizedAt`" framing, makes the export reproducible. |
| 5 | Invalid `format` value undefined | **ACCEPTED** | `format` present and not exactly `csv`/`json` (case-insensitive) → `InvalidExportRequestException` → typed 400. |
| 6 | No `ExportExceptionHandler` | **ACCEPTED** | Confirmed: `ApiExceptionHandler` has no `InvalidExportRequestException` mapping and must not import a feature-module exception (L12-adjacent). Add `ExportExceptionHandler`, mirroring `InvoiceExceptionHandler`'s exact shape (`@RestControllerAdvice`, `@Order(HIGHEST_PRECEDENCE)`, `about:blank` type). |
| 7 | Malformed `from`/`to` not explicitly mapped | **REJECTED, with a correction to the finding itself** | Verified `ApiExceptionHandler.onTypeMismatch` directly (lines 98-105): its `detail` is `"'" + e.getName() + "' is not a valid value for this request"` — only the **parameter name**, never the bad value. Kimi's claim that "the detail message may leak the raw value" is factually wrong. The existing generic handler already returns a correct, non-leaking 400; a dedicated parser would be redundant complexity for a cosmetic title difference only ("Malformed request parameter" vs. a hypothetical export-specific title), not a correctness gap. |
| 8 | Unbounded result set, no cap | **REJECTED** | No requirement (R27 or otherwise) asks for a size limit; this mirrors T20's own already-accepted "no premature optimization" stance for an MVP reporting read. Carried forward as a disclosed, known risk (§ below), not silently dropped. |
| 9 | No supporting index | **ACCEPTED** | Confirmed existing indexes (`idx_payments_invoice`, `idx_payments_state`, `idx_invoices_merchant`) don't cover the combined `state`+`finalized_at` filter this query needs. A one-line additive migration is cheap and non-breaking; added as a new `V<n>` migration. |

## Task

Implement `TransactionExportService` + `ExportController` (`GET /api/v1/exports/transactions`)
returning a merchant's finalized/attested payment history as a tax-ready export over a caller-
supplied date range (R27), as detailed in Phase 2's TIB, amended per the table above.

## Scope

**In:** `TransactionExportRow`, `TransactionExportRepository`, `TransactionExportService`,
`ExportController`, `InvalidExportRequestException`, `ExportExceptionHandler` (new, Finding 6),
a minimal CSV escaper (new, Finding 1), a new index migration (new, Finding 9).

**Out:** unchanged from Phase 2 (no new contract file, no pagination/streaming/row cap — Finding
8, rejected).

## Business Rules

- **R27, R28** (continuity) — unchanged from Phase 1/2.

## Locked Decisions

All of Phase 2's Locked Decisions stand, amended as follows:

- **Column sources are pinned exactly** (Finding 2): `invoiceUuid` and `tokenDecimals` ← `invoices`.
  `paymentUuid, chain, txHash, tokenContractAddress, amount, payToAddress (= payments.to_address,
  NOT invoices.pay_to_address), finalizedAt, attestedAt` ← `payments`. `receiptUuid, receiptUri`
  ← `receipts` (`receiptUri` built from `receipts.s3_bucket`/`receipts.s3_key`, Finding 3).
- **Result ordering is frozen** (Finding 4): `ORDER BY p.finalized_at ASC, p.payment_uuid ASC`.
- **Invalid `format` value is a typed 400** (Finding 5): only `csv`/`json` (case-insensitive) are
  valid; anything else throws `InvalidExportRequestException`.
- **CSV fields are minimally escaped** (Finding 1): any field containing `,`, `"`, or a newline is
  wrapped in double quotes with embedded quotes doubled (RFC 4180 minimal subset) — not a full
  CSV library, just the one escaping rule applied inline per field.
- **A new composite index supports the query** (Finding 9): migration
  `V10__payment_app_export_index.sql`, `CREATE INDEX idx_payments_state_finalized ON
  payments(state, finalized_at)` (covers the predicate's two most selective columns; the join to
  `invoices` for `merchant_account_uuid` still uses `idx_invoices_merchant` on the other side).
- **Malformed `from`/`to` Instant parsing uses the existing generic handler, unchanged** (Finding
  7, rejected as stated — see disposition table). No new parsing code for this case.
- **No row cap / pagination** (Finding 8, rejected) — carried forward as a disclosed risk, not a
  gap silently dropped.

## Dependencies

Unchanged from Phase 2, plus: `InvoiceExceptionHandler`'s exact shape as the direct template for
the new `ExportExceptionHandler` (Finding 6).

## Inputs / Outputs / State Changes

Unchanged from Phase 2, except `TransactionExportRow.amount`/`payToAddress` are now explicitly
sourced from `payments`, not `invoices` (Finding 2), and the repository's SQL carries the frozen
`ORDER BY` (Finding 4).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRow.java`
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRepository.java`
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportService.java`
- `services/payment/src/main/java/com/themistra/payment/export/ExportController.java`
- `services/payment/src/main/java/com/themistra/payment/export/InvalidExportRequestException.java`
- `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java` (new
  this phase, Finding 6)
- `services/payment/src/main/resources/db/migration/V10__payment_app_export_index.sql` (new this
  phase, Finding 9)

## Files NOT to Modify

Unchanged from Phase 2: `Invoice`/`Payment`/`Receipt`/their repositories/services; `spec/`;
`ArchitectureTest.FEATURE_MODULES` (this module owns no `@Entity`).

## Acceptance Criteria

AC1–AC8 from Phase 2 stand, plus:

- **AC9 (Finding 2).** `amount` and `payToAddress` reflect the *observed* on-chain payment
  (`payments.amount`, `payments.to_address`), never the invoice's *expected* values, even when
  they differ (e.g. an `OVERPAID`/`UNDERPAID` historical discrepancy, or a different destination
  than originally requested).
- **AC10 (Finding 4).** Two or more matching rows are always returned in `finalizedAt` ascending
  order, tie-broken by `paymentUuid` ascending — not merely "some stable order."
- **AC11 (Finding 5).** `format=xml` (or any value other than `csv`/`json`, case-insensitive) →
  typed 400.
- **AC12 (Finding 1).** A row whose `payToAddress` (or any other string column) contains a comma,
  quote, or newline is still a structurally valid CSV row when escaped per the frozen rule —
  verified by a test using a deliberately comma-containing value, not merely documented as
  "can't happen."
- **AC13 (Finding 6).** `InvalidExportRequestException` maps to RFC 9457 400 via
  `ExportExceptionHandler`, not the generic 500 fallback.

## Required Tests

Phase 2's tests stand, plus:

- A test asserting `amount`/`payToAddress` come from `payments`, not `invoices`, using a seeded
  row where the two tables' values deliberately differ (AC9).
- A test asserting ascending `finalizedAt` order across ≥2 rows (AC10).
- `format=xml` → 400 (AC11).
- A CSV-escaping test using a value containing a comma (AC12) — this requires inserting such a
  value directly (bypassing normal validation, mirroring T20's own "simulate via direct DB
  access" precedent for a malformed-row scenario), since no normal application path can produce
  one.
- `InvalidExportRequestException` → RFC 9457 400 with the correct `title`, not a 500 (AC13).

## Constraints

Unchanged from Phase 2, plus: the new index (Finding 9) is additive/non-breaking, consistent with
every other migration this service has shipped (Flyway, immutable, new `V<n>` file only).

## Open Questions / Carried-forward risks

No blockers. One disclosed, accepted risk (Finding 8, rejected): no row cap or pagination exists
for this export; a sufficiently large date range for a high-volume merchant could return a large
in-memory result set. Not addressed in this task per the human-approved disposition above —
revisit only if real usage makes it a genuine concern, mirroring T20's identical stance on scale.
