# auth · T11 — Phase 6: Implementation Notes

Implements the frozen brief (`04-frozen-task-brief.md`) exactly per the plan
(`05-implementation-plan.md`). No test file touched in this phase (Phase 10's job) — production
code only, as planned.

## Changes

### `LockoutStateMachine.java` (new)

`services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java`. Stateless,
non-Spring, final class — no `@Service`/`@Component`, matching the frozen brief's Dependencies
section (constants injected via plain constructor, not `@ConfigurationProperties`).

Types, exactly per the plan's public-method list:
- `LockoutSnapshot` record (`failedAttempts`, `lastFailedAt`, `lockedUntil`, `lockCount`) — compact
  constructor throws `IllegalArgumentException` on negative `failedAttempts`/`lockCount` (AC9).
- `LockoutAttemptOutcome` enum (`FAILURE`, `SUCCESS`).
- `AccountStatusChange` enum (`LOCK`, `UNLOCK`, `NONE`).
- `LockoutDecision` record (`failedAttempts`, `lastFailedAt`, `lockedUntil`, `lockCount`,
  `blocked`, `statusChange`) — includes `lastFailedAt` in the output, closing Finding 1's gap.
- Constructor `(int maxAttempts, Duration decayWindow, Duration baseLockDuration)`.
- `evaluate(LockoutSnapshot, Instant, LockoutAttemptOutcome)` — the single decision entry point.
- `reset()` — the Finding 8 unconditional-zero transition, for T12/T14's future use; not called
  anywhere in this task.

Private helpers, exactly per the plan: `blockedNoOp`, `applyFailure`, `applySuccess`, `decayed`,
`effectiveLockDuration`.

Key behavioral points, each traced to a frozen-brief decision:

- **Blocked path (Finding 5/12).** `evaluate` checks `now.isBefore(snapshot.lockedUntil())` first,
  before looking at `outcome` at all. If blocked, `blockedNoOp` returns every field unchanged from
  the input snapshot with `blocked=true` — the supplied outcome (failure or success) never reaches
  `applyFailure`/`applySuccess`. One code path serves both outcome cases, so the "ignore the
  outcome" rule can't drift between them.
- **No reset on locking (Finding 2, human-approved).** `applyFailure`'s locking branch sets
  `lockedUntil` and increments `lockCount`, but leaves `failedAttempts` at its just-incremented
  value (5) and `lastFailedAt` at `now` — no invented reset. This is what produces the approved
  escalating behavior: a failure evaluated shortly after `lockedUntil` passes (still within
  `decayWindow` of the locking failure) is not blocked, reaches `applyFailure`, finds `decayed(...)
  == false`, increments straight past the threshold again, and re-locks with `lockCount`
  incremented once more (doubled duration via `effectiveLockDuration`).
- **Decay boundary (Finding 3/7, AC4/AC5).** `decayed` uses `Duration.between(lastFailedAt,
  now).compareTo(decayWindow) > 0` — strictly greater, so exactly 30:00 elapsed does not decay,
  matching both AC4 and the now-reconciled AC5 (no separate "window start" concept — resolved by
  the frozen brief's schema-constraint finding).
- **Unconditional success reset (Finding 6, AC6).** `applySuccess` always returns the zeroed shape;
  `statusChange` is `UNLOCK` only if the snapshot had a non-null `lockedUntil`, otherwise `NONE` —
  the reset itself is not gated on having been locked, only the caller-facing unlock signal is.
- **Doubling formula (L4, unchanged from Phase 2/5).** `effectiveLockDuration` computes
  `baseLockDuration.multipliedBy(1L << lockCountBeforeThisLock)` — first lock (`lockCount=0`) → 15
  min, second (`lockCount=1`) → 30 min, third (`lockCount=2`) → 60 min. No cap, per L4.
- **Module boundary (L12).** No import of `Account`/`AccountStatus` or anything from `account`;
  the class depends only on `java.time.Duration`, `java.time.Instant`, `java.util.Objects`.

Maps to: frozen brief AC1-AC9 in full; Findings 1, 2, 3, 4 (constructor/precondition only — the
"`ACTIVE`-eligible" precondition is a T12 calling-convention note, not code in this class), 5, 6,
7, 8, 9 (no code impact — package.md drift, not touched), 10, 12.

## Acceptance criteria — implementation status

| ID | Status |
|---|---|
| AC1 | Done — 5th failure locks; `lockedUntil = now + 15min` at `lockCount=0`; `lockCount → 1`; `failedAttempts` stays at 5 |
| AC2 | Done — doubling via `effectiveLockDuration`; verified by inspection for `lockCount` 1→2 (30min) and 2→3 (60min) |
| AC3 | Done — `now >= lockedUntil` reaches `applyFailure`/`applySuccess`; `now < lockedUntil` returns `blockedNoOp` unchanged |
| AC4 | Done — `decayed` is strictly-greater-than on `decayWindow` |
| AC5 | Done — same boundary, computed solely from `lastFailedAt`; no window-start field exists in `LockoutSnapshot` |
| AC6 | Done — `applySuccess` always zeroes, independent of `lockedUntil` |
| AC7 | Done — no reset in the locking branch of `applyFailure`; a post-unlock failure within `decayWindow` of `lastFailedAt` re-locks via the same threshold check, doubled |
| AC8 | Done — `reset()` is unconditional, returns the fixed zeroed `LockoutDecision` with `statusChange=UNLOCK` |
| AC9 | Done — `LockoutSnapshot`'s compact constructor throws `IllegalArgumentException` on negative `failedAttempts`/`lockCount` |

Test-side proof of AC1-AC9 is Phase 10's job — no test file was written here, matching the plan
and this phase's guardrail (production code only; T11's own unit tests are still deferred to the
framework's normal test-generation phase despite the task statement's "unit-test boundaries"
wording, consistent with T09's precedent of deferring all test files to Phase 10).

## Deviations from the plan

None. Implementation matches `05-implementation-plan.md`'s public/private method list, type
shapes, and Execution order step 1 exactly. Steps 2-3 (tests, compile-verification harness) are
this phase's own build check only (see below), not test authorship.

## Build verification

`LockoutStateMachine.java` has zero dependencies beyond `java.time`/`java.util` — no Spring, no
other `authn`/`account` classes — so it compiles standalone without needing the module's resolved
classpath (unlike prior tasks, the pre-existing unrelated `token` package compile break never
enters the picture here):

```
javac -d /tmp/lockout-build services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java
```

Clean compile, no errors, no warnings.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 11.
- Requirements: R16, R17, R18, R19 (`requirements.md`).
- LOCKED decisions: L4 (5/30/15 + doubling, adopted formula unchanged since Phase 2), L12 (module
  boundary — verified no cross-module import).
- Frozen brief: `04-frozen-task-brief.md` — both authorized Files to Create covered by this phase
  (the test file is Phase 10's); no file outside that list touched.
