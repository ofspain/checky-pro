# notification · T12 · Phase 10 — Test Generation

Full Phase 10 test set for T12 (`EmailChannel`/`EmailTransport`/`SesEmailTransport`/
`FakeEmailTransport`/`EmailMessage`/`EmailTransportStartupValidation`), closing Kimi Phase 8
Finding #1 ("no `EmailChannel`/`SesEmailTransport` tests exist"). No production code changed. 6 new
test files, 31 new tests (267 total: 236 T01-T11/Phase-9 unaffected + 31 new).

## Files created

- `channel/EmailMessageTest.java` — 3 tests, plain JUnit, no Spring context, no Docker.
- `channel/FakeEmailTransportTest.java` — 8 tests, plain JUnit, no Spring context, no Docker.
- `channel/SesEmailTransportTest.java` — 4 tests, mocked `SesV2Client`, no Spring context, no Docker,
  no real AWS credential/network access.
- `channel/EmailChannelTest.java` — 9 tests, mocked `EmailTransport`, no Spring context, no Docker.
- `common/config/EmailTransportStartupValidationTest.java` — 4 tests, plain JUnit (the validated
  class is package-private, so this test lives in the same package).
- `channel/EmailTransportWiringTest.java` — 3 tests, `ApplicationContextRunner`-based (no
  Testcontainers, no Docker, no real AWS credentials — a fixed `aws.region` system property lets
  `SesV2Client.builder().build()` resolve a region without ever calling AWS).

## Test manifest

| Test method | Verifies | AC / Finding |
|---|---|---|
| `EmailMessageTest.toStringExcludesTheRecipientAddressSubjectAndBody` | `to`/`subject`/`body` never appear in `toString()` | Phase 9 fix (Finding #2's own latent twin) |
| `EmailMessageTest.toStringReportsSubjectAndBodyLengthsNotContent` | Lengths only, safe | AC5 |
| `EmailMessageTest.toStringHandlesNullSubjectAndBodyAsZeroLength` | Null-safety | Defensive |
| `FakeEmailTransportTest.sendCapturesTheMessageAndReturnsANonNullId` | Basic capture + id | AC3 |
| `FakeEmailTransportTest.sentMessagesReturnsAnUnmodifiableView` | Encapsulation | Defensive |
| `FakeEmailTransportTest.clearEmptiesTheCaptureList` | `clear()` | AC3 |
| `FakeEmailTransportTest.findMostRecentByRecipientReturnsEmptyWhenNothingSent` | Absent case | Finding #5 |
| `FakeEmailTransportTest.findMostRecentByRecipientReturnsTheOnlyMatch` | Single-match case | Finding #5 |
| `FakeEmailTransportTest.findMostRecentByRecipientReturnsTheLatestWhenSentMultipleTimesToTheSameRecipient` | "Most recent, not first" semantics | Finding #5 |
| `FakeEmailTransportTest.findMostRecentByRecipientIgnoresNonMatchingRecipients` | No false positive | Finding #5 |
| `FakeEmailTransportTest.concurrentSendsAreAllCapturedWithoutLoss` | Thread-safety, 16 concurrent senders | AC10, Finding #2 |
| `SesEmailTransportTest.sendBuildsTheCorrectRequestAndReturnsTheMessageId` | Correct `SendEmailRequest` (from/destination/subject/plain-text body), returns `messageId` | AC2 |
| `SesEmailTransportTest.awsServiceExceptionIsSanitizedToOnlyItsOwnTrustedErrorDetailsWithNoCause` | Adversarial: a token-shaped string in the untrusted top-level `message()` never appears in the re-thrown exception; only `awsErrorDetails()` surfaces; no cause attached | AC8, Finding #4 (Phase 3) |
| `SesEmailTransportTest.aGenericRuntimeExceptionIsConvertedToASafeGenericMessageWithNoCause` | A non-AWS `RuntimeException`'s own raw message is never forwarded | AC8, Phase 9 Finding #3 fix |
| `SesEmailTransportTest.requestConstructionHappensInsideTheTryBlock` | Static guard: locks the Phase 9 structural fix (construction now inside `try`) | Finding #3 |
| `EmailChannelTest.channelReturnsEmail` | `channel()` value | AC1 |
| `EmailChannelTest.sendBuildsTheCorrectEmailMessageAndDelegatesToTheTransport` | Correct `EmailMessage` built and forwarded | AC2/AC3 |
| `EmailChannelTest.sendThrowsForANullRecipientWithoutCallingTheTransport` | Validation, no transport call | AC7 |
| `EmailChannelTest.sendThrowsForABlankRecipientWithoutCallingTheTransport` | Validation, no transport call | AC7 |
| `EmailChannelTest.sendThrowsForANullSubjectWithoutCallingTheTransport` | Validation, no transport call | AC7 |
| `EmailChannelTest.sendThrowsForANullBodyWithoutCallingTheTransport` | Validation, no transport call | AC7 (Phase 9 fix) |
| `EmailChannelTest.sendThrowsForABlankBodyWithoutCallingTheTransport` | Validation, no transport call | AC7 (Phase 9 fix) |
| `EmailChannelTest.sendPropagatesTheTransportsOwnExceptionUnmodified` | Never caught/converted here | AC4 |
| `EmailChannelTest.successLogLineNeverReferencesTheRecipientVariable` | Static guard locking the Phase 9 PII fix | Finding #2 |
| `EmailTransportStartupValidationTest.acceptsSesWithoutThrowing` | Valid value | Finding #8 |
| `EmailTransportStartupValidationTest.acceptsFakeWithoutThrowing` | Valid value | Finding #8 |
| `EmailTransportStartupValidationTest.rejectsAnyOtherValueWithAClearMessageNamingTheActualValue` | Clear failure message | Finding #8 |
| `EmailTransportStartupValidationTest.rejectsACaseMismatchedValue` | Case-sensitivity | Finding #8 |
| `EmailTransportWiringTest.fakeTransportIsTheOnlyEmailTransportBeanWhenTransportIsFake` | Exactly one transport, no `SesV2Client` constructed | AC9, Finding #1/#9 (Phase 3) |
| `EmailTransportWiringTest.sesTransportIsTheOnlyEmailTransportBeanWhenTransportIsSes` | Exactly one transport, `SesV2Client` constructible without real credentials | AC9, Finding #1/#9 (Phase 3) |
| `EmailTransportWiringTest.neitherTransportIsRegisteredForAnUnrecognizedValue` | No accidental double-registration on a bad value | Finding #9 (Phase 3) |

## Named tests

No `package.md` §8 named test uniquely maps to this task (per Phase 1's own finding, reconfirmed
here) — R1/R2's own named tests (`shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
`shouldSendPasswordResetEmailOnAuthEmailRequestedReset`) remain in `AuthEventConsumerTest` (T06),
proving routing only. This task's own tests, listed above, prove the actual send is now real.

## Verification

`mvn -pl services/notification clean verify` — 267 tests, 0 failures, 0 errors. No production code
was modified in this phase.

## Addendum (post Phase 11) — 5 of 9 gaps fixed with new tests; 2 already covered, 1 rejected, 1 folded

Kimi's Phase 11 review raised 9 gaps. All verified against the actual test suite before acting —
**Gaps #3 and #4 turned out to be already covered** by a pre-existing T11 test that neither Kimi nor
the Phase 10 manifest had connected to T12's own new code.

- **Gap #1** (no test inspects `FakeEmailTransport.sentMessages()` end-to-end) — **fixed**: added
  `DeliveryOrchestratorIntegrationTest.dispatchCapturesARealSentEmailWithCorrectFields`, plus a
  `@BeforeEach clearFakeEmailTransport()` (this bean is a Spring singleton shared across every test
  in that class — without clearing it, an assertion on its own captures would be polluted by
  whichever earlier test ran first).
- **Gap #2** (startup validator tested only via direct construction) — **fixed**: added two
  `ApplicationContextRunner`-based tests to `EmailTransportStartupValidationTest` proving Spring
  itself fails context startup with the validator's own clear message (not a generic
  `NoSuchBeanDefinitionException`) for an invalid value, and starts normally for a valid one.
- **Gap #3** (no test proves `EmailChannel` is registered as a `NotificationChannel` bean) —
  **already covered, no new action**: `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`
  (T11, unchanged) already asserts, against the real, fully component-scanned application context,
  that exactly 2 `NotificationChannel` beans exist and their names are exactly `"EMAIL"`/`"IN_APP"` —
  since `NoOpEmailChannel` no longer exists, the only possible source of `"EMAIL"` today is
  `EmailChannel` itself. This is real, already-passing proof; the gap was in cross-referencing which
  existing test now covers this task's own new class, not in test coverage itself.
- **Gap #4** (no test proves `NoOpEmailChannel` no longer causes a duplicate-bean startup failure) —
  **already covered, no new action** (same test as Gap #3): `DeliveryOrchestrator`'s own constructor
  (`Collectors.toMap(NotificationChannel::channel, ...)`, T11) throws `IllegalStateException` at
  context-startup time if two beans ever returned `"EMAIL"` — since every `@SpringBootTest` in this
  module (not only this one test) boots successfully today, a stray/duplicate `"EMAIL"` bean would
  already be impossible without every one of those tests failing to even start.
- **Gap #5** (log safety guarded only by static source inspection) — **fixed**: added
  `EmailChannelTest.successLogEventContainsNeitherTheRecipientAddressNorRenderedContentAtRuntime`,
  attaching a real Logback `ListAppender` (already on the classpath via `spring-boot-starter`, no new
  test dependency) to inspect the actual formatted runtime log event, complementing (not replacing)
  the existing static guard.
- **Gap #6** (no test exercises `EmailChannel`+`FakeEmailTransport` wiring outside
  `DeliveryOrchestrator`) — **rejected as redundant once Gap #1 is fixed**: `EmailChannel`'s own
  channel-specific behavior (validation/delegation/logging) is already isolated at the unit level in
  `EmailChannelTest` (mocked `EmailTransport`); the real-Spring-context angle is now covered by
  Gap #1's own fix, exercised through `dispatch` with real beans. A third, narrower variant would add
  no new coverage.
- **Gap #7** (`findMostRecentByRecipient` untested under concurrent sends to the *same* recipient) —
  **fixed**: added
  `FakeEmailTransportTest.findMostRecentByRecipientNeverThrowsOrReturnsAPartiallyConstructedMessageUnderConcurrentSendsToTheSameRecipient`
  (16 threads, distinct subjects, same recipient) — an empirical confirmation of
  `CopyOnWriteArrayList`'s own already-documented snapshot-iteration contract, not a search for a bug
  that contract already rules out.
- **Gap #8** (no test for the failure mode when both transports are somehow present) — **rejected,
  out of scope**: this would test `DeliveryOrchestrator`'s own constructor behavior (T11, already
  frozen and shipped), not any code this task introduces. T12's own actual preventive mechanism (the
  `@ConditionalOnProperty` guards) is already proven mutually exclusive under every real property
  value by `EmailTransportWiringTest` (Phase 10).
- **Gap #9** (no test proves `EmailProperties` binds from the real `application.properties` file) —
  **fixed**: added `DeliveryOrchestratorIntegrationTest.emailPropertiesBindsFromTheRealApplicationPropertiesFile`,
  naming T12's own actual config change (the `transport` default flip to `"fake"`) explicitly, rather
  than relying on the implicit proof that the whole suite couldn't otherwise boot.

**Files touched this addendum:**
- `delivery/DeliveryOrchestratorIntegrationTest.java` — +2 tests (Gaps #1, #9), +2 autowired fields,
  +1 `@BeforeEach`.
- `common/config/EmailTransportStartupValidationTest.java` — +2 tests (Gap #2).
- `channel/EmailChannelTest.java` — +1 test (Gap #5), +`@BeforeEach`/`@AfterEach` log-capture setup.
- `channel/FakeEmailTransportTest.java` — +1 test (Gap #7).

No production code was changed in this addendum.

**Verification:** `mvn -pl services/notification clean verify` — 273 tests, 0 failures, 0 errors.
