# auth · T11 — Phase 10: Test Generation

Test manifest for `LockoutStateMachineTest.java` (new), against the frozen brief
(`04-frozen-task-brief.md`) and the Phase 9-resolved implementation. No production code touched.
Plain JUnit 5 + AssertJ, no Mockito (no collaborators to mock), no Spring context — matching
`agents.md`'s unit-test convention and `PasswordPolicyTest`'s precedent. All timestamps are fixed
offsets from one base `Instant` (`T0`) — no `Instant.now()` anywhere in the file.

## File

`services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java` — 18 test
methods, one shared `machine = new LockoutStateMachine(5, Duration.ofMinutes(30),
Duration.ofMinutes(15))` field (matches L4's constants exactly).

## Test → requirement / acceptance-criterion mapping

| Test | Maps to | What it proves |
|---|---|---|
| `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` | Named test (package.md §8, actually R17 per Finding 9) / AC1 | 4 sequential failures never lock; the 5th locks with `lockedUntil = now+15min`, `lockCount=1`, `statusChange=LOCK`, `failedAttempts=5` |
| `shouldResetLockoutCounterOnSuccessfulLogin` | Named test (actually R18 per Finding 9) / AC6 | Success with `failedAttempts=3`, never locked (`lockedUntil=null`), zeroes everything including `lastFailedAt`; `statusChange=NONE` (never locked, so no unlock signal expected) |
| `fourthFailureWithinWindowDoesNotLock` | AC5 | 4th failure: `failedAttempts=4`, no lock, `statusChange=NONE` |
| `fifthFailureExactlyAtThirtyMinuteBoundaryStillLocksWithoutPrematureDecay` | AC4/AC5 | Elapsed exactly 30:00 since the prior failure does not decay — still locks on the 5th |
| `failureJustPastThirtyMinuteBoundaryDecaysInsteadOfLocking` | AC4 | Elapsed 30:00:01 decays first; the new failure becomes attempt 1, not attempt 5 |
| `secondLockDoublesDurationToThirtyMinutesAndIncrementsLockCount` | AC2 | Locking with `lockCount=1` beforehand produces a 30-min lock, `lockCount→2` |
| `thirdLockDoublesDurationToSixtyMinutesAndIncrementsLockCount` | AC2 | Locking with `lockCount=2` beforehand produces a 60-min lock, `lockCount→3` — proves doubling continues past the first cycle |
| `successAtOrAfterLockedUntilIsPermittedAndResetsCountersWithUnlockSignal` | AC3, Phase 9 Finding 2 | `now == lockedUntil` is permitted (not blocked); success resets all counters and emits `UNLOCK` (was locked) |
| `attemptOneInstantBeforeLockedUntilIsBlockedRegardlessOfOutcome` | AC3, Finding 5/12 | `now == lockedUntil - 1ms` is blocked for **both** a `FAILURE` and a `SUCCESS` outcome; every field of the returned decision equals the input snapshot unchanged in both cases |
| `failedAttemptImmediatelyAfterLockExpiryReLocksWithDoubledDuration` | AC7 (human-approved, Phase 4 Finding 2) | A failure landing exactly at `lockedUntil` (15 min after the locking failure, within the 30-min decay window) is not blocked, is not decayed, and re-locks immediately with `lockCount` doubled again (`2`) — the confirmed escalating-lockout behavior |
| `failedAttemptWellAfterLockExpiryDecaysAndSignalsUnlockWithoutRelocking` | AC7, Phase 9 Finding 1/2 | A failure 46 minutes after the locking failure (past the decay window) decays first (`failedAttempts→1`), does not re-lock, `lockedUntil` clears to `null` (not the stale timestamp), and `statusChange=UNLOCK` — this is the exact scenario the Phase 9 bug fix targets |
| `resetAlwaysReturnsZeroedDecisionWithUnlockSignal` | AC8 | `reset()` is unconditional — zeroed shape, `statusChange=UNLOCK` |
| `negativeFailedAttemptsInSnapshotThrows` | AC9 | `LockoutSnapshot(-1, ...)` throws `IllegalArgumentException` |
| `negativeLockCountInSnapshotThrows` | AC9 | `LockoutSnapshot(0, null, null, -1)` throws `IllegalArgumentException` |
| `evaluateRejectsNullSnapshotNowOrOutcome` | Constraint (null-handling) | Three cases — null `snapshot`, `now`, `outcome` — each throws `NullPointerException` |
| `constructorRejectsNonPositiveMaxAttempts` | Phase 9 Finding 4 (accepted) | `maxAttempts` of `0` and `-1` both throw `IllegalArgumentException` |
| `constructorRejectsNonPositiveDecayWindow` | Phase 9 Finding 4 (accepted) | `decayWindow` of `Duration.ZERO` and a negative duration both throw |
| `constructorRejectsNonPositiveBaseLockDuration` | Phase 9 Finding 4 (accepted) | `baseLockDuration` of `Duration.ZERO` and a negative duration both throw |

## Coverage against the frozen brief's "Required Tests" list

Every bullet in `04-frozen-task-brief.md`'s Required Tests section is covered 1:1 by a test above:
both named tests; the 4th-failure boundary; the exactly-30:00 boundary; the just-past-30:00 decay;
both doubling cycles; the at/one-instant-before `lockedUntil` pair; the reset-without-prior-lock
case (folded into `shouldResetLockoutCounterOnSuccessfulLogin`, which already uses
`lockedUntil=null`); the two new AC7 cases (re-lock and decay-and-unlock); `reset()`; and the
negative-input guards. The constructor-validation tests are additive, covering the Phase 9
Finding 4 fix that postdates the Phase 5 plan.

## Build verification

Compiled and **executed** (not just compiled) against the module's resolved test classpath, using
the JUnit Platform Launcher directly (the module-wide `mvn test` still cannot run to completion due
to the pre-existing, unrelated `token` package compile break):

```
javac -d <out> -cp "$(cat /tmp/auth-cp.txt)" \
  services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java \
  services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java
```

Then launched via `org.junit.platform.launcher.core.LauncherFactory` selecting the test class.
Result:

```
18 tests found, 18 tests successful, 0 failed
```

## Specification references

- Task: `spec/auth-service/tasks.md`, task 11 ("Unit-test boundaries").
- Requirements: R16, R17, R18, R19.
- LOCKED decisions: L4.
- Frozen brief: `04-frozen-task-brief.md` — Required Tests section, AC1-AC9.
- Review resolution: `09-review-resolution.md` — Findings 1, 2, 4 (all three now have dedicated
  test coverage: the unlock-signal fix, the stale-`lockedUntil` fix, and constructor validation).
