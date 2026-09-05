# auth · T12 — Phase 6: Implementation Notes

Implements the frozen brief (`04-frozen-task-brief.md`) exactly per the plan
(`05-implementation-plan.md`). No test files touched in this phase (Phase 10's job) — production
code only, as planned.

## Changes

### `LockoutState.java` (new)

`services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java`. `@Entity` mapping
`lockout_state` exactly per `V1__auth_baseline_schema.sql:114-120`: `@Id @Column(name =
"account_id")` with no `@GeneratedValue` (the FK to `accounts.id` is the PK itself), non-null
`int` columns for `failedAttempts`/`lockCount`, nullable `Instant` columns for
`lastFailedAt`/`lockedUntil`. Static factory `of(Long accountId, LockoutDecision decision)` for
the insert path; package-visible `applyDecision(LockoutDecision)` for the update path — both
funnel through the same field-copy logic so there is exactly one place that maps a
`LockoutDecision` onto entity state. No import of anything from `account` — takes a plain `Long`.

Maps to: frozen brief Finding 10.

### `LockoutStateRepository.java` (new)

`services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java`.
Package-private (matches `AccountRepository`'s convention; `ArchitectureTest`'s
`repositories_are_never_public` rule). Two native `@Query` methods:
- `findByAccountUuidForUpdate` — `SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id =
  ls.account_id WHERE a.account_uuid = :accountUuid FOR UPDATE`. Native SQL against table/column
  names, not JPQL against the `Account` entity — confirmed by `grep`: this file has zero
  `com.themistra.auth.account` imports. Doubles as both the L12-safe UUID→row resolution (Finding
  1) and the pessimistic lock for concurrent-update safety (Finding 4) in one query.
- `findAccountIdByUuid` — scalar `SELECT a.id FROM accounts a WHERE a.account_uuid = :accountUuid`,
  used only when `LockoutService` needs to insert a brand-new row.

Maps to: frozen brief Findings 1, 4.

### `LockoutProperties.java` (new)

`services/auth/src/main/java/com/themistra/auth/authn/LockoutProperties.java`.
`@ConfigurationProperties(prefix = "themistra.auth.lockout") @Validated record` with `@Min(1)` on
all three fields (`maxAttempts`, `windowMinutes`, `baseLockMinutes`) — mirrors
`PasswordPolicyProperties`'s shape exactly: no defaults, fails startup on a missing or
non-positive value.

Maps to: frozen brief Finding 9.

### `LockoutService.java` (new)

`services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java`. `@Service`,
constructor-injected with `LockoutStateRepository`, `LockoutProperties`, `AccountService`. Builds
one `LockoutStateMachine` field from the three properties values at construction time (not a
Spring `@Bean` — matches T11's own design intent of a plain-constructor, non-Spring-aware class).

Three public, `@Transactional` entry points:

- **`recordFailedAttempt(UUID, Instant)`** — loads via `findByAccountUuidForUpdate`, builds a
  `LockoutSnapshot` (missing row → all-zero), calls `machine.evaluate(..., FAILURE)`. A blocked
  decision returns immediately with no write and no `AccountService` call (Finding 7). Otherwise
  persists (insert if the row was missing, resolving the internal id via
  `findAccountIdByUuid`; update in place otherwise) and applies the decision's `statusChange` via
  a private `applyStatusChange` switch that calls `accountService.lock`/`.unlock`/nothing.
- **`recordSuccessfulAttempt(UUID, Instant)`** — same shape, but a missing row short-circuits
  before any persistence or `AccountService` call (Finding 6: "no insert, there are no counters to
  reset"). A blocked decision behaves identically to the failure path.
- **`resetLockout(UUID)`** — calls `machine.reset()` (unconditional), updates the row only if one
  exists, but **always** calls `applyStatusChange` — safe because `AccountService.unlock` is
  itself a guarded no-op when the account isn't currently `LOCKED`. For T14's future use; not
  called by anything in this task.

`toSnapshot`, `applyStatusChange`, and `persistNewOrUpdated` are the three private helpers the
plan specified — each used by more than one public method, so the missing-row handling and the
decision→`AccountService`-call mapping each exist in exactly one place.

Maps to: frozen brief AC1-AC9, Findings 1, 2 (via `AccountService`'s guard, below), 3, 4, 5
(documented in this class's own Javadoc, since `LockoutService` is where a future caller most
needs the corrected precondition), 6, 7.

### `AccountService.java` — `lock(UUID)` / `unlock(UUID)` (new methods)

Added after the existing `getByUuid` method. Both `@Transactional`, both reuse the existing
private `getAccount(UUID)` helper — no new lookup logic. Both are **guarded**: `lock` calls
`account.lock()` only if `status == ACTIVE`; `unlock` calls `account.unlock()` only if `status ==
LOCKED`. Otherwise a silent no-op. This is the direct fix for Finding 2: without the guard, T11's
AC7 re-lock case (a failure evaluated while `Account.status` is still `LOCKED`) would call
`account.lock()` on an already-`LOCKED` account, and `Account.lock()`'s own guard
(`requireStatus(ACTIVE, "lock")`, `Account.java:89-92`) would throw `InvalidAccountStateException`.
No other method in this file changed.

Maps to: frozen brief Finding 2.

### `application.properties`

Added the three VERBATIM keys from `design.md` §4c under a new `# --- Lockout (L4) ---` section,
directly after the password-policy block: `themistra.auth.lockout.max-attempts=5`,
`.window-minutes=30`, `.base-lock-minutes=15`.

## Acceptance criteria — implementation status

| ID | Status |
|---|---|
| AC1 | Done — missing row on failure → zero snapshot → `evaluate(..., FAILURE)` → new row persisted with the returned fields |
| AC2 | Done — `LOCK` decision → row written, `accountService.lock(...)` called, same transaction; guard makes the `Account`-side call safe even when already `LOCKED` |
| AC3 | Done — success persists the zeroed row; `UNLOCK` decision → `accountService.unlock(...)` called, same transaction |
| AC4 | Done — `NONE` decision persists updated counters, no `AccountService` call (`applyStatusChange`'s `NONE` branch is a no-op) |
| AC5 | Done — `LockoutStateMachine` constructed exactly once, in `LockoutService`'s constructor |
| AC6 | Done — confirmed via `grep`: no `com.themistra.auth.account.Account` import anywhere in the four new `authn` files (only `AccountService` is imported, in `LockoutService.java`) |
| AC7 | Done — `decision.blocked()` short-circuits both `recordFailedAttempt` and `recordSuccessfulAttempt` before any write or `AccountService` call |
| AC8 | Done — `recordSuccessfulAttempt`'s `existing.isEmpty()` short-circuit skips persistence entirely; the returned decision is still the correct zeroed/`NONE` shape (computed by the machine against a zero snapshot, not fabricated separately) |
| AC9 | Done — `resetLockout` updates an existing row's fields but skips persistence when absent, while always attempting the (guarded, safe) `AccountService` call |
| AC10 | Not verified by this phase — requires two real, concurrent threads/transactions against a real Postgres instance; the `FOR UPDATE` clause is in place, but proving it actually serializes concurrent access is Phase 10's integration-test job, not something a single-threaded implementation phase can demonstrate |

## Deviations from the plan

None. Every file, method signature, and private-helper shape matches
`05-implementation-plan.md` exactly. The plan's `LockoutState.applyDecision` was described as
usable "though `LockoutService` calls `save()` explicitly anyway" — implemented exactly that way:
`applyDecision` mutates in place, `LockoutService` still calls `repository.save(state)`
immediately after, for consistency with every other repository call site in this codebase (none
of which rely on implicit dirty-checking alone).

## Build verification

`mvn -pl services/auth compile` still cannot run to completion — the pre-existing, unrelated
`token` package compile break (`OAuth2TokenType`/`JwtAuthenticationConverter` not found, tracked
since T03) blocks it, unchanged and untouched by this task. Unlike T11 (zero dependencies), this
task's classes have real Spring/JPA/cross-module dependencies, so verification used `javac`
against the module's fully resolved test-scope classpath with `-sourcepath` so the compiler could
pull in whatever it actually needed transitively:

```
javac -d <out> -cp "$(cat /tmp/auth-cp.txt)" -sourcepath services/auth/src/main/java \
  services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java \
  services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java \
  services/auth/src/main/java/com/themistra/auth/authn/LockoutProperties.java \
  services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java \
  services/auth/src/main/java/com/themistra/auth/account/AccountService.java
```

Clean compile, no errors, no warnings. Confirmed via the compiler's own output directory that only
`RefreshTokenTracker` (and its own two dependencies) were pulled in from the `token` package —
never the two broken classes (`ReuseDetectingAuthorizationService`, `SecurityChainsConfig`), since
nothing in this task's dependency chain references them.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 12.
- Requirements: R16, R17, R18, R19 (`requirements.md`).
- LOCKED decisions: L4 (constants wired through unchanged), L12 (module boundary — verified clean
  by direct `grep`, not just by inspection).
- Frozen brief: `04-frozen-task-brief.md` — all four authorized Files to Create present; both
  authorized Files to Modify (`AccountService.java`, `application.properties`) updated exactly as
  scoped; no file outside that list touched.
