# auth · T15 — Phase 10: Test Generation

**Phase 11 (Kimi test review) update:** 4 gaps raised. Gaps 1, 3, and 4 were restatements of
findings already resolved at Phase 8/9 (status→exception mapping residual risk, the `DELETED`
cross-reference to `AccountServiceTest.loginViewHidesDeletedAccountsLikeUnknownEmails`, and the
session-leak residual risk) — no new action beyond what's already documented in
`09-review-resolution.md`. Gap 2 was genuinely new and accepted: it identified that the frozen
brief's "redirect-target only" Constraint was justified by a "mock limitation" that turns out not
to be a hard limitation — swapping the shared Mockito `HttpServletResponse` for a real
`MockHttpServletResponse` per case (still zero Docker/Testcontainers dependency) lets the test
assert the actual 302 status code and `Location` header, not just the `sendRedirect` argument.
Applied: `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` now builds a fresh
`MockHttpServletResponse` per case via a new `invokeFailure` helper, collects all five, and asserts
both `getStatus()` and `getRedirectedUrl()` are uniform across all five. Re-verified: 11/11 tests
passing.

No new test written this phase — T15 is a test-only task, so its one test was written at Phase 6
(the frozen brief's own carve-out for test-only tasks) and refined at Phase 9 (one comment
reword). This phase's job is the manifest: confirming the frozen brief's coverage requirements are
fully met by what already exists, not adding more.

## Files

- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` — Phase 9
  applied one comment fix; Phase 11 (this update) applied Gap 2, switching the test's response
  handling from the shared Mockito mock to a real `MockHttpServletResponse` per case.

## Test → requirement / acceptance-criterion mapping

| Test | Maps to |
|---|---|
| `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` (named test, `package.md` §8) | R21, L5, AC1-AC5 — the entire frozen brief in one test: still-locked `LOCKED` (AC1), `SUSPENDED` (AC2), `DELETED` (AC3), non-existent email (AC4), and the `ACTIVE`+bad-password baseline, all asserted equal in status code (302) and `Location` header across all five (AC5, strengthened at Phase 11 beyond the frozen brief's original redirect-argument-only assertion). |

No other test was added or needed — the frozen brief's Scope > Out explicitly excludes a second
(integration-level) test, and `PENDING_VERIFICATION` was explicitly excluded as an equivalence
class of `SUSPENDED` (Phase 4 Finding 3 disposition), not a missed boundary.

## Coverage against the frozen brief's Required Tests list

The frozen brief names exactly one required test (`shouldReturnIndistinguishableResponseForLockedAndBadCredentials`)
and it exists, verbatim, covering all five acceptance criteria in a single method. Nothing in the
frozen brief's Required Tests section is unaddressed.

## Boundaries and state transitions considered, and their disposition

- **Still-locked `LOCKED` vs. expired-lock `LOCKED`:** both in scope conceptually (R21 doesn't
  distinguish them), but only still-locked is separately stubbed — expired-lock produces the
  identical exception type (`BadCredentialsException`) as the `ACTIVE` baseline, so it's covered by
  that case rather than a sixth stub (Phase 4 Finding 4 disposition, reflected in the Phase 9
  comment reword).
- **`PENDING_VERIFICATION`:** excluded as an equivalence class of `SUSPENDED` (same `DisabledException`
  path) — Phase 4 Finding 3, not re-litigated here.
- **`DELETED` vs. non-existent email:** both exercised, both producing `UsernameNotFoundException`
  via the same `Optional.empty()` stub — correctly mirrors production (`AccountService.findLoginView`'s
  `DELETED` filter), independently validated elsewhere by `AccountServiceTest
  .loginViewHidesDeletedAccountsLikeUnknownEmails` (confirmed Phase 9).
- **Session-state uniformity:** explicitly out of scope, documented as a residual risk since Phase
  4 (Finding 1) and reaffirmed at Phase 9 — not a boundary this test suite covers.

## Build verification

`mvn -pl services/auth clean test-compile` — zero errors.

`mvn -pl services/auth test -Dtest=LoginFailureHandlerTest`:

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

Executed via a real `mvn test` run (Docker present in this sandbox as of this task, though
Testcontainers itself remains unable to use it — irrelevant here since this test has no
Testcontainers dependency). All 11 tests in the file pass, including the new one and the 10
pre-existing tests, confirming no regression from the Phase 6 addition, the Phase 9 comment edit,
or the Phase 11 response-handling change.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 15.
- Requirements: R21. LOCKED decisions: L5.
- Frozen brief: `04-frozen-task-brief.md` — AC1-AC5, Required Tests.
- Review resolution: `09-review-resolution.md`.
