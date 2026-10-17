# payment · T22 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`, `artifacts/11-test-review-resolution.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R27** — merchant-scoped tax-ready export over a date range (token, amount, chain, tx hash, finality timestamp, receipt reference) | Yes | `ExportController.export` (`ExportController.java:45`), `TransactionExportRepository.SQL`/`toRow` (`TransactionExportRepository.java:39,71`) | `shouldExportTaxReadyHistoryForMerchantAndDateRange` (Controller + Integration, named) | No | No |
| **R28 (continuity)** — merchant-scoped reads | Yes | `merchantAccountUuidFrom` (`ExportController.java:137`), query filters by `merchantAccountUuid` (`TransactionExportRepository.java:42`) | `shouldExcludeOtherMerchantsPayments`, `shouldRejectNonMerchantCallerWithForbidden`, `shouldRejectUnauthenticatedCallerWithUnauthorized` | No | No (no 404-vs-403 case arises here — no path-variable lookup, confirmed Phase 1) |
| **O5 — eleven frozen columns, in order** | Yes | `TransactionExportRow` record | Named test asserts all 11 fields (both layers) | No | No |
| **O5 — amount decimal string (L6)** | Yes | `TransactionExportRepository.toRow`: `((BigDecimal) row[7]).toPlainString()` | Named test, `shouldSourceAmountAndPayToAddressFromPaymentsNotInvoices` | No | No |
| **O5 — CSV and JSON, Accept/query-param convention** | Yes | `wantsCsv` (`ExportController.java:70`) | `shouldReturnCsvWhenFormatParamIsCsv/Json`, `shouldReturnCsvWhenAcceptHeaderIsTextCsv`, `shouldReturnJsonForWildcardAcceptHeader`, `shouldReturnJsonForMalformedAcceptHeader`, `shouldPreferFormatParamOverConflictingAcceptHeader` | No | No |
| **L2 (continuity)** — only `ATTESTED` payments (a `Receipt` can't exist earlier) | Yes | `TransactionExportRepository.SQL`: `p.state = 'ATTESTED'` | `shouldExcludeFinalizedNotYetAttestedPayments` | No | No |
| **L6 (continuity)** — money as decimal string, never float | Yes | `TransactionExportRow.amount` is `String`; `BigDecimal.toPlainString()` | Named test | No | No |
| **L9 (continuity)** — `receiptUri` formula | Yes | `TransactionExportRepository.toRow`: `"s3://" + s3Bucket + "/" + s3Key + ".json"` | Named test (Integration) asserts the exact URI | No | No |
| **L10 (continuity)** — zero trust, `ROLE_MERCHANT` | Yes | `@PreAuthorize("hasRole('MERCHANT')")` (`ExportController.java:44`) | `shouldRejectNonMerchantCallerWithForbidden`, `shouldRejectUnauthenticatedCallerWithUnauthorized` | No | No |
| **L12 (continuity)** — no cross-module entity import | Yes | `TransactionExportRepository` uses `EntityManager.createNativeQuery`, no `Invoice`/`Payment`/`Receipt` import anywhere in `export` | `ArchitectureTest` (unchanged, no new module needed — `export` owns no `@Entity`) | No | No |
| **Phase 3/9 Kimi Finding 2 — observed, not expected, columns** | Yes | `TransactionExportRepository.SQL`: `p.amount, p.to_address`, never `i.amount`/`i.pay_to_address` | `shouldSourceAmountAndPayToAddressFromPaymentsNotInvoices`, mutation-verified (Phase 10, confirmed red when reverted) | No | No |
| **Phase 4 Finding 4 — deterministic ordering** | Yes | `ORDER BY p.finalized_at ASC, p.payment_uuid ASC` (`TransactionExportRepository.java:50`) | `shouldReturnRowsInAscendingFinalizedAtOrder`, `shouldTieBreakByPaymentUuidWhenFinalizedAtIsEqual`, mutation-verified | No | No (one disclosed limitation — see Risks) |
| **Phase 4/9 Finding 1 — CSV escaping** | Yes | `csvField` (`ExportController.java:127`) — comma, quote, `\n`, `\r` | `shouldEscapeCsvFieldsContainingSpecialCharacters` (structural parse, not substring) | No | No |
| **Phase 4 Finding 6 — `ExportExceptionHandler`** | Yes | `ExportExceptionHandler.onInvalidRequest` | `shouldReturn400ForInvalidFormatValue`, `shouldReturn400ForFromAfterTo`, `shouldReturn400ForMissingSubClaim`, `shouldReturn400ForMalformedSubClaim` | No | No |
| **Phase 6 finding — missing required param → 400** | Yes | `ApiExceptionHandler.onMissingParameter` (new) | `shouldReturn400ForMissingFromParam/ToParam` | No | No (deviation from "Files NOT to Modify," disclosed at Phase 6/9, human-approved) |
| **Phase 9 Finding 6 — `Content-Disposition`** | Yes | `ExportController.java:64` | `shouldAddContentDispositionHeaderForCsvResponse` (exact match, corrected at Phase 11) | No | No |
| **Phase 9 Finding 9 (Phase 3) — supporting index** | Yes | `V10__payment_app_export_index.sql` | `SchemaBaselineMigrationIntegrationTest` (migration count), no dedicated perf test (none required) | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. `TransactionExportService`/`ExportController` fully
implement R27 end to end: merchant-scoped, date-ranged, `ATTESTED`-only, correctly sourced from
observed on-chain data, deterministically ordered, in both CSV and JSON, with proper RFC 9457
error handling throughout.

**(2) Does it satisfy every acceptance criterion?** All thirteen (AC1–AC13, Phase 4/9 frozen
brief) are satisfied and each is backed by at least one real test — 8 of them by a real
Testcontainers Postgres test, not a mock, since the core logic is a multi-table SQL join that a
mock cannot meaningfully verify.

**(3) Does it violate any LOCKED decision?** No. L2, L6, L9, L10, L12 all hold, each confirmed by
direct inspection and by a dedicated test, not merely by comment.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **No row cap or pagination** (Phase 4 Finding 8, rejected; Phase 9 Finding 7, acknowledged) — a
  sufficiently large date range for a high-volume merchant could return a large in-memory CSV/
  JSON payload. Disclosed, not addressed, mirroring T20's identical "no premature optimization"
  stance; revisit only if real usage makes it a genuine concern.
- **The tie-break-ordering test's real guarantee is narrower than its name implies** (Phase 11
  resolution, disclosed): it reliably catches the `payment_uuid` tie-break column being wrong or
  reversed, but a mutation test showed it does **not** reliably catch the tie-break clause being
  dropped entirely, since Postgres's own unspecified order for equal `finalized_at` rows happened
  to coincide with insertion order on the small seeded table used. A known, disclosed test
  limitation, not a production defect — the production `ORDER BY` itself is correct and present.
- **No full-stack real-DB + MockMvc + CSV integration test exists** (Phase 11 Gap 7, rejected) —
  the two independent test layers (real-DB query correctness; stubbed-row CSV rendering) already
  cover every actual code path a combined test would re-exercise, but a true end-to-end seam
  defect (unlikely, given both layers share the same `TransactionExportRow` type contract) would
  not be caught by either alone. Judged acceptable risk given the cost of new, unprecedented test
  infrastructure for marginal additional coverage.
- **This is the platform's first export endpoint and the first required `@RequestParam` anywhere
  in this service** (Phase 0/6) — both the CSV/JSON format-negotiation convention and the
  `MissingServletRequestParameterException` handler are new, invented precedents, not mirrored
  from an existing one. Both are now tested and correct, but any *future* export endpoint should
  be checked for consistency with the choices made here rather than inventing a third convention.

## Verdict

**PASS** — `TransactionExportService`/`ExportController` fully implement R27, correctly source
observed (not expected) payment data, correctly scope to the caller's own merchant account,
correctly filter to `ATTESTED`-only within an inclusive date range, support both CSV and JSON with
proper escaping and a download header, and respect every continuity LOCKED decision (L2, L6, L9,
L10, L12). Two independent Kimi review cycles (Phase 8, Phase 11) and two rounds of human-approved
disposition surfaced and fixed 11 real issues total (plus one self-caught regression during
application of a Kimi fix), all verified against source rather than trusted verbatim. Full
payment-service suite: **451/451 passing**.
