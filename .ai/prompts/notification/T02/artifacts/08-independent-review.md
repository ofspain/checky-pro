<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T02 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T02 — Schema V1 |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T02 implementation. Findings only.

---

## Finding 1 · The VERBATIM V1 migration cannot run against the shared local Postgres without a schema-scoped `citext` workaround

**Issue:** `V1__notifications_baseline.sql` uses `CITEXT` for `contact_projection.email` and immediately executes `SET search_path TO notifications;`. PostgreSQL resolves unqualified type names only through the current search path. If `citext` is installed in the `public` schema (as `services/auth/V1__auth_baseline_schema.sql` does), the `SET search_path` line makes it invisible, so `CREATE TABLE contact_projection (... email CITEXT ...)` fails with "type 'citext' does not exist". The integration test works around this by pre-creating the `notifications` schema and executing `CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications` before Flyway runs, but that pre-step is **not** part of the real `mvn -pl services/notification flyway:migrate` path.

**Evidence:**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql` line 10: `email CITEXT,` and line 5: `SET search_path TO notifications;`.
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java` lines 68–73: the `@BeforeAll` explicitly installs `citext` into the `notifications` schema and documents that the originally-planned `public`-schema installation was insufficient because of the `SET search_path` line.
- The TIB's AC4 and Constraints section still says the real local `flyway:migrate` only requires auth's migrations to run first for `citext`, which is incorrect given the search-path issue the test itself discovered.

**Recommendation:** Either (a) amend the TIB/AC4 to require `CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications` (or equivalent) as a mandatory pre-step before `mvn -pl services/notification flyway:migrate`, or (b) record the real `mvn flyway:migrate` attempt as genuinely failing and escalate a design.md §4c change request to add `, public` to the `SET search_path` line. Until one of these happens, AC4 is not actually satisfied by the current files.

**Confidence:** High

---

## Finding 2 · `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope` only tests `SELECT`

**Issue:** The test name promises "no access at all" to ungranted tables, but the method body only asserts that `SELECT * FROM notifications.<table>` is denied. It does not verify that `INSERT`, `UPDATE`, or `DELETE` are also denied. A future regression that grants `INSERT` (but not `SELECT`) on an ungranted table would pass this test while violating the least-privilege intent.

**Evidence:**
- `NotificationBaselineMigrationIntegrationTest.java` lines 181–191: the loop only calls `statement.executeQuery("SELECT * FROM notifications." + table)`.
- The TIB's AC2 says "verified both statically and by a real Testcontainers attempt proving UPDATE/DELETE genuinely fail" — but that proof is only provided for `delivery_log`, not for the ungranted tables.

**Recommendation:** Extend the ungranted-tables loop to attempt an `INSERT` on each ungranted table (using minimal valid column values) and assert it is denied. `UPDATE`/`DELETE` are less meaningful without a row, but `INSERT` denial is the critical missing check. Rename the test to `notificationAppCannotSelectFromTablesOutsideAc2Scope` if the broader check is intentionally out of scope.

**Confidence:** Medium

---

## Finding 3 · AC4's real `mvn flyway:migrate` result is not recorded

**Issue:** AC4 requires `mvn -pl services/notification flyway:migrate` to be attempted for real against the local Docker Compose Postgres, with auth's migrations confirmed first. The self-review only records `mvn -pl services/notification -am verify` (19 tests, 0 failures). It does not state whether the real Maven-plugin migration was run, or whether it succeeded/failed.

**Evidence:**
- `artifacts/07-self-review.md` lines 32–33: "`mvn -pl services/notification -am verify` — 19 tests, 0 failures, clean `package`/`repackage`, unchanged from Phase 6's own final record."
- No mention of `mvn -pl services/notification flyway:migrate` anywhere in the self-review.

**Recommendation:** Add a dedicated AC4 verification entry to the self-review (and any Phase 9 resolution notes) that records:
- the exact commands run (compose up, auth `flyway:migrate`, notification `flyway:migrate`);
- whether notification `flyway:migrate` succeeded or failed;
- if it failed, the exact error and the remediation step taken.
If the real command was not run, AC4 is unverified regardless of how green the test suite is.

**Confidence:** High

---

## Finding 4 · Test helpers concatenate SQL strings without parameterization

**Issue:** The integration test builds `INSERT`, `UPDATE`, `DELETE`, and `SELECT` statements by concatenating `sourceEventKey` directly into the SQL text. Although `sourceEventKey` is derived from the test-controlled `"it-" + table` value, this is still SQL injection in test code and creates a brittle pattern that future maintainers may copy for user-controlled inputs.

**Evidence:**
- `NotificationBaselineMigrationIntegrationTest.java` lines 262–294: `insertStatementFor`, `cleanUpAsAdmin`, and the `assertInsertAndSelectSucceedUpdateAndDeleteAreDenied` method all concatenate `sourceEventKey` into SQL strings.
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` uses the same pattern, so this is inherited precedent, but it remains a security-antipattern.

**Recommendation:** Replace string concatenation with `PreparedStatement` for the `sourceEventKey` value in the grant-test helper. This is a test-only change and does not expand task scope.

**Confidence:** Low

---

## Finding 5 · V3 seed conventions (EMAIL subject vs. IN_APP null subject) are not regression-guarded

**Issue:** `V3__seed_launch_templates.sql` seeds all EMAIL rows with non-null `subject` values and all IN_APP rows with `NULL` subjects. This is a reasonable convention, but the integration test only asserts row count, version, and `(name, channel)` pairs. A regression that accidentally swaps a subject into an IN_APP row (or nulls an EMAIL subject) would not fail the test.

**Evidence:**
- `NotificationBaselineMigrationIntegrationTest.java` lines 227–257: `launchTemplatesAreSeededWithVersionOne()` queries `name`, `channel`, and `version` only.
- `V3__seed_launch_templates.sql` lines 16–57: EMAIL rows have concrete `subject` strings; IN_APP rows use `NULL`.

**Recommendation:** Add two narrow assertions to `launchTemplatesAreSeededWithVersionOne()`:
- every `EMAIL` row has a non-null `subject`;
- every `IN_APP` row has a null `subject`.

This encodes the convention without asserting exact subject strings, keeping the test stable if copy is revised.

**Confidence:** Low

---

## Finding 6 · `extractFirstSqlFence` is brittle against future design.md edits

**Issue:** The byte-for-byte V1 regression test extracts the **first** ```` ```sql ```` block from `design.md`. Today `design.md` contains exactly one SQL fence, so this works. If a future edit adds another SQL example (e.g., a seed-template example or a grant snippet) before the V1 baseline block, the test will compare the migration file against the wrong fence and fail with a misleading "byte-for-byte" error.

**Evidence:**
- `NotificationBaselineMigrationIntegrationTest.java` lines 117–134: `extractFirstSqlFence` returns the first fenced SQL block, with no check that the block's first line matches the V1 migration's header comment.

**Recommendation:** Make the extraction deterministic by also asserting the returned block starts with `-- Notification Service baseline (notifications schema).` (or by scanning for the fence whose first non-empty line matches a known marker). This preserves the strong byte-for-byte guard while making the fence selection robust.

**Confidence:** Low

---

## Summary

The implementation is internally consistent and the Testcontainers suite passes, but **Finding 1** is a hard blocker for AC4: the VERBATIM V1 SQL cannot run against the stated local-dev environment without the test-only citext workaround. Findings 2 and 3 are gaps in how AC2/AC4 are actually demonstrated. Findings 4–6 are precision/quality improvements in the integration test. None of these findings propose changing the VERBATIM V1 text; they identify that the surrounding instructions and verification must account for the search-path behavior the implementation already discovered.
