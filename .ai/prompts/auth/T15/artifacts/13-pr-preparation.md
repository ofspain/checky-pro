# auth · T15 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds one test method to one existing test file, with no production code
change anywhere.

## Commit title

```
Add indistinguishable login-response security test (T15)
```

## Commit message

```
Add indistinguishable login-response security test (T15)

Proves R21: password authentication against a LOCKED, SUSPENDED,
DELETED, or non-existent account fails identically to a bad-password
attempt against an ACTIVE account. LoginFailureHandler (T13) was
already built to guarantee this - this task adds the proof that was
missing. Confirmed at Phase 0 that no existing test actually compared
responses across these statuses: the closest prior tests either varied
the exception type against a fixed ACTIVE account, or varied account
status but only checked audit/lockout side effects, never the
response itself.

Adversarial review (Phase 3) caught that the session stores the raw
AuthenticationException (SimpleUrlAuthenticationFailureHandler
.saveException, confirmed protected final - not overridable), so
redirect-target equality alone doesn't fully prove response
indistinguishability if a login page ever renders that session
attribute. Human-approved: keep this test's scope at the
HTTP-observable response only and document the session-storage
detail as a residual risk, matching how T13 already assessed this
identical mechanism (currently unexploitable - no login page exists
yet).

Independent test review (Phase 11) then found the frozen brief's
"redirect-target only" limit was self-imposed rather than a real
constraint: swapping the shared Mockito HttpServletResponse for a
real MockHttpServletResponse per case (already a spring-test
transitive dependency, zero Docker/Testcontainers cost) lets the test
assert the actual 302 status code and Location header, not just an
internal method argument. Human-approved and applied - strengthens
AC5 beyond the frozen brief's original text.

One environment finding surfaced mid-task, unrelated to this task's
own correctness: Docker became available in this sandbox for the
first time since T11, but Testcontainers' Java client still cannot
complete its handshake with the daemon (empty-body 400 on every
detection strategy) - a docker-java/Docker Desktop API mismatch, not
a code issue. Diagnosed and documented for whoever tackles it next;
does not affect this task, since its one test has no Testcontainers
dependency.

11/11 tests passing in LoginFailureHandlerTest (10 pre-existing + 1
new), verified via a real `mvn test` run rather than the
javac/JUnit-Launcher workaround this chain has used since T12 - the
first task since T11 able to do that, even though the module's wider
Testcontainers suite still can't run here.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Tests only — no production code:**
- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` (modified) —
  one new test, `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`, plus a small
  `invokeFailure` helper; 11 tests total in this file, was 10.

**Process artifacts** (`.ai/prompts/auth/T15/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decision (session-leak scope, redirect-only), the Phase 3/8/11 Kimi reviews and their dispositions,
the Phase 11 human decision to strengthen AC5 via `MockHttpServletResponse`, and the Phase 12 PASS
verdict.

## Summary

Implements `tasks.md` task 15: the one remaining proof gap in the R16-R21/L4/L5 lockout-and-login
feature this multi-task chain has been building since T11. No production code changes — `T13`'s
`LoginFailureHandler` was already correct; this task closes the gap between "correct" and
"proven." Three things worth a reviewer's attention: (1) the test strengthening from
redirect-argument to real status/header assertions (Phase 11) is the most consequential change,
closing a gap the original scope decision didn't need to accept once a `MockHttpServletResponse`
was on the table; (2) the session-stored-exception residual risk is real but deliberately left
untested, consistent with T13's own prior assessment of the identical mechanism; (3) the
Testcontainers/Docker handshake diagnosis is a genuinely new, more precise finding than "Docker
unavailable" (every task's assumption since T12), but is explicitly out of scope for this task and
left for whoever next needs the module's integration suite running for real.

## Testing performed

`mvn -pl services/auth clean compile` and `mvn -pl services/auth clean test-compile` **both
succeed with zero errors** — re-verified fresh at Phase 12.

**Result: 11/11 tests passing** in `LoginFailureHandlerTest` (10 pre-existing + 1 new), executed
via a real `mvn -pl services/auth test -Dtest=LoginFailureHandlerTest` run — the first task since
T11 able to use real Surefire execution instead of the javac/JUnit-Launcher workaround, since
Docker became available mid-task (though it does not unblock Testcontainers itself; see the
handshake finding above).

A full-module `mvn -pl services/auth test` run was also attempted to check for regressions. It
reports pre-existing errors unrelated to this task's file: known Mockito
`UnnecessaryStubbingException`/field-init-order issues already documented in prior tasks'
artifacts, plus the Testcontainers handshake failure cascading across every `@SpringBootTest` class
that depends on `TestcontainersConfiguration`. None are new; none touch
`LoginFailureHandlerTest.java` or any file this task modified.

Kimi's independent code review (Phase 8) found 3 findings; all verified against source, all
resolved as documentation/comment updates (one independently corroborating a self-review finding).
Kimi's independent test review (Phase 11) found 4 gaps; 3 were restatements of already-resolved
items, 1 (Gap 2) was genuinely new and human-approved, strengthening the test's core assertion.
Full requirement-to-evidence-to-test traceability is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 15 — "Indistinguishable login response test."
- **Requirements:** R21 (`requirements.md`), fully proven and tested.
- **LOCKED decisions:** L5 (enumeration-safe responses — this test is the login-specific proof).
  L12 (module boundaries — confirmed clean via a fresh import check at Phase 12: the test file
  imports only DTOs from `com.themistra.auth.account`, no entities, no new cross-module
  dependency).
- **Named test:** `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` (`package.md`
  §8) — present verbatim, strengthened at Phase 11 to assert real HTTP-observable status/header
  equality, not just an internal redirect argument.
