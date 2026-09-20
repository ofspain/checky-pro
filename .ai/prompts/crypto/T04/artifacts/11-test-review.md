<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T04. -->

# crypto · T04 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite against the frozen brief's acceptance criteria, named tests, and `spec/crypto-service/agents.md`/`design.md` to identify coverage gaps, weak assertions, false positives, flakiness, and missing edge cases.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — Duplicate idempotency-key behavior is not exercised

**Why it matters:** `OutboxPublisher` documents that a duplicate key propagates as `DataIntegrityViolationException` and is not caught. If a future change silently catches/swallows that exception or converts it to a no-op, the platform's exactly-once publish semantics would break without any test failing.

**Suggested test:** Add a test in `OutboxPublisherTest` (or `OutboxTransactionIntegrationTest`) that calls `publish` twice with the same idempotency key and asserts the second call throws `DataIntegrityViolationException` (or the underlying `DuplicateKeyException`/`ConstraintViolationException`). This makes the documented behavior enforceable.

---

## Gap 2 — `JsonProcessingException` path in `OutboxPublisher` is not tested

**Why it matters:** `OutboxPublisher.publish` has a dedicated catch block that wraps `JsonProcessingException` in `IllegalStateException`. A regression that removed or weakened this catch (e.g., letting the checked exception escape) would break the method signature contract and fail callers unexpectedly.

**Suggested test:** Add a test that passes an unserializable payload (e.g., an `ObjectMapper` configured to fail on empty beans, or a self-referencing object) and asserts `IllegalStateException` with the expected message about the aggregate/event type.

---

## Gap 3 — Blank/empty string parameters are not validated

**Why it matters:** The suite tests `Objects.requireNonNull` for each parameter, but `OutboxPublisher` does not reject blank strings. A blank `idempotencyKey` would satisfy the Java null check but would be persisted as an empty string, potentially defeating the unique constraint and the deterministic-key requirement (L5).

**Suggested test:** Add tests asserting that blank `aggregateType`, `aggregateId`, `eventType`, or `idempotencyKey` values are rejected. If the implementation does not reject blanks, first add validation and then test it.

---

## Gap 4 — `OutboxRelay` is never exercised with multiple events or partial batch failure

**Why it matters:** The relay processes a batch of up to 100 events in a loop. The existing tests use a single event per batch. A bug in shared mutable state, loop exception handling, or batch processing would not be caught. The brief also documents "no cross-event ordering guarantee" when a send fails, but no test proves the loop continues after a failure.

**Suggested test:** Add a test with two events in the batch: the first event's `kafkaTemplate.send(...).get()` fails, the second succeeds. Assert that the second event is marked published and saved, while the first remains unpublished. This documents and enforces the continue-on-failure behavior.

---

## Gap 5 — Only one mapped aggregate type is exercised through `OutboxRelay`

**Why it matters:** `EventTopics` maps five aggregate types to five topics, but `OutboxRelayTest` only tests "tx-seen" and "unmapped-thing". A wiring mistake specific to "tx-confirmed", "tx-finalized", "tx-reorged", or "provider" would not be caught.

**Suggested test:** Parameterize a relay test over all five aggregate types and assert `kafkaTemplate.send` is invoked with the correct topic from `EventTopics.forAggregateType` for each.

---

## Gap 6 — `OutboxTransactionIntegrationTest` only asserts row count, not row content

**Why it matters:** The test proves transaction propagation (row appears on commit, absent on rollback), but if the wrong row were persisted (e.g., wrong aggregate type or idempotency key), the count-based assertion would still pass. This is a weak assertion for an integration test.

**Suggested test:** After `publishThenCommit`, fetch the row and assert its `aggregateType`, `aggregateId`, `eventType`, and `idempotencyKey` match the values passed to `publish`. After `publishThenRollback`, assert the row is absent by idempotency key as well as by count.

---

## Gap 7 — No test verifies `KafkaProducerConfig` producer properties

**Why it matters:** The explicit `KafkaProducerConfig` is a key T04 design decision that avoids the autoconfigured `KafkaTemplate<Object,Object>` ambiguity. If the bean names or properties regress, `OutboxRelay` constructor injection may fail or the producer may lose `acks=all`/idempotence settings.

**Suggested test:** Add a context test (e.g., `@SpringBootTest` with Kafka autoconfiguration excluded, or `ApplicationContextRunner`) that asserts a `KafkaTemplate<String, String>` bean exists, that its producer factory has `acks=all` and `enable.idempotence=true`, and that the autoconfigured `KafkaTemplate<Object,Object>` is not present.

---

## Gap 8 — No test verifies the JPA `ddl-auto=validate` / `open-in-view=false` properties

**Why it matters:** These properties were added in Phase 9 specifically because `OutboxEvent` is the first entity. If they are silently reverted, the service will boot even with an entity-mapping mismatch and `OpenEntityManagerInViewInterceptor` will be enabled by default.

**Suggested test:** Add a fast, non-Spring-context test (mirroring `runtimeFlywayIsDisabledInApplicationProperties`) that loads `application.properties` and asserts `spring.jpa.hibernate.ddl-auto=validate` and `spring.jpa.open-in-view=false`.

---

## Gap 9 — V3 migration idempotency is not explicitly tested

**Why it matters:** `ChainBaselineMigrationIntegrationTest` verifies that re-running all migrations does not throw, but it does not specifically assert that the V3 grant is idempotent (i.e., that the privilege set is unchanged after re-run). A non-idempotent V3 could pass the first run and fail or widen privileges on the second.

**Suggested test:** In `OutboxGrantMigrationIntegrationTest`, after the initial migration, run `Flyway.configure()...migrate()` again and re-assert the same INSERT/UPDATE/DELETE-denied behavior. This proves the migration is safe to re-run.

---

## Gap 10 — `TimeoutException` branch in `OutboxRelay` is not exercised

**Why it matters:** `OutboxRelay.relayOne` now calls `.get(30, TimeUnit.SECONDS)` and catches `TimeoutException`. The manifest acknowledges this is hard to test without a real 30-second wait or an injectable timeout. However, only the `ExecutionException` branch is currently tested; a regression that broke the `TimeoutException` catch (e.g., removing it) would not be caught.

**Suggested test:** Either (a) make `SEND_TIMEOUT_SECONDS` injectable via a constructor/package-private field and test a 0-second timeout, or (b) mock the `CompletableFuture` to throw `TimeoutException` from `.get(...)` and assert the event remains unpublished. Option (b) is a small design concession for testability.

---

## Gap 11 — No test for the `@PrePersist` fallback guard on `createdAt`

**Why it matters:** `OutboxEvent.onCreate()` is documented as a fallback guard that sets `createdAt` only if null. If this guard is accidentally removed or always overwrites `createdAt` with `Instant.now()`, the publisher's explicit Clock-based timestamp would be ignored in some persistence paths.

**Suggested test:** Create an `OutboxEvent` with a fixed `createdAt`, persist it via the repository (in an integration test), and assert the stored `created_at` equals the fixed value, not `Instant.now()`. Alternatively, unit-test `onCreate()` directly by reflection.

---

## Gap 12 — `EventTopicsTest` does not exercise null or blank aggregate types

**Why it matters:** `EventTopics.forAggregateType(null)` will throw `NullPointerException` (not the documented `IllegalStateException`). A caller passing null will get an unexpected exception type. This is a minor boundary-case gap.

**Suggested test:** Add `nullAggregateTypeThrows` and `blankAggregateTypeIsUnmapped` tests to document the expected behavior. If null should throw `IllegalStateException`, update `EventTopics.forAggregateType` to check explicitly.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | Duplicate idempotency key not tested | Silent dedupe regression | Publish same key twice → exception |
| 2 | JsonProcessingException path not tested | Serialization error contract breaks | Unserializable payload → IllegalStateException |
| 3 | Blank string parameters not validated | Empty idempotency/aggregate keys | Blank parameter → validation exception |
| 4 | Multi-event / partial batch failure not tested | Loop/state bugs unseen | Two events: first fails, second succeeds |
| 5 | Only one aggregate type relayed in tests | Wiring mistakes for other types | Parameterized relay test over all 5 types |
| 6 | Transaction test only checks row count | Wrong row could pass | Assert row content after commit |
| 7 | KafkaProducerConfig not tested | Producer bean/property regression | Assert bean exists with acks=all/idempotence |
| 8 | JPA properties not tested | Mapping validation silently disabled | Load properties and assert values |
| 9 | V3 idempotency not explicitly tested | Re-run may widen/fail privileges | Re-run Flyway and re-assert grants |
| 10 | TimeoutException branch not tested | Timeout handling regresses | Mock future throwing TimeoutException |
| 11 | @PrePersist fallback guard not tested | Clock-based timestamp ignored | Persist event with fixed createdAt |
| 12 | Null/blank aggregate types not tested | Unexpected exception type | Null/blank input tests |

(End of test review.)
