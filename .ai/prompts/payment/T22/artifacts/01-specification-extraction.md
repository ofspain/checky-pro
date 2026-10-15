# payment · T22 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R27.** When a merchant calls `GET /api/v1/exports/transactions` with a date range, return
  their finalized/attested payment history in a tax-ready export (format per Q6/O5), including
  token, amount, chain, tx hash, finality timestamp, and receipt reference.
- **R28** (continuity, per `design.md` O5: "R28's own rule extends here"). Merchant-scoped reads
  return only the caller's own resources. This endpoint has no path-variable resource lookup, so
  R28's 404-vs-403 cross-merchant case does not arise here — scoping is by construction (every row
  filtered by the caller's own `merchantAccountUuid`), not by a per-resource ownership check.

## Locked Decisions

- **L2.** A receipt — and therefore `ATTESTED` — exists only once a payment has reached
  `FINALIZED` and been signed. This task's export is defined over "finalized/attested payment
  history" (R27); only `ATTESTED` payments are eligible, since a `Receipt` row (needed for
  `receiptUri`) cannot exist before `ATTESTED` (T17/T19). A merely-`FINALIZED`-but-not-yet-
  `ATTESTED` payment has no receipt yet and is excluded, not included with a null receipt
  reference.
- **L6.** `amount` in the export is a decimal string in base units, never a JSON number/float —
  matches O5's own column spec exactly (`amount (base units, decimal string)`).
- **L9.** The receipt object's S3 key is immutable/WORM; `receiptUri` is reconstructed the same
  way `ReceiptService.receiptUri` already does (`"s3://" + bucket + "/" + s3Key + ".json"`), never
  refetched from S3 itself — this is a metadata read, not a document read.
- **L10.** Zero trust at the edge: the endpoint requires a valid JWT (OAuth2 resource server);
  rows are filtered to the caller's own `merchantAccountUuid` from the `sub` claim, mirroring
  `InvoiceController.merchantAccountUuidFrom`.
- **L12.** No feature module imports another feature module's `@Entity` class. The new `export`
  module's own query may read `invoices`/`payments`/`receipts` columns directly (native SQL,
  same-schema, no cross-schema query — `agents.md`'s "no cross-schema queries" rule is about
  cross-*service* schemas, not cross-*module* tables within this service's own single `payments`
  schema), but must never import the `Invoice`/`Payment`/`Receipt` entity classes themselves —
  mirrors `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment`'s own established shape
  of a native query issued by a module that owns neither target table.

## Files involved

**New (per `design.md` package map, lines 421-423):**
- `export/TransactionExportService.java`
- `export/ExportController.java`
- A new repository/query class for the three-table join (exact shape — Phase 2).
- A new flat DTO/record for one export row (exact shape — Phase 2).

**Existing, read-only:**
- `invoice/Invoice.java` (table `payments.invoices`) — `merchant_account_uuid`,
  `pay_to_address`, `token_decimals`, `invoice_uuid`.
- `payment/Payment.java` (table `payments.payments`) — `invoice_id` (FK), `chain`, `tx_hash`,
  `token_contract_address`, `amount`, `state`, `finalized_at`, `attested_at`, `payment_uuid`.
- `receipt/Receipt.java` (table `payments.receipts`) — `payment_id` (FK, unique),
  `receipt_uuid`, `s3_bucket`, `s3_key`.
- `invoice/InvoiceController.java` — `merchantAccountUuidFrom(Jwt)` pattern to mirror.
- `receipt/ReceiptService.java` — `receiptUri(location)` pattern to mirror (string shape only;
  this task builds the URI from raw `s3Bucket`/`s3Key` columns, not a `ReceiptStorageLocation`).

## Dependencies

- Spring Security OAuth2 resource server (`@AuthenticationPrincipal Jwt`, `@PreAuthorize`).
- `jakarta.persistence`/Spring Data JPA native query support (`@Query(nativeQuery = true)`), same
  mechanism as `InvoiceRepository`'s existing native queries — no new dependency.
- No new config keys; no new contract (`design.md`'s package map lists this module under
  "Exports, contracts, hardening" but cites no new `contracts/api/*.yaml` entry — the export
  endpoint's own request/response shape is defined entirely by O5's column list, not by an OpenAPI
  contract file. Logged as an open question below.

## Acceptance Criteria

Mapped to R27 (and R28's continuity):

- **AC1.** `GET /api/v1/exports/transactions?from=<date>&to=<date>` requires `ROLE_MERCHANT`;
  rows returned are scoped to the caller's own `merchantAccountUuid` only.
- **AC2.** Only `ATTESTED` payments are included, filtered by `finalizedAt` falling within
  `[from, to]` (inclusive — exact boundary semantics to confirm in Phase 2; `design.md` says "by
  `finalizedAt`").
- **AC3.** Each row carries exactly the eleven columns O5 names: `invoiceUuid, paymentUuid,
  receiptUuid, chain, txHash, tokenContractAddress, tokenDecimals, amount, payToAddress,
  finalizedAt, attestedAt, receiptUri`.
- **AC4.** `amount` is serialized as a decimal string (L6), never a JSON number.
- **AC5.** Both CSV and JSON are supported, selected by an `Accept`/query-param convention (O5)
  — exact mechanics to be designed in Phase 2 given no existing platform precedent was found
  (Phase 0 §5).
- **AC6.** A merchant with zero matching rows in range gets an empty export (empty list / header-
  only CSV), not an error.
- **AC7.** A malformed/missing JWT `sub` claim is a typed 400, mirroring
  `InvoiceController.merchantAccountUuidFrom` — not an uncaught exception.

## Tests required

- **Named test:** `shouldExportTaxReadyHistoryForMerchantAndDateRange` → R27 — a real
  Testcontainers test: seed an `ATTESTED` payment (with its invoice and receipt) for merchant A
  inside the date range, assert the export contains exactly the eleven correct column values.
- **Boundary tests implied** (not named, but required by AC2/AC6/AC7's own acceptance language):
  - A payment belonging to a different merchant is excluded even if otherwise in range (R28
    continuity / merchant scoping).
  - A payment outside the requested date range (`finalizedAt` before `from` or after `to`) is
    excluded.
  - A payment that is `FINALIZED` but not yet `ATTESTED` (no receipt yet) is excluded.
  - Empty result for a merchant/range with no matching rows.
  - `Accept: text/csv` vs. JSON (or the query-param equivalent) both return the same logical rows
    in their respective formats.
  - Missing/malformed `sub` claim → 400.

## Open Questions

- **Q-A (not a `package.md` §11 item — a new gap surfaced by this task alone).** No existing
  OpenAPI contract file defines this endpoint's request/response shape, and no sibling export
  endpoint exists anywhere in the platform to mirror for the `Accept`/query-param format
  convention O5 assumes. This will need a direct, reasoned Phase 2 proposal (not a lookup) for:
  exact query-param names (`from`/`to`? ISO-8601 date or date-time?), inclusive/exclusive range
  boundary, and the exact `Accept`-vs-query-param precedence rule. Low-risk, pure-engineering —
  resolvable directly in Phase 2/4 without `AskUserQuestion`, consistent with this session's
  established pattern for this class of decision.
- **Q-B.** The `stored-proc-dao` Skill `agents.md` names does not exist in this environment
  (Phase 0 §5, unchanged). Proceeding from the two real in-repo precedents identified there.
- No other genuine blockers identified. `package.md` §11 was checked in full; no open item there
  is scoped to T22.
