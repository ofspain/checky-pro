STATUS: FROZEN

# auth · T13 — Phase 4: Frozen Task Brief

Human-approved. Folds every Phase 3 (Kimi) finding into a final decision. Downstream phases may
not renegotiate anything below.

## Phase 3 findings — disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | High | Failure handler would call `LockoutService.recordFailedAttempt` for `PENDING_VERIFICATION`/`SUSPENDED` accounts, violating `LockoutService`'s own documented precondition | **ACCEPTED, amended.** Verified: `LockoutService.java`'s Javadoc explicitly restricts callers to `ACTIVE` accounts, or `LOCKED` with `locked_until` at/before now — not `PENDING_VERIFICATION`/`SUSPENDED`/`DELETED`. The failure handler now branches on `LoginView.status()`: `ACTIVE`, or `LOCKED` with `!isCurrentlyLocked(...)` → call `LockoutService.recordFailedAttempt`. Every other resolved status, and unknown accounts → audit only, no `LockoutService` call. |
| 2 | Medium | `login.failed` audit call underspecified — missing `outcome`, `actorUuid`, `ip`, `rawUserAgent`, `traceId` | **ACCEPTED, amended.** Verified `RecordAuditEventRequest`'s actual field list matches Kimi's citation exactly. Locked shape: `eventType="login.failed"`, `outcome=AuditOutcome.FAILURE`, `accountUuid`=resolved UUID or `null`, `actorUuid`=same as `accountUuid` (self-actor, matching every other self-initiated `AccountService` audit call in this codebase) or `null` for unknown accounts, `ip`/`rawUserAgent`/`traceId` captured from the request when available, `details=Map.of()`. |
| 3 | Medium | Brief incorrectly claimed `Account.status` flips back to `ACTIVE` only via successful login | **ACCEPTED, corrected.** Verified: `AccountService.resetPassword` already unlocks a `LOCKED` account (T07/T09), and T14's admin unlock will too. Corrected rationale: `Account.status` flips back via successful login, password reset, or admin unlock — the `AccountUserDetailsService` fix is still required regardless, because Spring's `DaoAuthenticationProvider` evaluates `accountLocked` *before* password verification, so a normal login attempt cannot itself be the unlocking event unless that pre-check is corrected first. |
| 4 | Medium | "No timing difference" constraint between known/unknown accounts is technically unachievable | **ACCEPTED, relaxed.** The constraint is response-shape identity only (same redirect, same status, same body) — not timing. Timing side-channels between a 1-query (unknown email) and 3-operation (known account: lookup + `LockoutService` + `AuditService`) path are accepted at the application layer; mitigated separately by rate limiting (O2/T31), not this task's concern. |
| 5 | Medium | No guidance for a `LOCKED` account whose interval is still active (`now < locked_until`) | **ACCEPTED, amended.** For this branch: Spring's own `accountLocked` check (unmodified by this task for a genuinely-still-locked account, per AC6) rejects the attempt via `LockedException` before password checking. The failure handler still records a `login.failed` audit event with the real `accountUuid`, but does **not** call `LockoutService.recordFailedAttempt` — the account is already blocked, no counter update is needed, and avoiding the call avoids an unnecessary pessimistic-lock query on every rejected attempt against an already-locked account. |
| 6 | Low-Medium | Lockout reset fires at the password-step success handler, before any future MFA step-up | **ACCEPTED, documented, not resolved.** Added as an explicit forward-compatibility note (see Constraints): this task resets lockout at the password-step success boundary only. T20 (MFA step-up integration) must review whether that placement is still correct once a step-up exists between password success and full SAS authentication. Not decided here — out of scope for T13 to pre-empt a task that doesn't exist yet. |
| 7 | Low | `isCurrentlyLocked(UUID, Instant)`'s behavior for a missing `lockout_state` row is undefined | **ACCEPTED, amended.** Returns `false` for a missing row — no active interval recorded means no lock, consistent with R18's own framing and with `LockoutService`'s existing missing-row-is-harmless philosophy (T12 Phase 9 Finding C). A `LOCKED` `Account.status` with a missing row is the same documented, operator-facing data-integrity scenario T12 already carries; this method does not repair it, it just doesn't get stuck on it either. |
| 8 | Low | Brief diverges from `design.md` §6's `LoginAttemptAuditService.java` without explanation | **ACCEPTED, documented.** `AuditService` (pre-existing) already implements everything R43 requires — persistence, outbox mirror, topic resolution. A `LoginAttemptAuditService` wrapping a single one-line call to it would be a pass-through with no behavior of its own; premature abstraction for what the two new handlers can call directly. Noted explicitly here rather than left as a silent deviation from the design map. |
| 9 | Low | Claimed the `SecurityChainsConfig.java` compile-break blocker might be stale | **REJECTED.** Verified false: re-ran `mvn -pl services/auth compile` live during this phase — identical errors to Phase 0, both files still broken (`JwtAuthenticationConverter` at `org.springframework.security.oauth2.jwt` does not exist; `OAuth2TokenType` at `org.springframework.security.oauth2.core` does not exist). Kimi's claim that the current import "is the correct class" is factually wrong — that import path is exactly the bug. |
| 10 | Low | Failure handler must normalize response across all `AuthenticationException` subclasses, not just the two named | **ACCEPTED, amended.** Explicit requirement: the handler produces the identical redirect (`/login?error`, same status, same body, same headers) for every `AuthenticationException` subclass it receives — `UsernameNotFoundException`, `BadCredentialsException`, `LockedException`, `DisabledException` alike. No branching on exception type for response shape (internal branching on resolved account status, per Finding 1/5, is fine — it only affects which internal calls fire, never the HTTP response). |

**Human-escalated decision:** the TIB's own genuine blocker — whether fixing
`SecurityChainsConfig.java`'s and `ReuseDetectingAuthorizationService.java`'s pre-existing
wrong-package-import compile break is in scope for T13 — was put to the human directly (Kimi's
attempt to dismiss it as stale was independently verified false first). **Decision: yes, fix both
imports as part of this task.** `ReuseDetectingAuthorizationService.java` is included even though
T13 doesn't otherwise touch it, because both files fail as one compilation unit — the module
cannot build with only one fixed.

All Phase 1 Open Questions are resolved: Q1 (UUID resolution on failure) resolved at Phase 2, now
sharpened by Finding 1's status-gating; Q2 (eligibility check placement) resolved by Findings 1/5
together; Q3 (success-path auditing) confirmed out of scope, unchanged from Phase 2; Q4 (the
import-bug blocker) resolved by the human decision above.

---

## Task

Wire T11/T12's lockout logic into the real SAS login flow via two new Spring Security handlers,
fix `AccountUserDetailsService`'s stale-`accountLocked` gap that would otherwise make R18
unreachable, and fix the two pre-existing wrong-package imports blocking `SecurityChainsConfig.java`
from compiling at all.

## Purpose

Makes the R16/R18/L4 chain real end-to-end: a failed password login increments the counter and
audits; a successful login clears it; an account whose lock has already expired can actually
complete a login attempt (previously impossible — Spring's own pre-authentication gate would
reject it regardless of elapsed time).

## Scope

**In:**
- `LoginFailureHandler` (new) — resolves the submitted email via `AccountService.findLoginView`;
  branches on resolved status:
  - Unknown email → `AuditService.record(...)` only, `accountUuid=null`.
  - `ACTIVE`, or `LOCKED` with `!isCurrentlyLocked` → `LockoutService.recordFailedAttempt` +
    `AuditService.record(...)` with the real `accountUuid`.
  - `PENDING_VERIFICATION`, `SUSPENDED`, `DELETED`, or `LOCKED` with `now < locked_until` →
    `AuditService.record(...)` only (real `accountUuid`, no `LockoutService` call).
  - Every branch produces the identical redirect response (Finding 4/10).
- `LoginSuccessHandler` (new) — calls `LockoutService.recordSuccessfulAttempt` with the
  authenticated principal's UUID (already the username, per `AccountUserDetailsService`).
- `LockoutService.isCurrentlyLocked(UUID, Instant)` (new method) — returns `false` for a missing
  row (Finding 7).
- `AccountUserDetailsService` fix — `accountLocked` computed via `isCurrentlyLocked`, not raw
  `Account.status == LOCKED` alone.
- `SecurityChainsConfig.java` — wire the two new handlers into `applicationChain`'s
  `.formLogin(...)`; fix the wrong-package `JwtAuthenticationConverter` import (human-approved).
- `ReuseDetectingAuthorizationService.java` — fix the wrong-package `OAuth2TokenType` import only
  (human-approved, required for the module to compile at all; no other change to this file).

**Out:**
- `LockoutStateMachine.java` (T11), `LockoutService`'s three existing methods (T12) — unchanged.
- MFA/TOTP step-up — separate tasks; Finding 6's placement note is documentation only.
- Success-path audit emission — task statement names only `login.failed` (Phase 2 scoping
  decision, unchanged).
- Rate limiting (O2/T31), admin unlock (T14).
- Any other content of `ReuseDetectingAuthorizationService.java` beyond the one-line import fix.

## Business Rules

- R16 — failed attempt for an `ACTIVE`-eligible account increments the counter and audits
  `login.failed`. Eligibility now precisely gated (Finding 1/5): `ACTIVE`, or `LOCKED` with
  `!isCurrentlyLocked`.
- R18 — once the interval elapses, the next attempt is allowed (enforced by the
  `AccountUserDetailsService` fix) and success resets/unlocks (the success handler).
- R43 — already fully implemented by `AuditService`; this task supplies the `login.failed` call
  site with the locked shape from Finding 2.

## Locked Decisions

- L4 — unchanged.
- L5 — enumeration safety: identical HTTP response across every branch (Finding 4/10), regardless
  of account status or exception subclass. Internal calls (which of `LockoutService`/`AuditService`
  fire) may differ; the response never does.
- L12 — no new `Account` import anywhere; the same pattern `LockoutService`/
  `AccountUserDetailsService` already establish.

## Dependencies

- `LockoutService.recordFailedAttempt`/`.recordSuccessfulAttempt` (T12) +
  `.isCurrentlyLocked(UUID, Instant)` (new, returns `false` for a missing row).
- `AuditService.record(RecordAuditEventRequest)` — locked call shape per Finding 2 above.
- `AccountService.findLoginView(String email)` (pre-existing).
- `java.time.Clock` (existing bean).

## Inputs

- Failure handler: `HttpServletRequest` (submitted username), `AuthenticationException` (any
  subclass, never branched on for response shape).
- Success handler: `Authentication` (principal name = account UUID string).

## Outputs

None externally observable beyond the existing default redirect behavior.

## State Changes

- `lockout_state` row created/updated via `LockoutService` (per T12's existing contract).
- `accounts.status` transitions via `LockoutService` → `AccountService` (per T12); this task's
  `AccountUserDetailsService` fix is what makes a post-expiry attempt reachable at all.
- New `auth_audit` row + Kafka mirror on every failed attempt against a *resolved* account
  (known email, any status) — not for unknown emails beyond the audit call itself, which still
  fires with `accountUuid=null`.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/authn/LoginFailureHandler.java`
- `services/auth/src/main/java/com/themistra/auth/authn/LoginSuccessHandler.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` — add
  `isCurrentlyLocked(UUID, Instant)`.
- `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java` —
  `accountLocked` via `isCurrentlyLocked`, not `Account.status` alone.
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` — wire the two
  new handlers; fix the `JwtAuthenticationConverter` import.
- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`
  — fix the `OAuth2TokenType` import only. No other change.

## Files NOT to Modify

- `LockoutStateMachine.java` (T11, frozen).
- `Account.java`, `AccountStatus.java`.
- `AuditService.java`, `EventTopics.java` — already implement R43 completely.
- Anything under `spec/`.
- Any content of `ReuseDetectingAuthorizationService.java` other than its one broken import line.

## Acceptance Criteria

- **AC1 (→ R16).** Failed login, `ACTIVE`-eligible account → `LockoutService.recordFailedAttempt`
  called.
- **AC2 (→ R16/R43).** Same failure → `AuditService.record(...)` with the locked shape (Finding 2),
  `eventType="login.failed"`, `outcome=FAILURE`.
- **AC3 (→ L5).** Unknown email → audit only (`accountUuid=null`, `actorUuid=null`), no
  `LockoutService` call, identical response to AC1's case.
- **AC4 (→ R18).** Successful login → `LockoutService.recordSuccessfulAttempt` called.
- **AC5 (→ R18, core fix).** `locked_until` already elapsed, `Account.status` still `LOCKED` →
  not rejected by Spring's pre-authentication check; attempt reaches password verification.
- **AC6 (→ L5, regression guard).** `now < locked_until` → still rejected by Spring's
  `accountLocked` check, same as before this task; `AuditService` still called (Finding 5), but
  not `LockoutService`.
- **AC7 (→ Finding 1/5).** `PENDING_VERIFICATION`/`SUSPENDED`/`DELETED`/still-`LOCKED` failures →
  audit only, never `LockoutService`.
- **AC8 (→ Finding 7).** `isCurrentlyLocked` on a missing row → `false`.
- **AC9 (→ Finding 10).** Every `AuthenticationException` subclass produces the identical response.
- **AC10 (→ human decision).** `mvn -pl services/auth compile` succeeds after this task's changes
  — the module builds end-to-end for the first time since T03.

## Required Tests

- `shouldAppendRowAndMirrorAuditEventForLoginFailure` (named).
- `shouldResetLockoutCounterOnSuccessfulLogin` (named, real call-site layer).
- Unknown-email failure (AC3).
- Each non-eligible status branch — `PENDING_VERIFICATION`, `SUSPENDED`, `DELETED`, still-`LOCKED`
  (AC7).
- `isCurrentlyLocked` boundary tests: missing row (AC8), at/before/after `locked_until` (mirrors
  T11/T12's existing boundary convention).
- The core fix (AC5) and its regression guard (AC6).
- Exception-subclass response-identity test (AC9) — likely the first test in this module needing
  `MockMvc` or an equivalent real-filter-chain harness (no precedent exists; flagged at Phase 0/1,
  a Phase 5 design decision, not resolved here).
- A full `mvn -pl services/auth compile`/`verify` run (AC10) — the first time this is possible for
  any task in this chain; also confirms nothing else in the module was broken by the import fixes.

## Constraints

- **Security/L5:** identical response across every branch and every exception subclass (Finding
  4/10) — the single most important constraint this task must not regress.
- **Transaction:** `isCurrentlyLocked` is read-only, no row lock (point-in-time check, not part of
  a read-evaluate-write cycle).
- **Module boundaries (L12):** as established.
- **Null handling:** `accountUuid`/`now` never null at any new method boundary.
- **Determinism:** `now` always caller-supplied via the injected `Clock`.
- **Forward-compatibility note (Finding 6):** lockout reset at the password-step success handler
  is this task's placement only; T20 (MFA step-up) must re-evaluate whether that's still correct
  once a step-up boundary exists between password success and full SAS authentication.
- **Scope of the import fix (human decision):** exactly two lines, one per file
  (`JwtAuthenticationConverter`, `OAuth2TokenType`), both wrong-package-only corrections. No other
  change to either file's logic, structure, or behavior.

## Open Questions

No blockers. The one genuine blocker (import-fix scope) was escalated to and resolved by explicit
human decision above.
