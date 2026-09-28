# notification · T06 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` is a consume-only Kafka fan-out layer (L2). As of T05, the module has:
schema (T02), config + resource-server wiring (T03), the idempotency ledger (T04:
`consumer/{ProcessedEvent,ProcessedEventRepository,IdempotencyGuard}`), and the recipient-contact
projection (T05: `preference/{ContactProjection,ContactProjectionRepository,ContactProjectionUpdater}`).
**No `@KafkaListener` exists anywhere in this module, or anywhere in this codebase** —
`auth-service`/`crypto-service` are both event *producers* only (via their own outbox pattern);
`spring-kafka` has been on the classpath since T01 but nothing has used its consumer side yet. T06
is the first real Kafka consumer in this entire platform's history.

## 2. Existing code this task touches

**Already exists, directly reusable (not to be duplicated or reworked):**
- `consumer/IdempotencyGuard.java` (T04) — `boolean recordIfNew(String eventKey, String eventType)`.
  This task's own consumer must call this first for every consumed message; a `false` result means
  skip all further processing for that message (already-processed).
- `preference/ContactProjectionUpdater.java` (T05) — `boolean upsertEmail(UUID accountUuid, String
  email, Instant occurredAt)`. Both source events now carry `email` (auth-service commit `64557d3`,
  T05's own real deliverable) — this task's consumer is the *first real caller* of this method.
- `contracts/events/auth/{email-requested,user-lifecycle}.v1.schema.json` — both now include
  `accountUuid`, and (as of `64557d3`) `email`; `email-requested` also has `purpose`/`token`;
  `user-lifecycle` also has `status`. Both have `occurredAt`.
- `services/auth`'s own `{EmailRequestedEventPayload,UserLifecycleEventPayload}Contract Test.java`
  and `AccountServiceTest`'s `ArgumentCaptor`-based assertions — the real, current shape of both
  events, already read and relied upon by this task's own Phase 0 (not re-read here, already
  verified in T05's own Phase 0/2).

**Not yet built, referenced by this task's own text but NOT this task's own scope (later tasks):**
- `template/{Template,TemplateRepository,TemplateRenderer}.java` — **task 9's own deliverable**.
  `Template` (the JPA entity mapping onto T02's already-seeded `templates` table) does not exist yet
  — this task cannot query the `templates` table via JPA.
- `delivery/{DeliveryLog,DeliveryLogRepository,DeliveryOrchestrator,DeliveryRetry}.java` — task 11's
  own deliverable. `DeliveryOrchestrator`'s own described role in `design.md` §6 is "resolve prefs →
  render → dispatch → log" — a component this task cannot call, since it doesn't exist.
- `channel/{NotificationChannel,EmailChannel}.java` — task 12's own deliverable. No email can
  actually be sent by anything in this codebase yet.
- `preference/{ChannelPreference,PreferenceResolver}.java` — task 8's own deliverable.

## 3. Established patterns to follow

**JPA entity/repository/service shape** — T04's `IdempotencyGuard`/T05's `ContactProjectionUpdater`
are the direct in-module precedent for how a small `@Service` calls into another module's own
package-private repository.

**Event payload deserialization** — no precedent exists *anywhere in this codebase* for a real
`@KafkaListener` consuming and deserializing a message. The closest analogues are one-directional:
`services/auth`'s own `OutboxPublisher`/`KafkaProducerConfig` (serializing *out*, via Jackson) and
its own contract tests (constructing a payload record, serializing it, and structurally comparing
against the JSON schema — proving the *shape*, never an actual consume path). `design.md` §6 names
`consumer/dto/` for "deserialization records matching contracts/events/*" — implying this task
should define Java records mirroring `EmailRequestedEventPayload`/`UserLifecycleEventPayload`'s own
field shape (not reuse `auth-service`'s own classes directly — cross-service source dependencies are
forbidden, `agents.md`: "Services depend only on `libs/` and `contracts/` — never on another
service's source").

**Kafka consumer configuration** — no `spring.kafka.consumer.*` properties exist yet in
`application.properties` (only `spring.kafka.bootstrap-servers`, added in T03 with no reader at the
time). No `@KafkaListener`/`ConsumerFactory`/`ContainerFactory` bean exists anywhere in this module.

## 4. Testing conventions

Unit tests: plain JUnit, fixed `Clock` where relevant. Integration: Testcontainers — but this task
is the first to potentially need **both** Postgres *and* Kafka together (T04/T05's own integration
tests only needed Postgres); `agents.md` says "integration tests use Testcontainers (Postgres +
Kafka)" as the general convention, not yet exercised by any test in this module. Named tests
(`package.md` §8): `shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
`shouldSendPasswordResetEmailOnAuthEmailRequestedReset`, `shouldWelcomeUserOnUserRegistered` — all
three literally say "shouldSend...", which is worth flagging now (see below).

## 5. Known gaps / unknowns

**This is the central finding of this phase.** The task statement says `AuthEventConsumer` routes
consumed events "→ the corresponding templates" — but `Template` (task 9), `TemplateRenderer` (task
9), `DeliveryOrchestrator` (task 11), and `EmailChannel` (task 12) **do not exist yet**. This task's
own three named tests (`package.md` §8) are literally titled "shouldSend...Email" — but nothing in
this codebase can send an email until task 12 lands. **I do not know** what T06's own real,
achievable deliverable looks like given this — plausible readings, none decided here:
- T06 defines the consumer + DTOs + calls into `IdempotencyGuard`/`ContactProjectionUpdater`, and
  resolves *which template name* applies per event (a pure in-memory mapping, e.g.
  `purpose == "verify_email" → "email.verify"`) without touching the `templates` table at all (no
  `Template` entity exists to query) — then hands off to some not-yet-fully-specified seam (an
  interface this task defines and a later task implements) rather than an actual send.
- Alternatively, the three named tests are only meant to *fully* pass once task 12 lands, and this
  task's own required tests are a narrower subset (e.g., proving the consumer dedupes and updates
  the projection correctly) — with the "shouldSend..." tests either not written yet, or written
  against a capturing/spy seam that doesn't yet connect to a real send.

Resolving this is **Phase 1/2's own job**, not decided here — but it is the single most consequential
open question this task faces, of the same real, load-bearing kind T05's own Phase 0 surfaced for
the missing `email` field (which required an explicit escalation and a genuine design decision
before Phase 2 could proceed).

**Secondary unknown**: no Kafka consumer configuration (group id, deserializer, error handling,
offset commit strategy) exists anywhere in this module to mirror — this task would be establishing
that convention for the first time in this codebase, not following an existing one.
