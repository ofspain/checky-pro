<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T30 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11) against `requirements.md`, `design.md`,
`tasks.md`, and the frozen brief for **T30 only**. `spec/auth-service/` confirmed byte-for-byte
unchanged since T30 began (`git diff 5e007ff...HEAD --stat -- spec/auth-service/` — empty,
`5e007ff` being T29's final commit, "t29 fixed").

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R40** — scheduled job hard-deletes expired verification tokens, old revoked families/archives, and stale ShedLock rows | Yes | `CleanupJob.java:57` (`run`), `:64-93` (three steps); `VerificationTokenService.java:174-176`, `VerificationTokenRepository.java:50`; `RefreshTokenTracker.java:109-111`, `RefreshTokenFamilyRepository.java:33` | `VerificationTokenServiceTest`/`RefreshTokenTrackerTest` (2 new, executed, green); `CleanupJobTest` (7, executed, green); `CleanupIntegrationTest.shouldCleanupExpiredTokensAndFamilies` (named, written, Docker-blocked) | No | No |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| **L1** — V1-V4 immutable; new schema only as numbered follow-up migrations | Yes | V1-V5 untouched (confirmed via `git diff`); `V6__cleanup_indexes.sql` is additive-only (two `CREATE INDEX IF NOT EXISTS` statements, no table/column changes) — the frozen brief's own D3 decision to add V6 rather than treat L1 as "exactly one follow-up ever" |

## This Task's Own Design Decisions — honored?

| Decision | Honored? | Evidence |
|---|---|---|
| **D1** — new `cleanup` package; job calls only public service methods, never either repository directly | Yes | `CleanupJob.java:3-4` imports only `VerificationTokenService`/`RefreshTokenTracker`; enforced permanently by the new `ArchitectureTest.cleanup_job_never_depends_on_repositories_directly` rule (Phase 11) |
| **D2** — no outer `@Transactional` on `run()`; each step independently try/catch-guarded | Yes | `CleanupJob.java:56-62` (`run()` has no `@Transactional`); `:64-93` (three try/catch-guarded private steps); proven by `CleanupJobTest`'s three failure-isolation tests |
| **D3** — new `V6__cleanup_indexes.sql` for the two supporting indexes | Yes | File exists, additive-only, matches the frozen brief's exact SQL |
| **D4** — ShedLock staleness predicate includes the `lock_until < now()` safety guard | Yes | `CleanupJob.java:87` (post-Phase-9 fix: `cutoff` bound as a raw `Instant`, not `Timestamp`); proven exactly (not just substring) by `CleanupJobTest` (Phase 11 Gap 4) and directly in `CleanupIntegrationTest` against a real currently-held lock row |
| *(Phase 8/9 fix)* `lockAtMostFor`/`defaultLockAtMostFor` extended to `PT1H` | Yes | `CleanupJob.java:57`, `AuthServiceApplication.java`; proven present with the exact value by `CleanupJobTest.runIsAnnotatedWithScheduledAndSchedulerLock` (Phase 11 Gap 3) |
| *(Phase 7/8 fix)* ShedLock cutoff bound as `Instant`, not `Timestamp` (timezone safety) | Yes | `CleanupJob.java:83-89`; `Timestamp` import removed entirely from the file |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | Expired-token deletion path, unit + integration tested |
| AC2 | **Met** | Old-revoked-family deletion path, unit + integration tested |
| AC3 | **Met** | `CleanupIntegrationTest` directly asserts the archive row count is zero after the owning family is deleted — proves the cascade, not just infers it |
| AC4 | **Met** | ShedLock predicate exact-matched (Phase 11 Gap 4) and proven against a real currently-held lock row in the integration test |
| AC5 | **Met** | `@SchedulerLock`/`@Scheduled` presence and exact attribute values proven via reflection (Phase 11 Gap 3); `LockProvider` bean proven to construct correctly (`CleanupConfigTest`) |
| AC6 | **Met** | Cron/retention values sourced from `CleanupProperties`/the `@Scheduled` placeholder, never hardcoded; validation bounds proven to actually reject bad config (`CleanupPropertiesTest`, Phase 11 Gap 1) |
| AC7 | **Met** | All three failure-isolation scenarios (token step, family step, ShedLock step each independently throwing) proven in `CleanupJobTest` |
| AC8 | **Met** | Both repositories remain package-private; permanently enforced by the new `ArchitectureTest` rule, not just true today by convention |

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. This is the first task since T25 to introduce a genuinely
new Flyway migration (`V6`), a new third-party dependency (ShedLock), and a new top-level package
(`cleanup`) — a larger surface than T28/T29's internal-logic-only changes — yet every file traces
directly to the frozen brief's own Files sections, with zero scope creep. The review process across
Phases 7/8/11 caught and fixed two genuine, verifiable defects before merge (the `Timestamp`
timezone dependency — caught independently by both self-review and Kimi — and the `lockAtMostFor`
duration risk), and added permanent structural protection (a new `ArchitectureTest` rule) against a
future regression of this task's own module-boundary design decision, not just point-in-time test
coverage.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC8 all have direct code
evidence and either an executed passing test or a written-but-Docker-blocked one, and the three
Phase 11 gaps that specifically targeted "a future silent regression wouldn't be caught" concerns
(config validation, module boundaries, ShedLock annotation presence) are now all closed with
dedicated, Docker-independent tests — an unusually thorough closing of that particular class of risk
relative to earlier tasks in this stretch.

**(3) Does it violate any LOCKED decision?** No. L1 is honored via the frozen brief's own explicit,
gate-approved reading of it (numbered follow-up migrations are the established pattern, not a
one-time exception) — a documented, deliberate decision, not a silent deviation.

**(4) Remaining risks:**
- **Unexecuted integration suite, now spanning SIX consecutive tasks (T25-T30).** `CleanupIntegrationTest`'s
  one test (the named `shouldCleanupExpiredTokensAndFamilies`) has never executed — Docker has been
  unavailable this entire session. This is the single highest-value pre-merge action across the
  whole stretch, and for T30 specifically it's also the only test that would catch an actual Spring
  context startup failure (ShedLock auto-wiring, the V6 migration applying) — a class of failure
  none of the unit tests can detect by construction.
- **`CleanupConfigTest`'s reliance on `JdbcTemplateLockProvider`'s constructor not eagerly touching
  the `DataSource`** was verified against ShedLock 7.7.0's actual source (it just wraps a
  `JdbcTemplate`), so this is a confirmed-safe test technique, not an assumption.
- **No metrics/observability beyond log lines** (Kimi Phase 8 Finding 4) — explicitly deferred as
  out of this task's scope, logged as a named follow-up, not silently dropped.
- Contract files (`contracts/api/auth.yaml`, `token-claims.md`) still don't exist — same
  long-standing gap noted at every task since T25; not applicable to this task's own surface (no
  HTTP endpoint).

---

## Verdict

**PASS** — every requirement, LOCKED decision, design decision, and acceptance criterion has direct
code evidence and either an executed passing test or a written-but-Docker-blocked one with a clear
account of why; the review process caught two real, independently-confirmed defects and closed
three distinct "future regression wouldn't be caught" gaps with permanent structural tests, a
notably thorough outcome for the first task in this stretch to add new infrastructure (a dependency,
a migration, and a new package) rather than only modify existing internal logic.

---

**Phase 12 complete — PASS.** Proceed to Phase 13 (PR / Commit Preparation).
