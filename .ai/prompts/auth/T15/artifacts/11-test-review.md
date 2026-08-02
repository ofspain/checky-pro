# auth · T15 — Phase 11: Test Review

Reviewed the Phase 10 manifest and the single test `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` in `LoginFailureHandlerTest.java` against the frozen brief (`04-frozen-task-brief.md`) and the task statement from `spec/auth-service/tasks.md`. The test satisfies the frozen brief's AC1-AC5. The gaps below concern coverage against the broader R21/task-statement property and against future regressions that the unit test cannot catch.

---

## Gap 1 — The test injects exception types, so it cannot catch a regression in the upstream status→exception mapping

- **Why it matters:** R21 is a property of the *full login flow*: account status → `AccountUserDetailsService.loadUserByUsername` → Spring Security's `DaoAuthenticationProvider` → exception → `LoginFailureHandler` → response. The T15 unit test starts at the handler and manually supplies `LockedException`, `DisabledException`, `UsernameNotFoundException`, and `BadCredentialsException`. If `AccountUserDetailsService` changed so that `SUSPENDED` no longer disables the principal, or so that `LOCKED` no longer sets `accountLocked`, the handler test would still pass while production would leak state.
- **Suggested test:** Add a unit test in `AccountUserDetailsServiceTest` that exercises `loadUserByUsername` for each relevant `AccountStatus` and asserts the resulting `UserDetails` flags (`isEnabled`, `isAccountNonLocked`) are what Spring will translate into the expected exception types. For an even stronger guarantee, drive a real `DaoAuthenticationProvider` with the returned `UserDetails` and a wrong password and assert the thrown exception type per status.

---

## Gap 2 — Response body/status are not asserted; only the redirect target is checked

- **Why it matters:** The task statement asks for "the same body/status on password failure." The test captures `response.sendRedirect(...)` and asserts the argument is always `"/login?error"`. It does not assert the HTTP status code (302), the `Location` header, or the absence of a response body — all of which are observable by a client. A mocked `HttpServletResponse` makes these harder to assert, but a regression that changed the status code or wrote a distinguishing body would not be caught.
- **Suggested test:** If the brief is amended to permit integration-level coverage, extend `SasLoginIntegrationTest.unknownEmailProducesTheSameResponseShapeAsAKnownAccountFailure` to cover `SUSPENDED` and `DELETED` accounts and assert full response equality (status, Location, and body). At the unit level, replace the Mockito `HttpServletResponse` with a real `MockHttpServletResponse` for at least the T15 test so the status code and `Location` header can be asserted directly.

---

## Gap 3 — `DELETED` and non-existent email are not independently exercised

- **Why it matters:** The test stubs `accountService.findLoginView(EMAIL)` to `Optional.empty()` once and calls the handler twice with `UsernameNotFoundException`. This proves the handler treats both cases identically, but it does not independently validate that a real `DELETED` account actually reaches `findLoginView` as `Optional.empty()` (i.e., the `DELETED` filter in `AccountService.findLoginView` is present and correct). The Phase 10 manifest correctly references `AccountServiceTest.loginViewHidesDeletedAccountsLikeUnknownEmails`, but R21 is not fully closed until that dependency is explicit and the two tests are clearly linked.
- **Suggested test:** No change to `LoginFailureHandlerTest` is required — the current test is correct. Add a code comment or test-name cross-reference in T15 pointing to `AccountServiceTest.loginViewHidesDeletedAccountsLikeUnknownEmails`, and ensure the test manifest lists that test as a dependency for AC3.

---

## Gap 4 — No test verifies that the handler does not leak state via response headers, cookies, or session attributes

- **Why it matters:** `SimpleUrlAuthenticationFailureHandler.saveException` stores the raw `AuthenticationException` in the session, and a future change could add a distinguishing header or cookie. The frozen brief accepted this as a residual risk (Phase 4 Finding 1). Because it is a residual risk rather than a covered property, the test suite leaves a real exploitable surface if a login page is ever added that renders the stored exception.
- **Suggested test:** If the residual risk is ever addressed in production, add assertions that no `WebAttributes.AUTHENTICATION_EXCEPTION` is stored, that `Set-Cookie`/`X-*`/custom headers are absent, and that `sendRedirect` is the only response mutation. Until then, document that the test intentionally does not cover this surface.

---

## Non-gaps (verified clean)

- **Frozen brief AC1-AC5 are covered:** all five redirect targets are captured and compared in one `containsOnly("/login?error")` assertion.
- **No `PENDING_VERIFICATION` case:** correctly excluded per the frozen brief's equivalence-class rationale (same `DisabledException` path as `SUSPENDED`).
- **No integration test added:** consistent with the frozen brief's Scope > Out and the environment's Docker limitation.
- **No flakiness observed:** fixed `Clock`, deterministic mocks, no wall-clock or async behavior.
