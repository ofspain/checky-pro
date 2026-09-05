# auth · T18 · Phase 11 — Test Review

Consumed `artifacts/10-test-generation.md` and the three test files it describes, plus the Phase 9 production changes they exercise.

No duplicate tests were found, and none of the test designs look flaky on their own except for the zero-padding search noted below. The integration suite's inability to run to green is the pre-existing Testcontainers/environment issue inherited from T17, not a test-design problem.

The following gaps remain against the acceptance criteria in `artifacts/04-frozen-task-brief.md` and the named tests in `spec/auth-service/package.md` §8.

---

1. **Gap · No running test proves failure audits survive the caller's rollback.**
   Phase 9 changed `AuditService.record` to `REQUIRES_NEW` precisely so `mfa.failed`/`mfa.disable_failed` would not be rolled back by the subsequent exception. `MfaServiceTest` only mocks `auditService.record`; `MfaServicePersistenceIntegrationTest` does not exercise failure paths and cannot run in this environment. A regression that reverted `AuditService` to `REQUIRED` would still pass the current test suite.
   **Suggested test:** Add a `@SpringBootTest`/Testcontainers test (or an `@DataJpaTest` with explicit transaction assertions) for `confirm`, `disable`, and `verifyRecoveryCode` failure paths that asserts an `auth_audit` row matching the failure event exists after the thrown exception.

2. **Gap · The atomic `confirmIfUnconfirmed` guard is mocked, so the concurrent-double-confirm race is not exercised.**
   `MfaServiceTest.confirmGeneratesTenSingleUseRecoveryCodesOnSuccess` stubs `confirmIfUnconfirmed` to return 1, so it cannot prove that two concurrent `confirm` calls would result in exactly 10 recovery-code rows. R23 requires exactly 10 single-use recovery codes.
   **Suggested test:** Add a concurrency test (multi-threaded against an embedded or Testcontainers database) that submits two concurrent `confirm` calls with the same valid code and asserts only one call succeeds and exactly 10 recovery-code rows exist for the account.

3. **Gap · Recovery-code format and entropy are not asserted.**
   `confirmGeneratesTenSingleUseRecoveryCodesOnSuccess` checks count, uniqueness, and that the stored hash matches each raw code, but does not verify the codes are 32 random bytes encoded as URL-safe Base64. A regression that shortened entropy or changed encoding would still pass.
   **Suggested test:** Assert each raw code is 43 characters long, contains only URL-safe Base64 characters, and decodes to 32 bytes.

4. **Gap · Caller-supplied `Clock` timestamps are not asserted for enroll/confirm/recovery codes.**
   The frozen brief requires every timestamp to come from the injected `Clock`. `MfaServiceTest` uses a fixed clock but never asserts `createdAt`, `confirmedAt`, or recovery-code `createdAt` equal `NOW`.
   **Suggested test:** In `beginEnrollGeneratesEncryptsAndPersistsWhenNoEnrollmentExists`, assert the saved enrollment's `createdAt` equals `NOW`; in `confirmGeneratesTenSingleUseRecoveryCodesOnSuccess`, assert `confirmIfUnconfirmed` was called with `NOW` and each saved `RecoveryCode.createdAt` equals `NOW`.

5. **Gap · `verifyRecoveryCode` already-used path does not assert the failure audit.**
   R29 requires an `mfa.failed` audit for any recovery-code verification failure, including an already-used code. `verifyRecoveryCodeThrowsWhenAlreadyUsed` only asserts the exception type; it does not verify `auditService.record` is invoked.
   **Suggested test:** Add assertions in the already-used test that `auditService.record` is called once with `eventType="mfa.failed"` and `outcome=FAILURE`.

6. **Gap · The named end-to-end tests are not actually executed because the integration suite is blocked.**
   `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` and `shouldRequirePasswordAndTotpToDisableMfa` are mapped to `MfaServicePersistenceIntegrationTest`, which cannot run due to the pre-existing Hibernate `existsByEmail` defect. The unit tests substitute mocks for repositories and `TotpVerifier`, so the named tests are not run end-to-end.
   **Suggested test:** Once the environment defect is fixed, execute `MfaServicePersistenceIntegrationTest` and treat the two named tests as verified only then. Until then, document them as pending execution.

7. **Gap · `TotpVerifierTest.verifyHandlesCodesRequiringLeadingZeroPadding` could fail if no zero-padded code appears in its search window.**
   The test searches only the first 5,000 steps (~42 hours) for a code beginning with `0`. There is no guarantee the RFC test secret produces a zero-padded code that early; if it does not, the test throws `AssertionError` rather than failing for a product reason.
   **Suggested test:** Replace the search with a deterministic secret/time pair known to produce a zero-padded code, or assert against a small precomputed table of such steps.

8. **Gap · No repository-level test directly exercises `confirmIfUnconfirmed` and `deleteByIdIfUnconfirmed`.**
   These two atomic methods were added in Phase 9 and are central to closing the concurrent-confirm and begin-enroll races. Their `0`-vs-`1` row semantics are assumed by the service tests but not directly verified.
   **Suggested test:** Add tests in `MfaPersistenceIntegrationTest` (or a new `MfaEnrollmentRepositoryTest`) that call `confirmIfUnconfirmed` on an unconfirmed row, then again on the now-confirmed row, expecting `1` then `0`; and similarly for `deleteByIdIfUnconfirmed`.
