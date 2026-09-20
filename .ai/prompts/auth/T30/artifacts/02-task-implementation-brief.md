<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T30 · Phase 2 — Task Implementation Brief

## Task

Scheduled cleanup job — add a ShedLock-annotated job that hard-deletes expired verification
tokens, old revoked refresh-token families (and, via cascade, their archive rows), and stale
ShedLock rows, on a nightly schedule.

## Purpose

Verification tokens, revoked refresh-token families/archives, and ShedLock's own bookkeeping rows
all accumulate indefinitely today with no cleanup path — this task adds the one job needed to keep
those tables bounded, guarded so that running on ≥2 EKS replicas (per `agents.md`) never executes
the cleanup concurrently.

## Scope

**In:** one new `@Component` scheduled job class; the ShedLock library as a new dependency;
`@EnableSchedulerLock` wiring on the application class; new delete-by-age repository methods on
`VerificationTokenRepository` and `RefreshTokenFamilyRepository`; the three
`themistra.auth.cleanup.*` config properties (already specified in `design.md`, not invented here).

**Out:** any new Flyway migration (V5 already has everything needed, per L1); any change to how
verification tokens or refresh-token families are created/consumed elsewhere; T31 (rate limiting),
T33/T34 (contracts).

## Business Rules

- **R40.** A scheduled cleanup job hard-deletes expired verification tokens, old revoked
  families/archives, and stale ShedLock rows.

## Locked Decisions

- **L1.** No new migration — `V5__lockout_cleanup_and_shedlock.sql` already contains the `shedlock`
  table this task needs; confirmed satisfied, not touched further.

## Resolutions to Phase 1's Open Questions

- **OQ1 (archive pruning scope).** Archive-row cleanup is **only** the side effect of the existing
  `ON DELETE CASCADE` on `refresh_token_archive.family_id` when an old *revoked* family is deleted.
  The job does **not** independently prune archive rows for families that are still active
  (non-revoked) — doing so would delete the exact evidence `checkAndRegisterPresentation` (D-003)
  needs to detect a superseded-token replay for a family that is still alive, actively weakening
  reuse detection for no cleanup benefit (an active family's archive rows are small and bounded by
  its own rotation count, not an unbounded accumulation problem).
- **OQ2 (ShedLock row retention).** Reuses `themistra.auth.cleanup.token-retention-days` (7 days) —
  no dedicated property exists in the spec, and ShedLock rows are low-cardinality (one row per
  distinct lock name ever used, not one per job execution), so the exact threshold has negligible
  practical impact; reusing an existing property avoids inventing a fourth config key for a
  low-stakes value.
- **OQ3 (sub-action failure isolation).** The three cleanup actions (tokens, families, ShedLock
  rows) run as three independent, individually try/catch-guarded steps inside one
  `@SchedulerLock`-guarded method — one failing is logged and does not prevent the other two,
  mirroring T28's D3 best-effort-per-sub-action precedent, applied here to three unrelated delete
  operations rather than a loop over families.
- **OQ4 (ShedLock version).** `net.javacrumbs.shedlock:shedlock-spring` +
  `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`, version **7.7.0** — confirmed present
  in the local Maven cache (fully resolved, not just a stub) and confirmed via its own POM that it
  declares Spring as an externally-supplied dependency (`${spring.version}`, not self-pinned), so it
  imposes no version conflict with this service's existing Spring Boot 3.5.4 / Spring Framework 6
  stack.

## Dependencies

`net.javacrumbs.shedlock:shedlock-spring:7.7.0`, `net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0`
(new); `VerificationTokenRepository`, `RefreshTokenFamilyRepository` (existing, each gaining one new
derived-delete method); `Clock` (existing); `DataSource`/`JdbcTemplate` (for the ShedLock provider
bean and for the raw `shedlock` row cleanup, since no JPA entity exists for that table).

## Inputs

None (a scheduled job, not request-driven). Reads its cron expression and both retention-day values
from configuration.

## Outputs

None (no HTTP response, no event emission — R40 doesn't call for an audit/event and no other
requirement scopes one in for this task).

## State Changes

- `verification_tokens` rows with `expires_at < now()` are hard-deleted.
- `refresh_token_family` rows with `revoked_at IS NOT NULL AND revoked_at < now() - family-retention-days`
  are hard-deleted; their `refresh_token_archive` rows are removed by the existing cascade, not by
  new application code.
- `shedlock` rows with `lock_until < now() - token-retention-days` are hard-deleted via a plain
  `JdbcTemplate`/native query (no JPA entity for this table).
- No other table is touched.

## Files to Create

- One new `@Component` job class (package/name: Phase 5's call — likely `common` or a new
  `cleanup` package, since it touches both `account` and `token` repositories and shouldn't live
  inside either).

## Files to Modify

- `services/auth/pom.xml` (add the two ShedLock dependencies).
- `services/auth/src/main/java/com/themistra/auth/AuthServiceApplication.java` (add
  `@EnableSchedulerLock`).
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java` (add a
  delete-by-expiry method).
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenFamilyRepository.java` (add a
  delete-by-old-revoked method).
- `services/auth/src/main/resources/application.properties` (add the three
  `themistra.auth.cleanup.*` keys).

## Files NOT to Modify

- Any Flyway migration file (L1).
- `RefreshTokenFamily.java`, `RefreshTokenArchiveEntry.java`, `VerificationToken.java` (entities
  unchanged — this task only deletes rows, never changes entity shape).
- `OutboxRelay.java` (precedent only, not touched).
- Any T25-T29 file.

## Acceptance Criteria

- **AC1 (R40).** Expired verification tokens are hard-deleted; non-expired ones are preserved.
- **AC2 (R40).** Refresh-token families revoked more than `family-retention-days` ago are
  hard-deleted; more-recently-revoked or never-revoked families are preserved.
- **AC3 (R40, OQ1).** Deleting an old revoked family removes its archive rows via cascade; an
  active family's archive rows are never touched by this job regardless of age.
- **AC4 (R40, OQ2).** ShedLock rows older than the reused retention threshold are hard-deleted;
  fresher ones are preserved.
- **AC5 (task statement).** The job method carries `@SchedulerLock` with an explicit lock name, and
  `@EnableSchedulerLock` is present on the application context, so only one instance across
  replicas executes a given scheduled firing.
- **AC6.** The job's cron and both retention values come from
  `themistra.auth.cleanup.{cron,token-retention-days,family-retention-days}`, not hardcoded
  literals.
- **AC7 (OQ3).** A failure in any one of the three cleanup steps is logged and does not prevent the
  other two from running.

## Required Tests

- `shouldCleanupExpiredTokensAndFamilies` (named, `package.md` §8) — proves AC1 and AC2 together in
  one integration-level test, per its own name.
- Unit-level (mocked repositories, no Docker needed): each of the three delete methods is called
  with the correct age threshold derived from the configured retention days and a fixed `Clock`;
  one step throwing does not prevent the other two from being invoked (AC7).
- Integration-level (Testcontainers + Awaitility, per the task statement): seed an expired token, an
  old revoked family (with an archive row), a recently-revoked family, a stale ShedLock row, and a
  fresh one; invoke the job directly (not wait for the real cron, which would make the test
  needlessly slow — Phase 5/10's call on the exact invocation mechanism); poll with Awaitility until
  the expected rows are gone and the ones that should survive still do; separately assert the
  archive row for the deleted family is also gone (cascade proof, AC3).

## Constraints

- **Transaction:** each of the three delete operations should be its own transactional unit (or the
  whole method `@Transactional` per step, matching OQ3's try/catch-per-step isolation) — a failure
  in one must not roll back the other two, which requires them not sharing one outer transaction.
- **Thread-safety:** ShedLock itself is the concurrency guard; no additional synchronization needed
  in application code.
- **Null handling:** N/A — no nullable business logic introduced, only age-threshold comparisons
  against a fixed `Clock`.
- **Module boundaries (L12):** the new job class needs both `VerificationTokenRepository`
  (`account`) and `RefreshTokenFamilyRepository` (package-private, `token`) — since it can't live
  inside `token` and also directly use `account`'s repository without violating that package's
  privacy from the other side, it must depend on `RefreshTokenFamilyRepository` only if placed
  inside `token`, or go through a `token`-package service class if placed elsewhere. Phase 5 must
  resolve this placement concretely; flagged here as a real constraint, not a detail to skip.
- **Security:** no new HTTP surface, no enumeration-safety concerns (R46 doesn't apply, no response
  body exists).
- **Performance:** bounded, once-nightly batch deletes; no pagination required unless row volumes
  are expected to be extreme (not indicated anywhere in the spec — a plain `DELETE ... WHERE` is
  sufficient).

## Open Questions

No blockers. (OQ1-OQ4 from Phase 1 all have a proposed resolution above, offered for Phase 3/4 to
challenge or confirm.)

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
