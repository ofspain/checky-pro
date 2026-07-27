# auth · T09 — Phase 10: Test Generation

Test manifest for the frozen brief (`04-frozen-task-brief.md`) and resolved implementation
(`09-review-resolution.md`). No production code changed in this phase.

All plain JUnit 5 + Mockito + AssertJ, no Spring context, fixed `Clock` — matching every existing
test in this module; no `MockMvc`/`@WebMvcTest` introduced.

## Files changed

- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — 1 test
  renamed/updated, 6 tests added (36 total, was 29).
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` — 2 tests
  added (14 total, was 12).
- `services/auth/src/test/java/com/themistra/auth/account/dto/RegisterAccountRequestValidationTest.java`
  — 1 test replaced (5 total, unchanged count).

## Test manifest

| Test | File | Verifies | Maps to |
|---|---|---|---|
| `shouldRejectPasswordShorterThan12OrLongerThan128` | `PasswordPolicyTest.java` (pre-existing, unchanged) | Named test — length boundary | R8 |
| `shouldRejectBreachedPasswordUsingHibpRange` | `PasswordPolicyTest.java` (pre-existing, unchanged) | Named test — HIBP breach rejection | R9 |
| `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` | `PasswordPolicyTest.java` (pre-existing, unchanged) | Fail-open + audit on breach-API failure | R10 |
| `registerCallsPasswordPolicyValidateWithTheConstructedAccountUuidAndSubmittedPassword` | `AccountServiceTest.java` (new) | `register` calls `validate` with the real, pre-persist account UUID as both accountUuid/actorUuid | AC1, AC2 |
| `registerRejectsPolicyViolatingPasswordWithoutTouchingRepositoryOrOutbox` | `AccountServiceTest.java` (new) | A policy violation in `register` prevents `existsByEmail`, `saveAndFlush`, token issuance, outbox publish | AC1, AC2, AC9 |
| `registerRejectsPolicyViolatingPasswordEvenWhenEmailIsAlreadyRegistered` | `AccountServiceTest.java` (new) | `register`'s policy check fires identically regardless of email existence — `existsByEmail` never reached, exception is `PasswordPolicyViolationException` not `DuplicateEmailException` | AC4 (L5 enumeration safety) |
| `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount` (renamed from `registerRejectsKnownDuplicateWithoutTouchingEncoder`) | `AccountServiceTest.java` (updated) | Regression guard: encoder + `validate` now run for a duplicate email too; `saveAndFlush`/token/outbox still skipped | Frozen brief Finding 1's trade-off |
| `registerMapsConstraintRaceToDuplicateEmail` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: concurrent-insert race still maps to `DuplicateEmailException` | AC10-equivalent regression guard |
| `registerHashesPasswordNormalizesEmailAndReturnsView` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: happy-path behavior unaffected by reorder | Regression guard |
| `shouldEmitVerifyEmailEventOnRegistration` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: R3 event emission unaffected | Regression guard |
| `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation` | `AccountServiceTest.java` (new) | `resetPassword`'s `InOrder` proof: `validate` → `unlock` (spied `Account`) → `encode` → `revokeAllForPrincipal` → `audit` | AC8 |
| `resetPasswordRejectsPolicyViolatingPasswordWithoutMutatingAccountOrRevokingSessions` | `AccountServiceTest.java` (new) | A policy violation in `resetPassword` prevents `unlock`, hash update, revocation, audit — account left exactly as found | AC5, AC6, AC9 |
| `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: happy-path reset unaffected (validate is a no-op mock by default) | Regression guard |
| `shouldUnlockAccountOnSuccessfulPasswordReset` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: LOCKED→ACTIVE unlock still happens on success | Regression guard |
| `shouldRejectPasswordResetWhenTokenConsumeReturnsEmpty` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: invalid-token rejection precedes any policy check (token never resolves an account) | Regression guard |
| `shouldRejectPasswordResetForIneligibleAccountStatuses` | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: eligibility gate still precedes the new policy check — ineligible accounts never reach `validate` | Regression guard |
| `registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate` | `AccountControllerTest.java` (new) | Controller's `catch (DuplicateEmailException)` block does not also swallow `PasswordPolicyViolationException` for `register` | AC9 |
| `passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate` | `AccountControllerTest.java` (new) | Same propagation proof for `passwordReset` | AC9 |
| `passwordLengthIsNoLongerBeanValidated` (replaces `passwordBoundaries`) | `RegisterAccountRequestValidationTest.java` (updated) | Documents that 11/129-char passwords now pass bean validation — length enforcement moved to `PasswordPolicy` | Frozen brief Finding 5/6's resolution |
| `validRequestPasses`, `noCompositionRules_longSimplePassphraseIsAllowed`, `blankAndMalformedEmailsRejected`, `blankPasswordRejected` | `RegisterAccountRequestValidationTest.java` (pre-existing, unmodified) | Regression: unaffected by `@Size` removal | Regression guard |
| `shouldChangePassword*` / `shouldRejectChangePassword*` / `shouldAllowNewPasswordIdenticalToCurrentPassword` / `shouldNotRevokeRefreshTokenFamiliesOnSuccessfulPasswordChange` (7 tests, T08) | `AccountServiceTest.java` (pre-existing, unmodified) | Regression: `changePassword` untouched by this task | AC10 |
| `changePassword*` (4 tests, T08) | `AccountControllerTest.java` (pre-existing, unmodified) | Regression: `changePassword` controller behavior untouched | AC10 |

## Acceptance criteria coverage

| ID | Covered by |
|---|---|
| AC1 | `registerCallsPasswordPolicyValidateWithTheConstructedAccountUuidAndSubmittedPassword`, `shouldRejectPasswordShorterThan12OrLongerThan128` |
| AC2 | `registerCallsPasswordPolicyValidateWithTheConstructedAccountUuidAndSubmittedPassword`, `shouldRejectBreachedPasswordUsingHibpRange` |
| AC3 | `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (internal to `PasswordPolicy`, exercised regardless of caller) |
| AC4 | `registerRejectsPolicyViolatingPasswordEvenWhenEmailIsAlreadyRegistered` |
| AC5 | `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation`, `shouldRejectPasswordShorterThan12OrLongerThan128` |
| AC6 | `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation`, `shouldRejectBreachedPasswordUsingHibpRange` |
| AC7 | `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` |
| AC8 | `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation` (`InOrder` proof) |
| AC9 | `registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate`, `passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate`, `registerRejectsPolicyViolatingPasswordWithoutTouchingRepositoryOrOutbox`, `resetPasswordRejectsPolicyViolatingPasswordWithoutMutatingAccountOrRevokingSessions` |
| AC10 | All pre-existing `changePassword*` tests, unmodified, still passing |

## Test execution

Ran via the established `javac` + JUnit Platform Launcher workaround (module-wide `mvn test`
still blocked by the pre-existing, unrelated `token` package break):

```
javac -cp "$(cat /tmp/auth-cp.txt)" -sourcepath services/auth/src/main/java:services/auth/src/test/java \
  AccountServiceTest.java AccountControllerTest.java RegisterAccountRequestValidationTest.java PasswordPolicyTest.java
java ... RunTests   # JUnit Platform Launcher, selectClass for all four test classes
```

**Result: 63/63 tests passing** — 36 in `AccountServiceTest`, 14 in `AccountControllerTest`, 5 in
`RegisterAccountRequestValidationTest`, 8 in `PasswordPolicyTest` (unchanged). One expected `WARN`
log line appears in output from `shouldNotPropagateWhenAuditServiceThrowsDuringFailOpen`
(pre-existing T03/T08 test deliberately exercising the swallowed-audit-failure path) — not a
failure.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 9.
- Requirements: R8, R9, R10 — all covered, no gaps.
- LOCKED decisions: L2 (unchanged, exercised via existing `PasswordPolicyTest`), L5 (register's
  enumeration-safety ordering now has a dedicated regression test).
- Named tests: both present and passing, unchanged, in `PasswordPolicyTest.java`.
