# auth · T12 · Phase 2 — Task Implementation Brief

## Task

Add `LockoutService`, `LockoutState` (JPA entity), `LockoutStateRepository`, and
`LockoutProperties` to the `authn` package. `LockoutService` loads/persists `lockout_state`,
delegates the decision to `LockoutStateMachine` (T11), and applies the resulting `Account.lock()`/
`unlock()` transition by calling two new thin methods on `AccountService`.

## Purpose

`LockoutStateMachine` (T11) is a pure decision function with no persistence and no caller today.
This task makes it real: a Spring-managed service that reads/writes the actual `lockout_state`
table and actually flips `Account.status` between `ACTIVE` and `LOCKED` when the machine says to.
T13 (SAS auth-path wiring) and T14 (admin unlock) will both call this service; neither is wired up
by this task.

## Scope

**In:**
- `LockoutService` with two public entry points: recording a failed attempt and recording a
  successful attempt, each for a given account UUID and moment.
- `LockoutState` entity mapping the existing (already-applied, immutable) `lockout_state` table
  exactly: `accountId`, `failedAttempts`, `lastFailedAt`, `lockedUntil`, `lockCount`.
- `LockoutStateRepository`, package-private, `JpaRepository<LockoutState, Long>` keyed on
  `accountId` (the table's actual PK per `V1__auth_baseline_schema.sql:114-120`).
- `LockoutProperties`, a validated `@ConfigurationProperties` record for the three L4 constants,
  plus adding the corresponding keys to `application.properties` (`design.md` §4c, VERBATIM).
- Two new `AccountService` methods, `lock(UUID)` / `unlock(UUID)` — thin wrappers around
  `account.lock()`/`account.unlock()`, the only sanctioned way `LockoutService` can reach the
  `Account` entity (L12 — `LockoutService` never imports `Account`).
- **Implementer decision (proposed here, subject to Phase 3 challenge):** a missing
  `lockout_state` row is treated as an implicit zero-value snapshot (`failedAttempts=0,
  lastFailedAt=null, lockedUntil=null, lockCount=0`) — rows are created lazily on first failure,
  not eagerly at registration. No change to `Account.register`/T02 needed.

**Out:**
- Wiring `LockoutService` into the SAS authentication success/failure path — that's T13
  (`tasks.md` item 13's own wording: "Integrate lockout counter increment into the SAS
  authentication failure path...").
- Any audit event (`login.failed`, `account.locked`) — T13's scope per the same task split R16/R17
  already imply (T11's frozen brief made the identical call for the pure-logic layer; this task
  extends that same boundary to the persistence layer).
- Any outbox/lifecycle event (`auth.user.lifecycle`) on lock/unlock. **Deliberately deferred**:
  `package.md` §11 Q5 asks this exact question ("is lock/unlock published only as an audit mirror,
  or also as a lifecycle event?") and is explicitly unresolved by the spec author. Emitting nothing
  is the conservative, reversible default — a later task can add it without touching this task's
  code. Not treated as a blocker for this task; flagged for the human's attention regardless.
- The admin-unlock endpoint (T14) and any change to `AccountService.resetPassword`. `resetPassword`
  already calls `account.unlock()` directly (T07/T09) without touching `lockout_state`, which
  leaves stale counters behind — a real, pre-existing gap (T11 frozen brief Finding 8) — but
  `tasks.md` item 12's text does not name `resetPassword`, and fixing it is unrelated-file scope
  creep for a task titled "Lockout service." Left as an explicit, documented gap for a future task,
  not fixed here.
- Any change to `LockoutStateMachine.java` itself (T11, frozen, read-only dependency).

## Business Rules

- R16 — a failed attempt for an `ACTIVE` account increments the failed-attempt counter (decision:
  `LockoutStateMachine`; persistence + `Account` status application: this task).
- R17 — the 5th failure within the window locks the account for the base duration, increments
  `lock_count` (decision: T11; persistence + `Account.lock()` application: this task).
- R18 — once the lockout interval elapses, a subsequent success resets the counter and transitions
  back to `ACTIVE` (decision: T11; persistence + `Account.unlock()` application: this task).
- R19 — the counter decays to zero after 30 minutes of inactivity (decision: T11; persistence:
  this task).

## Locked Decisions

- L4 — 5/30-min/15-min, doubling via `lock_count`. Already fully encoded in `LockoutStateMachine`;
  this task only supplies the three constants via `LockoutProperties` and constructs the machine
  once, as a long-lived field — never per-call.
- L12 — module boundaries, ArchUnit-enforced. `LockoutService` (package `authn`) must not import
  `com.themistra.auth.account.Account`. It reaches `Account.lock()`/`unlock()` only through the
  two new `AccountService` methods.

## Dependencies

- `LockoutStateMachine` (T11) — `evaluate(LockoutSnapshot, Instant, LockoutAttemptOutcome)` →
  `LockoutDecision`. Constructed once inside `LockoutService`'s constructor from `LockoutProperties`
  (`new LockoutStateMachine(properties.maxAttempts(), Duration.ofMinutes(properties.windowMinutes()),
  Duration.ofMinutes(properties.baseLockMinutes()))`) — not a separate Spring `@Bean`, consistent
  with `LockoutStateMachine`'s own design intent (plain constructor, not Spring-config-aware).
- `AccountService` — two new public methods this task adds: `lock(UUID accountUuid)` and
  `unlock(UUID accountUuid)`, both `@Transactional`, each a one-line call to the corresponding
  `Account` guarded transition after loading via the existing private `getAccount` helper. No
  other `AccountService` method changes.
- `java.time.Clock` — existing bean, constructor-injected into `LockoutService` (caller supplies
  `now` to `LockoutService`'s own methods; `Clock` is available if a default-to-now convenience
  overload is added, but `now` is always caller-supplied at the primary entry points — same
  determinism convention as T11).
- Config keys (`design.md` §4c, VERBATIM, not yet present in `application.properties`):
  ```properties
  themistra.auth.lockout.max-attempts=5
  themistra.auth.lockout.window-minutes=30
  themistra.auth.lockout.base-lock-minutes=15
  ```

## Inputs

- `recordFailedAttempt(UUID accountUuid, Instant now)` — account UUID (not the internal `Long` id
  — `LockoutStateRepository` resolves it internally via a join or a `findByAccount_AccountUuid`-
  style query, mirroring `AccountRepository.findByAccountUuid`'s existing convention) and the
  moment of evaluation.
- `recordSuccessfulAttempt(UUID accountUuid, Instant now)` — same shape.

## Outputs

- Both methods return the `LockoutDecision` T11 produced, so a future caller (T13) can inspect
  `blocked`/counters without a second read. Neither method throws for a missing account — per
  Business Rules' R16 scoping ("`ACTIVE` account"), the *caller* (T13) is responsible for only
  invoking this service for accounts it already knows are `ACTIVE`-eligible; this task does not
  re-validate account status internally (mirrors T11's own frozen brief Finding 4 disposition —
  the precondition lives at the call site, not in the decision/persistence layers).

## State Changes

- `lockout_state` row upserted (created on first failure if absent, updated thereafter) with
  exactly the fields `LockoutStateMachine.LockoutDecision` returns.
- `accounts.status` transitions `ACTIVE → LOCKED` or `LOCKED → ACTIVE` when the decision's
  `AccountStatusChange` is `LOCK`/`UNLOCK` respectively, via the two new `AccountService` methods,
  in the same transaction as the `lockout_state` write (both `@Transactional`, `LockoutService`'s
  method is the transaction owner; `AccountService.lock`/`unlock` join it under Spring's default
  `REQUIRED` propagation).
- No audit row, no outbox event (see Scope > Out).

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutProperties.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add `lock(UUID)`
  / `unlock(UUID)`. No existing method's body changes.
- `services/auth/src/main/resources/application.properties` — add the three
  `themistra.auth.lockout.*` keys (VERBATIM per `design.md` §4c).

## Files NOT to Modify

- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java` (T11, frozen).
- `services/auth/src/main/java/com/themistra/auth/account/Account.java`,
  `AccountStatus.java` — `lock()`/`unlock()` guards unchanged.
- `AccountService.resetPassword` — unchanged (see Scope > Out).
- Any Flyway migration (`V1`-`V5`, all already applied; L1 immutability).
- Anything under `spec/`.
- Any `mfa/`, `apikey/`, `admin/`, `token/`, `events/`, `common/` file.

## Acceptance Criteria

- **AC1 (→ R16/R19).** `recordFailedAttempt` on an account with no existing `lockout_state` row
  treats it as a zero-value snapshot, calls `evaluate(..., FAILURE)`, and persists a new row with
  exactly the returned fields.
- **AC2 (→ R17).** When the decision is `LOCK`, `lockout_state` is written and
  `accountService.lock(accountUuid)` is called, in the same transaction; `Account.status` becomes
  `LOCKED`.
- **AC3 (→ R18).** `recordSuccessfulAttempt` persists the zeroed row; when the decision is
  `UNLOCK`, `accountService.unlock(accountUuid)` is called in the same transaction;
  `Account.status` becomes `ACTIVE`.
- **AC4 (→ R19).** A decayed, non-relocking, non-unlocking decision (`NONE`) persists the updated
  counters without any `AccountService` call.
- **AC5 (→ L4).** `LockoutStateMachine` is constructed exactly once per `LockoutService` instance,
  from `LockoutProperties`' three values.
- **AC6 (→ L12).** `LockoutService.java` contains no import of `com.themistra.auth.account.Account`.
- **AC7.** A blocked attempt (machine returns `blocked=true`) results in no `lockout_state` write
  beyond the machine's own unchanged-passthrough values, and no `AccountService` call.

## Required Tests

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named) — five
  `recordFailedAttempt` calls against a real/mocked repository result in a persisted `LOCKED` row
  and exactly one `accountService.lock(...)` call.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named) — `recordSuccessfulAttempt` persists a
  zeroed row and, when previously locked, calls `accountService.unlock(...)` exactly once.
- Missing-row boundary (AC1).
- Blocked-attempt boundary — no write beyond pass-through, no `AccountService` call (AC7).
- `LockoutProperties` binds the three keys and fails startup on a missing/invalid one
  (`PasswordPolicyPropertiesTest` is the direct model).
- A Testcontainers-backed persistence round-trip proving `LockoutStateRepository` actually
  saves/loads every `lockout_state` column correctly against real Postgres (no `@DataJpaTest`
  precedent in this module — the existing convention goes straight to `@SpringBootTest` +
  `TestcontainersConfiguration`).

## Constraints

- **Transaction:** `LockoutService`'s two entry points are `@Transactional`; the `AccountService`
  call happens inside that same transaction, not after commit.
- **Thread-safety:** `LockoutStateMachine` instance is already stateless/shared-safe (T11); no new
  concurrency concern introduced by wrapping it in a `@Service`.
- **Module boundaries (L12):** enforced as stated above; `ArchitectureTest`'s existing
  `only_the_account_module_may_touch_the_Account_entity` rule already covers this without needing
  a new rule, since the only path from `authn` to `Account` is through `AccountService`'s public
  methods, which the rule already permits (it only forbids the `Account` entity class itself).
- **Null handling:** `accountUuid` and `now` are never null (`Objects.requireNonNull` at both
  `LockoutService` entry points, matching `LockoutStateMachine.evaluate`'s own convention).
- **Security:** no logging of failure counts/timestamps (same convention as T11 — audit is a
  separate, later concern).

## Open Questions

No blockers. `package.md` §11 Q5 (lifecycle-event publication) remains genuinely unresolved by the
spec author, but this task's scope explicitly emits no event either way (see Scope > Out), so it
does not block this task's implementation — only a future task's.
