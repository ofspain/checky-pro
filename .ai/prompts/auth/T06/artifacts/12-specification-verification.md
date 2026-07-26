# auth · T06 — Phase 12: Specification Verification

Verifying the final implementation (`06-implementation-notes.md`, `09-review-resolution.md`) and
tests (`10-test-generation.md` + its Phase 11 addendum) against `requirements.md`, `design.md`, and
`tasks.md` for T06 only.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R3 — `register` emits `auth.email.requested` in the same transaction | Yes | `AccountService.java:64-81` (`register`), `:198-208` (`issueAndEmitVerificationEmail`) | `shouldEmitVerifyEmailEventOnRegistration`, `registerRejectsKnownDuplicateWithoutTouchingEncoder` (negative: skipped on duplicate) | No | None |
| R4 — valid token activates a `PENDING_VERIFICATION` account, emits `auth.user.registered` | Yes | `AccountService.java:94-114` (`activateFromVerificationToken`) | `shouldActivateAccountWithValidVerificationToken` | No | None |
| R5 — uniform rejection for every invalid-token reason, including wrong status (Finding 2) and a missing resolved account (Finding 1/Gap 1) | Yes | `AccountService.java:96-108`; `AccountExceptionHandler.java:39-45` | `shouldRejectVerificationWhenTokenConsumeReturnsEmpty`, `shouldRejectVerificationWhenAccountIsNotPendingVerification`, `shouldRejectVerificationWhenAccountDisappearsAfterConsume`, `onVerificationTokenRejectedReturnsUniformBadRequest`, `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` | No | None |
| R6 (modified at Phase 0) — `resend-verification` public, email-identified, uniform response | Yes | `AccountService.java:123-128` (`resendVerificationIfPending`); `AccountController.java` `resendVerification` | `shouldResendVerificationOnlyForPending accounts`, `resendVerificationNormalizesEmailBeforeLookup`, `resendVerificationAlwaysReturnsTheSameAcknowledgementRegardlessOfMatch` | No | **Yes — R6's literal "authenticated caller" wording not followed; explicit, human-confirmed deviation recorded at Phase 0/1/4.** |
| R44 — `EventTopics` routes `verification-token` → `auth.email.requested` | Yes | `EventTopics.java` (`TOPIC_BY_AGGREGATE_TYPE`) | `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` | No | None |
| L5 — enumeration-safe responses | Yes | Single `VerificationTokenRejectedException` type, single handler mapping, uniform `resendVerificationIfPending` return (`void`) | Covered by R5/R6 tests above | No | None |
| L11 (widened) — new public paths registered | Yes | `PublicEndpoints.java` `METHOD_SCOPED` (both new entries); confirmed `SecurityChainsConfig` consumes the list automatically, no separate change needed | No dedicated test (structural; `SecurityChainsConfig`/ArchUnit sweep unchanged and unaffected, confirmed at Phase 6) | No | None |
| Finding 1 (frozen brief LOCKED exception) — raw token in `EmailRequestedEventPayload`, formalized, mitigated | Yes | `EmailRequestedEventPayload.java` (overridden `toString()`) | `shouldEmitVerifyEmailEventOnRegistration` (asserts `toString()` excludes the token) | No | **Yes — explicit, human-approved LOCKED exception to `agents.md`'s credential-handling rule, recorded at Phase 4.** |
| Finding 5 — self-service audit `actorUuid` = account's own UUID | Yes | `AccountService.java:112` | `shouldActivateAccountWithValidVerificationToken` | No | None |
| Finding 6 — outbox `aggregateId` = account UUID | Yes | `AccountService.java:203` | `shouldEmitVerifyEmailEventOnRegistration` | No | None |

## Principal-engineer assessment

**(1) Is the task fully complete?**
Yes. Both endpoints, the `AccountService` wiring, `EventTopics`, `PublicEndpoints`, and
`ProblemTypes` changes are all implemented exactly per the frozen brief. The one item logged as
deferred at Phase 9 (the `AccountNotFoundException` leak, self-review/Kimi Finding) was
subsequently fixed for real at Phase 11, closing what would otherwise have been the task's only
remaining known gap.

**(2) Does it satisfy every acceptance criterion?**
Yes. R3/R4/R5/R6(modified)/R44 and L5/L11 all have passing, named or purpose-built tests. The two
HIGH self-review/independent-review findings (the broken existing test suite, and the account-state
leak) are both closed — the first was a real, confirmed compile failure fixed at Phase 9; the
second was deferred at Phase 9 and then fixed at Phase 11 once a regression test made the cost of
leaving it clear.

**(3) Does it violate any LOCKED decision?**
No new violations. Two decisions were explicitly, narrowly overridden with full human sign-off and
paper trail, not silently: R6's "authenticated caller" wording (Phase 0/1/4 — resend-verification
is public and email-identified instead) and `agents.md`'s general credential-in-transit rule
(Phase 3/4 Finding 1 — the raw token appears once in `EmailRequestedEventPayload`, mitigated by the
overridden `toString()` and reliance on existing bounded Kafka/outbox retention).

**(4) Remaining risks?**
- The pre-existing, unrelated `token` package compile failure (tracked since T03) still blocks a
  real `mvn -pl services/auth test` / Surefire run. All 28 tests were verified via direct `javac` +
  JUnit Platform `Launcher` execution — the same engine Surefire delegates to.
- **Gap 3 from Phase 11, deferred, not fixed:** no test exercises the real Spring dispatcher
  routing a rejected `verify-email` request through to `AccountExceptionHandler` and producing an
  actual `400`/`application/problem+json` HTTP response — `AccountControllerTest` (plain Mockito)
  and `AccountExceptionHandlerTest` (direct method call) together cover the logic on both sides of
  that boundary but not the Spring wiring itself. Low risk (`@RestControllerAdvice` +
  `@ExceptionHandler` is standard, well-tested Spring machinery), but genuinely unverified by this
  task's test suite. Recommended as a candidate for a future `MockMvc`/integration-test pass,
  which the module doesn't have any precedent for yet.
- `EmailRequestedEventPayload`'s raw-token-in-transit design (Finding 1) depends on Notification
  Service (not yet built) handling it correctly once it exists — this task can only guarantee its
  own side (never logged, never echoed, excluded from `toString()`).
- No contract exists yet for `auth.email.requested` (`contracts/events/auth/
  email-requested.v1.schema.json` is task 33's) — `EmailRequestedEventPayload`'s shape is this
  task's own reasonable choice, not yet validated against a formal schema the way
  `UserLifecycleEventPayload` is.

## Verdict

**PASS** — every R3/R4/R5/R6(as modified)/R44 acceptance criterion and LOCKED decision (L5, L11,
plus the two explicitly-recorded overrides) is implemented exactly as specified, backed by 28
passing, deterministic tests including genuine regression coverage for both HIGH findings raised
during review. The one deferred item from Phase 9 was closed before this verification, not carried
forward. Remaining risks are a documented, low-risk test-coverage gap (Gap 3, no `MockMvc`
precedent in this module) and forward dependencies on not-yet-built work (Notification Service,
task 33's contract) that are explicitly out of this task's scope.
