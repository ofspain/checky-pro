# auth · T11 — Phase 12: Specification Verification

Verifying the final implementation (`LockoutStateMachine.java`, Phase 6/9) and test suite
(`LockoutStateMachineTest.java`, Phase 10/11) against `spec/auth-service/requirements.md`,
`design.md`, and `tasks.md` for T11 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R16** — failed login for an `ACTIVE` account increments the failed-attempt counter | Yes | `LockoutStateMachine.java:92-108` (`applyFailure`) — increments `failedAttempts`, sets `lastFailedAt = now` | `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (loop, lines 31-42), `fourthFailureWithinWindowDoesNotLock` (:75-84) | No | No — audit emission (the other half of R16) is explicitly T13's scope, not this class's; frozen brief Outputs section documents this class only decides the counter value |
| **R17** — 5th failure within the window locks for the base duration, increments `lockCount` | Yes | `LockoutStateMachine.java:98-108` (locking branch) | `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (:44-53), `fifthFailureExactlyAtThirtyMinuteBoundaryStillLocksWithoutPrematureDecay` (:87-97), `failureOneSecondAfterPriorFailureDoesNotPrematurelyDecay` (:113-122) | No | No — audit emission (`account.locked`) is T13's scope |
| **R18** — once the lockout interval elapses, the next attempt is permitted; success resets the counter and `lockCount` | Yes | `LockoutStateMachine.java:64-71` (blocked check, permits at/after `lockedUntil`), `:110-114` (`applySuccess`, zeroes everything) | `successAtOrAfterLockedUntilIsPermittedAndResetsCountersWithUnlockSignal` (:149-161), `attemptStrictlyAfterLockedUntilIsPermittedForBothOutcomes` (:177-194, success half) | No | No |
| **R19** — counter decays to 0 if 30 minutes pass since the last failure without reaching 5 | Yes | `LockoutStateMachine.java:116-119` (`decayed`), `:93` (applied before incrementing) | `failureJustPastThirtyMinuteBoundaryDecaysInsteadOfLocking` (:100-110), `failedAttemptWellAfterLockExpiryDecaysAndSignalsUnlockWithoutRelocking` (:223-236) | No | No |
| **L4** — 5/30-min/15-min, doubling via `lockCount` until reset on a successful post-lock login | Yes | `LockoutStateMachine.java:41-56` (constructor takes the three constants), `:121-125` (`effectiveLockDuration`, `baseLockDuration * 2^lockCount`) | `secondLockDoublesDurationToThirtyMinutesAndIncrementsLockCount` (:125-134), `thirdLockDoublesDurationToSixtyMinutesAndIncrementsLockCount` (:137-146) | No | No — no cap, matching L4's explicit "no cap" (Phase 4 Finding 10 disposition, documented at `LockoutStateMachine.java:122-123`) |

**Named tests (`package.md` §8):**
- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` — exists verbatim
  (`LockoutStateMachineTest.java:28`), maps to R17 in `requirements.md` (package.md itself says R15
  — a pre-existing numbering drift, confirmed and left unfixed per frozen brief Finding 9, same
  category as T09's Finding 7).
- `shouldResetLockoutCounterOnSuccessfulLogin` — exists verbatim (`LockoutStateMachineTest.java:61`),
  maps to R18 in `requirements.md` (package.md says R16 — same drift category). Covers R18's AC6
  "never locked" variant; the literal "was locked, then succeeds" variant of R18 is covered by the
  separate `successAtOrAfterLockedUntilIsPermittedAndResetsCountersWithUnlockSignal`, explicitly
  cross-referenced in the named test's Javadoc (:55-59) so the mapping isn't implicit — raised at
  Phase 11 Gap 3, resolved by documentation rather than restructuring the named test.

---

## Additional behavior beyond the four scoped requirements (frozen-brief-approved)

Two human-approved decisions extend past R16-R19's literal text; both are fully tested:

- **Post-lock escalation (frozen brief Finding 2, human-approved).** `failedAttempts` is not reset
  by locking itself, so a failure landing within `decayWindow` of the lock-triggering failure
  re-locks immediately with `lockCount` doubled again. Tested by
  `failedAttemptImmediatelyAfterLockExpiryReLocksWithDoubledDuration` (:206-220) and the failure
  half of `attemptStrictlyAfterLockedUntilIsPermittedForBothOutcomes` (:188-193).
- **`UNLOCK` signal on a non-relocking failure (Phase 9 Finding 2, human-approved).** Whenever a
  call clears a previously non-null `lockedUntil` to `null` — success or a non-relocking failure —
  `statusChange = UNLOCK`, keeping the caller's future `Account.status` in sync with
  `lockout_state.locked_until`. Tested by
  `failedAttemptWellAfterLockExpiryDecaysAndSignalsUnlockWithoutRelocking` (:223-236) and the
  success half of `attemptStrictlyAfterLockedUntilIsPermittedForBothOutcomes` (:182-186).

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. `LockoutStateMachine` is implemented exactly to the frozen
brief's scope (pure logic, no persistence, no Spring wiring), and
`LockoutStateMachineTest.java` covers both named tests plus every boundary, doubling cycle, and
state transition the frozen brief's Required Tests section lists — 22 tests, all passing (verified
by direct execution via the JUnit Platform Launcher this phase, not just compiled: `22 tests found,
22 tests successful, 0 failed`; module-wide `mvn -pl services/auth verify` still cannot run
end-to-end due to the pre-existing, unrelated `token` package compile break tracked since T03,
unaffected by and unrelated to this task).

**(2) Does it satisfy every acceptance criterion?** All nine (AC1-AC9) from the frozen brief are
implemented and directly tested — see the traceability matrix and the "additional behavior"
section above for the two ACs (AC7 and the Phase-9-added unlock signal) that extend past the
original four requirement IDs. AC1-AC6, AC8, AC9 map 1:1 to specific tests as listed in
`10-test-generation.md`'s manifest; AC7 has three dedicated tests covering both its re-lock and
decay-and-unlock branches, plus the strictly-after-boundary variant added at Phase 11.

**(3) Does it violate any LOCKED decision?** No. L4's 5/30/15 + doubling formula is implemented
exactly as adopted at Phase 2/4/5 (`effectiveDurationMinutes = baseLockMinutes *
2^lockCountBeforeThisLock`, no cap). L12's module boundary is intact — confirmed this phase via a
fresh `grep` for any `com.themistra.auth.account` import in `LockoutStateMachine.java`: no match.

**(4) Remaining risks:**
- Module-wide `mvn -pl services/auth verify` still cannot run to completion — the same
  pre-existing, unrelated compile break tracked since T03. Every test in this task was verified via
  isolated `javac` + JUnit Platform Launcher instead (22/22 passing, this phase's own run).
- `failedAttempts` grows unboundedly across repeated immediate re-lock cycles (6, 7, 8...) rather
  than being capped or re-set to the threshold value each time — raised at Phase 7 Finding (informal,
  not escalated to a numbered brief finding) as an observation, not a defect: no acceptance
  criterion or requirement constrains this value's upper bound, and the field is otherwise
  self-limiting in practice (doubling durations eventually exceed `decayWindow`, causing decay to
  take over — demonstrated by `failedAttemptWellAfterLockExpiryDecaysAndSignalsUnlockWithoutRelocking`).
  Documented here as an accepted residual, not silently ignored.
- `LockoutSnapshot` has no cross-field consistency invariant (e.g., `lockCount > 0` with
  `lockedUntil == null`) — explicitly out of scope per the frozen brief's Finding 10 disposition
  (only `failedAttempts >= 0`/`lockCount >= 0` are enforced). A caller (T12) constructing an
  inconsistent snapshot from corrupted data would not be rejected by this class; T12's own
  persistence-layer invariants are the intended enforcement point.
- The `package.md` §8 named-test-to-requirement-ID numbering drift (R15/R16 vs. actual R17/R18) is
  a pre-existing, three-times-affirmed-across-tasks issue (T09 Finding 7, this task's Finding 9),
  not a T11 defect — `spec/` is never modified per the guardrails.

---

## Verdict

**PASS** — `LockoutStateMachine` implements R16-R19/L4 exactly as frozen, all nine acceptance
criteria are met and tested, no LOCKED decision is violated, module boundaries are clean, and the
22-test suite (strengthened twice — Phase 9's bug-fix coverage, Phase 11's six additional
boundary/assertion gaps) passes in full under direct execution.
