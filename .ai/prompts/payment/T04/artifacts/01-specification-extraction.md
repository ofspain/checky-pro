# payment · T04 · Phase 1 — Specification Extraction

## Business Rules

- **R30** — when `invoice.created`, `payment.seen`, `payment.finalized`, or `receipt.issued` is
  emitted, `EventTopics` SHALL route it to `payments.invoice.created`, `payments.payment.seen`,
  `payments.payment.finalized`, and `payments.receipt.issued` respectively.

## Locked Decisions

- **L8** — outbox for every publish: these four events are written to the outbox in the same
  transaction as the state change and relayed to Kafka; no direct Kafka producer call from
  domain code.
- **L12** — module boundaries: the new `events` package owns its own entity/repository; no other
  module's entity is imported.

## Files involved

**New:**
- `services/payment/src/main/java/com/themistra/payment/events/OutboxEvent.java` — matches the
  real, already-migrated `outbox` table exactly (7 columns, **no `idempotency_key`** — Phase 0's
  own confirmed finding; crypto's own `OutboxEvent` is the wrong literal template on this one
  point).
- `services/payment/src/main/java/com/themistra/payment/events/OutboxEventRepository.java`.
- `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java` — `publish`
  takes **no idempotency-key parameter** (Phase 0's own finding).
- `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java`.
- `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java` — the exact
  4-entry map from `design.md` §4c (`invoice`, `payment-seen`, `payment-finalized`, `receipt`).
- `services/payment/src/main/java/com/themistra/payment/events/KafkaProducerConfig.java`.

**Existing, to modify:**
- `services/payment/src/main/resources/application.properties` — gains
  `themistra.payment.outbox.relay-interval-ms` (Phase 0's own flagged gap, resolved here by
  precedent: added in this task since `OutboxRelay` is built here, mirroring the
  "config lands in the task that introduces what it configures" discipline already established
  for ShedLock/jackson-yaml in T01).
- `PaymentServiceApplication.java` — gains `@EnableScheduling` (needed for `OutboxRelay`'s own
  `@Scheduled` method; T01's own Javadoc already pre-announced this exact task).

## Dependencies

- Local Kafka broker (`localhost:9094`, already configured in T03's own `application.properties`).
- No entity from any other module — this is the first entity in the service.

## Acceptance Criteria

- **AC1** — `OutboxEvent` maps exactly the real `outbox` table (7 columns); no
  `idempotencyKey` field.
- **AC2** — `OutboxPublisher.publish(aggregateType, aggregateId, eventType, payload)` serializes
  and saves inside the caller's own transaction (no `@Transactional` of its own); null/blank
  guards on the three string parameters and the payload.
- **AC3** — `EventTopics.forAggregateType(...)` returns exactly: `invoice` →
  `payments.invoice.created`, `payment-seen` → `payments.payment.seen`, `payment-finalized` →
  `payments.payment.finalized`, `receipt` → `payments.receipt.issued`; an unmapped type throws
  (fails loudly, never silently drops or guesses).
- **AC4** — `OutboxRelay` polls unpublished rows (batch of 100, oldest first), sends via
  `KafkaTemplate<String,String>` keyed by `aggregateId`, marks published only after a successful
  send, continues past a single row's failure, no ShedLock (at-least-once is acceptable since
  every future consumer must dedupe on its own terms — this service's own publish side has no
  idempotency key to dedupe on, by design, Phase 0's own finding).
- **AC5** — `KafkaProducerConfig` declares an explicit `ProducerFactory`/
  `KafkaTemplate<String,String>`, not Boot's ambiguous autoconfigured template.
- **AC6** — `PaymentServiceApplication` gains `@EnableScheduling`;
  `themistra.payment.outbox.relay-interval-ms` is a new, validated-or-defaulted config key.

## Tests required

- `com.themistra.payment.events.EventTopicsTest` (plain JUnit, no Spring context, no Docker) —
  the one test the task statement itself names:
  `shouldRouteEachPublishedEventToItsPaymentsTopic` (R30, AC3), plus the negative case (an
  unmapped aggregate type throws).
- Per Phase 0's own flagged question, resolved here: **no Testcontainers-backed
  "event actually reaches Kafka" integration test is added in this task** — task 4's own text
  names exactly one test (the routing unit test), and the "don't front-load beyond what the task
  names" discipline already established this session (T01/T02/T03) applies here too. If this
  needs stronger proof, that is a later task's own, separately-reviewed addition, not silently
  assumed into this one.

## Open Questions

None that block this task. Both items Phase 0 flagged are resolved above by direct precedent
citation: the relay-interval config key is added in this task (not deferred), and the
Testcontainers-integration-test question is resolved to "not in this task's own scope," matching
the task statement's own literal, narrow text.
