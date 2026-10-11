# payment · T02 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T02/artifacts/11-test-review.md`  
Consumed: `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`, `artifacts/10-test-generation.md`, `artifacts/04-frozen-task-brief.md`  
Scope: T02 only. Recommendations only — no rewrite.

---

### 1. V2 idempotency is only proven through Flyway bookkeeping, not real SQL re-execution

- **Gap:** `v2RoleCreationGuardIsIdempotentUnderARealReRun` (line 165) calls `Flyway.configure(...).migrate()` a second time. Flyway sees V2 as already applied and skips it; the actual `CREATE ROLE ... IF NOT EXISTS` guard and `GRANT` statements are never re-executed.
- **Why it matters:** A regression in the `IF NOT EXISTS` guard or in a `GRANT` statement's idempotency would not be caught by this test. The idempotency requirement in the frozen brief's Locked Decisions/Constraints is about the SQL itself, not Flyway's version tracking.
- **Suggested test:** Read `V2__payment_app_role_and_grants.sql` from disk and execute it directly against the admin connection after the initial migration, then re-verify that `payment_app` still has exactly the same LOGIN/CONNECT/privileges. Mirror `services/notification`'s `v2sOwnSqlIsIdempotentWhenReExecutedDirectlyNotJustSkippedByFlyway`.

---

### 2. Granted-table privilege types are not verified

- **Gap:** `informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables` (line 217) asserts the *set of table names* with grant rows equals `GRANTED_TABLES`. It does not check which privilege types (`INSERT`, `SELECT`, `UPDATE`, `DELETE`, etc.) are held on those tables.
- **Why it matters:** If V2 accidentally granted only `SELECT` (no `INSERT`) on `ledger_entries`, the ungranted-table test would still pass, and the functional test would fail later with a less precise error. AC3 explicitly requires `INSERT+SELECT only`.
- **Suggested test:** Query `information_schema.table_privileges WHERE grantee = 'payment_app' AND table_schema = 'payments'` and assert that for `ledger_entries` and `verification_records` the privilege-type set is exactly `{INSERT, SELECT}` (and no other privilege type appears for those tables).

---

### 3. No negative scan that V2 avoids hardcoding the database name

- **Gap:** `paymentAppHasLoginAndDatabaseConnect` (line 146) verifies `payment_app` has `CONNECT`, but it would pass even if V2 contained `GRANT CONNECT ON DATABASE checky TO payment_app`.
- **Why it matters:** The frozen brief's L13/Constraints discipline requires the migration to be environment-agnostic; the sibling services' own tests guard against this exact hardcoding risk.
- **Suggested test:** Strip SQL comments from `V2__payment_app_role_and_grants.sql` and assert that the executable text contains `current_database()` and does not contain the literal `checky`.

---

### 4. Ungranted-table DML denial is limited to SELECT

- **Gap:** `paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope` (line 200) only attempts `SELECT * FROM payments.<table>` for each ungranted table.
- **Why it matters:** A stray `INSERT` grant (without `SELECT`) on an ungranted table would not be caught here, though it would be caught by the information_schema test. More importantly, the test does not guard against privileges granted to `PUBLIC`, which the `information_schema.table_privileges` query (filtered to `grantee = 'payment_app'`) does not surface.
- **Suggested test:** For each ungranted table, attempt minimal `INSERT`, `UPDATE`, and `DELETE` statements as `payment_app` and assert each fails with a `permission denied` error, mirroring the DML-denial pattern already used for the granted tables.

---

### 5. L6 money-type / no-floating-point check is absent

- **Gap:** No test verifies that `invoices.amount` and `payments.amount` are created as `NUMERIC(78, 0)` or that no floating-point column type exists anywhere in the schema.
- **Why it matters:** L6 is a LOCKED decision, and AC1 requires every check constraint and column definition to match `design.md`. The byte-for-byte test is strong only if `design.md` is correct; a DB-level check proves the migration actually instantiates the intended types.
- **Suggested test:**
  - Query `information_schema.columns` for `table_schema = 'payments' AND column_name = 'amount'` and assert `data_type = 'numeric'`, `numeric_precision = 78`, `numeric_scale = 0`.
  - Scan `V1__payments_baseline.sql` (with comments stripped) for SQL type keywords `FLOAT`, `DOUBLE PRECISION`, and `REAL` anchored as type tokens.

---

### 6. `seed*AsAdmin` helpers use fragile last-row retrieval

- **Gap:** `seedPaymentAsAdmin` (line 320) and `seedReceiptAsAdmin` (line 349) insert rows and then read back the identity value with `SELECT id FROM payments.<table> ORDER BY id DESC LIMIT 1`.
- **Why it matters:** This pattern is order-dependent and would break if tests ever run in parallel or if a previous test leaves rows in the same table. It is also less readable than asking Postgres for the inserted key directly.
- **Suggested test improvement:** Rewrite the seed inserts to use `RETURNING id` and read the single returned key, eliminating the `ORDER BY ... LIMIT 1` fallback.

---

### 7. No negative scan that V1 contains no role/grant statements

- **Gap:** AC1 says V1 is schema-only with no role/grant statements. The byte-for-byte comparison against `design.md` enforces this indirectly, but there is no direct scan of the migration file.
- **Why it matters:** If a future edit to `design.md` accidentally introduces a `GRANT` or `CREATE ROLE` into the same verbatim block, the byte-for-byte test would still pass but the architectural split between V1 (schema) and V2 (grants) would be violated.
- **Suggested test:** Strip comments from `V1__payments_baseline.sql` and assert it contains no `CREATE ROLE` and no `GRANT` keywords.
