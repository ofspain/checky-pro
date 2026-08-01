# auth · T11 — Phase 8: Independent Code Review

Fresh, adversarial review of `LockoutStateMachine.java` (Phase 6) against the frozen brief
(`04-frozen-task-brief.md`), `requirements.md` R16–R19, `agents.md`, and the Phase 7
self-review.

---

## Finding 1 — Stale `lockedUntil` returned when a post-unlock failure does not re-lock

- **Issue.** The frozen brief Outputs section states `lockedUntil` must be **"null if not/no-longer
  locked."** In the non-locking `applyFailure` branch the implementation echoes
  `snapshot.lockedUntil()` unchanged. That branch is reachable only after the blocked check has
  established `now >= snapshot.lockedUntil()` whenever it was non-null; the account is therefore
  "no-longer locked," yet the returned decision still carries the expired timestamp.
- **Evidence.** `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java:80-82`:
  ```java
  return new LockoutDecision(
          failedAttempts, now, snapshot.lockedUntil(), snapshot.lockCount(),
          false, AccountStatusChange.NONE);
  ```
  This is exactly the AC7 scenario in which a failed attempt arrives well after `lockedUntil` and
  after `lastFailedAt + 30 min`: `decayed(...)` is true, `failedAttempts` resets to 1, no re-lock
  occurs, but the stale `lockedUntil` from the previous lock cycle is propagated.
- **Recommendation.** In the non-locking branch, return `null` for `lockedUntil` whenever
  `snapshot.lockedUntil()` was non-null. Keep `lockCount` unchanged — L4 only resets it on success
  (R18).
- **Confidence.** High.

---

## Finding 2 — No `UNLOCK` status signal when the unlock boundary is crossed by a failed attempt

- **Issue.** If Finding 1 is fixed so `lockedUntil` transitions non-null → null on a failed,
  non-relocking post-unlock attempt, the `AccountStatusChange` for that same transition is hardcoded
  to `NONE`. A downstream caller (T12/T14) that keys `Account.unlock()` off
  `statusChange == UNLOCK` would persist `lockout_state.locked_until = null` while leaving
  `Account.status = LOCKED` — an inconsistent persisted pair.
- **Evidence.** Same branch as Finding 1 (`LockoutStateMachine.java:79-83`) always constructs
  `AccountStatusChange.NONE`; contrast `applySuccess` (`LockoutStateMachine.java:92-96`), which
  correctly emits `UNLOCK` when `snapshot.lockedUntil() != null`.
- **Recommendation.** Resolve explicitly in Phase 9: either (a) emit `UNLOCK` alongside the
  `lockedUntil = null` change whenever a failure crosses the non-null → null boundary, or (b)
  document the accepted inconsistency if `NONE` is intentional. Do not leave the mismatch
  unexamined.
- **Confidence.** Medium. The frozen brief does not mandate a status change on a non-relocking
  post-unlock failure; R18 gates the `ACTIVE`/reset transition on success.

---

## Finding 3 — Required unit-test artifact is missing

- **Issue.** The task statement ("Unit-test boundaries") and the frozen brief §Required Tests
  mandate `LockoutStateMachineTest.java` covering the named tests, boundary cases, doubling,
  post-unlock re-lock/decay, `reset()`, and negative-input guards. The implementation file exists;
  no corresponding test artifact is present.
- **Evidence.** No file matches `services/auth/src/test/java/com/themistra/auth/authn/*LockoutStateMachine*Test*.java`.
  A repo-wide content search for `LockoutStateMachine` finds only the main source file.
- **Recommendation.** Add `services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java`
  before the PR is considered complete. It must exercise at minimum the cases listed in the frozen
  brief §Required Tests.
- **Confidence.** High.

---

## Finding 4 — No positivity validation for `maxAttempts`, `decayWindow`, or `baseLockDuration`

- **Issue.** A misconfigured `maxAttempts <= 0` makes every failure lock immediately; a negative
  `decayWindow` makes `decayed(...)` always true, preventing any lock; a negative `baseLockDuration`
  produces an immediate-expiry lock. The constructor performs only null checks.
- **Evidence.** `LockoutStateMachine.java:36-40`:
  ```java
  public LockoutStateMachine(int maxAttempts, Duration decayWindow, Duration baseLockDuration) {
      this.maxAttempts = maxAttempts;
      this.decayWindow = Objects.requireNonNull(decayWindow, "decayWindow must not be null");
      this.baseLockDuration = Objects.requireNonNull(baseLockDuration, "baseLockDuration must not be null");
  }
  ```
- **Recommendation.** Either validate here (`maxAttempts > 0`, `decayWindow > Duration.ZERO`,
  `baseLockDuration > Duration.ZERO`) or confirm and document that T12's validated
  `@ConfigurationProperties` (`LockoutProperties`) is the sole enforcement point. If validation is
  deferred to T12, record the assumption explicitly so a future caller cannot instantiate the
  machine directly with bad constants.
- **Confidence.** Medium (self-review already surfaced this; this is a confirmation plus a request
  for an explicit assumption record).

---

## Finding 5 — Extreme `lockCount` shifts can wrap to a negative multiplier

- **Issue.** `effectiveLockDuration` uses `1L << lockCountBeforeThisLock`. For
  `lockCountBeforeThisLock >= 63`, the shift wraps to a negative `long`. Multiplying a positive
  `Duration` by a negative value yields a negative duration, so `now.plus(...)` sets `lockedUntil`
  in the past — an effectively instant-unlock rather than an ever-escalating lock. For
  `lockCountBeforeThisLock == 62`, the positive multiplier is so large that `Duration.multipliedBy`
  will likely throw `ArithmeticException` rather than overflow cleanly.
- **Evidence.** `LockoutStateMachine.java:103-105`.
- **Recommendation.** No code change required — L4 explicitly specifies no cap, and the frozen
  brief Finding 10 disposition accepts this as a documented, theoretical limit. Consider adding a
  one-line code comment at this method referencing that accepted disposition so future maintainers
  do not treat it as an oversight.
- **Confidence.** Low / informational.
