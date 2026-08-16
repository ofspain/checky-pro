<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T30 · Phase 9 — Review Resolution

**Human Approval gate.** Consumes the self-review (Phase 7, 2 findings) and independent review
(Phase 8, Kimi, 5 findings). All findings verified against actual source before disposition. femi
decided the one finding with genuine design weight via human gate; the rest were unambiguous fixes
or expected pre-Phase-10 gaps.

## Self-review (Phase 7) findings

| # | Finding | Disposition |
|---|---|---|
| 1 | `Timestamp.from(cutoff)` introduces a latent timezone dependency | **Superseded by Kimi's Phase 8 Finding 1** (identical finding, independently confirmed) — resolved together below. |
| 2 | `CleanupProperties` Javadoc doesn't document the `tokenRetentionDays` reuse for ShedLock rows | **Superseded by Kimi's Phase 8 Finding 2** (identical finding, independently confirmed) — resolved below. |

## Independent review (Phase 8, Kimi) findings

| # | Finding | Confidence | Disposition |
|---|---|---|---|
| 1 | `Timestamp.from(cutoff)` binding creates a timezone/precision dependency in the ShedLock cleanup step | High | **ACCEPTED, applied.** Same finding as my own Phase 7 Finding 1. |
| 2 | `CleanupProperties` Javadoc hides the `tokenRetentionDays` reuse for ShedLock retention | High | **ACCEPTED, applied.** Same finding as my own Phase 7 Finding 2. |
| 3 | `lockAtMostFor = "PT10M"` could let a second replica start a concurrent cleanup pass if a first run against unpruned backlog exceeds 10 minutes | Medium | **ACCEPTED, femi's gate decision.** Extended to `PT1H` — a nightly job has no reason to need a tight bound, and an hour gives real headroom for a first run while still self-healing if a replica genuinely crashes mid-run. |
| 4 | No metrics beyond log lines for cleanup outcomes/failures | Low | **Rejected — deferred, out of scope.** R40 and the task statement call for the cleanup logic itself, not job observability tooling; adding Micrometer counters would be scope creep beyond this task's authorized files. Logged as a named follow-up, not silently dropped. |
| 5 | No cleanup-specific tests exist yet | High (that they're missing) / N/A (that this is a gap right now) | **No action — expected.** Kimi itself confirmed this is Phase 10's job, not a Phase 8 defect; explicitly flagged tests to write there. |

## Exact changes made

**`services/auth/src/main/java/com/themistra/auth/cleanup/CleanupJob.java`** (Findings 1 + 3):
- Removed the `java.sql.Timestamp` import and the `Timestamp.from(cutoff)` conversion;
  `deleteStaleShedLockRows` now binds `cutoff` (the `Instant`) directly via
  `jdbcTemplate.update("DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()", cutoff)`
  — matches how every JPA repository method in this codebase already binds `Instant` parameters,
  and sidesteps the JVM-default-timezone dependency entirely.
- `@SchedulerLock`'s `lockAtMostFor` changed from `"PT10M"` to `"PT1H"`.

**`services/auth/src/main/java/com/themistra/auth/AuthServiceApplication.java`** (Finding 3,
consistency): `@EnableSchedulerLock`'s `defaultLockAtMostFor` changed from `"PT10M"` to `"PT1H"` to
match the job's own explicit bound.

**`services/auth/src/main/java/com/themistra/auth/cleanup/CleanupProperties.java`** (Finding 2):
added a Javadoc comment directly on `tokenRetentionDays` stating it also governs stale `shedlock`
row retention, with the reasoning (low-cardinality, not worth a fourth config key) inline.

## Verification performed

- `mvn -pl services/auth -am clean compile` — clean, no errors.
- No repository/service-layer files were touched in this resolution round (only `CleanupJob`,
  `AuthServiceApplication`, `CleanupProperties`), so the Phase 6 regression check
  (`VerificationTokenServiceTest`/`RefreshTokenTrackerTest`, 39/39 passing) remains valid and was
  not re-run, since none of those tests exercise the files just changed.

---

**Phase 9 complete — review resolved, femi signed off.** Proceed to Phase 10 (Test Generation).
