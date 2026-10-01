<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T14 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T14 — Bounded retry + dead-letter |
| **Spec section** | Retry / dead-letter |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T14 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T14 regression-guard tests against the acceptance criteria and the frozen brief's required test list.

---

## Gap 1 · No `RetrySchedulerIntegrationTest` exists for the actual `@Scheduled`/`@SchedulerLock` behavior

**Why it matters:** `RetrySchedulerTest`'s own Javadoc states that "`sweep`'s own real timing/ShedLock behavior is proven at the integration level (`RetrySchedulerIntegrationTest`)." That file does not exist in the repository. The `@Scheduled` annotation, the `fixedDelayString` property binding, the ShedLock acquisition, and the real-time polling of due rows are therefore unproven in an automated test.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/delivery/` contains only `DeliveryOrchestratorIntegrationTest`; no `RetrySchedulerIntegrationTest.java`.
- `RetrySchedulerTest` mocks the repository and calls `processOne` and `sweep` directly.

**Suggested test:** Add `RetrySchedulerIntegrationTest` (Testcontainers Postgres, real Spring context, fixed `Clock`) that:
- inserts a `delivery_retry` row with `next_attempt_at` in the past;
- uses Awaitility to wait for the real `@Scheduled` sweep to run;
- asserts the row is processed (deleted on success or rescheduled) and that ShedLock inserted/updated its own row.

Alternatively, if real-time waiting is too flaky, use `@SpyBean` on `RetryScheduler` and verify `processOne` is invoked by the scheduled method at least once.

---

## Gap 2 · No structural test proves `@SchedulerLock` is present on `RetryScheduler.sweep`

**Why it matters:** AC6 requires the scheduled method to be ShedLock-guarded. The existing tests verify `@EnableSchedulerLock` on the application class but do not verify the annotation on the actual method. A missing `@SchedulerLock` would allow multiple replicas to run sweeps concurrently.

**Evidence:**
- `RetrySchedulerTest` does not inspect annotations.
- `T01SkeletonRegressionTest` checks `@EnableScheduling`/`@EnableSchedulerLock` at the application level, not per-method.

**Suggested test:** Add a reflection/static test in `RetrySchedulerTest` that asserts `RetryScheduler.sweep()` is annotated with `@SchedulerLock` and that its `name` is non-empty.

---

## Gap 3 · `RetrySchedulerTest` does not verify `retryRepository.save(retry)` is called on reschedule

**Why it matters:** The implementation's own comment notes that a missing `save` caused a real integration-test failure because the `DeliveryRetry` entity is detached when `processOne` runs. The unit test currently mutates the in-memory object and asserts on its fields, which passes even if `save` is never called.

**Evidence:**
- `RetrySchedulerTest.java` lines 103–114: `transientFailureBringingTheTotalToTwoAttemptsReschedulesAtDoubleTheInitialBackoff` asserts `retry.getAttempt()` and `retry.getNextAttemptAt()` but never verifies `retryRepository.save(retry)`.

**Suggested test:** Add `verify(retryRepository).save(retry)` to the reschedule tests. This is a one-line addition that locks the actual persistence behavior.

---

## Gap 4 · No test proves `RetryScheduler.sweep` only processes due rows

**Why it matters:** The repository method `findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc` is mocked in `sweepSkipsAFailingRowWithoutAbortingTheRest`. There is no test that boots the context, inserts a future-due row and a past-due row, and asserts only the past-due row is passed to `processOne`.

**Evidence:**
- `RetrySchedulerTest` mocks the repository return value.
- `DeliveryOrchestratorIntegrationTest` calls `processOne` directly.

**Suggested test:** Add to `RetrySchedulerIntegrationTest` (or a new repository integration test) two rows with different `next_attempt_at` values and assert the sweep processes only the one in the past.

---

## Gap 5 · No test proves the sweep processes rows oldest-first

**Why it matters:** The repository query is ordered by `next_attempt_at ASC, id ASC`, but no test asserts this ordering is respected in practice. A bug in the method name or Spring Data derivation could silently remove ordering.

**Evidence:**
- `RetrySchedulerTest` mocks the repository and only checks that two rows are returned.

**Suggested test:** In an integration test, insert two past-due rows with different `next_attempt_at` values and spy `RetryScheduler.processOne` to verify it is called in the expected order.

---

## Gap 6 · No test covers `DeliveryOrchestrator.replay` guard failures other than unknown notification kind

**Why it matters:** `replay` has several early-return guards: null template name, disabled preference, missing email, render failure, missing channel bean. Only unknown notification kind and disabled preference are tested. A regression in the other guards would not be caught by the unit suite.

**Evidence:**
- `DeliveryOrchestratorTest` has `replayWithAnUnknownNotificationKindWritesFailedAndReturnsPermanentFailure` and `replayHonorsAPreferenceDisabledSinceTheOriginalAttemptAndReturnsSuppressed`.
- No tests for: null template name (only a static source check), missing email, render failure, missing channel bean.

**Suggested tests:**
- `replayWithMissingEmailWritesFailedAndReturnsPermanentFailure`
- `replayWithRenderFailureWritesFailedAndReturnsPermanentFailure`
- `replayWithMissingChannelBeanWritesFailedAndReturnsPermanentFailure`

These mirror the existing `dispatchOneChannel` guard tests and close the replay coverage gap.

---

## Gap 7 · No test covers one channel succeeding while the other fails transiently

**Why it matters:** `bothChannelsFailingTransientlyScheduleTwoIndependentRetryRows` proves two failures create two rows. It does not prove that a success on one channel does not interfere with retry scheduling for the other.

**Evidence:**
- `DeliveryOrchestratorTest` lines 642–659: both channels fail.

**Suggested test:** Add a test where EMAIL succeeds and IN_APP fails transiently, asserting one `SENT` row for EMAIL and one `delivery_retry` row for IN_APP.

---

## Gap 8 · No test covers `recordUnrecoverableFailure`'s redaction behavior

**Why it matters:** AC8 requires error detail persisted on retry rows to be redacted via `SecretSafeLogging.redact()`. `recordUnrecoverableFailure` passes raw detail to `save`, which redacts it, but no test asserts the persisted value is redacted.

**Evidence:**
- `DeliveryOrchestratorTest` line 846–857: `recordUnrecoverableFailureWritesADeadLetteredRowAtTheGivenAttempt` asserts `errorDetail` equals the raw string passed in.

**Suggested test:** Pass a secret-shaped detail (e.g., `"token=abc123"`) and assert the saved row's `errorDetail` is `"token=***"`.

---

## Gap 9 · No test covers `scheduleFirstRetry`'s serialization failure handling

**Why it matters:** The brief says `scheduleFirstRetry` is best-effort and must never throw. If `ObjectMapper.writeValueAsString` fails, the exception is caught and logged, but no test locks this behavior.

**Evidence:**
- `DeliveryOrchestrator.java` lines 287–298: `scheduleFirstRetry` catches `Exception` and logs.
- No test in `DeliveryOrchestratorTest` exercises this path.

**Suggested test:** Mock `objectMapper` to throw `JsonProcessingException` during `dispatch` and assert no exception propagates and no retry row is inserted.

---

## Gap 10 · No test covers `DeliveryOrchestrator.replay` for the `IN_APP` channel

**Why it matters:** Every replay test uses `EMAIL`. While the code is channel-agnostic, an `IN_APP`-specific replay test would prove that `accountUuid.toString()` is used as recipient and that the in-app channel is invoked.

**Evidence:**
- `DeliveryOrchestratorTest` replay tests all pass `channel = "EMAIL"`.

**Suggested test:** Add `replaySuccessForInAppChannelWritesSentRow` verifying `inAppChannel.send(...)` is called and the saved row has the correct recipient.

---

## Summary

The T14 test suite is now very strong: unit tests cover first-retry scheduling, channel-agnostic scheduling, both channels failing, permanent vs. transient classification, immediate dead-lettering for `maxAttempts=1`, all five `replay` outcomes, backoff values at multiple attempts, the backoff cap, poison-pill handling, event-data deserialization, sweep skip-on-failure, the non-exhaustive-switch guard, property binding/validation, and the two `package.md` §8 named end-to-end integration tests (R12/R13). Phase 8 findings are addressed in both production code and tests (`@Max(62)`, default switch arm, displayName re-resolution, templateName guard).

The most important remaining gap is **Gap 1**: the claimed `RetrySchedulerIntegrationTest` does not exist, leaving the real `@Scheduled`/`@SchedulerLock` behavior untested. **Gap 3** is a close second: the reschedule unit test does not verify the actual `save` call. Gaps 4–10 are smaller coverage items.
