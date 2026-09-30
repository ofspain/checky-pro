<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T12 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T12 — Email channel (SES v2) |
| **Spec section** | Delivery orchestration / email channel |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T12 Phase 2 brief.

---

## Finding 1 · The real-vs-fake transport selection mechanism is not specified

**Severity:** High

**Evidence:**
- `EmailProperties` already has a `transport` field (`String`), deliberately left unconstrained in T03 so T12 could decide its allowed values.
- The brief says `EmailChannel` sends via SES v2 in "real (non-test) profiles" and via a capturing fake in tests, but it does not say how the code makes that choice.
- `agents.md` states: "Local dev runs against Docker Compose … and a capturing fake transport — no real email is sent in CI."

**Recommended brief amendment:**
Pin the selection mechanism. Two common, workable options:
1. **Profile-driven:** real `EmailChannel` is `@Profile("!test & !local")`; a separate `FakeEmailChannel` is `@Profile("test | local")` and also implements `NotificationChannel` with `channel() = "EMAIL"`. This keeps `EmailChannel` purely real but requires profile-specific beans.
2. **Property-driven:** `EmailProperties.transport()` has allowed values `ses` (real SES) and `fake` (capturing fake). `EmailChannel` injects a transport strategy selected by the property. This is more explicit and lets the same profile use either transport by flipping config.

Either way, specify the exact profile and/or property value that activates the fake in tests/local, and state that the default in `prod`/`staging`/`dev` is real SES.

---

## Finding 2 · How the fake transport is observed by tests is not specified

**Severity:** High

**Evidence:**
- AC3 requires that sent messages be observable to tests, but the brief does not describe the capture mechanism.
- A static in-memory list is the simplest approach but leaks state across tests and is not thread-safe.

**Recommended brief amendment:**
Specify a Spring bean (e.g., `FakeEmailTransport` or `CapturedEmailMessages`) that is injected into the fake transport and provides thread-safe, test-accessible methods such as `clear()`, `sentMessages()`, and `findByRecipient(String)`. Require it to be reset in a `@BeforeEach` to avoid ordering dependencies. Document the thread-safety requirement because `EmailChannel` is a singleton called concurrently from Kafka listener threads.

---

## Finding 3 · How the real SES mode is unit-tested without calling AWS is not specified

**Severity:** Medium

**Evidence:**
- AC2 says `send` calls AWS SES v2 with the correct sender/destination/subject/body.
- AC6 forbids hardcoding credentials or region, so a real client cannot be constructed in a unit test without environment setup.
- The brief says "real-transport mode invokes the SES v2 client with correct … (mocked client, unit-level)" but does not say how the mocked client is injected.

**Recommended brief amendment:**
Introduce a transport abstraction (e.g., `EmailTransport` interface with `send(EmailMessage)`). The production implementation wraps `SesV2Client`; the fake implementation captures. `EmailChannel` injects the `EmailTransport` bean and is unaware whether it is real or fake. Unit tests for real-mode behavior then mock the `EmailTransport` interface and assert that `EmailChannel` builds the correct `EmailMessage`. This also solves Finding #1 and #2 with a single seam.

If the project prefers to inject `SesV2Client` directly, specify a `@Primary` test bean or constructor seam so unit tests can supply a mocked `SesV2Client` without loading AWS credentials.

---

## Finding 4 · Exception sanitization is not specified

**Severity:** Medium

**Evidence:**
- AC4 says transport failures propagate unmodified to `DeliveryOrchestrator`.
- AC5 says no raw secret/token appears in any exception message or log line this class constructs.
- AWS SDK exceptions can include the request payload (subject/body containing a reset token) in the exception message or `toString()`.

**Recommended brief amendment:**
Clarify whether `EmailChannel` must sanitize exceptions before re-throwing. If AC5 applies only to messages the class itself constructs, state that AWS exceptions may leak and are `DeliveryOrchestrator`'s responsibility to redact when persisted (which it already does via `SecretSafeLogging.redact`). If AC5 applies to any exception escaping the class, specify that `EmailChannel` must catch `SesV2Exception`, extract a safe message (e.g., `errorCode()` + `awsErrorDetails().errorMessage()` without the raw payload), and re-throw a generic transport exception.

---

## Finding 5 · Validation of required fields is not specified

**Severity:** Medium

**Evidence:**
- `send(UUID accountUuid, String recipient, RenderedMessage message)` receives a resolved email and a rendered message.
- The brief does not say what happens if `recipient` is null/blank or if `message.subject()` is null (EMAIL templates always have a subject, but the type system allows null).
- `EmailProperties.from()` is `@NotBlank` but not validated as an email address.

**Recommended brief amendment:**
Specify validation behavior:
- If `recipient` is null/blank, throw `IllegalArgumentException` (caught by `DeliveryOrchestrator` and recorded as `FAILED`).
- If `message.subject()` is null, throw `IllegalArgumentException` because an email cannot be sent without a subject.
- `message.body()` may be non-null by contract; assert or document.
- Optionally add `@Email` validation to `EmailProperties.from()` (this is a T03 file change; note if it is in scope).

---

## Finding 6 · HTML vs plain-text email content is not specified

**Severity:** Medium

**Evidence:**
- `RenderedMessage.body()` is plain text (templates are text).
- SES v2 supports both `Text` and `Html` bodies.
- The brief does not say which to send.

**Recommended brief amendment:**
State that `EmailChannel` sends a plain-text email (`SendEmailRequest.content().simple().body().text()`) at launch. If future templates become HTML, a new task will add an HTML body or switch to HTML. This prevents a later developer from guessing.

---

## Finding 7 · Whether to log the SES message ID (and other success metadata) is not specified

**Severity:** Low

**Evidence:**
- A successful SES `sendEmail` returns a `MessageId`, useful for delivery tracking and dispute reconstruction.
- The brief says `EmailChannel` outputs `void` and persists nothing.
- `agents.md` requires structured JSON logs with `trace_id` and forbids logging tokens/secrets.

**Recommended brief amendment:**
Specify whether `EmailChannel` logs the SES `MessageId`, recipient, and account UUID at `INFO`/`DEBUG` on success. If so, require the log line to exclude subject/body/token. This is low priority but useful for operations and matches the observability rules.

---

## Finding 8 · The fake transport's activation in local dev vs. test is not specified

**Severity:** Low

**Evidence:**
- `agents.md` says local dev also uses the capturing fake transport.
- The brief focuses on "tests" but mentions "real (non-test) profiles."
- Without explicit activation rules, a local developer running the default Spring profile might accidentally attempt a real SES call.

**Recommended brief amendment:**
Explicitly list which profiles use the fake: `test` and `local`. In `dev`/`staging`/`prod`, the real SES transport is active. If using the property-driven option from Finding #1, set `themistra.notification.email.transport=fake` in `application-test.properties` and `application-local.properties`.

---

## Finding 9 · Coexistence of real and fake `NotificationChannel` beans is not addressed

**Severity:** Low

**Evidence:**
- `DeliveryOrchestrator` injects `List<NotificationChannel>`.
- If both `EmailChannel` and a fake `FakeEmailChannel` are `@Component` and both return `"EMAIL"`, `DeliveryOrchestrator` would see two EMAIL channels and likely misbehave (the `Collectors.toMap` collector would throw `IllegalStateException` at startup).

**Recommended brief amendment:**
Specify that exactly one bean may expose `channel() = "EMAIL"` in any given profile/property state. If the fake is implemented as a separate `NotificationChannel`, it must replace the real bean via `@Profile`/`@ConditionalOnProperty`, not coexist with it. If the fake is an internal transport strategy inside `EmailChannel`, this issue does not arise.

---

## Finding 10 · Configuration property migration / default values are not specified

**Severity:** Low

**Evidence:**
- `EmailProperties.transport` is new in meaning for T12 but the record already exists from T03.
- `application.properties` files across profiles may need updates to set `transport=fake` in test/local and `transport=ses` elsewhere.
- `agents.md` says startup fails on missing/invalid values in non-local profiles.

**Recommended brief amendment:**
List the required `application-*.properties` changes and the allowed values for `themistra.notification.email.transport`. If the property-driven design is chosen, provide a default value (`ses`) or make it explicit in every non-local profile. If the profile-driven design is chosen, document that `transport` is ignored or reserved for future use.

---

## Summary

The T12 brief correctly scopes the replacement of `NoOpEmailChannel` with a real SES-backed channel and a test-safe capturing fake, and it preserves the important boundaries (no `delivery_log` writes, no changes to `DeliveryOrchestrator` or `TemplateRenderer`). The most consequential gap is **Finding #1**: the real-vs-fake selection mechanism is unspecified, which is the central design decision of this task. **Findings #2 and #3** are closely related — the fake observation mechanism and the unit-test seam for real-mode behavior need to be pinned before implementation. **Findings #4, #5, and #6** are medium-severity behavioral ambiguities (exception sanitization, input validation, HTML vs text). Findings #7–10 are smaller but worth documenting to avoid inconsistent config and bean wiring.
