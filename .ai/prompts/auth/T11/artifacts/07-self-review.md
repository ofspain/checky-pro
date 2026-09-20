# auth · T11 — Phase 7: Self Review

Reviews `LockoutStateMachine.java` (Phase 6) against the frozen brief (`04-frozen-task-brief.md`)
and `agents.md`. No rewrite performed — findings only; fixes are Phase 9's job.

---

## Finding 1 — `lockedUntil` is not nulled when a non-relocking failure crosses the unlock boundary

- **Issue:** The frozen brief's Outputs section (carried over from Phase 2, unmodified) documents
  the contract explicitly: `lockedUntil: Instant | null` (post-transition; **null if
  not/no-longer locked**). `applyFailure`'s non-locking return branch instead echoes
  `snapshot.lockedUntil()` straight through, unchanged.
- **Severity:** High
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java:80-82`
  ```java
  return new LockoutDecision(
          failedAttempts, now, snapshot.lockedUntil(), snapshot.lockCount(),
          false, AccountStatusChange.NONE);
  ```
  This branch is only reachable after `evaluate` (line 53) has already established `now >=
  snapshot.lockedUntil()` whenever `snapshot.lockedUntil()` was non-null (otherwise `blockedNoOp`
  would have returned instead). So any time this line runs with a non-null incoming
  `lockedUntil`, the account is — by the brief's own definition — "no-longer locked," yet the
  returned decision reports a stale, already-expired `lockedUntil` timestamp instead of `null`.
  Concretely: this is exactly the AC7 scenario when it resolves via decay rather than re-lock —
  e.g. a failed attempt lands well after `lockedUntil` (>30 min since `lastFailedAt`, decays
  per `decayed(...)` at line 98-101, `failedAttempts` resets to 1) but the stale `lockedUntil`
  from the *previous* lock cycle rides along in the output untouched.
- **Recommendation:** In the non-locking branch, return `null` for `lockedUntil` whenever
  `snapshot.lockedUntil()` was non-null (i.e., we're crossing the boundary from locked to
  not-locked on this call); keep it as `snapshot.lockedUntil()` (already `null`) when it was
  already `null`. `lockCount` is unaffected — L4 only resets `lockCount` on success (R18), so
  preserving it here through decay is correct and should not change.

---

## Finding 2 — Crossing the unlock boundary via a failed (non-relocking) attempt never signals `UNLOCK`

- **Issue:** Finding 1's fix would make `lockedUntil` go non-null → null on a plain failed
  attempt, but `AccountStatusChange` for that same branch is hardcoded to `NONE` — never `UNLOCK`.
  If T12 keys `Account.unlock()` off `statusChange == UNLOCK`, `Account.status` would stay
  `LOCKED` in the database even after `lockout_state.locked_until` is cleared to `null` —
  an inconsistent pair a caller could persist.
- **Severity:** Medium
- **Evidence:** `LockoutStateMachine.java:79-83` (the same branch as Finding 1) always constructs
  `AccountStatusChange.NONE`, contrasted with `applySuccess` (line 92-96) which correctly emits
  `UNLOCK` when `snapshot.lockedUntil() != null`.
- **Recommendation:** Not a code fix in isolation — this is a genuine design question the frozen
  brief doesn't resolve: R18 only mandates the `ACTIVE`/reset transition on a *successful*
  post-unlock attempt; it's silent on whether a *failed*, non-relocking post-unlock attempt should
  also flip `Account.status` back to `ACTIVE` (so R16's counting can resume against an `ACTIVE`
  account) or leave it `LOCKED` despite `locked_until` being cleared. Recommend Phase 9 either (a)
  emit `UNLOCK` alongside the Finding 1 fix whenever `lockedUntil` transitions non-null → null
  through a failure, or (b) explicitly document the accepted inconsistency if `NONE` is kept
  on purpose. Do not resolve silently either way.

---

## Finding 3 — Constructor does not validate `maxAttempts`/`decayWindow`/`baseLockDuration`

- **Issue:** `LockoutSnapshot`'s compact constructor validates `failedAttempts >=
  0`/`lockCount >= 0` (AC9), but `LockoutStateMachine`'s own constructor performs no equivalent
  check on its three rule constants. A misconfigured `maxAttempts <= 0` would make
  `applyFailure`'s `failedAttempts < maxAttempts` (line 79) false on the very first failure,
  locking the account immediately with no defensive error signaling the misconfiguration.
- **Severity:** Low
- **Evidence:** `LockoutStateMachine.java:36-40` — only null-checks `decayWindow`/
  `baseLockDuration`; no positivity check on any of the three parameters.
- **Recommendation:** The frozen brief's Finding 10/AC9 scoped the invariant check to
  `LockoutSnapshot` only, and the plan notes T12 will source these three values from validated
  `@ConfigurationProperties` (`LockoutProperties`), which is the more likely enforcement point.
  Flagging for confirmation rather than assuming a fix is needed here — if T12's config validation
  is trusted to guarantee positive values, this can be closed as no-action.

---

## Finding 4 — `effectiveLockDuration`'s shift is unbounded (informational, already accepted)

- **Issue:** `baseLockDuration.multipliedBy(1L << lockCountBeforeThisLock)` is well-defined for
  `lockCountBeforeThisLock` in `[0, 63]` but Java masks shift distances ≥ 64 to `distance mod 64`
  for a `long` shift, so at extreme `lockCount` the multiplier would wrap unpredictably rather
  than continuing to grow or overflow cleanly.
- **Severity:** Low / informational
- **Evidence:** `LockoutStateMachine.java:103-105`.
- **Recommendation:** No action expected — this is the exact theoretical limit the frozen brief's
  Finding 10 disposition already reviewed and explicitly accepted ("L4 states no cap... a
  documented, accepted, theoretical limit, not a defect to fix"). Noted here only for
  file:line traceability in case Phase 9 wants to add a code comment pointing back to that
  disposition.

---

## Non-findings (verified clean)

- **Thread-safety:** all fields `final`, no mutable state, no side effects — confirmed stateless
  per the frozen brief's Constraints.
- **Module boundary (L12):** no import of `Account`/`AccountStatus` or anything from `account`;
  only `java.time`/`java.util` — confirmed via imports at lines 3-5.
- **Determinism:** no `Instant.now()`, no randomness anywhere in the class — `now` is always the
  caller-supplied parameter.
- **Security/logging:** no `Logger`, no field logged or printed anywhere — matches the frozen
  brief's "no logging of failure counts/timestamps... no logger at all."
- **Boundary arithmetic (AC1-AC5):** decay comparison (`decayed`, lines 98-101) is strictly
  `> decayWindow`, matching the documented inclusive-at-30:00 convention; lock threshold check
  (`failedAttempts < maxAttempts`, line 79) correctly locks at exactly the 5th failure, not the
  4th. No off-by-one found.
- **AC9 (negative-input guard):** `LockoutSnapshot`'s compact constructor throws
  `IllegalArgumentException` for both `failedAttempts < 0` and `lockCount < 0` — matches spec.
- **`reset()` (AC8):** unconditional, correct zeroed shape, `UNLOCK` status change — matches spec.
- **Money/transactions:** not applicable to this class (no persistence, no monetary types).

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — Outputs section (Finding 1's contract text), AC7
  (Finding 1/2's re-lock scenario), Finding 10/AC9 disposition (Finding 3/4's context).
- `agents.md`: module boundaries (L12), no-`Date`/caller-supplied-time convention, security
  logging rule.
