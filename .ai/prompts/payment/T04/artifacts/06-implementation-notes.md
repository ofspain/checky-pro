# payment · T04 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T04 — Outbox & EventTopics |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | this artifact |

## Files created

- `common/ClockConfig.java` — `Clock.systemUTC()` bean. Not named by the task statement but a
  necessary implied consequence of it: `OutboxPublisher`/`OutboxRelay` both need an injectable
  `Clock`, and none existed anywhere in this service before T04 (Phase 3 finding 1, self-produced
  — see `03-design-challenge.md`).
- `events/OutboxEvent.java` — `@Entity` mapped to the real, immutable `outbox` table
  (`V1__payments_baseline.sql`): `id, aggregate_type, aggregate_id, event_type, payload,
  created_at, published_at`. No `idempotency_key` field — payment's own `outbox` table has no such
  column, unlike crypto-service's. Confirmed by direct grep of the migration before writing this
  class, per the session's standing cross-service precedent-diffing rule.
- `events/OutboxEventRepository.java` — `findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable)`.
  No `findByIdempotencyKey`, for the same reason.
- `events/OutboxPublisher.java` — `publish(aggregateType, aggregateId, eventType, payload)`, 4
  params (no idempotency key param). Javadoc documents the resulting duplicate-publish limitation
  (disclosed, accepted at Phase 4 freeze).
- `events/EventTopics.java` — `TOPIC_BY_AGGREGATE_TYPE` map, verified byte-for-byte against
  `design.md` §4c before writing (R30). Unmapped type throws `IllegalStateException`.
- `events/KafkaProducerConfig.java` — explicit `ProducerFactory<String,String>`/
  `KafkaTemplate<String,String>` beans, mirroring `crypto-service`'s identical precedent, to avoid
  Boot's ambiguous autoconfigured generic-typed template.
- `events/OutboxRelay.java` — `@Scheduled` poll, batch 100, send-then-mark, at-least-once, no
  ShedLock (two replicas racing is an accepted harmless duplicate, not a correctness bug — design
  §4c / Phase 3).

## Files edited

- `PaymentServiceApplication.java` — added `@EnableScheduling` (necessary implied consequence of
  introducing `OutboxRelay`'s `@Scheduled` method; mirrors the T03 precedent for
  `@ConfigurationPropertiesScan`). `@EnableSchedulerLock` is deliberately NOT added here — it
  lands only with the first ShedLock-coordinated job (task 8).
- `application.properties` — added `themistra.payment.outbox.relay-interval-ms` (mirrors
  crypto-service's identical property name/default/env-var convention); corrected a now-stale
  comment that said no `@Entity` existed yet.
- `T01SkeletonRegressionTest.java` — updated the existing `applicationClassIsBareWithStandardMainMethod`
  regression guard's class-level-annotation count from 2 to 3 and added a positive
  `@EnableScheduling` check, to keep it accurate rather than letting it go stale against a known,
  certain consequence of this task (same pattern as T03's own identical update for
  `@ConfigurationPropertiesScan`). This is an update to an **existing** test, not new test
  authorship — `EventTopicsTest.java` has deliberately **not** been written in this phase; that is
  Phase 10's job. (The T03 process mistake — writing new tests during Phase 6 — is not repeated
  here.)

## Verification performed this phase

- `mvn -pl services/payment -am compile` — clean.
- `mvn -pl services/payment -am test` — 68/68 passing (0 new tests; existing suite only).
- Mutation check on the updated T01 regression assertion: commented out `@EnableScheduling`,
  confirmed `T01SkeletonRegressionTest` fails with the expected count mismatch, reverted, confirmed
  68/68 clean again.

## Traceability to frozen brief (`04-frozen-task-brief.md`)

- AC1 (OutboxPublisher persists within caller's transaction) — implemented, not yet unit-tested
  (Phase 10).
- AC2 (OutboxRelay at-least-once, no ShedLock) — implemented as specified.
- AC3 (EventTopics routes all 4 aggregate types, throws on unmapped) — implemented, matches
  design.md verbatim.
- AC4 (Clock injectable, no `new Date()`/`Instant.now()` direct calls in new code) — satisfied;
  `ClockConfig` added.
- AC5–AC7 — satisfied by the KafkaProducerConfig/application.properties additions described above.

No code deviates from the frozen brief. No LOCKED decision was touched.
