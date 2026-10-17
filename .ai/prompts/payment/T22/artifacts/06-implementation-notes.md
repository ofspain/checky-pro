# payment · T22 · Phase 6 — Implementation Notes

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md`

## Files created (exactly as planned)

1. `services/payment/src/main/resources/db/migration/V10__payment_app_export_index.sql`
2. `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRow.java`
3. `services/payment/src/main/java/com/themistra/payment/export/TransactionExportRepository.java`
4. `services/payment/src/main/java/com/themistra/payment/export/TransactionExportService.java`
5. `services/payment/src/main/java/com/themistra/payment/export/InvalidExportRequestException.java`
6. `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java`
7. `services/payment/src/main/java/com/themistra/payment/export/ExportController.java`

## One deviation from "Files NOT to Modify" — disclosed, not silent

**`services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` was
modified** — not listed in any prior phase's Files to Modify. Genuine gap found while wiring the
controller, not anticipated by the TIB, Kimi's Phase 3 review, or the Phase 4 freeze: `@RequestParam
Instant from`/`to` with Spring's default `required = true` throws
`MissingServletRequestParameterException` on a missing param — an exception this codebase had
never encountered before, since `ExportController` is the **first** endpoint anywhere in this
service with a required query parameter (every prior `@RequestParam` is `required = false`, and
every prior required input arrives as a path variable or `@Valid` request body). Unhandled, it
falls through to `ApiExceptionHandler.onUnexpected` and returns 500 — directly violating AC7
("missing `from`/`to` → a typed 400") and R29 (no internal detail on a 4xx/5xx path, here
compounded by the wrong status entirely).

Fixed by adding one handler method to `ApiExceptionHandler` (not a feature-module handler): this
is a generic Spring MVC framework exception, the same class of problem as the already-handled
`MethodArgumentTypeMismatchException` immediately below it, not a domain-specific concern that
belongs in `ExportExceptionHandler`. Confirmed this does not violate `ApiExceptionHandler`'s own
documented "must never import from a feature module" rule — the fix imports nothing from
`export`.

## Empirical confirmation (Phase 0's own flagged unknown, resolved before writing the repository)

Before writing `TransactionExportRepository.toRow`, wrote, ran, and deleted a throwaway
Testcontainers scratch test (mirrors T19/T20's own "written, run, deleted before commit"
precedent) seeding one real row across `invoices`/`payments`/`receipts` and printing each native-
query result column's actual runtime Java type. Confirmed against this exact Hibernate
6.6.22.Final / Postgres 16 pairing:

| Postgres type | Java runtime type |
|---|---|
| `uuid` | `java.util.UUID` |
| `smallint` | `java.lang.Short` |
| `numeric` | `java.math.BigDecimal` |
| `timestamptz` | `java.time.Instant` (directly — never `java.sql.Timestamp`) |

`TransactionExportRepository.toRow` casts accordingly, with no defensive dual-path handling for
an alternative temporal type — the confirmed behavior is relied on directly, consistent with this
codebase's own "don't add handling for a scenario that can't happen" convention, not hedged
against a hypothetical future Hibernate behavior change.

## Notes on the frozen design followed exactly

- Native query joins `payments.payments`/`payments.invoices`/`payments.receipts` directly by
  schema-qualified table name; no `Invoice`/`Payment`/`Receipt` entity class imported anywhere in
  `export` (L12).
- `amount`/`payToAddress` read from `payments` (`p.amount`, `p.to_address`), never `invoices`
  (Phase 4 Finding 2 correction).
- `receiptUri` built as `"s3://" + s3_bucket + "/" + s3_key + ".json"` (Phase 4 Finding 3).
- `ORDER BY p.finalized_at ASC, p.payment_uuid ASC` (Phase 4 Finding 4).
- `format` query param (exactly `csv`/`json`, case-insensitive, else 400 — Phase 4 Finding 5)
  takes precedence over `Accept: text/csv`.
- Minimal RFC 4180 escaping applied per CSV field (Phase 4 Finding 1) — quotes a value containing
  `,`, `"`, or `\n`, doubling embedded quotes; everything else passes through unchanged.
- `from > to` → `InvalidExportRequestException` → 400 via the new `ExportExceptionHandler`
  (Finding 6); a malformed (non-parseable) `from`/`to` value is left to Spring's own
  `MethodArgumentTypeMismatchException` path, already mapped by the existing generic handler
  (Finding 7, rejected at Phase 4 — confirmed still correct, now that the sibling missing-param
  gap above is also closed).

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **420/420 passing** — no
  regression from either the new module or the `ApiExceptionHandler` addition. (No new tests
  yet — Phase 10's job; this count is the pre-existing baseline, confirming nothing broke.)
- One pre-existing test needed a mechanical update unrelated to this task's own logic:
  `SchemaBaselineMigrationIntegrationTest.bothMigrationsAreRecordedAsSuccessfulInFlywayHistory`
  hard-coded the migration count at 9; updated to 10 for the new `V10` migration (mirrors every
  prior task's own identical, expected update to this same test).
