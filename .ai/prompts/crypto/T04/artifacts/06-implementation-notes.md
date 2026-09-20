# crypto · T04 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). Only `src/main` files touched — no tests (Phase 10 scope).

## Files created

- `services/crypto/src/main/resources/db/migration/V3__crypto_app_outbox_grant.sql` — grants
  `crypto_app` `INSERT, SELECT, UPDATE` on `chain.outbox`; no idempotency guard (amendment #9 —
  plain `GRANT` is already idempotent, matching `V2`'s own unguarded `GRANT` lines). Sequence grant
  not needed (already schema-wide from `V2`).
- `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` — `Clock.systemUTC()`
  bean (amendment #2).
- `services/crypto/src/main/java/com/themistra/crypto/events/EventTopics.java` — the design §4c
  `Map.of(...)` literal, copied verbatim.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java` — `@Id` typed `Long`
  with `@GeneratedValue(IDENTITY)` (amendment #1), `idempotencyKey` field mapped to the `NOT NULL
  UNIQUE` column, no `schemaVersion`/`headers` (amendment #7, documented in the class Javadoc).
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEventRepository.java` —
  package-private, mirrors auth's visibility exactly.
- `services/crypto/src/main/java/com/themistra/crypto/events/KafkaProducerConfig.java` — explicit
  `ProducerFactory<String,String>` (`acks=all`, `enable.idempotence=true`) + `KafkaTemplate<String,
  String>` (amendment #3), mirroring `services/auth`'s identical class.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java` —
  `publish(aggregateType, aggregateId, eventType, idempotencyKey, payload)`;
  `Objects.requireNonNull` on all 5 params (amendment #12); Javadoc documents the watchId-as-
  aggregateId convention (amendment #6) and duplicate-key propagation behavior (amendment #11).
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java` — `BATCH_SIZE = 100`
  (amendment #10); send-then-mark; unroutable type logged and skipped; no cross-event ordering
  guarantee, documented in the class Javadoc rather than enforced in code (amendment #8).

## Files modified

- `application.properties` — added `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:
  localhost:9094}` (amendment #4 — corrected port, verified against `services/auth/compose.local.yaml`)
  and `themistra.crypto.outbox.relay-interval-ms=${OUTBOX_RELAY_INTERVAL_MS:2000}`.
- `CryptoServiceApplication.java` — added `@EnableScheduling` (first task needing it); updated its own
  doc comment to explain why `OutboxRelay` still needs no `@EnableSchedulerLock` (it's deliberately
  lock-free per its own class Javadoc, not an oversight).

## Mapping to acceptance criteria

- **AC1–AC6 (R26)** — `EventTopics`'s map + `forAggregateType` throw-on-unmapped behavior, copied
  verbatim from design §4c. Not yet exercised by an automated test (Phase 10); the map's 5 entries
  and the throw path were verified by direct inspection against the design doc's own literal text.
- **AC7/AC8 (L5, agents.md)** — `OutboxPublisher.publish(...)`'s 5-parameter `requireNonNull` chain
  and single `repository.save(...)` call (no producer call anywhere in this class) satisfy both by
  construction; reasoned through the code directly, not yet test-verified.
- **AC9 (confirmed gap)** — `V3` grants `crypto_app` the needed privileges; **not yet verified against
  a real database in this phase** — Docker is unavailable in this environment (confirmed via `docker
  ps`), so the actual grant behavior is reasoned from the SQL text, not empirically proven. Phase 10's
  Testcontainers-based test is what will actually run it.
- **AC10 (`OutboxRelay`)** — `BATCH_SIZE = 100`, send-then-mark ordering, unroutable-skip,
  empty-batch-no-op all present in the code as designed; not yet test-verified (Phase 10).
- **AC11 (transaction join, amendment #5)** — `OutboxPublisher` declares no `@Transactional` of its
  own, so it joins whatever transaction is already open by Spring's default `REQUIRED` propagation.
  Not yet proven by the planned rollback test (needs Docker, Phase 10).
- **AC12 (`Long` id, amendment #1)** — `OutboxEvent.id` is `Long` with `@GeneratedValue(IDENTITY)`,
  matching `V1`'s `BIGINT GENERATED ALWAYS AS IDENTITY`. Not yet proven against a real Postgres
  instance (Phase 10, needs Docker).

## Verification performed this phase

- `mvn -pl services/crypto -am compile` — clean, 15 source files (up from 8 after T03), no errors.
- `mvn -pl services/crypto -am test-compile` — clean; confirms T01/T02/T03's existing test sources
  still compile against this task's changes.
- Docker unavailable in this environment (same limitation as T03) — none of AC9/AC11/AC12, all of
  which need a real Postgres instance, could be empirically verified this phase. This is a real
  limitation, not hidden: **Phase 10's test suite is what will actually prove these**, same posture as
  T03's own implementation notes.

## Deviations from the Phase 5 plan (flagged, not hidden)

None. Every file created/modified matches the Phase 5 plan and the frozen brief's Files
Create/Modify/NOT-Modify lists exactly — no file outside that scope was touched, and no class shape
diverges from what Phase 5 sketched.
