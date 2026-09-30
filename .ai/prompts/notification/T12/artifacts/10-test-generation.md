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
