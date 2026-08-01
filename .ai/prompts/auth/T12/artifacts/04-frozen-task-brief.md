STATUS: FROZEN

# auth · T12 — Phase 4: Frozen Task Brief

Human-approved. Folds every Phase 3 (Kimi) finding into a final decision. Downstream phases may
not renegotiate anything below.

## Phase 3 findings — disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | High | UUID → internal `account_id` resolution unspecified, risks L12 violation | **ACCEPTED, amended.** `LockoutStateRepository` gets two native `@Query` methods (native SQL, not JPQL against the `Account` entity — no Java-level dependency on `com.themistra.auth.account.Account`, so `ArchitectureTest`'s existing rule is unaffected): `findByAccountUuidForUpdate(UUID)` (joins `lockout_state`/`accounts` by `account_uuid`, `FOR UPDATE` — also resolves Finding 4) and `findAccountIdByUuid(UUID)` (scalar, used only when inserting a brand-new row on an account's first-ever failure). Neither method appears on any `public` type — `LockoutStateRepository` stays package-private per the existing `repositories_are_never_public` ArchUnit rule. |
| 2 | High | `AccountService.lock(UUID)` as a bare `account.lock()` wrapper crashes on T11's AC7 re-lock case (`Account.status` still `LOCKED`, guard requires `ACTIVE`) | **ACCEPTED, amended.** Both new `AccountService` methods become guarded, idempotent no-ops outside their applicable precondition: `lock(UUID)` calls `account.lock()` only if `status == ACTIVE` (no-op otherwise — covers the re-lock case, where `lockout_state` still updates but `Account.status` was never anything but `LOCKED` to begin with); `unlock(UUID)` calls `account.unlock()` only if `status == LOCKED`. |
| 3 | High | No `LockoutService` entry point for T14 (admin unlock) to reuse | **ACCEPTED, amended, scope-neutral** (same reasoning as T11 Finding 8's disposition). Add `resetLockout(UUID accountUuid)` to `LockoutService` — loads the row (no-op if absent), calls `LockoutStateMachine.reset()`, persists the zeroed row, calls `accountService.unlock(accountUuid)` (guarded, so a no-op if not currently `LOCKED`). Stays inside `LockoutService.java`, already a Files-to-Create entry — no new file, no scope creep. Not wired to any caller in this task (T14 is a future task). |
| 4 | High | Concurrent attempts can lose updates on the security-critical counter | **ACCEPTED, resolved by #1.** The same native `FOR UPDATE` query load-locks the row for the transaction's duration; pessimistic over optimistic — this is a low-QPS, per-account, security-critical counter where correctness matters more than throughput, and pessimistic locking needs no retry/conflict-handling logic at the call site. **Residual, accepted, documented (not fixed):** two *concurrent first-ever* failures for the same never-before-failed account can both observe "no row exists" (nothing to lock) and both attempt an insert; the second loses to the `account_id` primary-key constraint and surfaces as a `DataIntegrityViolationException`. Extremely low-probability (requires two simultaneous first failed logins for one specific, never-failed account) and low-stakes (worst case: one of the two requests needs to be retried by its caller, exactly like `AccountService.register`'s own accepted duplicate-email race). Not fixed with upsert/retry logic — disproportionate complexity for the risk. |
| 5 | High | T11 frozen brief's "T12 must not invoke the machine for any [non-`ACTIVE`] status" (Finding 4) contradicts T11's own AC7, which requires evaluating a `LOCKED`-but-`lockedUntil`-elapsed account | **ACCEPTED, clarified.** Confirmed contradiction (T11 `04-frozen-task-brief.md:71` vs. T11 AC7). Resolution: T11's Finding 4 text was imprecisely narrow — its own tested behavior (AC7) already required the broader reading. The corrected, single eligibility precondition (documented here for T13's benefit, since `LockoutService` cannot check `Account.status` itself per L12): **a caller may invoke `LockoutService` for an account whose status is `ACTIVE`, or `LOCKED` with `lockout_state.locked_until` at or before the evaluation instant.** Not `PENDING_VERIFICATION`, `SUSPENDED`, or `DELETED`. This is a clarification of T11's own intent, not a behavior change to T11's code (T11's tests already exercise exactly this broader case). |
| 6 | Medium | `recordSuccessfulAttempt` behavior on a missing `lockout_state` row is undefined | **ACCEPTED, amended.** A missing row on success is a no-op: no insert (there are no counters to reset), returns the same zeroed `LockoutDecision` a zero-value snapshot would produce (`statusChange = NONE`) without touching the repository. |
| 7 | Medium | Blocked-attempt write semantics ambiguous (skip save vs. write-back-unchanged) | **ACCEPTED, amended.** When `decision.blocked() == true`: no repository save, no `AccountService` call. Matches AC7's intent explicitly rather than leaving it inferrable. |
| 8 | Medium | `resetPassword`'s stale-counter gap needs to be either fixed or explicitly documented | **CONFIRMED, already addressed — no new brief change.** The Phase 2 TIB's Scope > Out already states this explicitly, with rationale and a citation to T11 Finding 8, exactly matching Kimi's own fallback recommendation ("explicitly document this as a known, accepted limitation... do not leave it silently unresolved"). No further action. |
| 9 | Low/Medium | `LockoutProperties` validation annotations/failure mode unspecified | **ACCEPTED, amended.** `@Validated @ConfigurationProperties(prefix = "themistra.auth.lockout")`, each of the three fields `@Min(1)` (all are positive-integer-minutes/counts; `maxAttempts` additionally makes no sense at `0`), matching `PasswordPolicyProperties`'s established pattern exactly (startup fails on missing/invalid, no silent defaults). |
| 10 | Low/Medium | `LockoutState` entity primary-key mapping unspecified | **ACCEPTED, amended.** `@Id @Column(name = "account_id") private Long accountId;` — no `@GeneratedValue` (the FK to `accounts.id` *is* the PK, per `V1__auth_baseline_schema.sql:114-120`). `failedAttempts`/`lockCount`: non-null `int` columns. `lastFailedAt`/`lockedUntil`: nullable `Instant` columns. Mirrors `VerificationToken.java`'s established entity-mapping style (caller-supplied timestamps, no `@PrePersist` for business fields). |

All Phase 1 Open Questions are resolved: Q1 (call direction) was settled at Phase 2 (`LockoutService`
calls `AccountService`); Q2 (lifecycle event) remains explicitly deferred, owner = whoever picks up
`package.md` §11 Q5's resolution, not blocking this task; Q3 (`resetPassword` gap) confirmed
out-of-scope at Finding 8 above; Q4 (lazy row creation) was settled at Phase 2 and reinforced by
Finding 6 above.

---

## Task

Add `LockoutService`, `LockoutState`, `LockoutStateRepository`, and `LockoutProperties` to the
`authn` package. `LockoutService` loads/persists `lockout_state` under a pessimistic row lock,
delegates the decision to `LockoutStateMachine` (T11), and applies the resulting transition by
calling two new guarded, idempotent methods on `AccountService`: `lock(UUID)` / `unlock(UUID)`.

## Purpose

Makes `LockoutStateMachine` (T11, a pure decision function with no caller today) real: a
Spring-managed service that reads/writes the actual `lockout_state` table and actually flips
`Account.status` between `ACTIVE` and `LOCKED`. T13 (SAS auth-path wiring) and T14 (admin unlock)
will both call this service; neither is wired up by this task.

## Scope

**In:**
- `LockoutService` with three public entry points: `recordFailedAttempt(UUID, Instant)`,
  `recordSuccessfulAttempt(UUID, Instant)`, `resetLockout(UUID)` (Finding 3).
- `LockoutState` entity, `@Id` on `accountId`, no surrogate key (Finding 10).
- `LockoutStateRepository`, package-private, with a pessimistic-locking native read
  (`findByAccountUuidForUpdate`) and a scalar id-resolution native query
  (`findAccountIdByUuid`) (Findings 1/4).
- `LockoutProperties`, validated `@ConfigurationProperties` record (Finding 9), plus the three
  `themistra.auth.lockout.*` keys added to `application.properties` (`design.md` §4c, VERBATIM).
- Two new `AccountService` methods, `lock(UUID)` / `unlock(UUID)` — guarded, idempotent
  (Finding 2), the only sanctioned path from `authn` to the `Account` entity (L12).
- Missing `lockout_state` row: implicit zero-value snapshot on failure (creates the row); no-op,
  no insert, on success (Finding 6) or reset.
- Blocked attempts: no repository write, no `AccountService` call (Finding 7).

**Out:**
- Wiring into the SAS authentication path — T13.
- Any audit event (`login.failed`, `account.locked`) — T13.
- Any outbox/lifecycle event on lock/unlock — deliberately deferred, `package.md` §11 Q5 is
  unresolved by the spec author; emitting nothing is the reversible default.
- The admin-unlock endpoint itself and wiring `resetLockout` to it — T14. `resetLockout` exists in
  this task only as a ready-to-call method.
- Any change to `AccountService.resetPassword` — confirmed out of scope (Finding 8).
- Any change to `LockoutStateMachine.java` (T11, frozen, read-only dependency).
- Upsert/retry logic for the concurrent-first-failure race (Finding 4's accepted residual risk).

## Business Rules

- R16 — failed attempt for an eligible account increments the counter (decision: T11; persistence
  + `Account` application: this task).
- R17 — 5th failure locks for the base duration, increments `lock_count` (decision: T11;
  persistence + `Account.lock()`: this task).
- R18 — post-unlock success resets the counter and unlocks (decision: T11; persistence +
  `Account.unlock()`: this task).
- R19 — counter decays after 30 minutes of inactivity (decision: T11; persistence: this task).

**Eligibility precondition (corrected per Finding 5):** a caller may invoke `LockoutService` for
an account whose status is `ACTIVE`, or `LOCKED` with `locked_until` at or before the evaluation
instant. Not `PENDING_VERIFICATION`, `SUSPENDED`, or `DELETED`. `LockoutService` cannot check this
itself (L12 — no `Account` import); it is the caller's (T13's) responsibility, documented here for
that future task's benefit.

## Locked Decisions

- L4 — 5/30-min/15-min, doubling via `lock_count`. Already fully encoded in `LockoutStateMachine`;
  this task supplies the three constants via `LockoutProperties` and constructs the machine once,
  as a long-lived field.
- L12 — module boundaries, ArchUnit-enforced. `LockoutService`/`LockoutStateRepository` never
  import `com.themistra.auth.account.Account`. The native `@Query` SQL strings in
  `LockoutStateRepository` reference the `accounts` table by name, not the `Account` Java class —
  no bytecode dependency, `ArchitectureTest`'s existing rule is satisfied without a new rule.

## Dependencies

- `LockoutStateMachine` (T11) — constructed once inside `LockoutService`'s constructor from
  `LockoutProperties` (`new LockoutStateMachine(properties.maxAttempts(),
  Duration.ofMinutes(properties.windowMinutes()), Duration.ofMinutes(properties.baseLockMinutes()))`).
- `AccountService.lock(UUID)` / `unlock(UUID)` — new, guarded, idempotent (Finding 2).
- `java.time.Clock` — existing bean; `now` is caller-supplied to `LockoutService`'s methods
  (determinism convention, matches T11).
- Config keys (`design.md` §4c, VERBATIM):
  ```properties
  themistra.auth.lockout.max-attempts=5
  themistra.auth.lockout.window-minutes=30
  themistra.auth.lockout.base-lock-minutes=15
  ```

## Inputs

- `recordFailedAttempt(UUID accountUuid, Instant now)`
- `recordSuccessfulAttempt(UUID accountUuid, Instant now)`
- `resetLockout(UUID accountUuid)` — no `now` parameter; `LockoutStateMachine.reset()` is
  unconditional and time-independent.

## Outputs

All three methods return the `LockoutDecision` (T11) produced, so a future caller (T13/T14) can
inspect `blocked`/counters without a second read.

## State Changes

- `lockout_state` row created (first failure) or updated (subsequent calls), exactly the fields
  `LockoutDecision` returns; no write at all for a blocked attempt or a no-row success.
- `accounts.status` transitions via the two guarded `AccountService` methods, in the same
  transaction as the `lockout_state` write (`LockoutService`'s methods are `@Transactional` and
  own the transaction; `AccountService.lock`/`unlock` join it under Spring's default `REQUIRED`
  propagation).
- No audit row, no outbox event.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutProperties.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — add guarded
  `lock(UUID)` / `unlock(UUID)`. No existing method's body changes.
- `services/auth/src/main/resources/application.properties` — add the three
  `themistra.auth.lockout.*` keys (VERBATIM).

## Files NOT to Modify

- `LockoutStateMachine.java` (T11, frozen).
- `Account.java`, `AccountStatus.java` — guards unchanged.
- `AccountService.resetPassword` — unchanged (Finding 8).
- Any Flyway migration (all applied; L1 immutability).
- Anything under `spec/`.
- Any `mfa/`, `apikey/`, `admin/`, `token/`, `events/`, `common/` file.

## Acceptance Criteria

- **AC1 (→ R16/R19).** No existing row → zero-value snapshot → `evaluate(..., FAILURE)` →
  persisted new row with exactly the returned fields.
- **AC2 (→ R17).** Decision `LOCK` → `lockout_state` written and `accountService.lock(...)`
  called, same transaction; no-op on the `Account` side if already `LOCKED` (Finding 2's guard).
- **AC3 (→ R18).** Success persists the zeroed row; decision `UNLOCK` → `accountService.unlock(...)`
  called, same transaction; no-op on the `Account` side if already `ACTIVE`.
- **AC4 (→ R19).** Decision `NONE` persists updated counters, no `AccountService` call.
- **AC5 (→ L4).** `LockoutStateMachine` constructed exactly once per `LockoutService` instance.
- **AC6 (→ L12).** No import of `com.themistra.auth.account.Account` anywhere in `authn`'s new
  files.
- **AC7.** Blocked attempt → no repository write, no `AccountService` call (Finding 7).
- **AC8 (→ Finding 6).** Success on a missing row → no insert, zeroed decision returned.
- **AC9 (→ Finding 3).** `resetLockout` zeroes any existing row (or no-ops if absent) and calls
  `accountService.unlock(...)`, guarded.
- **AC10 (→ Finding 4).** Two sequential (not concurrent) evaluations for the same account never
  lose an update — proven by a pessimistic-lock-respecting test, not just single-threaded logic.

## Required Tests

- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` (named) — five
  `recordFailedAttempt` calls → persisted `LOCKED` row, `accountService.lock(...)` called exactly
  once.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named) — `recordSuccessfulAttempt` persists a
  zeroed row; when previously locked, `accountService.unlock(...)` called exactly once.
- Missing-row boundary on failure (AC1) and on success (AC8, Finding 6).
- Blocked-attempt boundary — no write, no `AccountService` call (AC7).
- Re-lock boundary (Finding 2/5) — a failure evaluated while `Account.status` is still `LOCKED`
  (the AC7-from-T11 case) does not throw, and `lock(UUID)`'s guard correctly no-ops the redundant
  `Account`-side transition while `lockout_state` still updates.
- `resetLockout` on a locked account and on an already-clean account (AC9).
- `LockoutProperties` binds the three keys, fails startup on a missing/invalid one.
- Testcontainers-backed persistence round-trip: `LockoutStateRepository`'s native queries
  (`findByAccountUuidForUpdate`, `findAccountIdByUuid`) resolve correctly against real Postgres —
  unit-mocked tests cannot prove native SQL correctness.

## Constraints

- **Transaction:** `LockoutService`'s three entry points are `@Transactional`; the
  `AccountService` call happens inside that same transaction.
- **Concurrency:** pessimistic row lock (`FOR UPDATE`) on read, for the transaction's duration
  (Finding 4). Accepted residual: concurrent *first-ever* failures for one account can race on
  insert; documented, not engineered around (Finding 4 disposition).
- **Thread-safety:** `LockoutStateMachine` instance already stateless/shared-safe (T11).
- **Module boundaries (L12):** no `Account` import in `authn`'s new files; native SQL, not JPQL
  against the `Account` entity, for the UUID→id resolution.
- **Null handling:** `accountUuid`/`now` never null (`Objects.requireNonNull` at every entry
  point, matching T11's convention).
- **Security:** no logging of failure counts/timestamps (same convention as T11).

## Open Questions

No blockers. `package.md` §11 Q5 (lifecycle-event publication) remains genuinely unresolved by the
spec author but does not block this task (see Scope > Out).
