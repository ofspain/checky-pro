# notification · T12 · Phase 6 — Implementation Notes

Implemented exactly per the Phase 5 plan; no deviation forced by reality. All AWS SES v2 SDK API
shapes (`SendEmailRequest`, `Destination`, `EmailContent`, `Message`, `Body`, `Content`,
`SendEmailResponse.messageId()`, `AwsServiceException.awsErrorDetails()`) were verified directly
against the actual `sesv2-2.50.2`/`aws-core-2.25.16` jars on the local Maven repo (via `javap`)
before writing any code, not assumed from memory.

## Files created

- `channel/EmailMessage.java` — record (`to`, `from`, `subject`, `body`); `toString()` excludes
  `subject`/`body`, mirroring `RenderedMessage`'s own precedent (T09).
- `channel/EmailDeliveryException.java` — two-constructor unchecked exception; documented (and, in
  `SesEmailTransport`, honored) that the cause-attaching constructor is never used when converting a
  caught SDK exception, since SLF4J/Logback prints a full cause chain by default.
- `channel/EmailTransport.java` — the real/fake seam interface.
- `channel/FakeEmailTransport.java` — `@ConditionalOnProperty(..., havingValue = "fake")`,
  `CopyOnWriteArrayList`-backed capture list; `send`/`sentMessages`/`clear`/`findByRecipient`.
- `common/config/SesClientConfig.java` — `@ConditionalOnProperty(..., havingValue = "ses")`, one
  `@Bean SesV2Client sesV2Client() { return SesV2Client.builder().build(); }` — no explicit
  region/credentials, per L10.
- `channel/SesEmailTransport.java` — real transport; builds a plain-text-only `SendEmailRequest`;
  catches `SdkException`, branches on `AwsServiceException` for a safe `errorCode`/`errorMessage`,
  falls back to a generic class-name-only message otherwise; always re-throws via the
  message-only `EmailDeliveryException` constructor (no cause attached).
- `channel/EmailChannel.java` — `implements NotificationChannel`; `channel()` returns `"EMAIL"`;
  `validate` throws `IllegalArgumentException` for a blank `recipient` or null `subject` before any
  transport call; builds `EmailMessage` from `EmailProperties.from()` + the resolved `recipient` +
  the rendered `subject`/`body`; logs `INFO` with `accountUuid`/`recipient`/`messageId` only on
  success; does not catch anything `emailTransport.send` throws.

## Files modified

- `application.properties` — `themistra.notification.email.transport`'s own default flipped from
  `${EMAIL_TRANSPORT:ses}` to `${EMAIL_TRANSPORT:fake}`, with an inline comment explaining why
  (local/test must never reach AWS by accident; real environments set `EMAIL_TRANSPORT=ses`
  explicitly via their own deployment config).
- `T01SkeletonRegressionTest.java` — authorized file-inventory list: removed
  `channel/NoOpEmailChannel.java`; added the 7 new files; renamed the test method to
  `noExtraProductionClassesExistBeyondT12sOwnAuthorizedSet` (32 → 38 files).

## Files deleted

- `channel/NoOpEmailChannel.java`, `channel/NoOpEmailChannelTest.java` (pre-authorized since T11's
  own Javadoc).

## Mapping to acceptance criteria

- **AC1** (interface + deletion): `EmailChannel implements NotificationChannel`, `channel()` returns
  `"EMAIL"`; `NoOpEmailChannel`/its test deleted.
- **AC2** (real transport): `SesEmailTransport.send` builds the request from `EmailProperties.from()`
  (via `EmailChannel`'s own `EmailMessage.from`), `recipient`, `subject`, `body`.
- **AC3** (fake transport): `FakeEmailTransport`, active under the new default, captures without any
  AWS SDK call.
- **AC4** (no swallowing): `EmailChannel.send` has no `try/catch` around `emailTransport.send` at
  all — an exception it throws propagates directly.
- **AC5** (no secret in self-constructed messages/logs): `EmailChannel`'s own `INFO` log line
  excludes `subject`/`body`; `SesEmailTransport`'s own re-thrown exception never contains the
  original request.
- **AC6** (no hardcoded AWS credential/region): `SesClientConfig`'s only line is
  `SesV2Client.builder().build()`.
- **AC7** (validation): `EmailChannel.validate`, called first in `send`, before any `EmailMessage` is
  even constructed.
- **AC8** (sanitization): `SesEmailTransport.safeMessageFor` — verified structurally correct against
  the real `AwsServiceException`/`AwsErrorDetails` API surface (Phase 10 will add the mutation-style
  proof this claim needs).
- **AC9** (exactly one `"EMAIL"` bean): confirmed empirically — the full suite (including T11's own
  `DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`,
  unchanged) passes under the new default (`transport=fake`), proving `EmailChannel` +
  `FakeEmailTransport` wire as the sole `EMAIL` channel with no duplicate-bean collision.
- **AC10** (thread-safe fake): `CopyOnWriteArrayList` (Phase 10 will add the concurrent-call proof).

## Deviations from the plan

None. Every file, method signature, and design note (the no-cause-attached exception rule, the
plain-text-only body, the two `@ConditionalOnProperty` transports) was implemented exactly as
planned at Phase 5.

## Verification

- `mvn -pl services/notification clean compile` — clean.
- `mvn -pl services/notification clean verify` — 236 tests, 0 failures, 0 errors (239 − 3 removed
  with `NoOpEmailChannelTest`; no new tests added yet — that is Phase 10's own scope). The full suite
  passing under the new `transport=fake` default is itself a real, non-trivial proof: it means the
  whole application context — including T11's own `DeliveryOrchestratorIntegrationTest` — boots
  successfully with `EmailChannel`/`FakeEmailTransport` as the real `EMAIL` channel, not merely that
  the code compiles.
