<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T02 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T02 — Schema V1 |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T02 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · AC4 (real `mvn flyway:migrate`) has no automated regression guard

**Why it matters:** AC4 requires `mvn -pl services/notification flyway:migrate` to succeed against the real local Docker Compose Postgres. The Testcontainers integration test verifies the migrations against an isolated container with a `citext` pre-step, but it cannot verify the actual Maven-plugin invocation, the `pom.xml` flyway-maven-plugin configuration, or the documented local-dev command sequence. A future change to the plugin configuration (e.g., wrong schema, wrong credentials, missing `<executions>` guard) could break AC4 while all Testcontainers tests still pass.

**Suggested test:** Add a narrow unit test (plain JUnit, no container) that reads `services/notification/pom.xml` and asserts the `flyway-maven-plugin` block has the expected `<url>`, `<user>`, `<password>`, `<schemas>notifications</schemas>`, and no `<executions>` binding. This is weaker than a real migration run but at least guards the Maven-plugin path against silent drift. A stronger option is a CI step or a shell-based smoke test that runs the real command and asserts success.

---

## Gap 2 · V2 idempotency test does not actually re-execute the role-creation SQL

**Why it matters:** `v2RoleCreationGuardIsIdempotentUnderARealReRun()` calls `Flyway.configure(...).migrate()` a second time. Flyway skips already-applied migrations, so V2's `CREATE ROLE ... IF NOT EXISTS` and `GRANT` statements are never re-executed. The test therefore does not prove that V2's own SQL is idempotent — only that Flyway's bookkeeping is.

**Suggested test:** Add a test that connects as admin and runs V2's statements directly twice (or drops and recreates the role), asserting no error and that privileges remain correct. Alternatively, add a helper that manually executes V2's `DO $$ ... $$;` block twice against the container.

---

## Gap 3 · `UPDATE`/`DELETE` on ungranted tables are not tested

**Why it matters:** `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope()` now correctly tests both `SELECT` and `INSERT` denial on the seven ungranted tables. A regression that granted `UPDATE` or `DELETE` (without `SELECT`/`INSERT`) on one of those tables would still pass this test, violating the literal "no access at all" intent.

**Suggested test:** Extend the ungranted-tables loop to attempt a scoped `UPDATE` and `DELETE` on each table and assert `permission denied`. Because these are denied before any row is matched, the statements can target a non-existent key safely:

```java
assertThatThrownBy(() -> statement.execute(
    "UPDATE notifications." + table + " SET body = 'x' WHERE name = 'no-such-row'"))
    .isInstanceOf(SQLException.class)
    .hasMessageContaining("permission denied");
assertThatThrownBy(() -> statement.execute(
    "DELETE FROM notifications." + table + " WHERE name = 'no-such-row'"))
    .isInstanceOf(SQLException.class)
    .hasMessageContaining("permission denied");
```

---

## Gap 4 · V2 secrets-discipline invariants are not statically asserted

**Why it matters:** AC2 and L10 require that no password or environment-specific database name be committed in V2. The migration file comment states this, but there is no test that fails if someone later edits V2 to add `WITH PASSWORD '...'` or hardcode `ON DATABASE checky`.

**Suggested test:** Add a plain JUnit test (similar in style to `T01SkeletonRegressionTest`) that reads `V2__notification_app_role_and_grants.sql` and asserts:
- it does not contain the string `PASSWORD` (except in a comment, if necessary);
- it does not contain `ON DATABASE checky` or any other literal database name;
- it does contain `current_database()` in the `GRANT CONNECT` statement.

This mirrors the pom-file regression-guard style already established in T01.

---

## Gap 5 · The `citext` pre-step workaround is not regression-guarded in code

**Why it matters:** The integration test's `@BeforeAll` installs `citext` directly into the `notifications` schema. This workaround is critical for V1 to run, but the only place it is documented is in a Javadoc comment and (per Phase 10) the service README. If a future refactor moves or removes that pre-step, the test will fail with a confusing error, but the reason won't be obvious without reading history.

**Suggested test:** Add an assertion immediately after the pre-step that queries `pg_extension` and confirms `citext` is installed in `notifications`, with a descriptive failure message explaining that V1's `SET search_path TO notifications;` requires the extension to live in that schema.

---

## Summary

The T02 test suite now strongly covers AC1 (byte-for-byte V1), AC2 (grants on `delivery_log`, denial on ungranted tables, DDL denial, ownership split), AC3 (seed template count/names/versions/subject convention), and AC5 (Flyway history). The remaining gaps are around AC4 (no automated guard for the real Maven-plugin migration path) and precision of the privilege/idempotency assertions. None of these gaps indicate that the current migrations are wrong — they are test-coverage refinements that would make future regressions fail faster and with clearer messages.
