# auth · T12 — Phase 10: Test Generation

Test manifest against the frozen brief (`04-frozen-task-brief.md`) and the Phase 9-resolved
implementation. No production code touched. Plain JUnit 5 + AssertJ + Mockito for units (no
Spring context), Testcontainers + `@SpringBootTest` for the one persistence-proving integration
test — matching `agents.md`'s convention and this module's own established precedents
(`PasswordPolicyTest`/`PasswordPolicyPropertiesTest` for the unit shapes,
`AccountPersistenceIntegrationTest` for the integration shape).

## Files

- `services/auth/src/test/java/com/themistra/auth/authn/LockoutServiceTest.java` (new, 17 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutPropertiesTest.java` (new, 4 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java`
  (new, 4 tests)
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified — 4
  new tests for `lock`/`unlock`)

## Test → requirement / acceptance-criterion mapping

**`LockoutServiceTest.java`:**

| Test | Maps to | What it proves |
|---|---|---|
| `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` | Named test / AC2 | 5th failure persists a `LOCKED`-shaped row and calls `accountService.lock(...)` exactly once, never `unlock` |
| `shouldResetLockoutCounterOnSuccessfulLogin` | Named test / AC3 | A success on a previously-locked row persists the zeroed row and calls `accountService.unlock(...)` exactly once |
| `missingRowOnFailureCreatesNewRowViaResolvedAccountId` | AC1 | First-ever failure resolves the internal id and inserts a new row with `failedAttempts=1` |
| `missingRowOnFailureForNonexistentAccountIsANoOp` | Phase 9 Finding B | A failed id resolution no-ops (no save, no `AccountService` call) instead of throwing — directly exercises the Phase 9 fix |
| `missingRowOnSuccessIsANoOp` | AC8 | Success on a missing row never calls `findAccountIdByUuid`, never saves, never touches `AccountService` |
| `blockedAttemptWritesNothingAndCallsNothing` | AC7 | A blocked decision results in zero repository/`AccountService` interaction |
| `reLockWhileAccountStatusStillLockedDoesNotThrowAndStillLocksAgain` | AC2, T11 AC7 | A failure evaluated exactly at `lockedUntil` doesn't throw and re-locks with doubled duration — the scenario Finding 2's guard exists for |
| `resetLockoutZeroesAnExistingLockedRowAndUnlocks` | AC9 | `resetLockout` zeroes an existing row and calls `unlock` |
| `resetLockoutOnAlreadyCleanAccountIsHarmless` | AC9 | `resetLockout` on a missing row skips persistence but still (safely) calls `unlock` |
| `recordFailedAttemptRejectsNullAccountUuidOrNow` / `recordSuccessfulAttemptRejectsNullAccountUuidOrNow` / `resetLockoutRejectsNullAccountUuid` | Constraint (null-handling) | `Objects.requireNonNull` fires on every entry point |

**`LockoutPropertiesTest.java`:**

| Test | Maps to |
|---|---|
| `shouldBeValidWithL4Defaults` | AC5/L4 — the actual `application.properties` values (5/30/15) pass validation |
| `shouldRejectNonPositiveMaxAttempts` / `WindowMinutes` / `BaseLockMinutes` | Finding 9 — each `@Min(1)` fires independently |

**`LockoutPersistenceIntegrationTest.java`** (Testcontainers, real Postgres):

| Test | Maps to |
|---|---|
| `findAccountIdByUuidResolvesTheRealInternalId` | Finding 1 — the native scalar query works against real schema |
| `findByAccountUuidForUpdateReturnsEmptyForANeverFailedAccount` | AC1 — confirms the "missing row" precondition is real, not assumed |
| `fiveFailuresLockARealAccountAndPersistARealRow` | AC2, end-to-end — real `LockoutService` → real `AccountService.lock` → real `Account.status` read back as `LOCKED`, real persisted row |
| `successfulAttemptUnlocksARealLockedAccountAndClearsTheRealRow` | AC3, end-to-end — mirrors the above for the unlock path |

**`AccountServiceTest.java`** (new tests only):

| Test | Maps to |
|---|---|
| `lockTransitionsActiveToLocked` | Finding 2 — the happy path |
| `lockNoOpsWhenAccountIsNotActive` | Finding 2 — the guard itself: locking an already-`LOCKED` account doesn't throw `InvalidAccountStateException`, the exact bug Finding 2 fixed |
| `unlockTransitionsLockedToActive` | Finding 2 — the happy path |
| `unlockNoOpsWhenAccountIsNotLocked` | Finding 2 — the symmetric guard |

## Coverage against the frozen brief's Required Tests list

Every bullet is covered: both named tests; missing-row on failure and success; blocked-attempt
boundary; the re-lock boundary (Finding 2/5); `resetLockout` on both a locked and a clean account;
`LockoutProperties` binding and failure modes; and the Testcontainers-backed native-query
round-trip. The Phase 9 resolution's own new behavior (no-throw-on-nonexistent-account,
narrower lock scope) each get a dedicated test not present in the original Phase 5 plan.

## Build verification

Compiled and **executed** (not just compiled) via the JUnit Platform Launcher against the
module's resolved test classpath — `mvn -pl services/auth test` still cannot run to completion
due to the pre-existing, unrelated `token` package compile break (tracked since T03):

```
javac -d <out> -cp "$(cat /tmp/auth-cp.txt)" -sourcepath services/auth/src/main/java:services/auth/src/test/java \
  services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java \
  services/auth/src/test/java/com/themistra/auth/authn/LockoutServiceTest.java \
  services/auth/src/test/java/com/themistra/auth/authn/LockoutPropertiesTest.java \
  services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java
```

Clean compile, all four files. `AccountServiceTest`, `LockoutServiceTest`, and
`LockoutPropertiesTest` (58 tests total — the full, updated `AccountServiceTest` suite plus the
two new pure-unit files) were then **executed** via the JUnit Platform Launcher:

```
58 tests found, 58 tests successful, 0 failed
```

`LockoutPersistenceIntegrationTest`'s 4 tests compile clean but **could not be executed in this
environment** — `docker info` fails here (no Docker daemon available), and Testcontainers requires
one. This is a real, honestly-reported gap: the native-query and end-to-end behavior these tests
target has been verified by careful manual reasoning (Phase 6/7/8/9) and by the mocked
`LockoutServiceTest` suite, but not by an actual run against real Postgres. Flagged as a residual
verification risk for whoever next has Docker access, not silently claimed as passing.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 12 (`"Add LockoutService... loads/updates
  lockout_state, handles decay, and ties Account.lock()/unlock() to AccountService"`).
- Requirements: R16, R17, R18, R19.
- LOCKED decisions: L4, L12.
- Frozen brief: `04-frozen-task-brief.md` — AC1-AC10, Required Tests.
- Review resolution: `09-review-resolution.md` — Findings A (lock scope), B (existence-check
  consistency), C (documented invariant) all now have direct test coverage.
