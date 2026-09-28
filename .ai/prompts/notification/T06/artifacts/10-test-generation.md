# notification · T06 · Phase 10 — Test Generation

All tests deferred at Phase 9 (Kimi Phase 8 Findings #1–#6, all verified true) are added here, plus
`package.md` §8's 3 named tests (`shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
`shouldSendPasswordResetEmailOnAuthEmailRequestedReset`, `shouldWelcomeUserOnUserRegistered`).
4 new test files, 2 modified test files, 20 new tests (118 total: 98 T01–T05/Phase-6 unaffected +
20 new).

## Files created

- `consumer/AuthEventConsumerTest.java` — 10 tests, mocked `ObjectMapper`/`IdempotencyGuard`/
  `ContactProjectionUpdater`/`NotificationDispatcher`, no Spring context, no Docker. Covers the 3
  named routing tests, the exact idempotency key format for both topics (Finding #1), the
  non-dispatched-but-projected branches for both topics (Finding #3), and the idempotent
  short-circuit for both topics (Finding #3's own duplicate-delivery half).
- `consumer/AuthEventConsumerIntegrationTest.java` — 4 tests, `@Testcontainers` + `@SpringBootTest`
  against the shared local Kafka broker + a Testcontainers Postgres (the third test class in this
  module needing a real Spring context, after T04/T05's own precedents). A committed, permanent
  version of the Phase 6/7 scratch test (Finding #2): produces real messages to both topics,
  confirms consume → dedupe → project → dispatch (or not), and that redelivery does not re-dispatch.
  Uses its own `group-id` (`auth-event-consumer-it`) so it never contends with another test class's
  consumer group.
- `consumer/AuthEventConsumerTransactionRollbackIntegrationTest.java` — 1 test, its own Spring
  context (own `group-id`: `auth-event-consumer-rollback-it`), a `@Primary` always-throwing
  `NotificationDispatcher` test bean (Finding #4). Proves a failing `dispatch` rolls back both the
  idempotency record and the projection write - the atomicity claim in `AuthEventConsumer`'s own
  class-level Javadoc, unproven by any test before this phase. Kept as a separate class (not a
  method in `AuthEventConsumerIntegrationTest`) because a throwing `@Primary` dispatcher bean cannot
  coexist in the same Spring context as the spy `@Primary` dispatcher bean the other 4 tests need.
- `consumer/NoOpNotificationDispatcherTest.java` — 3 tests, a Logback `ListAppender` attached
  directly to the dispatcher's own logger (Finding #5). Proves the raw token value never appears in
  the formatted log output, that only the event-data key set is logged, and that
  `accountUuid`/`notificationKind` are both present (a log line with none of that would be useless
  for debugging, the flip side of the same test).

## Files modified

- `consumer/dto/EmailRequestedEventContractTest.java` — added
  `serializedFieldsConformToTheSchemasFormatConstraints` (Finding #6): `accountUuid` round-trips
  through `UUID.fromString`, `email` contains `@`, `occurredAt` round-trips through `Instant.parse`.
- `consumer/dto/UserLifecycleEventContractTest.java` — same addition; Finding #6's own enum-values
  half was already covered by the pre-existing `everyStatusValueInTheSchemaEnumDeserializesCleanly`.

## Test manifest

| Test method | Verifies | Finding / AC |
|---|---|---|
| `AuthEventConsumerTest.shouldSendVerificationEmailOnAuthEmailRequestedVerify` | `verify_email` routes to the matching dispatch call | `package.md` §8, AC4/AC5 |
| `AuthEventConsumerTest.shouldSendPasswordResetEmailOnAuthEmailRequestedReset` | `password_reset` routes to the matching dispatch call | `package.md` §8, AC4/AC5 |
| `AuthEventConsumerTest.shouldWelcomeUserOnUserRegistered` | `user.registered` routes to the matching dispatch call | `package.md` §8, AC4/AC5 |
| `AuthEventConsumerTest.idempotencyKeyForEmailRequestedIsAccountUuidPurposeOccurredAt` | Exact key format, email-requested topic | Finding #1, AC7 |
| `AuthEventConsumerTest.idempotencyKeyForUserLifecycleIsAccountUuidEventTypeOccurredAt` | Exact key format, lifecycle topic | Finding #1, AC7 |
| `AuthEventConsumerTest.unknownPurposeIsProjectedButNeverDispatched` | Unknown purpose deduped/projected, not dispatched | Finding #3, AC4 |
| `AuthEventConsumerTest.nonRegisteredEventTypeIsProjectedButNeverDispatched` | Non-registration eventType deduped/projected, not dispatched | Finding #3, AC4 |
| `AuthEventConsumerTest.duplicateEmailRequestedEventUpdatesNeitherProjectionNorDispatcher` | Guard `false` short-circuits everything, email-requested | Finding #3, AC2 |
| `AuthEventConsumerTest.duplicateLifecycleEventUpdatesNeitherProjectionNorDispatcher` | Guard `false` short-circuits everything, lifecycle | Finding #3, AC2 |
| `AuthEventConsumerTest.projectionIsRefreshedForADispatchedEmailRequestedEventToo` | Projection update is purpose-agnostic | AC3 |
| `AuthEventConsumerIntegrationTest.emailRequestedVerifyEmailIsConsumedDedupedProjectedAndDispatched` | Real end-to-end wiring, verify_email + redelivery no-redispatch | Finding #2 |
| `AuthEventConsumerIntegrationTest.unknownPurposeIsDedupedAndProjectedButNotDispatched` | Real end-to-end, unknown purpose | Finding #2/#3 |
| `AuthEventConsumerIntegrationTest.userRegisteredLifecycleEventIsConsumedDedupedProjectedAndDispatched` | Real end-to-end, user.registered | Finding #2 |
| `AuthEventConsumerIntegrationTest.nonRegisteredLifecycleEventTypeIsDedupedAndProjectedButNotDispatched` | Real end-to-end, user.locked | Finding #2/#3 |
| `AuthEventConsumerTransactionRollbackIntegrationTest.failingDispatchRollsBackTheIdempotencyRecordAndProjectionUpdate` | `@Transactional` atomicity across all 3 writes | Finding #4 |
| `NoOpNotificationDispatcherTest.dispatchNeverLogsTheRawTokenValue` | L4 compliance in real log output | Finding #5, AC9 |
| `NoOpNotificationDispatcherTest.dispatchLogsTheEventDataKeySetOnly` | Key-set-only logging | Finding #5 |
| `NoOpNotificationDispatcherTest.dispatchLogsAccountUuidAndNotificationKind` | Log line is still useful for debugging | Finding #5 |
| `EmailRequestedEventContractTest.serializedFieldsConformToTheSchemasFormatConstraints` | UUID/email/date-time format conformance | Finding #6 |
| `UserLifecycleEventContractTest.serializedFieldsConformToTheSchemasFormatConstraints` | UUID/email/date-time format conformance | Finding #6 |

## Negative-proof (mutation testing)

Replaced `if (!idempotencyGuard.recordIfNew(eventKey, event.purpose()))` in `onEmailRequested` with
`if (false)` (removing the dedupe short-circuit and the call itself), re-ran
`AuthEventConsumerTest` alone: exactly 2 tests failed -
`duplicateEmailRequestedEventUpdatesNeitherProjectionNorDispatcher` (the behavior the mutation
breaks) and `idempotencyKeyForEmailRequestedIsAccountUuidPurposeOccurredAt` (the mutation also
deletes the `recordIfNew` call the key-format assertion depends on) - the other 8 tests still
passed, confirming the mutation's blast radius was caught precisely, not accidentally over- or
under-covered. Reverted (`git checkout --`); `git status -s` on the file empty afterward; the
targeted test re-ran clean (10/10).

## A disclosed, informational characteristic (not a defect)

`AuthEventConsumerTransactionRollbackIntegrationTest`'s always-throwing dispatcher causes Spring
Kafka's own default error handler to retry the poisoned record (frozen brief Finding #7's own
already-accepted behavior: exceptions propagate, default retry/backoff applies). The test's own
assertion passes well within its 30-second budget, but the underlying retry-and-recover cycle for
that specific record can keep running in a background thread past the point the JUnit test method
itself returns, occasionally producing benign `Connection refused` log noise once that test class's
own Testcontainers Postgres is later torn down. Observed during this phase's own full-suite run;
does not affect any test's pass/fail outcome (confirmed: 118/118 clean, and reproducibly clean on
every full-suite re-run performed in this phase). Not fixed - a dedicated dead-letter/recovery
strategy is explicitly out of this task's own scope (Finding #7's own frozen disposition).

## Verification

`mvn -pl services/notification clean verify` — 118 tests, 0 failures (98 T01–T05/Phase-6 unaffected
+ 20 new). Re-ran the full suite twice more (`AuthEventConsumerIntegrationTest` specifically three
times) to confirm the group-id isolation fix from Phase 6/7's own noted flakiness holds; all runs
clean.

## Addendum (post Phase 11) — all 9 gaps verified against source and accepted

Kimi's Phase 11 review raised 9 gaps, all independently verified true before acting (none false).
All 9 are added (127 tests → from 118):

- **Gap #1** (no permanent guard that the idempotency short-circuit itself exists) — added
  `AuthEventConsumerTest.bothListenersShortCircuitOnTheIdempotencyGuardBeforeAnyOtherCall`, a static
  text-scan test mirroring `ContactProjectionRepository`'s own Gap #1 precedent from T05.
- **Gap #2** (fragile static-list-size redelivery assertion) — rewrote
  `emailRequestedVerifyEmailIsConsumedDedupedProjectedAndDispatched`'s redelivery check to filter
  `SpyDispatcherConfig.CALLS` by this test's own `accountUuid` and assert a count of exactly `1`,
  immune to ordering/interleaving with the class's other test methods.
- **Gap #3** (no integration-level `password_reset` coverage) — added
  `AuthEventConsumerIntegrationTest.passwordResetIsConsumedDedupedProjectedAndDispatched`.
- **Gap #4** (listener annotation metadata unlocked) — added
  `AuthEventConsumerTest.listenerMethodsAreAnnotatedWithTheCorrectTopicsAndAreTransactional`,
  reflection-based, asserting both `@KafkaListener(topics = ...)` values and both `@Transactional`
  presences.
- **Gap #5** (no malformed-message handling proof) — added
  `AuthEventConsumerIntegrationTest.malformedMessageDoesNotPermanentlyPoisonTheListener`: sends a
  non-JSON message first, then a well-formed one, and asserts the well-formed message is still
  consumed - proving the poisoned record doesn't wedge the listener, without asserting a specific
  non-existent row for the poison message itself (which carries no extractable key to query by).
- **Gap #6** (status vs. eventType routing untested independently) — added two
  `AuthEventConsumerTest` methods:
  `dispatchDependsOnEventTypeNotStatusForARegisteredEventWithAnUnusualStatus`
  (`status=SUSPENDED, eventType=user.registered` → dispatched) and
  `noDispatchForAnActiveStatusEventWithANonRegisteredEventType`
  (`status=ACTIVE, eventType=user.reinstated` → not dispatched).
- **Gap #7** (dispatch-on-stale-projection semantics untested) — added
  `AuthEventConsumerTest.dispatchStillHappensWhenTheProjectionUpsertIsRejectedAsStale`, stubbing
  `upsertEmail` to return `false` and asserting `dispatch` is still called.
- **Gap #8** (no proof the real `NoOpNotificationDispatcher` is the resolved Spring bean) — added
  `IdempotencyGuardIntegrationTest.theRealNoOpDispatcherIsTheResolvedSpringBean` - the one existing
  test class whose Spring context never overrides `NotificationDispatcher` with a spy/throwing test
  bean, making it the correct place for this proof.
- **Gap #9** (`auto-offset-reset`/`group-id` not asserted) — added
  `ApplicationPropertiesJpaConfigTest.kafkaConsumerGroupIdAndOffsetResetAreConfigured`, mirroring
  that file's own existing direct-properties-read technique (no Spring context, no Docker).

**Verification:** `mvn -pl services/notification clean verify` — 127 tests, 0 failures. Ran
`AuthEventConsumerIntegrationTest` (now 6 tests) three times in isolation to confirm Gap #2's and
Gap #5's own new tests are stable, not just passing once; all runs clean.
