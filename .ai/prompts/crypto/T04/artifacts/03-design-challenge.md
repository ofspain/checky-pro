<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) adversarial review for crypto · T04. -->

# crypto · T04 · Phase 3 — Design Challenge Findings

**Scope:** Review `artifacts/02-task-implementation-brief.md` (TIB) against `spec/crypto-service/agents.md`, `spec/crypto-service/design.md` §4a/§4c/§5/§6, `spec/crypto-service/requirements.md` R26, and the auth-service outbox precedent (`services/auth/src/main/java/com/themistra/auth/events/`).

**Directive:** Do not redesign or implement. Surface hidden assumptions, ambiguities, untestable rules, missing edge cases, conflicts with locked decisions or `agents.md`, unstated dependencies, ordering hazards, and contract mismatches. For each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Finding 1 — `OutboxEvent` entity id type is unspecified and risks mismatch with V1's `BIGINT`

**Issue:** The TIB says create `OutboxEvent` (JPA entity) but does not specify the type of its `id` field. `V1__chain_baseline.sql` defines `outbox.id` as `BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, while auth's mirrored `OutboxEvent` uses `UUID id`. A naive port of auth's entity would fail to map to the crypto schema.

**Severity:** High — a wrong id type will cause a runtime `MappingException` or INSERT failure as soon as the entity is used.

**Evidence:**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql:113-114`: `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`.
- `services/auth/src/main/java/com/themistra/auth/events/OutboxEvent.java:23-25`: `UUID id`.
- The TIB Files-to-Create lists `OutboxEvent.java` but gives no field-level guidance.

**Recommended brief amendment:**
- Explicitly require `OutboxEvent.id` to be `Long` (or `long`) and annotated with `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` to match V1's `BIGINT GENERATED ALWAYS AS IDENTITY`.
- Add an AC/test asserting the entity can be persisted and that the generated id is a `Long`.

---

## Finding 2 — No `Clock` bean is listed, but `OutboxRelay` depends on one

**Issue:** The TIB says `OutboxRelay` uses an injected `Clock`, matching auth's fixed-Clock testing convention, but neither Files-to-Create nor Files-to-Modify includes a `Clock` `@Bean`. Without one, Spring cannot wire `OutboxRelay` in the production context.

**Severity:** High — application context will fail to start once `OutboxRelay` is present.

**Evidence:**
- TIB Dependencies: "`java.time.Clock` — injected into `OutboxRelay` for `publishedAt`, per agents.md's fixed-`Clock` testing convention."
- `services/auth/src/main/java/com/themistra/auth/common/SecurityBeansConfig.java:36`: auth provides a `Clock` bean.
- `services/crypto/src/main/java/com/themistra/crypto/` currently contains no `Clock` bean.
- TIB Files-to-Modify lists only `CryptoServiceApplication.java` and `application.properties`.

**Recommended brief amendment:**
- Add a `Clock` bean (e.g., in a new `common/ClockConfig.java` or an existing common config class) to Files-to-Create/Modify.
- Require a test that the production context wires `OutboxRelay` successfully (or add it to the existing integration test scope once a Clock bean exists).

---

## Finding 3 — `KafkaTemplate<String, String>` autoconfiguration assumption conflicts with auth's explicit producer config

**Issue:** The TIB states "`KafkaTemplate<String, String>` — Spring Boot autoconfigures this ... no custom `KafkaTemplate` bean needed." Auth explicitly defines a `KafkaProducerConfig` because "autoconfiguration's generic type resolution ... is version-sensitive." Crypto is on the same Spring Boot version (3.5.4) and imports the same `spring-kafka` starter, so the same generic-type ambiguity may apply.

**Severity:** High — if Spring Boot autoconfigures `KafkaTemplate<Object, Object>` instead of `<String, String>`, constructor injection into `OutboxRelay` fails at startup.

**Evidence:**
- TIB Dependencies: "`KafkaTemplate<String, String>` — Spring Boot autoconfigures this from the standard `spring.kafka.bootstrap-servers` property ... no custom `KafkaTemplate` bean needed."
- `services/auth/src/main/java/com/themistra/auth/events/KafkaProducerConfig.java`: explicit `ProducerFactory<String, String>` and `KafkaTemplate<String, String>` beans.
- `services/auth/src/main/java/com/themistra/auth/events/KafkaProducerConfig.java:16-19`: comment explains why autoconfiguration was avoided.

**Recommended brief amendment:**
- Either verify via a startup test that Spring Boot resolves `KafkaTemplate<String, String>` correctly, or add an explicit `KafkaProducerConfig` (mirroring auth) to Files-to-Create.
- Do not rely on "it should autoconfigure" without an automated startup/integration assertion.

---

## Finding 4 — No local default for `spring.kafka.bootstrap-servers`

**Issue:** The TIB says add `spring.kafka.bootstrap-servers` to `application.properties` but does not specify a local-safe default. For `local` profile development (Docker Compose per `agents.md`), the broker is typically `localhost:9092`. Without a default, the `local` profile will fail to provide a complete config.

**Severity:** Medium — local boot and tests that load the real properties file will be incomplete.

**Evidence:**
- TIB Files-to-Modify: "`services/crypto/src/main/resources/application.properties` — add `spring.kafka.bootstrap-servers` and the relay-interval property."
- `agents.md` §Configuration: "Local dev runs against Docker Compose (Postgres + Kafka) ..."
- No default is proposed in the brief.

**Recommended brief amendment:**
- Add `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` (or the project's standard env-var name) to `application.properties`.
- Note that real environments override via External Secrets / env vars, matching the L13/no-secrets-committed rule.

---

## Finding 5 — Transaction propagation of `OutboxPublisher.publish` is not tested

**Issue:** The TIB's Constraint section emphasizes that `OutboxPublisher.publish` must join the caller's transaction (no `@Transactional` of its own). The Required Tests list a Mockito-based `OutboxPublisherTest` that mocks the repository — a test that cannot verify transaction propagation because there is no real transaction manager.

**Severity:** Medium — a future change could add `@Transactional(propagation = REQUIRES_NEW)` or call `repository.save` in a way that starts a new transaction, breaking the atomic state-change/event-write guarantee without failing any test.

**Evidence:**
- TIB Constraints: "Transaction: `OutboxPublisher.publish(...)` must join the caller's existing transaction (no `@Transactional` of its own that would start a new one) — a dual-write bug otherwise."
- TIB Required Tests: "`OutboxPublisherTest` (Mockito, mocked repository, no DB) ..."
- `services/auth/src/test/java/com/themistra/auth/events/OutboxPublisherTest.java`: also Mockito-only; no transaction test.

**Recommended brief amendment:**
- Add a Testcontainers/Spring integration test (or `@DataJpaTest` with an in-memory or Testcontainers DB) that calls `OutboxPublisher.publish` inside a `@Transactional` service method, forces the transaction to roll back, and asserts that no `outbox` row was persisted.
- This directly tests the "same transaction" requirement in `agents.md` §Events & messaging.

---

## Gap 6 — `aggregateId` as Kafka message key assumes it is always the `watchId`; partition semantics are undocumented

**Issue:** The TIB says the Kafka message is "keyed by `aggregateId`". `design.md` §4c specifies that `chain.tx.*` events use `watchId` as the partition key. If future callers pass a different value as `aggregateId` (e.g., `txHash` or `invoiceUuid`), ordering guarantees per watch will break, and consumers may process events for the same watch out of order.

**Severity:** Medium — correct partition/ordering semantics depend on a convention that is not enforced by code.

**Evidence:**
- TIB Outputs: "A Kafka message on the topic `EventTopics` resolves for that row's `aggregateType`, keyed by `aggregateId`."
- `spec/crypto-service/design.md` §4c (tx-finalized schema): "Partition key = watchId."
- The TIB does not state that `aggregateId` must equal `watchId` for `chain.tx.*` events.

**Recommended brief amendment:**
- Document the convention explicitly: for `chain.tx.*` events, callers MUST pass the `watchId` as `aggregateId` to preserve partition-key semantics.
- Alternatively, add a code-level assertion or dedicated publisher API for transaction events that takes `watchId` explicitly.

---

## Finding 7 — Outbox table has no top-level `schema_version` column despite versioned event schemas

**Issue:** `agents.md` §Events & messaging says "Event schemas live in `contracts/events/`, are versioned, and evolve backward-compatibly only." Crypto's `outbox` table (V1) has no `schema_version` column, and the TIB's `OutboxPublisher` signature does not include a schema version. The version must therefore live only inside the serialized payload. This makes it impossible to query or enforce schema version at the outbox/relay level.

**Severity:** Low-Medium — not a blocker for T04, but a divergence from auth's outbox model and a potential observability/audit gap.

**Evidence:**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql:112-124`: `outbox` columns are `id, aggregate_type, aggregate_id, event_type, idempotency_key, payload, created_at, published_at` — no `schema_version`.
- `services/auth/src/main/java/com/themistra/auth/events/OutboxEvent.java:35-36`: auth has `schemaVersion`.
- `agents.md` §Events & messaging: "Event schemas live in `contracts/events/`, are versioned, and evolve backward-compatibly only."

**Recommended brief amendment:**
- Either (a) accept the payload-embedded versioning explicitly and document that the outbox is schema-version-agnostic, or (b) add `schema_version` to the outbox table and `OutboxPublisher.publish` signature now, before T04 is frozen.
- If (a), add an AC/test asserting the payload contains a `schemaVersion` field for every published event (even if via a throwaway test payload).

---

## Finding 8 — `OutboxRelay` ordering semantics across send failures are undefined

**Issue:** The relay processes a batch ordered by `created_at ASC` and uses synchronous `send().get()`. If event N fails to send (caught, logged, retried next poll), the loop continues to event N+1, which may succeed and be delivered before event N. For `chain.tx.*` events (seen → confirmed → finalized), a downstream consumer could see `confirmed` or `finalized` before `seen` for the same watch.

**Severity:** Medium — correctness depends on whether consumers tolerate out-of-order events or whether ordering is required. The spec does not state an ordering guarantee, but the natural event lifecycle implies one.

**Evidence:**
- `services/auth/src/main/java/com/themistra/auth/events/OutboxRelay.java:41-49`: processes batch in a loop; one failure does not stop the loop.
- `spec/crypto-service/design.md` §4c event schema describes a lifecycle (`seen` → `confirmed` → `finalized`).
- `agents.md` §Events & messaging says consumers dedupe on idempotency key but does not mention ordering.

**Recommended brief amendment:**
- Explicitly state the ordering guarantee: either "events for the same aggregate are delivered in order" (requires the relay to stop the batch on first failure) or "consumers must tolerate out-of-order delivery within a partition" (current behavior).
- If in-order delivery is required, change the relay to break the batch on first send failure and add a test for it.

---

## Finding 9 — V3 migration idempotency is not specified

**Issue:** The TIB says create `V3__crypto_app_outbox_grant.sql` to grant `INSERT, SELECT, UPDATE` on `chain.outbox`, but it does not require the migration to be re-runnable/idempotent. `V2` includes an `IF NOT EXISTS` guard for role creation; `V3` should follow the same discipline so re-running migrations (e.g., local dev, CI) does not fail.

**Severity:** Low — Flyway records applied migrations and normally skips them, but idempotent SQL is still the project's established convention.

**Evidence:**
- TIB Files-to-Create: `V3__crypto_app_outbox_grant.sql`.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql:18-25`: `IF NOT EXISTS` guard for role creation.
- `ChainBaselineMigrationIntegrationTest.v2RoleCreationGuardIsIdempotentUnderARealReRun` verifies V2 idempotency.

**Recommended brief amendment:**
- Require `V3` to use `GRANT ... IF NOT GRANTED` or wrap the grant in a `DO $$ ... IF NOT EXISTS (SELECT ... FROM information_schema.table_privileges ...)` block so it is idempotent under re-run.
- Add a re-run idempotency test for `V3` mirroring the V2 test.

---

## Finding 10 — `OutboxRelay` batch size is not specified

**Issue:** The TIB describes `OutboxRelay` polling and forwarding rows but does not specify the batch/page size. Auth uses `BATCH_SIZE = 100`. If crypto's relay uses a different size, it affects throughput and memory; if it uses no limit, a large backlog could exhaust memory.

**Severity:** Low — functional behavior is correct for any reasonable batch size, but operational behavior depends on it.

**Evidence:**
- TIB Outputs: "Eventually (via `OutboxRelay`, at-least-once, unbounded latency but bounded by the poll interval): a Kafka message ..."
- `services/auth/src/main/java/com/themistra/auth/events/OutboxRelay.java:27`: `private static final int BATCH_SIZE = 100;`.
- TIB does not mention batch size.

**Recommended brief amendment:**
- Specify the batch/page size (e.g., 100, matching auth) and require a test that the relay queries with that page size.

---

## Finding 11 — Duplicate idempotency key handling is not specified

**Issue:** The TIB requires the idempotency key to be `NOT NULL UNIQUE` at the DB layer, which prevents duplicate keys. It does not specify how `OutboxPublisher.publish` should behave when a duplicate key is passed — should it throw, return silently, or be the caller's responsibility? The DB will throw `DataIntegrityViolationException`, but an explicit policy avoids ambiguous caller behavior.

**Severity:** Low-Medium — determines whether callers can safely retry `publish` for the same logical event.

**Evidence:**
- TIB Locked Decisions L5: "`OutboxPublisher`'s idempotency-key parameter is required ... the DB column itself is `NOT NULL UNIQUE`, so an omitted/duplicate key fails loudly at the database layer."
- TIB Constraints: "Null handling: `idempotencyKey`, `aggregateType`, `aggregateId`, `eventType` are all required, non-null parameters."
- No mention of duplicate-key behavior.

**Recommended brief amendment:**
- State the expected behavior: duplicate idempotency key causes a runtime exception (propagated from the DB), and callers must not republish the same key. Alternatively, define an idempotent upsert/skip policy if the same logical event may be retried.

---

## Finding 12 — No null-parameter validation strategy for `OutboxPublisher.publish`

**Issue:** The TIB says the four parameters are required and non-null but does not specify how nulls are rejected. Options include `@NonNull` annotations, explicit `Objects.requireNonNull` checks in `publish`, or relying on the DB `NOT NULL` constraints. Without an explicit strategy, different implementers may handle it differently, and the failure mode (NPE vs. constraint violation) will be inconsistent.

**Severity:** Low — the DB constraint is the backstop, but explicit validation gives a cleaner, earlier failure.

**Evidence:**
- TIB Constraints: "Null handling: `idempotencyKey`, `aggregateType`, `aggregateId`, `eventType` are all required, non-null parameters on `OutboxPublisher.publish(...)` — no silent defaulting."
- No implementation guidance is given.

**Recommended brief amendment:**
- Require explicit null checks (e.g., `Objects.requireNonNull` for each parameter at the top of `publish`) and add tests asserting `NullPointerException` (or a domain exception) for each null parameter.

---

## Summary of requested brief amendments

| # | Amendment | Priority |
|---|-----------|----------|
| 1 | Specify `OutboxEvent.id` as `Long` matching V1's `BIGINT IDENTITY`. | High |
| 2 | Add a `Clock` bean to Files-to-Create/Modify. | High |
| 3 | Verify `KafkaTemplate<String, String>` autoconfiguration or add explicit `KafkaProducerConfig`. | High |
| 4 | Add local default for `spring.kafka.bootstrap-servers`. | Medium |
| 5 | Add a real-transaction test for `OutboxPublisher` propagation. | Medium |
| 6 | Document that `aggregateId` must be `watchId` for `chain.tx.*` events. | Medium |
| 7 | Decide and document schema-version handling (payload-only vs. table column). | Low-Medium |
| 8 | Define ordering guarantee for same-aggregate events. | Medium |
| 9 | Require idempotent `V3` migration SQL. | Low |
| 10 | Specify `OutboxRelay` batch/page size. | Low |
| 11 | Define duplicate idempotency-key behavior. | Low-Medium |
| 12 | Require explicit null-parameter validation in `OutboxPublisher`. | Low |

(End of design challenge review.)
