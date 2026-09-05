# auth · T10 — Phase 10: Test Generation

Test manifest for the frozen brief (`04-frozen-task-brief.md`) and resolved implementation
(`09-review-resolution.md`). T10 is a test-only task — per Phase 6's own guardrail exception ("Do
NOT write tests here (that is Phase 10) unless the task itself is test-only"), all four new tests
were already written across Phase 6 (2 tests) and Phase 9's accepted-findings fixes (2 more tests).
This phase's job is the manifest, not new test-writing — no code changes made here.

All plain JUnit 5 + Mockito + AssertJ, no Spring context, fixed `Clock` — matching every existing
test in this module; no `MockMvc`/`@WebMvcTest` introduced (Phase 9 Finding 4, rejected).

## Files changed (across Phase 6 and Phase 9)

- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java` — 1
  test added (Phase 6).
- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java` — 1
  test added (Phase 6), same test strengthened (Phase 9 Findings 5/6).
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — 2 tests added
  (Phase 9 Findings 1/2, plus imports for `ProblemDetail`/`catchThrowableOfType`); the Phase 9
  Finding 1 test strengthened post-manifest (Phase 11 Gap 3) with absolute-value (`status`/`type`/
  `title`/`detail`) and leak-prevention (`instance`/`properties`) assertions on both `ProblemDetail`s,
  not just relative equality — imports added for `ProblemTypes`/`HttpStatus`.

**Phase 11 (Kimi test review) disposition, applied before this manifest's final state:** Gap 3
accepted and applied (above). Gaps 1, 2, 5, and 6's code suggestion rejected — Gap 1 re-litigates
the frozen Phase 4 "no rename" decision on R2 naming for the third time; Gaps 2/5 would add
combinatorial coverage that cannot detect anything not already proven, since every rejection
reason on both surfaces throws the identical no-arg `VerificationTokenRejectedException` with no
distinguishing state; Gap 6 (MockMvc/end-to-end) has zero precedent in this module and contradicts
the frozen brief's explicit no-Spring-context constraint. Gap 6's residual-risk framing and Gap 4's
already-documented named-test mapping are both carried into Phase 12's traceability matrix
explicitly, per Kimi's own fallback recommendations.

## Test manifest

| Test | File | Verifies | Maps to |
|---|---|---|---|
| `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose` | `VerificationTokenServiceTest.java` (new, Phase 6) | `consumeForPurpose(..., EMAIL_VERIFY)` boundary set: not-found, expired, already-used, deleted-account, suspended-account — all uniform `Optional.empty()` | AC2a (token-level reasons), AC2b (account-level reasons), R5 |
| `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` | `VerificationTokenServiceTest.java` (pre-existing, T07, cited not duplicated) | Wrong-purpose case for `consumeForPurpose(..., EMAIL_VERIFY)` | AC2a (wrong-purpose), R5 |
| `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces` | `AccountExceptionHandlerTest.java` (new, Phase 6; strengthened Phase 9 Findings 5/6) | Two `ProblemDetail`s from independently constructed exceptions have equal `status`/`type`/`title`/`detail`, and both `instance`/`properties` are null | AC4, L5 |
| `verifyEmailAndPasswordResetRejectionsProduceIdenticalResponsesThroughTheRealServiceMethods` | `AccountServiceTest.java` (new, Phase 9 Finding 1) | The same AC4 guarantee, proven through the *real* `activateFromVerificationToken`/`resetPassword` call sites and a real thrown exception, not a synthetic one | AC4, L5 (strengthens the above — real call sites, not just the handler in isolation) |
| `shouldRejectVerificationForEveryNonPendingAccountStatus` | `AccountServiceTest.java` (new, Phase 9 Finding 2) | `activateFromVerificationToken` rejects every non-`PENDING_VERIFICATION` status (`ACTIVE`, `LOCKED`, `SUSPENDED`, `DELETED`) uniformly — closes the `LOCKED` gap `consumeForPurpose` itself doesn't cover (`isAccountUsable` only excludes `DELETED`/`SUSPENDED`) | R5 (extends `shouldRejectVerificationWhenAccountIsNotPendingVerification`'s `ACTIVE`-only coverage) |
| `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` | `AccountControllerTest.java` (pre-existing, T02, cited not duplicated) | Duplicate-email registration returns the identical `202` acknowledgement as new registration, at the controller/response level | AC1, R2 |
| `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount` | `AccountServiceTest.java` (pre-existing, T09, cited not duplicated) | `AccountService.register` throws `DuplicateEmailException` for a duplicate email, at the service level | AC1, R2 |
| `registerMapsConstraintRaceToDuplicateEmail` | `AccountServiceTest.java` (pre-existing, T02, cited not duplicated) | The concurrent-insert race also maps to `DuplicateEmailException` | AC1, R2 (regression guard) |
| `shouldRejectPasswordResetForIneligibleAccountStatuses` | `AccountServiceTest.java` (pre-existing, T07) | `resetPassword` rejects every non-eligible status uniformly | AC3, R15 (regression guard) |
| `shouldRejectExpiredPasswordResetTokenViaConsumeForPurpose`, `shouldRejectAlreadyUsedPasswordResetTokenViaConsumeForPurpose`, `shouldRejectConsumeForPurposeWhenAccountIsUnusable`, `shouldRejectTokenWhenPurposeDoesNotMatchAndLeaveItUnconsumed`, `shouldRejectConsumeForPurposeWhenAccountBecomesUnusableBetweenTheTwoChecks` | `VerificationTokenServiceTest.java` (pre-existing, T07) | `consumeForPurpose(..., PASSWORD_RESET)`'s full boundary set, unmodified | AC3, R15 (regression guard) |
| `onVerificationTokenRejectedReturnsUniformBadRequest`, `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` | `AccountExceptionHandlerTest.java` (pre-existing, T06/T07) | The shared handler mapping's own internal uniformity, unmodified | R5/R15 (regression guard) |
| `shouldRejectVerificationWhenAccountIsNotPendingVerification` | `AccountServiceTest.java` (pre-existing, T06, unmodified — not merged/renamed per Phase 9's "no refactor" rule) | `ACTIVE`-specific case, now a subset of `shouldRejectVerificationForEveryNonPendingAccountStatus`'s coverage but kept distinct for its additional spy-based `activateEmail()`-never-invoked assertion | R5 (regression guard) |

## Acceptance criteria coverage

| ID | Covered by |
|---|---|
| AC1 | `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`, `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount`, `registerMapsConstraintRaceToDuplicateEmail` (all pre-existing, cited per frozen brief Finding 3/4's resolution) |
| AC2a | `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose` (not-found/expired/used), `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` (wrong-purpose, cited) |
| AC2b | `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose` (deleted/suspended) |
| AC3 | Five pre-existing `PASSWORD_RESET`-side `consumeForPurpose` tests, unmodified |
| AC4 | `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces` (handler-level) and `verifyEmailAndPasswordResetRejectionsProduceIdenticalResponsesThroughTheRealServiceMethods` (service-level, real call sites — Phase 9 Finding 1's strengthening) |

## Named tests from the header

- `shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` (R1/R2, `package.md` §8) —
  doesn't exist under this literal name; satisfied in substance by
  `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` (frozen brief
  Finding 3/4's resolution — explicit decision, not an oversight).
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` (R5, `package.md` §8) — exists
  verbatim at `VerificationTokenServiceTest.java:93`, covering the purpose-blind `verify`/`consume`
  API (zero production callers today). Its `consumeForPurpose` analog — the method the live
  production call path actually uses — is
  `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose`, this task's new
  test.

## Test execution

Ran via the established `javac` + JUnit Platform Launcher workaround (module-wide `mvn test` still
blocked by the pre-existing, unrelated `token` package break):

```
javac -cp "$(cat /tmp/auth-cp.txt)" -sourcepath services/auth/src/main/java:services/auth/src/test/java \
  AccountServiceTest.java AccountControllerTest.java RegisterAccountRequestValidationTest.java \
  PasswordPolicyTest.java AccountExceptionHandlerTest.java VerificationTokenServiceTest.java
java ... RunTests
```

**Result: 93/93 tests passing** across all six account-module test files (63 from T09's five
files, unchanged; 30 across `AccountExceptionHandlerTest`/`VerificationTokenServiceTest`, including
this task's 4 new tests), no Spring context, no database.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 10.
- Requirements: R2, R5, R15 — all covered, no gaps.
- LOCKED decisions: L5 — cross-surface consistency now proven both at the handler layer and
  through the real service call sites.
- Named tests: R5's exists verbatim (on a superseded method); R2's is satisfied in substance under
  a different, deliberately-kept name (frozen brief decision).
