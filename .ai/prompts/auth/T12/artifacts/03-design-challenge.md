# auth · T12 — Phase 3: Design Challenge

Adversarial review of the Phase 2 task implementation brief against `spec/auth-service/`
(`requirements.md`, `design.md`, `tasks.md`, `package.md`, `agents.md`) and the existing
`Account` / `AccountService` code.

---

## Finding 1 — Account UUID → internal `account_id` resolution for the initial insert is unspecified and risks violating L12

- **Severity.** High
- **Issue.** The `lockout_state` table's primary key is `account_id BIGINT` (`design.md:159-165`), but `LockoutService` receives only `UUID accountUuid`. The TIB says `LockoutStateRepository` resolves it internally via a join or a `findByAccount_AccountUuid`-style query without saying how. If `LockoutState` / `LockoutStateRepository` in the `authn` package imports or maps `Account` to perform that join, it may violate L12 and the existing ArchUnit rule that only the account module may touch the `Account` entity.
- **Evidence.** TIB Inputs/Scope says the repository is keyed on `accountId` (`Long`) but the service API is `UUID`. `Account.java` exposes only `accountUuid` externally; the internal `id` is never leaked. `design.md` shows `lockout_state.account_id` is a foreign key to `accounts.id`.
- **Recommended brief amendment.** State explicitly how UUID is translated to `account_id` without crossing the module boundary. Options: (a) a package-private `LockoutStateRepository` method with a native query joining `accounts` by UUID, returning only the `Long` id; (b) a new thin `AccountService.getInternalId(UUID)` helper; or (c) a read-only `AccountIdProjection` DTO. Verify the chosen option against `ArchitectureTest`.

---

## Finding 2 — Re-lock after expiry conflicts with `Account.lock()`'s `ACTIVE`-only guard

- **Severity.** High
- **Issue.** T11's AC7 requires that a failed attempt shortly after `lockedUntil` expires can re-lock the account with doubled duration. At that moment `Account.status` is still `LOCKED` in the database. `Account.lock()` throws `InvalidAccountStateException` unless `status == ACTIVE` (`Account.java:89-92`). The TIB's new `AccountService.lock(UUID)` is described as a thin wrapper around `account.lock()` with no idempotency guard, so a valid re-lock decision will crash.
- **Evidence.** `Account.java:88-92`; TIB State Changes describes calling `accountService.lock(...)` whenever the decision is `LOCK`; TIB AC2 asserts `Account.status` becomes `LOCKED`.
- **Recommended brief amendment.** Make `AccountService.lock(UUID)` guarded: only call `account.lock()` when `status == ACTIVE`, and only call `account.unlock()` when `status == LOCKED`. This keeps the invariant that the service only mutates when necessary and supports the AC7 re-lock case.

---

## Finding 3 — No `LockoutService` entry point for admin unlock (T14)

- **Severity.** High
- **Issue.** The TIB exposes only `recordFailedAttempt` and `recordSuccessfulAttempt`. It also says T13 (SAS auth-path wiring) and T14 (admin unlock) will both call this service. There is no method for T14 to clear counters or reset `lockout_state`, so T14 cannot use the service as described.
- **Evidence.** TIB Scope/In lists only the two attempt methods; TIB Scope/Out lists T14 as a separate endpoint, not as a consumer of a service method.
- **Recommended brief amendment.** Add `resetLockout(UUID accountUuid)` (or `clearLockout`) to `LockoutService` that persists the zeroed row and calls `AccountService.unlock(UUID)` when the account is currently `LOCKED`.

---

## Finding 4 — Concurrent attempts can lose updates

- **Severity.** High
- **Issue.** The TIB does not describe any concurrency control. Two simultaneous failed login attempts for the same account can read the same `lockout_state` snapshot, evaluate separately, and overwrite each other's writes. This can cause the 5-failure threshold to be missed or `lockCount` to be miscounted.
- **Evidence.** TIB State Changes describes only a read-evaluate-write pattern with no locking. `LockoutStateMachine` is stateless, so the concurrency hazard is entirely in the persistence layer.
- **Recommended brief amendment.** Specify pessimistic locking: `LockoutStateRepository` loads the row with `@Query(..., lockMode = PESSIMISTIC_WRITE)` or Spring Data's `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Alternatively, specify optimistic locking with a `@Version` column if the team prefers; given the security-critical counter, pessimistic is safer.

---

## Finding 5 — Precondition mismatch: T11 says `ACTIVE`-only, but AC7 requires evaluating `LOCKED`-but-expired accounts

- **Severity.** High
- **Issue.** The T11 frozen brief (Finding 4) says T12's `LockoutService` must not invoke `LockoutStateMachine` for any non-`ACTIVE` account status. T12's TIB passes the same precondition to T13 (only invoking this service for accounts it already knows are `ACTIVE`-eligible). But T11's own AC7 deliberately evaluates a failed attempt after `lockedUntil` has elapsed, when the persisted `Account.status` is still `LOCKED`. The briefs contradict each other.
- **Evidence.** T11 frozen brief §Business Rules / Finding 4 disposition; T11 AC7; T12 TIB Outputs/State Changes.
- **Recommended brief amendment.** Replace the `status == ACTIVE` precondition with not `DELETED`/`SUSPENDED`; if `LOCKED`, only evaluate when `now >= lockedUntil`. Log this as an explicit T11/T12 boundary clarification in the Open Questions section.

---

## Finding 6 — Behavior of `recordSuccessfulAttempt` when no `lockout_state` row exists is undefined

- **Severity.** Medium
- **Issue.** The TIB states that a missing row on failure is treated as a zero-value snapshot. It does not say how success is handled. If success also treats a missing row as zero-value, the service may insert a row full of nulls/zeros, polluting the table. If it does not handle the missing row, it may throw.
- **Evidence.** TIB AC1 covers the failure path. AC3 covers success but assumes a row exists. There is no AC for success on a never-failed account.
- **Recommended brief amendment.** State that `recordSuccessfulAttempt` on a missing row is a no-op (no insert) because there are no counters to reset, and returns a zeroed decision with `statusChange = NONE`.

---

## Finding 7 — Blocked-attempt write semantics are ambiguous

- **Severity.** Medium
- **Issue.** AC7 says a blocked attempt results in no `lockout_state` write beyond the machine's own unchanged-passthrough values. This is ambiguous: it could mean skip the repository save entirely, or write the same values back. Writing the same values is wasteful and increases the concurrency surface area for no benefit.
- **Evidence.** TIB AC7; `LockoutStateMachine.evaluate` returns the input snapshot unchanged when `blocked = true`.
- **Recommended brief amendment.** Specify that when `decision.blocked() == true`, `LockoutService` performs no repository save at all, and makes no `AccountService` call.

---

## Finding 8 — Reset-password stale counters create a persistent inconsistency

- **Severity.** Medium
- **Issue.** The TIB flags that `AccountService.resetPassword` already calls `account.unlock()` without touching `lockout_state`, leaving stale counters behind. This is a real user-visible bug: after a password reset unlocks a locked account, the next failed attempt starts from the old `failedAttempts`/`lockCount` and may re-lock much faster.
- **Evidence.** TIB Scope/Out; `AccountService.java:208-210` calls `account.unlock()` directly. The TIB defers fixing this as scope creep.
- **Recommended brief amendment.** Either extend scope to call the new `LockoutService.resetLockout(...)` from `resetPassword`, or explicitly document this as a known, accepted limitation with a planned follow-up task. Do not leave it silently unresolved.

---

## Finding 9 — `LockoutProperties` validation and failure mode are unspecified

- **Severity.** Low / Medium
- **Issue.** The TIB lists the three config keys but does not state the validation annotations or behavior. The Required Tests mention that startup must fail on a missing/invalid value, matching `PasswordPolicyPropertiesTest`, but the brief itself does not say how that is enforced.
- **Evidence.** TIB Dependencies lists the keys verbatim. agents.md says config is bound to validated `@ConfigurationProperties` and startup fails on missing/invalid values in non-local profiles.
- **Recommended brief amendment.** Specify `LockoutProperties` uses `@NotNull @Min(1)` on all three fields and is annotated `@Validated @ConfigurationProperties`. Align with the existing `PasswordPolicyProperties` pattern.

---

## Finding 10 — `LockoutState` entity primary-key mapping is unspecified

- **Severity.** Low / Medium
- **Issue.** The TIB says the entity maps the table exactly but does not say whether `accountId` is the `@Id`, whether it uses a generated value, or how it relates to the `accounts` table. A missing or wrong JPA mapping will fail at startup under `ddl-auto=validate`.
- **Evidence.** TIB Files to Create / Scope lists the entity but gives no mapping details. `design.md` shows `account_id` is both primary key and foreign key, with no separate surrogate id.
- **Recommended brief amendment.** State `LockoutState` has `@Id @Column(name = "account_id") private Long accountId;` (no `@GeneratedValue`), plus nullable Instant columns for `lastFailedAt` and `lockedUntil`, and non-null `int` columns for `failedAttempts` and `lockCount`.
