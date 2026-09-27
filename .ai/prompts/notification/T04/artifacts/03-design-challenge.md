<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T04 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T04 — Idempotency ledger |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 Task Implementation Brief. Findings only.

---

## Finding 1 · `recordIfNew` is not safe under concurrent duplicate calls

**Severity:** High

**Evidence:** The TIB specifies `IdempotencyGuard.recordIfNew` as:

> Checks `repository.existsById(eventKey)`; if true, returns `false`; if false, saves a new `ProcessedEvent` ... and returns `true`.

With two concurrent transactions, both can read `existsById=false` before either commits. Both then attempt `repository.save(...)`. The `processed_events.event_key PRIMARY KEY` constraint guarantees exactly one commit succeeds, but the other transaction will fail with a `DataIntegrityViolationException` (or equivalent `DuplicateKeyException`), not return `false`. The TIB's integration-test goal — "concurrent/duplicate `recordIfNew` calls resolve to exactly one `true`" — is therefore not achievable by the described implementation.

**Recommended brief amendment:** Add explicit concurrency handling to `recordIfNew`. Two options:
- (a) Catch `DataIntegrityViolationException` around `repository.save(...)` and return `false`; or
- (b) Use a native `INSERT INTO notifications.processed_events ... ON CONFLICT (event_key) DO NOTHING` query and return `true` iff a row was inserted.

Also update the integration-test requirement to verify the chosen behavior under actual concurrent calls (e.g., two threads, same key, one `true`, all others `false`, no unchecked exceptions).

---

## Finding 2 · Open Question references are copy-pasted from the wrong task

**Severity:** Medium

**Evidence:** The TIB's Scope section says T04 resolves "Phase 1's Open Question #1", "#2", and "#3". Per `design.md` §11, those questions are:
- Q1: Recipient resolution (contact projection)
- Q2: Email transport vendor
- Q3: In-app transport (SSE vs WebSocket)

None of these are resolved by T04 (idempotency ledger). T04 implements L1 and R7/R8, not Q1/Q2/Q3. The references appear to be copy-pasted from T03 (config/resource server) or another task.

**Recommended brief amendment:** Remove the Q1/Q2/Q3 references from T04 entirely. The Open Questions section should state "No blockers — T04 does not resolve any of `design.md` §11's open questions; it implements the idempotency ledger (L1, R7/R8) and leaves Q1–Q7 for their respective tasks."

---

## Finding 3 · `T01SkeletonRegressionTest` must be updated but is listed as "do not modify"

**Severity:** Medium-High

**Evidence:** T04 adds four new production Java files (`ProcessedEvent`, `ProcessedEventRepository`, `IdempotencyGuard`, `ClockConfig`). The T03-updated `T01SkeletonRegressionTest.noExtraProductionClassesExistBeyondT03sOwnAuthorizedSet()` asserts an exact list of eight production files under `src/main/java/com/themistra/notification`. After T04, that list will be twelve files. If `T01SkeletonRegressionTest.java` is truly not modified, the T01 regression test will fail in T04's own build.

**Recommended brief amendment:** Move `T01SkeletonRegressionTest.java` from "Files NOT to Modify" to "Files to Modify" with a note that the authorized production-class list in `noExtraProductionClassesExistBeyondT03sOwnAuthorizedSet()` must be updated to include the four new T04 files (mirroring how T03 itself updated the same test). Keep the disclosure requirement: the diff/self-review must explicitly call out this update as a justified T04-driven change, not hide it.

---

## Finding 4 · `NotificationBaselineMigrationIntegrationTest` stale `UNGRANTED_TABLES` is flagged but not resolved

**Severity:** Medium

**Evidence:** The TIB correctly notes that `NotificationBaselineMigrationIntegrationTest.UNGRANTED_TABLES` will become stale once `V4` grants access to `processed_events`. It says Phase 6 must "flag this precisely ... rather than silently leave a now-incorrect assertion in place." However, it does not explicitly authorize modifying the T02 test to remove `processed_events` from `UNGRANTED_TABLES`, nor does it require adding a new assertion that `processed_events` is now accessible. Leaving the stale assertion in place with only a "flag" creates a failing or misleading test.

**Recommended brief amendment:** Explicitly authorize (and require) updating `NotificationBaselineMigrationIntegrationTest` in T04: remove `processed_events` from `UNGRANTED_TABLES` and add it to a new verification that `notification_app` can `INSERT`/`SELECT` on it (while still denying `UPDATE`/`DELETE`). Treat this as a disclosed, justified amendment to T02's test, not a silent rewrite.

---

## Finding 5 · `ProcessedEvent` table/schema mapping is not specified

**Severity:** Low

**Evidence:** The TIB says `ProcessedEvent` "maps exactly onto `processed_events`'s existing 3 columns" but does not state whether the entity should use `@Table(name = "processed_events", schema = "notifications")` or rely on the datasource's `search_path`. The V1 table lives in the `notifications` schema. If the entity omits `schema = "notifications"`, Hibernate will resolve the table via the connection's search path, which is set to `notifications, public` in `application.properties`. That works, but it is implicit and fragile.

**Recommended brief amendment:** Explicitly require `@Table(name = "processed_events", schema = "notifications")` on the entity, matching the explicit schema naming already used in the Flyway migrations.

---

## Finding 6 · `@Transactional` propagation should be stated explicitly

**Severity:** Low

**Evidence:** The TIB says `IdempotencyGuard.recordIfNew` is "`@Transactional` (default `REQUIRED` propagation — deliberately not `REQUIRES_NEW`/`NOT_SUPPORTED`)". Spring's default is indeed `REQUIRED`, but stating it only in prose leaves room for an implementer to omit the annotation or add an explicit propagation that diverges.

**Recommended brief amendment:** Require the method signature to be annotated exactly as `@Transactional` with no propagation attribute (relying on the default) OR `@Transactional(propagation = Propagation.REQUIRED)`, and add a regression test that reflects on the method and asserts it does not use `REQUIRES_NEW` or `NOT_SUPPORTED`.

---

## Finding 7 · `ProcessedEvent` getter/accessor shape is unspecified

**Severity:** Low

**Evidence:** The TIB says `ProcessedEvent` should mirror `OutboxEvent`'s shape (protected no-arg constructor, static factory, no public setters). It does not state whether the entity should expose getters, and if so, for which fields. `OutboxEvent` exposes getters for all fields. Future tasks will need to read `eventKey`/`eventType`/`processedAt` from a persisted `ProcessedEvent` (e.g., for logging or debugging).

**Recommended brief amendment:** Add a one-line requirement: "Expose read-only getters for `eventKey`, `eventType`, and `processedAt`; no setter methods." This prevents an implementer from making the fields completely inaccessible or adding unwanted mutators.

---

## Finding 8 · `V4` grant comment should explain the append-only rationale

**Severity:** Low

**Evidence:** The TIB says `V4__notification_app_processed_events_grant.sql` grants `INSERT, SELECT` only, mirroring crypto's incremental-grant style. Crypto's `V3__crypto_app_outbox_grant.sql` includes a detailed comment explaining why `UPDATE` is needed for outbox. Notification's V4 should similarly document why `UPDATE`/`DELETE` are deliberately absent — a future reader might assume the grant is incomplete.

**Recommended brief amendment:** Require the migration file to include a comment such as:

```sql
-- T04: notification_app needs to record and read processed event keys for idempotency.
-- processed_events is append-only (L1, L3 philosophy): a key is never revised or deleted,
-- so only INSERT + SELECT are granted.
```

---

## Summary

The T04 brief is functionally sound in its entity/repository/service design and correctly honors L1's transaction-join requirement. The highest-severity issue is the unchecked concurrent-insert race in `recordIfNew` (Finding 1), which would manifest as runtime exceptions rather than clean deduplication. Findings 2, 3, and 4 are clarity/consistency issues that would cause build failures or misleading tests if left as written. Findings 5–8 are lower-severity precision items that reduce implementer guesswork.
