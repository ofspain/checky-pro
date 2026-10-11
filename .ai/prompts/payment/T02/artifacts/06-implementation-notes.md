# payment · T02 · Phase 6 — Implementation Notes

## What changed

1. **`services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (new)** —
   verbatim copy of `design.md` §4c's SQL. Confirmed byte-for-byte identical via `diff` against
   the spec file directly (not eyeballed) before moving on: 9 tables (`invoices`, `payments`,
   `verification_records`, `receipts`, `ledger_entries`, `ledger_anchors`, `processed_events`,
   `outbox`, `shedlock`).
2. **`services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql`
   (new)** — idempotent `CREATE ROLE payment_app LOGIN`, dynamic `GRANT CONNECT ON DATABASE`,
   schema `USAGE`, sequence `USAGE, SELECT`, and `INSERT, SELECT` on exactly `ledger_entries` and
   `verification_records`. No grant on any of the other seven tables. No password committed.
3. **`services/payment/README.md` (modified)** — added the "Local development database" section,
   mirroring `services/crypto/README.md`'s own identical shape exactly (substituting
   `payment_app`/`payments`), with one adaptation: the closing sentence notes
   `application.properties` doesn't exist yet (that's task 3), rather than claiming a matching
   default already exists there.

## Real migration run (task statement's own explicit instruction, not just file-writing)

Started the local Postgres container (`auth-postgres-1`, confirmed healthy), then ran the actual
command the task names:

```
mvn -pl services/payment flyway:migrate
```

Real output: `Creating schema "payments"`, `Migrating schema "payments" to version "1 - payments
baseline"`, `Migrating schema "payments" to version "2 - payment app role and grants"`,
`Successfully applied 2 migrations to schema "payments", now at version v2`, `BUILD SUCCESS`.

## Database state verified directly (not assumed from the SQL text)

- `\dt payments.*` (via `docker exec`) — all 9 real tables present, every one owned by `checky`
  (the admin/migration role), confirmed via `pg_tables.tableowner` — **AC7 satisfied**, not just
  by construction but checked.
- `pg_roles.rolcanlogin` for `payment_app` → `t` — **AC2's `LOGIN` requirement confirmed.**
- `has_database_privilege('payment_app', 'checky', 'CONNECT')` → `t` — **AC2's `CONNECT`
  requirement confirmed.**
- `information_schema.table_privileges` for grantee `payment_app` → exactly 4 rows:
  `ledger_entries` (INSERT, SELECT), `verification_records` (INSERT, SELECT). **AC3 (positive
  half) and AC5 both confirmed** — zero grants on any of the other seven tables.
- **Real TCP permission checks, connecting as `payment_app` from a separate container (not
  `docker exec` into the server's own container, which bypasses password auth via loopback trust
  — the exact pitfall Phase 0 flagged):**
  - `UPDATE payments.ledger_entries ...` → `ERROR: permission denied for table ledger_entries`.
  - `DELETE FROM payments.verification_records;` → `ERROR: permission denied for table
    verification_records`.
  - `CREATE TABLE payments.evil_test(id int);` → `ERROR: permission denied for schema payments`
    (**AC8 confirmed**).
  - `SELECT count(*) FROM payments.ledger_entries;` → succeeds, `count = 0` (positive control,
    proving the denials above are real permission boundaries, not a broken connection).

Every one of these was run for real against the actual migrated database, not inferred from
reading the migration SQL.

## Mapping to the plan (Phase 5) and acceptance criteria

| Plan step | Done | AC |
|---|---|---|
| 1. `V1__payments_baseline.sql` | Yes, byte-for-byte verified | AC1 |
| 2. `V2__payment_app_role_and_grants.sql` | Yes | AC2, AC3, AC4, AC5, AC6, AC7, AC8 |
| 3. Real migration run | Yes — real Maven/Flyway output captured above | AC2 |
| 4. `README.md` update | Yes | AC6 |
| 5. Test class | Deferred to Phase 10, per this phase's own explicit rule | — |
| 6. Final check | Partially done manually (real psql checks above); the formal, mutation-tested Java test is Phase 10's job | — |

## Deviations from the plan

None. No file outside the plan's own "Files to create"/"Files to modify" list was touched.
