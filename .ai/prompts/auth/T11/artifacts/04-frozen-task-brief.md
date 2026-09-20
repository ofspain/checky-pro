STATUS: FROZEN

# auth · T11 — Phase 4: Frozen Task Brief

Human-approved. Folds every Phase 3 (Kimi) finding into a final decision. Downstream phases may
not renegotiate anything below.

## Phase 3 findings — disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | High | Output snapshot omits `lastFailedAt`, so T12 has no machine-authoritative value to persist | **ACCEPTED, amended.** Verified `lockout_state.last_failed_at` is a real persisted column (`design.md:159-165`). Add `lastFailedAt: Instant \| null` to the output snapshot. A failed attempt (whether it locks or not) sets it to `now`. An explicit reset (success-triggered per R18/AC6, R19 decay, or the new `reset()` transition from #8) clears it to `null`. |
| 2 | High | `failedAttempts` state at/after a lock event is undefined | **ACCEPTED, resolved — human-approved as "leave at 5" (spec-literal).** R18 gates the counter reset on a *successful* post-unlock login only; R19's decay rule is not scoped to pre-lock-only, so it keeps applying after a lock too. Adopted rule: locking does **not** touch `failedAttempts`/`lockCount`/`lastFailedAt` beyond what R17 already specifies (set `lockedUntil`, increment `lockCount`) — no reset is invented. Consequence, confirmed and accepted: since the base lock (15 min) is shorter than the 30-min decay window, a failed attempt evaluated immediately after `lockedUntil` passes will find `Duration.between(lastFailedAt, now) < 30min` and therefore re-lock immediately, with `lockCount` doubling again. This is intentional escalating behavior, not a bug — flagged explicitly here since it has real UX impact (one mistyped password right after unlock re-locks for 30 min) and was confirmed by the human rather than assumed. New **AC7** added below to make this the tested, locked behavior. |
| 3 | High | Rolling 30-min window can't be implemented from the proposed snapshot (no `windowStart`) | **ACCEPTED, resolved by schema constraint, not preference.** `lockout_state` (`design.md:159-165`) has exactly `failed_attempts`, `last_failed_at`, `locked_until`, `lock_count` — no window-start column, and V1-V4 are immutable while V5 (`design.md`) only adds `shedlock` + an index, not a new column. Adding `windowStart` would require a schema change outside T11's (and this migration's) scope. **Formally adopts Kimi's option (b):** R17/R19 are implemented as the simplified "5 failures with no gap > 30 min since the last one" decay rule already described in the Phase 2 brief, not a true sliding window over full history. This is the approved interpretation of "rolling 30-minute window," not an approximation pending a future fix. |
| 4 | Medium | "`ACTIVE`-eligible" is undefined; machine can't itself enforce it | **ACCEPTED, amended.** `requirements.md:28` R16 text is "a password login attempt fails for an `ACTIVE` account" — the Phase 2 brief's "`ACTIVE`-eligible" paraphrase is replaced with the exact rule: `ACTIVE`-eligible ≡ `status == ACTIVE`. Precondition note added: T12's `LockoutService` must not invoke `LockoutStateMachine` for any non-`ACTIVE` account status; R21's uniform-rejection handling for `LOCKED`/`SUSPENDED`/`DELETED`/non-existent accounts happens upstream of this class, not inside it. |
| 5 | Medium | Behavior while `now < lockedUntil` (blocked) is unspecified | **ACCEPTED, amended.** When `now < lockedUntil`, the machine returns `blocked = true` and leaves `failedAttempts`, `lastFailedAt`, `lockedUntil`, and `lockCount` unchanged, **regardless of the supplied attempt outcome** (failure or success). This also resolves Finding 12 (successful-attempt semantics while locked) — same rule, same rationale: a caller must check `blocked` before trusting any state-change flags, but the machine is deterministic either way. |
| 6 | Medium | AC6 resets the counter on *any* successful login, not just a post-lock one — broader than R18's literal text | **ACCEPTED as an intentional, documented extension of R18**, not a defect. Rationale: the named test `shouldResetLockoutCounterOnSuccessfulLogin` (package.md §8) is titled generically, not "...after lock expiry"; leaving a stale `failedAttempts > 0` after any successful login (never having reached `LOCKED`) is inconsistent with standard lockout semantics and would be a strange, undocumented residual state for T12 to carry forward. AC6 stays as specified: any successful login zeroes `failedAttempts` and `lockCount` and clears `lockedUntil`/`lastFailedAt`, whether or not the account was ever `LOCKED`. |
| 7 | Medium | AC4 (boundary relative to `lastFailedAt`) and AC5 (boundary relative to "window start") are contradictory | **ACCEPTED, resolved by #3.** Since #3 formally adopts the simplified decay rule (no `windowStart` concept), AC5's "window start" wording is removed. AC5 is restated purely in terms of `lastFailedAt`, identical boundary convention to AC4: exactly 30:00 elapsed since the *previous* failure does not decay; the count-to-5 check and the decay check share one clock reference. |
| 8 | Medium | Admin unlock (R20) and password-reset unlock don't reuse the state machine, risking duplicated clear-logic in T12/T14 | **ACCEPTED, amended, scope-neutral.** Add one more pure transition to `LockoutStateMachine` — `reset()` — that takes no attempt outcome and returns a zeroed snapshot (`failedAttempts=0`, `lockCount=0`, `lockedUntil=null`, `lastFailedAt=null`). This stays inside the same file already listed in Files to Create; no new file, no new dependency. T12 (admin unlock, R20) and the existing `AccountService.resetPassword` (T07/T08, already calls `account.unlock()`) are the intended callers — noted here for their benefit, not implemented by them in this task. |
| 9 | Low | `package.md` §8 maps the named tests to R15/R16, but `requirements.md` numbers them R17/R18 | **Confirmed, not fixed** (never modify `spec/`). Verified: `package.md:91-92` says `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` → R15 and `shouldResetLockoutCounterOnSuccessfulLogin` → R16; `requirements.md:28-31` actually numbers these rules R17 and R18. This is the same drift pattern already logged and accepted as non-blocking in T09's Phase 4 (Finding 7) — a pre-existing spec-authoring numbering drift, not specific to T11. T11 proceeds against `requirements.md`'s real R17/R18 text. |
| 10 | Low | No invariants defined for corrupted/edge inputs (negative counters, `Duration` overflow) | **ACCEPTED, amended.** Add an "Input invariants" note: `failedAttempts >= 0` and `lockCount >= 0` are preconditions; the constructor/method throws `IllegalArgumentException` if violated. `Duration` overflow from `baseLockMinutes * 2^lockCount` at very large `lockCount` is a known, accepted, undocumented-cap situation — L4 explicitly specifies no cap, so this is not a defect to fix, only a documented theoretical limit. |
| 11 | Low | Header's referenced contracts (`auth.yaml`, `token-claims.md`, `email-requested`/`security-audit` schemas) don't exist yet in the repo | **Confirmed, not fixed.** Verified via repo search: only `contracts/events/auth/user-lifecycle.v1.schema.json` exists; the other three are future work (`tasks.md` items 33-34, tasks T-33/T-34, well after T11-T14). T11 does not emit events or call contracts, so this is a non-blocking header artifact. Carried forward as a dependency note for T13 (which will need `security-audit.v1.schema.json` to define the `account.locked` event shape). |
| 12 | Low | Successful-attempt semantics while locked are unspecified | **ACCEPTED, resolved by #5** (same disposition — `blocked=true` disregards the outcome and makes no state change). |

All Phase 1 Open Questions are resolved above. No open questions remain; #2 was escalated for and received explicit human approval (leave `failedAttempts` at threshold post-lock — spec-literal, no invented reset).

---

## Task

Implement `LockoutStateMachine` — a pure-logic, unit-testable class encoding the brute-force
lockout rules (5 failed attempts / 30-minute inactivity-decay window / 15-minute base lock, with
doubling on repeat locks). Unit-test the boundaries, including the escalating post-lock re-lock
behavior confirmed in Finding 2.

## Purpose

Provide the decision logic that later tasks (T12 `LockoutService`, T13 SAS auth-path wiring, T14
admin unlock) will call to determine whether an authentication failure locks an account, whether a
locked account may attempt again, and how the failed-attempt counter decays or resets. T11
delivers only the decision function — no persistence, no `Account` mutation, no audit, no Spring
wiring.

## Scope

**In:**
- `LockoutStateMachine` pure-logic class encoding R16-R19 / L4, with the amendments above.
- A small immutable snapshot/result type set for its inputs and outputs, including `lastFailedAt`
  in the output (Finding 1).
- A `reset()` transition returning a zeroed snapshot, for T12/T14's future admin-unlock and
  password-reset-unlock use (Finding 8).
- Unit tests covering the named tests plus all boundary cases, including the new AC7.

**Out:**
- `LockoutState` JPA entity, repository, `lockout_state` persistence (T12).
- `LockoutService`, `Account.lock()`/`unlock()` wiring, and actually calling `reset()` from admin
  unlock or password-reset (T12/T14).
- SAS authentication success/failure path integration, `login.failed`/`account.locked` audit
  emission (T13).
- Admin unlock endpoint (T14).
- A true sliding window over failure history (`windowStart`) — formally out of scope per Finding
  3's schema-constraint resolution; the simplified decay rule is the adopted design, not a stopgap.
- Any `@ConfigurationProperties` bean or `application.properties` keys — the machine takes its
  three rule constants via a plain constructor parameter, not Spring config (T12's concern).

## Business Rules

- **R16.** A failed login attempt for an `ACTIVE` account (verified exact wording,
  `requirements.md:28`) increments the failed-attempt counter and sets `lastFailedAt = now`.
  (Audit emission is T13; T11 only decides the new counter value.) `ACTIVE`-eligible = `status ==
  ACTIVE`; T12 must not invoke the machine for any other status (Finding 4).
- **R17.** Reaching 5 failed attempts within the rolling 30-minute window transitions to locked,
  sets `lockedUntil = now + effective-duration`, and increments `lockCount`. `failedAttempts` and
  `lastFailedAt` are **not** reset by locking itself (Finding 2, human-approved).
- **R18.** Once `lockedUntil` has passed, the next attempt is permitted. On success, `failedAttempts`,
  `lockCount`, `lockedUntil`, and `lastFailedAt` all reset (Finding 1/6) — and this reset applies to
  *any* successful login with `failedAttempts > 0`, not only a post-lock one (Finding 6, AC6).
- **R19.** If more than 30 minutes have elapsed since `lastFailedAt` without reaching 5 failures,
  the counter decays to 0 before the new attempt is applied. This decay rule is not scoped to
  pre-lock state only — it also governs the first failed attempt after a lock expires (Finding 2).

## Locked Decisions

- **L4.** 5 failed attempts / 30-minute rolling window / 15-minute base lock. Each subsequent lock
  doubles the effective duration via `lockCount`, until it resets (on a successful login, per R18,
  Finding 6). Adopted formula (implementer decision, unchanged from Phase 2, since L4 specifies no
  formula): `effectiveDurationMinutes = baseLockMinutes * 2^lockCountBeforeThisLock`. First lock
  (`lockCount=0`) → 15 min; second (`lockCount=1`) → 30 min; third (`lockCount=2`) → 60 min. No
  cap — L4 states none; `Duration` overflow at extreme `lockCount` is a documented, accepted,
  theoretical limit (Finding 10), not something to guard against.
- **"Rolling 30-minute window" = inactivity-decay rule**, formally adopted per Finding 3: 5
  failures counted as long as no gap between consecutive failures exceeds 30 minutes, computed
  solely from `lastFailedAt`. Not a true sliding window over full failure history — the
  `lockout_state` schema has no `windowStart` column and none is being added.

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

Invariants (Finding 10): `failedAttempts >= 0` and `lockCount >= 0` are required; violation throws
`IllegalArgumentException`.

Plus, per call, which transition is being evaluated: a failed attempt, a successful attempt, or an
unconditional `reset()` (Finding 8) — the last one takes no snapshot beyond producing a zeroed
result.

## Outputs

A decision/result describing the next state:
- `failedAttempts: int` (post-transition)
- `lastFailedAt: Instant | null` (post-transition; Finding 1) — set to `now` on any failed attempt
  (locking or not), cleared to `null` on any reset (success, per R18/AC6, or `reset()`).
- `lockedUntil: Instant | null` (post-transition; null if not/no-longer locked)
- `lockCount: int` (post-transition)
- Whether the account should now be locked (i.e., whether `Account.lock()` should be called by the
  caller) vs. should now be unlocked (`Account.unlock()`) vs. no `Account`-status change.
- Whether the attempt is currently blocked (locked-and-not-yet-expired) — needed by the caller
  before it even applies a failed/succeeded outcome. When `blocked = true`, all other output fields
  equal the input snapshot unchanged, regardless of the supplied outcome (Finding 5/12).

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

- `Account.java`, `AccountStatus.java` — `lock()`/`unlock()` guards stay exactly as-is; T11 does not
  call them.
- `application.properties` — no new config keys in this task.
- Anything under `spec/`.
- Any `account/`, `mfa/`, `apikey/`, `admin/`, `token/`, `events/`, `common/` file.

## Acceptance Criteria

- **AC1 (→ R17, L4).** 4 failures within window, 5th failure within window → locks; `lockedUntil =
  now + 15min` when `lockCount` was 0; `lockCount` becomes 1. `failedAttempts` remains 5 and
  `lastFailedAt` remains set to the 5th failure's timestamp (Finding 2).
- **AC2 (→ R17, L4 doubling).** A lock occurring when `lockCount` was 1 → duration 30 min;
  `lockCount` becomes 2. A third cycle (`lockCount` was 2) → duration 60 min; `lockCount` becomes 3
  — asserts doubling continues past the first cycle, not just once.
- **AC3 (→ R18).** `now` at or after `lockedUntil` → attempt is permitted (not blocked). A
  successful attempt evaluated in that state resets `failedAttempts` and `lockCount` to 0, clears
  `lockedUntil`, and clears `lastFailedAt`. `now` strictly before `lockedUntil` → attempt is
  blocked and no field changes (Finding 5).
- **AC4 (→ R19).** A new failure arriving with `Duration.between(lastFailedAt, now)` strictly
  greater than 30 minutes decays `failedAttempts` to 0 before counting the new failure (so it
  becomes attempt 1 of a fresh window, not attempt `n+1`). Exactly 30 minutes elapsed does not
  decay (boundary is inclusive of the window still being open at exactly 30:00).
- **AC5 (boundary, → R16/R17).** 4th failure within the window (elapsed since `lastFailedAt` ≤ 30
  min at every step) never locks. 5th failure with elapsed-since-previous-failure exactly 30:00
  still locks — same boundary convention as AC4, computed solely from `lastFailedAt` (Finding 7;
  the earlier "window start" wording is removed).
- **AC6 (→ R18, reset scope).** A successful login while `failedAttempts > 0` but the account never
  reached `LOCKED` also resets `failedAttempts`, `lockCount`, and `lastFailedAt` (reset is not
  gated on having actually been locked — adopted extension, Finding 6).
- **AC7 (→ R17/R18/R19 interaction, new — Finding 2).** A failed attempt evaluated at or after
  `lockedUntil` (the account's first post-unlock attempt) is **not** treated as blocked, and is
  evaluated against the still-unreset `failedAttempts` (5) and `lastFailedAt` (set at the locking
  failure): if `Duration.between(lastFailedAt, now) <= 30min`, it immediately re-locks with
  `lockCount` incremented again (doubled duration) — confirmed intentional escalating behavior, not
  a bug. If instead `Duration.between(lastFailedAt, now) > 30min`, R19 decay applies first
  (`failedAttempts` → 0), and the new failure becomes attempt 1 of a fresh window (no re-lock).
- **AC8 (→ Finding 8, `reset()`).** Calling `reset()` on any snapshot (locked, mid-window, or
  clean) returns `failedAttempts=0`, `lockCount=0`, `lockedUntil=null`, `lastFailedAt=null`, and an
  unlock status-change signal.
- **AC9 (→ Finding 10, invariants).** Constructing an input snapshot with `failedAttempts < 0` or
  `lockCount < 0` throws `IllegalArgumentException`.

## Required Tests

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named, package.md §8, actually
  R17 per `requirements.md` — see Finding 9) → AC1/AC5.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named, package.md §8, actually R18 per
  `requirements.md` — see Finding 9) → AC3/AC6.
- Boundary: 4th failure does not lock.
- Boundary: failure exactly at the 30-minute window edge still counts (no premature decay).
- Boundary: failure just past the 30-minute window edge decays first, restarting the count at 1.
- Doubling: second lock cycle duration is 30 min; third is 60 min.
- Boundary: attempt exactly at `lockedUntil` is permitted; one instant before is still blocked, with
  no field changes.
- Reset-without-prior-lock: success with `failedAttempts > 0`, `lockedUntil == null` still zeroes
  the counter and `lastFailedAt`.
- **New:** failed attempt immediately after `lockedUntil` (elapsed since `lastFailedAt` ≤ 30 min)
  re-locks immediately with doubled duration (AC7).
- **New:** failed attempt well after `lockedUntil` (elapsed since `lastFailedAt` > 30 min) decays
  first and does not re-lock (AC7).
- **New:** `reset()` zeroes every field regardless of starting state (AC8).
- **New:** negative `failedAttempts`/`lockCount` in the input throws (AC9).

## Constraints

- **Thread-safety:** the class must be stateless/immutable (no mutable instance fields beyond the
  fixed rule constants) so a single instance is safely shared across concurrent requests — all
  per-request state flows through method parameters and return values only.
- **Transaction:** none — no persistence, no `@Transactional`.
- **Module boundaries (L12):** class lives in `authn`; must not import `Account` or any
  `account`-package entity — it operates on primitive/record snapshots only, keeping the module
  boundary clean for when T12 wires it against the real `LockoutState` entity.
- **Null handling:** `lastFailedAt` and `lockedUntil` are legitimately nullable (never-failed /
  never-locked accounts); `now` and `failedAttempts`/`lockCount` are never null. No
  `Optional`-wrapped primitives; use plain nullable `Instant` fields on the input record, matching
  the nullable DB columns they mirror.
- **Determinism:** no `Instant.now()`, no randomness — `now` is always caller-supplied, matching the
  `agents.md` fixed-`Clock`-for-testing convention even though this class doesn't take a `Clock`
  directly.
- **Security:** no logging of failure counts/timestamps from this class (no logger at all, no side
  effects) — audit responsibility stays with T13.

## Open Questions

No blockers. Finding 2's design tension was escalated and resolved by explicit human decision
above (leave `failedAttempts` at threshold post-lock; no invented reset). All other findings
resolved by spec/schema verification, matching precedent from T09 Phase 4.
