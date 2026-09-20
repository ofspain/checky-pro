# auth · T11 · Phase 1 — Specification Extraction

## Business Rules

- **R16.** A failed password login attempt for an `ACTIVE` account increments the per-account
  failed-attempt counter and records a `login.failed` audit event. (The audit-recording half is
  T13's concern; T11 only needs the counter-increment *decision* to exist as a state transition.)
- **R17.** When the failed-attempt counter reaches 5 failed attempts within a rolling 30-minute
  window, the account transitions to `LOCKED` for 15 minutes, `lock_count` increments, and an
  `account.locked` audit event is recorded. (Audit recording is out of scope for the pure state
  machine; the lock decision + duration is in scope.)
- **R18.** Once a locked account's lockout interval has elapsed, the next authentication attempt
  is allowed; if it succeeds, the account transitions to `ACTIVE` and both the failed-attempt
  counter and `lock_count` reset to zero. (T11 computes "has the interval elapsed"; the
  reset-on-success transition itself, tied to `Account.unlock()`, is T12/T13's wiring.)
- **R19.** If the failed-attempt counter does not reach 5 within 30 minutes of the last failure,
  the counter decays to zero. This is the rolling-window mechanic the state machine must encode.

## Locked Decisions

- **L4. Brute-force lockout.** 5 failed attempts within a rolling 30-minute window transition an
  `ACTIVE` account to `LOCKED` for 15 minutes. Each subsequent lock doubles the effective
  duration via `lock_count` until it is reset. Counter decays 30 minutes after the last failure.
  This is the entire rule set T11 must encode; the numeric constants (5 / 30 / 15) are LOCKED —
  not proposable, not configurable-by-default-away.

## Files involved

**New (this task, per `design.md` §6, package `authn/`):**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java` — pure logic,
  unit-testable, no Spring annotations.
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutStateMachineTest.java`.

**Existing to read (context only, not modified by T11):**
- `services/auth/src/main/java/com/themistra/auth/account/Account.java` — `lock()`/`unlock()`
  guarded transitions T12 will call; T11 must produce decisions compatible with these guards
  (e.g. never signal "lock" for an account already `LOCKED`).
- `services/auth/src/main/java/com/themistra/auth/account/AccountStatus.java` — confirms `LOCKED`
  exists as a state.
- `services/auth/src/main/resources/db/migration/V1__*.sql` — `lockout_state` table shape
  (`account_id`, `failed_attempts`, `last_failed_at`, `locked_until`, `lock_count`), the field
  set `LockoutStateMachine`'s inputs/outputs should mirror since T12 will persist them directly.
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java` — pattern
  reference for any config record T11 introduces.

**Not touched by T11** (explicitly later tasks, confirmed in Phase 0):
- `LockoutService`, `LockoutState` entity/repository, `lockout_state` persistence wiring — T12.
- SAS authentication success/failure path integration, `login.failed`/`account.locked` audit
  emission — T13.
- Admin unlock endpoint — T14.

## Dependencies

- **Classes:** none required as collaborators — the state machine is pure logic. It may define
  its own small input/output types (e.g. a snapshot record mirroring `lockout_state` columns and
  a result/decision record) rather than depending on JPA entities (`LockoutState` entity doesn't
  exist yet and belongs to T12 regardless).
- **Config keys** (`design.md` §4c, not yet in `application.properties`):
  ```
  themistra.auth.lockout.max-attempts=5
  themistra.auth.lockout.window-minutes=30
  themistra.auth.lockout.base-lock-minutes=15
  ```
  Whether T11 introduces `LockoutProperties` + these keys, or just hardcodes/parameterizes the
  three constants for now and T12 introduces the config record, is unresolved — see Open
  Questions.
- **Time:** `java.time.Instant`/`Clock` semantics only (no `java.util.Date`); as a pure-logic
  class, `LockoutStateMachine` takes `Instant now` as a method parameter rather than injecting
  `Clock` as a field (confirmed pattern choice from Phase 0).
- **Contracts:** none of the four scoped contracts (`auth.yaml`, `token-claims.md`,
  `email-requested.v1.schema.json`, `security-audit.v1.schema.json`) are touched by a pure state
  machine with no API surface and no event emission. They're listed in the phase header because
  they're scoped to the *feature* (lockout/auth), not because T11 specifically consumes or
  produces them — no contract work is in scope here.

## Acceptance Criteria

- **AC1 (→ R17, L4).** Given `failed_attempts == 4` for an `ACTIVE`-eligible account and a new
  failure within the 30-minute window of `last_failed_at`, the machine decides: increment to 5,
  transition to locked, `locked_until = now + 15min` (first lock, `lock_count` 0→1).
- **AC2 (→ R17, L4 doubling).** Given a second lockout cycle (`lock_count == 1` going in), the
  next lock's effective duration doubles: `locked_until = now + 30min`, `lock_count` 1→2. (Exact
  formula — pre-increment vs post-increment `lock_count`, and whether it's
  `base * 2^lock_count` — is an Open Question; the acceptance criterion is "doubles each
  subsequent lock," not a specific formula, until Phase 2 fixes one.)
- **AC3 (→ R18).** Given a `LOCKED` account whose `locked_until` is in the past, the machine
  decides the next attempt is permitted; on a modeled "success" input, it decides: reset
  `failed_attempts = 0`, `lock_count = 0`, transition to unlocked/active-eligible.
  Given `locked_until` still in the future, the machine decides the attempt is still blocked.
- **AC4 (→ R19).** Given `failed_attempts` between 1 and 4 and a new attempt (success or the
  passage of time being evaluated) more than 30 minutes after `last_failed_at`, the machine
  decides: decay `failed_attempts` to 0 before applying the new attempt's outcome, rather than
  accumulating across the stale window.
- **AC5 (boundary, → R16/R17).** Exactly at the 5th failure within exactly the 30-minute window
  boundary (inclusive/exclusive edge) locks the account; the 4th failure at the same boundary
  does not.
- **AC6 (boundary, → R19).** Exactly at the 30-minute-since-`last_failed_at` boundary for decay —
  is the boundary itself "decayed" or "not yet decayed"? Needs a fixed convention (Open
  Question).

## Tests required

From `package.md` §8 (scoped to this task):
- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (labeled `→ R15` in `package.md`,
  actually `R17` per `requirements.md`'s numbering — see Open Questions).
- `shouldResetLockoutCounterOnSuccessfulLogin` (labeled `→ R16` in `package.md`, actually `R18`
  per `requirements.md`).

Boundary tests implied by "Unit-test boundaries" in the task statement and by L4/R16–R19, beyond
the two named tests above:
- 4th failure within window → still `ACTIVE`, not locked (boundary below threshold).
- 5th failure exactly at the 30-minute window edge → locks (or does not, per whatever
  inclusive/exclusive convention Phase 2 fixes).
- A failure just outside the 30-minute window from `last_failed_at` → counter decays first,
  so this becomes "failure #1" of a new window, not failure `#(n+1)`.
- Second consecutive lock cycle → lock duration doubles from 15 to 30 minutes; a third cycle's
  duration (continues doubling, e.g. to 60?) should also be asserted since L4 says "each
  subsequent lock" (implying it's not capped at one doubling) — worth at least one test past the
  first doubling to lock in the intended progression.
- Attempt evaluated exactly at `locked_until` (lock boundary: still locked vs. now unlocked).
- Successful login while `failed_attempts > 0` but account never reached `LOCKED` — counter still
  resets to 0 (R18's "reset the failed-attempt counter" isn't gated on having been locked; only
  the `ACTIVE`/`LOCKED` framing is — worth clarifying, see Open Questions).

## Open Questions

- **OQ1 — Doubling formula for `lock_count` (blocks AC2/design, not blocking Phase 1).** L4 says
  duration "doubles via `lock_count` until it is reset" but gives no formula, no cap, and no
  statement of whether `lock_count` is read before or after incrementing for the *current* lock
  being computed. Candidate reading: lock duration = `base-lock-minutes * 2^lock_count` using the
  `lock_count` value *before* this lock's increment (so first lock: `lock_count=0` → 15 * 2^0 =
  15 min; second: `lock_count=1` → 15 * 2^1 = 30 min). This is the most natural reading of "each
  subsequent lock doubles" and is what Phase 2 should adopt unless the author overrides it. No
  cap is specified in L4 — flag but do not invent one.
- **OQ2 — Inclusive/exclusive boundary conventions.** Neither `requirements.md` nor `design.md`
  states whether "within a rolling 30-minute window" and "15-minute lock" boundaries are
  inclusive or exclusive at the exact edge. Phase 2 needs to fix a convention (proposal:
  window/lock treated as still-active while `now.isBefore(boundary)`, i.e. the boundary instant
  itself is already expired/decayed) since this directly affects AC5/AC6 and their tests.
- **OQ3 — `package.md` §8 requirement-ID labels (`R15`/`R16`) vs `requirements.md` numbering
  (`R17`/`R18`).** Same drift class flagged in Phase 0 and previously triaged on earlier tasks —
  not a T11 blocker; the phase header's own scoped IDs (`R16`–`R19`) match `requirements.md` and
  are treated as authoritative here. Noted for the record, not escalated.
- **OQ4 — Is a config record (`LockoutProperties`) in scope for T11, or does the pure state
  machine take the three constants as constructor/method parameters until T12 introduces
  config?** Task 11's statement only asks for `LockoutStateMachine` + unit tests; task 12 ("Add
  `LockoutService` that loads/updates `lockout_state`, handles decay...") is the first task that
  actually needs the numbers to come from Spring config to be wired into a bean. Leaning toward:
  T11 defines the three constants as a small parameter object (record) that `LockoutStateMachine`
  takes in its constructor, without necessarily being a `@ConfigurationProperties` bean yet — but
  this is a Phase 2 design call, not decided here.
- **OQ5 — Q5 in `package.md` §11 (lockout event publication) is unresolved but out of scope for
  T11** — the state machine doesn't emit events or call `AuditService`; noted only so it isn't
  mistaken for a T11 blocker.
