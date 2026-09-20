# auth · T13 · Phase 11 — Test Review Findings

Input consumed: `artifacts/10-test-generation.md` and the actual test files it produced.
No rewrites — gaps and recommendations only.
Format: **Gap · Why it matters · Suggested test.**

---

## 1. `deletedAccountAuditsOnlyNeverCallsLockoutService` tests an internal branch production never reaches

**Gap:** The test stubs `AccountService.findLoginView(EMAIL)` to return a `LoginView` with `status = DELETED`, asserts audit fires with the real `accountUuid`, and never calls `LockoutService`. In production, `AccountService.findLoginView` already filters out `DELETED` accounts (`AccountService.java:339`), so a deleted email is indistinguishable from unknown and reaches the handler as an empty `Optional`. The real branch exercised in production is the unknown-email branch, not the status-gated branch.

**Why it matters:** The test gives false confidence that the `DELETED` status path is covered end-to-end, while the actual enumeration-safety behavior (deleted ≡ unknown) is not verified. A future refactor of the handler or the `findLoginView` filter could silently change real behavior without this test failing.

**Suggested test:** Remove the `DELETED` case from the status-gated helper, and add a focused test that stubs `findLoginView(email)` to return `Optional.empty()` and asserts the audit carries `accountUuid=null` / `actorUuid=null` and `LockoutService` is never touched. Keep the `PENDING_VERIFICATION` and `SUSPENDED` cases as they are reachable.

---

## 2. `everyExceptionSubclassProducesTheSameRedirect` only checks the redirect URL, not the full response shape

**Gap:** The test loops over `BadCredentialsException`, `UsernameNotFoundException`, `LockedException`, and `DisabledException` and verifies `sendRedirect("/login?error")` is called four times. It does not verify status code, headers beyond `Location`, or body, and it does not verify that no other response mutation happens for some subclasses but not others.

**Why it matters:** AC9 / L5 requires an *identical HTTP response* across every branch, not just the same redirect target. Two subclasses could produce the same `sendRedirect` call but differ in status code or body, and this test would pass.

**Suggested test:** Add assertions on `response.getStatus()` and `response.getHeader("Location")`, or use an in-memory stub of `HttpServletResponse` that records every method invoked and assert the recorded call sequence is identical across all four exception types.

---

## 3. `LoginSuccessHandlerTest` does not verify the inherited success behavior still runs

**Gap:** The tests verify that `recordSuccessfulAttempt` is called and that lockout failures / non-UUID principal names do not throw. None of them verify that `super.onAuthenticationSuccess(...)` is invoked, i.e., that the user is still redirected to the saved request or default success URL.

**Why it matters:** If the handler were accidentally changed to call `recordSuccessfulAttempt` and then return without delegating, two of the three existing tests would still pass, and the login flow would be broken.

**Suggested test:** Add a test that stubs a valid UUID principal and verifies a redirect/success interaction on the response (e.g., `response.sendRedirect(...)` is called, or the request-cache is consulted by the superclass).

---

## 4. `stillLockedAccountCannotLoginEvenWithCorrectPassword` does not assert that the login actually failed

**Gap:** The integration test registers and locks an account, calls `attemptLogin`, and asserts that the account status remains `LOCKED`. It never inspects `attempt.response.getStatusCode()` or the `Location` header.

**Why it matters:** An implementation that accidentally allowed the password check and then re-locked the account on the same `recordFailedAttempt` could leave the status `LOCKED` and still satisfy this assertion, even though the test name promises the login is denied. The response-shape assertion is what proves the lockout gate is actually reached and rejects the attempt.

**Suggested test:** Assert `attempt.response.getStatusCode()` is `HttpStatus.FOUND` and `attempt.response.getHeaders().getLocation()` is `/login?error` (or whatever the production failure redirect is). Compare it directly to a known-bad-password failure on the same account to enforce L5.

---

## 5. `wrongPasswordAgainstKnownAccountIncrementsCounterAndAudits` does not verify the counter increment or the audit

**Gap:** After one wrong-password login, the test only asserts `isCurrentlyLocked(accountUuid, Instant.now())` is `false`. It does not read the `failed_attempts` counter from `lockoutService`/`LockoutState`, and it does not verify an `auth_audit` row exists.

**Why it matters:** The test name claims it proves the counter increments and audits; without those assertions, a no-op failure handler would still pass. The counter and audit are verified only at the mocked unit layer, not in the end-to-end filter chain.

**Suggested test:** After the login attempt, query the lockout state for the account (e.g., expose a test-only lookup or use the repository/lockout service) and assert `failed_attempts == 1`; also query `auth_audit` and assert a `login.failed` row with the correct `accountUuid` exists.

---

## 6. `SasLoginIntegrationTest` relies on unverified `TestRestTemplate` HTTP semantics

**Gap:** The integration test uses `TestRestTemplate.exchange` and asserts `HttpStatus.FOUND`, but it does not configure redirect following or disable it, and it does not prove that the session cookie returned by `/login` is correctly propagated through the POST and any subsequent redirect.

**Why it matters:** Default `TestRestTemplate` behavior may follow redirects, which can turn a `302 FOUND` into a `200 OK` or `401 Unauthorized` on the target page; without controlling that, the status assertion is fragile and the cookie/session behavior is unproven.

**Suggested test:** At minimum, document the assumed `TestRestTemplate` configuration and assert both the POST response status/location and the cookie header presence. When Docker is available, run the test and adjust the assertions based on actual observed behavior.

---

## 7. Pending / suspended tests only assert `isEnabled()`, not `isAccountNonLocked()`

**Gap:** `pendingVerificationMapsToDisabled` and `suspendedMapsToDisabled` each assert only that `details.isEnabled()` is `false`. They do not assert that `details.isAccountNonLocked()` is `true`.

**Why it matters:** The whole point of the T13 fix is to separate the `accountLocked` decision from `Account.status`. For `PENDING_VERIFICATION` and `SUSPENDED`, `accountLocked` must remain `false`; otherwise Spring rejects those accounts via the wrong pre-authentication gate and the test would not catch a regression that breaks L5/R21 enumeration safety.

**Suggested test:** Add `assertThat(details.isAccountNonLocked()).isTrue()` to both tests.

---

## 8. No test verifies lockout-service failure isolation ordering in the failure handler

**Gap:** `bookkeepingFailureDoesNotPreventTheRedirect` covers the case where `accountService.findLoginView` fails, and `lockoutFailureStillAllowsAuditToFireAndRedirectToProceed` covers the case where `recordFailedAttempt` fails. Neither explicitly verifies that the audit still fires when `recordFailedAttempt` fails, *and* that the redirect happens in both cases.

**Why it matters:** Phase 9 Finding 3 explicitly called out that a `recordFailedAttempt` failure must not suppress the `login.failed` audit. The existing test verifies audit fires and redirect proceeds, but a more explicit ordering assertion would make the requirement self-documenting.

**Suggested test:** Keep the existing `lockoutFailureStillAllowsAuditToFireAndRedirectToProceed` test, but add a strict assertion that `auditService.record` is invoked before `response.sendRedirect`, and that the captured audit request contains the real `accountUuid`.

---

## 9. `expiredLockAccountCanSuccessfullyLoginAndUnlocks` does not assert the redirect target after successful login

**Gap:** The test asserts `FOUND` status and that the account transitions to `ACTIVE`. It does not assert the `Location` header, nor does it attempt the protected flow the login was meant to enable.

**Why it matters:** `FOUND` alone means only that *some* redirect happened. A redirect back to `/login?error` is also `FOUND` and would leave the current assertions passing while the login actually failed.

**Suggested test:** Assert the redirect location is the SAS authorization endpoint / saved request (or at least is not `/login?error`). When Docker is available, follow the redirect and verify an authorization code or token response is reachable.

---

## 10. `SasLoginIntegrationTest` is unverified in this environment

**Gap:** As noted in the test file and Phase 10 notes, the Testcontainers integration class compiles but has never executed because Docker is unavailable in this sandbox.

**Why it matters:** Four of the frozen brief's ACs (AC3, AC5, AC6, AC1 real-filter-chain reachability) have end-to-end coverage that is only theoretical. CSRF scraping, session propagation, and redirect behavior are hand-rolled and could be wrong.

**Suggested test:** No new test to write — flag this as a residual risk. When a Docker-capable environment is available, run `SasLoginIntegrationTest` as the first verification step; do not treat the Phase 10 green unit-test run as proof that the real SAS filter chain behaves correctly.
