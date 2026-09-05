# auth · T10 — Phase 8: Independent Code Review

Reviewed the Phase 6 implementation (`VerificationTokenServiceTest.java`,
`AccountExceptionHandlerTest.java`) together with `artifacts/07-self-review.md`,
against the frozen brief, `spec/auth-service/` requirements R2/R5/R15, L5,
`agents.md`, and the current production code under `services/auth/.../account`.

No production code was changed in this task, so findings are limited to test
coverage gaps and weak assertions.

---

## Finding 1 — Cross-surface comparison test does not exercise the two production surfaces

**Issue:** `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces`
claims to prove AC4 (invalid-verification-token and invalid-reset-token responses
are identical across surfaces). It constructs the same no-arg
`VerificationTokenRejectedException()` twice and calls the handler twice. It
demonstrates the handler is deterministic, but does not invoke
`AccountService.activateFromVerificationToken` or `AccountService.resetPassword`,
so it cannot detect a regression where one call site throws a different
exception, attaches a message, or diverges before reaching the handler.

**Evidence:**
- `AccountExceptionHandlerTest.java:59-68` — both `ProblemDetail`s are produced
  from explicitly constructed exceptions, not from the service methods.
- `AccountService.java` — `activateFromVerificationToken` and `resetPassword`
  each throw `VerificationTokenRejectedException` today, but the handler test
  has no dependency on that remaining true.

**Recommendation:** Strengthen AC4 either by:
(a) adding higher-level unit/integration tests that call
`AccountService.activateFromVerificationToken` with an invalid/bad-purpose token
and `AccountService.resetPassword` with an invalid token, route each result
through `AccountExceptionHandler`, and assert the two `ProblemDetail`s are equal;
or (b) renaming the current test to make clear it documents shared handler
mapping rather than proving cross-surface behavior.

**Confidence:** High.

---

## Finding 2 — New `consumeForPurpose(EMAIL_VERIFY)` boundary test omits two reachable rejection reasons

**Issue:** `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose`
covers not-found, expired, used, deleted-account, and suspended-account. R5's
"invalid" boundary also includes wrong-purpose tokens, and the production flow
for email verification can also reach `activateFromVerificationToken` with a
`LOCKED` account, where it throws `VerificationTokenRejectedException`. The test
name implies comprehensive R5 coverage; wrong-purpose and LOCKED are not present.
Wrong-purpose is exercised by adjacent tests, but not in the same EMAIL_VERIFY
`consumeForPurpose` boundary test.

**Evidence:**
- `VerificationTokenServiceTest.java:424-469` — five cases listed; no wrong-purpose
  or LOCKED-account case.
- `VerificationTokenService.java:124-125` — `consumeForPurpose` returns empty for
  purpose mismatch.
- `AccountService.java:129-130` — `activateFromVerificationToken` rejects any
  account whose status is not `PENDING_VERIFICATION`, including `LOCKED`.

**Recommendation:** Add wrong-purpose and LOCKED-account cases to the EMAIL_VERIFY
`consumeForPurpose` boundary test, or rename the test so it does not imply full
R5 coverage. Wrong-purpose at minimum should be included to maintain symmetry
with the PASSWORD_RESET-side tests.

**Confidence:** Medium.

---

## Finding 3 — R2 duplicate-registration coverage is unchanged and named-test mismatched

**Issue:** The task statement explicitly includes "duplicate registration" as one
of the three surfaces needing identical-response tests, but the Phase 6 diff did
not add or strengthen any R2 test. `AccountControllerTest` still relies on
`registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`,
which matches neither the §8 named test
`shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` nor the task's
wording.

**Evidence:**
- `spec/auth-service/package.md` §8 names the required test
  `shouldReturnSameAcknowledgementForDuplicateAndNewRegistration`.
- `AccountControllerTest.java:53` — existing test name differs from the named test.
- No new test or rename appears in the Phase 6 diff.

**Recommendation:** Either rename the existing test to the §8 named test or add
a thin alias test with the required name so the verification checklist item is
explicitly satisfied. If the brief intentionally treats R2 as regression-only,
document the rationale and the existing test mapping in the brief/artifact.

**Confidence:** Medium.

---

## Finding 4 — No end-to-end HTTP-response comparison across the two surfaces

**Issue:** R5/R15/L5 are user-facing API guarantees. The current tests verify
individual layers (token service returns `Optional.empty()`, service throws
`VerificationTokenRejectedException`, handler maps to a `ProblemDetail`), but no
test compares the actual HTTP response bodies of `POST /accounts/verify-email`
and `POST /accounts/password-reset` for invalid tokens. A controller-level
change (e.g., adding a conditional exception wrapper) could break uniformity
without failing the existing unit tests.

**Evidence:**
- `AccountControllerTest` verifies exception propagation but does not assert
  response body equality.
- `AccountExceptionHandlerTest` tests the handler in isolation, not through the
  controller/dispatcher.
- No integration test for T10 appears in the Phase 6 diff.

**Recommendation:** Add an integration test or `@WebMvcTest`/`MockMvc` test that
invokes both endpoints with invalid tokens and asserts identical status codes,
content type `application/problem+json`, and equal `ProblemDetail` bodies with no
account/token/state details leaked.

**Confidence:** Medium.

---

## Finding 5 — Cross-surface handler test asserts detail nullness separately rather than equality

**Issue:** `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces`
asserts `verifyEmailRejection.getDetail()` is null and `passwordResetRejection.getDetail()`
is null, but does not assert the two detail values are equal to each other.
The adjacent sibling test
`onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` does
assert equality, so the new test is stylistically inconsistent and slightly
weaker if the handler ever distinguishes the two surfaces through detail.

**Evidence:**
- `AccountExceptionHandlerTest.java:44-47` — sibling test uses equality assertions
  for all fields.
- `AccountExceptionHandlerTest.java:64-68` — cross-surface test uses status/type/title
  equality plus two independent null checks.

**Recommendation:** Replace the two `isNull()` assertions with a single
`assertThat(verifyEmailRejection.getDetail()).isEqualTo(passwordResetRejection.getDetail())`,
mirroring the sibling test.

**Confidence:** Low.

---

## Finding 6 — No guard against account/token detail leaking into `ProblemDetail` metadata

**Issue:** The enumeration-safety tests validate response fields but do not
guard against a future handler change that includes the account UUID, token
hash, or email in the `ProblemDetail` `instance`, `properties`, or an extension
field. R5/R15 and `agents.md` require no enumeration hints.

**Evidence:**
- `AccountExceptionHandlerTest.java:22-32` — asserts `getDetail()` is null but does
  not inspect other `ProblemDetail` fields for leaked identifiers.
- The handler implementation is not shown in the diff; the test suite assumes it
  already complies.

**Recommendation:** Add assertions that `problem.getInstance()` is null and that
`problem.getProperties()` is null or contains no account/token/email data. This
makes the enumeration-safety contract explicit and resistant to future handler
enhancements.

**Confidence:** Low.
