# auth · T07 — Phase 10: Test Generation

Test-only phase. No production code changed. Consumes `09-review-resolution.md`; closes out
Independent Findings 1–5 from `08-independent-review.md` (missing coverage for
`requestPasswordReset`, `resetPassword`, `consumeForPurpose`, `revokeAllForPrincipal`, the two new
endpoints, and the `PasswordResetConfirmRequest`/`RegistrationAcknowledgement` guards).

---

## Test manifest

### `VerificationTokenServiceTest.java` (extended)

| Test | Verifies |
|---|---|
| `shouldConsumeTokenWhenPurposeMatches` | `consumeForPurpose` succeeds for a `PASSWORD_RESET` token requested with the matching purpose. R15. |
| `shouldRejectTokenWhenPurposeDoesNotMatchAndLeaveItUnconsumed` | Finding 1: an `EMAIL_VERIFY` token requested as `PASSWORD_RESET` returns empty **and** is not marked used (`markConsumed` never called) — then a correctly-purposed consume of the same raw token still succeeds. R15. |
| `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` | Mirror case — the T06 regression closed by T07: a `PASSWORD_RESET` token must never activate an account. |
| `shouldRejectConsumeForPurposeWhenAccountIsUnusable` | Purpose matches, but the account is `SUSPENDED` — pre-mutation rejection, `markConsumed` never called. R15. |

Also added a purpose-parameterized overload of the existing `stubToken(...)` helper (defaults to
`EMAIL_VERIFY` for all pre-existing call sites, unchanged behavior there).

### `AccountServiceTest.java` (extended)

| Test | Verifies |
|---|---|
| `shouldEmitPasswordResetEventOnlyWhenEmailExists` (named test) | R13: `ACTIVE` and `LOCKED` accounts both issue+emit; `PENDING_VERIFICATION`, `DELETED`, `SUSPENDED`, and an unknown email are all a silent no-op (exactly 2 of 6 lookups issue a token). |
| `shouldEmitPasswordResetEventWithCorrectPurposeLabelAndToken` | The emitted `auth.email.requested` payload carries `purpose="password_reset"` and the raw token, matching `EmailRequestedEventPayload`'s R3 contract. |
| `requestPasswordResetNormalizesEmailBeforeLookup` | Same normalization contract as `resendVerificationIfPending`. |
| `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` (named test) | R14: password hash updated, `refreshTokenTracker.revokeAllForPrincipal(accountUuid, "PASSWORD_RESET")` called, audit recorded (`eventType="password.reset"`, `actorUuid=accountUuid`, `accountUuid=accountUuid`). |
| `shouldUnlockAccountOnSuccessfulPasswordReset` | Finding 8: `LOCKED` → `ACTIVE` on a successful reset. |
| `shouldRejectPasswordResetWhenTokenConsumeReturnsEmpty` | Uniform rejection when `consumeForPurpose` returns empty; no account lookup, no revoke, no audit. |
| `shouldRejectPasswordResetWhenAccountDisappearsAfterConsume` | Defensive case mirroring `activateFromVerificationToken`'s equivalent: a missing account never leaks `AccountNotFoundException`. |
| `shouldRejectPasswordResetForIneligibleAccountStatuses` | R15: `PENDING_VERIFICATION`, `DELETED`, `SUSPENDED` all uniformly rejected — and never leak `InvalidAccountStateException` from `Account.changePasswordHash`'s own `DELETED`-only guard, since it is structurally unreachable from this call path. |

### `AccountControllerTest.java` (extended)

| Test | Verifies |
|---|---|
| `passwordResetRequestReturnsForPasswordResetAcknowledgementRegardlessOfMatch` | `200` (bare return), `RegistrationAcknowledgement.forPasswordReset()` body, delegates to `requestPasswordReset`. Finding 5/R12. |
| `passwordResetReturnsNoContentOnSuccess` | `204`, delegates to `resetPassword(token, newPassword)`. R14. |
| `passwordResetPropagatesRejectionForTheExceptionHandlerToTranslate` | No local catch — mirrors `verifyEmail`'s existing test; the 400/`INVALID_TOKEN` translation stays `AccountExceptionHandler`'s job (already covered by `AccountExceptionHandlerTest`, unchanged). R15. |

### `RefreshTokenTrackerTest.java` (extended)

| Test | Verifies |
|---|---|
| `revokeAllForPrincipalRevokesEveryUnrevokedFamilyForThatPrincipal` | All matching families revoked with the given reason and timestamp. R14. |
| `revokeAllForPrincipalDoesNotTouchAnotherPrincipalsFamilies` | Isolation — only the queried principal's families are touched. |
| `revokeAllForPrincipalIsANoOpWhenNoFamiliesExist` | No exception when the principal has no live families. |
| `revokeAllForPrincipalIsIdempotentOnASecondCall` | A second call (simulating the `RevokedAtIsNull` filter finding nothing left) doesn't alter `revokedAt`. |

### `account/dto/PasswordResetDtoTest.java` (new file)

| Test | Verifies |
|---|---|
| `passwordResetRequestRejectsBlankAndMalformedEmail` | `@NotBlank @Email` on `PasswordResetRequest.email`. |
| `passwordResetConfirmRequestRejectsBlankTokenOrPassword` | `@NotBlank` on both `PasswordResetConfirmRequest` fields. |
| `passwordResetConfirmRequestToStringOmitsNewPasswordButKeepsToken` | Independent Finding 5 / Finding 7: the plaintext new password never leaks via a default record `toString()`; `token` is kept visible (existing `VerifyEmailRequest` precedent). |
| `forPasswordResetWordingIsDistinctFromStandardRegistrationMessage` | The new acknowledgement's wording is genuinely different from `standard()`'s (factually-wrong-if-reused concern from Phase 2 design). |

---

## Build/test verification

Same workaround as every prior phase in this task (module-wide `mvn compile`/`test` still blocked
by the pre-existing, unrelated `SecurityChainsConfig`/`ReuseDetectingAuthorizationService` compile
break — see memory `auth-service-token-package-broken`):

- Compiled the six affected/new test files in isolation via `javac -sourcepath` against the
  module's resolved dependency classpath (`mvn dependency:build-classpath`). **Zero errors.**
- Ran all of them via the JUnit Platform Launcher API:
  `VerificationTokenServiceTest`, `AccountServiceTest`, `AccountControllerTest`,
  `AccountExceptionHandlerTest` (untouched, re-run for regression), `PasswordResetDtoTest` (new),
  `RefreshTokenTrackerTest`, `EventTopicsTest` (untouched, re-run for regression).

**Result: 74/74 tests successful, 0 failed, 0 skipped, ~865ms.**

### ArchitectureTest re-verification (frozen brief requirement, Finding 12)

Compiled the entire `services/auth` main tree *except* the three files chained to the pre-existing
break (`SecurityChainsConfig.java`, `ReuseDetectingAuthorizationService.java`,
`AuthorizationServiceConfig.java` — the last one only because it directly instantiates the second)
and ran `ArchitectureTest` against that partial classpath via the same Launcher approach.

**6 of 7 rules passed**, including every rule capable of catching a problem with T07's new
`account → token` dependency (no rule in this file restricts that direction — confirmed at Phase 0
and again here). **1 rule failed**, logged below as an Open Question — it is unrelated to T07.

---

## Open Questions

### Pre-existing `ArchitectureTest` violation, unrelated to T07

`only_the_account_module_may_touch_the_Account_entity` fails: `AccountResponse.from(Account)`
(`account/dto/AccountResponse.java`) calls `Account` getters directly. The rule's `resideOutsideOfPackage("com.themistra.auth.account")`
check is stricter than intended — `AccountResponse` lives in the subpackage
`com.themistra.auth.account.dto`, which the rule treats as "outside" `com.themistra.auth.account`
(no `..` on the rule's package selector). Confirmed via `git log` that both
`AccountResponse.java` (`a2b8193`) and `ArchitectureTest.java` (this rule, `b78c201`) predate every
task in this session's chain (T03 onward) — original scaffolding-era code, never touched by T07 or
any prior task here.

This was never caught before because `mvn test` can't reach `ArchitectureTest` at all under the
module-wide compile break; this is the first time in the pipeline anyone has run it standalone.
Not a T07 defect, not fixed here per this phase's "no production code changes" guardrail and "work
only on T07" scope. Logging it rather than fixing it silently, per the guardrails' instruction for
anything that "looks wrong." Recommend a separate task: either loosen the rule's package selector
to `"com.themistra.auth.account.."` with an explicit carve-out for `account.dto`/`account.event`
(consistent with this same rule's own `because(...)` text, which already says those subpackages are
the sanctioned way to address accounts), or tighten `AccountResponse.from` to stop reaching into
`Account`'s getters directly.

---

## Next

Phase 11 — Kimi independent test review.
