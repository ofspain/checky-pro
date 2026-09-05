# auth · T15 — Phase 5: Implementation Plan

Plan only — no code. Traces entirely to the frozen brief (`04-frozen-task-brief.md`); no file
outside its Files sections.

## Files to create

None.

## Files to modify

- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` — add one
  new `@Test` method.

## Public methods (signatures)

None — no production code changes (frozen brief Files NOT to Modify).

One new test method:

```java
@Test
void shouldReturnIndistinguishableResponseForLockedAndBadCredentials() throws Exception
```

## Private methods

None new. Reuses the existing private helper `captureAuditRequest()` only if needed for
incidental audit-side assertions — not required by AC1-AC5, so not called. No new private helper
needed: five inline `handler.onAuthenticationFailure(...)` calls plus one shared
`ArgumentCaptor<String>`, matching `everyExceptionSubclassProducesTheSameRedirect`'s existing
shape exactly.

## Entities used

None — unit test, no persistence (`LoginView` is a DTO record, not an entity, already used
throughout this file).

## Repositories used

None.

## Services used (mocked, already fields on the test class)

- `accountService` (`@Mock AccountService`) — stub `findLoginView` per case.
- `lockoutService` (`@Mock LockoutService`) — stub `isCurrentlyLocked` for the still-locked case
  only.
- `auditService` — no new stubbing needed; `auditFailure` always calls `record(...)`, already
  covered by the class's blanket `@Mock` (no explicit `verifyNoInteractions`/`verify` needed for
  this test since AC1-AC5 don't assert on it).

## Test required

One test, per the frozen brief:

`shouldReturnIndistinguishableResponseForLockedAndBadCredentials` — five cases in one method:

1. Still-locked `LOCKED`: `findLoginView` → `LoginView(ACCOUNT_UUID, hash, LOCKED)`,
   `lockoutService.isCurrentlyLocked(...)` → `true`; drive with `new LockedException("locked")`.
2. `SUSPENDED`: `findLoginView` → `LoginView(ACCOUNT_UUID, hash, SUSPENDED)`; drive with
   `new DisabledException("disabled")`.
3. `DELETED`: `findLoginView` → `Optional.empty()` (matches production — `AccountService
   .findLoginView` already filters `DELETED` out); drive with
   `new UsernameNotFoundException("Bad credentials")`.
4. Non-existent email: `findLoginView` → `Optional.empty()`; drive with the same
   `UsernameNotFoundException`.
5. Baseline: `findLoginView` → `LoginView(ACCOUNT_UUID, hash, ACTIVE)`; drive with
   `new BadCredentialsException("bad")`.

Reuses the shared `request`/`response`/`accountService`/`lockoutService` `@Mock` fields
sequentially, re-stubbing `accountService.findLoginView(EMAIL)` (and, for the still-locked case
only, `lockoutService.isCurrentlyLocked(...)`) immediately before each `handler
.onAuthenticationFailure(...)` call — later `when()` stubs override earlier ones on the same mock,
so no `reset(...)` or fresh mocks are needed. This is the exact technique
`everyExceptionSubclassProducesTheSameRedirect` already uses (loop over cases against the shared
mocks, one `ArgumentCaptor` across all calls), extended here to vary `findLoginView`'s return value
per case instead of holding it fixed. A single `ArgumentCaptor<String>` captures all five
`sendRedirect` calls; `verify(response, times(5)).sendRedirect(captor.capture())` followed by
`assertThat(captor.getAllValues()).containsOnly(captor.getAllValues().get(0))` proves AC5's "all
five equal to each other," not just pairwise.

## Execution order

1. Add the new test method to `LoginFailureHandlerTest.java` (only file touched).
2. `mvn -pl services/auth clean test-compile` — verify it compiles.
3. Execute via the JUnit Platform Launcher workaround (Docker unavailable) — verify the new test
   passes and the existing 9 tests in this file are unaffected.
