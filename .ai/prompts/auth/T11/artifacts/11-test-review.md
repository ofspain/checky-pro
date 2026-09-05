# auth · T11 — Phase 11: Test Review

Reviewed `services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java` and the Phase 10 test manifest against the frozen brief (`04-frozen-task-brief.md`) acceptance criteria, named tests, and `agents.md` unit-test conventions.

---

## Gap 1 — `lastFailedAt` is not asserted on intermediate failures in the five-failure named test

- **Why it matters.** The frozen brief Finding 1 amendment explicitly requires `lastFailedAt` to be set to `now` on every failed attempt. `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` rebuilds the snapshot from `decision.lastFailedAt()` but never asserts it, so the test could pass even if the implementation left `lastFailedAt` null or stale.
- **Suggested test.** In the first-four-failures loop, add `assertThat(decision.lastFailedAt()).isEqualTo(now)`. Also assert `fifth.lastFailedAt().isEqualTo(fifthFailureAt)` after the 5th failure.

---

## Gap 2 — `lockCount` is not asserted on intermediate failures in the five-failure named test

- **Why it matters.** L4 says `lockCount` increments only on lock and resets only on success. A regression that incremented `lockCount` on every failure would not be caught by the current loop assertions, which check `failedAttempts`, `blocked`, `lockedUntil`, and `statusChange` but not `lockCount`.
- **Suggested test.** Add `assertThat(decision.lockCount()).isZero()` inside the first-four loop of `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` since the snapshot starts at 0 and no lock has occurred yet.

---

## Gap 3 — The named reset test does not verify the R18 post-unlock success scenario

- **Why it matters.** `package.md` maps `shouldResetLockoutCounterOnSuccessfulLogin` to R18, whose literal text describes a locked account whose lockout interval has elapsed and then succeeds. The current implementation of the named test starts with `lockedUntil=null`, so it only covers the AC6 extension (reset without prior lock). The R18 scenario is exercised by `successAtOrAfterLockedUntilIsPermittedAndResetsCountersWithUnlockSignal`, but it is not under the named test banner that downstream traceability expects.
- **Suggested test.** Convert the named test into two focused cases, or add a sibling named test starting from a locked snapshot (`failedAttempts=5`, `lockedUntil` in the past, `lockCount=1`) and asserting `UNLOCK` plus all counters cleared on a successful post-expiry attempt.

---

## Gap 4 — No test for `now` strictly after `lockedUntil`

- **Why it matters.** AC3 says attempts are permitted "at or after `lockedUntil`." All current tests that hit this boundary use exactly `lockedUntil`. A strict-comparison regression (e.g., treating `now == lockedUntil` as still blocked) could be masked; a strictly-after case removes that ambiguity.
- **Suggested test.** Add a test with `now = lockedUntil.plusMillis(1)` for both `SUCCESS` and `FAILURE`, asserting the same non-blocked outcome as the equality case (reset/UNLOCK for success, re-lock or decay/UNLOCK for failure depending on elapsed time).

---

## Gap 5 — Constructor null checks are not exercised

- **Why it matters.** The Phase 9 constructor validation added null checks for `decayWindow` and `baseLockDuration` (and positivity checks already covered). A future refactor that accidentally dropped the null checks would not fail the current unit suite.
- **Suggested test.** Add two assertions in the constructor-validation test verifying `new LockoutStateMachine(5, null, Duration.ofMinutes(15))` and `new LockoutStateMachine(5, Duration.ofMinutes(30), null)` both throw `NullPointerException`.

---

## Gap 6 — No test exercises a failure immediately after a prior non-locking failure

- **Why it matters.** The decay rule is triggered by elapsed time since `lastFailedAt`. A test where two failures occur in rapid succession (e.g., `now = lastFailedAt.plusSeconds(1)`) would guard against an accidental `>=` or `lastFailedAt`-not-updated bug on the very first non-locking transition. The named test covers this implicitly across its loop, but does not assert the boundary at the smallest possible elapsed time.
- **Suggested test.** Add a focused test: snapshot `failedAttempts=4`, `lastFailedAt=now.minusSeconds(1)`, evaluate `FAILURE` at `now`, assert `failedAttempts=5` and `lockedUntil = now + 15min`.
