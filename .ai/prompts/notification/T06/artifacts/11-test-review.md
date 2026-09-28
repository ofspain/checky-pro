<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T06 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T06 — Auth event consumer |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T06 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T06 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · No automated negative-proof that the idempotency short-circuit is required

**Why it matters:** The core correctness property of T06 — that duplicate Kafka delivery does not double-dispatch — rests on the `if (!idempotencyGuard.recordIfNew(...)) return;` guard. The Phase 10 artifact documents a one-time manual mutation test, but if a future refactor accidentally removes the guard or the `recordIfNew` call, the build will not fail until someone re-runs that manual check.

**Suggested test:** Add a plain JUnit test that reads `AuthEventConsumer.java` as text and asserts both listener methods contain the exact guard pattern (`if (!idempotencyGuard.recordIfNew(` followed by `return;` before any projection/dispatch calls). This is a cheap, permanent regression guard for the exact property the integration tests depend on.

---

## Gap 2 · `AuthEventConsumerIntegrationTest` uses a static, accumulating spy list

**Why it matters:** `SpyDispatcherConfig.CALLS` is a `static final CopyOnWriteArrayList<DispatchCall>` shared across all test methods in the class. The redelivery assertion in `emailRequestedVerifyEmailIsConsumedDedupedProjectedAndDispatched` compares `SpyDispatcherConfig.CALLS.size()` before and after re-publishing. This is safe under JUnit's default sequential execution, but it is fragile:
- If tests are ever run in parallel or reordered, the size comparison could include calls from other tests.
- The list is never cleared, so the test suite's behavior depends on method execution order.

**Suggested test:** Either clear `CALLS` in a `@BeforeEach` method or, better, assert dispatch/non-dispatch by filtering on the specific `accountUuid` rather than relying on total list size. For the redelivery test, assert that no call with the redelivered `accountUuid` and `notificationKind` was added after the second send.

---

## Gap 3 · No end-to-end integration test for `password_reset`

**Why it matters:** `package.md` §8 names two `auth.email.requested` tests (`shouldSendVerificationEmail...` and `shouldSendPasswordResetEmail...`). The unit test covers both, but the Kafka-backed integration test only exercises `verify_email`. A wiring or deserialization regression specific to the `password_reset` path would not be caught at the integration level.

**Suggested test:** Add an integration test method that produces `auth.email.requested(purpose=password_reset)` and asserts the idempotency record, projection update, and dispatch call (with the reset token) all occur correctly.

---

## Gap 4 · No test verifies the listener annotations' metadata

**Why it matters:** AC1 requires two real `@KafkaListener` methods, one per topic. The integration tests prove the wiring works by producing messages, but they do not lock the topic strings or the transaction annotation at the source-code level. A refactor that accidentally changed the topic string to a typo (e.g., `auth.email.request`) would not fail any existing test until the integration test is run against a real broker — and even then, the failure would look like a missing message, not a wrong topic.

**Suggested test:** Add reflection-based unit tests that inspect `AuthEventConsumer.class` and assert:
- `onEmailRequested` is annotated with `@KafkaListener(topics = "auth.email.requested", ...)`;
- `onUserLifecycle` is annotated with `@KafkaListener(topics = "auth.user.lifecycle", ...)`;
- both methods are annotated with `@Transactional`.

---

## Gap 5 · No test exercises malformed JSON / deserialization failure handling

**Why it matters:** The consumer methods declare `throws JsonProcessingException` and rely on the listener container's default error handler. A malformed message will trigger retries and eventually silence. This is the intended behavior, but there is no test that documents or locks it. A future change that accidentally swallowed deserialization exceptions would silently poison the consumer.

**Suggested test:** Add an integration test that sends a non-JSON string (or JSON missing a required field) to `auth.email.requested` and asserts that no idempotency record, projection update, or dispatch occurs for that message. This documents the "poison message is rejected" behavior without needing to assert the exact retry log output.

---

## Gap 6 · No test proves `status` is irrelevant to lifecycle routing

**Why it matters:** AC4's routing decision is explicitly based on `eventType`, not `status`, because multiple lifecycle transitions produce `status=ACTIVE`. The integration tests use `status=ACTIVE` for `user.registered` and `status=LOCKED` for `user.locked`, but they never vary `status` independently for the same `eventType`. A future regression that accidentally used `status` instead of `eventType` would not be caught.

**Suggested test:** Add a unit or integration test that calls `onUserLifecycle` (or produces a message) with `status=SUSPENDED` but `eventType=user.registered`, and assert that dispatch still occurs. Conversely, test `status=ACTIVE` with `eventType=user.reinstated` and assert no dispatch.

---

## Gap 7 · No test verifies the consumer still dispatches when projection upsert is rejected as stale

**Why it matters:** `ContactProjectionUpdater.upsertEmail` returns `boolean` (accepted/rejected). The consumer currently discards this value and always dispatches non-duplicate messages. This behavior is correct for T06, but it is untested. A future change that branched on the boolean (e.g., skipping dispatch on stale projection) would change behavior without failing a test.

**Suggested test:** Add a unit test that stubs `contactProjectionUpdater.upsertEmail(...)` to return `false` and asserts `notificationDispatcher.dispatch` is still called. This locks the current "dispatch regardless of projection freshness" semantics.

---

## Gap 8 · No test verifies the no-op dispatcher is a Spring bean

**Why it matters:** `NoOpNotificationDispatcher` is `@Component`-scanned and provides the only production implementation of `NotificationDispatcher` today. The integration tests override it with a `@Primary` spy, so they never exercise the real no-op bean in a Spring context. A refactor that removed `@Component` would only be caught by a full application startup test or by production.

**Suggested test:** Add a small `@SpringBootTest` (or extend an existing one) that autowires `NotificationDispatcher` and asserts the resolved bean is an instance of `NoOpNotificationDispatcher`. This is a low-priority gap because the integration tests do at least prove a dispatcher bean exists.

---

## Gap 9 · No test verifies `auto-offset-reset=latest` is configured

**Why it matters:** The brief explicitly chose `latest` to avoid replaying historical verification/reset/welcome events. This is an operational safety property, but it is only documented in `application.properties` and not asserted in tests.

**Suggested test:** Add an assertion in `ApplicationPropertiesJpaConfigTest` (or a new Kafka-config test) that the loaded `spring.kafka.consumer.auto-offset-reset` property equals `latest`. This prevents a future edit from accidentally switching it to `earliest`.

---

## Summary

The T06 test suite is now comprehensive: 20 new tests cover the 3 named routing tests, idempotency key format, non-dispatched-but-projected branches, duplicate short-circuit, real Kafka end-to-end consumption, transaction rollback atomicity, no-op dispatcher log safety, and schema format constraints. The strongest remaining gap is **Gap 1**: the manual mutation test that proves the idempotency guard is necessary is not encoded as an automated regression guard. **Gap 2** removes a latent fragility in the integration test's shared spy list. Gaps 3–9 are smaller precision, coverage, and configuration-locking items.
