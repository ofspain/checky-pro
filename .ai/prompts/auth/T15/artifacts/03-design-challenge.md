# auth · T15 — Phase 3: Design Challenge

 adversarial review of the Phase 2 brief for the indistinguishable-login-response test. The brief is narrow and internally consistent, but it leaves several edges of R21/L5 unaddressed.

---

## Finding 1 — `SimpleUrlAuthenticationFailureHandler` stores the concrete exception in the session, so redirect equality does not prove response indistinguishability

- **Issue:** The brief plans to assert only that every case calls `response.sendRedirect("/login?error")`. The superclass `SimpleUrlAuthenticationFailureHandler.onAuthenticationFailure` also invokes `saveException(request, response, exception)`, which puts the original `AuthenticationException` (including its message and type) into the session under `WebAttributes.AUTHENTICATION_EXCEPTION`. The default Thymeleaf/login form can render that exception message, meaning a `LockedException("User account is locked")` and a `BadCredentialsException("Bad credentials")` could produce visibly different pages even though the Location header is identical. The brief's redirect-only assertion therefore does not fully verify R21/L5.
- **Severity:** Medium — a real information-leak path that a redirect-only unit test cannot catch.
- **Evidence:** `LoginFailureHandler.java:68` calls `super.onAuthenticationFailure(...)`, which is `SimpleUrlAuthenticationFailureHandler`. The existing test stubs `request.getSession()` (`LoginFailureHandlerTest.java:82`) but never inspects `session.setAttribute(...)`.
- **Recommended brief amendment:** Either (a) add an assertion that the session receives a uniform exception/message for every case, or (b) state explicitly that `LoginFailureHandler` must override `saveException` to store a single generic `BadCredentialsException` (or no exception at all), or (c) document that the test is only the redirect-level component of R21 and that session/flash-attribute uniformity is verified elsewhere (e.g., a `SecurityChainsConfig` integration test or a login-page contract test). Without one of these, AC1-AC5 are weaker than the requirement they claim to cover.

---

## Finding 2 — "Indistinguishable response" is narrowed to redirect URL without stating what is out of scope

- **Issue:** The task statement from `tasks.md` asks for "the same body/status on password failure." The brief translates this into "all five captured redirect-target strings are equal." It never clarifies whether the test also asserts the HTTP status code (302), the `Location` header, response body absence, or timing. An adversarial reader could over-read the test as fully covering R21, when it actually covers only the redirect target.
- **Severity:** Medium — scope ambiguity that the Phase 4 freeze should resolve.
- **Evidence:** Brief Scope/Outputs says "Five captured redirect-target strings, asserted equal to each other." AC1-AC5 only mention the redirect target.
- **Recommended brief amendment:** Add a Constraints bullet listing what "indistinguishable" means in this test (redirect URL is identical) and what is explicitly excluded (status/header/timing assertions, integration with the actual `/login` page rendering). If status/header coverage is desired, add an assertion on `HttpServletResponse.getStatus()` and/or capture the `Location` header.

---

## Finding 3 — `PENDING_VERIFICATION` is not included even though it is disabled and produces `DisabledException`

- **Issue:** `AccountUserDetailsService.java:53-54` treats `PENDING_VERIFICATION` and `SUSPENDED` identically for the login gate: both set `.disabled(true)`, so both throw `DisabledException`. R21 lists `LOCKED`, `SUSPENDED`, `DELETED`, and non-existent, but `agents.md` L5 says "Enumeration-safe everywhere ... login return uniform responses that never reveal whether an email exists, whether an account is locked/suspended/deleted." The brief covers `SUSPENDED` but not `PENDING_VERIFICATION`, leaving a disabled-account case unproven for redirect uniformity.
- **Severity:** Low-Medium — the code path is very similar to `SUSPENDED`, but it is a distinct status whose exclusion is not justified in the brief.
- **Evidence:** `LoginFailureHandlerTest.java:136-138` already has a `pendingVerificationAccountAuditsOnlyNeverCallsLockoutService` test, but it does not assert the redirect (inherited `assertAuditOnlyForStatus` helper never verifies `sendRedirect`).
- **Recommended brief amendment:** Add `PENDING_VERIFICATION` as a sixth case, or explicitly document it as out of scope because it is covered by the `SUSPENDED`/`DisabledException` equivalence class and add a note explaining why that equivalence is safe.

---

## Finding 4 — AC1's "LOCKED" case is ambiguous about expired vs. active lockout interval

- **Issue:** `AccountUserDetailsService.java:48-55` distinguishes a still-locked account (`isCurrentlyLocked == true`) from a `LOCKED`-status account whose lockout interval has elapsed. The former throws `LockedException`; the latter proceeds to password verification and throws `BadCredentialsException`. The brief says "`LOCKED` (unexpired)" but does not state whether the expired-lock `LOCKED` case is in or out of scope. If it is out of scope, the test could miss a regression where an expired-lock account is treated differently from the baseline.
- **Severity:** Low-Medium — boundary ambiguity.
- **Evidence:** Brief AC1 says "`LOCKED` (unexpired) produces the same redirect target as `ACTIVE`+bad-password" but never defines the expired-lock branch.
- **Recommended brief amendment:** Rename AC1 to "still-within-lockout-interval `LOCKED`" and add a note that the expired-lock `LOCKED` branch is expected to produce `BadCredentialsException` like the baseline, and is either included implicitly or excluded explicitly.

---

## Finding 5 — The new test largely overlaps with existing `everyExceptionSubclassProducesTheSameRedirect`

- **Issue:** `LoginFailureHandlerTest.everyExceptionSubclassProducesTheSameRedirect` already iterates `BadCredentialsException`, `UsernameNotFoundException`, `LockedException`, and `DisabledException` against a fixed `ACTIVE` stub and asserts they all redirect to `/login?error`. The T15 test will make the same redirect assertion, just with upstream mocks that match production status paths. The brief does not explain why both tests are needed, which invites a reviewer to treat the new test as redundant.
- **Severity:** Low — duplication is defensible, but unstated.
- **Evidence:** `LoginFailureHandlerTest.java:177-196` already exists and passes the same four exception types through the handler.
- **Recommended brief amendment:** Add a brief note in Scope or Acceptance Criteria explaining that `everyExceptionSubclassProducesTheSameRedirect` proves handler-level exception-agnostic redirect behavior, while `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` proves the production end-to-end path from account status → exception → redirect with realistic `findLoginView`/`isCurrentlyLocked` stubs.

---

## Finding 6 — `package.md` maps the named test to R18 instead of R21

- **Issue:** The brief correctly identifies R21 as the scoped requirement, but `package.md` §8 lists `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` → R18 ("locked account's lockout interval has elapsed"). The test name and intent clearly belong to R21 (indistinguishable failure responses). The brief references `package.md` for the named test name but silently accepts the wrong requirement mapping.
- **Severity:** Low — a spec inconsistency, not an implementation risk.
- **Evidence:** `package.md` §8 line mapping the named test to `R18`; `requirements.md` R21 is the indistinguishable-response criterion.
- **Recommended brief amendment:** Add an Open Question or note at Phase 4 to correct the `package.md` mapping from R18 to R21. Do not silently adopt the R18 label in this task's traceability.

---

## Finding 7 — Line-number references are brittle

- **Issue:** The brief cites `AccountUserDetailsService.java:43-58` and `AccountService.java:339`. Line numbers drift with any edit to those files, making the frozen brief harder to verify later.
- **Severity:** Low — documentation/maintenance risk only.
- **Evidence:** Brief Scope/Inputs and Dependencies cite exact line numbers.
- **Recommended brief amendment:** Replace line-number references with method names (e.g., `AccountUserDetailsService.loadUserByUsername`, `AccountService.findLoginView`) or behavior descriptions.
