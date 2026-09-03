# crypto · T04 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Outbox & EventTopics. Add `OutboxPublisher` with the deterministic idempotency key (L5) and
`EventTopics` (design §4c). Unit-test routing (R26).

## Purpose

Give crypto-service its transactional-outbox mechanism — the only sanctioned way any future task
(starting with task 17's `chain.tx.*`/`chain.provider.degraded` emission) publishes a fact to Kafka.
Without this task, no event this service ever computes can leave the process. Third and final
"Foundation" task before adapter/quorum/watch work begins.

## Scope

**In:**
- `EventTopics` — the verbatim aggregate-type → topic map from design §4c, failing loudly on an
  unmapped type.
- `OutboxPublisher` — appends an `OutboxEvent` row in the caller's own transaction; idempotency key is
  a required, explicit parameter, guarded by `Objects.requireNonNull` on every required parameter
  **[amendment #12]**.
- `OutboxEvent` (JPA entity, `id` typed `Long` with `@GeneratedValue(strategy = IDENTITY)` to match
  V1's `BIGINT GENERATED ALWAYS AS IDENTITY` **[amendment #1]**) + `OutboxEventRepository`.
- `OutboxRelay` — polls unpublished rows (batch size **100**, matching auth **[amendment #10]**) and
  sends to Kafka; in scope per Phase 2's own resolved scoping decision.
- **`KafkaProducerConfig`** — explicit `ProducerFactory<String, String>` + `KafkaTemplate<String,
  String>` beans, mirroring `services/auth`'s own `KafkaProducerConfig` exactly (including
  `acks=all`, `enable.idempotence=true`) — **not** relying on Spring Boot autoconfiguration
  **[amendment #3]**.
- **A `Clock` bean** (new, since crypto has none today) for `OutboxRelay` to inject **[amendment #2]**.
- A new Flyway migration granting `crypto_app` `INSERT, SELECT, UPDATE` on `chain.outbox` (confirmed
  gap — `V2` grants only the three append-only tables). Plain `GRANT` statements need no idempotency
  wrapper, matching `V2`'s own bare `GRANT` lines (27/34) — only `CREATE ROLE` needed the `IF NOT
  EXISTS` guard, and this migration has no `CREATE ROLE` **[amendment #9 — rejected as originally
  proposed; the underlying migration is still in scope, just without the unneeded guard]**.

**Out:**
- The 5 `*Payload` classes under `events/event/` (task 17's concern).
- Any actual `chain.tx.*`/`chain.provider.degraded` emission logic.
- `contracts/events/chain/*` (task 23).
- Any edit to `V1__chain_baseline.sql`/`V2__crypto_app_role_and_grants.sql` — immutable once merged.
- Changing `OutboxRelay`'s continue-past-failure behavior, or adding a typed/dedicated publisher API
  for `chain.tx.*` events — both rejected as scope beyond this task (see Locked Decisions amendments
  #6/#8).
- Adding a `schema_version` column to `outbox` — `V1` is immutable/verbatim per design §4c; not
  available to this task (see amendment #7).

## Business Rules

- **R26.** `EventTopics` SHALL route each of the 5 `chain.*` aggregate types to its corresponding
  Kafka topic.

## Locked Decisions

- **L5.** Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype`.
  `OutboxPublisher`'s idempotency-key parameter is required, non-null-checked
  (`Objects.requireNonNull`, amendment #12), making the key mechanically unavoidable to omit; the DB
  column (`NOT NULL UNIQUE`) is the second line of defense. **A duplicate key propagates as an
  unchecked `DataIntegrityViolationException` from the database — `OutboxPublisher` does not catch,
  dedupe, or silently swallow it; callers must not call `publish` twice with the same logical key
  [amendment #11].**

Also load-bearing (agents.md "Events & messaging"): outbox write and DB write share one transaction,
no direct producer call from domain code; topic naming `<domain>.<entity>.<event>`; consumers dedupe
on the idempotency key; this service's code depends only on `libs/`/`contracts/` — `services/auth`'s
`events/` package is a structural pattern to mirror, never an import.

### Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

1. **`OutboxEvent.id` is `Long`**, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` — matches
   `V1`'s `BIGINT GENERATED ALWAYS AS IDENTITY` exactly (verified: `V1__chain_baseline.sql:114`).
   Auth's own `UUID id` does not apply here; a naive port would fail at persist time.
2. **A `Clock` `@Bean` is added** (crypto has none today — verified by search) so `OutboxRelay`'s
   constructor injection resolves in the real application context, not just in tests.
3. **`KafkaProducerConfig` is added explicitly**, mirroring `services/auth/.../events/KafkaProducerConfig.java`
   verbatim in structure: `ProducerFactory<String, String>` with `acks=all` and
   `enable.idempotence=true`, then `KafkaTemplate<String, String>` built from it. Verified: auth
   deliberately avoids Boot's autoconfigured `KafkaTemplate` because its generic-type resolution
   (`<Object,Object>` vs `<String,String>`) is version-sensitive — the same risk applies to crypto on
   the identical Spring Boot 3.5.4 / `spring-kafka` combination.
4. **Local Kafka bootstrap default is `localhost:9094`** (not the originally-proposed `9092`) —
   verified against `services/auth/compose.local.yaml`: the shared local Kafka container's external
   listener is mapped to host port 9094, matching auth's own
   `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}`. Crypto shares this same
   local broker (same pattern as sharing auth's Postgres container per T02's README).
5. **A real-transaction test is added** for `OutboxPublisher.publish(...)` (e.g. `@DataJpaTest` or
   Testcontainers-backed, calling `publish` inside a `@Transactional` method that then rolls back, and
   asserting no row was persisted) — proves the "joins the caller's transaction, never
   `REQUIRES_NEW`" constraint directly. This exceeds auth's own test precedent (which is Mockito-only
   for this exact class) but is cheap and closes a real regression risk.
6. **Documented (not code-enforced): for `chain.tx.*` events, callers MUST pass `watchId` as
   `aggregateId`** to preserve the Kafka partition-key semantics `design.md` §4c's event-schema
   description requires ("Partition key = watchId"). No code-level assertion or dedicated typed API is
   added — `OutboxPublisher` stays deliberately domain-agnostic (mirrors auth's own stated design
   principle); enforcing a specific aggregate-type's shape inside a generic method would contradict
   that. This is a Javadoc-level contract on `OutboxPublisher`, binding on task 17's future caller.
7. **Documented: schema versioning lives inside the serialized payload only.** `outbox`'s `V1` shape
   (immutable, verbatim per design §4c) has no `schema_version` column, unlike auth's outbox table —
   this is accepted as a deliberate, already-fixed characteristic of this task's schema, not a gap to
   retrofit. `OutboxPublisher.publish(...)` takes no `schemaVersion` parameter as a result.
8. **Documented: no cross-event ordering guarantee beyond attempted-send sequence.**
   `OutboxRelay` continues past a single send failure to the next row in the batch (matches auth's own
   deliberately-reasoned at-least-once behavior — rejected changing this to halt-on-first-failure, see
   Scope/Out). Consumers of `chain.tx.*` events must not assume strict `seen → confirmed → finalized`
   delivery order at the transport level; the idempotency key (L5) guarantees dedupe, not ordering.
9. **V3 migration needs no special idempotency guard.** Kimi's cited precedent (`V2`'s `IF NOT EXISTS`
   block) applies specifically to `CREATE ROLE` (which errors on re-run); `V2`'s own bare `GRANT`
   statements (lines 27, 34) carry no such guard, because `GRANT` is already idempotent in PostgreSQL.
   The new migration (grant-only, no `CREATE ROLE`) follows that same established, unguarded
   convention — rejected as originally proposed; the migration itself remains in scope.
10. **`OutboxRelay.BATCH_SIZE = 100`**, matching auth exactly — no reason to diverge.
11. Duplicate idempotency-key behavior — see Locked Decisions above.
12. **`OutboxPublisher.publish(...)` calls `Objects.requireNonNull(...)` on every required parameter**
    (`aggregateType`, `aggregateId`, `eventType`, `idempotencyKey`) before doing anything else — an
    earlier, clearer failure than waiting for the DB's `NOT NULL` constraint.

## Dependencies

- Jackson `ObjectMapper` (already on the classpath).
- Spring Data JPA (`JpaRepository`), already in use since T02.
- `KafkaTemplate<String, String>` — from the new explicit `KafkaProducerConfig` (amendment #3), backed
  by `spring.kafka.bootstrap-servers` (default `localhost:9094`, amendment #4).
- `java.time.Clock` — new bean (amendment #2), injected into `OutboxRelay`.
- Relay poll interval: `@Value("${themistra.crypto.outbox.relay-interval-ms:2000}")` on the
  `@Scheduled` annotation (single scalar — not a new `@ConfigurationProperties` class).
- No contract dependency.

## Inputs

- Calls to `OutboxPublisher.publish(aggregateType, aggregateId, eventType, idempotencyKey, payload)`
  from within an already-open transaction.
- `spring.kafka.bootstrap-servers` at runtime; `crypto_app`'s DB credentials (existing, T02).

## Outputs

- A persisted `outbox` row per `publish(...)` call, with a deterministic, unique idempotency key.
- Eventually (via `OutboxRelay`, at-least-once, no cross-event ordering guarantee — amendment #8): a
  Kafka message on the topic `EventTopics` resolves for that row's `aggregateType`, keyed by
  `aggregateId` (which callers of `chain.tx.*` events must set to `watchId` — amendment #6).

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
- `services/crypto/src/main/java/com/themistra/crypto/events/KafkaProducerConfig.java` (amendment #3)
- `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` (amendment #2 — Clock
  bean; named to match the "shared plumbing lives only in `common`" rule, L15)
- `services/crypto/src/main/resources/db/migration/V3__crypto_app_outbox_grant.sql`
- Test classes under `services/crypto/src/test/java/com/themistra/crypto/events/`.

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add
  `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}` (amendment #4) and the
  relay-interval property.
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
  `@EnableScheduling`.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql` — immutable once merged.
- `services/crypto/pom.xml` — `spring-kafka`/`org.testcontainers:kafka` already present (T01).
- Anything under `common/config/`, `ResourceServerConfig.java`, `PublicEndpoints.java` — T03's
  deliverable, unrelated. (`common/ClockConfig.java` is the one new `common/` file this task adds.)
- Any file under `spec/`.

## Acceptance Criteria

- **AC1–AC5 (R26).** `EventTopics.forAggregateType(...)` returns the correct topic for each of the 5
  mapped aggregate types.
- **AC6 (R26).** An unmapped aggregate type throws `IllegalStateException` naming the offending type.
- **AC7 (L5).** No overload of `publish(...)` omits the idempotency key; the persisted row's
  `idempotency_key` equals exactly what was passed; a null idempotency key (or any other required
  parameter) throws `NullPointerException` before any repository call (amendment #12).
- **AC8 (agents.md).** `OutboxPublisher` never calls a Kafka producer directly.
- **AC9 (confirmed gap).** `crypto_app` can `INSERT` and `UPDATE` `chain.outbox` over a real TCP
  connection (not `docker exec`).
- **AC10 (`OutboxRelay`).** Successful send marks published (real `Instant` via the new `Clock` bean);
  failed send leaves unpublished; unroutable type is skipped (logged, not thrown, not saved); empty
  batch is a no-op; batch size is 100 (amendment #10).
- **AC11 (amendment #5).** `OutboxPublisher.publish(...)` called inside a transaction that then rolls
  back leaves no `outbox` row persisted — proves it joins the caller's transaction rather than
  starting its own.
- **AC12 (amendment #1).** `OutboxEvent`'s generated id is a `Long`, persists successfully against the
  real `BIGINT IDENTITY` column.

## Required Tests

- **`shouldRouteEachChainEventToItsTopic` (package.md §8, → R26).**
- Unmapped-aggregate-type test on `EventTopics` — AC6.
- `OutboxPublisherTest` (Mockito) — saved fields including idempotency key, AC7/AC8; plus one test per
  null parameter asserting `NullPointerException` (amendment #12).
- `OutboxRelayTest` (Mockito, mocked repository + `KafkaTemplate`, fixed `Clock`) — the 4 auth-mirrored
  cases, AC10.
- A Testcontainers-based migration test (mirrors `ChainBaselineMigrationIntegrationTest`'s real-TCP
  technique) — `crypto_app` can `INSERT` then `UPDATE` `outbox`, still cannot `DELETE` — AC9, AC12.
- A transaction-rollback test (amendment #5) — AC11.

## Constraints

- **Transaction:** `OutboxPublisher.publish(...)` must join the caller's existing transaction — no
  `@Transactional` of its own. Proven by AC11's test, not just asserted.
- **Thread-safety:** `OutboxRelay`'s scheduled method has no distributed lock — deliberate, matches
  auth's at-least-once + downstream-dedupe reasoning (amendment #8).
- **Module boundaries:** all new classes under `events/`, except the one `Clock` bean under `common/`
  (L15 — shared plumbing only in `common`).
- **Null handling:** `Objects.requireNonNull` on every required `publish(...)` parameter (amendment
  #12).
- **Ordering:** no cross-event ordering guarantee beyond attempted-send sequence (amendment #8) —
  documented, not enforced.
- **Security:** no new security surface; T03's `ResourceServerConfig` is untouched.
- **Money:** not applicable.

## Open Questions

No blockers.
