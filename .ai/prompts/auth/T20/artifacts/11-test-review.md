# auth · T20 · Phase 11 — Test Review

Consumed `artifacts/10-test-generation.md` and the test files it describes.

No duplicate tests were found. The Testcontainers-backed suites (`MfaPersistenceIntegrationTest`, `MfaServicePersistenceIntegrationTest`, `SasLoginIntegrationTest`) are written but unexecuted in this environment — the same inherited environment limitation documented in Phase 10, not a test-design gap.

The following gaps remain against the acceptance criteria in the frozen brief and the named tests in `spec/auth-service/package.md` §8.

---

1. **Gap · No end-to-end assertion that a failed login-time MFA attempt persists an `mfa.failed` audit row.**
   `MfaServiceTest` verifies `auditService.record(...)` is called for service-level failures, and `TotpAuthenticationProviderTest` verifies the uniform response, but no integration test (unit or running) asserts that a wrong TOTP/recovery code at `/login` actually leaves a durable `auth_audit` row. Because `AuditService.record` is `REQUIRES_NEW`, a regression in transaction propagation or event routing could drop this audit without failing any existing test.
   **Suggested test:** Add to `SasLoginIntegrationTest` an assertion that after a wrong TOTP/recovery-code login, `auditService.list(accountUuid, ...)` contains an `mfa.failed` event.

2. **Gap · No full-stack test for a non-mandatory-role account without enrollment logging in password-only.**
   R27 / the negative case for R24 is covered only at the provider unit layer and the `TokenClaimsCustomizer` layer. `SasLoginIntegrationTest` does not exercise the real `/login` flow for a plain `USER`/`COMPLIANCE` account that has no confirmed enrollment.
   **Suggested test:** Add `userWithoutEnrollmentLogsInWithPasswordOnly` to `SasLoginIntegrationTest`, asserting the redirect does not contain `/login?error`.

3. **Gap · Recovery-code single-use semantics are not tested end-to-end through the login flow.**
   `MfaServiceTest` covers `markUsed` at the service layer, and `merchantCanLoginWithAnUnusedRecoveryCode` proves a recovery code works once, but no test proves the same code is rejected on a second login attempt.
   **Suggested test:** Extend `SasLoginIntegrationTest.merchantCanLoginWithAnUnusedRecoveryCode` (or add a new test) that logs in with the same recovery code a second time and asserts `/login?error`.

4. **Gap · `mfaCode` trimming (Phase 9 fix) is not exercised by any test.**
   `TotpAuthenticationDetailsSource` now `.strip()`s the form value, but every test passes already-clean strings. A regression that removed the `.strip()` would not be caught, and copy-paste whitespace would silently break logins.
   **Suggested test:** In `TotpAuthenticationProviderTest`, add a test where the request's `mfaCode` has leading/trailing whitespace and assert it is still dispatched correctly (e.g., a 6-digit code with spaces goes to `verifyTotpCodeForLogin`).

5. **Gap · Unexpected MFA-service failures are not asserted to produce operator-visible logs.**
   Phase 9 added a `log.warn(...)` for non-MFA `RuntimeException`s inside `TotpAuthenticationProvider.verifyMfaCodeOrFail`. The existing `unexpectedMfaServiceFailureStillFailsUniformly` only asserts the uniform client response, not the log line.
   **Suggested test:** Capture logging (e.g., a Logback `ListAppender`) in that test and assert a WARN message is emitted containing the unexpected exception's message.

6. **Gap · `OTP_VERIFIED` synthetic authority is not asserted to be absent from the token `roles` claim.**
   `TokenClaimsCustomizer` resolves `roles` from `RoleService`, so the synthetic authority should not appear there, but a future regression could cause it to leak into roles. The R26 test checks `amr`/`acr` but does not assert `roles` excludes `OTP_VERIFIED`.
   **Suggested test:** In `TokenClaimsCustomizerTest.shouldIssueTokenWithOtpAmrAndAcrAfterMfa`, add `assertThat(built.<List<String>>getClaim("roles")).doesNotContain("OTP_VERIFIED")`.

7. **Gap · `recordUseIfNewer` does not explicitly reject an earlier step after a later one was accepted.**
   `MfaPersistenceIntegrationTest.recordUseIfNewerAcceptsALaterStepButRejectsAReplayOfTheSameStep` proves later-step acceptance and same-step replay rejection, but does not assert that returning to an earlier step is also rejected. The bug fixed in Phase 9 was about comparing step starts; this boundary is the inverse of the same logic.
   **Suggested test:** After accepting `stepTwoStart`, call `recordUseIfNewer(saved.getId(), stepOneStart)` and assert it returns `0`.

8. **Gap · `SasLoginIntegrationTest` relies on `Instant.now()` multiple times within a test, which can cross TOTP step boundaries.**
   Each `referenceGenerateCode(enrollment.secret(), Instant.now())` call uses the current wall-clock step. The ±1-step tolerance window usually hides small delays, but if a test pauses near a boundary for more than ~60 seconds total, the generated code could fall outside the tolerance window and cause a flaky failure.
   **Suggested test:** Use a single captured `Instant` per test (or a `Clock` fixed for the test) and pass it to every `referenceGenerateCode` and `attemptLogin` call, eliminating the drift risk.
