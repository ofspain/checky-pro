# auth · T10 — Phase 11: Test Review

Reviewed `artifacts/10-test-generation.md` against the T10 task statement
(`spec/auth-service/tasks.md`, task 10), scoped requirements R2/R5/R15, LOCKED
decision L5, the named tests in `spec/auth-service/package.md` §8, and
`agents.md`.

The manifest is accurate about what was implemented and the tests cover the core
acceptance criteria. The remaining gaps are in named-test alignment, cross-surface
coverage breadth, and end-to-end response comparison.

---

## Gap 1 — The required named test for R2 does not exist under its §8 name

**Why it matters:** The Phase 11 prompt header and `spec/auth-service/package.md`
§8 both require the named test
`shouldReturnSameAcknowledgementForDuplicateAndNewRegistration`. The manifest
acknowledges it does not exist literally and is instead satisfied in substance by
`registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`.
The spec verification checklist says every §3 acceptance criterion must have a
passing named test from §8; a strict interpretation makes this checklist item
fail.

**Suggested test:** Rename the existing `AccountControllerTest` test to the §8
named test, or add a thin aliasing test with the exact name that delegates to
the existing assertion. Either choice should be recorded in the manifest's
named-test section.

---

## Gap 2 — Cross-surface identity (AC4) is only proven for one rejection reason

**Why it matters:** Both AC4 tests
(`onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces`
and
`verifyEmailAndPasswordResetRejectionsProduceIdenticalResponsesThroughTheRealServiceMethods`)
use a single rejection trigger: an empty `consumeForPurpose` result (token not
found). They do not prove that an account-status-driven rejection on the verify
surface (e.g., `ACTIVE` or `LOCKED`) produces the same response as an
account-status-driven rejection on the reset surface (e.g., `PENDING`). If either
service method started wrapping a reason-dependent message or selecting a
different exception for status reasons, the current tests would not detect it.

**Suggested test:** Parametrize the service-level cross-surface test to exercise
multiple rejection families on each surface (e.g., token not-found, expired,
already-used, deleted-account, suspended-account, wrong account status) and
assert every verify-surface `ProblemDetail` equals every reset-surface
`ProblemDetail`.

---

## Gap 3 — Service-level cross-surface test lacks leak-prevention and absolute-value assertions

**Why it matters:** `verifyEmailAndPasswordResetRejectionsProduceIdenticalResponsesThroughTheRealServiceMethods`
compares the two `ProblemDetail` objects to each other, but never asserts the
expected status/type/title or that `instance`/`properties` are absent. If a future
handler change returned a common non-conformant response (e.g., `500` with the
same body for both), the relative-equality assertions would still pass.

**Suggested test:** Add the same absolute-value and leak-prevention assertions
used elsewhere: `status` is `400`, `type` is `ProblemTypes.INVALID_TOKEN`,
`title` is the expected string, `detail` is null, and both `instance` and
`properties` are null.

---

## Gap 4 — The named test for R5 exercises a superseded code path

**Why it matters:** `shouldNotRevealAccountExistenceForInvalidVerificationToken`
exists verbatim, but the manifest agrees it targets `verify()`/`consume()`, which
have zero production callers. The live production path uses
`consumeForPurpose(..., EMAIL_VERIFY)`, tested under a different name
(`shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose`).
The verification checklist asks for a named test; it passes nominally, but the
named test itself does not exercise the production path.

**Suggested test:** Keep the existing named test as regression-only, and either
rename the production-path test to the §8 name or add a verification-checklist
note explicitly mapping the §8 named test to the superseded path and the
production-path test as the real coverage.

---

## Gap 5 — The "within-surface" boundary tests do not prove handler-output uniformity

**Why it matters:** `shouldRejectVerificationForEveryNonPendingAccountStatus` and
`shouldRejectPasswordResetForIneligibleAccountStatuses` assert the exception type
is uniform across statuses, but they do not route those exceptions through
`AccountExceptionHandler` to prove the resulting HTTP responses are identical.
A handler regression that maps one status to a different title/type would not be
caught by these tests.

**Suggested test:** Capture each exception thrown inside the loop, pass it
through a bare `AccountExceptionHandler`, and assert the resulting
`ProblemDetail` is equal for every status.

---

## Gap 6 — No end-to-end HTTP response comparison across the two surfaces

**Why it matters:** The task statement says the endpoints must "produce identical
responses." All T10 tests stop at the service or handler layer. A controller
regression (e.g., extra header, different content-type negotiation, or a
conditional wrapper that re-throws the exception differently) could break
response identity without failing any current unit test.

**Suggested test:** Add a `@WebMvcTest`/`MockMvc` test or an integration test that
invokes `POST /accounts/verify-email` and `POST /accounts/password-reset` with
invalid tokens and compares the full HTTP response (status, content-type, body).
If this remains intentionally out of scope per previous phase decisions, record
the residual risk in the manifest rather than claiming full end-to-end coverage.

---

## Note on duplicate coverage

`shouldRejectVerificationWhenAccountIsNotPendingVerification` and
`shouldRejectVerificationForEveryNonPendingAccountStatus` both cover the `ACTIVE`
non-pending verification path. The manifest justifies keeping the older test for
its spy-based `activateEmail()`-never-invoked assertion. This is a reasonable
trade-off and not a harmful duplicate.
