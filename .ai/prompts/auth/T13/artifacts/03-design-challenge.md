# auth · T13 · Phase 3 — Design Challenge Findings

Input consumed: `artifacts/02-task-implementation-brief.md`.  
Findings below are raised against that brief before it is frozen.  
Each item follows: **Issue · Severity · Evidence · Recommended brief amendment.**

---

### 1. Failure handler invokes `LockoutService.recordFailedAttempt` for PENDING / SUSPENDED accounts

**Severity:** High

**Evidence:**
- R16 says the failed-attempt counter is incremented only when "a password login attempt fails for an `ACTIVE` account."
- `AccountUserDetailsService` currently returns `disabled=true` for `PENDING_VERIFICATION` and `SUSPENDED`, so those failures still reach the failure handler.
- The brief only gates the `recordFailedAttempt` call on account *existence* ("if the account exists, calls ..."), not on status.
- `LockoutService`’s own contract (T12 JavaDoc, lines 25–29) explicitly says callers must invoke it only for `ACTIVE` accounts or `LOCKED` accounts whose interval has elapsed — not for `PENDING`, `SUSPENDED`, or `DELETED` accounts.

**Recommended brief amendment:**
State that the failure handler must record an audit event for any resolved account, but must call `LockoutService.recordFailedAttempt` **only** when `LoginView.status()` is `ACTIVE`, or `LOCKED` with `!isCurrentlyLocked`. For `PENDING_VERIFICATION`, `SUSPENDED`, and unknown/deleted emails, only `AuditService.record(...)` is invoked.

---

### 2. The `"login.failed"` audit contract is underspecified

**Severity:** Medium

**Evidence:**
- The brief specifies only `eventType = "login.failed"` and `accountUuid` (real or `null`).
- It does not specify `AuditOutcome` (`FAILURE` is implied but not stated), `actorUuid`, `ip`, `rawUserAgent`, `traceId`, or `details`.
- R43 and `agents.md` require every security-relevant action to record actor, target, outcome, and correlation id.
- Existing `AccountService` code uses the account UUID as both target and actor for self-initiated actions; for an unknown email there is no actor.

**Recommended brief amendment:**
Add a concrete `RecordAuditEventRequest` shape:
- `eventType = "login.failed"`
- `outcome = AuditOutcome.FAILURE`
- `accountUuid` = resolved UUID, or `null` if unknown email
- `actorUuid` = resolved UUID (self-actor) for a known account, or `null` for unknown
- `ip` / `rawUserAgent` / `traceId` = captured from the request/trace context when available
- `details = Map.of()` (no extra details needed).

---

### 3. Brief rationale incorrectly claims `Account.status` flips back to `ACTIVE` only via successful login

**Severity:** Medium

**Evidence:**
- The brief states that `Account.status` only flips back via a successful login (R18), and uses that to argue why the `AccountUserDetailsService` fix is load-bearing.
- `AccountService.resetPassword` already unlocks a `LOCKED` account as part of a successful password reset (lines 208–210), and T14’s admin unlock endpoint will also transition `LOCKED → ACTIVE`.

**Recommended brief amendment:**
Correct the sentence to: “`Account.status` only flips back to `ACTIVE` via a successful login, password reset, or admin unlock.”  Then restate that the `AccountUserDetailsService` fix is still required because Spring’s `DaoAuthenticationProvider` evaluates `accountLocked` before the password check, so a normal login attempt cannot be the event that unlocks the account unless the pre-check is bypassed.

---

### 4. The "no timing difference" constraint between known and unknown failures is unachievable

**Severity:** Medium

**Evidence:**
- The brief’s Constraints section asserts "no response-shape or timing difference between known/unknown accounts in the failure handler."
- For a known `ACTIVE` account, the handler performs `AccountService.findLoginView` + `LockoutService.recordFailedAttempt` (pessimistic row-lock read/write) + `AuditService.record` (audit insert + outbox insert).
- For an unknown email, only `AccountService.findLoginView` is performed.
- These paths have measurably different DB round-trips and latency. There is no sensible way to execute identical writes for a non-existent account.

**Recommended brief amendment:**
Relax the constraint to response-shape identity only: both branches must produce the same HTTP response (redirect to `/login?error`). Remove the timing claim, or explicitly state that timing side-channels are accepted at the application layer and mitigated separately via rate limiting (O2/T31).

---

### 5. No guidance for `LOCKED` accounts whose lock interval is still active

**Severity:** Medium

**Evidence:**
- R21 requires that locked, suspended, deleted, and non-existent accounts all fail indistinguishably.
- A `LOCKED` account with a future `locked_until` will trigger Spring’s `LockedException` before password checking.
- `LockoutService.recordFailedAttempt` would treat such a call as `blocked()` and perform a no-op mutation — it is safe, but it also adds an unnecessary pessimistic-lock query.
- The brief does not state whether the handler should invoke `LockoutService` or only audit for this branch, making AC3/AC5 boundary tests ambiguous.

**Recommended brief amendment:**
Specify that for `LOCKED` accounts with `now < locked_until`, the handler records a `login.failed` audit event (with the real `accountUuid`) but does **not** call `LockoutService.recordFailedAttempt`, because the lockout state machine already blocks the attempt and no counter update is needed.

---

### 6. `login.success` lockout reset is positioned before future MFA step-up

**Severity:** Low–Medium

**Evidence:**
- The success handler is wired into `formLogin`, i.e., the password step only.
- T16–T20 add a TOTP/recovery-code step-up before the authorization code is issued; password success alone is not a completed SAS authentication.
- Resetting lockout and the returned decision may fire before the user completes MFA, which could reset a lockout that should arguably persist until full authentication succeeds.

**Recommended brief amendment:**
Add a future-compatibility note: this task resets lockout on the password-step success handler only. Full MFA step-up integration (T20) must review whether lockout reset should remain here or move to the post-MFA success boundary. This task does not decide that placement.

---

### 7. Semantics of `LockoutService.isCurrentlyLocked` when no `lockout_state` row exists are undefined

**Severity:** Low

**Evidence:**
- T12 documents the invariant that a `LOCKED` account should always have a `lockout_state` row, but also notes that invariant violation is an operator-facing data-integrity scenario.
- `recordSuccessfulAttempt` treats a missing row as a no-op, which means it will not flip `Account.status` back to `ACTIVE`.
- If `isCurrentlyLocked` returns `false` for a missing row, a `LOCKED` account with missing state could be allowed into password verification but would remain `LOCKED` forever through this path.

**Recommended brief amendment:**
Define `isCurrentlyLocked(UUID, Instant)` to return `false` when no row exists, and document this as the correct behavior under R18 (no active interval means no lock). Optionally note that a missing row while `Account.status == LOCKED` is a data-integrity condition detectable by metrics/audit and not repaired by this method.

---

### 8. Brief diverges from `design.md` §6 file map without explanation

**Severity:** Low

**Evidence:**
- `spec/auth-service/design.md` §6 lists `authn/LoginAttemptAuditService.java` as the owner of “login success/failure auditing.”
- The brief instead creates `LoginFailureHandler` and `LoginSuccessHandler` and has them call `AuditService` directly.
- This is not necessarily wrong, but it is an unstated architectural decision that could confuse the implementer or leave a stale file in the map.

**Recommended brief amendment:**
Either add a note explaining why `LoginAttemptAuditService` is intentionally not introduced for T13 (handlers already own the one-line audit calls), or update the design map in Phase 4 to remove the unused service.

---

### 9. Compile-break blocker in `SecurityChainsConfig` may be stale

**Severity:** Low

**Evidence:**
- The Open Questions section claims `SecurityChainsConfig.java` fails to compile due to a wrong-package import of `JwtAuthenticationConverter`.
- The current file imports `org.springframework.security.oauth2.jwt.JwtAuthenticationConverter`, which is the correct class.
- The stated blocker may therefore be already resolved, or the actual compile failure may have a different cause.

**Recommended brief amendment:**
Before Phase 4, verify the current compile state with `mvn -pl services/auth compile` and replace the blocker description with the actual error (if any). If it compiles, remove the blocker question and treat the wiring as straightforward.

---

### 10. Failure handler must explicitly normalize multiple `AuthenticationException` subclasses

**Severity:** Low

**Evidence:**
- The brief describes response identity for known vs unknown accounts, but the handler will receive different exception types depending on internal state: `UsernameNotFoundException` (unknown/deleted), `BadCredentialsException` (wrong password), `LockedException` (still locked), `DisabledException` (pending/suspended).
- If the custom handler inspects the exception type to choose a redirect, a regression in L5/R21 enumeration safety becomes possible.

**Recommended brief amendment:**
State that the failure handler must delegate to the same redirect behavior (`/login?error`) for **all** `AuthenticationException` subclasses it handles, without varying status, body, headers, or redirect target based on the concrete exception type.
