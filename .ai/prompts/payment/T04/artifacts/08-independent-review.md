# payment · T04 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T04/artifacts/08-independent-review.md`  
Consumed: Phase 6 implementation, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, `spec/payment-service/agents.md`  
Scope: T04 only. Findings only — no rewrite.

---

### Executive summary

- Build: `mvn -pl services/payment test` passes — **61/61 tests green**, but that count is misleading because the one test this task is required to add is missing.
- Phase 7 findings 1 and 2 are disclosed, precedent-based observations rather than defects; finding 3 confirms an intentional package-private boundary.
- I found three new issues: the required `EventTopicsTest` is absent, there is no source-text guard for the new `application.properties` line, and `OutboxPublisher` has a latent atomicity risk if a future call site forgets `@Transactional`.

---

### 1. Required `EventTopicsTest` is missing

- **Issue:** The frozen brief explicitly lists `EventTopicsTest.java` under Files to Create and Required Tests, and `package.md` §8 names the test (`shouldRouteEachPublishedEventToItsPaymentsTopic`, R30). No such file exists in `services/payment/src/test/java/com/themistra/payment/events/`, so the core AC3 routing behavior has no permanent, CI-enforced regression guard.
- **Evidence:**
  - `artifacts/04-frozen-task-brief.md:92` (Files to Create)
  - `artifacts/04-frozen-task-brief.md:131-134` (Required Tests)
  - Direct filesystem check: `services/payment/src/test/java/com/themistra/payment/events/` does not contain `EventTopicsTest.java`
  - `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java:12-17` (the mapping to test)
- **Recommendation:** Create `services/payment/src/test/java/com/themistra/payment/events/EventTopicsTest.java` with a parameterized test covering all four mappings (`invoice`, `payment-seen`, `payment-finalized`, `receipt`) and a negative test asserting `IllegalStateException` for an unmapped aggregate type. Mirror `services/crypto/src/test/java/com/themistra/crypto/events/EventTopicsTest.java`.
- **Confidence:** High.

---

### 2. No source-text guard for the T04 `application.properties` addition

- **Issue:** `ApplicationPropertiesContentTest` is still T03-scoped; it does not assert that `themistra.payment.outbox.relay-interval-ms` is present. A future edit could drop the line, breaking `@Scheduled(fixedDelayString = ...)` at runtime, without failing any existing test. (The `@EnableScheduling` annotation itself is covered by the updated T01 skeleton test, but the property it reads is not.)
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/common/ApplicationPropertiesContentTest.java`
  - `services/payment/src/main/resources/application.properties:40`
  - `artifacts/04-frozen-task-brief.md:96-99` and `:126` (AC6)
- **Recommendation:** Extend `ApplicationPropertiesContentTest` (or add a dedicated T04 source-text guard) with an assertion that `application.properties` contains `themistra.payment.outbox.relay-interval-ms=`.
- **Confidence:** Medium.

---

### 3. `OutboxPublisher` silently loses the "same transaction" guarantee if a caller forgets `@Transactional`

- **Issue:** `OutboxPublisher.publish` calls `repository.save(...)` directly and never verifies that a transaction is active (e.g., `TransactionSynchronizationManager.isActualTransactionActive()` or `@Transactional(propagation = MANDATORY)`). If a future call site omits `@Transactional`, the outbox row auto-commits independently of the state change, violating `agents.md`'s "Events & messaging" rule that every publish is in the same transaction as the DB write. The failure is silent — no exception, no log — and only surfaces later as a missing or duplicated event.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/events/OutboxPublisher.java:57`
  - `artifacts/07-self-review.md:12-23` (finding 1, already noted and deferred)
  - `spec/payment-service/agents.md:36-37` (outbox in same transaction)
- **Recommendation:** Either add a runtime guard that throws if no transaction is active, or make the method `@Transactional(propagation = MANDATORY)` so the container enforces the contract. The self-review defers this because `crypto-service` has the same shape; from an independent review perspective it remains a latent correctness risk that should be logged for a future cross-service hardening pass if not fixed here.
- **Confidence:** Medium.

---

### 4. Unroutable outbox events will be re-logged on every poll forever

- **Issue:** In `OutboxRelay.relayOne`, an `IllegalStateException` from `EventTopics.forAggregateType` is caught, logged at `ERROR`, and the method returns without marking the row published. The row remains in the unpublished partition, so the same error is re-logged every `relay-interval-ms` until a code fix is deployed. Since there is no quarantine/dead-letter column in the immutable schema, a single bad row becomes a perpetual log-noise source.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java:63-72`
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:113-122` (no quarantine/terminal state column)
- **Recommendation:** Acceptable for now because no currently emitted aggregate type is unmapped, but document this as a known operational behavior. If the service ever introduces dynamic aggregate types, add either a quarantine column (requires a new migration) or a circuit/backoff in the relay.
- **Confidence:** Low.

---

### 5. Positive confirmation: `OutboxEvent` maps the real schema exactly

- **Issue:** Not a finding — positive confirmation. The entity's 7 fields (`id`, `aggregateType`, `aggregateId`, `eventType`, `payload`, `createdAt`, `publishedAt`) and their nullability/length constraints match `V1__payments_baseline.sql:113-121`. There is no `idempotencyKey` field, matching the immutable schema and the Phase 3 disposition.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/events/OutboxEvent.java`
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:113-121`
- **Recommendation:** None.
- **Confidence:** High.

---

### 6. Positive confirmation: producer config and scheduling infrastructure are in place

- **Issue:** Not a finding — positive confirmation. `KafkaProducerConfig` declares `ProducerFactory<String,String>` and `KafkaTemplate<String,String>` with `acks=all` and `enable.idempotence=true`; `ClockConfig` provides a `Clock.systemUTC()` bean; `PaymentServiceApplication` now carries `@EnableScheduling`; and `EventTopics` matches `design.md` §4c verbatim.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/events/KafkaProducerConfig.java:24-40`
  - `services/payment/src/main/java/com/themistra/payment/common/ClockConfig.java:17-20`
  - `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java:18`
  - `services/payment/src/main/java/com/themistra/payment/events/EventTopics.java:12-17`
- **Recommendation:** None.
- **Confidence:** High.
