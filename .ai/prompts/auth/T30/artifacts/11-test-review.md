<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T30 · Phase 11 — Test Review

Consumes the Phase 10 test manifest and the actual test files. All Docker-independent tests pass (47/47). Findings only — no test or production code changes in this phase.

---

## Executive Summary

The Phase 10 suite is comprehensive and well-layered. Unit tests cover cutoff derivation, failure isolation, and SQL predicate shape; integration test `shouldCleanupExpiredTokensAndFamilies` covers the full end-to-end behavior with survivors and cascade verification. The gaps below are minor and mostly architectural/observability rather than correctness.

---

## Findings

### Gap 1 — No test that invalid `CleanupProperties` fail startup

**Why it matters:** `CleanupProperties` carries `@NotBlank` on `cron` and `@Min(1)` on both retention days, so an invalid config is supposed to prevent startup (AC6). Without a test, a future refactor could drop `@Validated` or move the record out of `@ConfigurationPropertiesScan` and silently allow a zero-retention or missing-cron configuration. A missing cron would make `@Scheduled` fail at context-refresh time in a different way, but invalid retention values would produce an immediate mass-delete or no-op.

**Suggested test:** Add an `@SpringBootTest`-style test (or a `ApplicationContextInitializer` test) that sets `themistra.auth.cleanup.family-retention-days=0` and asserts the context fails to start with a `BindException` / validation failure. Alternatively, test the record directly with a Jakarta validator.

**Evidence:** `CleanupProperties.java:14-21`.

---

### Gap 2 — No ArchUnit rule enforces the cleanup package's service-only boundary

**Why it matters:** `CleanupJob` must depend only on public service types (`VerificationTokenService`, `RefreshTokenTracker`), never on package-private repositories or entity classes. The production code currently satisfies this, but `ArchitectureTest` has no rule guarding the `cleanup` package. A future maintainer could add a direct `VerificationTokenRepository` or `RefreshTokenFamilyRepository` dependency to `CleanupJob`, re-introducing the exact module-boundary tension the Phase 3/4 design decision resolved.

**Suggested test:** Add an ArchUnit rule: no classes in `com.themistra.auth.cleanup` may depend on classes in `com.themistra.auth.account..` or `com.themistra.auth.token..` except for `*Service` and `*Tracker` public types. (Or, more simply, forbid direct dependency on any class named `*Repository`.)

**Evidence:** `ArchitectureTest.java:21-97`; `CleanupJob.java:3-4`.

---

### Gap 3 — No test verifies `@SchedulerLock` is actually present and configured

**Why it matters:** The integration test calls `cleanupJob.run()` directly, bypassing ShedLock's AOP proxy entirely. The unit tests mock collaborators. If `@SchedulerLock` were accidentally removed from `run()` or the `LockProvider` bean were missing, every existing test would still pass, but the multi-replica safety required by AC5 would be broken in production.

**Suggested test:** Add a lightweight Spring-context test (Testcontainers not required) that autowires `CleanupJob`, reflects on `run()`, and asserts the method is annotated with both `@Scheduled` and `@SchedulerLock` with the expected `name`, `lockAtLeastFor`, and `lockAtMostFor` values. Also assert the context contains a `LockProvider` bean.

**Evidence:** `CleanupJob.java:56-57`; `CleanupConfig.java:18-21`.

---

### Gap 4 — ShedLock SQL assertion uses substring matching rather than exact predicate verification

**Why it matters:** `runDeletesStaleShedLockRowsUsingTokenRetentionDaysCutoffAndTheSafetyGuardPredicate` checks that the SQL string contains `DELETE FROM shedlock`, `lock_until < ?`, and `lock_until < now()`. This is sufficient to catch gross errors, but it would not catch a subtle bug where the two `lock_until` predicates are ORed together instead of ANDed, or where the parameter placeholder is bound to the wrong value.

**Suggested test:** Strengthen the assertion to verify the exact SQL string (white-space-normalized if needed) is `"DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()"`, and that the captured parameter equals the expected cutoff `Instant`. The current test already captures the `Instant`; adding an exact-SQL assertion would close the hole.

**Evidence:** `CleanupJobTest.java:72-84`.

---

## Non-Issues Confirmed

- **Cutoff derivation:** `CleanupJobTest` verifies token cutoff = current instant, family cutoff = `now - familyRetentionDays`, and ShedLock cutoff = `now - tokenRetentionDays`.
- **Failure isolation (AC7/D2):** all three failure-isolation unit tests pass; each step's failure does not prevent the other two from being invoked.
- **Phase 9 Timestamp fix:** `CleanupJobTest` captures the ShedLock parameter as a raw `Instant`, proving `Timestamp.from(...)` was removed.
- **Cascade correctness (AC3):** `CleanupIntegrationTest` asserts the archive row for the deleted old-revoked family is gone.
- **Survivor correctness (AC1/AC2/AC4):** the integration test verifies the non-expired token, recently-revoked family, never-revoked family, and currently-held ShedLock row all survive.
- **ShedLock safety guard (D4):** the integration test directly confirms a future-dated (currently-held) lock row is not pruned.
- **T29 regression coverage:** `RefreshTokenTrackerTest.revokeForAuthorizationDoesNotArchiveOldHash` was added and passes.

---

## Traceability Summary

| AC | Covered By | Gap |
|---|---|---|
| AC1 — expired tokens deleted | `CleanupJobTest.runDeletesExpiredTokensUsingCurrentInstantAsCutoff`, `VerificationTokenServiceTest.deleteExpiredTokensDelegatesToRepositoryWithGivenCutoffAndReturnsItsCount`, `CleanupIntegrationTest.shouldCleanupExpiredTokensAndFamilies` | None |
| AC2 — old revoked families deleted | `CleanupJobTest.runDeletesOldRevokedFamiliesUsingFamilyRetentionDaysCutoff`, `RefreshTokenTrackerTest.deleteRevokedFamiliesOlderThanDelegatesToRepositoryWithGivenCutoffAndReturnsItsCount`, `CleanupIntegrationTest.shouldCleanupExpiredTokensAndFamilies` | None |
| AC3 — cascade archive deletion | `CleanupIntegrationTest.shouldCleanupExpiredTokensAndFamilies` (asserts archive count zero) | None |
| AC4 — stale ShedLock rows deleted | `CleanupJobTest.runDeletesStaleShedLockRowsUsingTokenRetentionDaysCutoffAndTheSafetyGuardPredicate`, `CleanupIntegrationTest.shouldCleanupExpiredTokensAndFamilies` | Gap 4 (substring vs exact SQL) |
| AC5 — ShedLock-guarded single execution | Production code only; annotations present | Gap 3 (no runtime/reflective verification) |
| AC6 — config-driven, not hardcoded | `CleanupJobTest` uses `CleanupProperties` | Gap 1 (no invalid-config startup test) |
| AC7 — step failure isolation | `CleanupJobTest` failure-isolation triplet | None |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval.
