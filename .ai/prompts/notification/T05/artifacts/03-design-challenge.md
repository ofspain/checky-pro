<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T05 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T05 — Contact projection (Q1/O1) |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T05 Phase 2 brief.

---

## Finding 1 · `email` CITEXT mapping is unspecified and will likely fail `ddl-auto=validate`

**Severity:** High

**Evidence:**
- V1's `contact_projection.email` is declared `CITEXT`.
- `application.properties` sets `spring.jpa.hibernate.ddl-auto=validate`, so Hibernate will compare the entity against the real column type at startup.
- `auth-service`'s `Account.email` (also `CITEXT`) had to be annotated with both `@Column(name = "email", columnDefinition = "citext")` **and** a custom `@JdbcType(CitextJdbcType.class)` to avoid schema-validation and parameter-binding failures; the class's Javadoc documents that `@JdbcTypeCode(SqlTypes.OTHER)` alone is insufficient and that omitting `columnDefinition` breaks validation.
- No `CitextJdbcType` currently exists in `services/notification`, and the T05 brief does not mention creating or reusing one.

**Recommended brief amendment:**
Specify the `ContactProjection.email` mapping explicitly. At minimum, add `@Column(name = "email", columnDefinition = "citext")` so `ddl-auto=validate` accepts the column. Additionally, either:
- create a notification-local `CitextJdbcType` (mirroring auth) and annotate `email` with `@JdbcType(CitextJdbcType.class)` so any future query-by-email parameter binding works, or
- add a design note that T05 introduces no parameterized queries on `email` and that a future task adding such a query must introduce the custom `JdbcType` then.

---

## Finding 2 · Out-of-order guard uses `<=`, but equal `occurredAt` semantics are undocumented

**Severity:** Medium

**Evidence:**
- The proposed native upsert includes `WHERE contact_projection.updated_at <= EXCLUDED.updated_at`.
- The brief's rationale says "a later-processed but chronologically-older event ... must never overwrite a newer projection state with stale data."
- If two consumed events have the **same** `occurredAt` (e.g., produced in the same millisecond on different topics), `<=` lets the later-processed event overwrite the earlier-processed one, while `<` would keep the first. Neither choice is obviously wrong, but the brief does not say which behavior is intended.

**Recommended brief amendment:**
State the intended tie-breaking rule explicitly. If "first processed wins" is desired, change the guard to `<`; if "at least as recent (including equal) overwrites" is desired, keep `<=` and document that equal timestamps are not considered stale.

---

## Finding 3 · No explicit test requirement that `notification_app` cannot `DELETE` from `contact_projection`

**Severity:** Medium

**Evidence:**
- AC5 states the role must be able to `INSERT/SELECT/UPDATE` and "still cannot `DELETE`."
- The brief says `contact_projection` moves out of the insert-only `UNGRANTED_TABLES` pattern into its own dedicated grant-proof test, but it only lists the three allowed privileges; it does not require the new test to actively attempt and fail a `DELETE`.
- A regression that accidentally added `DELETE` to `V5` would not be caught unless the dedicated test explicitly asserts the denial.

**Recommended brief amendment:**
Add a requirement that the `contact_projection` grant-proof integration test must execute `DELETE` as `notification_app` and assert it is rejected (e.g., `PSQLException` with `permission denied`).

---

## Finding 4 · `T01SkeletonRegressionTest` method name remains T04-specific

**Severity:** Low

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` contains `void noExtraProductionClassesExistBeyondT04sOwnAuthorizedSet()`.
- The brief asks to expand the authorized list from 12 to 15 files but does not mention renaming the test.

**Recommended brief amendment:**
When updating the authorized file list, rename the method to `noExtraProductionClassesExistBeyondT05sOwnAuthorizedSet()` so the test name matches its updated scope.

---

## Finding 5 · `displayName` nullability is not explicitly addressed in the entity mapping

**Severity:** Low

**Evidence:**
- AC4 requires every row created/updated by this task to have `display_name = NULL`.
- V1 defines `display_name VARCHAR(200)` with no `NOT NULL` constraint.
- The brief says `displayName` is mapped but never written; it does not state whether the entity column should be nullable.

**Recommended brief amendment:**
Confirm the entity maps `displayName` as nullable (e.g., `@Column(name = "display_name")` without `nullable = false`, or explicitly `nullable = true`). Also add an integration-test assertion that `repository.findById(...).getDisplayName()` is null after both insert and update paths.

---

## Finding 6 · Future email-delivery tasks may query by `email`, but CITEXT binding is deferred

**Severity:** Low

**Evidence:**
- The brief positions `ContactProjection` as the recipient-address source for R1/R2/R6.
- Those requirements do not necessarily query by `email`, but any future task that does will hit the same `String`→`citext` parameter-binding problem already solved in `auth-service`.
- T05's own scope deliberately avoids queries by `email`, so deferring the custom `JdbcType` is reasonable, but it is unstated.

**Recommended brief amendment:**
Add a note under "Out" or "Open Questions" that any future query-by-email feature must introduce/reuse a `CitextJdbcType`; T05 itself only writes/reads by `account_uuid` and therefore does not need it.

---

## Summary

The T05 brief is internally consistent with L2 and `agents.md`, and the upsert shape correctly mirrors T04's native-query precedent. The most consequential gap is **Finding 1**: without an explicit `citext` mapping strategy, the first `@Entity` in the notification module will likely fail `ddl-auto=validate` at startup. **Finding 2** should be resolved before implementation so the tie-breaking semantics are intentional and testable. Findings 3–6 are smaller precision or test-coverage items that should be folded into the brief or acceptance criteria.
