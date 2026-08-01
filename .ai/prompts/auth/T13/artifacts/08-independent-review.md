# auth · T13 · Phase 8 — Independent Code Review Findings

Inputs consumed: `artifacts/07-self-review.md`, the Phase 6 implementation files, `04-frozen-task-brief.md`, and `spec/auth-service/agents.md`.
Findings only — no rewrites. Format: Issue · Evidence · Recommendation · Confidence.

---

## 1. `LoginFailureHandler` bookkeeping failures can break the uniform failure redirect

**Evidence:**
- `LoginFailureHandler.onAuthenticationFailure` calls `recordFailure(request)` before delegating to `super.onAuthenticationFailure(...)` (`LoginFailureHandler.java:56`).
- `recordFailure` executes `accountService.findLoginView(email)`, optionally `lockoutService.recordFailedAttempt(...)`, and `auditService.record(...)`.
- If any of these calls throws, the exception propagates and the user receives a server error instead of the required `/login?error` redirect.
- This violates L5/AC9's response-shape identity guarantee.

**Recommendation:**
Wrap the internal bookkeeping in `recordFailure` with `try/catch`, log the failure, and always delegate to `super.onAuthenticationFailure(...)`. The lockout/audit side effects are best-effort, not load-bearing for the response.

**Confidence:** High.

---

## 2. `LoginSuccessHandler` lets a `LockoutService` failure break a successful login

**Evidence:**
- `LoginSuccessHandler.onAuthenticationSuccess` calls `lockoutService.recordSuccessfulAttempt(...)` before `super.onAuthenticationSuccess(...)` (`LoginSuccessHandler.java:37-38`).
- A DB or transaction failure while clearing the lockout would prevent a user with valid credentials from logging in.

**Recommendation:**
Wrap `recordSuccessfulAttempt` in `try/catch`, log the error, and always call `super.onAuthenticationSuccess(...)` afterward.

**Confidence:** High.

---

## 3. `login.failed` audit event is lost if `LockoutService.recordFailedAttempt` throws

**Evidence:**
- `recordFailure` invokes `lockoutService.recordFailedAttempt(...)` before `auditFailure(...)` (`LoginFailureHandler.java:74-77`).
- If `recordFailedAttempt` throws, the audit event is never emitted, creating an audit-trail gap for R43.

**Recommendation:**
Call `auditService.record(...)` before `lockoutService.recordFailedAttempt(...)`, or wrap the two calls independently so a failure in one does not suppress the other.

**Confidence:** High.

---

## 4. Session-stored exception still carries enumeration risk for a future custom login page

**Evidence:**
- As noted in Phase 7, `SimpleUrlAuthenticationFailureHandler.saveException` stores the concrete `AuthenticationException` subclass in the session.
- `LockedException`, `DisabledException`, `BadCredentialsException`, and `UsernameNotFoundException` differ by type and message.
- A future O4 custom login page that displays the stored exception would re-open the enumeration channel closed by AC9.

**Recommendation:**
Override `saveException(...)` in `LoginFailureHandler` to store only a generic `AuthenticationException('Bad credentials')`, or clear the session attribute. Do not rely on future page authors.

**Confidence:** High.

---

## 5. No unit tests for the new handlers or for `isCurrentlyLocked`

**Evidence:**
- `services/auth/src/test/java/com/themistra/auth/authn/` has no `LoginFailureHandlerTest`, `LoginSuccessHandlerTest`, or tests for `LockoutService.isCurrentlyLocked`.
- The frozen brief requires named tests and `isCurrentlyLocked` boundary tests (AC8, AC9).

**Recommendation:**
Phase 10 must add handler unit tests (mock request/response/authentication) and `LockoutService` boundary cases for missing row, null `lockedUntil`, and before/at/after `lockedUntil`.

**Confidence:** High.

---

## 6. `AccountUserDetailsServiceTest` no longer compiles

**Evidence:**
- The service constructor now requires `LockoutService` and `Clock`.
- `AccountUserDetailsServiceTest` still constructs it with a single argument.

**Recommendation:**
Phase 10 must update the test constructor and add coverage for the new `isCurrentlyLocked` interactions.

**Confidence:** High.

---

## 7. `ReuseDetectingAuthorizationServiceTest` import is broken by the production import fix

**Evidence:**
- Production import was corrected to `org.springframework.security.oauth2.server.authorization.OAuth2TokenType`.
- The test still imports the old wrong package, blocking test compilation (Phase 6 notes).

**Recommendation:**
Phase 10 must apply the same one-line import correction in the test file; it is a direct, necessary consequence of the production change.

**Confidence:** High.

---

## 8. `LoginSuccessHandler` assumes the principal name is always UUID-shaped

**Evidence:**
- `UUID.fromString(authentication.getName())` is called without validation (`LoginSuccessHandler.java:37`).
- If the handler is ever reached with a non-UUID principal name, it throws `IllegalArgumentException`.

**Recommendation:**
Guard the parse; log and skip lockout reset for unparseable principal names instead of throwing.

**Confidence:** Medium.

---

## 9. `LoginFailureHandler` hardcodes the `username` request parameter

**Evidence:**
- `recordFailure` reads `request.getParameter('username')` (`LoginFailureHandler.java:61`).
- A future `formLogin` customization with a different username parameter name would silently break audit/lockout attribution.

**Recommendation:**
Make the parameter name configurable (e.g. `themistra.auth.login.username-parameter`, default `username`) or document the coupling explicitly in the handler Javadoc.

**Confidence:** Low.

---

## 10. Timing side-channel between known and unknown failure branches remains observable

**Evidence:**
- Known-account failures execute up to `findLoginView` + `recordFailedAttempt` (row-lock write) + `AuditService.record`; unknown-account failures execute only `findLoginView`.
- The brief explicitly accepts this timing gap, but the AC9 test should not imply timing equality.

**Recommendation:**
Document in the AC9 test that it verifies response shape only, not timing. Treat timing as out of scope for T13.

**Confidence:** Medium.

---

## Non-findings

- `AccountUserDetailsService` correctly derives `accountLocked` from `LockoutService.isCurrentlyLocked` (AC5/AC6).
- L12 module boundary is respected: no `Account` entity imports in the changed files.
- `isCurrentlyLocked` returns `false` for missing rows and null `lockedUntil`, matching AC8.
- `LoginFailureHandler` status gating calls `recordFailedAttempt` only for `ACTIVE` or expired-`LOCKED` accounts.
- Import fixes in `SecurityChainsConfig` and `ReuseDetectingAuthorizationService` use the correct packages.
