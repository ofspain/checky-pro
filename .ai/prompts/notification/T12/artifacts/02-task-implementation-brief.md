# notification · T12 · Phase 2 — Task Implementation Brief

## Task

Implement `EmailChannel` (replacing `NoOpEmailChannel`): a real, non-stub `NotificationChannel` for
`EMAIL` that sends via Amazon SES v2 in real (non-test) profiles and via a capturing fake in tests,
so no real email is ever sent in CI.

## Purpose

The first task making the `EMAIL` delivery path's own final leg real — until now, every message
`DeliveryOrchestrator` (T11) resolved and rendered for the `EMAIL` channel was logged and discarded
by `NoOpEmailChannel`. This task closes that gap for R1/R2 at the actual-send level (T06 already
proved routing/dispatch-call correctness; this task proves an email genuinely goes out).

## Scope

**In:**
- `channel/EmailChannel.java` — `@Component`, `implements NotificationChannel`, `channel()` returns
  `"EMAIL"`.
- Whatever AWS SES v2 client wiring `EmailChannel` needs (e.g. a `SesV2Client` bean) — no
  application-level credential/region property; the SDK's own default provider chain resolves both
  (L10, `agents.md`).
- A capturing fake transport for tests (`agents.md`: *"no real email is sent in CI"*) — makes sent
  messages observable to a test.
- Delete `channel/NoOpEmailChannel.java` and `channel/NoOpEmailChannelTest.java` (pre-authorized
  since T11's own Javadoc).

**Out:**
- Any change to `DeliveryOrchestrator` (T11) — it already resolves any `"EMAIL"`-returning
  `NotificationChannel` bean generically; no change needed for a new implementation to be picked up.
- Any change to `TemplateRenderer`/rendering logic (T09) — `EmailChannel` receives an
  already-rendered `RenderedMessage`, it does not render.
- Writing to `delivery_log` — stays `DeliveryOrchestrator`'s exclusive responsibility (L11);
  `EmailChannel` only sends or throws.
- In-app channel (`InAppChannel`, task 13), retry/dead-letter logic (task 14) — untouched.
- Readiness/health-check wiring for email-transport reachability (`package.md:114`) — not assigned
  to this task number in `tasks.md`; out of scope here.

## Business Rules

- **R1.** `verify_email` messages are actually sent (not merely dispatched/logged) via this channel.
- **R2.** `password_reset` messages are actually sent via this channel.
- **R15.** No secret, token, or full API key appears in any log line or exception message this
  class constructs.

## Locked Decisions

- **L4.** No secrets/tokens in messages or logs.
- **L5.** Channels behind one interface — `EmailChannel implements NotificationChannel`, no other
  coupling to `DeliveryOrchestrator`.
- **L10.** Secrets discipline — no committed/hardcoded AWS credential; SDK default provider chain.
- **L11.** Module boundaries — no import from `delivery/`; no write to `delivery_log`.

## Dependencies

`NotificationChannel` (T11, interface), `TemplateRenderer.RenderedMessage` (T09, the message type),
`EmailProperties` (T03, `from`/`transport`), `software.amazon.awssdk:sesv2` (already a `pom.xml`
dependency).

## Inputs

`send(UUID accountUuid, String recipient, TemplateRenderer.RenderedMessage message)` — `recipient`
is the resolved email address (T11's own established convention for the `EMAIL` channel); `message`
carries `subject()`/`body()`/`version()`, already rendered, already redaction-safe via its own
`toString()`.

## Outputs

None (`void`). Success is a normal return; failure is a thrown exception, left uncaught here.

## State Changes

None — `EmailChannel` persists nothing of its own; it is a stateless, synchronous collaborator.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/channel/EmailChannel.java`
- Whatever new file(s) the SES client bean and the capturing fake require (exact shape is a Phase 5
  implementation-plan decision, not fixed here).

## Files to Modify

- `T01SkeletonRegressionTest.java` — expected, per every prior task's own unbroken precedent.

## Files NOT to Modify

- `channel/NotificationChannel.java`, `delivery/DeliveryOrchestrator.java` (T11) — no change needed.
- `template/TemplateRenderer.java` (T09).
- `common/config/EmailProperties.java` (T03) — read, not modified, unless Phase 3 finds a genuine
  new field is required (not currently expected).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — no cross-service dependency for this task.

## Acceptance Criteria

1. **AC1.** `EmailChannel implements NotificationChannel`; `channel()` returns `"EMAIL"`.
   `NoOpEmailChannel` and its own test are deleted.
2. **AC2.** In the real (non-test) transport mode, `send` calls AWS SES v2 with
   `EmailProperties.from()` as sender, `recipient` as destination, `message.subject()`/`message.body()`
   as content.
3. **AC3.** In tests, `send` uses a capturing fake instead of ever reaching AWS; sent messages are
   observable to a test.
4. **AC4.** `send` never catches-and-swallows a transport failure; it propagates to
   `DeliveryOrchestrator`'s own already-implemented outcome-recording `try/catch` (T11, unchanged).
5. **AC5.** No raw secret/token/API key is embedded in any exception message or log line this class
   constructs.
6. **AC6.** No AWS credential or region is hardcoded or read from an application property.

## Required Tests

No `package.md` §8 named test uniquely maps to this task (R1/R2's own names already exist, proving
routing only, in `AuthEventConsumerTest`, T06). This task's own tests use new method names covering:
`channel()` value; real-transport mode invokes the SES v2 client with correct
sender/destination/subject/body (mocked client, unit-level); fake-transport mode captures without
touching AWS; a thrown SES exception propagates unmodified out of `send`; `from` is sourced from
config, not hardcoded; `T01SkeletonRegressionTest` reflects the file changes.

## Constraints

- **`send` must not swallow exceptions** (AC4) — `DeliveryOrchestrator`'s own outcome table depends
  on seeing the real exception.
- **No new grant migration** — `EmailChannel` touches no database table.
- **No AWS SDK secret-retrieval in application code** (`agents.md`) — default provider chain only.
- **Stateless, thread-safe** — `EmailChannel` is a singleton Spring bean called concurrently across
  Kafka listener container threads (T06's own `concurrency: 2` precedent); it must hold no mutable
  instance state.
- **`DeliveryOrchestrator` (T11) is not modified** — its generic `List<NotificationChannel>`
  injection already picks up any correctly-`@Component`-annotated `"EMAIL"`-returning bean.

## Open Questions

No blockers. Both unknowns carried from Phase 0/1 (the capturing-fake-transport mechanism, AWS
credential/region resolution) resolve via ordinary implementer judgment and `agents.md`'s own
existing rules, respectively — the same class of internally-resolved decision every prior task in
this pipeline has made without escalation.
