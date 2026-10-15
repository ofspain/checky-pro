# payment · T14 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T14/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`, `application.properties`, `06-implementation-notes.md`; also ran the test live against a real Kafka broker.

---

## Executive summary

- The new integration test correctly proves R13, R14, and the forward-sequence aspect of O6 against a live Postgres Testcontainer and a real Kafka broker.
- The test passed **3/3** and the full payment suite is **295/295 green** when a Kafka broker is available on `localhost:9094`.
- The most significant independent finding is that the test relies on an **externally provisioned Kafka broker** instead of a Testcontainers Kafka container, making it non-self-contained and CI-unfriendly. This directly contradicts the Phase 4 design-challenge approval and must be resolved before this task can be considered fully done.
- Remaining findings are medium/low: fire-and-forget producer sends, negative assertions that don't first confirm consumption, missing invoice-state assertion in AC3, and the bundling of three scenarios into one R14 test method.

---

## New Finding 1 (High) — Test depends on an external Kafka broker, not Testcontainers

- **Issue:** The test class has **no `KafkaContainer`**. It relies on `spring.kafka.bootstrap-servers` defaulting to `localhost:9094` from `application.properties` — the same shared local broker every service uses. When I first attempted to run the test, `localhost:9094` was unreachable; after starting `services/auth/compose.local.yaml`'s `kafka` service, the test passed. This is not the Testcontainers-Kafka design that Phase 4 approved.
- **Impact:** The test is not self-contained. It cannot run in a clean CI environment without extra compose-file orchestration, and it will fail mysteriously for any developer who hasn't started the local Kafka dependency.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java` has no `KafkaContainer` field and no `spring.kafka.bootstrap-servers` override in `@DynamicPropertySource`.
  - `services/payment/src/main/resources/application.properties:35` sets `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}`.
  - The build failed before `docker compose -f services/auth/compose.local.yaml up -d kafka` and succeeded afterward.
- **Recommendation:** Add a static `org.testcontainers.kafka.KafkaContainer` (or `org.testcontainers.containers.KafkaContainer`) to the test class and override `spring.kafka.bootstrap-servers` via `@DynamicPropertySource`, exactly as Phase 4 specified. If an external broker must be retained temporarily, add a prominent Javadoc note and a fail-fast check at `@BeforeAll`.
- **Confidence:** High.

---

## New Finding 2 (Medium) — `kafkaTemplate.send(...)` is fire-and-forget

- **Issue:** Every `kafkaTemplate.send(...)` call ignores the returned `CompletableFuture<SendResult>`. If the producer cannot deliver a message, the test only discovers it via a downstream `Awaitility` timeout, not a clear producer exception.
- **Impact:** Harder-to-diagnose failures; a slow or temporarily unavailable broker will produce `Awaitility` timeout stack traces instead of a direct `TimeoutException` at the send call.
- **Evidence:**
  - `ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java:226-227`, `:231-232`, `:236-237`, `:282-283`, `:293-294`, `:306-307` all call `kafkaTemplate.send(...)` without waiting.
- **Recommendation:** Introduce a small helper such as:
  ```java
  private void sendAndWait(String topic, String key, String json) {
      kafkaTemplate.send(topic, key, json).get(5, TimeUnit.SECONDS);
  }
  ```
  and use it everywhere. This preserves ordering intent and surfaces producer errors immediately.
- **Confidence:** High.

---

## New Finding 3 (Medium) — Negative assertions do not first confirm the event was consumed

- **Issue:** Scenarios (a), (b), and (c) in `shouldIgnoreOutOfOrderOrStaleChainEvents` use `await().pollDelay(...).untilAsserted(...)` to assert that the state did *not* change. `pollDelay` only delays the first evaluation; it does not guarantee the Kafka consumer actually processed the stimulus message. If the message were lost or the consumer hadn't joined yet, the negative assertion would pass vacuously.
- **Impact:** False confidence in R14 coverage.
- **Evidence:**
  - Lines `:284-287`, `:295-298`, `:308-311` — each asserts no state change after a fixed delay.
- **Recommendation:** For each scenario, first `await()` until `processedEventRepository.existsById(idempotencyKey)` becomes true (proving the message was consumed and deduped), then assert the state invariant. For (b)/(c), an even stronger check is to await a new `verification_records` ignored row before asserting the payment state.
- **Confidence:** High.

---

## New Finding 4 (Medium) — AC3 does not verify the invoice-state transition

- **Issue:** `shouldHandleTheFullForwardSequenceAndReachFinalized` only asserts `payments.payments` fields (`amount`, `from_address`, `to_address`). It does not verify that the invoice moved from `OPEN` to `PAID`, even though `InvoiceService.markOutcomeIfOpenOrExpired` is a real part of the `onChainTxFinalized` wiring being tested.
- **Impact:** A regression that broke invoice-state transition would not be caught by this integration test.
- **Evidence:**
  - `ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java:319-332` queries only `payments.payments`.
- **Recommendation:** Add an assertion that `payments.invoices.state = 'PAID'` for the invoice seeded in the test.
- **Confidence:** High.

---

## Confirming self-review Finding 1 — Unused invoice seed in scenario (a)

- **Independent assessment:** Confirmed. In scenario (a) the finalized event correlates by `(chain, txHash)` and the "no known payment" branch is reached before any invoice lookup. Seeding an invoice with `unknownWatchId` adds setup cost and misleading context.
- **Recommendation:** Remove `seedInvoice(unknownWatchId)` and `unknownInvoiceUuid` from scenario (a); use a bare random UUID for the event's own `watchId` field.
- **Confidence:** High.

---

## Confirming self-review Finding 2 — Three scenarios bundled into one R14 test

- **Independent assessment:** Confirmed and worth fixing. A failure in scenario (a) prevents (b) and (c) from running, hiding whether the other two staleness paths are healthy. The package.md mapping can be preserved by keeping `shouldIgnoreOutOfOrderOrStaleChainEvents` as the name of scenario (c) (the most interesting cross-topic race) and extracting (a) and (b) into named siblings such as `shouldIgnoreFinalizedForUnknownPayment` and `shouldIgnoreReorgedForAlreadyFinalizedPayment`.
- **Alternative:** Keep the bundled method but add explicit, descriptive assertion messages and a comment noting the three independent assertions. Splitting is preferred.
- **Confidence:** Medium.

---

## New Finding 5 (Low) — Kafka topics are auto-created implicitly

- **Issue:** The test relies on `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` in the broker. The logs show `UNKNOWN_TOPIC_OR_PARTITION` warnings before auto-creation succeeds. While the current broker config enables this, it is an implicit environmental assumption.
- **Impact:** If the broker is ever reconfigured to disable auto-creation, the test fails with `UnknownTopicOrPartitionException`.
- **Evidence:**
  - Log lines: `"The metadata response from the cluster reported a recoverable issue ... {payments.invoice.created=UNKNOWN_TOPIC_OR_PARTITION}"`.
- **Recommendation:** Explicitly create the four `chain.tx.*` topics (and optionally `payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized` for outbox noise) in `@BeforeAll` using `KafkaAdmin`/`AdminClient`. This documents the topic contract and removes the implicit assumption.
- **Confidence:** Low.

---

## New Finding 6 (Low) — `spring.task.scheduling.enabled=false` is a no-op here

- **Issue:** The implementation notes correctly identify that `PaymentServiceApplication` declares `@EnableScheduling` explicitly, so Boot's `spring.task.scheduling.enabled` property does not disable `OutboxRelay`/`InvoiceExpirySweepJob`. The property is left in place as a documented no-op.
- **Impact:** Harmless for correctness, but slightly misleading. The outbox relay still forwards events during the test (visible in logs).
- **Recommendation:** Either remove the property and replace it with a comment, or — if the noise ever matters — use a `@TestConfiguration` that replaces `OutboxRelay`/`InvoiceExpirySweepJob` with no-op beans for this class. For now, a clarifying comment is sufficient.
- **Confidence:** Low.

---

## Positive confirmations

- All three ACs are implemented and named.
- `@EntityScan` correctly scopes to the five real feature packages, excluding `archtestfixtures`.
- `@MockBean JwtDecoder` is a minimal, correct fix for the full-context security wiring.
- `auto-offset-reset=earliest`, the dedicated consumer group id, and `WebEnvironment.NONE` are all correctly configured.
- Cleanup includes `verification_records`, `processed_events`, `outbox`, `payments`, and `invoices`.
- JSON payloads use quoted decimal strings for `amount`, matching the real schema.
- Every test uses fresh UUIDs/`txHash`es; no cross-test mutable state.
- No `Thread.sleep`; `Awaitility` is used throughout.
- Raw JDBC interpolation is safe because every value is internally generated.
- The two production cron strings were fixed to 6-field Spring cron format; this was a genuine pre-existing bug.
- With a broker running, the test is stable: 3/3 passing, full suite 295/295 green.

---

## Build context

- `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`: **3/3 passing** (with external Kafka broker on `localhost:9094`).
- Full `mvn -pl services/payment -am test`: **295/295 passing**.
- The Kafka broker was started from `services/auth/compose.local.yaml` for this run.

---

## Recommended follow-up order

1. **Add a `KafkaContainer` to the test class** (Finding 1) — this is the only blocker to calling T14 fully self-contained.
2. **Wait for `kafkaTemplate.send` futures** (Finding 2).
3. **Strengthen R14 negative assertions by first awaiting `processed_events`** (Finding 3).
4. **Add invoice-state assertion to AC3** (Finding 4).
5. **Split the bundled R14 test into three methods** (self-review Finding 2).
6. **Remove the unused invoice seed in scenario (a)** (self-review Finding 1).
7. **Optional:** pre-create Kafka topics explicitly (Finding 5) and clarify the scheduling-property no-op (Finding 6).
