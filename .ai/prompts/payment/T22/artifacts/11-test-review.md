# payment · T22 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the actual `ExportControllerTest` / `TransactionExportServiceIntegrationTest` against the frozen brief's acceptance criteria.

---

## Gap 1 — Empty CSV response is not tested

- **Why it matters:** AC6 explicitly requires that no matching rows return "a CSV with only the header line — not an error." The controller tests verify the empty JSON path, and the integration test verifies an empty list from the service, but no test requests CSV when the service returns an empty list.
- **Suggested test:** In `ExportControllerTest`, add `shouldReturnCsvHeaderOnlyWhenNoMatchingRows`: stub the service to return an empty list, call `GET /api/v1/exports/transactions?from=...&to=...&format=csv`, and assert the response body is exactly the header line followed by one newline and no data rows.

---

## Gap 2 — Invalid (non-UUID) `sub` claim is not tested

- **Why it matters:** `ExportController.merchantAccountUuidFrom` has two validation branches: missing/blank `sub` and `sub` that fails `UUID.fromString`. Only the missing branch is tested. A regression that broke the catch block or changed the exception type would return 500 for a malformed JWT subject instead of the typed 400 AC7 requires.
- **Suggested test:** In `ExportControllerTest`, add a JWT with `.subject("not-a-uuid")` and `roles=[MERCHANT]`, call the export endpoint, and assert a 400 with title `"Invalid export request"` and detail containing `"sub claim is not a valid UUID"`.

---

## Gap 3 — `from == to` inclusive boundary is not tested

- **Why it matters:** AC2 says the range is inclusive on both ends. The existing boundary test seeds one row on the `from` instant and another on the `to` instant, but `from` and `to` are different. It does not prove that a row whose `finalizedAt` equals both bounds (i.e., `from == to`) is included.
- **Suggested test:** In the integration test, add `shouldIncludePaymentWhenFromEqualsTo`: set `from = to = finalizedAt`, seed one ATTESTED payment at exactly that instant, and assert one row is returned.

---

## Gap 4 — Tie-breaking order when `finalizedAt` is equal is not tested

- **Why it matters:** AC10 requires ascending order by `finalizedAt`, tie-broken by `paymentUuid`. The existing order test seeds rows with different `finalizedAt` values, so it would pass even if the tie-breaker were wrong or absent.
- **Suggested test:** In the integration test, seed two ATTESTED payments with the same `finalizedAt` but known, ordered `paymentUuid` values (e.g., deterministically generated UUIDs), and assert `rows.get(0).paymentUuid()` is the lexicographically smaller one.

---

## Gap 5 — Malformed `to` parameter is not tested

- **Why it matters:** AC7 requires a typed 400 for malformed `from`/`to`. The controller suite only tests malformed `from`. A Spring-binding bug that affected only the second parameter (unlikely but possible) would not be caught.
- **Suggested test:** In `ExportControllerTest`, add `shouldReturn400ForMalformedToValue`: call with `?from=2026-01-01T00:00:00Z&to=not-an-instant` and assert 400 with title `"Malformed request parameter"`.

---

## Gap 6 — CSV structural integrity is only spot-checked

- **Why it matters:** `shouldEscapeCsvFieldsContainingSpecialCharacters` asserts that the response body contains the escaped substring, but it does not verify that the row parses back to exactly the original value or that it has the expected number of columns. A bug that inserted an extra comma outside the escaped field would still contain the escaped substring.
- **Suggested test:** Parse the CSV response body (e.g., with Apache Commons CSV or a small deterministic split), assert the row has exactly 12 columns, and assert the `payToAddress` cell equals the original unescaped value `"0xdest,with\"quote\nand\rbreaks"`.

---

## Gap 7 — No end-to-end integration test exercises CSV rendering

- **Why it matters:** The controller tests prove CSV rendering with stubbed `TransactionExportRow`s, and the integration tests prove the query with the default JSON path, but no test exercises the full stack from real database rows through the controller to a CSV response.
- **Suggested test:** Add a `@SpringBootTest(webEnvironment = RANDOM_PORT)` test (or a `MockMvc`-based integration test) that seeds real rows via the integration test's helpers and calls `GET ...?format=csv`, then asserts the response content type, `Content-Disposition`, header row, and at least one data row.

---

## Gap 8 — `Content-Disposition` filename is not fully verified

- **Why it matters:** The existing test only checks that the header starts with `attachment; filename="transactions-` and ends with `.csv"`. It would pass with a static or incorrect middle section. The brief does not strictly require a dynamic filename, but the test name implies the range is included.
- **Suggested test:** Assert the header equals exactly `attachment; filename="transactions-2026-01-01T00:00:00Z-2026-12-31T23:59:59Z.csv"` for the fixed test range, or assert the middle portion contains both instants.

---

## Summary

The Phase 10 suite is strong: it covers security, format negotiation, validation, CSV escaping, merchant scoping, date filtering, state filtering, ordering, and the observed-vs-expected column source. The highest-value additions are Gap 1 (empty CSV behavior), Gap 3 (`from == to` boundary), Gap 4 (tie-breaker ordering), and Gap 7 (end-to-end CSV integration). Gaps 2, 5, 6, and 8 are lower-priority polish.
