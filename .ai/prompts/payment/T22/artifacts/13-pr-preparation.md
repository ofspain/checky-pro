# payment · T22 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable.

## Commit title

`payment T22: TransactionExportService/ExportController - tax-ready transaction export (R27)`

## Commit message

```
payment T22: TransactionExportService/ExportController - tax-ready transaction export (R27)

Implements GET /api/v1/exports/transactions: a merchant-scoped, date-ranged, tax-ready export of
ATTESTED payment history, in both CSV and JSON, backed by a single three-table reporting read
(invoices/payments/receipts) via a plain EntityManager-backed repository that owns no entity -
the minimal extension of InvoiceRepository's own established "native query from a module that
owns neither target table" precedent to a query that in fact owns none of its three tables.

Kimi's own Phase 3 design challenge found a genuinely high-severity issue my own TIB missed
entirely: amount/payToAddress needed pinning to payments' own observed on-chain values, never
invoices' expected ones - the two tables carry duplicate-looking column names for deliberately
different things, and the export column name (payToAddress) itself maps to payments.to_address,
not the similarly-named invoices.pay_to_address, a real naming trap. Mutation-verified both this
correction and the frozen deterministic ORDER BY at Phase 10, and again implicitly when both
tests failed red during Phase 11's test-review response and were reverted after confirming so.

Implementation surfaced one real, previously-unencountered gap: ExportController's from/to are
this service's first required @RequestParam anywhere, and Spring's own
MissingServletRequestParameterException for a missing one was unhandled anywhere in this
codebase, falling through to a 500 - fixed in the shared ApiExceptionHandler (a generic
framework-level concern, not a feature-module one). Phase 9's independent review caught and fixed
a second real regression during its own fix: Kimi's suggested isCompatibleWith(CSV) check for
Accept-header negotiation would have resolved "Accept: */*" to CSV, contradicting this task's own
frozen "wildcard means JSON, the default" decision, since a wildcard is compatible with
everything - corrected to an exact type/subtype match before committing, confirmed via a scratch
probe.

Two independent Kimi review cycles (Phase 8, Phase 11) found 15 issues total across design,
implementation, and tests; all were verified against source before accepting or rejecting, not
trusted verbatim - one Phase 11 suggestion (an exact Content-Disposition filename assertion) was
itself factually wrong against the real code and corrected rather than applied literally.

Full suite: 451/451 passing (420 pre-task baseline + 31 new: 21 controller, 10 integration).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/resources/db/migration/V10__payment_app_export_index.sql` (new).
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRow.java` (new).
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRepository.java` (new).
- `services/payment/src/main/java/com/themistra/payment/export/TransactionExportService.java` (new).
- `services/payment/src/main/java/com/themistra/payment/export/InvalidExportRequestException.java` (new).
- `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java` (new).
- `services/payment/src/main/java/com/themistra/payment/export/ExportController.java` (new).
- `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` —
  one new `MissingServletRequestParameterException` handler (disclosed deviation from the
  initial "Files NOT to Modify" list, Phase 6/9).

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/export/ExportControllerTest.java`
  (new) — 21 tests.
- `services/payment/src/test/java/com/themistra/payment/export/TransactionExportServiceIntegrationTest.java`
  (new) — 10 tests.
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`
  — mechanical update for the new migration count (9 → 10), same as every prior task that added
  a migration.

## Summary

T22 is the platform's first export/reporting-read endpoint and the first endpoint anywhere in
this service with a required query parameter — both surfaced genuine, previously-unencountered
gaps (the missing-parameter handler; the Accept-header wildcard regression) rather than simply
extending an existing pattern. The most consequential finding was Kimi's own Phase 3 catch of the
observed-vs-expected column source, including the `payToAddress` naming trap, which would have
silently reported the wrong payment data on every discrepancy case (`OVERPAID`/`UNDERPAID`, or a
payment to a different observed address than requested) had it shipped as originally briefed.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `test-compile`: clean throughout every phase.
- `mvn -o -pl services/payment -am test -Dtest=ExportControllerTest`: **21/21 passing**.
- `mvn -o -pl services/payment -am test -Dtest=TransactionExportServiceIntegrationTest`:
  **10/10 passing** (real Testcontainers Postgres, three-table join).
- Three empirical scratch probes this task (each written, run, and deleted before its own
  commit, per this session's established discipline): native-query column runtime types
  (Phase 6), the `wantsCsv` Accept-header/format logic including the wildcard regression
  (Phase 9), and the tie-break ordering's real discriminating power (Phase 11).
- Mutation-testing this task: the observed-vs-expected column source, the `ORDER BY` direction,
  and the `payment_uuid` tie-break direction — all three confirmed red when deliberately broken,
  then reverted and confirmed green.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`; the daemon itself was found stopped mid-task and
  restarted): **451/451 passing** (420 pre-task baseline + 31 new).

## Specification references

- **Task:** T22 — Tax export (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R27, R28 (continuity).
- **LOCKED decisions:** L2, L6, L9, L10, L12 (all continuity).
- **Design decision:** O5 (tax-export format, `design.md`).
- **Process decisions (not numbered requirements):** observed-vs-expected column sourcing,
  deterministic ordering, the `format`-over-`Accept` content-negotiation convention (invented
  here — the platform's first export endpoint, no existing precedent to mirror), minimal CSV
  escaping, and the `Content-Disposition` filename shape — all resolved via Phase 2/4/9, with
  human sign-off via `AskUserQuestion` at both human-approval gates (Phase 4, Phase 9).
