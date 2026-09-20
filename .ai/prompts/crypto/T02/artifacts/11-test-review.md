<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# crypto · T02 · Phase 11 — Test Review

Reviewed `artifacts/10-test-generation.md` and the actual test file
`services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java`.

Local Maven verification was not possible (`mvn` is not installed), so the review is based on reading
the committed test and migration SQL.

---

## Findings

### 1. The integration test cannot pass as written because `V2` hardcodes database name `checky`

- **Gap:** `V2__crypto_app_role_and_grants.sql` contains `GRANT CONNECT ON DATABASE checky TO crypto_app`,
  but the test's `PostgreSQLContainer` runs against the default database name (`test`). The Flyway
  migration in `@BeforeAll` will fail before any test assertion runs.
- **Why it matters:** Phase 10 claims a green `mvn -pl services/crypto verify`, but by inspection the
  migration is environment-specific and incompatible with the Testcontainers default. This blocks
  CI/local verification of AC2/AC3 and masks any real grant behavior.
- **Suggested test:** Either configure the container with `.withDatabaseName("checky")` and assert the
  JDBC URL ends with `/checky`, or change `V2` to be database-name-agnostic (e.g., move the `CONNECT`
  grant out of the migration into compose/infra). If the container is renamed, also assert that
  `crypto_app` can connect to that database after migration.

### 2. Table-existence assertion is too weak

- **Gap:** `allTenBaselineTablesExist` uses `assertThat(actual).containsAll(expected)`. This passes if
  the 10 expected tables exist, but also passes if unexpected extra tables exist. It also does not
  verify that `V1`'s columns, types, constraints, or indexes match `design.md` §4c.
- **Why it matters:** AC1 requires byte-for-byte equivalence to the spec. A future change could add a
  stray table, drop a column, or weaken a constraint without failing this test.
- **Suggested test:** Change the assertion to `containsExactlyInAnyOrder` the 10 tables (explicitly
  excluding `flyway_schema_history`). Add a companion schema-contract test that queries
  `information_schema.columns`, `pg_constraint`, and `pg_indexes` to assert the column names/types,
  check constraints, unique constraints, and indexes for each table.

### 3. No automated proof that `V2` is idempotent

- **Gap:** The self-review manually dropped the schema and re-ran migrations to prove the
  `IF NOT EXISTS` guard works, but the test suite does not exercise a re-run.
- **Why it matters:** A non-idempotent V2 (e.g., an unguarded `CREATE ROLE`) would break the common
  local-dev pattern of resetting the schema without recreating the Postgres container.
- **Suggested test:** After the initial migration in `@BeforeAll`, call `Flyway.migrate()` a second
  time and assert it succeeds with the schema version still at `2`. Optionally run `Flyway.validate()`
  and assert no checksum mismatch.

### 4. No positive assertion that `crypto_app` can SELECT on the granted tables

- **Gap:** `cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables` proves INSERT
  succeeds and UPDATE/DELETE are denied, but it never asserts SELECT works.
- **Why it matters:** AC3 is "INSERT+SELECT-only." A broken grant that accidentally revoked SELECT
  would not be caught.
- **Suggested test:** After inserting a row, query it back (e.g., `SELECT count(*) FROM chain.<table>
  WHERE tx_hash = ...`) and assert the count is `1`.

### 5. Scope-denial test covers only one table and only SELECT

- **Gap:** `cryptoAppHasNoAccessToTablesOutsideAc3Scope` checks only that `SELECT` on `chain.watches`
  is denied. It does not check other non-AC3 tables, nor INSERT/UPDATE/DELETE on any of them.
- **Why it matters:** A mis-scoped grant on, say, `outbox` or `provider_health` would not be detected.
- **Suggested test:** Loop over the non-AC3 tables (`watches`, `provider_health`, `chain_cursors`,
  `token_allowlist`, `screening_results`, `outbox`, `shedlock`) and assert that `SELECT`, `INSERT`,
  `UPDATE`, and `DELETE` all fail with "permission denied" when run as `crypto_app`.

### 6. No verification of `spring.flyway.enabled=false` (AC4)

- **Gap:** AC4 requires runtime Flyway to be disabled, but the test suite does not assert this
  property. Phase 10 argues it is covered by a manual smoke test.
- **Why it matters:** A one-line config regression (removing or flipping the property) would cause
  the app to attempt migrations as the restricted `crypto_app` role at startup and fail.
- **Suggested test:** Add a lightweight unit test that loads
  `services/crypto/src/main/resources/application.properties` via `java.util.Properties` and asserts
  `spring.flyway.enabled` equals `false`. A Spring-context smoke test is preferable when a main class
  exists, but the properties-level guard is sufficient for T02.

### 7. No verification that `crypto_app` cannot perform DDL

- **Gap:** The tests do not assert that the restricted role cannot create, alter, or drop tables in
  the `chain` schema.
- **Why it matters:** The least-privilege design depends on `crypto_app` having no DDL rights; the
  current tests only cover DML on specific tables.
- **Suggested test:** Assert that `CREATE TABLE chain.evil_test(id int)` and
  `DROP TABLE chain.observations` executed as `crypto_app` both throw a permission-denied error.

### 8. No verification of migration history

- **Gap:** There is no assertion that `flyway_schema_history` records both V1 and V2 as successfully
  applied.
- **Why it matters:** A skipped or misordered migration would silently break the schema-versioning
  contract.
- **Suggested test:** Query `chain.flyway_schema_history` and assert rows exist with `version = '1'`
  and `version = '2'`, both with `success = true`.

### 9. No verification of table ownership

- **Gap:** The tests do not assert that the 10 baseline tables are owned by the migration/admin role
  rather than by `crypto_app`.
- **Why it matters:** The entire AC3 owner/grantee split relies on tables being owned by a different
  role from `crypto_app`. In the Testcontainers run the owner is the container's default superuser,
  not `checky`, so this is also a portability gap.
- **Suggested test:** Query `pg_tables` (or `information_schema.tables`) for schema `chain` and assert
  `tableowner != 'crypto_app'` for each baseline table. If the test is meant to mirror production,
  consider creating a `checky` superuser in the container and running the migration as `checky`.

---

## What is already well covered

- Real Testcontainers Postgres container and real TCP/JDBC connection as `crypto_app` (not a loopback
  shell bypass).
- Positive INSERT and negative UPDATE/DELETE cases on all three AC3 tables.
- Wrong-password rejection for the role.
- Sibling-service `git status` check (though not a Maven build re-run).

The main blocker is finding #1: until the `checky` database-name mismatch is resolved, the new test
suite cannot run successfully.
