# payment · T02 · Phase 10 — Test Generation (Test Manifest)

New file: `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`
(12 test methods, Testcontainers Postgres, real JDBC). No production code was changed beyond what
Phase 6 already committed. Structural template: `services/crypto`'s own
`ChainBaselineMigrationIntegrationTest`, adapted to this task's own scope (9 tables, 2 granted
tables, no `application.properties` yet).

## Test → Acceptance Criterion mapping

| Test method | Verifies |
|---|---|
| `allNineBaselineTablesExistAndNoOthers` | AC1 |
| `v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock` | AC1 (automates Phase 6's manual `diff`) |
| `bothMigrationsAreRecordedAsSuccessfulInFlywayHistory` | AC2 |
| `paymentAppHasLoginAndDatabaseConnect` | AC2 (automates Phase 6's manual `pg_roles`/`has_database_privilege` checks) |
| `v2RoleCreationGuardIsIdempotentUnderARealReRun` | idempotency (Locked Decisions) |
| `paymentAppRoleRequiresItsProvisionedPassword` | real auth path (not a `docker exec` loopback-trust bypass) |
| `paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheTwoGrantedTables` | AC3, AC4 |
| `paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope` | AC5 |
| `informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables` | AC5 (the real query form) |
| `noPasswordIsCommittedInTheRoleMigration` | AC6 |
| `baselineTablesAreOwnedByTheMigrationRoleNeverByPaymentApp` | AC7 |
| `paymentAppCannotPerformDdlInThePaymentsSchema` | AC8 |

## Real bugs found and fixed while writing these tests (not hidden)

Ran the suite for real before declaring it done — 2 of 12 tests failed on first run:

1. **`noPasswordIsCommittedInTheRoleMigration`** — the exact comment-vs-content ambiguity bug
   already found and fixed twice in T01's own test file: `V2`'s own explanatory comment
   legitimately documents the local-dev `ALTER ROLE payment_app PASSWORD 'payment-app-local-
   only'` instruction in prose, which the regex then matched as if it were a real, committed
   password. Fixed with a `stripSqlComments` helper (the SQL counterpart to T01's own
   `stripXmlComments`/`stripJavaComments`), applied before this one check.
2. **`informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables`** — a genuine test bug, not
   a comment issue: `information_schema.table_privileges` returns one row per privilege type, so
   each granted table appears twice (INSERT, SELECT) — the raw row list never equals the 2-element
   `GRANTED_TABLES` list. Fixed by adding `DISTINCT` to the query.

Re-ran after both fixes: **20/20 pass** (12 new + 8 from T01, same module).

## Mutation checks (non-vacuity proof)

- Temporarily added `UPDATE` to `V2`'s own grant statement (simulating an accidental leak):
  **1 failure** (`paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheTwoGrantedTables`).
  Reverted: 20/20 pass.
- Temporarily added a new `GRANT SELECT ON payments.invoices TO payment_app;` line (simulating a
  pre-emptive grant this task deliberately doesn't make): **2 failures**
  (`informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables`,
  `paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope`). Reverted: 20/20 pass, confirmed via
  `git status` that no stray edit survived either mutation.

## Final state

`mvn -pl services/payment test` → `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0`,
`BUILD SUCCESS`.
