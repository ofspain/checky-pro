# auth · T11 · Phase 2 — Task Implementation Brief

## Task

Implement `LockoutStateMachine` — a pure-logic, unit-testable class encoding the brute-force
lockout rules (5 failed attempts / 30-minute rolling window / 15-minute base lock, with doubling
on repeat locks). Unit-test the boundaries.

## Purpose

Provide the decision logic that later tasks (T12 `LockoutService`, T13 SAS auth-path wiring, T14
admin unlock) will call to determine whether an authentication failure locks an account, whether
a locked account may attempt again, and how the failed-attempt counter decays. T11 delivers only
the decision function — no persistence, no `Account` mutation, no audit, no Spring wiring.

## Scope

**In:**
- `LockoutStateMachine` pure-logic class encoding R16–R19 / L4.
- A small immutable snapshot/result type set for its inputs and outputs.
- Unit tests covering the named tests plus boundary cases.

**Out:**
- `LockoutState` JPA entity, repository, `lockout_state` persistence (T12).
- `LockoutService`, `Account.lock()`/`unlock()` wiring (T12).
- SAS authentication success/failure path integration, `login.failed`/`account.locked` audit
  emission (T13).
- Admin unlock endpoint (T14).
- Any `@ConfigurationProperties` bean or `application.properties` keys — the machine takes its
  three rule constants via a plain constructor parameter, not Spring config (config wiring is
  T12's concern, once a real `LockoutService` bean exists to inject it into).

## Business Rules

- **R16.** A failed login attempt for an `ACTIVE`-eligible account increments the failed-attempt
  counter. (Audit emission is T13; T11 only decides the new counter value.)
- **R17.** Reaching 5 failed attempts within the rolling 30-minute window transitions to locked,
  sets the lock expiry to `now + effective-duration`, and increments `lock_count`.
- **R18.** Once `locked_until` has passed, the next attempt is permitted; on success, both
  `failed_attempts` and `lock_count` reset to 0.
- **R19.** If 30 minutes have elapsed since `last_failed_at` without reaching 5 failures, the
  counter decays to 0 before the new attempt is applied.

## Locked Decisions

- **L4.** 5 failed attempts / 30-minute rolling window / 15-minute base lock. Each subsequent
  lock doubles the effective duration via `lock_count`, until `lock_count` resets (on a
  successful post-lock login, per R18). Adopted formula (implementer decision, since L4 specifies
  no formula): `effectiveDurationMinutes = baseLockMinutes * 2^lockCountBeforeThisLock`, i.e. the
  first lock uses `lock_count=0` → 15 min; the second uses `lock_count=1` → 30 min; the third
  uses `lock_count=2` → 60 min. No cap — L4 states none. `lock_count` increments by exactly 1
  per lock event, applied after the duration for *this* lock is computed.

## Dependencies

None — no Spring beans, no repositories, no other `authn`/`account` classes as collaborators.
Depends only on `java.time` (`Instant`, `Duration`).

## Inputs

A snapshot of current lockout state plus the moment of evaluation:
- `failedAttempts: int`
- `lastFailedAt: Instant | null` (null if never failed)
- `lockedUntil: Instant | null` (null if not currently locked)
- `lockCount: int`
- `now: Instant`

Plus, per call, which transition is being evaluated: a failed attempt, or a successful attempt.

## Outputs

A decision/result describing the next state:
- `failedAttempts: int` (post-transition)
- `lockedUntil: Instant | null` (post-transition; null if not/no-longer locked)
- `lockCount: int` (post-transition)
- Whether the account should now be locked (i.e., whether `Account.lock()` should be called by
  the caller) vs. should now be unlocked (`Account.unlock()`) vs. no `Account`-status change.
- Whether the attempt is currently blocked (locked-and-not-yet-expired) — needed by the caller
  before it even applies a failed/succeeded outcome.

## State Changes

None performed by this class. It is a pure function from (snapshot, now, attempt outcome) →
next-state decision. All actual mutation of `lockout_state` rows and `Account.lock()`/`unlock()`
calls happens in T12's `LockoutService`.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java`
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java`

## Files to Modify

None.

## Files NOT to Modify

- `Account.java`, `AccountStatus.java` — `lock()`/`unlock()` guards stay exactly as-is; T11 does
  not call them.
- `application.properties` — no new config keys in this task.
- Anything under `spec/`.
- Any `account/`, `mfa/`, `apikey/`, `admin/`, `token/`, `events/`, `common/` file.

## Acceptance Criteria

- **AC1 (→ R17, L4).** 4 failures within window, 5th failure within window → locks;
  `lockedUntil = now + 15min` when `lockCount` was 0; `lockCount` becomes 1.
- **AC2 (→ R17, L4 doubling).** A lock occurring when `lockCount` was 1 → duration 30 min;
  `lockCount` becomes 2. A third cycle (`lockCount` was 2) → duration 60 min; `lockCount` becomes
  3 — asserts doubling continues past the first cycle, not just once.
- **AC3 (→ R18).** `now` at or after `lockedUntil` → attempt is permitted (not blocked). A
  successful attempt evaluated in that state resets `failedAttempts` and `lockCount` to 0 and
  clears `lockedUntil`. `now` strictly before `lockedUntil` → attempt is blocked.
- **AC4 (→ R19).** A new failure arriving with `Duration.between(lastFailedAt, now)` strictly
  greater than 30 minutes decays `failedAttempts` to 0 before counting the new failure (so it
  becomes attempt 1 of a fresh window, not attempt `n+1`). Exactly 30 minutes elapsed does not
  decay (boundary is inclusive of the window still being open at exactly 30:00).
- **AC5 (boundary, → R16/R17).** 4th failure within the window never locks. 5th failure exactly
  at the 30-minute window boundary (elapsed == 30:00 from the *window start*, not
  `last_failed_at` of the 5th attempt) still locks, consistent with AC4's inclusive convention.
- **AC6 (→ R18, reset scope).** A successful login while `failedAttempts > 0` but the account
  never reached `LOCKED` also resets `failedAttempts` to 0 (reset is not gated on having actually
  been locked).

## Required Tests

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named, package.md §8) → AC1/AC5.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named, package.md §8) → AC3/AC6.
- Boundary: 4th failure does not lock.
- Boundary: failure exactly at the 30-minute window edge still counts (no premature decay).
- Boundary: failure just past the 30-minute window edge decays first, restarting the count at 1.
- Doubling: second lock cycle duration is 30 min; third is 60 min.
- Boundary: attempt exactly at `lockedUntil` is permitted; one instant before is still blocked.
- Reset-without-prior-lock: success with `failedAttempts > 0`, `lockedUntil == null` still zeroes
  the counter.

## Constraints

- **Thread-safety:** the class must be stateless/immutable (no mutable instance fields beyond
  the fixed rule constants) so a single instance is safely shared across concurrent requests —
  all per-request state flows through method parameters and return values only.
- **Transaction:** none — no persistence, no `@Transactional`.
- **Module boundaries (L12):** class lives in `authn`; must not import `Account` or any
  `account`-package entity — it operates on primitive/record snapshots only, keeping the module
  boundary clean for when T12 wires it against the real `LockoutState` entity.
- **Null handling:** `lastFailedAt` and `lockedUntil` are legitimately nullable (never-failed /
  never-locked accounts); `now` and `failedAttempts`/`lockCount` are never null. No
  `Optional`-wrapped primitives; use plain nullable `Instant` fields on the input record, matching
  the nullable DB columns they mirror.
- **Determinism:** no `Instant.now()`, no randomness — `now` is always caller-supplied, matching
  the `agents.md` fixed-`Clock`-for-testing convention even though this class doesn't take a
  `Clock` directly.
- **Security:** no logging of failure counts/timestamps from this class (no logger at all,
  no side effects) — audit responsibility stays with T13.

## Open Questions

No blockers.
