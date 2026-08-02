# auth · T15 — Phase 10: Test Generation

No new test written this phase — T15 is a test-only task, so its one test was written at Phase 6
(the frozen brief's own carve-out for test-only tasks) and refined at Phase 9 (one comment
reword). This phase's job is the manifest: confirming the frozen brief's coverage requirements are
fully met by what already exists, not adding more.

## Files

- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` — no change
  this phase (Phase 9 already applied the one accepted comment fix).

## Test → requirement / acceptance-criterion mapping

| Test | Maps to |
|---|---|
| `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` (named test, `package.md` §8) | R21, L5, AC1-AC5 — the entire frozen brief in one test: still-locked `LOCKED` (AC1), `SUSPENDED` (AC2), `DELETED` (AC3), non-existent email (AC4), and the `ACTIVE`+bad-password baseline, all asserted redirect-equal to each other in one comparison (AC5). |

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
pre-existing tests, confirming no regression from either the Phase 6 addition or the Phase 9
comment edit.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 15.
- Requirements: R21. LOCKED decisions: L5.
- Frozen brief: `04-frozen-task-brief.md` — AC1-AC5, Required Tests.
- Review resolution: `09-review-resolution.md`.
