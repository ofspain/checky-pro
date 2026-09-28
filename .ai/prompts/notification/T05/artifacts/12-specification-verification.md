# notification · T05 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `ContactProjection` maps exactly onto the 4 existing columns, client-assigned `@Id`, explicit `citext` mapping | Yes | `ContactProjection.java:37` (`@Table(name = "contact_projection", schema = "notifications")`), `email` column `columnDefinition = "citext"` | Validated by Hibernate's own `ddl-auto=validate` in every Testcontainers run (permanent, via `ContactProjectionUpdaterIntegrationTest`); `firstCallCreatesARowWithNullDisplayName`/`citextPreservesOriginalCaseOnReadBack` assert field-level correctness | No | No |
| **AC2** — upsert creates on first call, updates on a later call | Yes | `ContactProjectionRepository.java` (native `INSERT ... ON CONFLICT ... DO UPDATE`) | `ContactProjectionUpdaterUnitTest` (2 tests), `ContactProjectionUpdaterIntegrationTest.laterCallUpdatesEmailAndUpdatedAt`, `repositoryUpsertEmailReturnsAffectedRowCountDirectly` | No | No |
| **AC3** — out-of-order guard rejects a stale write; transaction-join/rollback proven | Yes | `WHERE notifications.contact_projection.updated_at <= EXCLUDED.updated_at`; `ContactProjectionUpdater.java` `@Transactional` default `REQUIRED` | `olderCallDoesNotOverwriteANewerProjection` (entity + JDBC-direct proof), `equalOccurredAtTiesResolveToTheLaterProcessedCallWinning`, `upsertEmailJoinsAnExternallyOpenedTransactionAndRollsBackWithIt` + `...CommitsWhenAnExternallyOpenedTransactionCommits`, `upsertEmailUsesDefaultRequiredPropagation` (reflection) | No | No |
| **AC4** — `display_name` always null for rows this task writes | Yes | `ContactProjectionRepository`'s upsert never references `display_name` | `firstCallCreatesARowWithNullDisplayName`, `laterCallUpdatesEmailAndUpdatedAt`, and (Phase 11 Gap #6) `olderCallDoesNotOverwriteANewerProjection` all assert `getDisplayName()` is null | No | No |
| **AC5** — `notification_app` can `INSERT`/`SELECT`/`UPDATE`; still cannot `DELETE` | Yes | `V5__notification_app_contact_projection_grant.sql` (whole file) | `NotificationBaselineMigrationIntegrationTest.notificationAppCanInsertSelectAndUpdateButNotDeleteOnContactProjection` | No | No |
| **AC6** — both a unit test and a Testcontainers integration test pass | Yes | — | `ContactProjectionUpdaterUnitTest` (4 tests), `ContactProjectionUpdaterIntegrationTest` (9 tests) | No | No |
| L2 (consume-only, no synchronous cross-service call) | Yes | `ContactProjectionUpdater`/`ContactProjectionRepository` never call another service; both source events (`auth.email.requested`, `auth.user.lifecycle`) now genuinely carry `email` (commit `64557d3`) | Implicit — no test needed to prove an absence of network calls in code this small and directly inspectable | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T05's own literal scope (`tasks.md` task 5:
consume `auth.user.lifecycle`/`auth.email.requested` to populate `contact_projection`, blocker for
email delivery; confirm approach against Q1). All 4 production files delivered
(`ContactProjection`, `ContactProjectionRepository`, `ContactProjectionUpdater`, `V5`) plus 2 test
files (92 tests total). `T01SkeletonRegressionTest.java` and
`NotificationBaselineMigrationIntegrationTest.java` were modified exactly as the frozen brief
authorized. The one file outside `services/notification`'s own boundary — auth-service's event
payloads — was a separate, explicitly user-authorized change (commit `64557d3`), not silently folded
into this task's own scope; Q1 is now resolved for real, not deferred.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 all hold, each with direct
evidence and automated coverage. Two of the task's own most novel properties (the out-of-order guard,
the direct-repository transaction requirement) were each independently mutation-tested or
empirically discovered to need a fix before being trusted — not merely asserted correct by
inspection.

**(3) Does it violate any LOCKED decision?** No. L2 holds throughout — every write is local, sourced
from arguments a caller already has (themselves derived from a consumed event), never a live call to
`auth-service` or anywhere else.

**(4) Remaining risks?**
- `displayName` remains permanently unpopulated by any code in this codebase — `V3`'s own
  `{{displayName}}` template placeholder (T02) stays unusable until some future task either adds a
  display-name field to an auth event (mirroring this task's own `email` precedent) or removes the
  placeholder. Not this task's own risk to resolve, but worth surfacing for whoever picks up template
  rendering (task 9).
- `ContactProjectionRepository` still technically exposes inherited `JpaRepository` mutators
  (`save`/`delete`) that bypass the out-of-order guard — documented with a prominent Javadoc warning
  (Phase 9), not structurally prevented, since narrowing the interface would contradict the frozen
  brief's own explicit `extends JpaRepository` choice.
- No real consumer exists yet to prove this projection actually gets populated from a real Kafka
  message — by design, correctly deferred to task 6 (`AuthEventConsumer`).

## `package.md` §9 whole-service checklist — items relevant to T05

No checklist item names contact-projection population directly; every item requiring feature code
(idempotent consumers, delivery log, preference resolution, in-app stream, contract conformance)
still requires task 6+ — correctly out of scope here.

## Cross-task regression check

Full `services/notification` suite: `T01SkeletonRegressionTest` (9 tests, its own 15-file authorized
list) and `NotificationBaselineMigrationIntegrationTest` (15 tests, `contact_projection` now in
grant-proof coverage) both still pass alongside T03's 45, T04's 13, and T05's own 8 — confirms T05's
changes didn't regress anything the prior four tasks established.

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20,
matching the established precedent. Not touched here.

---

**PASS** — all 6 acceptance criteria satisfied with direct evidence and automated coverage (92
tests, two properties independently mutation-tested or empirically corrected), no LOCKED decision
violated, task boundary held throughout, and Q1 (recipient resolution) is now genuinely resolved —
not merely deferred — via a real, verified, cross-service contract change.
