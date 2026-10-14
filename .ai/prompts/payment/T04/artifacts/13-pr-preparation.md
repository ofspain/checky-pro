# payment · T04 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T04: outbox pipeline (OutboxPublisher, OutboxRelay, EventTopics)`

## Commit message

```
payment-service T04: outbox pipeline (OutboxPublisher, OutboxRelay, EventTopics)

Adds the service's first entity and its outbound Kafka pipeline: OutboxEvent/
OutboxEventRepository mapping the real, already-migrated 7-column outbox table
(no idempotencyKey - that migration is immutable, T02, and unlike
crypto-service's own outbox this one has no dedup column at all);
OutboxPublisher as the sole sanctioned way to emit a domain event, 4-parameter
signature, with its own documented duplicate-row limitation disclosed in its
class Javadoc; OutboxRelay to deliver it (batch-100 poll, send-then-mark,
at-least-once, no ShedLock); EventTopics to route it (R30, 4-entry map
verified verbatim against design.md section 4c); KafkaProducerConfig to
produce it (explicit ProducerFactory<String,String>/KafkaTemplate<String,String>,
acks=all, enable.idempotence=true); and ClockConfig (Clock.systemUTC()) so
both event-writing classes have an injectable Clock - a necessary implied
consequence of the task statement that Phase 3's design challenge caught
before any code was written, since no Clock bean existed anywhere in this
service before this task.

Kimi 2.7's independent code review (Phase 8) found a latent OutboxPublisher
transaction-guard risk (accepted, matches crypto-service's own identical
precedent - no change) and a missing source-text guard for the new
relay-interval property (accepted and fixed in Phase 10). Kimi's test review
(Phase 11) then found 7 real coverage gaps against AC2/AC4/AC5/AC6/AC7 and
AC3's own completeness; 6 were fixed - ClockConfigTest, KafkaProducerConfigTest,
OutboxPublisherTest, and OutboxRelayTest added (mirroring crypto-service's own
identical tests, adapted for payment's narrower 4-parameter publish and its
own 4 aggregate types), EventTopicsTest tightened to assert the map is
exactly 4 entries, and the relay-interval property guard tightened to its
full expected value - the 7th (an untested @PrePersist fallback) was left
unactioned, consistent with crypto-service having no equivalent test either.

This is the first task in this pipeline where every Kimi-reviewed phase (3,
8, 11) was run as a genuine, separate handoff and waited on rather than
self-produced - a corrected discipline from this session (T01-T03's own
Phase 3/8/11 artifacts were self-produced, disclosed as such at the time).

Spec: spec/payment-service/tasks.md task 4. Requirement: R30. Locked
decisions: L8 (outbox for every publish, no direct producer call from
domain code), L12 (module boundaries - events package owns OutboxEvent
exclusively).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/events/OutboxEvent.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxEventRepository.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java`
- `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java`
- `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java`
- `services/payment/src/main/java/com/themistra/payment/events/KafkaProducerConfig.java`
- `services/payment/src/main/java/com/themistra/payment/common/ClockConfig.java`
- `services/payment/src/test/java/com/themistra/payment/events/EventTopicsTest.java`
- `services/payment/src/test/java/com/themistra/payment/events/OutboxPublisherTest.java`
- `services/payment/src/test/java/com/themistra/payment/events/OutboxRelayTest.java`
- `services/payment/src/test/java/com/themistra/payment/events/KafkaProducerConfigTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/ClockConfigTest.java`

**Modified**
- `services/payment/src/main/resources/application.properties` (new relay-interval property)
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
  (`@EnableScheduling`)
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`
  (annotation-count guard updated 2→3)
- `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java`
  (one new assertion, Phase 11 Gap 2)

**Process artifacts (not application code)**
- `.ai/prompts/payment/T04/artifacts/00-*.md` through `12-*.md` — the full 14-phase pipeline
  record for this task (this file is `13-*.md`).

## Summary

`payment-service` now has its outbound Kafka pipeline: an `OutboxEvent` entity mapping the real,
immutable `outbox` table; `OutboxPublisher` as the only sanctioned write path; `OutboxRelay`
delivering at-least-once; `EventTopics` routing all four of this service's own future domain
events (`invoice.created`, `payment.seen`, `payment.finalized`, `receipt.issued`) to their
correct topics; and the supporting `KafkaProducerConfig`/`ClockConfig` infrastructure. No call
site publishes an event yet — that lands in tasks 6, 8, 10, and 19, each owning its own feature.

## Testing performed

- `mvn -pl services/payment -am compile` — clean throughout.
- 17 new tests added this task across 5 files (`EventTopicsTest` grew from 7→8,
  `ApplicationPropertiesContentTest` from 7→8, plus 3 wholly new files:
  `OutboxPublisherTest` (9), `OutboxRelayTest` (13), `KafkaProducerConfigTest` (2),
  `ClockConfigTest` (1)).
- **85/85 non-Docker-dependent tests passing** (`mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest'`), up from 59 before this task's Phase 11
  additions. `SchemaBaselineMigrationIntegrationTest` (17 tests, T02, unaffected by this task)
  was not run this session — Docker unavailable in this environment — but was last confirmed
  green in Phase 6 (full 68/68 suite, Docker up).
- Mutation tests across Phases 6 and 11 on: the `T01SkeletonRegressionTest` annotation count,
  the `EventTopics` mapping (twice — once pre-Gap-1, once via the new exact-map test),
  the `outboxRelayIntervalIsConfigured` property value, `OutboxRelay.BATCH_SIZE`,
  `KafkaProducerConfig`'s `acks` setting, `ClockConfig`'s zone, and `OutboxPublisher`'s
  `createdAt` wiring — all seven caught their introduced defect, all seven reverted clean.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 4 — "Outbox & EventTopics."
- **Requirement IDs:** R30 (aggregate-type-to-topic routing).
- **LOCKED decisions:** L8 (outbox for every publish), L12 (module boundaries).

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T04.**
