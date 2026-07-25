# auth · T06 — Phase 11: Test Review Findings

Reviewed `AccountServiceTest.java`, `AccountControllerTest.java`,
`AccountExceptionHandlerTest.java`, and `EventTopicsTest.java` against the frozen brief acceptance
criteria and named tests. The suite now covers the named tests and the main happy/negative paths.
The gaps below are about guarding a known deferred defect, test fragility, and response-shape
coverage.

---

## Gap 1 — The deferred `AccountNotFoundException` leak in `activateFromVerificationToken` is not covered by any test (HIGH)

**Why it matters:** Phase 9 explicitly logged but did not fix the edge case where
`verificationTokenService.consume(rawToken)` resolves an account UUID that `accountRepository`
`findByAccountUuid` then fails to load. In that case the current production code throws
`AccountNotFoundException`, which maps to `404 NOT_FOUND` instead of the R5 uniform `400`
`INVALID_TOKEN`. Because there is no test for this path, the defect can regress silently if the
schema's `ON DELETE CASCADE` behavior ever changes or a caller reuses the method differently.

**Suggested test:** Add `shouldRejectVerificationWhenAccountDisappearsAfterConsume`: stub
`verificationTokenService.consume(...)` to return a UUID, stub `accountRepository.findByAccountUuid`
for that UUID to return `Optional.empty()`, and assert that `activateFromVerificationToken` throws
`VerificationTokenRejectedException` (not `AccountNotFoundException`). This test will fail against
the current code and should pass once the deferred fix is applied.

---

## Gap 2 — `shouldResendVerificationOnlyForPendingAccounts` models sequential behavior with repeated `when(...)` on the same matcher (MEDIUM)

**Why it matters:** The test stubs `accountRepository.findByEmail(...)` three times in a row with
the same email argument. Mockito's behavior for repeated stubbings of the same matcher is
implementation-dependent (the last stub may override all invocations, or strict-stubbing may flag
earlier stubs as unused). The test happens to pass now, but it is fragile and confusing to read.

**Suggested test:** Refactor to either:
- use three distinct email strings (one pending, one active, one unknown) so each `when` matches a
  unique input, or
- chain a single stub with
  `.thenReturn(Optional.of(pending), Optional.of(active), Optional.empty())`.

Either approach makes the sequential-return behavior explicit and resilient across Mockito
versions/strictness modes.

---

## Gap 3 — The actual HTTP `400`/`INVALID_TOKEN` response is never tested through the controller layer (MEDIUM)

**Why it matters:** `AccountControllerTest` uses plain Mockito and only verifies that the rejection
exception propagates uncaught from the controller method. `AccountExceptionHandlerTest` directly
calls the handler method. Neither proves that Spring's dispatcher actually routes the exception
from the controller to the handler and produces the intended HTTP status/problem-type body in a
real request.

**Suggested test:** Add a `MockMvc`-based test (consistent with any existing web-layer tests in the
module) for `POST /accounts/verify-email` with an invalid token, asserting:
- HTTP status `400`,
- `Content-Type: application/problem+json`,
- `problem.type` equals `ProblemTypes.INVALID_TOKEN`,
- response body contains no echo of the submitted token.

This closes the gap between the controller test and the handler test.

---

## Gap 4 — `resendVerificationIfPending` email normalization is not tested (LOW)

**Why it matters:** The service calls `normalize(email)` before looking up the account. A controller
bug or future refactor that passes the raw `ResendVerificationRequest.email()` could miss
uppercase/whitespace differences and produce a no-match for a valid pending account.

**Suggested test:** In `AccountServiceTest`, call `service.resendVerificationIfPending("  [EMAIL_REDACTED]  ")`
and verify `accountRepository.findByEmail` is invoked with `"[EMAIL_REDACTED]"` (the normalized form).

---

## Gap 5 — Duplicate registration does not verify absence of token-issue and outbox emission (LOW)

**Why it matters:** `registerRejectsKnownDuplicateWithoutTouchingEncoder` verifies the encoder and
repository are not touched on a duplicate, but it does not verify that
`verificationTokenService.issue(...)` and `outboxPublisher.publish(...)` are also skipped. The
exception is thrown before those calls today, but a future refactor could move them earlier.

**Suggested test:** Add `verify(verificationTokenService, never()).issue(any(), any())` and
`verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any())` to the existing
duplicate-registration test.

---

## Gap 6 — `AccountServiceTest` does not assert that the wrong-status rejection path never calls `account.activateEmail()` (LOW)

**Why it matters:** `shouldRejectVerificationWhenAccountIsNotPendingVerification` proves the method
throws `VerificationTokenRejectedException` and that no event/audit is published. It does not
explicitly prove that `Account.activateEmail()` itself was never invoked, so a regression that
removed the status pre-check and instead caught `InvalidAccountStateException` would still pass.

**Suggested test:** Use a Mockito-spied `Account` (or a custom subclass/flag) and verify that
`activateEmail()` is not called when the account is not `PENDING_VERIFICATION`.

---

## Summary

The T06 test suite is now substantially complete and all named tests are represented. The highest-
value addition is Gap 1: a guard test for the deferred `AccountNotFoundException` leak, which is the
only currently-accepted-but-unfixed defect in this task. Gaps 2 and 3 are test-quality improvements;
Gaps 4–6 are targeted assertions that reduce the chance of regressions in normalization, duplicate
handling, and status-check ordering.
