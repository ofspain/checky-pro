# payment · T04 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T04/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `services/payment/src/test/java/com/themistra/payment/events/EventTopicsTest.java`, `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java`  
Scope: T04 only. Recommendations only — no rewrites.

---

### Verification baseline

- `mvn -pl services/payment test` passes — **76/76 green**.
- `EventTopicsTest` covers the named test `shouldRouteEachPublishedEventToItsPaymentsTopic` (R30) plus three boundary cases.
- `ApplicationPropertiesContentTest.outboxRelayIntervalIsConfigured` closes the Phase 8 source-text gap.

---

### Gap 1 — No guard against extra or altered `EventTopics` mappings

- **Why it matters:** The parameterized test only proves the four *known* mappings are correct. If a future edit adds a fifth aggregate type, changes a topic value, or swaps keys, the existing tests stay green because they never assert that the map is *exactly* the four entries in `design.md` §4c.
- **Suggested test:** Add a test that reflects on `EventTopics.TOPIC_BY_AGGREGATE_TYPE` (package-private static field) or source-scans the class and asserts the key set is exactly `{invoice, payment-seen, payment-finalized, receipt}` and the values match the four expected topic strings. This makes any unauthorized mapping change fail CI.

---

### Gap 2 — `outboxRelayIntervalIsConfigured` checks presence but not shape

- **Why it matters:** The current assertion (`contains("themistra.payment.outbox.relay-interval-ms=")`) would pass if the line were `themistra.payment.outbox.relay-interval-ms=99999`, had no env-var override, or were commented out on a later line. AC6 expects `${OUTBOX_RELAY_INTERVAL_MS:2000}`.
- **Suggested test:** Assert the property line contains the exact substring `themistra.payment.outbox.relay-interval-ms=${OUTBOX_RELAY_INTERVAL_MS:2000}` (or split by `=` and verify both the placeholder and the default).

---

### Gap 3 — `ClockConfig` has no test

- **Why it matters:** AC7 requires a single `Clock.systemUTC()` bean. The file is new production code with no regression guard; it could be deleted or changed to a different zone/implementation without any test failing.
- **Suggested test:** Add `ClockConfigTest` using `ApplicationContextRunner` with `ClockConfig` and asserting `context.getBean(Clock.class)` exists and `clock.getZone() == ZoneOffset.UTC` (or that `clock.instant()` is within a second of `Instant.now()`).

---

### Gap 4 — `KafkaProducerConfig` has no test

- **Why it matters:** AC5 is an explicit acceptance criterion: explicit `ProducerFactory<String,String>` and `KafkaTemplate<String,String>` with `acks=all` and `enable.idempotence=true`. Without a test, a regression that removes `acks=all`, disables idempotence, or changes the generic type back to `Object` compiles cleanly and passes the suite.
- **Suggested test:** Add `KafkaProducerConfigTest` (mirrors `services/crypto/.../KafkaProducerConfigTest`): use `ApplicationContextRunner` with `KafkaAutoConfiguration` and `KafkaProducerConfig`, assert the `ProducerFactory` is a `DefaultKafkaProducerFactory`, and assert `ProducerConfig.ACKS_CONFIG == "all"`, `ENABLE_IDEMPOTENCE_CONFIG == true`, and a single `KafkaTemplate` bean exists.

---

### Gap 5 — `OutboxPublisher` guard/serialization behavior is untested

- **Why it matters:** AC2 requires null/blank guards on `aggregateType`, `aggregateId`, `eventType` and serialization via `ObjectMapper`. A future refactor could drop a guard, change the exception type, or break the `createdAt` wiring, and no test would fail because `OutboxPublisher` currently has zero tests.
- **Suggested test:** Add `OutboxPublisherTest` with Mockito-injected `OutboxEventRepository`, a fixed `Clock`, and cases for: happy path (serializes payload, calls `repository.save` with correct fields and `createdAt`), null/blank param guards, and an unserializable payload producing `IllegalStateException` with a `JsonProcessingException` cause.

---

### Gap 6 — `OutboxRelay` polling and send semantics are untested

- **Why it matters:** AC4 describes batch-100 polling, Kafka send keyed by `aggregateId`, mark-published-after-success, and continue-on-failure. These paths are all in `OutboxRelay.relayOne`, which currently has no unit tests. A bug in batch size, key selection, ordering, or failure handling would not be caught.
- **Suggested test:** Add `OutboxRelayTest` with mocked `OutboxEventRepository` and `KafkaTemplate` (mirrors `services/crypto/.../OutboxRelayTest`): verify successful send marks published and saves; failed send leaves unpublished; timeout/interrupted paths restore state; one failure does not block the rest of the batch; batch size is 100; unroutable aggregate type is skipped; empty batch is a no-op.

---

### Gap 7 — `OutboxEvent` fallback `@PrePersist` path is not exercised

- **Why it matters:** The `@PrePersist` method uses `Instant.now()` rather than the injected `Clock`. The normal path (`OutboxPublisher`) always sets `createdAt`, so this only fires if a future call site bypasses the factory method. If that happens, tests using a fixed `Clock` would observe non-deterministic timestamps, violating the agents.md convention to use an injectable `Clock`.
- **Suggested test:** Either add a test that constructs an `OutboxEvent` via reflection/default constructor, triggers `@PrePersist`, and asserts `createdAt` is set (accepting `Instant.now()` as fallback behavior), or remove the fallback and enforce that all persistence goes through `OutboxEvent.create`.

---

### Summary

The generated tests correctly cover R30 / AC3 and close the AC6 source-text gap identified in Phase 8. The remaining gaps are against AC2, AC4, AC5, AC7, and the completeness of AC6/AC3. Priorities: **Gap 1 and Gap 2** are cheap strengthenings of existing tests; **Gaps 4–6** are the largest AC coverage holes; **Gap 3** and **Gap 7** are smaller but worth closing for convention compliance.
