# auth · T12 — Phase 1: Specification Extraction

Extraction only — no design, no implementation. Scope: T12 ("Lockout service") exactly as stated
in `tasks.md` item 12. Starting ID set is the header's; widened only where noted below.

## Business Rules

- **R16.** A failed password login attempt for an `ACTIVE` account increments the per-account
  failed-attempt counter and records a `login.failed` audit event. *(The counter decision itself
  is `LockoutStateMachine`'s job, T11, already done; audit emission is explicitly T13's job per
  `tasks.md` item 13's own wording — T12 only has to load/persist the machine's decision.)*
- **R17.** Reaching 5 failed attempts within the rolling 30-minute window transitions the account
  to `LOCKED` for 15 minutes, increments `lock_count`, and records an `account.locked` audit
  event. *(Same split: the lock decision is T11's; T12 must apply it — persist `lockout_state` and
  call `Account.lock()` via `AccountService`; audit emission is T13's.)*
- **R18.** Once a locked account's lockout interval has elapsed, the next authentication attempt
  is allowed; if it succeeds, the account transitions to `ACTIVE` and the failed-attempt counter
  and `lock_count` reset. *(T12 must apply this: persist the reset `lockout_state` row and call
  `Account.unlock()` via `AccountService` when the machine's decision signals it.)*
- **R19.** If the failed-attempt counter does not reach 5 within 30 minutes of the last failure,
  it decays to zero. *(Decided by `LockoutStateMachine`; T12 persists whatever it returns —
  T12 itself performs no time-window arithmetic of its own.)*

**Not in this task's scope, cited only as forward context:**
- **R20** (admin unlock, `POST /admin/accounts/{accountUuid}/unlock`) — `tasks.md` item 14, T14.
  `LockoutStateMachine.reset()` (T11) exists specifically so T14 can reuse it later; T12 does not
  need to expose anything admin-facing.
- **R21** (uniform rejection for `LOCKED`/`SUSPENDED`/`DELETED`/non-existent accounts) — governs
  the SAS authentication path, `tasks.md` item 13/15/20, T13.

## Locked Decisions

- **L4.** 5 failed attempts / rolling 30-minute window / 15-minute base lock, doubling via
  `lock_count` until reset, counter decays 30 minutes after the last failure. Already fully
  encoded in `LockoutStateMachine` (T11); T12 must configure and construct it with these exact
  values via `LockoutProperties`, not re-implement or alter the rule.
- **L12.** No feature module may import an entity class from another feature module; ArchUnit-
  enforced (`ArchitectureTest.only_the_account_module_may_touch_the_Account_entity`). Directly
  constrains this task: `LockoutService` (package `authn` per `design.md` §6) cannot import
  `Account`. It must reach `Account.lock()`/`unlock()` through `AccountService`'s public API.

## Files involved

**Existing, to read/extend:**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateMachine.java` (T11) — the
  decision engine this service wraps. Read-only; not modified by this task.
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `resetPassword`
  (line ~208-210) already calls `account.unlock()` directly, bypassing `lockout_state` entirely.
  Whether this task modifies that call site is an open question below, not decided here.
- `services/auth/src/main/java/com/themistra/auth/account/Account.java` — `lock()`/`unlock()`
  guarded transitions (read-only reference; T12 cannot import this class per L12, only reason
  about its documented behavior).
- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql:114-120` —
  `lockout_state` table, already applied, not modified (V1 is immutable, L1).
- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql` —
  `idx_lockout_state_locked_until`, already applied.
- `services/auth/src/main/resources/application.properties` — `themistra.auth.lockout.*` keys do
  not exist yet; `design.md` §4c (VERBATIM artifacts) specifies the exact keys to add (see
  Dependencies below).

**New, expected by `design.md` §6's package map (`authn/`):**
- `LockoutState.java` — JPA entity for `lockout_state`.
- `LockoutStateRepository.java` — package-private `JpaRepository<LockoutState, Long>`.
- `LockoutProperties.java` — `@ConfigurationProperties` record for the three L4 constants.
- `LockoutService.java` — the task's namesake: loads/updates `lockout_state`, calls
  `LockoutStateMachine.evaluate(...)`, applies `Account.lock()`/`unlock()` via `AccountService`.

## Dependencies

- `LockoutStateMachine` (T11) — `evaluate(LockoutSnapshot, Instant, LockoutAttemptOutcome)` →
  `LockoutDecision`; `reset()`. Constructor `(int maxAttempts, Duration decayWindow, Duration
  baseLockDuration)`.
- `AccountService` — the only sanctioned path to `Account.lock()`/`unlock()` (L12). No method
  exists yet for "lock/unlock this account by UUID" — whether one is added to `AccountService`
  itself, or `LockoutService` is called *by* `AccountService`, is undecided (Open Question below).
- `java.time.Clock` — existing bean (`SecurityBeansConfig.clock()`), constructor-injected, no
  `Instant.now()` inline (established convention, confirmed T11 and every existing service class).
- Config keys (`design.md` §4c, VERBATIM — copy exactly):
  ```properties
  themistra.auth.lockout.max-attempts=5
  themistra.auth.lockout.window-minutes=30
  themistra.auth.lockout.base-lock-minutes=15
  ```
- `lockout_state` table columns: `account_id BIGINT PK/FK`, `failed_attempts INT`,
  `last_failed_at TIMESTAMPTZ`, `locked_until TIMESTAMPTZ`, `lock_count INT` — exact match to
  `LockoutStateMachine.LockoutSnapshot`'s four business fields.
- `contracts/events/auth/security-audit.v1.schema.json` — listed in this task's header but does
  **not exist in the repo** (confirmed by search). Not a hard dependency for T12 itself (T12 does
  not emit audit/outbox events per the R16/R17 split above), but T13 will need it.

## Acceptance Criteria

Derived from R16-R19/L4 as applied to a persistence-backed service (the pure-logic ACs already
live in `T11/artifacts/04-frozen-task-brief.md` and are not repeated here — T12's ACs are about
correctly *wrapping* that engine, not re-deriving its rules):

- **AC1 (→ R16/R19).** Given an account with an existing (or absent) `lockout_state` row,
  recording a failed attempt loads the current row (or treats a missing row as a fresh
  `LockoutSnapshot` with all-zero/null fields), calls `LockoutStateMachine.evaluate(...,
  FAILURE)`, and persists exactly the returned `failedAttempts`/`lastFailedAt`/`lockedUntil`/
  `lockCount`.
- **AC2 (→ R17).** When the machine's decision signals `AccountStatusChange.LOCK`, the service
  calls into `AccountService` so `Account.lock()` fires, transitioning `ACTIVE → LOCKED`, in the
  same unit of work as the `lockout_state` write (atomicity — see Open Questions).
- **AC3 (→ R18).** Recording a successful attempt loads the current row, calls
  `LockoutStateMachine.evaluate(..., SUCCESS)`, persists the zeroed result, and — when the
  decision signals `AccountStatusChange.UNLOCK` — calls into `AccountService` so `Account.unlock()`
  fires, transitioning `LOCKED → ACTIVE`, in the same unit of work.
- **AC4 (→ R19).** A missing `lockout_state` row (never-failed account) is handled without error —
  either an implicit zero-value snapshot or a lazily-created row; the task must pick one and does
  not yet specify which.
- **AC5 (→ L4).** `LockoutStateMachine` is constructed exactly once (a Spring bean) from
  `LockoutProperties`' three values — `5`, `Duration.ofMinutes(30)`, `Duration.ofMinutes(15)` in
  the default profile — never re-instantiated per call.
- **AC6 (→ L12).** `LockoutService` (package `authn`) contains no import of
  `com.themistra.auth.account.Account`; verified by the existing `ArchitectureTest` rule with no
  new exception needed, or a new rule is added if the chosen wiring direction requires one.

## Tests required

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named, `package.md` §8) — at this
  layer, proves the service-level wiring: five recorded failures against real/mocked persistence
  result in a persisted `LOCKED` `lockout_state` row and `Account.lock()` invoked exactly once.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named, `package.md` §8) — proves a recorded success
  persists the zeroed row and, when previously locked, calls `Account.unlock()` exactly once.
- Boundary: recording a failure/success for an account with no existing `lockout_state` row does
  not throw and produces the correct first-write behavior.
- Boundary: recording a failure while already locked (`blocked=true` from the machine) results in
  no `lockout_state` write beyond what the machine's no-op decision specifies, and no
  `Account.lock()`/`unlock()` call.
- Persistence round-trip: a `LockoutState` entity saved and reloaded via
  `LockoutStateRepository` preserves every field exactly (nullable `lastFailedAt`/`lockedUntil`
  included) — likely a Testcontainers-backed test per this module's established persistence-test
  convention (no `@DataJpaTest`-only precedent exists here).
- `LockoutProperties` binds the three `themistra.auth.lockout.*` keys correctly and fails startup
  if one is missing/invalid, mirroring `PasswordPolicyPropertiesTest`'s pattern.

## Open Questions

- **Q1 (blocker for design).** Which direction does the `LockoutService` ↔ `AccountService` call
  go? Either (a) `LockoutService` is injected with `AccountService` and calls a new
  `AccountService.lock(UUID)`/`unlock(UUID)` method after persisting `lockout_state`, or (b)
  `AccountService` is injected with `LockoutService`, calls it to get a `LockoutDecision`, and
  applies `Account.lock()`/`unlock()` itself in the same transaction it already controls. No
  precedent for either shape exists in this codebase (confirmed at Phase 0 — `authz`/`audit` both
  avoid this kind of cross-module service dependency entirely). L12 forbids only direct `Account`
  entity access, not a service-to-service call in either direction, so both are legal; this is an
  architectural choice, not a requirements gap. Must be resolved at Phase 2/3, not before.
- **Q2 (package.md §11, Q5 — genuine spec-author question, unresolved).** *"Is lock/unlock
  published only as an `auth.security.audit` mirror, or also as a lifecycle event on
  `auth.user.lifecycle`?"* Bears on whether `LockoutService` (or its caller) needs to also call
  `outboxPublisher.publish(...)` with a `UserLifecycleEventPayload`-shaped event when `Account`
  transitions via lock/unlock, the same way `AccountService.suspend`/`reinstate`/`delete` already
  do. R16/R17's audit-emission half is T13's scope per the Business Rules section above, but this
  question is about the *lifecycle* event specifically, which is a different mechanism
  (`auth.user.lifecycle` topic) than the audit trail. Not resolvable from the spec package as
  written; flagged for the human at Phase 4 the same way T11 escalated its own genuine design
  tensions.
- **Q3.** Does this task also close the pre-existing gap in `AccountService.resetPassword` (calls
  `account.unlock()` today but never touches `lockout_state`, leaving stale counters on an
  `ACTIVE` account after a password-reset-driven unlock)? `tasks.md` item 12's one-line
  description doesn't say either way, and T11's frozen brief (Finding 8) flagged this exact gap
  without assigning it a task number. Not a hard blocker — T12 can be implemented either way — but
  the decision changes `resetPassword`'s Files-to-Modify status, so it needs to be settled before
  Phase 4 freezes scope.
- **Q4.** Does `LockoutService` need a "lazily create the row on first failure" write path, or
  should every `ACTIVE` account get a `lockout_state` row at registration time (`Account.register`,
  T02) so it's never missing? Neither `requirements.md` nor `design.md` specifies this; the schema
  itself allows either (no `NOT NULL` FK requiring a row to exist per account). Affects AC4's exact
  shape; not a blocker, resolvable as an implementer decision at Phase 2 if the human doesn't want
  to weigh in.
