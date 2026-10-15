# payment · T22 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Implement `TransactionExportService` + `ExportController` (`GET /api/v1/exports/transactions`)
returning a merchant's finalized/attested payment history as a tax-ready export over a caller-
supplied date range (R27).

## Purpose

The first reporting/export surface in this service (and, per Phase 0 §5, the first anywhere in
the platform) — gives a merchant a tax-ready record of their attested on-chain payments: token,
amount, chain, tx hash, finality timestamp, and a pointer to the already-signed receipt (T17/T19),
without requiring them to reconstruct it by calling three other endpoints and joining the results
themselves.

## Scope

**In:** `TransactionExportService`, `ExportController`, the new three-table reporting read, CSV
and JSON rendering of the result.

**Out:** Any new contract file under `contracts/api/` (none is listed for this task; the response
shape is defined entirely by this brief, not an OpenAPI spec — logged under Open Questions).
Pagination/streaming (no requirement names it; a tax-ready export for one merchant's history is
assumed to be bounded enough to return in one response, mirroring T20's identical "no premature
optimization" stance). Any change to `Invoice`/`Payment`/`Receipt` or their repositories.

## Business Rules

- **R27.** Merchant-scoped tax-ready export of finalized/attested payment history over a date
  range: token, amount, chain, tx hash, finality timestamp, receipt reference.
- **R28** (continuity) — every row is scoped to the caller's own `merchantAccountUuid`; no cross-
  merchant case arises (no path-variable lookup).

## Locked Decisions

- **L2/L9/L10/L12** — see Phase 1; unchanged.
- **New, resolved directly (low-risk, pure engineering — no `AskUserQuestion`):**
  - **Date-range params are `from`/`to` query params, each an RFC3339 `Instant` string (not a
    bare calendar date).** Rejected a calendar-date (`YYYY-MM-DD`) form: this service has no
    merchant-local timezone concept anywhere else (every timestamp in this codebase is `Instant`,
    UTC, injected `Clock`), and inventing a day-boundary convention here would be a one-off. Range
    is **inclusive on both ends**: `finalizedAt >= from AND finalizedAt <= to`. `from > to` is a
    typed 400. Either param missing is a typed 400 (no implicit "all time" default — an unbounded
    tax export is very unlikely to be what a caller actually wants, and silently returning
    everything on a missing param is a worse failure mode than a loud 400).
  - **Format selection: an optional `format` query param (`csv` or `json`) takes precedence; if
    absent, the `Accept` header decides (`text/csv` → CSV; anything else, including absent/`*/*`/
    `application/json` → JSON, the default).** This is the direct, reasoned proposal Phase 0/1
    flagged as unprecedented elsewhere in the platform — no existing sibling endpoint exists to
    mirror, so this is invented here, not looked up.
  - **CSV has no generic escaping/quoting.** Every column value in this export's own fixed shape
    is constitutionally comma-free and newline-free (UUIDs, chain name, hex addresses/hashes, a
    decimal digit string, RFC3339 instants, an `s3://bucket/key.json` URI) — a generic RFC 4180
    CSV writer would be unused complexity for a format with no field that can ever contain the one
    character (`,`) that would need escaping. The header row and each data row are plain comma-
    joined strings.
  - **The reporting read is a plain Java class (`TransactionExportRepository`, `@Repository`),
    not a Spring Data JPA interface — it injects `EntityManager` directly and calls
    `createNativeQuery(sql).getResultList()`, mapping each `Object[]` row to a
    `TransactionExportRow` by hand.** No existing precedent in this codebase (or any sibling
    service, checked at Phase 0) covers "a repository that owns no entity, reading three other
    modules' tables for one reporting query" — `InvoiceRepository`'s own native-query precedent is
    still a `JpaRepository<Invoice, Long>` that happens to also read `payments.payments` by raw
    column; this task's query reads three tables and belongs to none of them, so a bare
    `EntityManager`-backed class (no domain type at all) is the correct, minimal extension of that
    same idea, not a new pattern invented from nothing. This satisfies L12 directly: no
    `Invoice`/`Payment`/`Receipt` entity class is ever imported.
  - **Only `payment.state = 'ATTESTED'` rows are included** (L2 continuity — a `Receipt` row, and
    therefore a `receiptUri`, cannot exist for any earlier state).

## Dependencies

- `jakarta.persistence.EntityManager` (already a transitive Spring Boot JPA starter dependency;
  no new dependency added).
- Spring Security `@AuthenticationPrincipal Jwt` / `@PreAuthorize("hasRole('MERCHANT')")` —
  mirrors `InvoiceController`.
- `InvoiceController.merchantAccountUuidFrom`'s exact pattern, duplicated here (that method is
  `private static` on `InvoiceController`, not shared plumbing — mirrors this codebase's own
  established "duplicate a small private helper rather than force a shared `common` utility for a
  two-line method" precedent, e.g. `LedgerVerifier`'s own duplicated `sha256` helper, T20).

## Inputs

- `merchantAccountUuid` — from the JWT `sub` claim.
- `from`, `to` — RFC3339 `Instant` query params, both required.
- `format` — optional query param (`csv`|`json`); else `Accept` header; else JSON.

## Outputs

- JSON: a `List<TransactionExportRow>` serialized as a JSON array (Jackson, default
  `HttpMessageConverter` — no custom serializer needed since `TransactionExportRow`'s own
  `amount` field is a `String`, not a `BigDecimal`, so no numeric-vs-string Jackson concern
  arises).
- CSV: `text/csv`, a header line (the eleven column names, O5's own order) followed by one line
  per row, same order.
- `TransactionExportRow` (new record): `invoiceUuid, paymentUuid, receiptUuid, chain, txHash,
  tokenContractAddress, tokenDecimals (short), amount (String, base units), payToAddress,
  finalizedAt (Instant), attestedAt (Instant), receiptUri (String)` — exactly O5's eleven columns,
  in O5's own order.

## State Changes

None — this task is purely read-only.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRow.java`
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRepository.java`
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportService.java`
- `services/payment/src/main/java/com/themistra/payment/export/ExportController.java`
- `services/payment/src/main/java/com/themistra/payment/export/InvalidExportRequestException.java`
  (the typed 400 for a missing/malformed `sub`, missing `from`/`to`, or `from > to` — mirrors
  `InvoiceController`'s own `InvalidInvoiceRequestException` shape, one per module rather than a
  shared type, matching that module's own existing precedent).

## Files NOT to Modify

`Invoice`/`Payment`/`Receipt`/their repositories/services (read-only consumers only); anything
under `spec/`; `ArchitectureTest.FEATURE_MODULES` (confirmed at Phase 0 — this module owns no
`@Entity`, so the list's own fail-fast rule does not apply to it).

## Acceptance Criteria

- **AC1.** `GET /api/v1/exports/transactions` requires `ROLE_MERCHANT`; every row returned belongs
  to the caller's own `merchantAccountUuid`.
- **AC2.** Only `ATTESTED` payments are included, filtered by `finalizedAt` inclusively within
  `[from, to]`.
- **AC3.** Each row carries exactly the eleven O5 columns, correctly populated from the real
  `invoices`/`payments`/`receipts` rows.
- **AC4.** `amount` is always a decimal string (L6), never a JSON number.
- **AC5.** `format=csv` or `Accept: text/csv` returns CSV (header + rows); anything else returns
  JSON; `format` wins over `Accept` when both are present.
- **AC6.** No matching rows → an empty JSON array, or a CSV with only the header line — not an
  error.
- **AC7.** Missing/malformed `sub`, missing `from`/`to`, or `from > to` → a typed 400.
- **AC8.** A payment belonging to a different merchant, or one that is `FINALIZED` but not yet
  `ATTESTED`, or one whose `finalizedAt` falls outside `[from, to]`, is excluded.

## Required Tests

- **Named test:** `shouldExportTaxReadyHistoryForMerchantAndDateRange` (R27) — a real
  Testcontainers test: seed an `ATTESTED` payment (with its invoice and receipt, via the real
  `LedgerService`/`ReceiptService` path used in T17/T19's own tests, not hand-inserted rows) for
  merchant A inside the range; assert the export contains exactly the eleven correct values.

Boundary tests implied by AC1/AC2/AC5–AC8, not separately named:
- A second merchant's otherwise-matching payment is excluded (AC1/AC8).
- A payment outside `[from, to]` is excluded; one exactly on each boundary is included (AC2,
  inclusive-range proof).
- A `FINALIZED`-not-yet-`ATTESTED` payment is excluded (AC2/AC8, L2 continuity).
- Empty result for a merchant/range with nothing matching (AC6), in both formats.
- `format=csv`, `Accept: text/csv`, default JSON, and `format` overriding a conflicting `Accept`
  header (AC5).
- Missing `from`, missing `to`, `from > to`, and a malformed/missing `sub` claim (AC7).
- Two matching payments in range → both rows present, in some stable order (not itself specified
  by R27 — implementer's choice, e.g. `finalizedAt` ascending, documented in Phase 5/6 rather than
  litigated here).

## Constraints

- **Performance:** no pagination/streaming (Scope, Out) — a single bounded result set, mirroring
  T20's own "no premature optimization" stance; revisit only if a real merchant's own export size
  makes it a genuine concern.
- **Security:** `ROLE_MERCHANT` + JWT `sub`-based scoping (L10); no admin/ops bypass of the
  merchant filter anywhere in this task's own scope.
- **Thread-safety:** stateless beyond injected `EntityManager`/repository, consistent with every
  other service class in this codebase.
- **Transaction:** read-only; `TransactionExportService`'s own method should be
  `@Transactional(readOnly = true)` (standard JPA practice for a native query via `EntityManager`
  outside any Spring Data repository's own implicit transaction handling) — not itself mandated by
  any LOCKED decision, but the correct, minimal choice for a multi-statement-free but still JPA-
  context-dependent native query.
- **Module boundaries:** no `Invoice`/`Payment`/`Receipt` entity import (L12).
- **Null handling:** `tokenDecimals` is a Postgres `smallint` (maps to Java `short`, never boxed/
  nullable, mirroring `Invoice.tokenDecimals`'s own existing mapping) — the native query's raw
  `Object[]` row must be cast accordingly, confirmed empirically at Phase 6 (Phase 0's own flagged
  unknown about native-query numeric/temporal mapping), not assumed here.

## Open Questions

No blockers. Both Phase 1 open questions (Q-A, Q-B) are resolved directly above; neither needed
`AskUserQuestion` — both are low-risk, pure-engineering choices with no product-policy dimension,
consistent with this session's own established pattern for this class of decision (e.g. T19's
advisory-lock placement, T20's head-to-tail direction).
