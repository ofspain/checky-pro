<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T12 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T12 — Email channel (SES v2) |
| **Spec section** | Delivery orchestration / email channel |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T12 implementation. Findings only.

---

## Finding 1 · No `EmailChannel` or `SesEmailTransport` tests exist

**Severity:** High

**Issue:** T12 introduces five new production files (`EmailChannel`, `EmailTransport`, `EmailMessage`, `EmailDeliveryException`, `SesEmailTransport`, `FakeEmailTransport`, plus `SesClientConfig`) and deletes `NoOpEmailChannel`. The only test in the `channel` package is `NoOpInAppChannelTest`. There are no tests for `EmailChannel.channel()`, real-transport request construction, fake-transport capture, exception propagation, `EmailProperties.from()` usage, `EmailMessage.toString()` safety, or exception sanitization.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/channel/` contains only `NoOpInAppChannelTest.java`.
- `EmailChannelTest`, `SesEmailTransportTest`, and `FakeEmailTransportTest` do not exist.
- The frozen brief lists required tests: channel value, mocked SES client real-transport mode, fake capture, exception propagation, `from` sourced from config.

**Recommendation:** Add the missing tests before considering T12 complete:
- `EmailChannelTest`: channel value, validation failures, delegates to `EmailTransport` with correct `EmailMessage`, propagates exceptions, sources `from` from config.
- `SesEmailTransportTest` (mocked `SesV2Client`): builds correct `SendEmailRequest`, returns `messageId`, converts `SdkException` to `EmailDeliveryException` with sanitized message and no cause, handles `AwsServiceException` vs. client-side exception differently.
- `FakeEmailTransportTest`: captures sent messages, `clear()` works, `findByRecipient` works, concurrent sends are captured safely.
- `EmailMessageTest`: `toString()` excludes subject and body content.

**Confidence:** High

---

## Finding 2 · `EmailChannel` logs the recipient email address, which is PII

**Severity:** High

**Issue:** `EmailChannel.send` logs `accountUuid`, `recipient`, and `messageId` at `INFO`. `agents.md` states: "Never log tokens, secrets, reset-token values, full API keys, or PII." The recipient email address is PII. This is a standing-rule violation, even though the brief's AC5 only explicitly forbids secrets/tokens/API keys.

**Evidence:**
- `channel/EmailChannel.java` line 50: `log.info("Email sent: accountUuid={}, recipient={}, messageId={}", accountUuid, recipient, messageId);`
- `agents.md` line 54: "Never log tokens, secrets, reset-token values, full API keys, or PII."

**Recommendation:** Remove `recipient` from the log line. Log only `accountUuid` and `messageId` (and possibly template name/version if useful). The `delivery_log` already records the recipient for dispute purposes; the application log does not need it.

**Confidence:** High

---

## Finding 3 · `SesEmailTransport` request construction is outside the `try/catch`

**Severity:** Medium

**Issue:** `SendEmailRequest` is built before the `try` block. A `RuntimeException` during construction (e.g., a null `body` reaching `Content.builder().data(null)`) escapes as a raw, unsanitized exception, violating the class's own contract to never let a raw exception escape.

**Evidence:**
- `channel/SesEmailTransport.java` lines 46–57: request construction precedes the `try` at line 59.
- `EmailChannel.validate` (line 53–59) checks `recipient` and `message.subject()` but not `message.body()`.

**Recommendation:** Either:
1. Move the request-builder chain inside the `try` block so any construction failure is caught and converted to `EmailDeliveryException`, or
2. Add a null/blank `body` check in `EmailChannel.validate` so an invalid `EmailMessage` never reaches `SesEmailTransport`.

Option 1 is safer because it protects against any future builder failure mode, not just null body.

**Confidence:** High

---

## Finding 4 · `EmailChannel` does not validate `message.body()`

**Severity:** Low/Medium

**Issue:** As noted in the self-review, `EmailChannel.validate` checks `recipient` and `subject` but not `body`. `TemplateRenderer` currently guarantees a non-null body, but a future bug there would surface as a cryptic builder failure deep in `SesEmailTransport` rather than a clear `IllegalArgumentException` at the channel boundary.

**Evidence:**
- `channel/EmailChannel.java` lines 53–59: only `recipient` and `subject` are validated.
- `channel/SesEmailTransport.java` line 53: `message.body()` is passed directly to `Content.builder().data(...)`.

**Recommendation:** Add `message.body() == null || message.body().isBlank()` to `EmailChannel.validate` with a clear error message, or document in `EmailChannel`'s Javadoc why `body` is trusted. Adding the check is cheap and consistent with the existing validation style.

**Confidence:** Medium

---

## Finding 5 · `FakeEmailTransport.findByRecipient` semantics are unclear and inefficient

**Severity:** Low

**Issue:** `findByRecipient` iterates the entire list and overwrites `match` on every match, so it returns the *last* message to that recipient, not the first. With a `CopyOnWriteArrayList` this is the most recent send. The method name does not imply "most recent," and the loop is O(n) even after finding a match.

**Evidence:**
- `channel/FakeEmailTransport.java` lines 42–50: loop continues after finding a match.

**Recommendation:** Decide and document the intended semantics. If "most recent" is correct, rename to `findMostRecentByRecipient` and break early by iterating in reverse or returning the last element. If "first" is correct, break after the first match. Either way, add tests.

**Confidence:** Low

---

## Finding 6 · `FakeEmailTransport` does not capture `accountUuid`

**Severity:** Low

**Issue:** `EmailChannel.send` receives `accountUuid`, but only `recipient`, `from`, `subject`, and `body` are placed into `EmailMessage` and captured by `FakeEmailTransport`. Tests cannot assert which account a captured message belonged to without correlating via recipient.

**Evidence:**
- `channel/EmailChannel.java` lines 46–47: `EmailMessage` is built from `recipient`, `from`, `subject`, `body` only.
- `channel/EmailMessage.java`: record has no `accountUuid` field.

**Recommendation:** Either add `accountUuid` to `EmailMessage` (if useful for testing and logging) or document that tests must correlate by recipient. Adding it would also let `EmailChannel` log `accountUuid` while removing `recipient` (Finding #2).

**Confidence:** Low

---

## Finding 7 · `SesEmailTransport` does not log at all, making production debugging harder

**Severity:** Low

**Issue:** On success, `SesEmailTransport` returns the `messageId` but logs nothing. On failure, it throws a sanitized `EmailDeliveryException` without logging. While `DeliveryOrchestrator` logs the exception, there is no channel-level log for success or for the fact that an SES call was attempted.

**Evidence:**
- `channel/SesEmailTransport.java`: no `log` field or log statements.

**Recommendation:** Add a `DEBUG`-level log line in `SesEmailTransport` before calling SES ("sending email via SES to recipient count=1") and on success ("SES returned messageId={}"). Ensure these logs never include subject/body/token. This improves observability without violating AC5 or `agents.md`.

**Confidence:** Low

---

## Finding 8 · Invalid `transport` values produce a generic Spring DI error at startup

**Severity:** Low

**Issue:** As noted in the self-review, any value other than exactly `ses` or `fake` causes an unsatisfied-dependency failure with a message that does not name the misconfigured property or its allowed values.

**Evidence:**
- `channel/SesEmailTransport.java` and `channel/FakeEmailTransport.java` use `@ConditionalOnProperty(havingValue = "...")`.
- `common/config/SesClientConfig.java` also uses `@ConditionalOnProperty(havingValue = "ses")`.
- No validator exists for `themistra.notification.email.transport`.

**Recommendation:** Add a startup validator (mirroring `LinkPropertiesStartupValidation`) that asserts `transport` is exactly `ses` or `fake` and produces a clear failure message. This is an operability improvement, not a correctness blocker.

**Confidence:** Low

---

## Finding 9 · The external network call holds a DB connection/transaction open

**Severity:** Medium

**Issue:** As noted in the self-review, `EmailChannel.send` performs a synchronous AWS SES HTTP call inside `DeliveryOrchestrator`'s `@Transactional` boundary. This holds a database connection and transaction open for the duration of the HTTP call, increasing connection-pool pressure and lock duration.

**Evidence:**
- `delivery/DeliveryOrchestrator.java` line 109: `dispatch` is `@Transactional`.
- `channel/EmailChannel.java` line 48: `emailTransport.send(emailMessage)` is synchronous and network-bound.

**Recommendation:** Document this as an accepted architectural consequence of T11's design (as the self-review does). A real fix (outbox, async send, or moving the send outside the transaction) is out of T12's scope but should be tracked for a future task if SES latency becomes an operational issue.

**Confidence:** Medium

---

## Finding 10 · Duplicate send risk if the transaction aborts after a successful SES call

**Severity:** Medium

**Issue:** Also noted in the self-review: if the surrounding transaction rolls back after SES has sent the email (e.g., Kafka rebalance abort), the idempotency record is rolled back too, so a redelivery causes a second real email. This is the first task where this has real consequences.

**Evidence:**
- `delivery/DeliveryOrchestrator.java`: `dispatch` is `@Transactional` and calls `EmailChannel.send` synchronously.
- `consumer/AuthEventConsumer.java`: listener methods are `@Transactional` and call `dispatch` after idempotency/projection writes.

**Recommendation:** Document as an accepted risk. The duplicate is low-harm for `verify_email`/`password_reset` (same valid link). A real exactly-once solution would require redesigning T11's transaction boundary or adding an outbox — out of T12 scope.

**Confidence:** Medium

---

## Summary

The T12 implementation is clean and follows the frozen brief: `EmailChannel` delegates to a transport seam, exactly one transport bean is active per profile, the fake is thread-safe, SES exceptions are sanitized, and `NoOpEmailChannel` is removed. The dominant issue is **Finding #1: no T12-specific tests exist**, leaving the new code unguarded. **Finding #2** is a genuine `agents.md` violation (PII in logs) and should be fixed. **Finding #3** closes a real sanitization hole in `SesEmailTransport`. Findings #4–8 are smaller quality/observability items. Findings #9 and #10 are architectural risks inherited from T11 that should be disclosed rather than fixed in this task.
