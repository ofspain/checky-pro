<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T12 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T12 — Email channel (SES v2) |
| **Spec section** | Delivery orchestration / email channel |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T12 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T12 regression-guard tests against the acceptance criteria and the frozen brief's required test list.

---

## Gap 1 · No end-to-end integration test asserts an actual email is captured by `FakeEmailTransport`

**Why it matters:** `DeliveryOrchestratorIntegrationTest` (T11) still runs with the default `transport=fake` and therefore wires the real `EmailChannel` + `FakeEmailTransport`, but it only asserts `delivery_log` rows. It never inspects `FakeEmailTransport.sentMessages()`. A bug that caused `EmailChannel` to be bypassed or `FakeEmailTransport` to stop capturing would not be caught by the existing integration tests, even though the full path is exercised.

**Suggested test:** Extend `DeliveryOrchestratorIntegrationTest` (or add a new `EmailChannelIntegrationTest`) to autowire `FakeEmailTransport`, call `orchestrator.dispatch(...)` for a known notification kind, and assert:
- exactly one captured `EmailMessage` exists for the expected recipient;
- its `accountUuid`, `from`, `subject`, and `body` match the rendered template;
- the body contains the expected token/link placeholder substitution.

This is the single most load-bearing scenario for T12: proving a real event results in a real (fake-sent) email.

---

## Gap 2 · `EmailTransportStartupValidation` is tested in isolation, not as a startup guard

**Why it matters:** The test exercises the constructor directly, but it does not prove that Spring actually instantiates the validator as a `@Component` and that its failure message appears at startup, rather than the generic `NoSuchBeanDefinitionException` it was designed to prevent.

**Suggested test:** Add an `ApplicationContextRunner` test in `EmailTransportWiringTest` (or a new `EmailTransportStartupValidationIntegrationTest`) that includes `EmailTransportStartupValidation.class` in the user configuration, sets `transport=sendgrid`, and asserts that context startup fails with an `IllegalStateException` whose message contains `sendgrid`, `ses`, and `fake`.

---

## Gap 3 · No test proves `EmailChannel` is registered as a `NotificationChannel` bean

**Why it matters:** `DeliveryOrchestrator` injects `List<NotificationChannel>`. The wiring tests prove transport selection, but they do not prove that `EmailChannel` itself is discoverable as a `NotificationChannel` by Spring. A missing `@Component` or incorrect package would silently remove the EMAIL channel from the orchestrator's list.

**Suggested test:** Add to `EmailTransportWiringTest` (or a new `EmailChannelWiringTest`) an `ApplicationContextRunner` that registers `EmailChannel.class` + a fake `EmailTransport` bean + `EmailProperties`, and asserts `context.getBeansOfType(NotificationChannel.class)` contains a bean whose `channel()` is `"EMAIL"`.

---

## Gap 4 · No test proves `NoOpEmailChannel` no longer exists in the Spring context

**Why it matters:** `NoOpEmailChannel` was deleted, but a stray class file, stale test dependency, or accidental reintroduction would cause two EMAIL-channel beans and break `DeliveryOrchestrator`'s `Collectors.toMap` collector at startup.

**Suggested test:** Add an `ApplicationContextRunner` test that scans the real `com.themistra.notification.channel` package (or at least registers all classes found there) and asserts that exactly one bean returns `"EMAIL"` from `channel()`. Alternatively, keep relying on `T01SkeletonRegressionTest`'s file inventory, but note that file inventory does not catch stale compiled classes.

---

## Gap 5 · Runtime log safety is only guarded by static source inspection

**Why it matters:** `EmailChannelTest.successLogLineNeverReferencesTheRecipientVariable` and `EmailMessageTest.toStringExcludesTheRecipientAddressSubjectAndBody` parse source code. This is a fast, permanent guard, but it does not prove the log appender receives no PII at runtime if a future refactor introduces a conditional branch or a different log statement.

**Suggested test:** Add a runtime log-capture test (using a library like `uk.org.lidalia:slf4j-test` or a custom appender) that calls `EmailChannel.send(...)` and asserts the captured log event's formatted message contains neither the recipient email address nor any token from the body. This complements, not replaces, the static guards.

---

## Gap 6 · No test exercises the real `EmailChannel` + `FakeEmailTransport` wiring without `DeliveryOrchestrator`

**Why it matters:** All integration tests that touch `EmailChannel` today go through `DeliveryOrchestrator`. A direct test of `EmailChannel` as a Spring bean would isolate channel-specific behavior (validation, logging, transport delegation) from orchestrator logic.

**Suggested test:** A lightweight `@SpringBootTest`-style test (or `ApplicationContextRunner`) that autowires `NotificationChannel` beans, finds the EMAIL channel, casts it to `EmailChannel`, calls `send(...)` with a real `FakeEmailTransport` in the context, and asserts the captured message. This also closes Gap #3.

---

## Gap 7 · `FakeEmailTransport.findMostRecentByRecipient` is not tested under concurrent sends

**Why it matters:** The concurrent-send test proves no messages are lost, but it does not prove that `findMostRecentByRecipient` returns a consistent, correct latest message when multiple sends to the same recipient happen concurrently.

**Suggested test:** Add a concurrent test where multiple threads send to the same recipient with distinct subjects, then call `findMostRecentByRecipient` and assert the returned subject is one of the sent subjects and that the call itself does not throw or return a partially-constructed `EmailMessage`.

---

## Gap 8 · No test verifies the failure mode when both transports are somehow present

**Why it matters:** `DeliveryOrchestrator` uses `Collectors.toMap(NotificationChannel::channel, ...)` with no merge function, so two beans returning `"EMAIL"` would throw `IllegalStateException` at startup. The `@ConditionalOnProperty` wiring is designed to prevent this, but there is no test that exercises the failure mode if the guard is ever bypassed.

**Suggested test:** Add an `ApplicationContextRunner` test that registers both `FakeEmailTransport` and `SesEmailTransport` (with a mocked `SesV2Client` bean) without any conditional property restrictions and asserts that context startup fails due to the duplicate EMAIL channel. This is a defensive test for the wiring contract.

---

## Gap 9 · No test verifies `EmailProperties` still binds and validates in the real context

**Why it matters:** `EmailChannelTest` constructs `EmailProperties` manually. `EmailTransportStartupValidationTest` also constructs it manually. No test boots the context and asserts that `EmailProperties.from()` and `transport()` are bound from `application.properties`.

**Suggested test:** Add an assertion to an existing integration test (e.g., `DeliveryOrchestratorIntegrationTest`) that autowires `EmailProperties` and checks `from()` equals the configured value. This is a small guard against a future properties-binding regression.

---

## Summary

The T12 test suite is now comprehensive at the unit level: 31 new tests cover `EmailMessage` safe `toString()`, `FakeEmailTransport` capture/clear/concurrency, `SesEmailTransport` request construction and exception sanitization, `EmailChannel` validation/delegation/log-shape, transport-property startup validation, and conditional bean wiring. The Phase 8 findings (missing tests, PII logging, request construction outside try/catch, body validation, fake semantics, invalid transport error message) are all addressed.

The remaining gaps are mostly integration-level or defensive-wiring concerns. **Gap 1** is the most important: without an integration test that inspects `FakeEmailTransport.sentMessages()`, the task cannot claim to have proven that a real end-to-end event results in a captured email. **Gap 2** ensures the startup validator actually runs in context. Gaps 3–9 are smaller robustness items.
