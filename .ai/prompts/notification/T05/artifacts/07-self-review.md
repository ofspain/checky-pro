# notification · T05 · Phase 7 — Self-Review

## Files reviewed

- `preference/{ContactProjection,ContactProjectionRepository,ContactProjectionUpdater}.java`
- `db/migration/V5__notification_app_contact_projection_grant.sql`
- `T01SkeletonRegressionTest.java`, `NotificationBaselineMigrationIntegrationTest.java` (both diffs)

## Findings

### Finding 1 — the native upsert query itself was never actually executed in Phase 6

**Severity:** High (process gap; **resolved during this review, no defect found**)

**Evidence:** Phase 6's own "Verification performed" section only established that Hibernate's
`ddl-auto=validate` accepts `ContactProjection`'s mapping (proven by every Testcontainers
`@SpringBootTest` booting clean) — it never actually invoked
`ContactProjectionUpdater.upsertEmail`/`ContactProjectionRepository.upsertEmail` anywhere. Spring
Data JPA does not validate a `nativeQuery = true` `@Query`'s own SQL syntax at repository-proxy
creation time — only at first real invocation. This meant the task's own core deliverable (the
upsert, including its `ON CONFLICT ... DO UPDATE ... WHERE` guard) had literally never run against a
real Postgres instance before this review.

**Action taken during this review (not a Phase 9 deferral — informational, no code changed):** wrote
a temporary, uncommitted scratch test (`ScratchContactProjectionVerificationTest`, deleted before
this artifact was written, same discipline as T04's own Phase 6 scratch verification) exercising the
full sequence: insert on first call, update on a later-timestamped call, and — critically — no
overwrite on an older-timestamped call. **All three passed exactly as designed.** The
`WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at` clause's own syntax
(fully schema-qualified table reference inside an `ON CONFLICT DO UPDATE ... WHERE`) is valid
Postgres and behaves correctly.

**Recommendation:** Phase 10 should still make this permanent as a real, committed test (already
planned) — this review's own scratch check closes the immediate risk but is not a substitute for
the actual required test suite.

### Finding 2 — `ContactProjectionUpdater.upsertEmail`'s `void` return discards the accepted/rejected signal

**Severity:** Low

**Evidence:** `ContactProjectionUpdater.java` — `upsertEmail` calls
`repository.upsertEmail(...)` (which returns `int`, the affected-row count: `1` accepted, `0`
rejected as stale) but discards the result entirely. A future caller (task 6's own
`AuthEventConsumer`) has no way to know whether a given call actually refreshed the projection or
was silently rejected by the out-of-order guard — relevant for logging/observability, not for
correctness (nothing in this task's own scope needs the caller to branch on this).

**Recommendation:** Consider changing the return type to `boolean` (mirroring `IdempotencyGuard.recordIfNew`'s
own shape) if/when a future task's own logging needs visibility into stale-write rejections. Not
required for T05's own scope — no action taken.

## Confirmed non-issue — no concurrent-call test needed (unlike T04)

T04's own `IdempotencyGuard` needed a dedicated concurrent-call test because its first design
(check-then-insert) had a real TOCTOU race. `ContactProjectionRepository.upsertEmail`'s
`ON CONFLICT ... DO UPDATE` is Postgres's own atomic upsert primitive — specifically designed to
resolve concurrent-insert races for the same key at the database level, not something this task's
own code needs to defend against separately. Deliberately not planning a T04-style concurrent test
for Phase 10; the out-of-order (stale-write) test is the real guard this table needs, not a
race-condition proof.

## Verification performed

- `mvn -pl services/notification clean verify` — 80 tests, 0 failures, unchanged from Phase 6's own
  final record.
- Scratch verification (described in Finding 1, deleted before this commit): confirmed the upsert's
  full behavior (insert, update, out-of-order rejection) against a real Postgres instance.
