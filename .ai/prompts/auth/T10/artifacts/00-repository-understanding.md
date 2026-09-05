# auth · T10 — Phase 0: Repository Understanding

## 1. Architecture summary

`services/auth` is a Spring Boot 3.5.4 / Java 21 module, package-by-feature under
`com.themistra.auth.{account,authn,authz,audit,events,token,common}`. Errors are RFC 9457
`application/problem+json` via `AccountExceptionHandler` (`@RestControllerAdvice`) + `ProblemTypes`
constants. `AccountController` is `AccountService`'s only HTTP-facing caller;
`AccountControllerTest` constructs the controller directly with a mocked `AccountService` (no
Spring dispatcher), so `@RestControllerAdvice` translation isn't observable at that layer —
`AccountExceptionHandlerTest` is the only place the actual `ProblemDetail`/status/type mapping is
verified directly (confirmed via that test class's own Javadoc).

## 2. Existing code this task touches

T10's task statement — "Add tests that duplicate registration, invalid verification tokens, and
invalid reset tokens produce identical responses" — touches no new production code by its own
wording (test-only task, like a lighter version of T09's testing half). The relevant existing
production code (all unchanged, all pre-existing from T02/T05/T06/T07):

- **Duplicate registration:** `AccountController.register` catches `DuplicateEmailException`
  locally and always returns the same `202`/`RegistrationAcknowledgement.standard()` regardless of
  whether `AccountService.register` threw it — this is the *only* exception this controller method
  catches locally; every other exception (including T09's `PasswordPolicyViolationException`)
  propagates uncaught.
- **Invalid verification token:** `AccountService.activateFromVerificationToken` throws the single
  `AccountService.VerificationTokenRejectedException` for every rejection reason (token not
  found/expired/used/wrong-purpose, resolved account missing, or wrong account status) — never a
  distinguishing exception type. `AccountExceptionHandler.onVerificationTokenRejected` maps it to a
  fixed `400`/`INVALID_TOKEN` body with title "Verification token is invalid or expired" and
  `detail: null` (no variable content).
- **Invalid reset token:** `AccountService.resetPassword` throws the exact same
  `AccountService.VerificationTokenRejectedException` class (not a separate reset-specific type)
  for every one of its own rejection reasons (token not found/expired/used/wrong-purpose, resolved
  account missing, or ineligible status). It is handled by the identical
  `onVerificationTokenRejected` mapping — meaning a password-reset rejection currently produces the
  same literal title text ("Verification token is invalid or expired"), not reset-specific wording.
  This is existing T07 behavior, not something T10 is scoped to change.

## 3. Established patterns to follow

- **One exception type per uniform-response guarantee.** `VerificationTokenRejectedException` is
  deliberately reused across both verify-email and password-reset rejection paths rather than
  having two near-identical exception classes — R5 and R15 are both satisfied by the same handler
  mapping. `DuplicateEmailException` for registration is a separate class, caught earlier
  (controller-level, not handler-level) specifically because a `202` success-shaped response can't
  be produced by throwing through to a `@RestControllerAdvice` that only maps to error statuses.
- **Handler-level "identical regardless of construction site" tests already exist as a pattern.**
  `AccountExceptionHandlerTest.onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite`
  (pre-existing) constructs the same exception twice, standing in for two different internal
  `AccountService` call sites ("token not found" vs. "wrong account status"), and asserts
  byte-for-byte identical `ProblemDetail` fields. T08 added the equivalent for
  `CurrentPasswordMismatchException`
  (`onCurrentPasswordMismatchResponseIsIdenticalRegardlessOfConstructionSite`). This is the
  established idiom for proving uniformity in this module — not integration tests, not `MockMvc`.
- **Service-level tests already assert the *type* of exception thrown for each rejection reason**,
  not the HTTP shape (that's the handler test's job). E.g.
  `shouldRejectVerificationWhenTokenConsumeReturnsEmpty`,
  `shouldRejectVerificationWhenAccountIsNotPendingVerification`,
  `shouldRejectVerificationWhenAccountDisappearsAfterConsume`,
  `shouldRejectPasswordResetWhenTokenConsumeReturnsEmpty`,
  `shouldRejectPasswordResetWhenAccountDisappearsAfterConsume`,
  `shouldRejectPasswordResetForIneligibleAccountStatuses` (all pre-existing, `AccountServiceTest`)
  each assert `.isInstanceOf(VerificationTokenRejectedException.class)` (several also assert
  `.isNotInstanceOf(...)` a distinguishing type like `AccountNotFoundException` or
  `InvalidAccountStateException`).
- **Controller-level duplicate-registration uniformity already has a dedicated test:**
  `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` (pre-existing,
  `AccountControllerTest`) asserts the duplicate-email response has identical status and body to
  the success case.

## 4. Testing conventions

Plain JUnit 5 + Mockito + AssertJ, no Spring context, fixed `Clock.fixed(...)`. No `MockMvc` /
`@WebMvcTest` anywhere in the module (reconfirmed at T09 Phase 0/11, still true). ArchUnit
(`ArchitectureTest`) enforces module boundaries; the pre-existing `AccountResponse.from(Account)`
violation logged at T07 Phase 10 remains unfixed and out of scope. `mvn -pl services/auth test`
still cannot run to completion due to the pre-existing `token` package compile break (tracked since
T03) — verification will need the same `javac` + JUnit Platform Launcher workaround used every
phase since.

## 5. Known gaps / unknowns

- **Correction (found while re-checking before Phase 1): `VerificationTokenServiceTest.java`
  (T05/T07-era) has much deeper existing coverage than the account/controller-layer files alone
  suggest.** `shouldNotRevealAccountExistenceForInvalidVerificationToken` (line 93) — the *exact*
  literal named test from `package.md` §8 — already exists, and is extremely thorough: one test
  method exercises not-found, expired, already-used, deleted-account, and suspended-account, each
  via both `verify()` and `consume()`, all asserting `.isEmpty()`/uniform rejection. However, it
  tests the **purpose-blind** `verify`/`consume` API, not `consumeForPurpose` — the method T07
  actually changed `AccountService.activateFromVerificationToken` to call. So the named test exists,
  but not against the exact method the current production call path uses.
- On the `PASSWORD_RESET` side, `consumeForPurpose` (the method `AccountService.resetPassword`
  actually calls) already has thorough but *separately-named* coverage: wrong-purpose,
  account-becomes-unusable-mid-flight (race), expired, already-used, and unusable-account-upfront
  each have their own dedicated test (lines 335-421) — no single consolidated "uniformity" named
  test the way the `EMAIL_VERIFY` side has one, but the underlying `Optional.empty()` contract is
  proven uniform across every reason individually.
- **Net picture:** the asymmetry is real — `EMAIL_VERIFY` has one thorough named test on a
  superseded method; `PASSWORD_RESET` has thorough coverage on the current method but no single
  named test. What's still genuinely absent anywhere: a test on `consumeForPurpose` itself (not
  `verify`/`consume`) proving the `EMAIL_VERIFY` side's different rejection reasons are uniform
  the same way the old `verify`/`consume`-based test does, and any test that directly compares an
  invalid-verification-token response to an invalid-reset-token response side-by-side (both funnel
  through the same exception/handler, but nothing asserts that explicitly). Phase 1 must account
  for this corrected picture, not the earlier (less accurate) assessment.
- **`package.md` §8 named-test list has no entry at all for invalid-reset-token uniformity** — only
  `shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` (R1/R2) and
  `shouldNotRevealAccountExistenceForInvalidVerificationToken` (R5) appear; nothing analogous is
  listed for R15 (invalid reset token). This may mean R15's uniformity was intended to be covered
  by the *same* named test as R5 (since both share one exception class and one handler test), or it
  may be a genuine gap in `package.md` itself — same category of documentation drift already logged
  at T09 Phase 1 (package.md's requirement-ID numbering not matching current `requirements.md`).
  Flagged for Phase 1, not resolved here.
- **Neither of this task's two cited named tests
  (`shouldReturnSameAcknowledgementForDuplicateAndNewRegistration`,
  `shouldNotRevealAccountExistenceForInvalidVerificationToken`) currently exists under those exact
  method names** in `AccountServiceTest.java` or `AccountControllerTest.java` — the closest
  equivalents (`registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`,
  `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite`) cover the same
  ground under different names. Whether T10 should rename/consolidate onto the literal named-test
  names, or whether the existing tests already satisfy the named-test requirement in substance, is
  a Phase 1 question.
