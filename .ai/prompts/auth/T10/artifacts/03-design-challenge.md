# auth · T10 — Phase 3: Design Challenge

Reviewed `artifacts/02-task-implementation-brief.md` against the T10 task statement
(`spec/auth-service/tasks.md`, task 10), scoped requirements R2/R5/R15, LOCKED
decision L5, and the named tests in `spec/auth-service/package.md` §8.

The brief has a sound, narrow scope and correctly avoids scope creep. The
following issues should be resolved before the brief is frozen.

---

## Issue 1 — AC4 does not define what "identical response" means

**Severity:** Medium  
**Evidence:** AC4 requires "An invalid-verification-token response and an invalid-reset-token
response are directly shown to be identical to each other, not merely each internally uniform."
The TIB says the new test will compare "the `ProblemDetail` (or exception instance/type)." The
"or" leaves the comparison criteria ambiguous. Two different `VerificationTokenRejectedException`
instances would pass an "instance/type" comparison while producing different `ProblemDetail`
bodies if the handler later changes.  
**Recommended brief amendment:** Pin the comparison to the observable HTTP contract: same HTTP
status, same `application/problem+json` content type, and equal `ProblemDetail` status/type/title
fields, with no leaked account/token/state details. State explicitly whether the test sits at the
handler layer (recommended — both surfaces share `onVerificationTokenRejected`) or at the service
layer.

---

## Issue 2 — AC2 omits wrong-purpose rejection for `consumeForPurpose(..., EMAIL_VERIFY)`

**Severity:** Medium  
**Evidence:** R5 requires uniform rejection for every invalid verification-token reason. In
`VerificationTokenService.consumeForPurpose`, "wrong purpose" is a distinct, attacker-reachable
rejection path: it returns `Optional.empty()` immediately, without marking the token consumed.
The existing PASSWORD_RESET-side tests already cover this
(`shouldRejectTokenWhenPurposeDoesNotMatchAndLeaveItUnconsumed`), but AC2's enumerated boundary
set is `(not-found, expired, used, deleted, suspended)` — no wrong-purpose case. This leaves
EMAIL_VERIFY's wrong-purpose behavior unverified and breaks symmetry with the reset side.  
**Recommended brief amendment:** Add "wrong purpose" to AC2 and to the required boundary test for
`consumeForPurpose(..., EMAIL_VERIFY)`, mirroring the existing PASSWORD_RESET wrong-purpose
test.

---

## Issue 3 — R2 coverage is asserted only at the controller layer

**Severity:** Low  
**Evidence:** AC1 lists R2 as already satisfied by
`registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` in
`AccountControllerTest`. That test stubs `accountService.register` to throw
`DuplicateEmailException`, so it proves the controller's catch-and-translate behavior but does
not assert that the service actually emits the same exception for a duplicate as for a new
registration. If `AccountService.register` drifts to throw something else, the controller test
stays green while the endpoint would misbehave in integration.  
**Recommended brief amendment:** Add a regression assertion in `AccountServiceTest` (or cite an
existing one) that a duplicate email path reaches the same `DuplicateEmailException` as a new
registration reaches successful persistence, and reference it in AC1.

---

## Issue 4 — Spec verification-checklist named-test mismatch is waved away

**Severity:** Low  
**Evidence:** `spec/auth-service/package.md` §8 requires the named test
`shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` for R2. The TIB explicitly rejects
renaming the existing `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`
as "unrelated churn." The package verification checklist says: "All §3 acceptance criteria have a
passing named test from §8." Without the exact named test, the checklist item is at risk of being
marked incomplete by a strict reviewer.  
**Recommended brief amendment:** Either rename the existing test to the §8 name, or add a thin
pass-through/alias test with the §8 name that delegates to the existing assertion (and note the
existing test remains the real substance). Choose one and state the rationale.

---

## Issue 5 — Cross-surface test location is deferred to Phase 5

**Severity:** Low  
**Evidence:** The TIB says the exact file for the cross-surface comparison test
(`AccountExceptionHandlerTest` vs. `AccountServiceTest`) is "a Phase 5 planning decision." This
leaves an unstated dependency on test conventions: handler-layer tests may need Spring context or
bean wiring, while service-layer tests are plain Mockito. The constraint section already mandates
"plain JUnit 5 + Mockito + AssertJ, no Spring context."  
**Recommended brief amendment:** Decide the layer now: recommend
`AccountExceptionHandlerTest` with a plain Mockito-instantiated handler (both rejection surfaces
route through `onVerificationTokenRejected`, so no Spring context is required). State that this
satisfies the no-Spring-context constraint.

---

## Issue 6 — AC2 conflates token-level and account-level rejection reasons

**Severity:** Low  
**Evidence:** AC2 lists rejection reasons for `consumeForPurpose(..., EMAIL_VERIFY)` as
"not-found, expired, used, deleted, suspended." "Deleted" and "suspended" are account states, not
token states; grouping them under `consumeForPurpose` reaction reasons makes the acceptance
criterion harder to read and harder to map to the implementation's two-step flow (`consumeForPurpose`
returns empty for unusable account; `activateFromVerificationToken` separately enforces the
`PENDING_VERIFICATION` status gate).  
**Recommended brief amendment:** Split AC2 into token invalidity reasons (not found, expired,
already used, wrong purpose) and account ineligibility reasons (deleted or suspended account).
This clarifies what the boundary test must actually stub.

---

## Issue 7 — The task statement's "invalid reset tokens" could be misread

**Severity:** Low  
**Evidence:** The T10 task statement says "duplicate registration, invalid verification tokens,
and invalid reset tokens produce identical responses." A reader might interpret "invalid reset
tokens" as including `POST /accounts/password-reset-request` (which returns a uniform
acknowledgement for any email). The TIB correctly limits scope to confirmation tokens, but this is
implicit.  
**Recommended brief amendment:** Add an explicit sentence under Scope: "Reset-token uniformity
refers to the `POST /accounts/password-reset` confirmation endpoint, not to the request endpoint."
