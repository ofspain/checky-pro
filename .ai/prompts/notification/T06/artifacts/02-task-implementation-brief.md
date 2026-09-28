# notification · T06 · Phase 2 — Task Implementation Brief

## Task

Add `AuthEventConsumer` — the first real `@KafkaListener` in this codebase — consuming
`auth.email.requested` and `auth.user.lifecycle`. Per message: dedupe via `IdempotencyGuard`
(T04), refresh the recipient projection via `ContactProjectionUpdater` (T05), then hand off to a
new, minimal seam (`NotificationDispatcher`) that a later task (11/12) implements fully.

## Purpose

Wires the first genuinely end-to-end consumer path in `notification-service`, turning the
already-built T04/T05 mechanisms into something a real Kafka message actually exercises, while
deliberately not building the parts (template rendering, channel dispatch, delivery logging) that
depend on tasks 9/11/12.

## Scope

**In:**
- `consumer/dto/EmailRequestedEvent.java` / `consumer/dto/UserLifecycleEvent.java` — records
  mirroring the real, current contract shape (post `64557d3`/`fbebf1d`):
  `EmailRequestedEvent(UUID accountUuid, String purpose, String token, String email, Instant
  occurredAt)`; `UserLifecycleEvent(UUID accountUuid, String status, String email, String
  eventType, Instant occurredAt)`. Own records, not `auth-service`'s own classes (`agents.md`:
  never depend on another service's source).
- `consumer/NotificationDispatcher.java` — the seam the user explicitly authorized: `void
  dispatch(UUID accountUuid, String notificationKind, Map<String, String> eventData)`.
  `notificationKind` is one of `"verify_email"`, `"password_reset"`, `"user.registered"` — the
  literal left-hand keys of `design.md` §4c's own topic-mapping table, not a resolved template
  *name* (template-name-per-channel resolution needs `PreferenceResolver`/`Template`, neither of
  which exist yet — deliberately left to whichever task implements this interface). `eventData`
  carries whatever raw, event-specific data a future renderer needs (e.g. `token` for the two
  email-requested kinds; empty for `user.registered` — no other data exists to pass).
- `consumer/AuthEventConsumerNoOpDispatcher.java` — a temporary, real (not a stub/TODO)
  `@Component` `NotificationDispatcher` implementation that structurally logs the dispatch call at
  `INFO` and does nothing else, `@ConditionalOnMissingBean` so a later task's own real
  implementation (task 11/12) transparently replaces it without this class needing to change or be
  deleted at exactly the right moment. This is what makes `AuthEventConsumer` itself
  production-ready code today (no dangling unimplemented interface) rather than a half-finished
  feature.
- `consumer/AuthEventConsumer.java` — two `@KafkaListener` methods (one per topic, mirroring how
  `auth-service`'s own producer side treats each topic's payload as an opaque `String`, never a
  type-mapped object): deserialize via Jackson into the matching DTO, call
  `IdempotencyGuard.recordIfNew(eventKey, eventType)` first (event key = the payload's own
  `accountUuid` + the Kafka record's own offset/partition is NOT the key — see Constraints for the
  real key decision), skip all further processing on `false`, then call
  `ContactProjectionUpdater.upsertEmail`, then resolve `notificationKind` and call
  `NotificationDispatcher.dispatch`.
- Kafka consumer configuration in `application.properties` (group id, deserializer) — this task
  establishes the convention for the first time in this codebase.

**Out:**
- `Template`/`TemplateRenderer` (task 9), `DeliveryOrchestrator`/`DeliveryLog` (task 11),
  `NotificationChannel`/`EmailChannel` (task 12), `ChannelPreference`/`PreferenceResolver` (task 8)
  — none built here; `NotificationDispatcher` is the seam these tasks fill in.
- Actually sending any email or recording any delivery attempt — cannot happen until task 12/11
  exist. The 3 named tests (`package.md` §8, all titled "shouldSend...") are satisfied *partially*
  now (proving the consumer resolves the correct `notificationKind` and calls the dispatcher with
  the correct arguments) — not proving an email was sent, since nothing can send one yet.
- `PaymentEventConsumer` — task 7's own scope, a structurally similar but separate consumer.

## Business Rules

R1, R2, R6 (stated in full in Phase 1's own extraction) — partially implemented: this task proves
the *routing* decision (which `notificationKind` a given event resolves to) and the *idempotency*/
*projection-refresh* wiring; the actual "SHALL send" behavior these three requirements describe is
only fully realized once tasks 9, 11, and 12 exist.

## Locked Decisions

- **L1.** Idempotent by event key — `IdempotencyGuard.recordIfNew` called first, unconditionally,
  for every consumed message before any other processing.
- **L2.** Consume-only — no synchronous call to `auth-service` or anywhere else.
- **L5.** Channels behind one interface — not violated; this task makes no channel-dispatch
  decision at all (that's task 11/12's own job, behind the `NotificationDispatcher` seam).

## Dependencies

`spring-kafka` (present since T01, first real use), `IdempotencyGuard` (T04), `ContactProjectionUpdater`
(T05), Jackson `ObjectMapper` (already a transitive dependency via `spring-boot-starter-web`).

## Inputs

`contracts/events/auth/{email-requested,user-lifecycle}.v1.schema.json` (post `64557d3`/`fbebf1d`,
both now carry every field this task needs); `design.md` §4c's own topic-mapping table (the
`notificationKind` values' own literal source).

## Outputs

`AuthEventConsumer.java`, `NotificationDispatcher.java`, `AuthEventConsumerNoOpDispatcher.java`,
`consumer/dto/{EmailRequestedEvent,UserLifecycleEvent}.java`; `application.properties`'s new Kafka
consumer keys.

## State Changes

Real state change (via the mechanisms this task calls, not new state this task itself owns): a row
in `processed_events` per consumed message; `contact_projection` refreshed per message.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/consumer/AuthEventConsumer.java`
- `.../consumer/NotificationDispatcher.java`
- `.../consumer/AuthEventConsumerNoOpDispatcher.java`
- `.../consumer/dto/EmailRequestedEvent.java`
- `.../consumer/dto/UserLifecycleEvent.java`

## Files to Modify

- `services/notification/src/main/resources/application.properties` — add
  `spring.kafka.consumer.group-id`, `spring.kafka.consumer.auto-offset-reset` (and any other key
  Phase 5/6 finds genuinely necessary once the listener configuration is worked out in detail).

## Files NOT to Modify

- `services/notification`'s own T01-T05 files (`pom.xml`, migrations, `ProcessedEvent*`,
  `ContactProjection*`, `ClockConfig`, config records, `ResourceServerConfig`, `PublicEndpoints`).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — the two exceptions already made (both
  event payloads) are their own separate, already-committed, already-verified changes, not part of
  this task's own file list or scope.

## Acceptance Criteria

1. **AC1.** `AuthEventConsumer` has two real `@KafkaListener` methods, one per topic, each
   deserializing into the matching DTO.
2. **AC2.** Every consumed message calls `IdempotencyGuard.recordIfNew` first; a `false` result
   skips all further processing for that message (no projection update, no dispatch call).
3. **AC3.** Every non-duplicate message calls `ContactProjectionUpdater.upsertEmail` with the
   event's own `accountUuid`/`email`/`occurredAt`.
4. **AC4.** `auth.email.requested(verify_email)` resolves to `notificationKind = "verify_email"`;
   `auth.email.requested(password_reset)` resolves to `"password_reset"`;
   `auth.user.lifecycle(eventType = "user.registered")` resolves to `"user.registered"`. Any other
   `eventType` on `auth.user.lifecycle` (`user.suspended`, `user.reinstated`, `user.deleted`,
   `user.locked`, `user.unlocked`) is **not** dispatched — R6 is specifically about registration,
   and `eventType` (not `status`) is now the real, unambiguous signal for that (Phase 1's own
   resolved blocker).
5. **AC5.** `NotificationDispatcher.dispatch` is called with the correct `accountUuid`,
   `notificationKind`, and `eventData` for each of the 3 in-scope cases.
6. **AC6.** `AuthEventConsumerNoOpDispatcher` is a real, working (not placeholder) implementation —
   logs and returns, no exception, no `TODO`.

## Required Tests

Named (`package.md` §8, satisfied partially per Scope's own "Out" note):
`shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
`shouldSendPasswordResetEmailOnAuthEmailRequestedReset`, `shouldWelcomeUserOnUserRegistered` — each
proves routing + dispatch-call correctness, not an actual send. Plus: idempotent redelivery (a
second delivery of the same event calls `IdempotencyGuard` but not `ContactProjectionUpdater`/
`NotificationDispatcher` again); the 5 non-registration `eventType` values on `auth.user.lifecycle`
never call the dispatcher.

## Constraints

- **Idempotency key shape:** the event key `IdempotencyGuard.recordIfNew` uses must be derivable
  from the payload alone (no reliance on Kafka's own offset/partition, which isn't stable across
  redelivery/rebalance) — Phase 5's own job to pin down the exact string format (e.g.
  `accountUuid + ":" + eventType/purpose + ":" + occurredAt`), not decided here.
- **Module boundaries (L11-adjacent):** `NotificationDispatcher` lives in `consumer/`, callable by
  whatever `delivery/`-package class implements it later — no reverse dependency from `consumer/`
  into a not-yet-existing `delivery/` package.
- **Money types / thread-safety:** not applicable.
- **Null handling:** every field this task reads from a deserialized DTO is required by its own
  schema (`additionalProperties: false`, explicit `required` arrays) — no defensive null-handling
  beyond what a failed deserialization already surfaces as a real exception.

## Open Questions

No blockers. Both of Phase 0/1's own real blockers (missing `email`, missing `eventType`) are
resolved via explicit user decisions and real, already-committed, already-tested auth-service
changes (`64557d3`, `fbebf1d`) before this phase began. The seam design (`NotificationDispatcher`)
is itself the user's own explicitly chosen resolution to the "→ templates" scope question.
