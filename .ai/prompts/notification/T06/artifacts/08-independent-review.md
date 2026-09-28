<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T06 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T06 — Auth event consumer |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T06 implementation. Findings only.

---

## Finding 1 · No committed test locks the idempotency key format

**Issue:** `AuthEventConsumer` derives the idempotency key as `accountUuid + ":" + purpose/eventType + ":" + occurredAt`. This format is the non-negotiable correctness property for deduplication across redeliveries and rebalance. A future refactor that changed the delimiter, field order, or `occurredAt` serialization would silently break deduplication for events already in flight, yet no existing test asserts the exact key string.

**Evidence:**
- `AuthEventConsumer.java` lines 65 and 95 build the key with string concatenation.
- No test in `src/test` asserts the value passed to `IdempotencyGuard.recordIfNew` for any event.

**Recommendation:** Add a unit test for `AuthEventConsumer` (constructor-injected with mocked `ObjectMapper`, `IdempotencyGuard`, `ContactProjectionUpdater`, and `NotificationDispatcher`) that:
- deserializes a known JSON payload;
- captures the `eventKey` passed to `idempotencyGuard.recordIfNew(...)`;
- asserts it equals the expected `accountUuid + ":" + purpose/eventType + ":" + occurredAt` string.

**Confidence:** High

---

## Finding 2 · No committed end-to-end test exercises the consumer against a real Kafka broker

**Issue:** AC1 requires two real `@KafkaListener` methods. The self-review confirmed the listener wiring works via a temporary scratch test, but that test was deleted. There is no committed test that produces a message to `auth.email.requested`/`auth.user.lifecycle` and observes the consumer process it.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/consumer/` contains only `IdempotencyGuard*Test`, `ContactProjectionUpdater*Test` (in `preference/`), and `dto/*ContractTest`.
- No `AuthEventConsumerTest` or `AuthEventConsumerIntegrationTest` exists.
- The self-review explicitly states this was only verified by a deleted scratch test and recommends Phase 10 make it permanent.

**Recommendation:** Add a Kafka-backed integration test (using the module's existing convention of the shared local broker or Testcontainers Kafka) that:
- produces a real JSON message to each topic;
- polls until `processed_events` and `contact_projection` reflect the expected changes;
- asserts `NotificationDispatcher.dispatch` was called with the correct arguments (via a spy bean);
- re-publishes the same message and asserts `dispatch` is not called a second time.

**Confidence:** High

---

## Finding 3 · No test verifies projection-update behavior on non-dispatched or duplicate events

**Issue:** The self-review's scratch test confirmed that unknown `purpose` values and non-registration `eventType` values still refresh `contact_projection`, and that duplicate deliveries do not. No committed test asserts either behavior.

**Evidence:**
- `AuthEventConsumer.java` lines 70 and 100 call `contactProjectionUpdater.upsertEmail` after the dedupe check and before the dispatch filter.
- No test in `src/test` verifies `upsertEmail` is called for `purpose=mystery_purpose` or `eventType=user.locked`, or that it is skipped on a duplicate delivery.

**Recommendation:** In the Kafka-backed integration test, add scenarios for:
- `auth.email.requested(purpose=unknown)` → assert `contactProjectionUpdater.upsertEmail` was called, `dispatch` was not;
- `auth.user.lifecycle(eventType=user.locked)` → assert `upsertEmail` was called, `dispatch` was not;
- redelivering an already-processed message → assert `upsertEmail` is not called again.

**Confidence:** Medium

---

## Finding 4 · No test verifies transaction rollback when `dispatch` throws

**Issue:** Both listener methods are `@Transactional` so that idempotency record, projection refresh, and dispatch share a transaction. The no-op dispatcher never throws, so no test can currently prove that a failing `dispatch` rolls back the preceding writes. When task 11/12 replaces the dispatcher with a real, fallible implementation, this property will become critical.

**Evidence:**
- `AuthEventConsumer.java` lines 62 and 92 annotate the listener methods with `@Transactional`.
- `NoOpNotificationDispatcher.java` line 30 only logs and returns.

**Recommendation:** Add an integration test that injects a `@Primary` test-only `NotificationDispatcher` bean that throws a runtime exception. Produce a message, assert the exception propagates, then query `processed_events` and `contact_projection` directly and assert no rows were written. This proves the atomicity of the listener's transaction boundary.

**Confidence:** Medium

---

## Finding 5 · No test verifies `NoOpNotificationDispatcher` does not log token values

**Issue:** `agents.md` L4 forbids logging tokens/secrets. The implementation logs only `eventData.keySet()`, so it is correct, but no test asserts this. A future edit that accidentally switched to logging `eventData` values would not fail any test.

**Evidence:**
- `NoOpNotificationDispatcher.java` line 30 logs `eventData.keySet()`.
- `EmailRequestedEventContractTest.java` tests that the DTO's `toString()` excludes the token, but there is no equivalent test for the dispatcher log output.

**Recommendation:** Add a unit test for `NoOpNotificationDispatcher` that captures the log output (e.g., via Logback's `ListAppender`) and asserts the raw token value does not appear when dispatching `verify_email`/`password_reset`.

**Confidence:** Medium

---

## Finding 6 · Contract tests do not validate schema format constraints

**Issue:** `EmailRequestedEventContractTest` and `UserLifecycleEventContractTest` verify that required fields are present and no extra fields are serialized, but they do not validate that values conform to the schema's format constraints (`uuid`, `email`, `date-time`) or that enum values are honored. This is weaker than a true JSON Schema validation.

**Evidence:**
- `EmailRequestedEventContractTest.java` lines 40–49 only check field presence.
- No test uses a JSON Schema validator library (e.g., `networknt/json-schema-validator`) to validate the serialized DTO against the schema file.

**Recommendation:** Either introduce a JSON Schema validator in the contract tests or add explicit assertions for format/enum constraints (e.g., assert `accountUuid` serializes as a valid UUID string, `email` contains `@`, `occurredAt` is ISO-8601). This strengthens the structural substitute for code generation.

**Confidence:** Low

---

## Finding 7 · `NotificationDispatcher` interface in `consumer/` creates a forward dependency from `delivery/`

**Issue:** `NotificationDispatcher` is placed in `com.themistra.notification.consumer`. A future real implementation is expected to live in a `delivery/` package (task 11/12). That implementation will depend on an interface in `consumer/`, which is architecturally backward: the delivery mechanism depends on the consumer package.

**Evidence:**
- `NotificationDispatcher.java` package declaration: `com.themistra.notification.consumer`.
- Javadoc references `DeliveryOrchestrator` and `EmailChannel` as future implementers.

**Recommendation:** Consider moving `NotificationDispatcher` to a neutral package such as `com.themistra.notification.delivery.api` or `com.themistra.notification.notification` so both `consumer/` and `delivery/` can depend on it without crossing package boundaries. If the brief explicitly wants it in `consumer/`, document the intended future relocation.

**Confidence:** Low

---

## Finding 8 · `AuthEventConsumer` discards the `upsertEmail` boolean return

**Issue:** `ContactProjectionUpdater.upsertEmail` returns `boolean` indicating whether the projection was updated or rejected as stale. `AuthEventConsumer` ignores this value. This is not a correctness defect for T06, but it means the consumer cannot observe or log projection freshness.

**Evidence:**
- `AuthEventConsumer.java` lines 70 and 100 call `contactProjectionUpdater.upsertEmail(...)` without capturing the result.

**Recommendation:** Either capture the result and log it at `DEBUG`/`INFO` (e.g., "projection updated" vs "stale event skipped") or document in the Javadoc that the return value is intentionally ignored. This preserves observability without changing behavior.

**Confidence:** Low

---

## Summary

The T06 production code is well-structured: the two `@KafkaListener` methods correctly route by `purpose`/`eventType`, respect L1 by deduping first, refresh the projection before dispatching, and use `@Transactional` to keep the three steps atomic. The `NoOpNotificationDispatcher` safely avoids logging tokens, the contract tests provide a reasonable structural substitute for generated DTOs, and `auto-offset-reset=latest` is correctly chosen. The dominant issue is the absence of committed consumer-specific tests (Findings 1–5). Findings 6–8 are smaller precision and package-design items.
