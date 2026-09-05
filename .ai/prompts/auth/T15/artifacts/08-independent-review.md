# auth · T15 — Phase 8: Independent Code Review

Reviewed the one-file Phase 6 implementation (`LoginFailureHandlerTest.java`, new test `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`) and the Phase 7 self-review with fresh, adversarial eyes. The test matches the frozen brief (`04-frozen-task-brief.md`) and documents the accepted residual risks. No production code was touched, so thread-safety, transactions, module boundaries, and secrets handling are not applicable. The issues below concern what the test can and cannot actually prove about R21.

---

## Finding 1 — The test injects exception types manually, so it cannot catch a regression in the upstream status→exception mapping

- **Issue:** `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` constructs `LockedException`, `DisabledException`, `UsernameNotFoundException`, and `BadCredentialsException` directly and passes them to `LoginFailureHandler`. It does not drive `AccountUserDetailsService.loadUserByUsername` or Spring Security's `DaoAuthenticationProvider`. Consequently, if `AccountUserDetailsService` changed to throw a different exception for `SUSPENDED` (e.g., `BadCredentialsException`) or if `findLoginView` stopped filtering `DELETED` accounts to `Optional.empty()`, this test would still pass while the production login flow would leak state.
- **Evidence:**
  - `LoginFailureHandlerTest.java:207, 212, 216, 219, 225` — exception instances are constructed inline.
  - `LoginFailureHandler.java:58-69` — the handler receives the exception from upstream; it does not derive it from account status itself.
  - `AccountUserDetailsServiceTest.java` tests the `UserDetails` flags (`isEnabled`, `isAccountNonLocked`) but never asserts that a still-locked account causes Spring to throw `LockedException`, that `SUSPENDED` causes `DisabledException`, or that `DELETED` causes `UsernameNotFoundException`.
- **Recommendation:** Treat this as a residual risk in the frozen brief (Phase 4 already documents the redirect-only scope) and add a cross-reference that the R21 property is fully proven only when combined with an integration or `DaoAuthenticationProvider`-level test. If the project wants T15 to assert the full status→exception→redirect chain, the brief would need to add an integration test or a Spring-MVC `MockMvc` login attempt per status — both explicitly excluded by the frozen brief's Scope > Out.
- **Confidence:** Medium — the handler-level assertion is correctly scoped, but the test name and task statement ("asserting that locked, suspended, deleted, and non-existent accounts all return the same body/status on password failure") describe an end-to-end property that this unit test alone cannot fully verify.

---

## Finding 2 — The `DELETED` and non-existent cases are indistinguishable in the test itself, not only in production

- **Issue:** The brief correctly notes that `DELETED` and non-existent emails share the same production code path via `AccountService.findLoginView` returning `Optional.empty()`. However, the test literally reuses the same stub (`when(accountService.findLoginView(EMAIL)).thenReturn(Optional.empty())`) and the same `UsernameNotFoundException` for both sequential cases. This is appropriate proof of the *equivalence*, but it means the test provides no independent validation of the `DELETED`-specific behavior in `AccountService.findLoginView` (filtering by `status != DELETED`). A regression that removed the `DELETED` filter would not be caught here.
- **Evidence:** `LoginFailureHandlerTest.java:214-219` runs two consecutive invocations with `findLoginView` stubbed empty and `UsernameNotFoundException`. `AccountService.java:339-341` implements the `DELETED` filter, but the test mocks it away.
- **Recommendation:** No code change needed for T15 — the frozen brief already accepts that `DELETED` and non-existent are the same path and that `findLoginView`'s behavior is tested elsewhere (`AccountServiceTest` / integration tests). Update the residual-risk note to make explicit that T15 assumes `AccountService.findLoginView`'s `DELETED` filter is validated by other tests.
- **Confidence:** Low-Medium — the gap is real, but it is consistent with the brief's deliberate unit-level scope and the existing division of test responsibilities.

---

## Finding 3 — Comment at the baseline case overstates what the stub exercises

- **Issue:** The inline comment at lines 221-222 says the baseline "stands in for an expired-lock `LOCKED` account, which produces this same exception type." The baseline stubs `AccountStatus.ACTIVE`, not an expired-lock `LOCKED` account. The equivalence is true at the *exception-type* level (`BadCredentialsException`), but the comment could be read by a future maintainer as claiming the `findLoginView` stub itself exercises the expired-lock scenario, which it does not.
- **Evidence:** `LoginFailureHandlerTest.java:221-224` — comment and stub use `AccountStatus.ACTIVE`.
- **Recommendation:** Reword to clarify that the baseline *exception type* is shared with expired-lock `LOCKED`, not that the baseline test case itself stubs an expired-lock account. This is the same disposition as Phase 7's self-review Finding 1, independently confirmed.
- **Confidence:** High — documentation clarity only.

---

## Non-findings (verified clean)

- **Redirect assertion is correct and sufficient for the frozen brief's scope:** `verify(response, times(5)).sendRedirect(...)` plus `containsOnly("/login?error")` is a strong, single-comparison assertion matching AC5.
- **Stub interactions across cases are safe:** the leftover `lockoutService.isCurrentlyLocked(ACCOUNT_UUID, NOW)` stub from the still-locked case is not consulted for `SUSPENDED`, `DELETED`, non-existent, or `ACTIVE` cases because `isLockoutEligible` short-circuits on status before calling it.
- **No production code or spec deviation:** the test matches the frozen brief's five cases exactly and does not touch files outside `LoginFailureHandlerTest.java`.
- **Session-stored exception risk:** correctly left as the documented residual risk from Phase 4 Finding 1; no new evidence changes that disposition.

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — AC1-AC5, Scope, Residual risks.
- Implementation: `LoginFailureHandlerTest.java:198-229`.
- `AccountUserDetailsService.java` and `AccountUserDetailsServiceTest.java` — upstream status-to-exception mapping.
