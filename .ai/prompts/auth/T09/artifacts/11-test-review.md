# auth · T09 — Phase 11: Test Review

Reviewed `artifacts/10-test-generation.md` against the T09 task statement
(`spec/auth-service/tasks.md`, task 9), scoped requirements R8–R10, LOCKED
 decision L2, and the named tests in `spec/auth-service/package.md` §8.

The test manifest is directionally sound and the register/reset policy-integration
tests correctly exercise the new ordering decisions. The following gaps remain
relative to the specification.

---

## Gap 1 — `changePassword` policy integration is omitted from the manifest

**Why it matters:** The T09 task statement explicitly says to apply
`PasswordPolicy` to *registration, change-password, and password-reset*.
The Phase 10 manifest instead marks `changePassword` as "untouched by this task"
and relies solely on pre-existing T08 tests (AC10). If the production code
now calls `passwordPolicy.validate` inside `changePassword`, those T08 tests do
not demonstrate that the new policy dependency is invoked, correctly ordered, or
that its exception propagates. If T08 already strengthened this in a separate
Phase 11 run, the T09 manifest should at minimum reference and claim that
coverage rather than asserting `changePassword` is "untouched."

**Suggested test:** Add `changePasswordCallsPasswordPolicyValidateAfterCurrentPasswordAndBeforeEncoding`
to `AccountServiceTest`, using `InOrder(passwordEncoder, passwordPolicy)` to
prove: `matches(current)` → `validate(new)` → `encode(new)`. Also add a
rejection-path test showing `validate` throwing prevents `encode` and the audit.

---

## Gap 2 — Register enumeration-safety ordering is not asserted on the repository

**Why it matters:** AC4/L5 is the core security justification for running the
policy check before `existsByEmail`. The manifest *describes* the intended
ordering, but the described test
(`registerRejectsPolicyViolatingPasswordEvenWhenEmailIsAlreadyRegistered`) only
checks the exception type. Without an explicit `verify(accountRepository,
never()).existsByEmail(...)` assertion, the test would still pass if the
implementation accidentally ran `existsByEmail` first and then checked policy —
exposing the same response for existing vs. new emails only because both happen
to throw the same exception.

**Suggested test:** Strengthen
`registerRejectsPolicyViolatingPasswordEvenWhenEmailIsAlreadyRegistered` with
`InOrder(passwordPolicy, accountRepository)` or a `never()` verification that
`existsByEmail` is not invoked when the password is policy-violating.

---

## Gap 3 — Reset token is not proven reusable after a policy-violating attempt

**Why it matters:** The Phase 10 manifest explains — correctly — that because
`resetPassword` is `@Transactional`, a policy violation thrown after
`consumeForPurpose` rolls back the token consumption, leaving the token valid for
a retry. The listed unit test
(`resetPasswordRejectsPolicyViolatingPasswordWithoutMutatingAccountOrRevokingSessions`)
uses a mock for `verificationTokenService` and cannot observe transactional
rollback; it only proves no mutation happened *within* the service method. If a
future refactor moved `consumeForPurpose` outside the transaction or consumed the
token differently, this test would still pass while the token would silently be
burned.

**Suggested test:** Add an integration test (Testcontainers Postgres + Kafka) or
a transactional unit test with a real verification-token row that:
1. requests a reset token, 2. attempts to reset with a policy-violating password,
3. asserts `PasswordPolicyViolationException`, and 4. immediately re-submits a
compliant password with the *same* token and succeeds.

---

## Gap 4 — Failure-mode mapping is only tested inside `PasswordPolicyTest`

**Why it matters:** R8 (length), R9 (breach), and R10 (breach-API-down) are
covered entirely as internal `PasswordPolicy` behavior. The service-level tests
stub `passwordPolicy.validate` to throw a generic
`PasswordPolicyViolationException` and therefore do not prove that the three
different failure catalogues survive the integration into registration,
reset, and change-password flows. A wiring bug that always throws a single,
generic message would satisfy every mocked service test while masking a
regression in user-facing diagnostics and audit specificity.

**Suggested test:** In `AccountServiceTest`, add parametrized rejection tests
(starting at the boundary values: 11, 129, and a known-breached password) that
call the real `PasswordPolicy` (with a fixed/mock `BreachedPasswordClient`) and
assert that the resulting exception is
`PasswordPolicyViolationException` with a message clearly tied to the specific
mode. Add the fail-open brea-check-failed path once, and verify the
`password.breach_check_failed` audit is still emitted.

---

## Gap 5 — Controller tests prove exception propagation but not response semantics

**Why it matters:** `registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate`
and `passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate`
assert that the exception is not swallowed by the controller. They do not assert
status code, content-type, or that the body is RFC 9457 `application/problem+json`
without enumeration hints. The agents.md standing rule and R46 require this
response contract; a controller that re-threw the exception but used the wrong
mapper would pass both controller tests.

**Suggested test:** Either extend the existing controller assertions to check
that the propagated exception is the one the exception handler maps to
`HttpStatus.BAD_REQUEST`, or add a dedicated
`AccountExceptionHandlerTest` entry asserting the `ProblemDetail` for
`PasswordPolicyViolationException` has status 400, type title, and no internal
message details.

---

## Note on current code state

The live test files under `services/auth/.../account/` already contain comments
labelled "Kimi Phase 11 Gap" that address several of the above points
(particularly the `changePassword` ordering and `resetPassword` rejection-path
no-op assertions). The Phase 10 artifact should be reconciled against those
changes so that the manifest accurately reflects what is already implemented and
what remains to be tested.
