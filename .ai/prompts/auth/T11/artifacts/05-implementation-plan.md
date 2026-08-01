# auth · T11 — Phase 5: Implementation Plan

Plans the frozen brief (`04-frozen-task-brief.md`). No code below — signatures and call-order only.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java`
2. `services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java`

Both authorized by the frozen brief's Files to Create list. No other file is touched.

## Files to modify

None — the frozen brief's Files to Modify is empty.

## Public methods (signatures)

All types live in `com.themistra.auth.authn`. `LockoutStateMachine` is a plain, stateless,
non-Spring class (no `@Service`, no bean) per the frozen brief's Dependencies/Constraints — matches
`agents.md`'s "no Spring config for pure logic" split already used by `PasswordPolicy` vs. its
`@ConfigurationProperties` sibling, except this class isn't a bean at all.

- `public record LockoutSnapshot(int failedAttempts, Instant lastFailedAt, Instant lockedUntil, int lockCount)`
  — input snapshot. Nullable `lastFailedAt`/`lockedUntil` (never-failed / never-locked accounts).
  Compact constructor validates `failedAttempts >= 0` and `lockCount >= 0`, throwing
  `IllegalArgumentException` otherwise (frozen brief AC9).

- `public enum LockoutAttemptOutcome { FAILURE, SUCCESS }` — which transition `evaluate` applies.

- `public enum AccountStatusChange { LOCK, UNLOCK, NONE }` — the caller-facing signal for whether
  `Account.lock()`/`unlock()` should be invoked by T12; this class never calls them itself.

- `public record LockoutDecision(int failedAttempts, Instant lastFailedAt, Instant lockedUntil, int lockCount, boolean blocked, AccountStatusChange statusChange)`
  — output. Field order intentionally mirrors `LockoutSnapshot` plus the two new decision fields,
  so the "unchanged snapshot passthrough" case (blocked path) reads as a visible no-op in tests.

- `public LockoutStateMachine(int maxAttempts, Duration decayWindow, Duration baseLockDuration)`
  — the three L4 rule constants (5, 30 min, 15 min), caller-supplied per the frozen brief's
  Dependencies section (T12 will source them from `LockoutProperties`/`application.properties`;
  this class takes plain values, no `@ConfigurationProperties` coupling).

- `public LockoutDecision evaluate(LockoutSnapshot snapshot, Instant now, LockoutAttemptOutcome outcome)`
  — the single decision entry point, covering R16–R19, L4, and the blocked-path rule (frozen brief
  Finding 5/12). `Objects.requireNonNull` on `snapshot`, `now`, `outcome`.

- `public LockoutDecision reset()` — the Finding 8 transition. Takes no snapshot; always returns
  the zeroed decision (`failedAttempts=0`, `lastFailedAt=null`, `lockedUntil=null`, `lockCount=0`,
  `blocked=false`, `statusChange=UNLOCK`). Noted for T12/T14's future admin-unlock and
  password-reset-unlock use; not called by anything in this task.

## Private methods

- `private LockoutDecision blockedNoOp(LockoutSnapshot snapshot)` — builds the unchanged-passthrough
  `LockoutDecision` with `blocked=true`, used when `now.isBefore(snapshot.lockedUntil())`. Shared by
  both outcome branches so the "ignore the outcome entirely" rule (Finding 5/12) can't drift between
  them.
- `private boolean decayed(LockoutSnapshot snapshot, Instant now)` — `snapshot.lastFailedAt() != null
  && Duration.between(snapshot.lastFailedAt(), now).compareTo(decayWindow) > 0`. Strictly greater
  than, matching AC4/AC5/AC7's inclusive-at-30:00 boundary.
- `private LockoutDecision applyFailure(LockoutSnapshot snapshot, Instant now)` — decay check first
  (R19), then increment, then threshold check (R17: lock + `lockCount++` + doubled duration via
  `baseLockDuration.multipliedBy(1L << lockCountBeforeThisLock)`) or plain increment (R16/no-op
  `lockedUntil`/`lockCount` otherwise). Never resets `failedAttempts` on the locking branch itself
  (frozen brief Finding 2 — human-approved, no invented reset).
- `private LockoutDecision applySuccess(LockoutSnapshot snapshot)` — always the zeroed reset shape
  (R18/AC6: unconditional on `failedAttempts > 0`, not gated on having been `LOCKED`) with
  `statusChange = snapshot.lockedUntil() != null ? UNLOCK : NONE`.
- `private Duration effectiveLockDuration(int lockCountBeforeThisLock)` — isolates the doubling
  formula (`baseLockDuration * 2^lockCountBeforeThisLock`, no cap per L4) so `applyFailure` reads as
  policy, not arithmetic.

## Entities used

None. Per the frozen brief's Constraints (module boundary, L12), this class imports nothing from
`account` — no `Account`, no `AccountStatus`. `AccountStatusChange` is this class's own enum, not a
reuse of anything account-owned.

## Repositories used

None — pure logic, no persistence (frozen brief State Changes: None).

## Services used

None — no collaborators, no Spring wiring (frozen brief Dependencies: none beyond `java.time`).

## Unit/integration tests required

Plain JUnit 5 + AssertJ, no Mockito (no collaborators to mock), no Spring context — matches every
other pure-logic test precedent in this module (e.g. `PasswordPolicyTest`'s boundary-style
assertions, minus the mocked `BreachCheckClient`). All timestamps built from a fixed base `Instant`
plus `Duration` offsets — no `Instant.now()` anywhere in the test, per `agents.md`'s fixed-clock
convention.

`LockoutStateMachineTest.java`, one `LockoutStateMachine` instance per test constructed with
`(5, Duration.ofMinutes(30), Duration.ofMinutes(15))`:

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named test) — drive 4 sequential
  `FAILURE` evaluations inside the window, assert `blocked=false`/`statusChange=NONE` each time,
  then the 5th assert `lockedUntil = now + 15min`, `lockCount = 1`, `statusChange = LOCK`,
  `failedAttempts = 5` unchanged (AC1).
- `shouldResetLockoutCounterOnSuccessfulLogin` (named test) — snapshot with `failedAttempts=3`,
  `lockedUntil=null` (never locked), evaluate `SUCCESS`, assert all fields zeroed including
  `lastFailedAt=null` (AC3/AC6).
- `fourthFailureWithinWindowDoesNotLock` — boundary, asserts `statusChange=NONE`,
  `lockedUntil=null` after exactly 4 failures (AC5).
- `secondLockDoublesDurationToThirtyMinutes` / `thirdLockDoublesDurationToSixtyMinutes` — drive two
  and three full lock-then-post-window-failure cycles (using the AC7 re-lock path below to get past
  each lock without a success reset), assert 30 min then 60 min durations and `lockCount` 2 then 3
  (AC2).
- `failureExactlyAtThirtyMinuteBoundaryStillLocksWithoutPrematureDecay` — `lastFailedAt` set so the
  5th failure lands at exactly `+30:00` from the 4th; assert it still locks, not decays first (AC4/AC5).
- `failureJustPastThirtyMinuteBoundaryDecaysFirst` — 5th failure at `+30:00:01`; assert
  `failedAttempts` becomes 1 (decayed then counted), no lock (AC4).
- `attemptExactlyAtLockedUntilIsPermitted` — `now == lockedUntil`, assert `blocked=false` (AC3).
- `attemptOneInstantBeforeLockedUntilIsStillBlocked` — `now == lockedUntil.minusMillis(1)`, assert
  `blocked=true` and every field equals the input snapshot unchanged, for both a `FAILURE` and a
  `SUCCESS` outcome supplied in that state (AC3, Finding 5/12).
- `successWithFailedAttemptsButNeverLockedStillResets` — `failedAttempts=2`, `lockedUntil=null`,
  `SUCCESS` → all zeroed, `statusChange=NONE` (not `UNLOCK`, since it was never locked) (AC6).
- `failedAttemptImmediatelyAfterLockExpiryReLocksWithDoubledDuration` — snapshot at the moment of a
  first lock (`failedAttempts=5`, `lockCount=1`, `lockedUntil=t+15m`, `lastFailedAt=t`), evaluate a
  `FAILURE` at `now = t+15m` (i.e. `lockedUntil`, permitted); assert it re-locks immediately
  (`Duration.between(lastFailedAt, now)` = 15 min ≤ 30 min, no decay), new `lockedUntil = now +
  30min` (`lockCount` was 1), `lockCount → 2` (AC7 — the human-approved escalating-lockout case).
- `failedAttemptWellAfterLockExpiryDecaysInsteadOfReLocking` — same starting snapshot, but
  `now = t + 46min` (i.e. `Duration.between(lastFailedAt, now)` = 46 min > 30 min); assert decay
  fires first, `failedAttempts → 1`, no re-lock (AC7).
- `resetAlwaysReturnsZeroedDecisionRegardlessOfStartingState` — call `reset()` with no snapshot
  argument (it takes none); assert the fixed zeroed output. A second call from a distinct
  `LockoutStateMachine` instance is redundant given `reset()` is snapshot-independent — one
  assertion suffices, `statusChange=UNLOCK` (AC8).
- `negativeFailedAttemptsInSnapshotThrows` / `negativeLockCountInSnapshotThrows` —
  `assertThatThrownBy(() -> new LockoutSnapshot(-1, null, null, 0))` /
  `new LockoutSnapshot(0, null, null, -1)` → `IllegalArgumentException` (AC9).
- `evaluateRejectsNullSnapshotNowOrOutcome` — three `assertThatThrownBy` cases for
  `NullPointerException` on each parameter (constraint: no silent null acceptance, consistent with
  `PasswordPolicy.validate`'s `Objects.requireNonNull` precedent).

## Execution order

1. `LockoutStateMachine.java` — write `LockoutSnapshot`, `LockoutAttemptOutcome`,
   `AccountStatusChange`, `LockoutDecision` as nested/sibling types first (the public shape), then
   the constructor, then `evaluate`/`reset`, then the private helpers
   (`blockedNoOp` → `decayed` → `effectiveLockDuration` → `applyFailure`/`applySuccess`) — bottom-up
   so each helper compiles against ones already written.
2. `LockoutStateMachineTest.java` — write the two named tests first (they're the acceptance floor),
   then the boundary/decay pair, then the doubling pair, then the blocked-path pair, then the
   Finding 2/AC7 re-lock pair (the highest-risk, human-approved behavior — gets the most scrutiny in
   Phase 7/8 review), then `reset()`, then the two invariant/null-rejection tests.
3. Compile + run via the established `javac` + JUnit Platform Launcher workaround (module-wide
   `mvn test` still blocked by the pre-existing, unrelated `token` package break) — this is Phase
   6's own verification step, listed here only to confirm the plan accounts for it; no code is
   written in this phase.

No schema/migration step — this task touches no persisted schema (frozen brief State Changes:
None; `lockout_state` persistence is T12's).
