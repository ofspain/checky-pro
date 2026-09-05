# auth · T01 · Phase 11 — Test Review

## Tests under review
`artifacts/10-test-generation.md` (Phase 10 Test Manifest)

## Review summary
The frozen brief explicitly decided that T01 authors **no new test file** because `package.md` §8 has no named test mapped to this task. The Phase 10 manifest is consistent with that decision: it documents manual SQL introspection (AC1–AC2), git diff checks (AC3/L1), and a live `mvn flyway:migrate` run (AC4), plus indirect coverage from existing Spring Boot integration tests that autoconfigure Flyway on startup.

This review accepts the frozen-brief constraint but identifies gaps where automation would make the current manual/implicit verification more robust, repeatable, and failure-diagnosable in CI.

---

## Gaps

### 1. No dedicated automated schema-regression test for V5
**Gap.** AC1 and AC2 rely entirely on manual SQL introspection against a local Docker Compose Postgres. There is no automated test that asserts the `shedlock` table or `idx_lockout_state_locked_until` index exist after Flyway runs.
**Why it matters.** If V5 is accidentally modified, omitted, renamed incorrectly, or checksum-mismatched, the failure signal in CI will be either non-existent (manual step is not rerun) or an obscure context-startup failure in an unrelated integration test. A focused test would fail immediately with a clear message.
**Suggested test.** Add a lightweight integration test (e.g., `V5SchemaMigrationTest`) using Testcontainers Postgres that lets Spring Boot autoconfigure Flyway and then queries `information_schema.tables` / `pg_indexes` to assert: (a) `shedlock` exists with columns `name`, `lock_until`, `locked_at`, `locked_by`; (b) `idx_lockout_state_locked_until` exists; and (c) `auth.flyway_schema_history` has exactly one successful row for version `5`.

### 2. No automated assertion that the new index is partial
**Gap.** The Phase 10 manifest checks for the index name, but not that its definition includes the `WHERE locked_until IS NOT NULL` predicate.
**Why it matters.** A full (non-partial) index on `locked_until` would pass a basic existence check while wasting storage and not matching the design rationale for efficient expired-lock scans. The partial predicate is part of the LOCKED verbatim SQL in `design.md` §4c and should be verified.
**Suggested test.** Extend the proposed `V5SchemaMigrationTest` with an assertion that `pg_indexes.indexdef` for `idx_lockout_state_locked_until` contains `WHERE (locked_until IS NOT NULL)`.

### 3. No CI guard enforcing L1 / V1–V4 immutability
**Gap.** AC3 is verified by `git status`/`git diff`, which is manual and not enforced by the build or CI pipeline.
**Why it matters.** L1 is a LOCKED decision: existing Flyway migrations must never be edited. A future PR could inadvertently modify `V1`–`V4`, leading to production schema drift, Flyway checksum failures, or history conflicts that are expensive to repair.
**Suggested test.** Add a CI check or pre-commit hook that fails if any file matching `services/auth/src/main/resources/db/migration/V[1-4]__*.sql` is modified in a pull request. This is a pipeline/process test, not a Java unit test, and does not conflict with the frozen brief's "no new Java test for T01" decision.

### 4. No test guards the `flyway-maven-plugin` lifecycle binding
**Gap.** The plugin block intentionally has no `<executions>` section so it only runs when explicitly invoked. There is no automated check preventing a future change from binding it to `validate`, `compile`, or `verify`.
**Why it matters.** An accidental lifecycle binding would cause `mvn verify`/`mvn install` to trigger Flyway against `localhost:5432` in CI environments where Postgres is not running, breaking all builds and CI stages.
**Suggested test.** Add a Maven structural assertion or CI script that parses `services/auth/pom.xml` and fails if `flyway-maven-plugin` contains an `<executions>` block. Alternatively, ensure the effective POM (`mvn -pl services/auth help:effective-pom`) contains no default-bound executions and add this check to the CI gate.

### 5. No automated idempotency / re-run verification
**Gap.** The manifest verifies a single `mvn flyway:migrate` run, but does not verify that Flyway remains happy on a subsequent run or that the migration is idempotent given the `IF NOT EXISTS` clauses.
**Why it matters.** Local developers routinely stop/start the database and rerun migrations. If the checksum were to change or `IF NOT EXISTS` were removed, the second run would fail with a Flyway validation error that would not show up on the first run.
**Suggested test.** In the proposed `V5SchemaMigrationTest`, call `Flyway.migrate()` twice against the same Testcontainers datasource and assert both calls succeed and that `flyway_schema_history` still contains exactly one successful V5 row. This verifies the combination of Flyway's checksum tracking and the migration's `IF NOT EXISTS` idempotency.

### 6. Existing indirect coverage cannot currently be exercised
**Gap.** The Phase 10 manifest correctly notes that existing `@SpringBootTest` integration tests run V5 as part of context startup. However, the module currently fails compilation with pre-existing `token`-module symbol errors (`OAuth2TokenType`, `JwtAuthenticationConverter`), so no integration test can run until those are fixed.
**Why it matters.** Until the pre-existing compile error is resolved, the only working verification path is the dedicated `mvn flyway:migrate` command. The "implicit coverage from integration tests" claim cannot be validated in CI today.
**Suggested test.** No T01-specific test is needed for this gap; track the pre-existing `token`-module compilation failure as its own defect (outside T01) so the full test suite can run and exercise V5 automatically.
