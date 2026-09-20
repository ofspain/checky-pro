# auth · T08 — Phase 10: Test Generation

Test-only phase. No production code changed. Consumes `09-review-resolution.md`; implements the
frozen brief's full Required Tests list plus Kimi's Finding 4 closure (`shouldRejectNullAccountOrActorUuid`, added at Phase 9).

---

## Test manifest

### `AccountServiceTest.java` (extended)

| Test | Verifies |
|---|---|
| `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword` | AC1/AC4/AC5/AC6: hash updated, `passwordPolicy.validate` called before `encode`, audit recorded with `actorUuid = accountUuid` = the caller's own UUID. |
| `shouldRejectChangePasswordWhenCurrentPasswordDoesNotMatch` | AC2: `CurrentPasswordMismatchException`; `passwordPolicy.validate`/`encode` never called; hash unchanged; no audit. |
| `shouldRejectChangePasswordWhenNewPasswordViolatesPolicy` | AC3: a policy-violating new password propagates `PasswordPolicyViolationException` (only reached after the current-password check passes); no mutation, no audit. |
| `shouldRejectChangePasswordForEveryNonActiveAccountStatus` | AC7: all four non-`ACTIVE` statuses uniformly rejected via `InvalidAccountStateException`; `passwordEncoder.matches` never called — proves the NPE-avoidance ordering (Finding 4) is real, not incidental. |
| `shouldNotRevokeRefreshTokenFamiliesOnSuccessfulPasswordChange` | AC8: `refreshTokenTracker.revokeAllForPrincipal` never called on success — the documented trade-off (decision 3) as an enforced regression guard. |
| `shouldAllowNewPasswordIdenticalToCurrentPassword` | AC9: resubmitting the current password as the new one succeeds like any other valid new password. |

### `AccountControllerTest.java` (extended)

| Test | Verifies |
|---|---|
| `changePasswordReturnsNoContentOnSuccess` | `204`, delegates to `accountService.changePassword(accountUuid, currentPassword, newPassword)` with the UUID derived from `Authentication`, not the request body. |
| `changePasswordPropagatesCurrentPasswordMismatchForTheExceptionHandlerToTranslate` | No local catch — mirrors `verifyEmail`/`passwordReset`'s existing pattern; the 400/`CURRENT_PASSWORD_MISMATCH` translation stays `AccountExceptionHandler`'s job. |
| `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate` | Same, for `PasswordPolicyViolationException`. |

### `AccountExceptionHandlerTest.java` (extended)

| Test | Verifies |
|---|---|
| `onCurrentPasswordMismatchReturns400WithCurrentPasswordMismatchType` | Finding 2/3: `400`, `ProblemTypes.CURRENT_PASSWORD_MISMATCH`, fixed title, no variable detail. |
| `onPasswordPolicyViolationReturns400WithValidationErrorTypeAndDetail` | `400`, `ProblemTypes.VALIDATION_ERROR`, `detail` = the policy violation message (matches `InvalidAccountStateException`'s existing precedent of exposing `e.getMessage()`). |

### `PasswordPolicyTest.java` (signature-migration + Finding 4 closure)

| Test | Verifies |
|---|---|
| `shouldRejectPasswordShorterThan12OrLongerThan128` (named test, unchanged assertion) | Now exercised via a real 3-arg call — the same boundary logic T08's own scope depends on, per the Phase 4 resolution of the original blocker. |
| All 10 other pre-existing tests | Updated to the new signature; behavior unchanged. |
| `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (assertion flipped) | AC10: the recorded audit event's `accountUuid`/`actorUuid` now equal the values passed into `validate`, replacing the old `isNull()` assertions that predate T08's audit-context fix. |
| `shouldRejectNullAccountOrActorUuid` (new) | Kimi Finding 4: `validate` throws `NullPointerException` for either UUID argument being `null`. |

---

## Build/test verification

Same workaround as every prior phase (module-wide `mvn compile`/`test` still blocked by the
pre-existing, unrelated `SecurityChainsConfig`/`ReuseDetectingAuthorizationService`/
`AuthorizationServiceConfig` compile break — see memory `auth-service-token-package-broken`):

- Compiled the four affected test files in isolation via `javac -sourcepath` against the module's
  resolved dependency classpath. **Zero errors.**
- Ran all of them via the JUnit Platform Launcher API: `AccountServiceTest`, `PasswordPolicyTest`,
  `AccountControllerTest`, `AccountExceptionHandlerTest`.

**Result: 55/55 tests successful, 0 failed, 0 skipped, ~720ms.**

No `ArchitectureTest` re-verification needed this task — T08 introduces no new cross-module
dependency (unlike T07's `account → token` addition), so the module-boundary rules are unaffected.

---

## Next

Phase 11 — Kimi independent test review.
