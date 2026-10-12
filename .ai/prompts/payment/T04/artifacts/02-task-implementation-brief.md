# payment · T04 · Task Implementation Brief (TIB)

## Task

T04 — Outbox & EventTopics.

## Purpose

Give the service its first entity and its outbound Kafka pipeline: `OutboxEvent`/
`OutboxEventRepository` matching the real, already-migrated `outbox` table, `OutboxPublisher` as
the sole sanctioned way to emit a domain event, `OutboxRelay` to deliver it, `EventTopics` to
route it, and `KafkaProducerConfig` to produce it — so every later task that needs to publish
`invoice.created`/`payment.seen`/`payment.finalized`/`receipt.issued` has this infrastructure
ready.

## Scope

**In:**
- `OutboxEvent` (7 columns, no `idempotencyKey` — the real schema, not crypto's literal template).
- `OutboxEventRepository`, `OutboxPublisher` (no idempotency-key parameter), `OutboxRelay`,
  `EventTopics` (4-entry map), `KafkaProducerConfig`.
- `application.properties`: `themistra.payment.outbox.relay-interval-ms`.
- `PaymentServiceApplication`: `@EnableScheduling`.

**Out:**
- Any call site that actually publishes one of the four events — those land in tasks 6, 8, 10,
  19 (whichever task first reaches that state transition).
- A Testcontainers-backed "event reaches real Kafka" integration test (Phase 1's own resolved
  decision — not in this task's named scope).
- Any entity/repository for `invoices`, `payments`, `verification_records`, `receipts` — those
  are tasks 5, 9, 10, 15 respectively.

## Business Rules

- **R30** — exact aggregate-type-to-topic routing for the four named events.

## Locked Decisions

- **L8** — outbox for every publish, no direct producer call from domain code.
- **L12** — module boundaries (the `events` package owns its own entity).

## Dependencies

- Local Kafka broker (`localhost:9094`, already configured T03).

## Inputs

- `design.md` §4c's own `EventTopics` mapping block.
- `services/crypto/.../events/*.java` (structural template, NOT a literal `OutboxEvent`/
  `OutboxPublisher` signature template — Phase 0's own confirmed schema difference).
- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (the real
  `outbox` table shape to match).

## Outputs

- `services/payment/src/main/java/com/themistra/payment/events/{OutboxEvent,
  OutboxEventRepository,OutboxPublisher,OutboxRelay,EventTopics,KafkaProducerConfig}.java` (new).
- `application.properties` (modified: one new property).
- `PaymentServiceApplication.java` (modified: `@EnableScheduling`).

## State Changes

None beyond what `OutboxPublisher`/`OutboxRelay` do at runtime (not exercised by this task's own
test — no call site exists yet).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/events/OutboxEvent.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxEventRepository.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java`
- `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java`
- `services/payment/src/main/java/com/themistra/payment/events/KafkaProducerConfig.java`
- `services/payment/src/test/java/com/themistra/payment/events/EventTopicsTest.java`

## Files to Modify

- `services/payment/src/main/resources/application.properties` —
  `themistra.payment.outbox.relay-interval-ms=${OUTBOX_RELAY_INTERVAL_MS:2000}` (mirrors crypto's
  own default value and env-var naming exactly).
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` —
  `@EnableScheduling`.

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql` (immutable, T02).
- Any `common`/`common.config` file (T03's own scope).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — `OutboxEvent` maps exactly the real 7-column `outbox` table; no `idempotencyKey`.
- **AC2** — `OutboxPublisher.publish(String aggregateType, String aggregateId, String eventType,
  Object payload)` — note the signature has **4 parameters, not crypto's 5** (no idempotency key)
  — serializes via `ObjectMapper`, saves inside the caller's own transaction, null/blank-guards
  all three string parameters and the payload.
- **AC3** — `EventTopics.forAggregateType("invoice")` → `"payments.invoice.created"`;
  `"payment-seen"` → `"payments.payment.seen"`; `"payment-finalized"` →
  `"payments.payment.finalized"`; `"receipt"` → `"payments.receipt.issued"`; any other input
  throws `IllegalStateException`.
- **AC4** — `OutboxRelay.relay()` polls `findByPublishedAtIsNullOrderByCreatedAtAsc` (batch 100),
  sends each via `KafkaTemplate<String,String>` keyed by `aggregateId`, marks published only
  after a successful send, logs and continues past a single row's `ExecutionException`/
  `TimeoutException`/`InterruptedException`/`DataAccessException` rather than halting the batch.
- **AC5** — `KafkaProducerConfig` declares `ProducerFactory<String,String>` and
  `KafkaTemplate<String,String>` beans explicitly, with `acks=all` and
  `enable.idempotence=true`.
- **AC6** — `application.properties` has the new relay-interval property;
  `PaymentServiceApplication` has `@EnableScheduling`.

## Required Tests

- `EventTopicsTest` (plain JUnit, no Spring context, no Docker): the one named test
  (`shouldRouteEachPublishedEventToItsPaymentsTopic`, R30/AC3 — all four mappings, one assertion
  each or parameterized) plus a negative case (an unmapped aggregate type throws
  `IllegalStateException`).

## Constraints

- **Transaction:** `OutboxPublisher.publish` must never open its own transaction — it joins
  whatever `@Transactional` context the caller already has (default `REQUIRED` propagation via
  Spring's own JPA repository save).
- **Thread-safety:** `EventTopics`'s map is immutable (`Map.of(...)`), inherently thread-safe;
  `OutboxRelay`'s own `@Scheduled` method runs on one thread at a time by Spring's own default
  (no explicit concurrency control needed, no ShedLock per AC4's own stated at-least-once
  tolerance).
- **Null handling:** `OutboxPublisher` throws `NullPointerException`/`IllegalArgumentException`
  on a null/blank required parameter, mirroring crypto's own established guard style.
- **Module boundaries:** `events` package owns `OutboxEvent`; no other module imports it as an
  entity (later tasks call `OutboxPublisher.publish(...)`, never touch `OutboxEvent` directly).

## Open Questions

No blockers.
