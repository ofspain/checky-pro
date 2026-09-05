# auth · T13 · Phase 2 — Task Implementation Brief

## Task

Wire T11/T12's already-built lockout logic into the real SAS login flow: a custom
`AuthenticationFailureHandler` and `AuthenticationSuccessHandler` (new, `authn` package) that call
`LockoutService.recordFailedAttempt`/`.recordSuccessfulAttempt`, plus a `login.failed` audit call.
Also fix a load-bearing gap this task's own analysis exposed: `AccountUserDetailsService`'s
static, `Account.status`-derived `accountLocked` flag would otherwise permanently block
authentication for any account whose lockout interval has already elapsed, contradicting R18.

## Purpose

`LockoutStateMachine` (T11) and `LockoutService` (T12) have no caller today. This task makes the
whole R16/R18/L4 chain real end-to-end: a failed password login actually increments the counter
and audits; a successful login actually clears it; and — the part neither prior task could
address — an account whose lock has already expired can actually complete a login attempt at all,
rather than being rejected by Spring's own stale `accountLocked` gate before password checking
ever runs.

## Scope

**In:**
- `AuthenticationFailureHandler` (new) — resolves the submitted username (email) via
  `AccountService.findLoginView(email)`; if the account exists, calls
  `LockoutService.recordFailedAttempt(uuid, now)` and `AuditService.record(...)` with
  `eventType = "login.failed"` and the real `accountUuid`; if it doesn't, calls only
  `AuditService.record(...)` with `accountUuid = null`. The resulting HTTP response (redirect to
  `/login?error`) is identical in both branches — L5 is preserved by response-shape identity, not
  by skipping the lookup.
- `AuthenticationSuccessHandler` (new) — the authenticated principal's name is already the account
  UUID (`AccountUserDetailsService`'s existing behavior); calls
  `LockoutService.recordSuccessfulAttempt(uuid, now)`, then proceeds with the default
  redirect/success behavior.
- **`AccountUserDetailsService` fix (proposed here, subject to Phase 3 challenge):** add a new
  read-only `LockoutService.isCurrentlyLocked(UUID, Instant)` method (T12's file, new method) and
  use it — not `Account.status == LOCKED` alone — to compute the `accountLocked` flag passed to
  Spring. `Account.status` only flips back to `ACTIVE` via a *successful* login (R18), but Spring's
  `DaoAuthenticationProvider` rejects a locked-`accountLocked` user *before* password checking —
  so without this fix, once locked, an account can never complete the very login attempt that R18
  says should be allowed once the interval elapses. This is this task's single most significant
  design decision.
- `SecurityChainsConfig.java` — wire the two new handlers into `applicationChain`'s
  `.formLogin(...)`.

**Out:**
- Any change to `LockoutStateMachine.java` (T11) or `LockoutService`'s existing three methods
  (T12) — only a new fourth read-only method is added.
- MFA/TOTP step-up (`TotpAuthenticationProvider` etc.) — separate tasks (T16+).
- Success-path audit emission — the task statement names only `login.failed`; R43's broader
  illustrative list is not treated as expanding this task's scope (see Open Questions — this is a
  deliberate scoping choice, not an oversight).
- Rate limiting (O2/T31), admin unlock (T14).
- Fixing the pre-existing `SecurityChainsConfig.java`/`ReuseDetectingAuthorizationService.java`
  wrong-package-import compile break — **explicitly not decided here**; see Open Questions.

## Business Rules

- R16 — failed attempt for a known account increments the counter and audits `login.failed`.
- R18 — once the interval elapses, the next attempt is allowed; success resets and unlocks.
  Enforcing "allowed" is this task's `AccountUserDetailsService` fix; "resets and unlocks" is the
  success handler calling `LockoutService.recordSuccessfulAttempt`.
- R43 — security-relevant actions are audited via the outbox. Already fully implemented by
  `AuditService.record(...)`; this task supplies the `login.failed` call site only.

## Locked Decisions

- L4 — unchanged; this task adds no lockout arithmetic.
- L5 — enumeration safety. The failure handler's two branches (known/unknown account) must
  produce an identical HTTP response; only the internal `LockoutService`/`AuditService` calls
  differ.
- L12 — no new class imports `Account`; `AccountUserDetailsService` already depends on
  `AccountService` (pre-existing pattern, corrected at T12 Phase 12 to no longer be described as
  novel), and the new handlers depend on `AccountService`/`LockoutService`/`AuditService` only.

## Dependencies

- `LockoutService.recordFailedAttempt`/`.recordSuccessfulAttempt` (T12, unchanged) +
  `.isCurrentlyLocked(UUID, Instant)` (new method, this task).
- `AuditService.record(RecordAuditEventRequest)` (pre-existing, unchanged).
- `AccountService.findLoginView(String email)` (pre-existing, already used by
  `AccountUserDetailsService`, reused here).
- `java.time.Clock` (existing bean).

## Inputs

- Failure handler: `HttpServletRequest` (for the submitted username), `AuthenticationException`.
- Success handler: `Authentication` (principal name = account UUID string).
- `AccountUserDetailsService`'s fix: the same `email` parameter it already receives.

## Outputs

None externally observable beyond the existing default redirect behavior — no new response body,
no new endpoint, no new claim.

## State Changes

- `lockout_state` row created/updated (via `LockoutService`, as already specified by T12).
- `accounts.status` transitions (via `LockoutService` → `AccountService`, already specified by
  T12) — this task's `AccountUserDetailsService` fix is what makes the *attempt* reachable in the
  first place when previously locked.
- New `auth_audit` row + Kafka mirror on a failed attempt against a known account (via the
  pre-existing `AuditService`).

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/authn/LoginFailureHandler.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LoginSuccessHandler.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` — add
  `isCurrentlyLocked(UUID, Instant)`.
- `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java` — compute
  `accountLocked` via the new `LockoutService` method, not `Account.status` alone.
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` — wire the two
  new handlers into `applicationChain`'s `.formLogin(...)`.

## Files NOT to Modify

- `LockoutStateMachine.java` (T11, frozen).
- `Account.java`, `AccountStatus.java`.
- `AuditService.java`, `EventTopics.java` — already implement R43 completely.
- `ReuseDetectingAuthorizationService.java` — its own import bug is unrelated to this task's own
  file (`SecurityChainsConfig.java`); not touched regardless of how Open Question 1 resolves.
- Anything under `spec/`.

## Acceptance Criteria

- **AC1 (→ R16).** A failed login for a known, `ACTIVE`-eligible account calls
  `LockoutService.recordFailedAttempt(uuid, now)`.
- **AC2 (→ R16/R43).** The same failure calls `AuditService.record(...)` with
  `eventType = "login.failed"` and the real `accountUuid`.
- **AC3 (→ L5).** A failed login for an unknown email calls `AuditService.record(...)` with
  `accountUuid = null` and does **not** call `LockoutService`; the HTTP response is identical to
  AC1's case.
- **AC4 (→ R18).** A successful login calls `LockoutService.recordSuccessfulAttempt(uuid, now)`.
- **AC5 (→ R18, the core fix).** An account whose `lockout_state.locked_until` has already passed,
  but whose `Account.status` is still `LOCKED` (nothing has flipped it yet), is **not** rejected by
  Spring's pre-authentication `accountLocked` check — the login attempt proceeds to password
  verification. A correct password then succeeds and unlocks (AC4); a wrong password is recorded
  as a normal failed attempt (AC1), which may re-lock per T11's own AC7 escalation.
- **AC6 (→ L5).** `AccountUserDetailsService`'s `accountLocked` computation change does not alter
  its behavior for a genuinely, currently-locked account (`locked_until` still in the future) — it
  remains rejected, same as before this task.

## Required Tests

- `shouldAppendRowAndMirrorAuditEventForLoginFailure` (named).
- `shouldResetLockoutCounterOnSuccessfulLogin` (named, at this real-call-site layer).
- Unknown-email failure: no `LockoutService` call, audit call with `accountUuid = null` (AC3).
- The core fix (AC5): `isCurrentlyLocked` returns `false` once `now >= locked_until` even when
  `Account.status` is still `LOCKED`; `AccountUserDetailsService` reflects that in `accountLocked`.
- Regression (AC6): `isCurrentlyLocked` returns `true` while `now < locked_until`.
- `LockoutService.isCurrentlyLocked` boundary tests mirroring T11/T12's existing at/before
  `lockedUntil` boundary convention.

## Constraints

- **Security/L5:** covered above — no response-shape or timing difference between known/unknown
  accounts in the failure handler.
- **Transaction:** `LockoutService.isCurrentlyLocked` is read-only, no lock needed (unlike
  `recordFailedAttempt`/`recordSuccessfulAttempt`'s pessimistic `FOR UPDATE OF ls`) — it's a
  point-in-time check consumed before any mutation, not part of a read-evaluate-write cycle.
- **Module boundaries (L12):** as stated above.
- **Null handling:** `accountUuid`/`now` never null at any new method's entry point, matching
  every prior task's convention in this module.
- **Determinism:** `now` is caller-supplied (the injected `Clock`), never `Instant.now()` inline.

## Open Questions

- **Blocker.** `SecurityChainsConfig.java` — the file this task must modify to wire in the new
  handlers — currently fails to compile due to a pre-existing, two-line wrong-package-import bug
  (`JwtAuthenticationConverter`, tracked as "unrelated" since T03, root-caused at this task's own
  Phase 0). This task cannot be built/tested end-to-end against that file without either fixing
  those two import lines or finding an isolated-compilation path that doesn't require it — unlike
  T09-T12, which never needed to touch this file at all. Whether fixing the import is in scope for
  this task is a human scope decision, not a design question; explicitly deferred to Phase 4.
