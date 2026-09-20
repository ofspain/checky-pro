# auth · T07 — Phase 9: Review Resolution

**Human Approval gate.** Self-review (`07-self-review.md`) found 2 findings; Kimi's independent
review (`08-independent-review.md`) validated both and added 7 more.

---

## Accepted and fixed

### Self-review 1 / Kimi validated — `AccountServiceTest.java` no longer compiles

**Reason accepted:** confirmed by direct compilation — a hard, existing-test build break.

**Change made:** added `@Mock private RefreshTokenTracker refreshTokenTracker;` and included it in
`setUp()`'s constructor call.

### Self-review 2 / Kimi validated — four tests stub the wrong method after the `activateFromVerificationToken` fix

**Reason accepted:** confirmed — `activateFromVerificationToken` now calls
`consumeForPurpose(rawToken, EMAIL_VERIFY)`; four existing tests still stubbed the old `consume`.

**Change made:** all four stubs (`shouldActivateAccountWithValidVerificationToken`,
`shouldRejectVerificationWhenTokenConsumeReturnsEmpty`,
`shouldRejectVerificationWhenAccountIsNotPendingVerification`,
`shouldRejectVerificationWhenAccountDisappearsAfterConsume`) updated from
`verificationTokenService.consume(...)` to
`verificationTokenService.consumeForPurpose(..., VerificationToken.Purpose.EMAIL_VERIFY)`.

---

## Rejected

### Independent Finding 6 — `shouldResendVerificationOnlyForPendingAccounts` "identical stub keys"

**Reason rejected:** checked directly against the actual source (Kimi itself flagged only
**Medium** confidence, noting their tool's output redacted the email literals). The test already
uses three distinct emails (`pending@example.com`, `active@example.com`, `unknown@example.com`) —
not a repeated stub. This is the same T06-era test already verified correct during T06's own
Phase 11, untouched by T07. No change made.

---

## Deferred to Phase 10 (not fixed here, by design)

### Independent Findings 1-5 — missing test coverage for all new T07 behavior

**Reason deferred:** by design, this pipeline splits implementation/fix work from full test
generation (Phase 10) — same precedent as every prior task in this chain. Specifically:
`requestPasswordReset`/`resetPassword` (`AccountServiceTest`), the two new endpoints
(`AccountControllerTest`), `revokeAllForPrincipal` (`RefreshTokenTrackerTest`),
`consumeForPurpose` (`VerificationTokenServiceTest`), and the `PasswordResetConfirmRequest`
`toString()` guard — all real, all correctly identified, all Phase 10's job.

### Independent Finding 7 — module-wide Maven compile failure blocks test execution

**Reason deferred/acknowledged, not a T07 defect:** this is the same pre-existing,
already-tracked issue noted since T03 (`SecurityChainsConfig`/`ReuseDetectingAuthorizationService`
— see memory `auth-service-token-package-broken`). Not caused by T07, not touched by T07. Verified
per established practice: isolated `javac` compilation of the affected files, bypassing the broken
ones entirely.

---

## Verification

All six affected/related test files (`AccountServiceTest`, `AccountControllerTest`,
`AccountExceptionHandlerTest`, `EventTopicsTest`, `VerificationTokenServiceTest`,
`RefreshTokenTrackerTest`) compiled and ran via the established `javac` + JUnit Platform `Launcher`
method.

**Result: 51/51 tests successful, 0 failed, 0 skipped, ~750ms.**
