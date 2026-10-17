# payment · T22 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, and `spec/payment-service/design.md`.

---

## Finding 1 — CSV escaping omits carriage returns

- **Issue:** `ExportController.csvField(...)` quotes a value only if it contains `,`, `"`, or `\n`. A raw-admin-inserted value that contains a bare `\r` (without `\n`) is emitted unquoted. RFC 4180 treats `CRLF` as a record terminator, and many CSV parsers also treat a lone `\r` as a line break, which would silently split a field across two rows.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` lines 101-106.
- **Recommendation:** Extend the escape predicate to include `\r`, or switch to a minimal RFC 4180 writer that already handles the full set of line-ending characters.
- **Confidence:** Low — the scenario requires a deliberately malformed row, but the same threat class motivated the comma/quote/newline escape that is already there.

---

## Finding 2 — `Accept` header CSV detection is a substring match

- **Issue:** `wantsCsv(...)` decides CSV by `accept.toLowerCase().contains("text/csv")`. That matches invalid media types such as `application/text/csv-foo` or `not-text/csv`, and ignores quality values (`q=0`). It also does not handle multiple `Accept` values properly.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` line 73.
- **Recommendation:** Parse the header with `MediaType.parseMediaTypes(...)` and check `CSV.isCompatibleWith(parsed)` while respecting `q` values, or fall back to JSON when `text/csv` is present with `q=0`.
- **Confidence:** Low — in practice a real client sends `text/csv` or `application/json` cleanly, but the current logic is not robust media-type negotiation.

---

## Finding 3 — No committed tests exercise the export module

- **Issue:** The Phase 6 notes state 420/420 tests pass, but no `ExportControllerTest` or `TransactionExportServiceTest` exists yet. The new endpoint, the CSV escaping path, the native-query mapping, and the `MissingServletRequestParameterException` handler addition are all unverified by CI.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/export/` is empty; `artifacts/06-implementation-notes.md` lines 75-80.
- **Recommendation:** Phase 10 must add tests covering: the named end-to-end export, merchant scoping, ATTESTED-only filtering, inclusive date boundaries, observed-vs-expected `amount`/`payToAddress` source, `format=csv`/`json`/`xml`, `Accept: text/csv`, missing `from`/`to`, `from > to`, ascending order, CSV escaping with a comma/quote/newline, and the RFC 9457 shape of `InvalidExportRequestException`.
- **Confidence:** High — untested code cannot be considered complete.

---

## Finding 4 — `MissingServletRequestParameterException` handler is not exercised by tests

- **Issue:** The `ApiExceptionHandler` addition for missing required query params is the first of its kind in this service. No existing test covers it, so a regression that removed the handler would cause missing `from`/`to` to return 500 instead of the typed 400 AC7 requires.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` lines 99-112; no test file imports `MissingServletRequestParameterException`.
- **Recommendation:** Include a MockMvc test in the Phase 10 export test suite that calls `GET /api/v1/exports/transactions` without `from` or `to` and asserts a 400 with title "Missing request parameter".
- **Confidence:** Medium — the handler is correct by inspection, but only a test proves it stays correct.

---

## Finding 5 — Native-query result mapping is position-based and fragile

- **Issue:** `TransactionExportRepository.toRow(...)` casts `Object[]` elements by numeric index. The SELECT clause and the row mapping are separated by 40 lines; a future edit that reorders, adds, or removes columns in the SQL will compile cleanly but fail at runtime with a `ClassCastException` or silently wrong data.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRepository.java` lines 35-47 (SQL) and 65-82 (mapping).
- **Recommendation:** Add a strict comment at the top of the SQL warning that column order must stay in sync with `toRow`, or switch to a JPA `@SqlResultSetMapping` / `@ConstructorResult` so the mapping is declared alongside the query and validated at startup.
- **Confidence:** Low — the code is correct now, but the maintenance hazard is real.

---

## Finding 6 — CSV responses lack a `Content-Disposition` attachment header

- **Issue:** The CSV response returns `text/csv` with no `Content-Disposition`. A merchant using a browser will likely see the CSV rendered inline rather than downloaded as a file, and automated clients receive no suggested filename.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` lines 56-58.
- **Recommendation:** Add `Content-Disposition: attachment; filename="transactions-<from>-<to>.csv"` to CSV responses.
- **Confidence:** Low — usability gap, not a correctness bug.

---

## Finding 7 — The CSV body is fully materialized in memory

- **Issue:** `toCsv(...)` appends every row to a single `StringBuilder` before returning the complete string. This compounds the already-disclosed unbounded-result risk (Finding 8 rejected at Phase 4): a large export allocates the full CSV in the heap twice (once as row objects, once as the giant string).
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` lines 76-95.
- **Recommendation:** When the scale concern is revisited, stream CSV rows directly to the response `OutputStream` instead of materializing the full string. For the current MVP, document this as the known upper-bound of the accepted risk.
- **Confidence:** Low — aligns with the disclosed Phase 4 stance, but worth noting as a concrete amplification of that risk.

---

## Summary

The T22 implementation follows the frozen brief closely: merchant-scoped read, observed payment columns, deterministic ordering, minimal CSV escaping, and proper exception handling. The most important additions before considering it complete are tests (Finding 3) and a regression test for the new missing-parameter handler (Finding 4). Findings 1, 2, 5, 6, and 7 are smaller polish/maintenance items that should be dispositioned at the Phase 9 gate.
