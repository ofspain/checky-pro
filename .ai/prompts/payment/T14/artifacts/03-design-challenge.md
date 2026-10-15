# payment · T14 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T14/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `ChainEventConsumer.java`, `PaymentService.java`, `ProcessedEventRepository.java`, `InvoiceService.java`, `ChainTxSeen/Confirmed/Finalized/Reorged.java`, `OutboxRelay.java`, `KafkaProducerConfig.java`, notification-service's `AuthEventConsumerIntegrationTest.java`, payment's existing integration-test patterns.

---

## Executive summary

The brief is directionally sound: a single Testcontainers integration class that exercises R13, R14, and O6 against real Postgres + Kafka is exactly what T14 needs. The cross-event-type resolution for R14 (Decision 2) is a real, well-reasoned finding.

However, the brief under-specifies several runtime details that will cause flaky or failing tests if left to default Spring Boot / Testcontainers behavior. The most consequential gaps are Kafka consumer offset-reset behavior, background scheduled-task noise, and JSON payload construction for `BigDecimal` amounts.

A baseline integration-test run is **not required** to produce this design review — the issues below are visible from the code and from the established integration-test precedents in auth/notification/payment.

---

## Finding 1 — The brief overstates "mirroring AuthEventConsumerIntegrationTest exactly"

- **Issue:** The cited precedent (`services/notification/src/test/java/com/themistra/notification/consumer/AuthEventConsumerIntegrationTest.java`) does **not** use a Testcontainers Kafka broker. It relies on a shared local Kafka broker and only brings up a Postgres Testcontainer. It also uses a `@TestConfiguration` spy bean, not `@MockBean`, and verifies a projection table and dispatcher calls, not raw JDBC against domain tables.
- **Impact:** A reader following the brief may expect a copy-paste shape that does not exist. The payment test will necessarily diverge: Kafka Testcontainer, `@MockBean CryptoClient`, manual Flyway migration, raw JDBC verification, and Awaitility-based assertions.
- **Recommendation:** Reword Decision 1 to say "adapts the single-class/shared-containers/Awaitility shape of `AuthEventConsumerIntegrationTest`, but with a Kafka Testcontainer and payment-specific verification" instead of "mirroring ... exactly".
- **Confidence:** High.

---

## Finding 2 — Missing `auto.offset.reset=earliest`

- **Issue:** The brief does not specify `spring.kafka.consumer.auto-offset-reset`. Spring Boot's default for a new consumer group is `latest`. In an integration test that produces messages very soon after context startup, the Kafka consumer may not have completed its initial partition assignment before the first message lands. With `latest`, those messages are never read and the test times out.
- **Impact:** Flaky or permanently failing AC1/AC2/AC3, especially on slower CI runners.
- **Recommendation:** Add `spring.kafka.consumer.auto-offset-reset=earliest` to the test's `@SpringBootTest(properties = {...})` block. This is safe because each test uses unique keys and cleanup guarantees no stale data matters.
- **Confidence:** High.

---

## Finding 3 — Background scheduled tasks are not suppressed

- **Issue:** A full `@SpringBootTest` starts `OutboxRelay` (polls every 2s by default) and `InvoiceExpirySweepJob` (cron every 5 minutes). The relay will forward `invoice.created`/`payment.seen` outbox rows to Kafka, and the sweep job could mutate invoice state during a long-running assertion window.
- **Impact:** Non-deterministic Kafka topic noise; possible test flakes where an invoice is swept to `EXPIRED` before a finalization event is processed.
- **Recommendation:** Disable scheduling for this test class via `spring.task.scheduling.enabled=false` in `@SpringBootTest(properties = {...})`. This stops both the relay and the expiry sweep without affecting `@KafkaListener` containers. The outbox rows written by `InvoiceService.create` will simply remain unpublished, which is fine — T14 is testing consumed events, not the outbox relay.
- **Confidence:** High.

---

## Finding 4 — Cleanup list is incomplete

- **Issue:** Decision 3 says clean `payments.payments` and `payments.invoices` via raw JDBC. It omits `payments.processed_events`, `payments.outbox`, and `payments.verification_records`.
- **Impact:**
  - Leftover `processed_events` rows from one test could mask duplicate-delivery assertions in another if keys ever collide (unlikely with random UUIDs, but not impossible in a shared static container).
  - Leftover `outbox` rows feed the relay; even with scheduling disabled, they accumulate and make debugging harder.
  - Leftover `verification_records` make it impossible to write future assertions on audit-trail counts.
- **Recommendation:** Expand `cleanUp()` to truncate `payments.payments`, `payments.invoices`, `payments.processed_events`, `payments.outbox`, and `payments.verification_records` after each test.
- **Confidence:** High.

---

## Finding 5 — `BigDecimal amount` must be serialized as a decimal string, not a JSON number

- **Issue:** `ChainTxFinalized.amount()` is a `BigDecimal`. If the test constructs payloads by serializing DTOs with the default Spring `ObjectMapper`, `BigDecimal` is written as a JSON number (e.g., `1000`), but the event schema requires a decimal string (`"1000"`). Jackson's `BigDecimal` deserializer usually accepts both, but relying on the deserializer's leniency makes the test verify the wrong producer contract.
- **Impact:** Test passes against a payload that a schema-strict producer would reject; a future schema-enforcement change could break production without breaking this test.
- **Recommendation:** Build finalized-event JSON manually (or with a Map whose `amount` value is a `String`) so the produced payload contains `"amount":"1000"`.
- **Confidence:** High.

---

## Finding 6 — AC3 does not actually prove per-payment ordering

- **Issue:** AC3 publishes `seen → confirmed → finalized` and asserts the final payment state. Because the three events are on three different topics, Kafka only guarantees order within each topic, not across topics. The test therefore does not validate that a `finalized` arriving before a `confirmed` is handled correctly — it only validates that the happy-path forward sequence works.
- **Impact:** O6's real concern is "per-payment ordering is preserved." A happy-path test is a weak proof; it does not exercise the race window.
- **Recommendation:** Either (a) add an explicit ordering-violation test case that publishes `finalized` before `confirmed` (with enough delay/await to force cross-topic interleaving) and asserts the final state is still correct, or (b) reword AC3/O6 to "forward sequence reaches FINALIZED" rather than "ordering is preserved." Option (a) is strongly preferred.
- **Confidence:** Medium.

---

## Finding 7 — Kafka topic auto-creation is assumed but not guaranteed

- **Issue:** The brief assumes the Testcontainers Kafka broker will auto-create `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized`, and `chain.tx.reorged`. While `apache/kafka:3.8.0` in KRaft mode defaults to `auto.create.topics.enable=true`, this is an implicit environmental dependency.
- **Impact:** A future image/version change, or an environment with auto-creation disabled, would cause `UnknownTopicOrPartitionException` and fail the entire class.
- **Recommendation:** Make topic creation explicit: in `@BeforeAll`, after the Kafka container starts, use `AdminClient` to create the four topics with at least one partition. This also lets the test pin the partition count to 1, simplifying O6 reasoning.
- **Confidence:** Medium.

---

## Finding 8 — Decision 2 is sound, but its sibling limitation on `chain.tx.reorged` should be acknowledged

- **Issue:** Decision 2 correctly notes that a second `chain.tx.confirmed` for the same transaction cannot test the stale-confirmation branch because the idempotency key collides. The same structural limitation already exists for `chain.tx.reorged` (documented in `ChainTxReorged.java` Phase 9 Finding 9: one idempotency key per `{chain}:{txHash}:reorged`). A second genuine reorg of the same transaction is indistinguishable from a duplicate delivery.
- **Impact:** The brief's R14 scope should explicitly state that same-event-type staleness for `confirmed` and `reorged` is contractually unreachable; only cross-event-type staleness and the `finalized`-before-`seen` out-of-order case are end-to-end testable.
- **Recommendation:** Add a short note in Decision 2 that the same idempotency-key limitation applies to `chain.tx.reorged`, and that this is a known contract constraint, not a test gap.
- **Confidence:** High.

---

## Finding 9 — `@MockBean CryptoClient` hides the unregister-watch path

- **Issue:** Decision 3 proposes mocking `CryptoClient` so `unregisterWatch` is a silent no-op. This is correct for isolating T14 from crypto-service, but it means T14 does not exercise the fact that `onChainTxSeen` holds a database connection open across an outbound HTTP call.
- **Impact:** The test cannot catch integration issues related to transaction/connection timeout tuning for that path. This is acceptable for T14's scope (idempotency/ordering), but it should be stated as an accepted boundary.
- **Recommendation:** Add a note: "`CryptoClient` is mocked because T14's scope is dedupe/ordering/staleness, not crypto-service availability. The held-open-DB-connection behavior of `onChainTxSeen` remains covered by unit tests and is out of scope here."
- **Confidence:** Low.

---

## Finding 10 — Missing explicit web environment / port configuration

- **Issue:** The brief does not specify `WebEnvironment.NONE` or `server.port`. Payment's existing integration tests use `WebEnvironment.NONE`. The Spring Boot default for `@SpringBootTest` is `MOCK`, which also does not start a server, but relying on the default is brittle.
- **Impact:** If the default ever changes, or if another integration test in the same module starts a server on the same port, the context may fail to start.
- **Recommendation:** Add `webEnvironment = SpringBootTest.WebEnvironment.NONE` to the `@SpringBootTest` annotation.
- **Confidence:** Low.

---

## Finding 11 — Dedicated consumer group-id should be explicit and isolated

- **Issue:** Decision 3 and Constraints mention a "dedicated group-id" but do not give a value or explain why it must differ from `payment-service`.
- **Impact:** A developer might reuse `payment-service`, causing offset-commit interference with a locally running payment-service instance or with another test class.
- **Recommendation:** Use a fixed, clearly named group id such as `payment-chain-idempotency-it` and document it in the brief.
- **Confidence:** Low.

---

## Positive confirmations

- A single combined class is the right granularity; it keeps container startup cost amortized across AC1-AC3.
- `InvoiceService.create` is the correct seeding path — it exercises the real validation, watch-id assignment, and outbox write.
- `ProcessedEventRepository` (package-private) is the right verification hook for R13.
- Raw JDBC verification is appropriate because the `consumer` package must not depend on `Payment`/`Invoice` entities (L12).
- `Awaitility` with `pollDelay` for negative assertions is the correct pattern; no `Thread.sleep`.
- The cross-event-type resolution for R14 is the only dedupe-respecting way to test real staleness end-to-end.
- Classifying the test as `*IntegrationTest` keeps it out of the non-Docker fast suite, matching the project's established exclusion pattern.

---

## Disposition summary

| Finding | Severity | Disposition |
|---|---|---|
| 1. Overstated precedent | Low | Reword Decision 1 |
| 2. Missing `auto-offset-reset=earliest` | High | Add to test properties |
| 3. Background scheduled tasks | High | Disable scheduling |
| 4. Incomplete cleanup | Medium | Expand cleanup list |
| 5. BigDecimal JSON shape | Medium | Build payloads manually |
| 6. AC3 weak ordering proof | Medium | Add out-of-order test or reword AC3 |
| 7. Topic auto-creation | Medium | Pre-create topics in `@BeforeAll` |
| 8. Acknowledge reorg idempotency limitation | Low | Add note to Decision 2 |
| 9. Mocked CryptoClient boundary | Low | Document as accepted |
| 10. Missing web environment | Low | Add `WebEnvironment.NONE` |
| 11. Explicit group-id | Low | Add fixed group id |

---

## Recommended next step

Approve the brief with the above amendments, then proceed to Phase 5 (test implementation). The amendments are test-configuration details, not architectural changes, and all remain within the "zero production code" guardrail.
