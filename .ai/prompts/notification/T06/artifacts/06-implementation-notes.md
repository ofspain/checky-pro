# notification · T06 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) with all 9 Phase 3 findings
folded in. `src/main` files created per "Files to Create", plus the two contract tests the frozen
brief itself named there (Finding #6's own required carve-out — not a Phase 10 addition), plus one
justified, undisclosed-in-the-brief `T01SkeletonRegressionTest.java` update (same recurring gap
T04/T05 each hit before this task).

## Files created

- `consumer/dto/EmailRequestedEvent.java` / `consumer/dto/UserLifecycleEvent.java` — hand-written
  deserialization records (Finding #6, disclosed deviation from `agents.md`'s codegen rule, mirrors
  `services/auth`'s own producer-side payload records for the identical, already-established
  reason). Both `toString()`-safe: `EmailRequestedEvent` excludes `token` (Finding #4/L4, mirrors
  `services/auth`'s own `EmailRequestedEventPayload`); `UserLifecycleEvent` carries no secret field.
- `consumer/NotificationDispatcher.java` — the seam interface, `dispatch(UUID accountUuid, String
  notificationKind, Map<String, String> eventData)`. Javadoc documents why `email` is intentionally
  omitted (Finding #8): a future implementation resolves the recipient via `contact_projection`,
  which `AuthEventConsumer`'s own `upsertEmail` call (same transaction, runs first) keeps at least
  as fresh as the event allows.
- `consumer/NoOpNotificationDispatcher.java` (renamed per Finding #9) — real, working implementation
  (AC6): logs and returns, no exception, no `TODO`. Logs only `accountUuid`/`notificationKind`/
  `eventData.keySet()` — never `eventData`'s own values (Finding #4/AC9).
- `consumer/AuthEventConsumer.java` — two `@KafkaListener` methods (AC1), one per topic. Both
  `@Transactional` default `REQUIRED` (Finding #3/AC8): dedupe → projection-refresh → dispatch run
  atomically, so a future failing `dispatch` rolls back the idempotency record too. Idempotency key
  format pinned exactly as Finding #1/AC7: `accountUuid + ":" + purpose/eventType + ":" +
  occurredAt`, using the deserialized `Instant`'s own `toString()`. `recordIfNew` returning `false`
  short-circuits before any projection update or dispatch call (AC2). Unknown `purpose` values and
  non-`user.registered` `eventType` values are still dedup-recorded and projection-refreshed but
  never dispatched (Finding #2, AC4).
- `consumer/dto/EmailRequestedEventContractTest.java` / `consumer/dto/UserLifecycleEventContractTest.java`
  — required per the frozen brief's own explicit Files-to-Create carve-out (Finding #6), not
  deferred to Phase 10. Mirror `services/auth`'s own `*PayloadContractTest` pattern: schema-match,
  known-value-set deserialization, and (for the email DTO) the no-raw-token-in-`toString()` proof
  (AC9's DTO-level counterpart).

## Files modified

- `application.properties` — added `spring.kafka.consumer.group-id=notification-service` and
  `spring.kafka.consumer.auto-offset-reset=latest` (Finding #5), with the rationale comment the
  finding required; trimmed the now-stale "no consumer exists yet" comment from the pre-existing
  `bootstrap-servers` block.
- `T01SkeletonRegressionTest.java` — **not named in the frozen brief's own Files to Modify list**, a
  real gap discovered during implementation (the same class of gap T04 and T05 each hit in turn:
  every feature task's new production files break this test's exact-file-list assertion, and the
  brief-freezing phase has never yet anticipated it in advance). Fixed as a required, narrow,
  disclosed deviation: `noExtraProductionClassesExistBeyondT05sOwnAuthorizedSet` renamed to
  `...T06sOwnAuthorizedSet`; Javadoc updated to describe T06's own 5 new files; the 15-file
  `containsExactly(...)` list widened to 20, inserting `consumer/AuthEventConsumer.java`,
  `consumer/NoOpNotificationDispatcher.java`, `consumer/NotificationDispatcher.java`,
  `consumer/dto/EmailRequestedEvent.java`, `consumer/dto/UserLifecycleEvent.java` in their real
  sorted positions (verified empirically via the actual `Files.walk(...).sorted()` test run, not
  guessed — uppercase class-name files under `consumer/` all sort before the lowercase `consumer/dto/`
  subdirectory's own two files in Java's default `String` ordering).

## Verification performed

- `mvn -pl services/notification clean verify` — **98 tests, 0 failures, 0 errors**, clean
  `package`/`repackage` (up from T05's own 92; +6 = the two new contract tests' own 3+3 methods
  each). Confirms both `@KafkaListener` methods bind and the consumer group joins the local broker
  cleanly (`IdempotencyGuardIntegrationTest` — the only Testcontainers/Kafka-backed suite this task
  touches — passed, 6/6, alongside every other pre-existing test, unchanged).
- Both new contract tests pass clean, proving `EmailRequestedEvent`/`UserLifecycleEvent`'s own
  serialization matches the real schema files (`contracts/events/auth/*.v1.schema.json`) exactly,
  and that `EmailRequestedEvent.toString()` never contains a raw token value.

## Acceptance criteria mapping

- **AC1** — `AuthEventConsumer` has exactly two `@KafkaListener` methods, `onEmailRequested`
  (topic `auth.email.requested`) and `onUserLifecycle` (topic `auth.user.lifecycle`), each
  deserializing via `objectMapper.readValue(rawJson, ...)` into the matching DTO. ✅
- **AC2** — both methods call `idempotencyGuard.recordIfNew(...)` first and `return` immediately on
  `false`, before any `contactProjectionUpdater`/`notificationDispatcher` call. ✅
- **AC3** — both methods call `contactProjectionUpdater.upsertEmail(event.accountUuid(),
  event.email(), event.occurredAt())` for every non-duplicate message, purpose/eventType-agnostic. ✅
- **AC4** — `onEmailRequested`'s own `switch` resolves `verify_email`/`password_reset` to matching
  `notificationKind` values and returns (no dispatch) for any other `purpose`; `onUserLifecycle`
  only dispatches on `eventType.equals("user.registered")`, returning otherwise. ✅
- **AC5** — `notificationDispatcher.dispatch(...)` is called with `event.accountUuid()`, the
  resolved `notificationKind`, and: `Map.of("token", event.token())` for both email-requested cases;
  `Map.of()` for `user.registered`. ✅
- **AC6** — `NoOpNotificationDispatcher` is a real `@Component` that logs and returns; no exception,
  no `TODO`. ✅
- **AC7** — idempotency key format matches Finding #1's own pinned shape exactly, both listeners. ✅
- **AC8** — both listener methods carry `@Transactional` (default `REQUIRED`). ✅
- **AC9** — proven by `EmailRequestedEventContractTest.toStringExcludesTheRawToken`; the same
  discipline applies to `NoOpNotificationDispatcher`'s own log statement, which never logs
  `eventData`'s values. ✅
