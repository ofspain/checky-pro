# payment · T04 · Phase 5 — Implementation Plan

Traces to the frozen brief (`04-frozen-task-brief.md`) only. No file or decision beyond it.

## Files to create

1. `services/payment/src/main/java/com/themistra/payment/common/ClockConfig.java`
2. `services/payment/src/main/java/com/themistra/payment/events/OutboxEvent.java`
3. `services/payment/src/main/java/com/themistra/payment/events/OutboxEventRepository.java`
4. `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java`
5. `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java`
6. `services/payment/src/main/java/com/themistra/payment/events/KafkaProducerConfig.java`
7. `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java`
8. `services/payment/src/test/java/com/themistra/payment/events/EventTopicsTest.java`

## Files to modify

1. `services/payment/src/main/resources/application.properties` — one new property.
2. `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` —
   `@EnableScheduling`.

## Public methods (signatures)

- `ClockConfig.clock()` (`@Bean`) → `Clock`.
- `OutboxEvent.create(String aggregateType, String aggregateId, String eventType, String
  payloadJson, Instant createdAt)` (static factory) → `OutboxEvent`; `markPublished(Instant)`,
  `isPublished()`; plain getters for all 7 columns.
- `OutboxEventRepository extends JpaRepository<OutboxEvent, Long>`:
  `findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable)`.
- `OutboxPublisher.publish(String aggregateType, String aggregateId, String eventType, Object
  payload)` → `void`.
- `EventTopics.forAggregateType(String aggregateType)` → `String` (static).
- `KafkaProducerConfig.outboxProducerFactory(String bootstrapServers)` (`@Bean`) →
  `ProducerFactory<String,String>`; `outboxKafkaTemplate(ProducerFactory<String,String>)`
  (`@Bean`) → `KafkaTemplate<String,String>`.
- `OutboxRelay.relay()` (`@Scheduled`) → `void`.
- `PaymentServiceApplication.main(String[])` — unchanged signature, only the class annotation
  list changes.

## Private methods

- `OutboxRelay.relayOne(OutboxEvent)` — per-row send/mark/error-handling, mirrors crypto's own
  exactly (send-then-mark ordering, scoped exception handling per failure category).

## Entities used

- `OutboxEvent` (new, this task's own — the first entity in the service).

## Repositories used

- `OutboxEventRepository` (new).

## Services used

None beyond what this task itself builds.

## Unit / integration tests required

- `EventTopicsTest` (plain JUnit, no Spring context, no Docker):
  1. `shouldRouteEachPublishedEventToItsPaymentsTopic` — R30/AC3, parameterized or one assertion
     per mapping (all 4: `invoice`, `payment-seen`, `payment-finalized`, `receipt`).
  2. `shouldThrowForAnUnmappedAggregateType` — AC3's negative case.

## Execution order

1. **`ClockConfig`** — first, since both event-writing classes depend on it; a quick
   `mvn compile` confirms the bean compiles before building on it.
2. **`OutboxEvent`** — the entity, matching the real 7-column table exactly (re-verified against
   `V1__payments_baseline.sql` directly in this phase, not from memory).
3. **`OutboxEventRepository`** — the one query method this task needs.
4. **`OutboxPublisher`** — 4-parameter signature, null/blank guards, the documented
   duplicate-row-limitation Javadoc (frozen brief AC2).
5. **`EventTopics`** — the 4-entry map, verbatim from `design.md` §4c.
6. **`KafkaProducerConfig`** — explicit producer/template beans.
7. **`OutboxRelay`** — depends on all of the above; written last among the production classes.
8. **`application.properties` + `PaymentServiceApplication`** — the two modified files, applied
   together since `@EnableScheduling` and the relay-interval property are two halves of the same
   change.
9. **Compile check** — `mvn -pl services/payment compile` before writing the test.
10. **`EventTopicsTest`** — written last, against the now-real `EventTopics`.
11. **Final check** — `mvn -pl services/payment test` (still Docker-free for this task — no
    Testcontainers dependency is exercised by `EventTopicsTest`, and no other test in the suite
    starts the real Spring context in a way that would trigger `OutboxRelay`'s own scheduled
    execution against a real, possibly-unavailable Kafka broker during the test run).
