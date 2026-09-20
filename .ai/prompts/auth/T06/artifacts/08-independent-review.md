# auth · T06 — Phase 8: Independent Code Review Findings

Reviewed the implementation in `AccountService.java`, `AccountController.java`,
`AccountExceptionHandler.java`, `VerifyEmailRequest.java`, `ResendVerificationRequest.java`,
`EmailRequestedEventPayload.java`, `EventTopics.java`, `PublicEndpoints.java`,
`ProblemTypes.java`, and the existing `AccountServiceTest.java`, against the frozen brief
(`04-frozen-task-brief.md`) and `agents.md`. Findings include the self-review's real defects and
a few fresh observations.

---

## Finding 1 — `AccountServiceTest.java` no longer compiles (HIGH)

**Issue:** `AccountService` gained a `VerificationTokenService` constructor parameter, but
`AccountServiceTest.setUp()` still constructs the service with the old five-argument signature.
This is a hard compile failure in an already-committed test file.

**Evidence:**
- `AccountService.java` lines 42–44: constructor now requires `VerificationTokenService`.
- `AccountServiceTest.java` lines 56–61: constructor call omits the new parameter.

**Recommendation:** Add a mocked `VerificationTokenService` to the test's `setUp()` constructor call.
Any fix here is a prerequisite for running the module's tests.

**Confidence:** HIGH.

---

## Finding 2 — `AccountServiceTest.registerNeverPublishesAnEvent` asserts the opposite of the new behavior (HIGH)

**Issue:** The existing test explicitly verifies that `register` never publishes an outbox event.
After T06, `register` must publish `auth.email.requested` (R3). Once the compile error is fixed,
this test will fail logically rather than by accident.

**Evidence:**
- `AccountServiceTest.java` lines 83–94: `registerNeverPublishesAnEvent`.
- `AccountService.java` lines 78–79 and 191–201: `register` now calls `issueAndEmitVerificationEmail`,
  which calls `outboxPublisher.publish(...)`.

**Recommendation:** Replace this test with one that asserts `register` publishes an
`auth.email.requested` event with purpose `verify_email`, aggregate type `verification-token`, and
aggregate ID equal to the new account's UUID.

**Confidence:** HIGH.

---

## Finding 3 — `activateFromVerificationToken` can leak account existence via `AccountNotFoundException` (MEDIUM)

**Issue:** After `VerificationTokenService.consume(rawToken)` returns a UUID, the method calls the
shared `getAccount(accountUuid)`, which throws `AccountNotFoundException` if the account is missing.
`AccountExceptionHandler` maps that to a `404 NOT_FOUND` response — a different, distinguishing
response from the uniform `400 INVALID_TOKEN` required by R5.

The path is normally unreachable because `verification_tokens.account_id` has `ON DELETE CASCADE`,
but the method's R5 contract should not depend on an external FK constraint never changing.

**Evidence:**
- `AccountService.java` line 97: `Account account = getAccount(accountUuid);`
- `AccountExceptionHandler.java` lines 17–23: `AccountNotFoundException` → `404 NOT_FOUND`.
- `AccountExceptionHandler.java` lines 39–45: `VerificationTokenRejectedException` → `400 INVALID_TOKEN`.

**Recommendation:** Replace the `getAccount(...)` call with
`accountRepository.findByAccountUuid(accountUuid)` and treat `Optional.empty()` exactly like a
non-`PENDING_VERIFICATION` account — i.e., throw `VerificationTokenRejectedException`.

**Confidence:** MEDIUM.

---

## Finding 4 — `AccountService.register` javadoc directly contradicts the implementation (MEDIUM)

**Issue:** The method's javadoc still claims "No event is published here" and that the
`auth.email.requested` event belongs to a "not-yet-built verification-token flow." The code now
publishes that event on line 78. This is actively misleading.

**Evidence:**
- `AccountService.java` lines 58–61: stale javadoc.
- `AccountService.java` line 78: `issueAndEmitVerificationEmail(saved);`.

**Recommendation:** Rewrite the javadoc to state that registration emits `auth.email.requested`
(with purpose `verify_email`) in the same transaction as account creation.

**Confidence:** HIGH.

---

## Finding 5 — `AccountService.activateEmail(UUID, UUID)` javadoc is also stale (MEDIUM)

**Issue:** The javadoc describes the self-service verification flow as "not yet built" and says the
method is reachable only via an admin endpoint. The self-service path now exists as
`activateFromVerificationToken`.

**Evidence:**
- `AccountService.java` lines 125–129: stale "Interim (D-024)... not yet built" comment.
- `AccountService.java` lines 92–106: the new `activateFromVerificationToken` self-service method.

**Recommendation:** Update the javadoc to note that `activateEmail` remains the admin-initiated
path (with a real admin `actorUuid`), while `activateFromVerificationToken` is the self-service path
(actor = account's own UUID).

**Confidence:** HIGH.

---

## Finding 6 — `AccountController` class-level javadoc no longer describes the public surface (LOW)

**Issue:** The class comment says "{@code POST /accounts} is the only public route this service
exposes on this path." T06 adds public `POST /accounts/verify-email` and `POST /accounts/resend-verification`.

**Evidence:**
- `AccountController.java` lines 21–22: stale class javadoc.
- `AccountController.java` lines 68 and 79: new public `@PostMapping` methods.
- `PublicEndpoints.java` lines 30–31: both new paths are listed as public.

**Recommendation:** Update the class javadoc to list the three public POST routes (`/accounts`,
`/accounts/verify-email`, `/accounts/resend-verification`) and note that the other `/accounts/**`
endpoints require authentication.

**Confidence:** HIGH.

---

## Finding 7 — No T06-specific tests exist yet, and the existing controller test may also break (LOW/MEDIUM)

**Issue:** The test manifest from the frozen brief Required Tests includes controller-level tests
for verify-email, resend-verification, event routing, and registration event emission. No new test
files were added in Phase 6, and `AccountServiceTest` is already broken. `AccountControllerTest`
will likely also need updates because `AccountService.register` now depends on
`VerificationTokenService` even in `MockMvc` controller tests.

**Evidence:**
- Frozen brief Required Tests (lines 193–209).
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` exists and
  presumably constructs/uses `AccountService`.

**Recommendation:** Once the production code is accepted, Phase 10 must add/update:
- `AccountServiceTest` for event emission on register and the new self-service activation/resend
  methods.
- `AccountControllerTest` for verify-email and resend-verification endpoints, including that all
  invalid-token reasons and wrong-status cases return the same `INVALID_TOKEN` response.
- `EventTopicsTest` to assert the new `verification-token` mapping.

**Confidence:** MEDIUM.

---

## Summary

The T06 implementation matches the frozen brief's design decisions well, but the existing test file
is broken (Finding 1) and contains an assertion that is now the opposite of required behavior
(Finding 2). Findings 3–6 are correctness or documentation issues that should be fixed in Phase 9;
Finding 7 is a testing-coverage reminder for Phase 10. The most serious runtime risk is Finding 3,
because it is a latent enumeration-safety violation in the public `verify-email` path.
