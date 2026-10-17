# payment · T22 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

No production code changed this phase.

## Test files

1. `services/payment/src/test/java/com/themistra/payment/export/ExportControllerTest.java` — 18
   tests, `@WebMvcTest` + `SecurityMockMvcRequestPostProcessors.jwt()`, mirrors
   `InvoiceControllerTest`'s exact shape. `TransactionExportService` is mocked — this layer
   proves routing, security, format negotiation, and validation, not the query itself.
2. `services/payment/src/test/java/com/themistra/payment/export/TransactionExportServiceIntegrationTest.java`
   — 8 tests, real Testcontainers Postgres, mirrors `PaymentRepositoryIntegrationTest`'s shape.
   Proves the three-table join itself — cannot be meaningfully proven against a mock.

## Traceability matrix

| Test | File | Maps to |
|---|---|---|
| `shouldExportTaxReadyHistoryForMerchantAndDateRange` | Controller | Named test (R27); AC3, AC4 — full 11-field JSON shape, amount as quoted string |
| `shouldExportTaxReadyHistoryForMerchantAndDateRange` | Integration | Named test (R27); AC1–AC3 against a real 3-table join, including the `receiptUri` formula |
| `shouldReturnEmptyJsonArrayWhenNoMatchingRows` | Controller | AC6 |
| `shouldReturnCsvWhenFormatParamIsCsv` | Controller | AC5 |
| `shouldAddContentDispositionHeaderForCsvResponse` | Controller | Phase 9 Kimi Finding 6 |
| `shouldReturnJsonWhenFormatParamIsJson` | Controller | AC5 |
| `shouldReturnCsvWhenAcceptHeaderIsTextCsv` | Controller | AC5 |
| `shouldReturnJsonForWildcardAcceptHeader` | Controller | Phase 9 Kimi Finding 2's own regression check (`*/*` → JSON, the Phase 4 frozen default) |
| `shouldReturnJsonForMalformedAcceptHeader` | Controller | Phase 9 Kimi Finding 2 (malformed `Accept` falls back to JSON, not 500) |
| `shouldPreferFormatParamOverConflictingAcceptHeader` | Controller | AC5 (`format` wins) |
| `shouldReturn400ForInvalidFormatValue` | Controller | AC7, Phase 3 Kimi Finding 5 |
| `shouldReturn400ForMissingFromParam` / `ToParam` | Controller | AC7; Phase 6's `MissingServletRequestParameterException` fix (Phase 8 Kimi Finding 4) |
| `shouldReturn400ForFromAfterTo` | Controller | AC7 |
| `shouldReturn400ForMalformedFromValue` | Controller | AC7 (Phase 3 Kimi Finding 7, rejected disposition — confirms the existing generic handler still produces a correct 400) |
| `shouldReturn400ForMissingSubClaim` | Controller | AC7, L10 |
| `shouldRejectNonMerchantCallerWithForbidden` | Controller | AC1, L10 |
| `shouldRejectUnauthenticatedCallerWithUnauthorized` | Controller | L10 |
| `shouldEscapeCsvFieldsContainingSpecialCharacters` | Controller | AC12; Phase 3 Kimi Finding 1 + Phase 9 Kimi Finding 1 (comma, quote, `\n`, and `\r` all in one value) |
| `shouldExcludeOtherMerchantsPayments` | Integration | AC1, AC8, R28 continuity |
| `shouldExcludePaymentsOutsideDateRange` | Integration | AC2, AC8 |
| `shouldIncludePaymentsOnDateRangeBoundaries` | Integration | AC2 (inclusive-range proof, both ends) |
| `shouldExcludeFinalizedNotYetAttestedPayments` | Integration | AC2, AC8, L2 continuity |
| `shouldReturnEmptyListWhenNoMatchingRows` | Integration | AC6 |
| `shouldReturnRowsInAscendingFinalizedAtOrder` | Integration | AC10 (Phase 3 Kimi Finding 4) — mutation-verified (see below) |
| `shouldSourceAmountAndPayToAddressFromPaymentsNotInvoices` | Integration | AC9 (Phase 3 Kimi Finding 2) — mutation-verified (see below) |

## Mutation-testing performed this phase

Two of the Kimi-driven corrections are load-bearing enough to verify the test actually catches a
regression, not merely that it happens to pass:

- **Column source (Finding 2).** Edited `TransactionExportRepository`'s SQL to select
  `i.amount, i.pay_to_address` (the invoice's expected values) instead of `p.amount, p.to_address`
  (the payment's observed values). `shouldSourceAmountAndPayToAddressFromPaymentsNotInvoices`
  failed red (`expected: "999" but was: "1000"`). Reverted; full suite confirmed green again.
- **Ordering (Finding 4).** Edited the `ORDER BY` to `finalized_at DESC`.
  `shouldReturnRowsInAscendingFinalizedAtOrder` failed red. Reverted; full suite confirmed green
  again.

## Verification performed this phase

- `mvn -o -pl services/payment -am test-compile`: clean.
- `mvn -o -pl services/payment -am test -Dtest=ExportControllerTest`: **18/18 passing**.
- `mvn -o -pl services/payment -am test -Dtest=TransactionExportServiceIntegrationTest`:
  **8/8 passing** (real Testcontainers Postgres).
- Both mutation-tests above: confirmed red, then reverted and confirmed green.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **446/446 passing**
  (420 pre-task baseline + 18 + 8 new).
