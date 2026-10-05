<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T17 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T17 — End-to-end `auth.email.requested(verify)` redelivery/idempotency proof |
| **Spec section** | R1, R7, R8, L1, L3 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/01-specification-extraction.md` + existing integration tests + production chain |
| **Produces** | `artifacts/03-design-challenge.md` |

Phase 3 adversarial design review of the T17 extraction before implementation.

---

## Finding 1 · The new test must not repeat the spy-dispatch mistake of the existing consumer integration test

**Challenge:** `AuthEventConsumerIntegrationTest` (T06) proves Kafka → consumer wiring, but it replaces the real `NotificationDispatcher` bean with a `SpyDispatcherConfig`. A naive end-to-end test built on top of that class would inherit the same spy and still prove nothing about preference resolution, template rendering, or `FakeEmailTransport` capture.

**Resolution:** Create a new, standalone test class that does **not** import any spy/test dispatcher configuration. The Spring context must wire the real `DeliveryOrchestrator` as the `NotificationDispatcher` implementation. This is the entire point of the task: glue the two already-proven halves together.

---

## Finding 2 · Test package choice is constrained by package-private repositories

**Challenge:** The new test needs to assert on both `processed_events` (idempotency) and `delivery_log` (dispute-grade log). `ProcessedEventRepository` is package-private in `com.themistra.notification.consumer`; `DeliveryLogRepository` is package-private in `com.themistra.notification.delivery`. A test in a third package cannot inject either directly.

**Resolution:** Place the test in `com.themistra.notification.delivery` so `DeliveryLogRepository` is directly injectable, and query `processed_events` via a simple JDBC `SELECT event_key FROM notifications.processed_events WHERE event_key = ?`. This mirrors the existing `AuthEventConsumerIntegrationTest` pattern, which already uses JDBC for contact-projection queries while living in the `consumer` package. The test's primary assertion surface is the delivery outcome, so `delivery` is the natural home.

**Alternative considered:** Root package with JDBC for both tables. Rejected because it abandons the repository convenience for `delivery_log` and sends a misleading "this test doesn't belong anywhere" signal.

---

## Finding 3 · The test must use its own Kafka consumer group ID

**Challenge:** `AuthEventConsumerIntegrationTest` already uses `auth-event-consumer-it`. If the new test reuses the same group ID, the two test classes can contend for partitions, consume each other's messages, or leave offset state that makes redelivery assertions flaky.

**Resolution:** Set a unique group ID for this test class only, e.g.:
```
@SpringBootTest(properties = "spring.kafka.consumer.group-id=verify-email-redelivery-it")
```
This follows the precedent in `AuthEventConsumerIntegrationTest` and isolates this test from every other class reading `auth.email.requested`.

---

## Finding 4 · `verify_email` produces both an EMAIL and an IN_APP delivery row

**Challenge:** The notification mapping for `verify_email` uses templates `email.verify` (EMAIL) and `user.verify` (IN_APP). `FakeEmailTransport` only captures the email. If the test only asserts the captured email, it misses the IN_APP half of the real transaction. If the test asserts "exactly one delivery_log row," it will fail because two rows are written.

**Resolution:** Assert both rows exist after the first delivery:
- Exactly one captured `EmailMessage` for the account in `FakeEmailTransport`.
- Two `delivery_log` rows for the account: one `EMAIL` with outcome `SENT` and one `IN_APP` with outcome `SENT`.

On redelivery, assert both counts are unchanged (email capture still 1, delivery_log rows still 2).

---

## Finding 5 · The shared `FakeEmailTransport` singleton must be cleared per test

**Challenge:** `FakeEmailTransport` is a Spring singleton within the test context. Without clearing it, a second test method in the same class could see messages from the first, and redelivery assertions would be meaningless.

**Resolution:** Add a `@BeforeEach` method that calls `fakeEmailTransport.clear()`, mirroring `DeliveryOrchestratorIntegrationTest` exactly.

---

## Finding 6 · Redelivery timing must be waited, not assumed

**Challenge:** After producing the same event a second time, the consumer must actually consume and dedupe it before the assertion runs. If the test asserts immediately, it may pass falsely before the second message is processed.

**Resolution:** Use `Awaitility` with a small `pollDelay` (e.g., 3 seconds) and a reasonable timeout (e.g., 10 seconds), then assert the email capture count and delivery_log row count are unchanged. This mirrors the redelivery assertion pattern in `AuthEventConsumerIntegrationTest`.

---

## Finding 7 · The background retry scheduler should be pushed far out or accepted as harmless

**Challenge:** `RetryScheduler` is `@Scheduled` and fires once at context startup, then holds a ShedLock for at least 10 seconds. The new test does not create retry rows under normal conditions, so a startup sweep is harmless. However, if a transient failure somehow occurred, the real sweep could race with the test's own assertions.

**Resolution:** Set `themistra.notification.retry.scheduler-interval-seconds` to a very large value (e.g., `999999`) via `@DynamicPropertySource`, mirroring `DeliveryOrchestratorIntegrationTest`. This eliminates any background sweep interference without modifying production code.

---

## Finding 8 · No new dependencies, no production code changes

**Challenge:** The task's scope is a single new integration test. No new Maven dependency is needed (`testcontainers`, `spring-kafka`, `awaitility` are already present). No production file should be modified.

**Resolution:** Confirm no `pom.xml` changes. Confirm no modifications to `AuthEventConsumer`, `DeliveryOrchestrator`, `FakeEmailTransport`, `IdempotencyGuard`, repositories, templates, or configuration. The only new file is the test class.

---

## Finding 9 · The `payments.receipt.issued` scenario is explicitly out of scope

**Challenge:** Phase 0 deferred the payment-scenario redelivery proof. The extraction document calls this out, but a future reviewer might expect it in T17.

**Resolution:** Document in the new test's Javadoc (and in the Phase 12 verification artifact) that this test covers only `auth.email.requested(verify_email)`, per the Phase 0 user decision. Do not add a second scenario to this task.

---

## Decisions Made

1. **New test class:** `com.themistra.notification.delivery.VerifyEmailRedeliveryIntegrationTest`.
2. **No spy/test dispatcher override** — use the real production wiring.
3. **Unique Kafka group ID:** `verify-email-redelivery-it`.
4. **Postgres setup:** same Testcontainers + Flyway + schema/password pattern as existing integration tests.
5. **Repository access:** inject `DeliveryLogRepository` directly (same package); query `processed_events` via JDBC.
6. **Assertions:** one captured email, two `SENT` `delivery_log` rows; unchanged counts after redelivery.
7. **Shared state hygiene:** `@BeforeEach clear()` on `FakeEmailTransport`; scheduler interval pushed to `999999`.
8. **Scope:** only `auth.email.requested(verify_email)`; payment scenario explicitly deferred.
9. **No production code or dependency changes.**
