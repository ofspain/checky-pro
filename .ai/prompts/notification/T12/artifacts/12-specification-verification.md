# notification · T12 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T12 — Email channel (O2/Q2) |
| **Consumes** | All prior T12 artifacts (Phases 0-11) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R1** — `verify_email` messages are actually sent, not merely dispatched | Yes | `EmailChannel.java:47` `send` → `EmailTransport.send`; `SesEmailTransport.java:55` performs the real SES v2 call | `DeliveryOrchestratorIntegrationTest.dispatchCapturesARealSentEmailWithCorrectFields` (real end-to-end, real seeded `email.verify` template, real captured message) | No | No |
| **R2** — `password_reset` messages are actually sent | Yes | Same call path as R1 — `EmailChannel`/`SesEmailTransport` are template-agnostic | `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` (T11, exercises `verify_email`); the identical code path serves `password_reset` | No | No — no dedicated `password_reset`-specific test exists, since `EmailChannel` itself never branches on notification kind (that's `DeliveryOrchestrator`'s own resolved responsibility, T11, unchanged) |
| **R15** — no secret/token/API key in a rendered message or log line this class constructs | Yes | `EmailChannel.java:42-58` (log line names only `accountUuid`/`messageId`); `SesEmailTransport.java:80-85` `safeMessageFor` (never forwards a raw SDK exception's own message); `EmailMessage.java` `toString()` (excludes `to`/`subject`/`body`) | `EmailChannelTest.successLogLineNeverReferencesTheRecipientVariable` + `successLogEventContainsNeitherTheRecipientAddressNorRenderedContentAtRuntime` (static + runtime); `SesEmailTransportTest.awsServiceExceptionIsSanitizedToOnlyItsOwnTrustedErrorDetailsWithNoCause` (adversarial); `EmailMessageTest.toStringExcludesTheRecipientAddressSubjectAndBody` | No | No |
| **L4** — no secrets or tokens in messages or logs | Yes | Same evidence as R15 | Same as R15 | No | No |
| **L5** — channels behind one interface, orchestrator channel-agnostic | Yes | `EmailChannel implements NotificationChannel`; `DeliveryOrchestrator` (T11, unchanged — zero lines touched by this task) | `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames` (T11, now implicitly covers `EmailChannel` too, since `NoOpEmailChannel` no longer exists) | No | No |
| **L10** — no email-transport credential committed; no AWS SDK secret-retrieval in application code | Yes | `SesClientConfig.java:23-24` — the entire bean body is `SesV2Client.builder().build()`, no explicit region/credential property anywhere | `EmailTransportWiringTest.sesTransportIsTheOnlyEmailTransportBeanWhenTransportIsSes` (proves the client is constructible via the SDK's own default provider chain, using only a test-scoped `aws.region` system property, never a real credential) | No | No |
| **L11** — module boundaries; no write to `delivery_log`; no import from `delivery/` | Yes | `EmailChannel.java`/`EmailTransport.java`/`SesEmailTransport.java`/`FakeEmailTransport.java` all live in `channel/`; none imports anything from `com.themistra.notification.delivery` (verified directly — grep for `import com.themistra.notification.delivery` across every T12 file returns nothing) | Implicit — `EmailChannel.send` has no `try/catch` around `emailTransport.send` at all (`EmailChannelTest.sendPropagatesTheTransportsOwnExceptionUnmodified`), so it structurally cannot record an outcome itself | No | No |
| **AC1** — `EmailChannel implements NotificationChannel`; `channel()` returns `"EMAIL"`; `NoOpEmailChannel` deleted | Yes | `EmailChannel.java:29,42-44`; `NoOpEmailChannel.java` confirmed absent (`git log` shows deletion at `68a1bb6`) | `EmailChannelTest.channelReturnsEmail` | No | No |
| **AC2** — real transport calls AWS SES v2 with `from`/destination/subject/body | Yes | `SesEmailTransport.java:57-68` | `SesEmailTransportTest.sendBuildsTheCorrectRequestAndReturnsTheMessageId` (asserts the actual built `SendEmailRequest`) | No | No |
| **AC3** — fake transport captures without reaching AWS, observable to tests | Yes | `FakeEmailTransport.java` (`@ConditionalOnProperty(..., havingValue="fake")`) | `FakeEmailTransportTest` (8 tests) + `DeliveryOrchestratorIntegrationTest.dispatchCapturesARealSentEmailWithCorrectFields` (real end-to-end capture) | No | No |
| **AC4** — `send` never catches/swallows a transport failure | Yes | `EmailChannel.java:47-58` — no `try/catch` at all around the transport call | `EmailChannelTest.sendPropagatesTheTransportsOwnExceptionUnmodified` | No | No |
| **AC5** — no raw secret/token/API key in a self-constructed message or log | Yes | Same evidence as R15/L4 | Same as R15/L4 | No | No |
| **AC6** — no hardcoded AWS credential/region | Yes | `SesClientConfig.java:23-24` | `EmailTransportWiringTest.sesTransportIsTheOnlyEmailTransportBeanWhenTransportIsSes` | No | No |
| **AC7** — validates `recipient`/`subject`/`body` before any transport call | Yes | `EmailChannel.java:60-73` | `EmailChannelTest` (5 validation tests, each also asserting `verifyNoInteractions(emailTransport)`) | No | No |
| **AC8** — `SesEmailTransport` never lets a raw SDK exception escape unmodified | Yes | `SesEmailTransport.java:55-78` (construction inside `try`, catches `RuntimeException` broadly, `safeMessageFor` at line 80) | `SesEmailTransportTest` (2 adversarial tests: `AwsServiceException` and a generic `RuntimeException`) + `requestConstructionHappensInsideTheTryBlock` (static guard) | No | No |
| **AC9** — exactly one `NotificationChannel` bean returns `"EMAIL"` in any given run | Yes | `EmailChannel`/`FakeEmailTransport`/`SesEmailTransport` `@ConditionalOnProperty` guards, mutually exclusive by construction | `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames` (T11, real context, default `fake`) + `EmailTransportWiringTest` (all 3: `fake`, `ses`, and an invalid value registers neither) | No | No |
| **AC10** — `FakeEmailTransport`'s capture list is thread-safe | Yes | `FakeEmailTransport.java:30` `CopyOnWriteArrayList` | `FakeEmailTransportTest.concurrentSendsAreAllCapturedWithoutLoss` (16 threads, distinct recipients) + `findMostRecentByRecipientNeverThrowsOrReturnsAPartiallyConstructedMessageUnderConcurrentSendsToTheSameRecipient` (16 threads, same recipient) | No | No |

## Answers

**(1) Is the task fully complete?** Yes. All 6 originally-planned files exist plus the 2 added during
review (`EmailTransportStartupValidation`, and `application.properties`'s own default-value edit);
`NoOpEmailChannel`/its test are deleted, per T11's own pre-authorized Javadoc. The task has been
through 3 rounds of adversarial review (Kimi Phases 3, 8, 11) and this session's own self-review
(Phase 7), with every finding either fixed, correctly rejected with evidence, or explicitly
documented as an accepted, out-of-scope risk inherited from T11's own already-frozen design.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC10, see matrix above. AC8
(never let a raw SDK exception escape unmodified) is the most security-sensitive constraint this
task carries and is proven by a genuinely adversarial test
(`awsServiceExceptionIsSanitizedToOnlyItsOwnTrustedErrorDetailsWithNoCause`) that deliberately embeds
a token-shaped string in the *untrusted* half of a real AWS exception object and confirms it never
surfaces — not merely a happy-path assertion.

**(3) Does it violate any LOCKED decision?** No. L4/L5/L10/L11 all hold, per the matrix. `DeliveryOrchestrator`
(T11) required zero changes — confirmed directly (`git diff` across this task's own full commit
range touches no file under `delivery/`), the strongest possible proof that L5's own
"channel-agnostic orchestrator" property was honored, not merely asserted.

**(4) Remaining risks?**
- **Findings #9/#10 from the self-review and Phase 8 review** (a real SES call runs synchronously
  inside `DeliveryOrchestrator`'s own open DB transaction; a transaction abort after a successful
  send can cause a real duplicate email on redelivery) are explicitly disclosed, accepted
  architectural risks inherited from T11's own already-frozen transaction boundary — not new bugs
  this task introduces, and not fixed here, since doing so would mean redesigning T11's own frozen
  brief. Low-harm in practice for `verify_email`/`password_reset` (a duplicate re-delivers the same
  still-valid link).
- **`password_reset` has no dedicated end-to-end test** distinct from `verify_email` — acceptable,
  since `EmailChannel`/`SesEmailTransport`/`FakeEmailTransport` are all notification-kind-agnostic;
  the kind-to-template resolution is `DeliveryOrchestrator`'s own already-tested responsibility
  (T11), not something this task's own code branches on.
- **Real Amazon SES has never actually been called** — by design (AC3, `agents.md`'s own "no real
  email is sent in CI" mandate). `SesEmailTransport`'s own correctness against the *real* SES service
  (as opposed to its documented API contract, verified via `javap` against the actual SDK jars before
  writing any code) will only be confirmed the first time `EMAIL_TRANSPORT=ses` runs in a real `dev`/
  `staging` environment — a deployment-time validation this task's own scope cannot itself perform.

## Verdict

**PASS** — T12 fully satisfies R1, R2, R15, and every locked decision and acceptance criterion it
touches; the real transport is genuinely wired to Amazon SES with adversarially-proven exception
sanitization; the fake transport is thread-safe and end-to-end observable; `DeliveryOrchestrator`
(T11) required zero changes, confirming the channel-behind-one-interface boundary held. The full
suite is green at 273 tests, 0 failures, 0 errors.
