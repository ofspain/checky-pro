# notification · T12 · Phase 1 — Specification Extraction

## Business Rules

- **R1.** WHEN an `auth.email.requested` event with purpose `verify_email` is consumed, THEN the
  system SHALL send the recipient an email-verification message containing the verification link.
  T06 already routes/dispatches for this purpose; this task makes the actual *send* real for the
  first time.
- **R2.** WHEN an `auth.email.requested` event with purpose `password_reset` is consumed, THEN the
  system SHALL send the recipient a password-reset message containing the reset link. Same relation
  to T06 as R1.
- **R15.** WHEN rendering a message or writing a log line, THEN the system SHALL NOT include
  secrets, access/refresh tokens, raw password-reset token values beyond the intended one-time
  link, or full API keys. `EmailChannel` doesn't render (T09 already did), but its own exception
  messages/log lines are a fresh surface this rule constrains.

## Locked Decisions

- **L4.** No secrets or tokens in messages or logs — constrains what `EmailChannel.send` may log or
  embed in a thrown exception's own message.
- **L5.** Channels behind one interface — `EmailChannel implements NotificationChannel`;
  `DeliveryOrchestrator` (T11) stays channel-agnostic and requires no change (confirmed at Phase 0:
  its own `List<NotificationChannel>` injection collects any bean whose `channel()` returns
  `"EMAIL"` automatically).
- **L10.** Secrets discipline — no email-transport credential is committed; External Secrets
  Operator injects them; no AWS SDK secret-retrieval in application code. For AWS SES specifically,
  this points to the SDK's own default credential/region provider chain (IRSA on EKS), not an
  application-level credentials property — `agents.md` forbids the alternative.
- **L11.** Module boundaries — `EmailChannel` lives in `channel/`; it must not write to
  `delivery_log` itself (that stays `DeliveryOrchestrator`'s own exclusive responsibility, per T11's
  already-implemented outcome-decision table) and must not import anything from `delivery/`.

## Files involved

**Existing, read-only (interface/contract this task must honour):**
- `channel/NotificationChannel.java` (T11) — the interface.
- `template/TemplateRenderer.java` — specifically the nested `RenderedMessage` record
  (`subject`/`body`/`version`), the third parameter `send` receives.
- `common/config/EmailProperties.java` (T03) — `from`/`transport` fields, already validated,
  already bound in `application.properties`.
- `pom.xml` — `software.amazon.awssdk:sesv2` already declared (O2/Q2 already resolved at T03; not
  re-litigated here).

**Existing, to delete (pre-authorized since T11's own Javadoc):**
- `channel/NoOpEmailChannel.java` — *"replaced (this file deleted, the real implementation added in
  its place) by task 12's own `EmailChannel`"*.
- `channel/NoOpEmailChannelTest.java` (T11) — necessary consequence once its own subject class is
  gone, mirroring T06→T11's identical precedent for `NoOpNotificationDispatcher`.

**Existing, to modify:**
- `T01SkeletonRegressionTest.java` — authorized file-inventory list, per every prior task's own
  unbroken precedent.

**New, this task's own deliverable:**
- `channel/EmailChannel.java` — the real implementation.
- Whichever AWS SDK client wiring `EmailChannel` needs (a `SesV2Client` bean — placement and shape
  are a Phase 2/3 design call, not decided here).
- The **capturing fake transport** `agents.md`/`package.md` both mandate (*"a capturing fake
  transport — no real email is sent in CI"*) — no existing scaffolding anywhere in the tree;
  building it, and deciding how `EmailProperties.transport` selects it vs. the real SES path, is
  this task's own job (Phase 2/3).

## Dependencies

- `NotificationChannel` (interface, T11).
- `TemplateRenderer.RenderedMessage` (T09) — the message type `send` receives, already-rendered,
  already-redaction-safe via its own `toString()` override.
- `EmailProperties` (T03) — `from` (sender address), `transport` (selector value).
- `software.amazon.awssdk:sesv2` — the AWS SES v2 Java SDK, already a `pom.xml` dependency (no new
  dependency expected; if the design needs additional AWS SDK artifacts — e.g. a specific
  credentials-provider module — that is a Phase 3 finding, not assumed here).
- No Kafka/Postgres/JPA dependency of its own — `EmailChannel` is a synchronous, stateless
  collaborator called by `DeliveryOrchestrator`; it persists nothing.

## Acceptance Criteria

1. **AC1.** `EmailChannel implements NotificationChannel`; `channel()` returns `"EMAIL"`.
   `NoOpEmailChannel` and its own test are deleted.
2. **AC2.** In the real (non-test) transport mode, `send` actually calls AWS SES v2 to deliver an
   email — using `EmailProperties.from()` as the sender, `recipient` (the resolved email address,
   per T11's own established `EMAIL`-channel convention) as the destination, and
   `message.subject()`/`message.body()` as the content.
3. **AC3.** In tests, `send` uses a capturing fake instead of ever reaching AWS — no real email is
   sent in CI (`agents.md`). The fake must make sent messages observable to a test (e.g. a queryable
   in-memory list), otherwise no test could assert R1/R2 are actually satisfied end-to-end.
4. **AC4.** `send` does not itself catch-and-swallow a real transport failure; it lets the exception
   propagate to its own caller. `DeliveryOrchestrator` (T11, already implemented, not touched by
   this task) already wraps every channel's own `send` call in a try/catch that converts a thrown
   exception into a `FAILED` `delivery_log` row — `EmailChannel` must not duplicate or bypass that
   outcome-recording responsibility (L11).
5. **AC5.** No raw secret, token, or full API key is embedded in any exception message or log line
   `EmailChannel` itself constructs (R15/L4) — mirrors `RenderedMessage.toString()`'s own established
   "safety lives in what gets constructed" precedent (T09), not a downstream filter this class must
   itself apply.
6. **AC6.** No AWS credential or region value is hardcoded or read from an application-level
   property; the SDK's own default provider chain resolves both (L10).

## Tests required

**No `package.md` §8 named test uniquely maps to this task.** R1/R2's own named tests
(`shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
`shouldSendPasswordResetEmailOnAuthEmailRequestedReset`) already exist and pass in
`AuthEventConsumerTest` (T06) — proving routing/dispatch-call correctness only, explicitly *"not an
actual send"* (T06's own frozen brief scope note, reconfirmed at Phase 0). This task makes the
actual send real for the first time; its own tests will need new method names (mirrors T09/T10/T11's
own precedent of adding many tests beyond a small named-test set), not a re-use of those exact two
strings in a different class testing a different concern. This interpretation is carried forward as
a design note, not re-opened as a blocker.

Boundary tests implied by the acceptance criteria above:
- `channel()` returns `"EMAIL"`.
- Real-transport mode: the SES v2 client is invoked with the correct `from`/destination/subject/body
  (a mocked `SesV2Client`, unit-level — mirrors the established "mocked collaborators, no Spring
  context, no Docker" convention).
- Fake-transport mode: `send` captures the message without ever touching a real `SesV2Client` or
  network call.
- A thrown SES exception propagates out of `send` unmodified (not swallowed, not converted to a
  return value) — `DeliveryOrchestrator`'s own existing tests (T11) already prove the *caller* side
  of this contract; this task's own test proves `EmailChannel` itself doesn't break it.
- `from` address comes from `EmailProperties`, not a hardcoded literal (a config-change regression
  test, mirroring established convention elsewhere in this codebase for config-sourced values).
- `T01SkeletonRegressionTest` reflects the new/deleted files.

## Open Questions

No genuine blockers requiring escalation — consistent with every prior task in this pipeline, both
remaining unknowns from Phase 0 resolve via ordinary implementer judgment, not a decision only the
spec's author can make:

- **Capturing-fake-transport mechanism** (Phase 0's own flagged unknown): resolvable as a Phase 3
  design decision (e.g. `EmailProperties.transport` value selects between a real `@Bean` and a fake
  one via `@ConditionalOnProperty`, vs. a test-only `@Primary` override — mirrors this codebase's own
  established `@TestConfiguration`+`@Primary` pattern, e.g. `IdempotencyGuardIntegrationTest`'s
  `FixedClockConfig`). Not escalated — the same class of "how exactly" decision T06/T08/T09/T10/T11
  each resolved internally.
- **AWS region/credential resolution** (Phase 0's own flagged unknown): resolves by convention, not
  by decision — `agents.md`'s own explicit prohibition on AWS SDK secret-retrieval in application
  code, combined with `L10`, means the SDK's default provider chain (IRSA on EKS; local/dev profile
  behavior is a Phase 3 detail) is the only rule-conforming choice. Not a real open question once
  `agents.md` is applied.
