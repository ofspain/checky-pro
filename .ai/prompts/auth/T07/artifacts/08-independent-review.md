# auth · T07 · Phase 8 — Independent Code Review Findings

Reviewed the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/auth-service/agents.md`, and the current `services/auth` source.

---

## Phase 7 Self-Review Findings — Validated

| # | Issue | Validation |
|---|-------|------------|
| 1 | `AccountServiceTest` does not compile because it omits the new `RefreshTokenTracker` constructor argument | **Confirmed.** `AccountService.java:44-47` declares a 7-argument constructor; `AccountServiceTest.java:61-66` calls it with 6 arguments. Confidence: **High**. |
| 2 | Four existing `AccountServiceTest` tests still stub `verificationTokenService.consume(...)` instead of `consumeForPurpose(..., EMAIL_VERIFY)` | **Confirmed and broader in impact.** The four stubs are at `AccountServiceTest.java:189`, `:213`, `:233`, `:251`; `AccountService.activateFromVerificationToken` now calls `consumeForPurpose(..., EMAIL_VERIFY)` at `AccountService.java:102-104`. Under `MockitoExtension` strict stubbing, the old stubs will fail as `UnnecessaryStubbingException`; additionally, `consumeForPurpose` is unstubbed, so it returns `Optional.empty()` and the success test will incorrectly throw `VerificationTokenRejectedException`. Confidence: **High**. |

---

## Independent Findings

### 1. The T07 implementation is almost entirely untested

- **Issue:** The new password-reset service methods, endpoints, purpose-aware token consumption, and family-wide revocation have no unit or integration tests.
- **Evidence:** A repo-wide search for `requestPasswordReset`, `resetPassword`, `consumeForPurpose`, `revokeAllForPrincipal`, or `forPasswordReset` finds only production code plus two references in `VerificationTokenServiceTest` that merely assert a `PASSWORD_RESET` token can be *issued* (`VerificationTokenServiceTest.java:286-290`). None of the test cases listed in the frozen brief's Required Tests section exist: there is no `shouldEmitPasswordResetEventOnlyWhenEmailExists`, no `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`, no wrong-purpose rejection test, no `LOCKED`→`ACTIVE` reset test, no revoke-all isolation test, and no `PasswordResetConfirmRequest.toString()` guard test.
- **Recommendation:** Add the missing tests before Phase 9 remediation closes. Minimum set: (a) `AccountServiceTest` tests for `requestPasswordReset` (match/no-match/wrong-status) and `resetPassword` (success, wrong purpose, wrong status, expired/used token, LOCKED unlock, family-revoke delegation); (b) `VerificationTokenServiceTest` tests for `consumeForPurpose` (correct purpose success, wrong purpose no-mutation, expiry, reuse); (c) `RefreshTokenTrackerTest` tests for `revokeAllForPrincipal` (single family, multiple families, idempotency, correct reason); (d) `AccountControllerTest` tests for the two new endpoints' status codes and acknowledgement shape; (e) a dedicated test for `RegistrationAcknowledgement.forPasswordReset()` wording and `PasswordResetConfirmRequest.toString()` redaction.
- **Confidence:** High.

---

### 2. `AccountControllerTest` omits the two new public endpoints

- **Issue:** The controller test file was not extended for `POST /accounts/password-reset-request` or `POST /accounts/password-reset`, leaving the contract between controller and service unverified.
- **Evidence:** `AccountControllerTest.java` contains five tests (register success/duplicate, `me`, verify-email, resend-verification) and no mention of `PasswordResetRequest`, `PasswordResetConfirmRequest`, `passwordResetRequest(...)`, or `passwordReset(...)`.
- **Recommendation:** Add controller tests asserting: (1) `passwordResetRequest` returns `RegistrationAcknowledgement.forPasswordReset()` with HTTP `200`; (2) `passwordReset` returns `204` on success; (3) both delegate to the correct `AccountService` methods with the right arguments; (4) `passwordReset` propagates `VerificationTokenRejectedException` for the handler to translate.
- **Confidence:** High.

---

### 3. `RefreshTokenTrackerTest` does not cover `revokeAllForPrincipal`

- **Issue:** The tracker test file covers issuance, rotation, and reuse detection but not the new bulk-revocation method added for T07.
- **Evidence:** `RefreshTokenTrackerTest.java:165` ends after the reuse-check idempotency test. There is no test for `revokeAllForPrincipal`.
- **Recommendation:** Add tests verifying it (a) revokes every unrevoked family for the given principal, (b) leaves a different principal's families untouched, (c) is idempotent on a second call, and (d) writes the reason `"PASSWORD_RESET"`. These directly cover the frozen brief's R14 acceptance criteria and the named test `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`.
- **Confidence:** High.

---

### 4. `VerificationTokenServiceTest` does not cover `consumeForPurpose`

- **Issue:** The purpose-aware consumption method is the security fix for Finding 1 of the design review, but it has no dedicated tests.
- **Evidence:** `VerificationTokenServiceTest` tests the purpose-blind `consume` and `verify` methods extensively, but only line 286 touches `PASSWORD_RESET`, and that is for issuance. There is no test that an `EMAIL_VERIFY` token rejected from a `PASSWORD_RESET` redemption is *not* marked used.
- **Recommendation:** Add tests that (a) `consumeForPurpose(rawToken, PASSWORD_RESET)` succeeds for a `PASSWORD_RESET` token and fails with no mutation for an `EMAIL_VERIFY` token, and (b) the mirror case for `EMAIL_VERIFY` redemption rejecting a `PASSWORD_RESET` token. These are required by the frozen brief's "T06 regression closed" acceptance criterion.
- **Confidence:** High.

---

### 5. `PasswordResetConfirmRequest.toString()` guard is implemented but not tested

- **Issue:** The DTO correctly overrides `toString()` to omit `newPassword` (`PasswordResetConfirmRequest.java:21-24`), but there is no regression test ensuring the plaintext new password never leaks if the record is logged.
- **Evidence:** No test file references `PasswordResetConfirmRequest`. The frozen brief's Required Tests section explicitly calls for verifying the new password is never logged/echoed and that `PasswordResetConfirmRequest.toString()` omits it.
- **Recommendation:** Add a small unit test asserting `new PasswordResetConfirmRequest("token", "super-secret").toString()` does not contain `"super-secret"`. This is cheap coverage against accidental removal of the override.
- **Confidence:** High.

---

### 6. `AccountServiceTest.shouldResendVerificationOnlyForPendingAccounts` appears to use identical stub keys

- **Issue:** The test stubs `accountRepository.findByEmail(...)` three times in a row with what appears to be the same email literal (rendered as `[EMAIL_REDACTED]` in all three lines by the tool chain). If the actual literals are identical, Mockito overwrites each stub with the next, so all three lookups return `Optional.empty()` and the test would fail its assertion that exactly one token was issued.
- **Evidence:** `AccountServiceTest.java:268-270` shows three consecutive `when(accountRepository.findByEmail("[EMAIL_REDACTED]")).thenReturn(...)` calls; the test then calls `service.resendVerificationIfPending("[EMAIL_REDACTED]")` three times and expects one issuance. This pattern is structurally fragile regardless of whether the current literals differ.
- **Recommendation:** Inspect the actual source literals. If they are identical, refactor to use three distinct normalized email strings (e.g. `pending@example.com`, `active@example.com`, `unknown@example.com`) and update the corresponding stub/verify calls. Even if they currently differ, rewrite the test to make the three cases explicit and independent so future diffs cannot silently collapse them.
- **Confidence:** Medium (limited by email redaction in the rendered source; the pattern itself is suspicious).

---

### 7. Module-wide compilation failure blocks execution of any T07 tests via Maven

- **Issue:** `mvn -pl services/auth test-compile` currently fails before reaching `AccountServiceTest`, blocked by pre-existing compile errors in `SecurityChainsConfig.java` and `ReuseDetectingAuthorizationService.java`.
- **Evidence:** Build output:
  ```
  ReuseDetectingAuthorizationService.java:[10,48] cannot find symbol: class OAuth2TokenType
  SecurityChainsConfig.java:[12,47] cannot find symbol: class JwtAuthenticationConverter
  ```
  These classes are explicitly listed in the frozen brief as "Files NOT to Modify" and out of T07 scope.
- **Recommendation:** This is not a T07 code defect, but it is a practical blocker: T07's new tests cannot be compiled or run through the normal Maven lifecycle until those unrelated failures are resolved (separate task/PR). Ensure Phase 9 remediation of the T07 findings is validated by at least compiling the affected test files in isolation once the module-level blocker is cleared.
- **Confidence:** High.

---

## Summary

The implementation correctly reflects the frozen brief (purpose-aware consumption, `ACTIVE`/`LOCKED` gating, `200` request response, `204` confirm response, family revocation, toString guard). The dominant risk is **missing test coverage**: almost every new T17 behavior is implemented but unverified, and the existing `AccountServiceTest` is both uncompilable and mis-stubbed. No deviations from `agents.md`, L5, or the frozen brief's LOCKED decisions were found in the production code.
