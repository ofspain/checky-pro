# notification · T05 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T05: contact projection (ContactProjection, upsert)`

## Commit message

```
notification-service T05: contact projection

Add ContactProjection (maps onto T02's already-migrated contact_projection
table, client-assigned @Id on accountUuid) and ContactProjectionRepository,
with a native INSERT ... ON CONFLICT (account_uuid) DO UPDATE ... WHERE
updated_at <= EXCLUDED.updated_at upsert - a genuine upsert target, unlike
T04's insert-once processed_events ledger. The trailing WHERE guards
against out-of-order delivery: auth.email.requested and auth.user.lifecycle
are two different Kafka topics with no cross-topic ordering guarantee, so
a later-processed but chronologically-older event must never overwrite a
newer projection state. Add ContactProjectionUpdater, the public wrapper
a future consumer.AuthEventConsumer (task 6) will call; @Transactional
default REQUIRED, same join-caller's-transaction rationale as T04's own
IdempotencyGuard. Add V5__notification_app_contact_projection_grant.sql
(INSERT+SELECT+UPDATE, mirroring crypto-service's own V3 outbox grant -
the only other table in this codebase needing UPDATE for a real reason).

Before this task, neither source event actually carried an email address
- verified directly against both JSON contracts and auth-service's own
real payload classes (Phase 0), not assumed from design.md's own O1
recommendation. Resolved via an explicit user decision and a real,
already-committed, already-tested auth-service change (commit 64557d3,
"auth events: add email to auth.email.requested and auth.user.lifecycle")
before this task's own Phase 2 began - Q1 (recipient resolution) is now
genuinely resolved, not deferred.

ContactProjection.email uses columnDefinition = "citext" (required, not
cosmetic - verified against auth-service's own Account.email, which hit
the identical ddl-auto=validate failure without it). No custom JdbcType -
this task only ever reads/writes by account_uuid, never queries by email,
so the parameter-binding half of auth's own fix is deliberately deferred
to whichever future task first needs a query-by-email.

Updates two T01/T02 test files per the frozen brief's own explicit
authorization: T01SkeletonRegressionTest's 12-file list becomes 15;
NotificationBaselineMigrationIntegrationTest moves contact_projection
from ungranted to a dedicated grant-proof test with a real
UPDATE-succeeds check (this table's grant is broader than T04's
insert-only one).

92 tests total (80 T01-T04 unaffected + 12 new: 4 unit + 8 integration),
covering insert, update, out-of-order rejection (including the equal-
timestamp tie-break boundary), display_name-stays-null (including after
a rejected write), citext case-preservation, transaction-join/rollback/
commit, and a permanent static guard for the WHERE clause's own presence.
Two real bugs found and fixed during this task's own pipeline: the core
upsert query was never actually executed until self-review caught the
gap (Phase 7); a direct repository-level test failed with "No
EntityManager with actual transaction available" until wrapped in a
TransactionTemplate (Phase 11).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/preference/{ContactProjection,ContactProjectionRepository,ContactProjectionUpdater}.java`
- `services/notification/src/main/resources/db/migration/V5__notification_app_contact_projection_grant.sql`
- `services/notification/src/test/java/com/themistra/notification/preference/{ContactProjectionUpdaterUnitTest,ContactProjectionUpdaterIntegrationTest}.java`

**Modified**
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (15-file authorized list, renamed method)
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (`contact_projection` grant-proof coverage; Flyway-history expectation widened to `"1".."5"`)

**Process artifacts**
- `.ai/prompts/notification/T05/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).
- Separately, not part of this task's own file list: the auth-service contract change (commit
  `64557d3`) this task's own Phase 1/2 required before it could proceed.

## Summary

Establishes the recipient-contact projection every later email-delivery task (R1, R2, R6) depends
on — genuinely resolving `package.md` §11's own Q1, not deferring it. Required a real, explicitly
user-authorized cross-service change (adding `email` to two auth-service events) before this task's
own design could even be finalized; both the cross-service change and this task's own implementation
are independently tested and verified.

## Testing performed

- `mvn -pl services/notification clean verify` — 92 tests, 0 failures, `BUILD SUCCESS`.
- Real `ContactProjection` entity mapping (including its `citext` column) validated by Hibernate's
  own `ddl-auto=validate` in every Testcontainers run — permanent, not a one-time scratch check.
- Two real mutation tests, both reverted clean (`git status -s` empty afterward): (1) removed the
  `WHERE` out-of-order guard entirely from the native query — confirmed both the behavioral test
  (Phase 10) and, later, the dedicated static-scan test (Phase 11) independently catch it; (2)
  confirmed via direct execution that the static scan alone (no Docker, sub-second) catches the same
  regression the slower Testcontainers test does.
- Two real bugs found and fixed via empirical testing before being trusted: the core upsert query
  had never actually run before Phase 7's own self-review scratch check; a direct
  `repository.upsertEmail` call (bypassing `ContactProjectionUpdater`'s own `@Transactional`) failed
  with a real `InvalidDataAccessApiUsage` until wrapped in a `TransactionTemplate` (Phase 11).
- `git status -s services/auth services/crypto` — empty throughout this task's own commits (the one
  auth-service change was its own separate, already-verified commit, not touched again here).

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 5 ("Contact projection (Q1/O1)").
- **Requirements:** R1, R2, R6 (verification/reset/welcome emails) depend on this projection for a
  real recipient address.
- **LOCKED decision:** L2 (consume-only — every write here is local, never a synchronous call to
  `auth-service` or elsewhere).
- **Open question resolved:** `package.md` §11 Q1 (recipient resolution) — genuinely resolved via
  the auth-service contract change, not merely deferred.

## Known, deliberate gaps (not this task's scope)

- `displayName` is permanently unpopulated — no data source exists anywhere in auth's own domain.
  `V3`'s own `{{displayName}}` template placeholder (T02) stays unusable until a future task either
  adds a display-name field somewhere upstream or removes the placeholder.
- `ContactProjectionRepository` still technically exposes inherited `JpaRepository` mutators
  (`save`/`delete`) that bypass the out-of-order guard — documented with a prominent Javadoc warning,
  not structurally prevented (the frozen brief's own `extends JpaRepository` choice can't be
  narrowed without contradicting an already-frozen decision).
- No real Kafka consumer exists yet to exercise this projection end-to-end — task 6's own scope.

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 6, 6, and 7
  findings respectively — all 19 verified against actual source before disposition; all were real.
- `ContactProjectionUpdater.upsertEmail`'s return type changed from `void` to `boolean` during Phase
  9 (Kimi's own Finding #5, matching self-review's own Finding #2 independently) — mirrors
  `IdempotencyGuard.recordIfNew`'s own shape; no caller exists yet to consume it, closed proactively
  rather than left for a follow-up refactor.
- The two empirically-discovered bugs (Phase 7's unverified upsert query, Phase 11's transaction
  requirement for direct repository calls) are both worth a reviewer's attention as reminders that
  "the entity mapping validated cleanly" is not the same claim as "the query actually works" — this
  task's own pipeline caught both before merge, not after.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T05.**
