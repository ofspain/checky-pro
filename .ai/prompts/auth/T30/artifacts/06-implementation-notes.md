<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T30 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) exactly per the Phase 5 plan.
Only the files the plan authorized were touched.

## What changed

**`pom.xml`** — added `net.javacrumbs.shedlock:shedlock-spring:7.7.0` and
`net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.7.0` as compile-scope dependencies.

**`V6__cleanup_indexes.sql`** (new) — two indexes, exactly D3's SQL: a plain index on
`verification_tokens(expires_at)`, and a partial index on `refresh_token_family(revoked_at)`
filtered to `WHERE revoked_at IS NOT NULL` (matching the cleanup query's own filter, and the same
partial-index style already used by `idx_refresh_token_family_current_hash` in V2).

**`account/VerificationTokenRepository.java`** — added `deleteExpiredBefore(Instant cutoff)`, a
`@Modifying @Query` bulk JPQL delete, matching `markConsumed`/`invalidateActive`'s existing shape
in the same file.

**`account/VerificationTokenService.java`** — added `deleteExpiredTokens(Instant cutoff)`,
`@Transactional`, delegating straight to the new repository method. Matches every other public
method in this class in being individually `@Transactional`-annotated.

**`token/RefreshTokenFamilyRepository.java`** — added `deleteRevokedBefore(Instant cutoff)`, same
`@Modifying @Query` bulk-delete shape; added the `Modifying`/`Query`/`Param`/`Instant` imports this
file didn't previously need.

**`token/RefreshTokenTracker.java`** — added `deleteRevokedFamiliesOlderThan(Instant cutoff)`,
`@Transactional`, delegating to the new repository method — placed alongside `revokeAllForPrincipal`
since both are family-wide (not single-family) mutations.

**`AuthServiceApplication.java`** — added `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`
(a required attribute on this annotation, not optional — verified against ShedLock 7.7.0 sources).

**`application.properties`** — added the three `themistra.auth.cleanup.*` keys, each with an
`${ENV_VAR:default}` override exactly matching every other property in this file's convention.

**New `cleanup` package** (three new files, matching the plan exactly):
- `CleanupProperties.java` — validated record, identical shape to `ApiKeyProperties`.
- `CleanupConfig.java` — the `LockProvider` bean via `new JdbcTemplateLockProvider(dataSource)`.
  Verified against ShedLock 7.7.0's own sources that its default table/column names
  (`shedlock` / `name, lock_until, locked_at, locked_by`) match this schema's V5 table exactly, so
  no customization (table name, column names) was needed on the provider.
- `CleanupJob.java` — the `@Scheduled` + `@SchedulerLock`-annotated job, three independently
  try/catch-guarded private steps as planned. `deleteStaleShedLockRows` uses D4's exact two-clause
  predicate (`lock_until < ? AND lock_until < now()`).

## Mapping to the plan

Matches the Phase 5 plan's proposed signatures exactly — no implementation-time deviation was
needed this time (unlike T29's phase 6, which had one minor open detail; T30's plan was fully
concrete down to the exact SQL/predicate shapes, since Phase 4's D1-D4 decisions already resolved
every design question before Phase 5 began).

## Mapping to acceptance criteria

- **AC1/AC2** — the two new bulk-delete repository methods, called from the two new service
  methods, called from `CleanupJob`'s two corresponding private steps.
- **AC3** — no application code deletes archive rows; the database's own `ON DELETE CASCADE` (V2)
  handles it as soon as `deleteRevokedBefore` deletes the owning family row. Active families are
  never matched by the `revokedAt IS NOT NULL AND revokedAt < :cutoff` predicate regardless of how
  old their archive entries are.
- **AC4** — `deleteStaleShedLockRows`'s predicate matches D4 exactly.
- **AC5** — `@SchedulerLock(name = "auth-cleanup-job", ...)` on `run()`, `@EnableSchedulerLock` on
  the application class, `LockProvider` bean in `CleanupConfig` — all three pieces ShedLock needs
  to actually function (Finding 6) are present.
- **AC6** — the cron comes from `${themistra.auth.cleanup.cron}` directly in the `@Scheduled`
  annotation; both retention values come from the injected `CleanupProperties` record — no
  hardcoded literals anywhere in `CleanupJob`.
- **AC7** — each of the three private step methods has its own try/catch; none of the three calls
  share a transaction (`run()` itself carries no `@Transactional`, matching D2), so one step's
  failure cannot roll back another's already-committed delete.
- **AC8** — `VerificationTokenRepository` and `RefreshTokenFamilyRepository` remain
  package-private; `CleanupJob` only ever calls `VerificationTokenService`/`RefreshTokenTracker`,
  never either repository directly.

## Deviations from the plan

None. Every file, method signature, and SQL predicate matches Phase 5's plan exactly.

## Verification performed this phase

- `mvn -pl services/auth -am clean compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='VerificationTokenServiceTest,RefreshTokenTrackerTest'` — all
  39 pre-existing tests (22 + 17) in the two modified service classes still pass; no regression.
- Verified `Clock` is already an available bean (`common/SecurityBeansConfig.java`) — no new bean
  needed for `CleanupJob`'s constructor injection.
- Could not verify actual Spring context startup (ShedLock auto-wiring, Flyway applying V6) this
  session — Docker has been unavailable the entire session, and no non-Testcontainers context test
  exists in this codebase to substitute. This is a real, not-yet-executed verification gap specific
  to this task (a scheduled-job/ShedLock wiring issue would only surface at actual context startup,
  unlike a pure unit-testable service method) — flagged clearly, not glossed over.

No test code was written in this phase, per the guardrails — Phase 10's job.

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
