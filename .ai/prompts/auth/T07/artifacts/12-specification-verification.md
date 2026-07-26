# auth · T07 — Phase 12: Specification Verification

Verifying the final implementation (Phase 6/9) and tests (Phase 10/11) against
`spec/auth-service/requirements.md`, `design.md`, and `tasks.md` for T07 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R12** — uniform acknowledgement from `password-reset-request` regardless of match | Yes | `AccountController.java:98-103` (`passwordResetRequest` — bare return, no branching, `200` default); `RegistrationAcknowledgement.java:21-24` (`forPasswordReset()`) | `passwordResetRequestReturnsForPasswordResetAcknowledgementRegardlessOfMatch` (`AccountControllerTest.java`, exercises both a match and a non-match, Phase 11 Gap 1 fix) | No | No |
| **R13** — issue+emit `auth.email.requested`/`password_reset` only for `ACTIVE`/`LOCKED` accounts | Yes | `AccountService.java:149-154` (`requestPasswordReset`); `AccountService.java:186-188` (`isPasswordResetEligible`, `ACTIVE \|\| LOCKED`) | `shouldEmitPasswordResetEventOnlyWhenEmailExists` (named test — `AccountServiceTest.java`, all six status permutations); `shouldEmitPasswordResetEventWithCorrectPurposeLabelAndToken` (payload shape) | No | No |
| **R14** — valid reset updates password hash, revokes all refresh-token families, records `password.reset` audit | Partially (see note below) | `AccountService.java:167-184` (`resetPassword`); `RefreshTokenTracker.java:77-81` (`revokeAllForPrincipal`) | `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` (named test); `shouldUnlockAccountOnSuccessfulPasswordReset`; `revokeAllForPrincipal*` (`RefreshTokenTrackerTest.java`, 4 tests) | **Yes** — "policy-compliant new password" enforcement (`PasswordPolicy`) is not wired into `resetPassword` | **No — documented, human-approved scope split.** Phase 0 (`00-repository-understanding.md`) confirmed `PasswordPolicy` wiring for password-reset belongs to task 9 ("Password policy enforcement... apply to registration, change-password, and password-reset"), a separate, later task in `tasks.md`. T07's own one-line task statement ("update password and revoke all refresh-token families") does not mention policy enforcement. Not a silent gap — flagged and human-confirmed before implementation began. |
| **R15** — uniform rejection for invalid/expired/used/deleted/suspended reset tokens | Yes | `VerificationTokenService.java:114-139` (`consumeForPurpose` — purpose check, then `resolveUsableAccount` excludes `DELETED`/`SUSPENDED` before any mutation); `AccountService.java:167-176` (fresh re-read + `isPasswordResetEligible` pre-check, defensively also excludes `PENDING_VERIFICATION`, unreachable in practice since R13 never issues a token to one); `AccountExceptionHandler.java:39-40` (single `VerificationTokenRejectedException` → `400`/`INVALID_TOKEN` mapping, unchanged from T06) | `shouldRejectTokenWhenPurposeDoesNotMatchAndLeaveItUnconsumed`, `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify`, `shouldRejectConsumeForPurposeWhenAccountIsUnusable`, `shouldRejectConsumeForPurposeWhenAccountBecomesUnusableBetweenTheTwoChecks`, `shouldRejectExpiredPasswordResetTokenViaConsumeForPurpose`, `shouldRejectAlreadyUsedPasswordResetTokenViaConsumeForPurpose` (`VerificationTokenServiceTest.java`); `shouldRejectPasswordResetForIneligibleAccountStatuses`, `shouldRejectPasswordResetWhenTokenConsumeReturnsEmpty`, `shouldRejectPasswordResetWhenAccountDisappearsAfterConsume` (`AccountServiceTest.java`); `passwordResetPropagatesRejectionForTheExceptionHandlerToTranslate` (`AccountControllerTest.java`) | No | No |
| **L5** (enumeration-safe responses, password-reset request/confirm) | Yes | Same evidence as R12/R15 above — request path never branches; confirm path's only distinguishing outcome is success (`204`) vs. failure (`400`), and every failure *reason* is uniform (single exception type, single problem-detail mapping) | Covered by the R12/R15 test list above | No | No |
| **L11** (widened — new public paths registered in `PublicEndpoints`) | Yes | `PublicEndpoints.java:32-33` (both new entries added) | `methodScopedContainsBothPasswordResetEndpoints` (`PublicEndpointsTest.java`, new file, Phase 11 Gap 8 fix) | No | No |
| **L12** (module boundaries — no cross-module entity imports) | Yes | `AccountService.java` imports `com.themistra.auth.token.RefreshTokenTracker` (a `@Component` service, not an entity); `RefreshTokenFamily`/`RefreshTokenFamilyRepository` remain package-private, never imported outside `token` | `ArchitectureTest` re-run in isolation (Phase 10) — 6/7 rules pass, including every rule capable of catching an `account → token` entity leak; the one failing rule is pre-existing and unrelated (see Remaining risks) | No | No |
| **R43** (audit on security-relevant actions) | Yes | `AccountService.java:183` (`recordAudit("password.reset", accountUuid, accountUuid)`, self-service actor pattern, same as T06's `account.activated`) | Asserted inside `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` | No | No |
| **R44** (`auth.email.requested` routing via `EventTopics`) | Yes (unchanged, reused) | `EventTopics.java`'s existing `"verification-token" -> "auth.email.requested"` mapping (added in T06); T07 reuses the same aggregate type for `password_reset`-purpose events | `EventTopicsTest` (untouched, re-run for regression, still green); `shouldEmitPasswordResetEventWithCorrectPurposeLabelAndToken` confirms the aggregate type used at the call site | No | No |
| **Named test** `shouldEmitPasswordResetEventOnlyWhenEmailExists` | Yes | — | `AccountServiceTest.java` | No | No |
| **Named test** `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` | Yes | — | `AccountServiceTest.java` | No | No |
| Contracts (`auth.yaml`, `token-claims.md`, `email-requested.v1.schema.json`, `security-audit.v1.schema.json`) | N/A to T07 | None of these files exist yet anywhere in the repo (`contracts/api/` is empty, only `.gitkeep`) | N/A | Pre-existing, not T07-specific | No — `tasks.md` items 33/34 ("Contract files... Author contracts/api/auth.yaml...") are later, dedicated tasks; T07 predates them by design |

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes, against T07's own literal statement: both endpoints exist,
responses are uniform (R12/L5), and a valid reset updates the password, revokes every refresh-token
family, and audits the action (R14's non-policy clauses). The one open thread — policy enforcement
on the new password — is explicitly task 9's job per `tasks.md`'s own two-step design and a
Phase 0 human-confirmed decision, not an oversight.

**(2) Does it satisfy every acceptance criterion?** R12, R13, R15, L5, and L11 fully. R14 partially —
password update, family revocation, and audit are all correct and tested; the "policy-compliant new
password" clause is deliberately deferred to task 9.

**(3) Does it violate any LOCKED decision?** No. L1 (no new migration needed, none added), L5, L11
(widened per T06 precedent), and L12 are all upheld. The new `account → token` dependency is a
service-to-service call, not an entity import, and `ArchitectureTest`'s module-boundary rules confirm
this.

**(4) Remaining risks:**
- Module-wide `mvn -pl services/auth verify` still cannot run end-to-end — pre-existing, unrelated
  compile break in `SecurityChainsConfig`/`ReuseDetectingAuthorizationService`/`AuthorizationServiceConfig`
  (tracked since T03). Every test in this task was verified via isolated `javac` + JUnit Platform
  Launcher instead; this is a standing risk for the whole module, not introduced by T07.
- A genuinely pre-existing `ArchitectureTest` violation (`AccountResponse.from(Account)` vs.
  `only_the_account_module_may_touch_the_Account_entity`'s package-selector precision) was discovered
  while re-verifying T07's own module-boundary safety at Phase 10. Confirmed via `git log` to predate
  T03. Not caused by, or in scope for, T07 — logged there as an Open Question, not fixed.
- R14's password-policy clause remains open until task 9 lands; until then, `resetPassword` accepts
  any non-blank string as the new password (enforced only by `PasswordResetConfirmRequest`'s
  `@NotBlank`).

---

## Verdict

**PASS** — every requirement, LOCKED decision, and named test in T07's scope is implemented and
tested; the sole partial item (R14's policy-enforcement clause) is a pre-agreed, documented deferral
to task 9, not a defect in this task.
