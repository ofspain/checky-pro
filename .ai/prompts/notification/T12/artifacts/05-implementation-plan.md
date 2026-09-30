# notification · T12 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's own Files to Create/Modify/Delete. No file is added
beyond what Phase 4 authorized. No code — signatures and behavior only.

## Files to create

### `channel/EmailMessage.java`
```
public record EmailMessage(String to, String from, String subject, String body)
```
Overrides `toString()` to exclude `subject`/`body` (they may carry a rendered token-bearing link,
e.g. `verify_email`/`password_reset`'s own computed link) — mirrors `TemplateRenderer.RenderedMessage`'s
own established safe-`toString()` precedent (T09) exactly, so no future log statement that happens
to print an `EmailMessage` whole can leak content.

### `channel/EmailDeliveryException.java`
```
public class EmailDeliveryException extends RuntimeException {
    public EmailDeliveryException(String message)
    public EmailDeliveryException(String message, Throwable cause)
}
```
Standard two-constructor unchecked exception shape. **`SesEmailTransport` (below) only ever uses the
single-`message` constructor when converting a caught SDK exception** — never the `cause`-attaching
one. This is deliberate, not an oversight: SLF4J/Logback prints a `Throwable`'s full "Caused by:"
chain by default, so attaching the original SDK exception as `cause` would silently defeat the whole
sanitization Finding #4 exists to provide, the first time `DeliveryOrchestrator`'s own existing
`log.error(..., e)` (T11, unchanged) logs it. The two-arg constructor exists only for ordinary Java
exception-convention completeness / any future caller with a genuinely safe cause to attach; it is
not used by this task's own code.

### `channel/EmailTransport.java`
```
public interface EmailTransport {
    String send(EmailMessage message);
}
```
Returns the provider's own opaque success identifier (a real SES `messageId`, or a synthetic id from
the fake). Throws `EmailDeliveryException` on failure — documented via Javadoc `@throws`, not a
checked `throws` clause (matches this codebase's own unchecked-exception convention throughout).

### `channel/FakeEmailTransport.java`
```
@Component
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "fake")
public class FakeEmailTransport implements EmailTransport {
    public String send(EmailMessage message)
    public List<EmailMessage> sentMessages()
    public void clear()
    public Optional<EmailMessage> findByRecipient(String recipient)
}
```
Backed by `CopyOnWriteArrayList<EmailMessage>` (AC10 — thread-safe; `EmailChannel` is a singleton
called concurrently from Kafka listener threads, T06's own `concurrency: 2` precedent).
`sentMessages()` returns an unmodifiable view. `send` appends and returns
`"fake-" + UUID.randomUUID()`. `findByRecipient` returns the most recently sent match, if any.

### `common/config/SesClientConfig.java`
```
@Configuration
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "ses")
public class SesClientConfig {
    @Bean SesV2Client sesV2Client()
}
```
`SesV2Client.builder().build()` — no explicit region/credentials (L10; the SDK's own default
provider chain resolves both). Conditional at the class level so the bean, and any credential/region
resolution attempt at all, never happens under `transport=fake` (test/local default).

### `channel/SesEmailTransport.java`
```
@Component
@ConditionalOnProperty(prefix = "themistra.notification.email", name = "transport", havingValue = "ses")
public class SesEmailTransport implements EmailTransport {
    SesEmailTransport(SesV2Client sesV2Client)
    public String send(EmailMessage message)
    private String safeMessageFor(SdkException e)
}
```
`send` builds a plain-text-only (Finding #6) `SendEmailRequest` (`fromEmailAddress` =
`message.from()`, `destination().toAddresses(message.to())`,
`content().simple().subject(...).body().text(message.body())`), calls
`sesV2Client.sendEmail(request)`, returns `response.messageId()`.

Catches `SdkException` (the SES v2 SDK's own common superclass for both client-side and
service-side failures). `safeMessageFor` branches: if the exception is an `AwsServiceException`,
build the safe message from its own `awsErrorDetails().errorCode()` +
`awsErrorDetails().errorMessage()` only (AWS's own error description, never the original request);
otherwise (a client-side `SdkException`, e.g. a network failure with no AWS-supplied error detail),
fall back to a generic, class-name-only message (never that exception's own `getMessage()` verbatim
— a client-side exception's own message is not a trusted-safe value, unlike AWS's own structured
error fields). Wraps the result in `new EmailDeliveryException(safeMessage)` — no cause attached
(see the exception class's own note above) — and throws it.

### `channel/EmailChannel.java`
```
@Component
public class EmailChannel implements NotificationChannel {
    EmailChannel(EmailTransport emailTransport, EmailProperties emailProperties)
    public String channel()
    public void send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message)
    private void validate(String recipient, TemplateRenderer.RenderedMessage message)
}
```
`channel()` returns `"EMAIL"`. `validate` throws `IllegalArgumentException` if `recipient` is
null/blank or `message.subject()` is null (AC7) — called first, before any `EmailMessage` is built
or any transport call is attempted. `send` then builds `new EmailMessage(recipient,
emailProperties.from(), message.subject(), message.body())`, calls
`emailTransport.send(emailMessage)`, and logs the returned id at `INFO`
(`accountUuid`, `recipient`, `messageId` only — never `subject`/`body`, matching L4/R15 and Kimi's
own Finding #7). Does not catch any exception `emailTransport.send` throws — it propagates to
`DeliveryOrchestrator`'s own existing `try/catch` (T11, unchanged), which records the `FAILED`
outcome (AC4, L11 — no duplicated outcome-recording responsibility).

## Files to modify

- `application.properties` — one line: `themistra.notification.email.transport=${EMAIL_TRANSPORT:ses}`
  → `themistra.notification.email.transport=${EMAIL_TRANSPORT:fake}`.
- `T01SkeletonRegressionTest.java` — authorized file-inventory list: remove
  `channel/NoOpEmailChannel.java`; add `channel/EmailChannel.java`, `channel/EmailTransport.java`,
  `channel/EmailMessage.java`, `channel/SesEmailTransport.java`, `channel/FakeEmailTransport.java`,
  `channel/EmailDeliveryException.java`, `common/config/SesClientConfig.java`.

## Files to delete

- `channel/NoOpEmailChannel.java`, `channel/NoOpEmailChannelTest.java` (pre-authorized since T11's
  own Javadoc).

## Public methods (signatures)

Listed inline with each file above. Summary: `EmailChannel.channel()`, `EmailChannel.send(UUID,
String, RenderedMessage)`; `EmailTransport.send(EmailMessage)`; `FakeEmailTransport.send(EmailMessage)`,
`.sentMessages()`, `.clear()`, `.findByRecipient(String)`; `SesEmailTransport.send(EmailMessage)`;
`SesClientConfig.sesV2Client()`.

## Private methods

`EmailChannel.validate(String, RenderedMessage)`; `SesEmailTransport.safeMessageFor(SdkException)`.

## Entities used

None — this task introduces no `@Entity`/table (L11, unchanged from Phase 2's own Scope/Out).

## Repositories used

None.

## Services used

`EmailProperties` (T03, existing), `TemplateRenderer.RenderedMessage` (T09, existing type only, not
the service itself), `NotificationChannel` (T11, existing interface). No repository, no `Clock`
(this task needs no timestamp of its own — the SES `messageId` and delivery timestamp already exist
via SES's own response and `DeliveryOrchestrator`'s own `clock.instant()`, T11, unchanged).

## Unit tests required

- `EmailMessageTest` — `toString()` excludes `subject`/`body` content.
- `FakeEmailTransportTest` — `send` captures and returns a non-null id; `sentMessages()` reflects
  captures; `clear()` empties; `findByRecipient` finds the right one; a concurrent-call test (mirrors
  `IdempotencyGuardIntegrationTest.concurrentCallsWithSameKeyResolveToExactlyOneTrue`'s own precedent)
  proving no message is lost under concurrent `send` calls (AC10).
- `SesEmailTransportTest` — mocked `SesV2Client`: `send` builds the correct `SendEmailRequest`
  (from/destination/subject/plain-text body) and returns `response.messageId()`; a thrown
  `AwsServiceException`-shaped exception (constructed with a token-bearing `awsErrorDetails().errorMessage()`
  string as a deliberate adversarial fixture) is converted to an `EmailDeliveryException` whose own
  `getMessage()` never contains that token substring, and whose `getCause()` is `null` (AC8 — a
  mutation-style proof, not just a happy-path assertion); a generic non-AWS `SdkException` is also
  converted safely, never forwarding its own raw message.
- `EmailChannelTest` — `channel()` returns `"EMAIL"`; `send` builds the correct `EmailMessage` and
  calls `emailTransport.send`; throws `IllegalArgumentException` for a blank `recipient` and for a
  null `subject`, in both cases without ever calling `emailTransport.send` (AC7); a thrown
  `EmailDeliveryException` from `emailTransport.send` propagates out of `EmailChannel.send`
  unmodified (AC4 — not caught, not converted).
- `EmailTransportWiringTest` (new, `ApplicationContextRunner`-based, no Testcontainers/Docker) —
  with `themistra.notification.email.transport=fake`: `FakeEmailTransport` bean exists,
  `SesEmailTransport`/`SesClientConfig`'s own `SesV2Client` bean does not; with `...=ses`: the
  reverse. Directly proves Finding #1/#9's own "exactly one transport selected, never both" claim,
  faster and without Postgres/Kafka since no repository/listener is involved in this specific proof.

## Integration tests required

None new with their own dedicated Testcontainers context — `EmailChannel`/`EmailTransport` persist
nothing and consume no Kafka event directly. The existing
`DeliveryOrchestratorIntegrationTest.exactlyTwoNotificationChannelBeansAreRegisteredWithExpectedNames`
(T11) already proves, against the real full application context (default `transport=fake`), that
exactly one `"EMAIL"`-returning bean exists — this task must keep it passing (AC9), not re-implement
it.

## Execution order

1. `channel/EmailMessage.java` (no dependencies).
2. `channel/EmailDeliveryException.java` (no dependencies).
3. `channel/EmailTransport.java` (depends on `EmailMessage`).
4. `channel/FakeEmailTransport.java` (depends on `EmailTransport`, `EmailMessage`).
5. `common/config/SesClientConfig.java` (depends on the AWS SDK only).
6. `channel/SesEmailTransport.java` (depends on `EmailTransport`, `EmailMessage`,
   `EmailDeliveryException`, `SesV2Client`).
7. `channel/EmailChannel.java` (depends on `EmailTransport`, `EmailMessage`, `EmailProperties`,
   `NotificationChannel`).
8. Delete `channel/NoOpEmailChannel.java` and `channel/NoOpEmailChannelTest.java`.
9. `application.properties` — flip the default.
10. `T01SkeletonRegressionTest.java` — update the authorized file list.
11. Tests, in the same dependency order as the files they cover:
    `EmailMessageTest` → `FakeEmailTransportTest` → `SesEmailTransportTest` → `EmailChannelTest` →
    `EmailTransportWiringTest`.
12. Full suite: `mvn -pl services/notification clean verify`.
