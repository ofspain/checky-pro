# auth · T13 — Phase 1: Specification Extraction

Extraction only — no design, no implementation. Scope: T13 ("Login failure/success tracking")
exactly as stated in `tasks.md` item 13. Starting ID set is the header's (R16, R18, R43, L4),
widened only where noted below.

## Business Rules

- **R16.** IF a password login attempt fails for an `ACTIVE` account, THEN the system SHALL
  increment the per-account failed-attempt counter and record a `login.failed` audit event.
  *(Counter decision: T11's `LockoutStateMachine`. Persistence: T12's `LockoutService`. This
  task's job: call `LockoutService.recordFailedAttempt` from the real SAS failure path, and call
  `AuditService.record(...)` with `eventType = "login.failed"`.)*
- **R18.** WHEN a locked account's lockout interval has elapsed, THEN the system SHALL allow the
  next authentication attempt; IF it succeeds, THEN the system SHALL transition the account to
  `ACTIVE` and reset the failed-attempt counter and `lock_count`. *(This task's job: call
  `LockoutService.recordSuccessfulAttempt` from the real SAS success path.)*
- **R43.** WHEN any security-relevant action occurs (login success/failure, lock, unlock, MFA
  events, password/key changes, token reuse, API-key operations), THEN the system SHALL append an
  `auth_audit` row and mirror a reduced event to `auth.security.audit` via the outbox. *(Already
  fully implemented by the pre-existing `AuditService.record(...)`, confirmed at Phase 0 — this
  task's scope is calling it with the right `eventType`, not building new audit plumbing. The task
  statement only explicitly names `login.failed`; whether a successful login also needs an audit
  call is Open Question 3 below.)*

## Locked Decisions

- **L4.** 5/30-min/15-min lockout, doubling via `lock_count`. Already fully implemented (T11
  logic, T12 persistence); this task supplies no new lockout arithmetic, only the real call sites.
- **L5.** Enumeration-safe responses — login must not reveal whether an email exists or whether an
  account is locked/suspended/deleted. Directly constrains how this task resolves an account UUID
  from a failed login attempt (Open Question 1): any lookup this task adds must not create a new
  timing or response-shape signal distinguishing "unknown email" from "known email, wrong
  password" from "known email, locked account."
- **L12.** Module boundaries, ArchUnit-enforced. Whatever class(es) this task adds must not import
  `Account` directly — same constraint T12's `LockoutService` already satisfies; this task calls
  `LockoutService`/`AuditService`, not `Account`, directly.

## Files involved

**Existing, to read/extend:**
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` — the
  `applicationChain` bean's `.formLogin(Customizer.withDefaults())` (line 65) is where the real
  SAS login success/failure path lives (confirmed at Phase 0: chain 1's `securityMatcher` doesn't
  cover `/login`, so chain 2's form-login handling is authoritative). **This file currently fails
  to compile** due to a pre-existing, unrelated wrong-package import (`JwtAuthenticationConverter`)
  — see Phase 0's headline finding and Open Question 4 below.
- `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java` —
  `loadUserByUsername(email)`, the `UserDetailsService` backing `DaoAuthenticationProvider`. Its
  returned `UserDetails` username is the account UUID string, not the email — relevant to how this
  task resolves a UUID on success but NOT necessarily on failure (Open Question 1).
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` (T12) —
  `recordFailedAttempt(UUID, Instant)`, `recordSuccessfulAttempt(UUID, Instant)`. Read-only
  dependency, not modified by this task.
- `services/auth/src/main/java/com/themistra/auth/audit/AuditService.java` — `record(
  RecordAuditEventRequest)`. Read-only dependency.
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — may be needed to
  resolve an email to an account UUID for the failure path (Open Question 1); `findLoginView` and
  `getAccount`-style lookups already exist. Whether this task adds a new lookup or reuses an
  existing one is a design question, not decided here.

**New, expected by `design.md` §6's package map (`authn/`), status per Phase 0:**
- `LoginAttemptAuditService.java` — proposed by `design.md`, but `AuditService` already implements
  everything R43 requires; this may end up being either a thin router or unnecessary as a distinct
  class. Not assumed either way here.
- Some form of `AuthenticationSuccessHandler`/`AuthenticationFailureHandler` (or equivalent) — no
  Spring Security handler precedent exists anywhere in this codebase (confirmed at Phase 0); this
  task is the first to need one.

## Dependencies

- `LockoutService.recordFailedAttempt(UUID accountUuid, Instant now)` /
  `.recordSuccessfulAttempt(UUID accountUuid, Instant now)` — T12, unchanged.
- `AuditService.record(RecordAuditEventRequest)` — pre-existing, unchanged. Already resolves
  `EventTopics.forAggregateType("audit")` → `auth.security.audit` internally (R43 satisfied by
  the existing implementation).
- `AccountUserDetailsService` — pre-existing, unchanged, the `UserDetailsService` this task's new
  handler(s) sit downstream of.
- `java.time.Clock` — existing bean, for `now` at the call sites (matches every prior task's
  determinism convention).
- No new config keys — `LockoutProperties` (T12) already covers the only lockout-related
  configuration this task depends on.

## Acceptance Criteria

- **AC1 (→ R16).** A failed password login for a known, `ACTIVE`-eligible account calls
  `LockoutService.recordFailedAttempt` with the account's UUID and the current instant.
- **AC2 (→ R16).** The same failed attempt calls `AuditService.record(...)` with
  `eventType = "login.failed"`, a resolvable `accountUuid` when the account is known.
- **AC3 (→ R18).** A successful login calls `LockoutService.recordSuccessfulAttempt` with the
  account's UUID (available directly from the authenticated principal — see `Files involved`) and
  the current instant.
- **AC4 (→ L5).** Whatever mechanism this task uses to resolve an account UUID for a failed
  attempt must not introduce a response-time or response-shape difference between "email doesn't
  exist," "email exists, wrong password," and "email exists, account locked" — all three must
  remain indistinguishable to the caller, matching every other enumeration-safe endpoint in this
  service.
- **AC5 (→ R43).** The `login.failed` audit event's `eventType` string is exactly `"login.failed"`
  (matching R16's own wording), not a different label — for consistency with however
  `LockoutService`'s and `AuditService`'s call sites are keyed/queried later.

## Tests required

- `shouldAppendRowAndMirrorAuditEventForLoginFailure` (named, `package.md` §8 — maps to R37 there,
  a numbering drift; the real match is R43, confirmed at Phase 0, same drift pattern as T09/T11's
  own already-logged issues) — a failed login attempt results in an `auth_audit` row and a Kafka
  mirror on `auth.security.audit` with `eventType = "login.failed"`.
- `shouldResetLockoutCounterOnSuccessfulLogin` (named, `package.md` §8) — at this layer, a
  successful login through the real SAS flow results in `LockoutService.recordSuccessfulAttempt`
  being invoked (T11/T12 already prove the downstream counter-reset logic; this task's own test
  proves the real call site wiring).
- Boundary: a failed login for an unknown email does not call `LockoutService` (there's no account
  to track) and does not create a response distinguishable from a failed login on a known email
  (L5/AC4).
- Boundary: a failed login for a non-`ACTIVE` account (e.g., already `SUSPENDED`/`DELETED`) — does
  this task's new code even run for those, or does authentication already reject them before
  reaching any new hook? Depends on resolving Open Question 2.

## Open Questions

- **Q1 (blocker for design).** How does the failure path resolve an account UUID? A bad-password
  failure never completes authentication, so the UUID-bearing `UserDetails` object
  `AccountUserDetailsService` builds may not be reachable from a standard
  `AuthenticationFailureHandler`. The submitted username (email) is available on the failed
  `Authentication` token, so a fresh lookup (e.g., via `AccountService`) may be needed — but per
  L5 (AC4), that lookup's timing/response must not create a new enumeration signal. Not resolvable
  from the spec package alone; a Phase 2/3 design question.
- **Q2 (blocker for design).** Does this task's new code run for every failed login attempt
  (including unknown emails and non-`ACTIVE` accounts), or only for attempts against a known,
  `ACTIVE`-or-`LOCKED`-but-expired account? R16's text scopes the counter/audit rule to `ACTIVE`
  accounts specifically ("IF a password login attempt fails for an `ACTIVE` account") — this
  suggests the new hook itself needs to check eligibility before calling `LockoutService`
  (mirroring T12's own documented precondition), but where that check happens (inside the new
  handler, or already enforced upstream by Spring's `accountLocked`/`UsernameNotFoundException`
  handling) is unresolved. Phase 0's Known Gap 4 (Spring's built-in `accountLocked` vs.
  `LockoutService`'s own state) is the same underlying question from a different angle.
- **Q3.** Does a successful login also need an `AuditService.record(...)` call (R43's broad text
  says "login success/failure"), or does the task statement's narrower "record `login.failed`
  audit events" mean success is intentionally left to whatever else already signals it (e.g.,
  token issuance)? Not resolvable from the spec text alone.
- **Q4 (blocker for implementation, not for design).** `SecurityChainsConfig.java` — the exact
  file this task must modify — currently fails to compile due to a pre-existing, two-line
  wrong-package-import bug (Phase 0's headline finding), unrelated in origin to this task but
  directly blocking this task's ability to build/test its own change to that file. Whether fixing
  those two import lines is in this task's scope, or must be raised as a separate prerequisite
  change, needs an explicit human decision at Phase 4 — this is not a requirements gap, it's a
  scope-boundary question the spec package cannot answer on its own.
