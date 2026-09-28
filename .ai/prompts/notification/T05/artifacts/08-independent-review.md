<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T05 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T05 — Contact projection (Q1/O1) |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T05 implementation. Findings only.

---

## Finding 1 · Required T05 tests are entirely missing

**Issue:** AC6 requires both a unit test and a Testcontainers integration test, including a dedicated out-of-order proof. The codebase currently has no `preference/` test directory and no test that exercises `ContactProjectionUpdater.upsertEmail` or `ContactProjectionRepository.upsertEmail` against either mocks or a real database.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/` has no `preference/` subdirectory.
- `grep` for `ContactProjectionUpdater`, `ContactProjectionRepository`, or `upsertEmail` in `src/test` returns only references in `T01SkeletonRegressionTest.java` (production-file list) and `NotificationBaselineMigrationIntegrationTest.java` (grant test for `contact_projection`).
- The self-review confirms the core upsert SQL was only verified via a temporary, deleted scratch test.

**Recommendation:** Add the missing tests before considering T05 complete:
- `ContactProjectionUpdaterUnitTest` (mocked repository): assert `upsertEmail` delegates to `repository.upsertEmail` with the exact `accountUuid`, `email`, and `occurredAt` parameters.
- `ContactProjectionUpdaterIntegrationTest` (Testcontainers + `@SpringBootTest`):
  - insert on first call, then read back and assert `email` and `updatedAt` match the event values;
  - update on a later-timestamped call for the same `accountUuid`;
  - **out-of-order proof:** call with an older `occurredAt` after a newer one and assert the projection is not overwritten;
  - transaction join/rollback: call within a test-managed transaction, roll back, assert no row was written; call within a transaction, commit, assert the row persists.

**Confidence:** High

---

## Finding 2 · `JpaRepository` inheritance exposes `save()` and other mutators that bypass the out-of-order guard

**Issue:** `ContactProjectionRepository` extends `JpaRepository<ContactProjection, UUID>`, so it inherits `save`, `saveAll`, and `delete`. The brief's intended write path is the native `upsertEmail`, which enforces the out-of-order guard via `WHERE updated_at <= EXCLUDED.updated_at`. A future developer in the same `preference` package could call `repository.save(...)` and overwrite `updated_at` without that guard, violating AC3 silently.

**Evidence:**
- `ContactProjectionRepository.java` line 11: `interface ContactProjectionRepository extends JpaRepository<ContactProjection, UUID>`.
- The repository is package-private, so only code in `preference/` can see the type, but `ContactProjectionUpdater` lives there too and could be refactored (or a new class added) that uses the inherited methods.
- `ContactProjection.java` Javadoc explicitly says the entity is "read-only in practice" and that the only write path is `upsertEmail`, yet the repository interface makes a conflicting write path available.

**Recommendation:** Either:
- change the repository to extend `Repository<ContactProjection, UUID>` and explicitly expose only the read methods needed plus `upsertEmail` (this removes `save`/`delete` from the API), or
- add a prominent warning in the repository's Javadoc that the inherited `save`/`delete` methods must not be used for projection updates; all writes must go through `upsertEmail`.

The brief explicitly says `extends JpaRepository`, so the second option may be the only one that respects the frozen brief; still, the risk should be documented.

**Confidence:** Medium

---

## Finding 3 · No test proves `display_name` stays null (AC4)

**Issue:** AC4 requires every row created or updated by this task to have `display_name = NULL`. The upsert SQL correctly omits `display_name`, but there is no automated test that reads a row back and asserts `getDisplayName()` is null. A future refactor that accidentally added `display_name` to the `INSERT`/`UPDATE` list would not fail any existing test.

**Evidence:**
- `ContactProjectionRepository.java` lines 31–34: the upsert inserts/updates only `account_uuid`, `email`, and `updated_at`.
- No test in `src/test` reads `ContactProjection` or queries the `display_name` column.

**Recommendation:** Add an assertion in the integration test: after calling `upsertEmail`, fetch the projection via `repository.findById(accountUuid)` and assert `getDisplayName()` is null. Also assert directly via JDBC that the `display_name` column is null for the inserted/updated row.

**Confidence:** Medium

---

## Finding 4 · No test verifies `@Transactional` propagation behavior

**Issue:** The brief's Constraints section explicitly requires `ContactProjectionUpdater.upsertEmail` to use default `REQUIRED` propagation so a future caller can combine it with `IdempotencyGuard.recordIfNew` in one transaction. The implementation has `@Transactional` on the method (default is REQUIRED), but no test verifies the behavior or the annotation.

**Evidence:**
- `ContactProjectionUpdater.java` line 34: `@Transactional` on `upsertEmail`.
- No test in `src/test` exercises transaction join/rollback for this service or reflects on the method to assert the propagation value.

**Recommendation:**
- Add an integration test that calls `upsertEmail` inside a test-managed transaction, rolls back, and asserts no row was written.
- Add a fast unit test that uses reflection to inspect `ContactProjectionUpdater.upsertEmail` and assert it is annotated with `@Transactional` and does not specify `REQUIRES_NEW` or `NOT_SUPPORTED`.

**Confidence:** Medium

---

## Finding 5 · `ContactProjectionUpdater.upsertEmail` discards the affected-row count

**Issue:** `ContactProjectionRepository.upsertEmail` returns the affected-row count (`1` accepted, `0` rejected as stale), but `ContactProjectionUpdater.upsertEmail` discards it. A future caller has no signal for whether the projection was refreshed or the write was suppressed by the out-of-order guard.

**Evidence:**
- `ContactProjectionUpdater.java` line 35–37: `public void upsertEmail(...)` calls `repository.upsertEmail(...)` and does not capture the return value.
- `ContactProjectionRepository.java` lines 27–28: Javadoc documents the return value semantics.

**Recommendation:** Consider changing the return type to `boolean` (accepted = true, rejected = false), mirroring `IdempotencyGuard.recordIfNew`. This is not required for T05's own scope if no caller uses the result, but it preserves observability for task 6+ logging/metrics without requiring a follow-up refactor. If left as `void`, document in the Javadoc that the suppressed-stale-write signal is intentionally discarded.

**Confidence:** Low

---

## Finding 6 · `V5` migration comment implies `display_name` is revised

**Issue:** The migration comment says "contact_projection is a genuine upsert target - email/display_name/updated_at are revised over an account's lifetime", but this task never writes `display_name`. The wording is slightly misleading and could cause a future reader to believe T05 populates `display_name`.

**Evidence:**
- `V5__notification_app_contact_projection_grant.sql` lines 2–3.
- `ContactProjection.java` Javadoc lines 31–34 explicitly states `displayName` is always null for rows this task creates/updates.

**Recommendation:** Update the comment to "email and updated_at are revised over an account's lifetime; display_name is mapped but never written by this task." This keeps the comment aligned with the implementation and AC4.

**Confidence:** Low

---

## Summary

The T05 production code is well-aligned with the brief: the native `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE` correctly implements the out-of-order guard, the `citext` mapping addresses the `ddl-auto=validate` risk, the `V5` grant is least-privilege, and the regression-test updates for T01/T02 are correct. The dominant issue is the absence of the required T05-specific test suite (Finding 1). Findings 2–4 are design/test-coverage risks around the `JpaRepository` API surface, AC4 verification, and transaction propagation. Findings 5–6 are minor quality items.
