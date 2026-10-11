# payment · T02 · Task Implementation Brief (TIB)

## Task

T02 — Schema V1.

## Purpose

Create the `payments` schema baseline (all 8 tables, verbatim from `design.md` §4c), run it
against local Postgres, and establish the least-privilege `payment_app` runtime role — granted
INSERT+SELECT only on the two append-only/history tables this task names, and nothing on the
other six (every other table's own grant lands in whichever later task first needs it).

## Scope

**In:**
- `V1__payments_baseline.sql` — schema-only, verbatim §4c, no role/grant statement.
- `V2__payment_app_role_and_grants.sql` — idempotent role creation, schema/sequence `USAGE`, and
  INSERT+SELECT-only on `ledger_entries` and `verification_records`.
- Running `mvn -pl services/payment flyway:migrate` against local Docker Compose Postgres.
- One README line documenting the local-dev `ALTER ROLE payment_app PASSWORD ...` step.

**Out:**
- Any grant on `invoices`, `payments`, `receipts`, `ledger_anchors`, `processed_events`, or
  `outbox` for `payment_app` — deferred to the task that first needs each one.
- Any Java code (entity, repository, service, controller) — tasks 5, 9, 10, 15, 19, 23.
- Setting `payment_app`'s real password anywhere outside the local-dev README instruction.

## Business Rules

None — no `requirements.md` R-number applies to this schema-only task.

## Locked Decisions

- **L6** — `NUMERIC(78, 0)` for every amount column; no floating-point type anywhere in `V1`.
- **L7** — `ledger_entries` append-only in practice, not just in comment: `payment_app` genuinely
  cannot `UPDATE`/`DELETE` it (or `verification_records`), proven against real Postgres.
- **L12** — this migration's own table set matches `design.md` §5's stated module ownership; no
  table is invented or renamed relative to that mapping.
- **L13** — no password committed in `V2__payment_app_role_and_grants.sql`.

## Dependencies

None at the Java level. Infrastructure: local Docker Compose Postgres running and reachable at
`localhost:5432` (the Flyway Maven plugin's own already-configured admin-role URL, from T01).

## Inputs

- `design.md` §4c (the full `V1__payments_baseline.sql` text, copied verbatim).
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` and
  `services/notification/.../V2__notification_app_role_and_grants.sql` (the two real,
  already-built templates for the role/grants file's shape).

## Outputs

- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (new).
- `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql` (new).
- The `payments` schema created in the local Docker Compose Postgres instance (a real, run
  side-effect of this task, not just a file).

## State Changes

Database state: the `payments` schema and its 8 tables are created; the `payment_app` role is
created (no password set by the migration itself).

## Files to Create

- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`
- `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql`
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`

## Files to Modify

- `services/payment/README.md` — one line, the local-dev `ALTER ROLE` instruction.

## Files NOT to Modify

- `services/payment/pom.xml` (the Flyway plugin is already correctly configured from T01 — no
  change needed).
- `PaymentServiceApplication.java`, `T01SkeletonRegressionTest.java` (T01's own files, out of
  scope).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — `V1__payments_baseline.sql` matches `design.md` §4c verbatim: all 8 tables
  (`invoices`, `payments`, `verification_records`, `receipts`, `ledger_entries`, `ledger_anchors`,
  `processed_events`, `outbox`), every check constraint, every index.
- **AC2** — `mvn -pl services/payment flyway:migrate` succeeds against local Docker Compose
  Postgres; the `payments` schema exists afterward with all 8 tables.
- **AC3** — `payment_app` exists; connecting as `payment_app`, `INSERT`/`SELECT` succeed on
  `ledger_entries` and `verification_records`; `UPDATE`/`DELETE` on either fail with a real
  Postgres permission error.
- **AC4** — `payment_app` can `INSERT` into either granted table relying on its
  `GENERATED ALWAYS AS IDENTITY` column without a separate sequence-permission failure (schema-wide
  `GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payments TO payment_app`).
- **AC5** — `payment_app` has no grant whatsoever on the other six tables.
- **AC6** — no password for `payment_app` is committed anywhere in `V2`; `README.md` documents the
  one manual local-dev `ALTER ROLE` step.

## Required Tests

- `com.themistra.payment.SchemaBaselineMigrationIntegrationTest` (Testcontainers Postgres, new):
  asserts AC1 (table/constraint existence), AC2 (migration succeeds cleanly), AC3 (permission
  grant/denial connecting specifically as `payment_app`, not the admin role), AC4 (identity-column
  insert succeeds), and AC5 (no grant on the other six tables — a real
  `information_schema.table_privileges` query for `payment_app`, not a text-scan).

## Constraints

- **Security:** `payment_app` is the only role this task creates; it owns nothing (table owner
  stays the migration/admin role) — the owner/grantee split is what makes the restriction real
  (Phase 0's own cited lesson: table owners bypass GRANT/REVOKE).
- **Transaction:** not applicable (DDL/role statements, no application transaction yet).
- **Module boundaries:** not applicable (no Java code).
- **Null handling / performance / thread-safety:** not applicable.
- **Idempotency:** the role-creation block must be safe to re-run (guarded `CREATE ROLE`); plain
  `GRANT` statements need no such guard (re-granting an already-held privilege is a silent Postgres
  no-op, per crypto-service's own T04 lesson, confirmed in that service's real migration history).

## Open Questions

No blockers.
