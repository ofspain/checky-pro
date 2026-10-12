STATUS: FROZEN (approved 2026-10-12, Human Approval gate)

# payment · T04 · Frozen Task Brief

## Phase 3 disposition (2 findings ACCEPTED, 2 positive confirmations)

1. **No `Clock` bean exists anywhere in `services/payment`.** Real, compile-blocking gap:
   `OutboxPublisher`/`OutboxRelay` both need one. Added `ClockConfig` to Files to Create below,
   mirroring `crypto-service`'s own exact one-bean template.
2. **No protection against a duplicate `OutboxEvent` row** given the schema's own immutable lack
   of a dedup key. No schema change is possible in this task — documented as a named, visible
   limitation in `OutboxPublisher`'s own class Javadoc instead, so no later call site assumes a
   dedup guarantee this layer doesn't provide.
3. **4-parameter `publish` signature** (no idempotency key) — confirmed correctly justified
   against both the real schema and `agents.md`'s own precise wording. No change.
4. **Deferring the Testcontainers-Kafka integration test** — confirmed consistent with this
   task's own named scope and this session's established discipline. No change.

## Task

T04 — Outbox & EventTopics.

## Purpose

Give the service its first entity and its outbound Kafka pipeline: `OutboxEvent`/
`OutboxEventRepository` matching the real, already-migrated `outbox` table, `OutboxPublisher` as
the sole sanctioned way to emit a domain event (with its known, documented duplicate-row
limitation), `OutboxRelay` to deliver it, `EventTopics` to route it, `KafkaProducerConfig` to
produce it, and `ClockConfig` so both event-writing classes have an injectable `Clock` — so every
later task that needs to publish `invoice.created`/`payment.seen`/`payment.finalized`/
`receipt.issued` has this infrastructure ready.

## Scope

**In:**
- `OutboxEvent` (7 columns, no `idempotencyKey`).
- `OutboxEventRepository`, `OutboxPublisher` (4-parameter signature, documented duplicate-row
  limitation), `OutboxRelay`, `EventTopics` (4-entry map), `KafkaProducerConfig`.
- `ClockConfig` (Phase 3 finding 1).
- `application.properties`: `themistra.payment.outbox.relay-interval-ms`.
- `PaymentServiceApplication`: `@EnableScheduling`.

**Out:**
- Any call site that actually publishes one of the four events — tasks 6, 8, 10, 19.
- A Testcontainers-backed "event reaches real Kafka" integration test.
- Any entity/repository for `invoices`, `payments`, `verification_records`, `receipts`.
- A schema change to add a dedup key to `outbox` (not possible — immutable migration).

## Business Rules

- **R30** — exact aggregate-type-to-topic routing for the four named events.

## Locked Decisions

- **L8** — outbox for every publish, no direct producer call from domain code.
- **L12** — module boundaries (the `events` package owns its own entity).

## Dependencies

- Local Kafka broker (`localhost:9094`, already configured T03).

## Inputs

- `design.md` §4c's own `EventTopics` mapping block.
- `services/crypto/.../events/*.java` and `.../common/ClockConfig.java` (structural templates —
  NOT a literal `OutboxEvent`/`OutboxPublisher` signature template, per Phase 0/3's own confirmed
  schema difference).
- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (the real
  `outbox` table shape to match).

## Outputs

- `services/payment/src/main/java/com/themistra/payment/events/{OutboxEvent,
  OutboxEventRepository,OutboxPublisher,OutboxRelay,EventTopics,KafkaProducerConfig}.java` (new).
- `services/payment/src/main/java/com/themistra/payment/common/ClockConfig.java` (new).
- `application.properties` (modified: one new property).
- `PaymentServiceApplication.java` (modified: `@EnableScheduling`).

## State Changes

None exercised by this task's own test (no call site exists yet).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/events/OutboxEvent.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxEventRepository.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java`
- `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java`
- `services/payment/src/main/java/com/themistra/payment/events/KafkaProducerConfig.java`
- `services/payment/src/main/java/com/themistra/payment/common/ClockConfig.java`
- `services/payment/src/test/java/com/themistra/payment/events/EventTopicsTest.java`

## Files to Modify

- `services/payment/src/main/resources/application.properties` —
  `themistra.payment.outbox.relay-interval-ms=${OUTBOX_RELAY_INTERVAL_MS:2000}`.
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` —
  `@EnableScheduling`.

## Files NOT to Modify

- `V1__payments_baseline.sql`, `V2__payment_app_role_and_grants.sql` (immutable, T02).
- Any other `common`/`common.config` file (T03's own scope).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — `OutboxEvent` maps exactly the real 7-column `outbox` table; no `idempotencyKey`.
- **AC2** — `OutboxPublisher.publish(String aggregateType, String aggregateId, String eventType,
  Object payload)` serializes via `ObjectMapper`, saves inside the caller's own transaction,
  null/blank-guards all three string parameters and the payload. Its own class Javadoc
  explicitly documents the duplicate-row limitation (Phase 3 finding 2) — no schema-level dedup
  exists at this layer.
- **AC3** — `EventTopics.forAggregateType("invoice")` → `"payments.invoice.created"`;
  `"payment-seen"` → `"payments.payment.seen"`; `"payment-finalized"` →
  `"payments.payment.finalized"`; `"receipt"` → `"payments.receipt.issued"`; any other input
  throws `IllegalStateException`.
- **AC4** — `OutboxRelay.relay()` polls `findByPublishedAtIsNullOrderByCreatedAtAsc` (batch 100),
  sends each via `KafkaTemplate<String,String>` keyed by `aggregateId`, marks published only
  after a successful send, logs and continues past a single row's failure.
- **AC5** — `KafkaProducerConfig` declares `ProducerFactory<String,String>` and
  `KafkaTemplate<String,String>` beans explicitly, with `acks=all` and
  `enable.idempotence=true`.
- **AC6** — `application.properties` has the new relay-interval property;
  `PaymentServiceApplication` has `@EnableScheduling`.
- **AC7** (Phase 3 finding 1) — `ClockConfig` exists, a single `Clock.systemUTC()` bean.

## Required Tests

- `EventTopicsTest` (plain JUnit, no Spring context, no Docker): the one named test
  (`shouldRouteEachPublishedEventToItsPaymentsTopic`, R30/AC3 — all four mappings) plus a
  negative case (an unmapped aggregate type throws `IllegalStateException`).

## Constraints

- **Transaction:** `OutboxPublisher.publish` must never open its own transaction.
- **Thread-safety:** `EventTopics`'s map is immutable; `OutboxRelay`'s `@Scheduled` method needs
  no explicit concurrency control (no ShedLock, per AC4's own stated at-least-once tolerance).
- **Null handling:** `OutboxPublisher` throws on a null/blank required parameter.
- **Module boundaries:** `events` package owns `OutboxEvent`; no other module imports it.
- **Known, documented limitation (Phase 3 finding 2):** no duplicate-publish protection at this
  layer — correctness depends on each future call site firing `publish` exactly once per real
  event.

## Open Questions

None. All resolved via the Phase 3 disposition above.
