# crypto · T04 · Phase 2 — Task Implementation Brief (TIB)

## Task

Outbox & EventTopics. Add `OutboxPublisher` with the deterministic idempotency key (L5) and
`EventTopics` (design §4c). Unit-test routing (R26).

## Purpose

Give crypto-service its transactional-outbox mechanism — the only sanctioned way any future task
(starting with task 17's `chain.tx.*`/`chain.provider.degraded` emission) publishes a fact to Kafka.
Without this task, no event this service ever computes can leave the process. This is the third and
final "Foundation" task before adapter/quorum/watch work begins.

## Scope

**In:**
- `EventTopics` — the verbatim aggregate-type → topic map from design §4c, failing loudly on an
  unmapped type.
- `OutboxPublisher` — appends an `OutboxEvent` row in the caller's own transaction; the idempotency
  key is a **required, explicit parameter** (not silently optional, not constructed internally from
  parts `OutboxPublisher` has no business knowing about — it stays domain-agnostic, mirroring auth's
  own "feature modules own their own payload shapes" design).
- `OutboxEvent` (JPA entity) + `OutboxEventRepository` — required for `OutboxPublisher` to persist
  anything; not named in the task statement but functionally unavoidable (Phase 1 finding).
- `OutboxRelay` — polls unpublished rows and sends to Kafka. **Decision (resolves Phase 1 Open
  Question 1): in scope for this task.** Task 4 is the only outbox-related task in the entire
  29-task list; without a relay, every row this task's own `OutboxPublisher` writes is permanently
  inert. Building it now, mirroring auth's `OutboxRelay` structurally (send-then-mark, at-least-once,
  unroutable-type skip-not-throw), is the only way this task's own deliverable does anything.
- A new Flyway migration granting `crypto_app` write access to `outbox` — **confirmed gap** (Phase 0/1
  finding, verified by direct read of `V2__crypto_app_role_and_grants.sql`): only
  `observations`/`attestations`/`quorum_decisions` are granted; `outbox` has none. Unlike those three
  append-only tables (INSERT+SELECT only, per L3), **`outbox` also needs `UPDATE`** — `OutboxRelay`
  marks a row published by setting `published_at` and saving the already-persisted entity, which JPA
  executes as an `UPDATE`, not a new `INSERT`. Grant must be `INSERT, SELECT, UPDATE`, not the
  three-table precedent's `INSERT, SELECT`.

**Out:**
- The 5 `*Payload` classes under `events/event/` (design §6) — those are task 17's concern, emitted
  only once real facts exist to publish. `OutboxPublisherTest` uses a throwaway local test record for
  its payload, matching auth's own precedent, not a real payload type.
- Any actual `chain.tx.*`/`chain.provider.degraded` emission logic — no caller of `OutboxPublisher`
  exists yet in this service.
- `contracts/events/chain/*` (task 23) — not consumed by this task.
- Any change to `V1__chain_baseline.sql`/`V2__crypto_app_role_and_grants.sql` — immutable once
  merged; the fix is a new `V3`, never an edit to either.

## Business Rules

- **R26.** `EventTopics` SHALL route each of the 5 `chain.*` aggregate types to its corresponding
  Kafka topic.

## Locked Decisions

- **L5.** Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype`.
  `OutboxPublisher`'s idempotency-key parameter is required (no overload without it, no default),
  making the key mechanically unavoidable to omit — the DB column itself is `NOT NULL UNIQUE`, so an
  omitted/duplicate key fails loudly at the database layer as a second line of defense.

Also load-bearing (agents.md "Events & messaging," not restated elsewhere): outbox write and DB write
share one transaction, no direct producer call from domain code; topic naming
`<domain>.<entity>.<event>`; consumers dedupe on the idempotency key; this service's code depends only
on `libs/`/`contracts/` — `services/auth`'s `events/` package is a structural pattern to mirror, never
an import.

## Dependencies

- Jackson `ObjectMapper` (already on the classpath).
- Spring Data JPA (`JpaRepository`), already in use since T02.
- `KafkaTemplate<String, String>` — Spring Boot autoconfigures this from the standard
  `spring.kafka.bootstrap-servers` property (mirrors T03's own decision to use a standard Spring
  property, e.g. `jwk-set-uri`, over a custom `themistra.*` key wherever the framework already
  provides one) — no custom `KafkaTemplate` bean needed.
- `java.time.Clock` — injected into `OutboxRelay` for `publishedAt`, per agents.md's fixed-`Clock`
  testing convention.
- Relay poll interval: a plain `@Value("${themistra.crypto.outbox.relay-interval-ms:2000}")` on the
  `@Scheduled` annotation, matching auth's own single-scalar precedent — **not** a new
  `@ConfigurationProperties` class (that pattern is reserved for the multi-field, cross-validated
  config T03 introduced; one scalar doesn't warrant it).
- No contract dependency — `EventTopics` keys are plain strings, not schema-validated payloads.

## Inputs

- Calls to `OutboxPublisher.publish(aggregateType, aggregateId, eventType, idempotencyKey, payload)`
  from within an already-open transaction (none exist yet in this codebase — this task builds the
  mechanism only).
- `spring.kafka.bootstrap-servers` at runtime; `crypto_app`'s DB credentials (existing, T02).

## Outputs

- A persisted `outbox` row per `publish(...)` call, with a deterministic, unique idempotency key.
- Eventually (via `OutboxRelay`, at-least-once, unbounded latency but bounded by the poll interval): a
  Kafka message on the topic `EventTopics` resolves for that row's `aggregateType`, keyed by
  `aggregateId`.

## State Changes

- New row in `chain.outbox` per publish (via the caller's transaction).
- `outbox.published_at` set (an `UPDATE`) once `OutboxRelay` successfully sends to Kafka.
- New Flyway migration state: `V3` applied, granting `crypto_app` `INSERT, SELECT, UPDATE` on
  `outbox`.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/events/EventTopics.java`
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java`
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java`
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEventRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java`
- `services/crypto/src/main/resources/db/migration/V3__crypto_app_outbox_grant.sql`
- Test classes under `services/crypto/src/test/java/com/themistra/crypto/events/` (see Required
  Tests).

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add `spring.kafka.bootstrap-servers`
  and the relay-interval property.
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
  `@EnableScheduling` (first task needing it, per the class's own "add it when needed" precedent from
  T01/T03).

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql` — immutable once merged.
- `services/crypto/pom.xml` — `spring-kafka`/`org.testcontainers:kafka` already present (T01).
- Anything under `common/`, `common/config/` — T03's deliverable, unrelated to this task.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1–AC5 (R26).** `EventTopics.forAggregateType("tx-seen"|"tx-confirmed"|"tx-finalized"|
  "tx-reorged"|"provider")` returns `"chain.tx.seen"|"chain.tx.confirmed"|"chain.tx.finalized"|
  "chain.tx.reorged"|"chain.provider.degraded"` respectively.
- **AC6 (R26).** An unmapped aggregate type throws (`IllegalStateException`, naming the offending
  type), never silently drops or guesses.
- **AC7 (L5).** `OutboxPublisher.publish(...)` has no overload omitting the idempotency key; the
  persisted row's `idempotency_key` column equals exactly what was passed.
- **AC8 (agents.md).** `OutboxPublisher` never calls a Kafka producer directly — its only side effect
  is a repository save.
- **AC9 (confirmed gap).** `crypto_app` can `INSERT` and `UPDATE` `chain.outbox` over a real TCP
  connection (not a `docker exec` shell — T02's own established verification technique, since local
  Unix-socket/loopback connections bypass real password auth).
- **AC10 (`OutboxRelay`).** A successful send marks the row published (real `Instant`, injected
  `Clock`); a failed send leaves it unpublished for retry; an unroutable aggregate type is skipped
  (logged, not thrown, not saved); an empty batch is a no-op.

## Required Tests

- **`shouldRouteEachChainEventToItsTopic` (package.md §8, → R26).** All 5 mappings, table-driven or
  five explicit assertions.
- Unmapped-aggregate-type test on `EventTopics` — covers AC6.
- `OutboxPublisherTest` (Mockito, mocked repository, no DB) — asserts the saved `OutboxEvent`'s
  fields including the idempotency key; a throwaway local payload record, matching auth's own
  `OutboxPublisherTest` precedent — covers AC7/AC8.
- `OutboxRelayTest` (Mockito, mocked repository + `KafkaTemplate`, fixed `Clock`) — the 4 auth-mirrored
  cases (successful send, failed send, unroutable type, empty batch) — covers AC10.
- A Testcontainers-based migration test (extends/mirrors `ChainBaselineMigrationIntegrationTest`'s
  real-TCP-connection technique) proving `crypto_app` can `INSERT` a row and then `UPDATE` its
  `published_at` — covers AC9. Confirm it still **cannot** `DELETE` (no wider grant than needed).

## Constraints

- **Transaction:** `OutboxPublisher.publish(...)` must join the caller's existing transaction (no
  `@Transactional` of its own that would start a new one) — a dual-write bug otherwise.
- **Thread-safety:** `OutboxRelay`'s scheduled method has no distributed lock (no ShedLock) — deliberate,
  matching auth's own reasoning: at-least-once delivery is safe only because every consumer downstream
  is required to dedupe on the idempotency key (L5) — this is not a bug to fix in this task.
- **Module boundaries:** all new classes under `events/`; no feature-module (`watch`, `attest`, etc.)
  exists yet to conflict with.
- **Null handling:** `idempotencyKey`, `aggregateType`, `aggregateId`, `eventType` are all required,
  non-null parameters on `OutboxPublisher.publish(...)` — no silent defaulting.
- **Security:** no new security surface — `OutboxRelay` and `OutboxPublisher` are internal,
  non-HTTP components; T03's `ResourceServerConfig` is untouched.
- **Money:** not applicable — no monetary values in this task.

## Open Questions

No blockers. (The three items Phase 1 flagged — relay in scope, `OutboxPublisher` signature, config
style — are resolved above as this task's own scoping decisions, not external blockers; `package.md`
§11's Q1–Q8 don't touch this task's area at all.)
