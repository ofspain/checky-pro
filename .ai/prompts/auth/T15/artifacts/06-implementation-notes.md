# auth · T15 — Phase 6: Implementation Notes

This task is test-only (frozen brief Files NOT to Modify explicitly excludes all production code),
so this phase's deliverable *is* the test itself, per `06-implementation.md`'s own carve-out.

## What changed

- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` — added one
  new test, `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`, inserted between
  `everyExceptionSubclassProducesTheSameRedirect` and `bookkeepingFailureDoesNotPreventTheRedirect`.
  No other file touched.

## Mapping to the plan (Phase 5) and acceptance criteria (Phase 4 frozen brief)

Implemented exactly as planned, with one simplification found while writing it: the plan's
tentative "fresh mock per case" note was corrected during Phase 5 itself (edited before
implementation) to the simpler, already-established technique — reusing the shared `request`/
`response`/`accountService`/`lockoutService` fields with sequential re-stubbing, exactly matching
`everyExceptionSubclassProducesTheSameRedirect`. No further deviation from that.

- **AC1** (still-locked `LOCKED`) — `findLoginView` stubbed to `LoginView(..., LOCKED)`,
  `isCurrentlyLocked` stubbed `true`, driven with `LockedException`.
- **AC2** (`SUSPENDED`) — `findLoginView` stubbed to `LoginView(..., SUSPENDED)`, driven with
  `DisabledException`.
- **AC3** (`DELETED`) — `findLoginView` stubbed to `Optional.empty()` (matches production:
  `AccountService.findLoginView` already filters `DELETED` out before this handler ever sees it),
  driven with `UsernameNotFoundException`.
- **AC4** (non-existent email) — same `Optional.empty()` stub, same exception type as AC3 (both
  are, correctly, the identical production code path).
- **AC5** (all five equal to each other) — one shared `ArgumentCaptor<String>` across all five
  `onAuthenticationFailure` calls, `verify(response, times(5)).sendRedirect(captor.capture())`,
  then `assertThat(captor.getAllValues()).containsOnly("/login?error")` — proves mutual equality,
  not four isolated pairwise checks.

## Deviation forced by reality

None in the code itself. One environment finding surfaced while verifying this phase, recorded
here rather than hidden:

**Docker became available in this sandbox partway through this phase** (it was unavailable for
every prior task since T12). Re-running the full `services/auth` suite with Docker up did not,
however, unblock the existing Testcontainers-based integration tests (`SasLoginIntegrationTest`,
`LockoutPersistenceIntegrationTest`, `RoleAssignmentIntegrationTest`,
`RefreshTokenFamilyIntegrationTest`) — they still fail, but with a different, more specific error
than before: `docker info` succeeds from the shell, yet Testcontainers' Java client
(`docker-java`, bundled at Testcontainers 1.21.3) gets an empty-body `BadRequestException (Status
400)` on every detection strategy it tries, before it ever attempts to start a container. This
looks like a `docker-java`/Docker Desktop API version mismatch, not a "Docker unavailable" problem
as previously assumed. Tried and confirmed insufficient: explicitly setting `DOCKER_HOST` for the
Maven process. Full diagnosis recorded for future reference (not this task's to fix — module-wide
environment issue, human-confirmed out of scope for T15).

**This finding does not affect T15's own test**, which is pure Mockito/JUnit with no Spring
context and no Testcontainers dependency, and which passed cleanly regardless (see Build
verification below).

## Build verification

`mvn -pl services/auth clean test-compile` — succeeds with zero errors.

`mvn -pl services/auth test -Dtest=LoginFailureHandlerTest`:

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

All 11 tests in this file pass — the 10 pre-existing tests plus the one new test this phase adds.
Confirmed via the real Surefire run (not the javac/JUnit-Launcher workaround this chain has used
since T12 — Docker's arrival made a normal `mvn test` possible again for non-Testcontainers-based
test classes in this module, for the first time since T11).

A full-module `mvn -pl services/auth test` run was also attempted to check for regressions
elsewhere. It reports 25 pre-existing errors, none touching this task's file or the lockout/login
chain (T11-T14's files): a handful are known Mockito `UnnecessaryStubbingException`/field-init-order
issues already documented in prior tasks' artifacts (`AdminAccountRoleControllerTest`,
`ReuseDetectingAuthorizationServiceTest`, `TokenClaimsCustomizerTest`); the rest are the
Testcontainers handshake failure described above, cascading across every `@SpringBootTest` class
that depends on `TestcontainersConfiguration`. None are new; none are caused by this task's one-file
change.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 15.
- Requirements: R21.
- LOCKED decisions: L5.
- Frozen brief: `04-frozen-task-brief.md` — AC1-AC5, Scope, Constraints, Residual risks.
- Named test: `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` — present verbatim.
