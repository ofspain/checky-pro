# payment · T02 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / AC | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| AC1 — V1 verbatim, all 9 tables | Yes | `V1__payments_baseline.sql` (130 lines) | `allNineBaselineTablesExistAndNoOthers`, `v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock` | No | No (corrected from an initial 8-table miscount, caught at Phase 3, fixed before any code existed) |
| AC2 — migration succeeds; `payment_app` has `LOGIN`/`CONNECT` | Yes | real `mvn flyway:migrate` run (Phase 6); `V2__payment_app_role_and_grants.sql:20-25` | `bothMigrationsAreRecordedAsSuccessfulInFlywayHistory`, `paymentAppHasLoginAndDatabaseConnect` | No | No |
| AC3 — INSERT/SELECT succeed, UPDATE/DELETE denied on the two granted tables | Yes | real TCP checks (Phase 6); `V2:...GRANT INSERT, SELECT...` | `paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheTwoGrantedTables` | No | No |
| AC4 — identity-sequence insert works | Yes | `V2`'s schema-wide sequence grant | covered within the same AC3 test (INSERT succeeds) | No | No |
| AC5 — no grant on the other seven tables | Yes | real `information_schema` query (Phase 6) | `paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope` (now INSERT/UPDATE/DELETE too, Phase 11), `informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables`, `grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore` | No | No |
| AC6 — no password committed in V2 | Yes | `V2` file content (comment-stripped) | `noPasswordIsCommittedInTheRoleMigration` | No | No |
| AC7 — tables owned by admin role, never `payment_app` | Yes | real `pg_tables` query (Phase 6) | `baselineTablesAreOwnedByTheMigrationRoleNeverByPaymentApp` | No | No |
| AC8 — `payment_app` cannot CREATE/DROP in `payments` | Yes | real TCP checks (Phase 6) | `paymentAppCannotPerformDdlInThePaymentsSchema` | No | No |
| L6 — `NUMERIC(78,0)`, no floating-point type | Yes | `V1`'s own `amount` columns | `amountColumnsAreNumericSeventyEightZeroAndNoFloatingPointTypeExistsAnywhere` (Phase 11) | No | No |
| L7 — `ledger_entries` genuinely append-only | Yes | covered by AC3's own UPDATE/DELETE denial | same as AC3 | No | No |
| L12 — module boundaries / table ownership mapping | Yes (vacuously — no Java code yet) | table set matches `design.md` §5 | — (ArchUnit lands at task 25) | No | No |
| L13 — no secret, with the one documented carve-out | Yes | `V2` (no password); `README.md` (the one placeholder) | `noPasswordIsCommittedInTheRoleMigration` | No | No |
| V1/V2 architectural split (not a numbered AC, but load-bearing) | Yes | `V1` schema-only, `V2` role-only | `v1ContainsNoRoleOrGrantStatements` (Phase 11) | No | No |
| V2 environment-agnostic (`current_database()`, no hardcoded name) | Yes | `V2:24` | `v2NeverHardcodesTheDatabaseName` (Phase 11) | No | No |
| V2 idempotent under a real re-run (not just Flyway's own skip) | Yes | `V2`'s `IF NOT EXISTS` guard | `v2sOwnSqlIsIdempotentWhenReExecutedDirectlyNotJustSkippedByFlyway` (Phase 11) | No | No |

## Principal-engineer review

1. **Is the task fully complete?** Yes. `V1`/`V2` exist, were run for real against local Postgres
   (not just written), `README.md` documents the local-dev step, and the test suite proves every
   claim empirically. All three review passes (Phase 3, 8, 11) ran; every genuinely valid finding
   across all three was applied and mutation-verified (or confirmed via a real first-run failure);
   every rejected finding (Phase 8's three, all premature-test-review) was rejected with cited,
   re-checked evidence.
2. **Does it satisfy every acceptance criterion?** Yes — AC1 through AC8, plus every Phase-3
   addition, all pass against a real Testcontainers Postgres instance (25/25, including the 8
   carried over from T01 in the same module).
3. **Does it violate any LOCKED decision?** No. L6 (money types), L7 (append-only in practice,
   not just comment), L12 (table/module mapping), and L13 (secrets, with its one explicit,
   human-approved carve-out) are all satisfied and tested against a live database, not merely
   asserted from the SQL text.
4. **Remaining risks?**
   - None internal to this task. The suite was mutation-tested across Phase 10 (2 real test bugs
     found and fixed before declaring it done) and Phase 11 (7 more, all genuine, none factually
     wrong) — an unusually thorough review history for a schema-only task, reflecting how much
     real behavior (grants, ownership, auth) a "just write the DDL" task actually carries.
   - **One real, factual error of my own, caught and corrected mid-pipeline:** Phase 0 and Phase 1
     both miscounted the baseline as 8 tables; `design.md` §4c actually defines 9 (`shedlock` was
     dropped from my own count). Caught at Phase 3 (Kimi), verified directly against source,
     corrected forward from Phase 4 onward — disclosed here again for completeness, not hidden.
   - **The same external, unrelated risk already disclosed for T01:** the 7/753 `auth-service`
     test regression remains untouched and separately tracked in memory — still not this task's
     concern, and still not silently omitted.

## Verdict

**PASS** — every acceptance criterion is implemented, tested against a real database, and
mutation-verified; no LOCKED decision is violated; both self-corrections (the table-count error,
the seed-helper `tx_hash` collision) were caught and fixed within the pipeline itself, not left
for a human to find later.
