<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T30 · Phase 1 — Specification Extraction

## Business Rules

- **R40.** WHEN a scheduled cleanup job runs, THEN the system SHALL hard-delete verification tokens
  whose `expires_at` has passed, refresh-token families/archives older than the configured
  retention, and stale ShedLock rows.

## Locked Decisions

- **L1.** The V1–V4 Flyway migrations are immutable; any new schema work is delivered only as a
  follow-up migration named `V5__...`. **Already satisfied for this task**: `V5__lockout_cleanup_and_shedlock.sql`
  already exists and already contains the `shedlock` table this task needs — confirmed at Phase 0.
  T30 therefore requires **no new migration file**; adding one would be unnecessary, not merely
  disallowed.

## Files involved

**Existing files to read/extend:**
- `account/VerificationToken.java` / `VerificationTokenRepository.java` — needs a new
  derived-delete method keyed on `expires_at`.
- `token/RefreshTokenFamily.java` / `RefreshTokenFamilyRepository.java` (package-private) — needs a
  new derived-delete method keyed on `revoked_at`; `refresh_token_archive`'s
  `ON DELETE CASCADE` (V2) means deleting a family row already removes its own archive rows.
- `events/OutboxRelay.java` — not modified, but the closest existing precedent for a
  `@Component` + `@Scheduled` job shape in this codebase (interval read from a `themistra.auth.*`
  property with a default).
- `application.properties` — add the three already-specified keys:
  `themistra.auth.cleanup.cron`, `.token-retention-days`, `.family-retention-days`.
- `AuthServiceApplication.java` — already carries `@EnableScheduling`; will additionally need
  `@EnableSchedulerLock` (new annotation, new import) for ShedLock to activate.
- `pom.xml` — add `shedlock-spring` + `shedlock-provider-jdbc-template` as new dependencies
  (confirmed at Phase 0: no existing version pin anywhere in the monorepo).

**New files the spec expects:** none named explicitly by `design.md`'s file tree (unlike most
earlier tasks, no class name is specified for the job itself) — Phase 2 will need to decide the
job class's name/package and whether a dedicated `shedlock` row-cleanup query needs its own new
repository or a plain `JdbcTemplate`/`EntityManager` native query (no JPA entity exists for the
`shedlock` table, since ShedLock manages that table itself, not via an application-level entity).

## Dependencies

- ShedLock: `net.javacrumbs.shedlock:shedlock-spring`, `net.javacrumbs.shedlock:shedlock-provider-jdbc-template`
  (new Maven dependencies), `@EnableSchedulerLock`, `@SchedulerLock`.
- `VerificationTokenRepository`, `RefreshTokenFamilyRepository` (existing).
- `Clock` (existing, must be used for any "now" comparison — every other time-based cleanup/decision
  in this codebase already does this).
- Config keys (already specified in `design.md`, not to be invented): `themistra.auth.cleanup.cron`
  (`0 2 * * *`), `themistra.auth.cleanup.token-retention-days` (`7`),
  `themistra.auth.cleanup.family-retention-days` (`90`).
- `org.awaitility:awaitility` (existing test dependency, already on the classpath).

## Acceptance Criteria

- **AC1 (R40).** A verification token whose `expires_at` is in the past is hard-deleted by the job;
  one whose `expires_at` is still in the future is not.
- **AC2 (R40).** A `refresh_token_family` row revoked more than `family-retention-days` ago is
  hard-deleted; one revoked more recently, or never revoked at all, is not.
- **AC3 (R40).** Deleting an old revoked family removes its own `refresh_token_archive` rows too
  (via the existing `ON DELETE CASCADE`, not new application logic) — subject to Open Question 1
  below on whether archive rows need any *independent* pruning beyond this cascade.
- **AC4 (R40).** A `shedlock` row whose `lock_until` is sufficiently in the past is hard-deleted by
  the job (exact "sufficiently" threshold is Open Question 2 below).
- **AC5 (multi-replica correctness, task statement's own "ShedLock-annotated" requirement).** The
  job is annotated so that, across multiple concurrently-running instances, only one actually
  executes the cleanup logic per scheduled firing — the core reason this task exists rather than
  being a plain `@Scheduled` method like `OutboxRelay`'s.
- **AC6 (cron/config correctness).** The job's schedule and both retention thresholds are read from
  the three `themistra.auth.cleanup.*` properties, not hardcoded.

## Tests required

**Named test (`package.md` §8):** `shouldCleanupExpiredTokensAndFamilies` — mapped to R36 in the
spec file text itself, but T30's own header correctly scopes this task to R40; same recurring §8
numbering bug seen at nearly every prior task (re-confirmed, not silently trusted).

Implied boundary/behavioral tests:
1. Expired verification token deleted; non-expired one preserved.
2. Old revoked family deleted; recently-revoked or never-revoked family preserved.
3. Deleting an old revoked family also removes its archive rows (cascade proof).
4. Stale `shedlock` row deleted; a fresh/currently-held lock row preserved.
5. The job runs on its own configured schedule/lock name — some proof the `@SchedulerLock`
   annotation is present and correctly named (unit- or context-level, not necessarily requiring two
   real concurrent instances to prove true mutual exclusion, which is hard to simulate meaningfully
   in a single-JVM test).
6. Integration-level (Awaitility, Docker-permitting): seed expired/old/stale rows directly, trigger
   the job (or wait for its schedule, or invoke it directly — Phase 2's call), and use Awaitility to
   poll until the rows are gone rather than asserting synchronously.

## Open Questions

- **OQ1.** Does "refresh-token ... archives" in the task statement mean archive-row cleanup is
  purely a side effect of the family-row `ON DELETE CASCADE`, or does the job also need to
  independently prune `refresh_token_archive` rows for families that are still active (not yet
  revoked) but have accumulated very old `superseded_at` entries from many rotations? Neither
  `requirements.md` nor `design.md` distinguishes these — flagged at Phase 0, restated here as a
  genuine open question for Phase 2/3, not assumed either way.
- **OQ2.** What retention threshold applies to "stale ShedLock rows"? No property is specified
  anywhere in the spec package for this specifically (unlike the other two, which have named
  properties). Candidates: reuse `token-retention-days`, reuse `family-retention-days`, a fixed
  short threshold (ShedLock rows are only ever momentarily "live" during a job run, so even a few
  days old is unambiguously stale), or a new dedicated property. Genuine gap, not decided here.
- **OQ3.** Should the job invoke each of the three cleanup actions (tokens, families/archives,
  shedlock rows) as one unit, or should they be independently schedulable/failable? The task
  statement's singular "a ShedLock-annotated job" suggests one job, one lock name, doing all three —
  but whether a failure in one sub-action (e.g., the shedlock-row cleanup) should prevent or be
  independent of the other two is not specified. Analogous in spirit to T28's D3 (best-effort
  per-family) but for a different task's own three sub-actions — Phase 2/3's call.
- **OQ4.** Exact Maven coordinates/version for ShedLock — confirmed at Phase 0 that nothing in the
  monorepo currently pins one. Not a blocker (any reasonably current, stable version is a safe
  choice for a first-time addition), but flagged as a decision Phase 2 should make explicitly
  rather than pick silently without recording the version chosen.

None of these four are true blockers to starting Phase 2 — each has at least one reasonable default
this task's own frozen-brief gate (Phase 3/4) can confirm or redirect, matching this pipeline's
established pattern for tasks with design latitude.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
