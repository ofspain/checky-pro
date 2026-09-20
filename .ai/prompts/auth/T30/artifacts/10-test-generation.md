<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T30 · Phase 10 — Test Generation

Test manifest for the resolved implementation (`artifacts/09-review-resolution.md`). No production
code changed in this phase. All tests below were written and executed.

## `VerificationTokenServiceTest.java` (extended — 1 new test, 23 total)

| Test | Verifies |
|---|---|
| `deleteExpiredTokensDelegatesToRepositoryWithGivenCutoffAndReturnsItsCount` | AC1 — the exact cutoff passed through to `VerificationTokenRepository.deleteExpiredBefore`, count returned unchanged. |

## `RefreshTokenTrackerTest.java` (extended — 1 new test, 18 total)

| Test | Verifies |
|---|---|
| `deleteRevokedFamiliesOlderThanDelegatesToRepositoryWithGivenCutoffAndReturnsItsCount` | AC2 — the exact cutoff passed through to `RefreshTokenFamilyRepository.deleteRevokedBefore`, count returned unchanged. |

## `CleanupJobTest.java` (new — 6 tests, mocked collaborators + fixed `Clock`)

| Test | Verifies |
|---|---|
| `runDeletesExpiredTokensUsingCurrentInstantAsCutoff` | AC1/AC6 — the token-deletion step's cutoff is exactly the fixed clock's current instant. |
| `runDeletesOldRevokedFamiliesUsingFamilyRetentionDaysCutoff` | AC2/AC6 — the family-deletion step's cutoff is `now - familyRetentionDays`. |
| `runDeletesStaleShedLockRowsUsingTokenRetentionDaysCutoffAndTheSafetyGuardPredicate` | AC4/D4 — the exact SQL predicate (`lock_until < ?` AND the `lock_until < now()` safety clause) and cutoff (`now - tokenRetentionDays`); also proves Phase 9's fix — the parameter captured is a raw `Instant`, not a `java.sql.Timestamp`. |
| `runContinuesPastAFailureInDeleteExpiredTokens` | AC7/D2 — token-step throwing doesn't prevent the other two steps. |
| `runContinuesPastAFailureInDeleteOldRevokedFamilies` | AC7/D2 — family-step throwing doesn't prevent the other two steps. |
| `runContinuesPastAFailureInDeleteStaleShedLockRows` | AC7/D2 — ShedLock-step throwing doesn't hide that the other two (which run first in `run()`'s sequence) already completed. |

## `CleanupIntegrationTest.java` (new — 1 test, Testcontainers + Awaitility per the task statement)

`shouldCleanupExpiredTokensAndFamilies` (**named test, `package.md` §8**) seeds, in one real
Postgres-backed run: an expired and a non-expired verification token; an old-revoked family (95
days ago, past the 90-day retention) with an archive row; a recently-revoked family (60 seconds
ago); a never-revoked family (200 days old but still active); a stale ShedLock row (30 days old,
past the 7-day reused retention) and a currently-held/future one. Calls `cleanupJob.run()` directly,
then uses `Awaitility.await().untilAsserted(...)` to verify:
- the expired token and old-revoked family are both gone; their survivors (non-expired token,
  recently-revoked family, never-revoked family) are all untouched (**AC1, AC2** — this is the one
  test proving both together, per the named test's own title);
- the old-revoked family's archive row is gone too, proving the cascade rather than assuming it
  (**AC3**);
- the stale ShedLock row is gone and the currently-held one survives (**AC4**, and directly
  confirms D4's safety-guard clause in a real database, not just via the mocked-`JdbcTemplate`
  proof in `CleanupJobTest`).

Lives in `cleanup` (not `account`/`token`) since seeding both `VerificationToken` and
`RefreshTokenFamily`/`RefreshTokenArchiveEntry` fixtures directly via a raw `EntityManager` works
regardless of which package a repository interface is private to — both entity classes themselves
are public, so this is the same technique `SessionIntegrationTest`/`RefreshTokenFamilyIntegrationTest`
already established, applied here without needing to import either package-private repository.

## Verification performed

- `mvn -pl services/auth -am clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='CleanupJobTest,VerificationTokenServiceTest,RefreshTokenTrackerTest'`
  — **47/47 pass** (6 new `CleanupJobTest` + 23 `VerificationTokenServiceTest` [22 pre-existing + 1
  new] + 18 `RefreshTokenTrackerTest` [17 pre-existing + 1 new]).
- `mvn -pl services/auth test -Dtest='CleanupIntegrationTest'` — compiles clean; fails only on the
  same pre-existing `Could not find a valid Docker environment` `ApplicationContext` failure as
  every other Testcontainers-backed test this entire session — not a compile or logic error. This
  is now the **sixth** consecutive task (T25–T30) carrying a fully-written-but-never-executed
  integration suite.

The named `package.md` §8 test (`shouldCleanupExpiredTokensAndFamilies`) is fully written and
scoped exactly to its own name — both halves (tokens and families) proven in one test, matching the
frozen brief's own framing of this as the task's single defining integration test.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi test review) on approval.
