# auth · T09 — Phase 8: Independent Code Review Findings

Adversarial review of the Phase 6 implementation (`ef1c5ce`) and Phase 7 self-review. Findings only — no rewrites.

---

### Finding 1 — Phase 6 omitted all test-file changes required by the frozen brief and implementation plan

**Confidence:** High

**Issue:** The production-code changes for T09 are present, but the test files explicitly listed in scope were not modified. Without them, the implementation cannot satisfy its own acceptance criteria or the named-test traceability checklist.

**Evidence:**
- `git show --stat ef1c5ce` shows only two production files changed: `AccountService.java` and `RegisterAccountRequest.java`.
- `04-frozen-task-brief.md` §Files to Modify lists:
  - `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java`
  - `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java`
  - `services/auth/src/test/java/com/themistra/auth/account/dto/RegisterAccountRequestValidationTest.java`
- `05-implementation-plan.md` §Files to Modify lists the same three test files and §Execution order places their updates at steps 4–6.
- `06-implementation-notes.md` explicitly states: "No test files touched in this phase" and "Test-side proof of AC1-AC10 … is Phase 10's job, not this phase's."

**Recommendation:** Do not treat the implementation as merge-ready until the frozen brief's required test updates are delivered. Either fold the missing test work back into Phase 6/9, or explicitly re-scope the brief so Phase 10 owns the test changes — but the current mismatch between the approved brief and the committed code is a blocker.

---

### Finding 2 — `AccountServiceTest.registerRejectsKnownDuplicateWithoutTouchingEncoder` is now a failing test

**Confidence:** High

**Issue:** The existing test asserts that the password encoder is never invoked when the email is a duplicate. The new `register` ordering calls `passwordEncoder.encode(...)` before `accountRepository.existsByEmail(...)`, so the encoder is always touched. The test will fail at `verify(passwordEncoder, never()).encode(anyString())`.

**Evidence:**
- `AccountServiceTest.java:149` — `verify(passwordEncoder, never()).encode(anyString());`
- `AccountService.java:86` — `Account account = Account.register(email, passwordEncoder.encode(request.password()));`
- `AccountService.java:87-91` — `passwordPolicy.validate(...)` then `existsByEmail(...)`.
- `AccountServiceTest.java:42` — `RAW_PASSWORD = "correct-horse-battery"` (24 chars, policy-compliant); the mocked `passwordPolicy.validate(...)` is a no-op, so execution reaches encode before `DuplicateEmailException`.

**Recommendation:** Update the test as already specified in `05-implementation-plan.md`: rename to reflect that encoding happens, flip the encoder assertion to `verify(passwordEncoder).encode(RAW_PASSWORD)`, and keep the existing assertions that `saveAndFlush`, `verificationTokenService.issue(...)`, and `outboxPublisher.publish(...)` are never reached. The `06-implementation-notes.md` claim that "existing register/reset tests … pass unmodified" is incorrect for this test.

---

### Finding 3 — `RegisterAccountRequestValidationTest.passwordBoundaries` asserts behavior that no longer exists

**Confidence:** High

**Issue:** Removing `@Size(min=12, max=128)` from `RegisterAccountRequest.password` means bean validation no longer enforces the length boundary. The test still asserts that 11- and 129-character passwords produce constraint violations, which will fail.

**Evidence:**
- `RegisterAccountRequest.java:20-21` — `password()` now carries only `@NotBlank`.
- `RegisterAccountRequestValidationTest.java:45-50` — `passwordBoundaries()` asserts:
  - `validate("[EMAIL_REDACTED]", "a".repeat(11))` is not empty
  - `validate("[EMAIL_REDACTED]", "a".repeat(129))` is not empty

**Recommendation:** Remove or rewrite `passwordBoundaries()` to document that the bean-validation layer now rejects only blank passwords. Consider renaming the class (e.g., `RegisterAccountRequestBeanValidationTest`) since "Validation" no longer covers password length. Preserve the other four tests (`validRequestPasses`, `noCompositionRules_longSimplePassphraseIsAllowed`, `blankAndMalformedEmailsRejected`, `blankPasswordRejected`), which remain valid. This exact update is already in `05-implementation-plan.md` step 6.

---

### Finding 4 — No new `AccountServiceTest` coverage for `register` policy enforcement or ordering

**Confidence:** High

**Issue:** There is no test proving that `register` calls `passwordPolicy.validate`, that a policy violation prevents the duplicate-email/repository/outbox paths, or that enumeration safety (AC4) is preserved.

**Evidence:**
- Grep of `AccountServiceTest.java` for `passwordPolicy` shows only the T08 `changePassword` tests reference it.
- No test exists for `register` + `PasswordPolicyViolationException`.
- `04-frozen-task-brief.md` §Required Tests and `05-implementation-plan.md` specify four new `AccountServiceTest` methods for `register`.

**Recommendation:** Add the tests described in `05-implementation-plan.md`:
- `register` calls `passwordPolicy.validate` with the constructed account UUID and submitted password.
- `register` rejects a policy-violating password before touching the repository or outbox.
- `register` rejects a policy violation identically even when the email is already registered (proves AC4/L5).

---

### Finding 5 — No new `AccountServiceTest` coverage for `resetPassword` policy enforcement or ordering

**Confidence:** High

**Issue:** There is no test proving that `resetPassword` calls `passwordPolicy.validate`, that validation precedes mutation (`unlock`, `encode`, `revoke`, `audit`), or that a policy violation prevents all side effects.

**Evidence:**
- `AccountServiceTest.java` has no new `resetPassword` tests added since the T07/T08 baseline.
- `AccountService.java:200-210` inserts `passwordPolicy.validate(...)` between eligibility and mutation, but no test asserts this ordering.
- `04-frozen-task-brief.md` AC8 and `05-implementation-plan.md` require an `InOrder` proof.

**Recommendation:** Add the tests specified in `05-implementation-plan.md`:
- `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation` using `InOrder` to prove `validate` precedes `passwordEncoder.encode(...)`, `refreshTokenTracker.revokeAllForPrincipal(...)`, and `auditService.record(...)`.
- `resetPasswordRejectsPolicyViolatingPasswordWithoutMutatingAccountOrRevokingSessions` proving no encode/revoke/audit occurs when `validate` throws.

---

### Finding 6 — `AccountControllerTest` missing propagation tests for `register` and `passwordReset`

**Confidence:** High

**Issue:** The controller layer has no tests verifying that `PasswordPolicyViolationException` propagates uncaught from the new `register` and `passwordReset` call sites for the exception handler to translate.

**Evidence:**
- `AccountControllerTest.java` contains `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate` (T08).
- No equivalent tests exist for `controller.register(...)` or `controller.passwordReset(...)`.
- `04-frozen-task-brief.md` §Required Tests and `05-implementation-plan.md` explicitly require both.

**Recommendation:** Add `registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate` and `passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate`, mirroring the T08 controller test pattern and verifying that the controller's `try/catch (DuplicateEmailException)` block does not also swallow `PasswordPolicyViolationException`.

---

### Finding 7 — HIBP breach check executes inside a `@Transactional` public endpoint with no mandated rate-limit backstop

**Confidence:** Medium

**Issue:** `AccountService.register` is `@Transactional` and public (`POST /accounts`). Calling `passwordPolicy.validate` from within it triggers an outbound HTTPS request to the HIBP range API (default timeout 3000 ms), holding a DB connection and transaction open for the duration. Registration is not one of the endpoints `requirements.md` R41 names for per-account rate limiting (`login`, `/oauth2/token`, `password-reset confirmation`, `MFA verification`).

**Evidence:**
- `AccountService.java:82-87` — `@Transactional` `register` calls `passwordPolicy.validate`.
- `PasswordPolicy.java:64-74` — `validateNotBreached` calls `breachCheckClient.isBreached(...)`.
- `services/auth/src/main/resources/application.properties:65` — `breach-check.timeout-ms=3000`.
- `requirements.md:65` (R41) — registration is absent from the rate-limited endpoint list.

**Recommendation:** Treat as an accepted residual risk per the self-review, but ensure the project tracks a follow-up. Options are (a) move the breach-check call outside the transactional boundary (requires restructuring `@Transactional` placement), or (b) add `POST /accounts` to R41's per-account rate-limit set. Do not silently ignore it.

---

### Finding 8 — `resetPassword` Javadoc overstates token consumption on a policy violation

**Confidence:** Medium

**Issue:** The Javadoc says "A policy-violating password submitted with an otherwise-valid, unused token still consumes that token." Because `consumeForPurpose` runs inside the same `@Transactional` method, the token is marked consumed only within the transaction; when `passwordPolicy.validate` throws, Spring rolls back the transaction, so the token is NOT durably consumed.

**Evidence:**
- `AccountService.java:185-186` — "still consumes that token".
- `AccountService.java:192` — method is `@Transactional`.
- `AccountService.java:194-196` — `consumeForPurpose` marks the token used; `AccountService.java:203` can throw `PasswordPolicyViolationException`, triggering rollback.

**Recommendation:** Clarify the Javadoc to state that the consume check passes (so the response differs from an invalid-token rejection), but the transaction rolls back and the token remains available. The accepted residual token-validity signal (Finding 3 of the frozen brief) comes from the response type, not from durable token consumption. This avoids misleading future maintainers into believing the token is permanently burned on a bad-password attempt.

---

### Finding 9 — Implementation plan and implementation notes disagree on whether tests belong in Phase 6

**Confidence:** Medium

**Issue:** `05-implementation-plan.md` places test-file updates at steps 4–6 of the Phase 6 execution order, while `06-implementation-notes.md` states tests are Phase 10's job. The committed code followed the notes, not the plan.

**Evidence:**
- `05-implementation-plan.md` §Execution order steps 4–6: `AccountServiceTest.java`, `AccountControllerTest.java`, `RegisterAccountRequestValidationTest.java`.
- `06-implementation-notes.md`: "No test files touched in this phase" and "Test-side proof of AC1-AC10 … is Phase 10's job."

**Recommendation:** Resolve the discrepancy at the Phase 9 human-approval gate. If tests are genuinely Phase 10 work, update the frozen brief and implementation plan so the scope boundary is clear. If tests are required before Phase 6 is considered complete, do not approve the current commit and send it back for the missing test work.
