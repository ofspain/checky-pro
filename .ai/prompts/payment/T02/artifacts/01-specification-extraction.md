# payment · T02 · Phase 1 — Specification Extraction

## Business Rules

None directly — like T01, this task introduces no behavior of its own; it lays the schema every
later requirement (R1–R31) will persist against. No `requirements.md` R-number names "the schema
must exist," matching both sibling services' own identical T02/T01-era schema task.

## Locked Decisions

- **L6** — money is `NUMERIC`/`BigDecimal`; the baseline migration's own `amount` columns
  (`invoices.amount`, `payments.amount`) are `NUMERIC(78, 0)` exactly as design.md §4c specifies —
  no floating-point type anywhere.
- **L7** — `ledger_entries` is append-only. This task is where that becomes real: the
  `payment_app` runtime role (named per Phase 0's cross-service convention, not spec-stated
  directly) is granted INSERT+SELECT only on `ledger_entries` **and** `verification_records`
  (task statement's own explicit second table) — no UPDATE, no DELETE, on either, ever.
- **L12** — module boundaries (package-by-feature). Not directly exercised by a schema-only task
  (no Java packages added), but the migration's own table set must not pre-empt a later task's own
  entity ownership — each table in `V1__payments_baseline.sql` already names which future module
  owns it (design.md §5), and this task changes none of that mapping.
- **L13** — secrets discipline. The role-and-grants migration (whichever file it lands in) commits
  no password for `payment_app` — confirmed as the universal, two-for-two sibling convention in
  Phase 0, not an assumption.

## Files involved

**New:**
- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` — verbatim
  transcription of `design.md` §4c's full baseline (8 tables: `invoices`, `payments`,
  `verification_records`, `receipts`, `ledger_entries`, `ledger_anchors`, `processed_events`,
  `outbox`). No role/grant statement in this file (Phase 0 precedent: both siblings keep `V1`
  schema-only).
- `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql` —
  **resolved by precedent, not left as an open question:** mirrors
  `crypto-service`'s/`notification-service`'s own identical `V2__<service>_app_role_and_grants.sql`
  naming and content shape exactly (idempotent `CREATE ROLE payment_app LOGIN` guarded by an
  existence check, dynamic `GRANT CONNECT` via `current_database()`, schema-wide `USAGE` +
  sequence `USAGE, SELECT`, then the task's own two named tables at INSERT+SELECT only). Both
  sibling services made this exact same two-file split from an equally single-sentence spec task,
  so this is the established house convention, not a new design choice being introduced here.
- `services/payment/README.md` — gains the one-line local-dev `ALTER ROLE payment_app PASSWORD
  'payment-app-local-only';` instruction, mirroring both siblings' own README addition in their
  equivalent task.

**Existing, untouched:** `services/payment/pom.xml` (Flyway plugin already registered in T01, no
change needed — the plugin's own `<schemas>payments</schemas>` config already matches).

## Dependencies

None at the Java level (no entity/repository/service code in this task — those are tasks 5, 9, 10,
15, 19, 23 as each table gets its own owning module). Infrastructure dependency: local Docker
Compose Postgres must be running for the actual `mvn -pl services/payment flyway:migrate` run the
task statement itself requires as part of the task, not just verification.

## Acceptance Criteria

- **AC1** (task statement, part 1): `V1__payments_baseline.sql` exists and matches `design.md`
  §4c's own SQL verbatim — all 8 tables, all check constraints, all indexes.
- **AC2** (task statement, part 1, continued): running `mvn -pl services/payment flyway:migrate`
  against local Docker Compose Postgres succeeds and the `payments` schema is created with all 8
  tables.
- **AC3** (task statement, part 2; L7): `payment_app` exists, can `INSERT`/`SELECT` on
  `ledger_entries` and `verification_records`, and genuinely **cannot** `UPDATE` or `DELETE`
  either — proven against a real Postgres, not asserted from the SQL text alone (Phase 0's own
  cited lesson: table owners bypass GRANT/REVOKE, so this only means something if `payment_app` is
  a distinct, non-owning role, which the migration's own structure must guarantee).
- **AC4** (Phase 0 precedent, not literal task text — same category as T01's bare-Application-
  class precedent): the sequence-usage grant (`GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA
  payments TO payment_app`) is present, or `payment_app`'s own `INSERT` on either of its two
  granted tables fails at the identity-sequence step regardless of the table-level grant being
  correct (the exact failure mode crypto-service's own T02 lesson names).

## Tests required

No pre-mapped named test in `package.md` §8 (same situation as T01 — this task has no R-number).
Per Phase 0's own cited lesson ("a Testcontainers-based JDBC integration test is strictly better
than a text-scan guard test whenever the thing being guarded is actual database behavior"), this
task's test is **not** a `T01SkeletonRegressionTest`-style plain-JUnit file scan — it needs a real
Postgres:

- `com.themistra.payment.SchemaBaselineMigrationIntegrationTest` (Testcontainers Postgres),
  asserting:
  - all 8 tables exist with their check constraints intact (AC1/AC2).
  - connecting **as `payment_app`** (not the migration/admin role), `INSERT` and `SELECT` succeed
    on `ledger_entries` and `verification_records`; `UPDATE` and `DELETE` on either are rejected by
    Postgres itself with a real permission-denied error (AC3).
  - `payment_app` can `INSERT` a row relying on `GENERATED ALWAYS AS IDENTITY` on one of its two
    granted tables without a separate, manual sequence grant failure (AC4).
  - `payment_app` has **no** grant at all on the other six tables (`invoices`, `payments`,
    `receipts`, `ledger_anchors`, `processed_events`, `outbox`) — proving this task did not
    pre-emptively grant anything beyond its own two named tables (Phase 0's cited
    least-privilege-by-construction convention).

## Open Questions

None that block this task. Both items Phase 0 flagged (one file vs. two; the exact role name) are
resolved above by direct citation to the established, two-for-two cross-service precedent, not
left open.
