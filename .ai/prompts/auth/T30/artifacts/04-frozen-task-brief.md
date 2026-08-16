<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T30 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
6 findings). All 6 verified against actual source before disposition — none were misreadings.
femi decided the two findings with genuine trade-offs via human gate; the remaining four are
mechanical amendments folded in directly.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | Job needs repositories from two package-private modules; placement unresolved | Medium | **Resolved, femi's gate decision.** New `cleanup` package holds the job; `VerificationTokenService` (account) and `RefreshTokenTracker` (token) each gain one new public method. Repositories stay package-private. |
| 2 | Transaction isolation of the three steps is self-contradictory in the TIB | Medium | **Accepted, folded in.** No `@Transactional` on the job method; each of the three cleanup calls runs in its own independent transaction via the target repository/service method's own `@Transactional`, matching T28's established "Spring Data's automatic per-call transaction" precedent (`SessionService.revokeAll`). |
| 3 | No index supports either cleanup query's WHERE clause; will full-scan as tables grow | Medium | **Resolved, femi's gate decision.** A new `V6__cleanup_indexes.sql` migration adds the two supporting indexes. L1 is read as establishing the "new schema work goes in numbered follow-up migrations" pattern (V5 being the first instance), not as authorizing exactly one follow-up migration ever — V6 is a narrow, directly-motivated addition, not a broader schema change. |
| 4 | ShedLock staleness predicate doesn't guard against pruning a currently-held lock | Low | **Accepted, folded in.** Predicate is `lock_until < :cutoff AND lock_until < now()` — the second clause is the safety guard Kimi specified. |
| 5 | Outbox table cleanup ambiguously in/out of scope | Low | **Accepted, folded in.** Explicitly out of scope for T30 (belongs to the outbox relay or a separate operational task) — stated below under Scope so Phase 5/6 doesn't silently add it. |
| 6 | Missing `LockProvider` bean — `@SchedulerLock` will fail at runtime without it | Low | **Accepted, folded in.** Added to Files to Create below. |

## Task

Scheduled cleanup job — add a ShedLock-annotated job that hard-deletes expired verification
tokens, old revoked refresh-token families (and, via cascade, their archive rows), and stale
ShedLock rows, on a nightly schedule.

## Purpose

Unchanged from Phase 2: keep `verification_tokens`, `refresh_token_family`/`refresh_token_archive`,
and `shedlock` bounded, guarded against concurrent execution across ≥2 EKS replicas.

## Scope

**In:** one new `cleanup` package containing the job class and its `LockProvider` bean; the
ShedLock library as a new dependency; `@EnableSchedulerLock` on the application class; one new
public method each on `VerificationTokenService` (account) and `RefreshTokenTracker` (token); the
three `themistra.auth.cleanup.*` config properties; a new `V6__cleanup_indexes.sql` migration for
the two supporting indexes.

**Out:** any change to how verification tokens or refresh-token families are created/consumed
elsewhere; **outbox table cleanup** (Finding 5 — belongs to the outbox relay or a separate
operational task, not this one); T31 (rate limiting); T33/T34 (contracts).

## Business Rules

- **R40.** A scheduled cleanup job hard-deletes expired verification tokens, old revoked
  families/archives, and stale ShedLock rows.

## Locked Decisions

- **L1.** V1-V4 remain immutable. `V5__lockout_cleanup_and_shedlock.sql` is untouched. This task
  adds `V6__cleanup_indexes.sql` as a new, narrow follow-up migration (femi's Finding 3 decision) —
  consistent with L1's own framing of new schema work arriving as numbered follow-up migrations.

## This Task's Own Design Decisions (D1-D4, decided at this gate)

- **D1 (Finding 1).** New `cleanup` package holds the job class. `VerificationTokenService` gains a
  new public `deleteExpiredTokens(Instant cutoff)` (or equivalent) method; `RefreshTokenTracker`
  gains a new public `deleteRevokedFamiliesOlderThan(Instant cutoff)` (or equivalent) method — both
  existing, already-public service classes in their respective modules. `VerificationTokenRepository`
  and `RefreshTokenFamilyRepository` remain package-private; the job never references either
  directly.
- **D2 (Finding 2).** The job method itself carries no `@Transactional` annotation. Each of the
  three cleanup calls (token deletion, family deletion, ShedLock row deletion) is its own
  independent transactional unit via the target method's own transaction boundary, individually
  wrapped in try/catch in the job so one failing is logged and does not prevent the other two.
- **D3 (Finding 3).** New migration `V6__cleanup_indexes.sql`:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_verification_tokens_expires_at ON verification_tokens(expires_at);
  CREATE INDEX IF NOT EXISTS idx_refresh_token_family_revoked_at ON refresh_token_family(revoked_at)
      WHERE revoked_at IS NOT NULL;
  ```
  (Partial index on the second, since the cleanup query already filters `revoked_at IS NOT NULL`.)
- **D4 (Finding 4).** ShedLock cleanup predicate: `lock_until < :cutoff AND lock_until < now()` —
  the second clause guards against ever pruning a currently-held or future-dated lock row.

## Dependencies

`net.javacrumbs.shedlock:shedlock-spring:7.7.0`, `net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0`
(new); `VerificationTokenService`, `RefreshTokenTracker` (existing, each gaining one new public
method); `Clock` (existing); `DataSource`/`JdbcTemplate` (for the `LockProvider` bean and the raw
`shedlock` row cleanup).

## Inputs

None (scheduled, not request-driven). Reads cron and both retention-day values from configuration.

## Outputs

None.

## State Changes

- `verification_tokens` rows with `expires_at < now()` hard-deleted (via
  `VerificationTokenService.deleteExpiredTokens`).
- `refresh_token_family` rows with `revoked_at IS NOT NULL AND revoked_at < now() - family-retention-days`
  hard-deleted (via `RefreshTokenTracker.deleteRevokedFamiliesOlderThan`); their
  `refresh_token_archive` rows removed by the existing `ON DELETE CASCADE`, never independently
  pruned for active families (Phase 2's OQ1 resolution, re-confirmed).
- `shedlock` rows matching D4's predicate hard-deleted via a plain `JdbcTemplate` query (no JPA
  entity for this table).

## Files to Create

- New `cleanup` package: the job class (`@Component`, one `@Scheduled` + `@SchedulerLock`-annotated
  method calling the three steps per D2) and a configuration class providing the `LockProvider`
  bean (`@Bean JdbcTemplateLockProvider lockProvider(DataSource dataSource)`, Finding 6).
- `services/auth/src/main/resources/db/migration/V6__cleanup_indexes.sql` (D3).

## Files to Modify

- `services/auth/pom.xml` (ShedLock dependencies).
- `AuthServiceApplication.java` (`@EnableSchedulerLock`).
- `account/VerificationTokenService.java` (new public method).
- `account/VerificationTokenRepository.java` (new package-private derived-delete method, called
  only from `VerificationTokenService`).
- `token/RefreshTokenTracker.java` (new public method).
- `token/RefreshTokenFamilyRepository.java` (new package-private derived-delete method, called
  only from `RefreshTokenTracker`).
- `application.properties` (three `themistra.auth.cleanup.*` keys).

## Files NOT to Modify

- `V1`-`V5` migration files (L1).
- `RefreshTokenFamily.java`, `RefreshTokenArchiveEntry.java`, `VerificationToken.java` (entities).
- `OutboxRelay.java`, the `outbox` table/repository (Finding 5, out of scope).
- Any T25-T29 file.

## Acceptance Criteria

- **AC1 (R40).** Expired verification tokens hard-deleted; non-expired preserved.
- **AC2 (R40).** Families revoked more than `family-retention-days` ago hard-deleted; more-recent
  or never-revoked preserved.
- **AC3 (R40).** Deleting an old revoked family cascades its archive rows; an active family's
  archive rows are never touched regardless of age.
- **AC4 (R40, D4).** ShedLock rows matching the staleness predicate hard-deleted; currently-held or
  future-dated rows never touched.
- **AC5.** `@SchedulerLock` with an explicit lock name is present; `@EnableSchedulerLock` and the
  `LockProvider` bean are wired so ShedLock is actually functional at runtime, not just annotated.
- **AC6.** Cron and both retention values come from `themistra.auth.cleanup.*` properties.
- **AC7 (D2).** A failure in any one of the three steps is logged and does not prevent the other
  two from running, and does not roll back work already committed by an earlier step.
- **AC8 (D1).** `VerificationTokenRepository` and `RefreshTokenFamilyRepository` remain
  package-private; the job never imports either directly.

## Required Tests

- `shouldCleanupExpiredTokensAndFamilies` (named, `package.md` §8).
- Unit-level: each new repository method's WHERE-clause boundary (age cutoff edge); each new
  service-layer method's delegation; the job's per-step failure isolation (one step's mocked
  collaborator throws, the other two are still invoked).
- Integration-level (Testcontainers + Awaitility): seed one expired token, one non-expired token,
  one old-revoked family (with an archive row), one recently-revoked family, one never-revoked
  family, one stale ShedLock row, one fresh ShedLock row; invoke the job directly; poll with
  Awaitility until expected deletions have happened; assert survivors are untouched; assert the old
  family's archive row is gone (cascade) and confirm via the new indexes' existence (e.g., a
  metadata query) as a lightweight AC3/D3 sanity check if convenient, though not required.

## Constraints

- **Transaction:** per D2 — no outer `@Transactional` on the job method.
- **Thread-safety:** ShedLock is the concurrency guard; no additional synchronization.
- **Module boundaries (L12):** per D1 — both repositories stay package-private.
- **Security:** no new HTTP surface; R46 not applicable.
- **Performance:** D3's two indexes make the nightly deletes index-supported rather than full
  table scans.

## Open Questions

No blockers. All Phase 1 OQs (1-4) and all 6 Phase 3 findings are resolved above.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
