# auth · T15 — Phase 2: Task Implementation Brief

## Task

Add a security test proving that password-authentication failure produces an indistinguishable
response across `LOCKED`, `SUSPENDED`, `DELETED`, and non-existent-email accounts.

## Purpose

Close the gap Phase 0 confirmed by direct inspection: no existing test varies account status
across all four R21 cases and compares the resulting response. `LoginFailureHandlerTest
.everyExceptionSubclassProducesTheSameRedirect` (T13) varies the exception *object* against a
fixed `ACTIVE` stub; the per-status tests (`suspendedAccountAuditsOnlyNeverCallsLockoutService`
etc.) vary status but assert only audit/lockout side effects, never the response itself.

## Scope

**In:**
- One new unit test, `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`
  (`package.md` §8 name, verbatim), added to `LoginFailureHandlerTest.java`.
- Drives `LoginFailureHandler.onAuthenticationFailure` with the exact exception each status
  produces in production (verified against `AccountUserDetailsService.java:43-58`, not assumed):
  `LockedException` for `LOCKED`-unexpired, `DisabledException` for `SUSPENDED`,
  `UsernameNotFoundException` for `DELETED` and for a non-existent email (both reach it via
  `findLoginView`'s empty `Optional`, already the same code path per `AccountService.java:339`),
  and `BadCredentialsException` as the baseline (`ACTIVE` + wrong password).
- Captures `response.sendRedirect(...)`'s argument per case (`ArgumentCaptor`, matching
  `everyExceptionSubclassProducesTheSameRedirect`'s established technique) and asserts all five
  captured values are equal.

**Out:**
- No new integration/Testcontainers test. `SasLoginIntegrationTest.java` is not modified. Reason:
  Docker is unavailable in this sandbox (confirmed at Phase 0, consistent with every task since
  T12); a new integration test here could not be executed or verified, and the unit-level test
  above already proves the property at the exact point (`LoginFailureHandler`) where
  indistinguishability is enforced. This is a scope decision, not an oversight — flagged
  explicitly for Phase 4 sign-off rather than decided silently.
- No production code changes. `LoginFailureHandler`'s behavior is already correct (built in T13
  for this exact purpose); this task adds proof, not behavior.
- No changes to `AccountUserDetailsServiceTest.java`, `AccountServiceTest.java`, or any other
  existing test file.

## Business Rules

- **R21.** Password authentication against `LOCKED`, `SUSPENDED`, `DELETED`, or a non-existent
  account fails with a response indistinguishable from bad credentials, revealing no account
  state.

## Locked Decisions

- **L5.** Enumeration-safe responses — this test is the login-specific proof of that decision.

## Dependencies

- `LoginFailureHandler` (class under test, unmodified).
- `LockedException`, `DisabledException`, `UsernameNotFoundException`, `BadCredentialsException`
  (`org.springframework.security.*`) — the four exception types production code actually throws.
- Mockito `ArgumentCaptor<String>` on `HttpServletResponse.sendRedirect(String)`, matching the
  existing test file's established capture pattern.

## Inputs

- A mocked `HttpServletRequest`, `HttpServletResponse`, and one `AuthenticationException` instance
  per case (5 cases total: `LOCKED`, `SUSPENDED`, `DELETED`, non-existent, `ACTIVE`+bad-password).

## Outputs

- Five captured redirect-target strings, asserted equal to each other.

## State Changes

None — pure unit test, no persistence, no Spring context.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` — add the
  one new test method.

## Files NOT to Modify

- Any file under `src/main/java` (no production code change).
- `SasLoginIntegrationTest.java` (see Scope > Out).
- Any other test file.

## Acceptance Criteria

- **AC1 (R21).** `LOCKED` (unexpired) produces the same redirect target as `ACTIVE`+bad-password.
- **AC2 (R21).** `SUSPENDED` produces the same redirect target as AC1's baseline.
- **AC3 (R21).** `DELETED` produces the same redirect target as AC1's baseline.
- **AC4 (R21).** Non-existent email produces the same redirect target as AC1's baseline.
- **AC5 (R21, L5).** All five captured values (the four above plus the baseline itself) are
  asserted equal to each other in one comparison, not four isolated single-case assertions.

## Required Tests

- `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` (named test, `package.md` §8)
  — the only test this task adds.

## Constraints

- **Security:** the test must assert on the redirect target only (what a real client/attacker
  observes), never inspect internal handler state, matching how R21 itself is framed (external
  observability, not internal correctness).
- **Determinism:** no `Instant.now()`/wall-clock dependency — this test needs none, since
  `LockedException` vs. the others requires no lock-expiry arithmetic (that's `AccountUserDetailsService`'s
  job, already tested elsewhere; this test starts one level downstream, at the exception already
  having been thrown).
- **Thread-safety / transaction:** not applicable — no Spring context, no persistence.
- **Module boundaries (L12):** not exercised — no new dependency, `authn`-package-internal test.
- **Null handling:** not applicable — no null-argument case in scope for this task (already
  covered by T13's `nullUsernameParameterAuditsWithNullUuidsAndNeverCallsLockoutService`).

## Open Questions

No blockers. The unit-vs-integration placement (Phase 0's flagged question) is resolved above as a
scoping decision with explicit rationale, not left open — but it's exactly the kind of call worth
surfacing for explicit confirmation at Phase 4 rather than treating as silently settled.
