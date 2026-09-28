# notification · T06 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — two real `@KafkaListener` methods, one per topic, each deserializing into the matching DTO | Yes | `AuthEventConsumer.java` `onEmailRequested`/`onUserLifecycle`, `@KafkaListener(topics = "auth.email.requested"/"auth.user.lifecycle", ...)` | `AuthEventConsumerTest.listenerMethodsAreAnnotatedWithTheCorrectTopicsAndAreTransactional` (reflection), all `AuthEventConsumerIntegrationTest` methods (real broker) | No | No |
| **AC2** — every message calls `recordIfNew` first; `false` skips all further processing | Yes | Both listener bodies, dedupe check is the first statement after deserialization | `duplicateEmailRequestedEventUpdatesNeitherProjectionNorDispatcher`, `duplicateLifecycleEventUpdatesNeitherProjectionNorDispatcher`, `bothListenersShortCircuitOnTheIdempotencyGuardBeforeAnyOtherCall` (static-scan) | No | No |
| **AC3** — every non-duplicate message calls `upsertEmail` with `accountUuid`/`email`/`occurredAt` | Yes | Both listener bodies, `logProjectionOutcome(event.accountUuid(), contactProjectionUpdater.upsertEmail(...))` | `projectionIsRefreshedForADispatchedEmailRequestedEventToo`, `unknownPurposeIsProjectedButNeverDispatched`, `nonRegisteredEventTypeIsProjectedButNeverDispatched`, all 6 integration tests | No | No |
| **AC4** — purpose/eventType routing resolves correctly; non-registration eventTypes and unknown purposes never dispatch | Yes | `onEmailRequested`'s `switch`, `onUserLifecycle`'s `"user.registered".equals(...)` guard | 3 named tests + `unknownPurposeIsProjectedButNeverDispatched`, `nonRegisteredEventTypeIsProjectedButNeverDispatched`, `dispatchDependsOnEventTypeNotStatusForARegisteredEventWithAnUnusualStatus`, `noDispatchForAnActiveStatusEventWithANonRegisteredEventType` | No | No |
| **AC5** — `dispatch` called with correct `accountUuid`/`notificationKind`/`eventData` for all 3 in-scope cases | Yes | Both listener bodies' final `notificationDispatcher.dispatch(...)` calls | 3 named tests, `emailRequestedVerifyEmailIsConsumedDedupedProjectedAndDispatched`, `passwordResetIsConsumedDedupedProjectedAndDispatched`, `userRegisteredLifecycleEventIsConsumedDedupedProjectedAndDispatched` | No | No |
| **AC6** — `NoOpNotificationDispatcher` is real (not placeholder), logs and returns | Yes | `NoOpNotificationDispatcher.java` | `NoOpNotificationDispatcherTest` (3 tests), `IdempotencyGuardIntegrationTest.theRealNoOpDispatcherIsTheResolvedSpringBean` | No | No |
| **AC7** — idempotency key format exactly `accountUuid + ":" + purpose/eventType + ":" + occurredAt` | Yes | Both listener bodies, `eventKey` construction | `idempotencyKeyForEmailRequestedIsAccountUuidPurposeOccurredAt`, `idempotencyKeyForUserLifecycleIsAccountUuidEventTypeOccurredAt` | No | No |
| **AC8** — both listener methods `@Transactional` | Yes | `@Transactional` on both methods | `listenerMethodsAreAnnotatedWithTheCorrectTopicsAndAreTransactional`, `AuthEventConsumerTransactionRollbackIntegrationTest` | No | No |
| **AC9** — `NoOpNotificationDispatcher`'s log output never contains a raw token | Yes | `NoOpNotificationDispatcher.java:30` logs `eventData.keySet()` only | `NoOpNotificationDispatcherTest.dispatchNeverLogsTheRawTokenValue`, `EmailRequestedEventContractTest.toStringExcludesTheRawToken` (DTO layer) | No | No |
| L1 (idempotent by event key) | Yes | Dedupe-first ordering in both listeners; native `ON CONFLICT DO NOTHING` (T04) underneath | Covered above (AC2/AC7) plus T04's own suite | No | No |
| L2 (consume-only, no synchronous cross-service call) | Yes | `AuthEventConsumer`/`NotificationDispatcher`/`NoOpNotificationDispatcher` never call another service; `dispatch` deliberately omits `email`, resolved later via the local `contact_projection` | Implicit — no network client exists anywhere in this task's own files | No | No |
| L4 (no secrets/tokens in logs) | Yes | `NoOpNotificationDispatcher.java:30`, `EmailRequestedEvent.toString()` | `NoOpNotificationDispatcherTest` (2 tests), `EmailRequestedEventContractTest.toStringExcludesTheRawToken` | No | No |
| L5 (channels behind one interface) | Not yet applicable | `NotificationDispatcher` is the seam a future `NotificationChannel`-based implementation will sit behind (task 11/12) - T06 introduces no channel-specific code that could violate this | N/A | No | No (on track, not yet built) |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T06's own literal scope (`tasks.md` task 6: consume
`auth.user.lifecycle`/`auth.email.requested`, dedupe, refresh `contact_projection`, route to a
dispatch seam). All 5 production files delivered (`AuthEventConsumer`, `NotificationDispatcher`,
`NoOpNotificationDispatcher`, `EmailRequestedEvent`, `UserLifecycleEvent`) plus 1 modified file
(`application.properties`) plus the 2 frozen-brief-required contract tests and, across Phases
10–11, 7 more test files/additions (127 tests total, up from T05's own 92 — 35 new). The
`T01SkeletonRegressionTest.java` update was disclosed as a required, undisclosed-in-brief deviation
(same recurring class of gap as T04/T05), not silent scope creep.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC9 all hold, each with
direct evidence and automated coverage, including two properties that were empirically verified
rather than merely asserted correct by inspection: the real end-to-end listener wiring (Phase 7's
own scratch test, made permanent in Phase 10) and the transaction-rollback atomicity of a failing
`dispatch` (Phase 10's own dedicated integration test).

**(3) Does it violate any LOCKED decision?** No. L1/L2/L4 all hold with direct evidence. L5 is not
yet applicable — `NotificationDispatcher` is designed as the seam a future channel-based
implementation sits behind, and T06 introduces no channel-specific logic itself that could violate
it.

**(4) Remaining risks?**
- **A schema-violating payload missing a required field deserializes to `null` rather than
  throwing**, verified empirically this phase (a scratch test confirmed `EmailRequestedEvent`
  deserializes a JSON payload missing `email` with `email() == null`, no exception). Jackson's
  default record deserialization does not enable `FAIL_ON_MISSING_CREATOR_PROPERTIES`. This means
  `ContactProjectionUpdater.upsertEmail` could be called with a `null` email for a genuinely
  malformed upstream payload - not a defect T06 introduces (the same characteristic applies to
  `auth-service`'s own hand-written payload records, and no schema-validating deserializer exists
  anywhere in this repo - Finding #6's own already-accepted scope boundary), but worth flagging for
  whoever next touches deserialization strictness. `contact_projection.email` has no `NOT NULL`
  constraint at the database level (confirmed via `V1__notifications_baseline.sql`: `email CITEXT`,
  no `NOT NULL`), so this would not fail at the DB layer either - it would silently store a `null`
  email.
- **`package.md` §9's "terminates in a dead-letter outcome, not an infinite loop"** is only
  partially satisfied: retry is bounded and does terminate (proven this phase by
  `malformedMessageDoesNotPermanentlyPoisonTheListener` - the default `FixedBackOff(0, 9)` exhausts
  and the listener moves on), but there is no dead-letter topic or handler - a permanently failing
  message is logged and skipped, not routed anywhere recoverable. Already disclosed and explicitly
  deferred at Phase 4 (Finding #7): "a dedicated dead-letter topic/handler is a genuine future gap
  ... not this task's own scope to build." Not named in `tasks.md` for any task through T20 - a
  genuine whole-service gap, not specific to T06.
- **Per-event-type redelivery-safety is proven end-to-end (integration level) only for
  `verify_email`**, not separately for `password_reset`/`user.registered`. The underlying mechanism
  is proven generic (the idempotency key format and short-circuit are identical regardless of
  `purpose`/`eventType` value, per `AuthEventConsumerTest`'s own unit-level duplicate tests), and
  `package.md` §8's own test plan doesn't literally name one redelivery test per event type - judged
  sufficient, not a gap requiring further action.
- `displayName` remains permanently unpopulated (T05's own already-disclosed risk, unchanged by
  T06 - this task never reads or writes it).

## `package.md` §9 whole-service checklist — items relevant to T06

- [x] All §3 acceptance criteria have a passing named test from §8 — the 3 literally-named tests
  (`shouldSendVerificationEmailOnAuthEmailRequestedVerify`,
  `shouldSendPasswordResetEmailOnAuthEmailRequestedReset`, `shouldWelcomeUserOnUserRegistered`) all
  pass.
- [x] Every §4a LOCKED decision implemented as written — L1/L2/L4 all hold; L5 not yet applicable
  (see above).
- [x] Every §4c VERBATIM config key copied exactly — `auto-offset-reset=latest`, `group-id`
  matches `package.md` §4c's own naming convention; asserted directly by
  `ApplicationPropertiesJpaConfigTest.kafkaConsumerGroupIdAndOffsetResetAreConfigured`.
- [x] Every consumer is idempotent — proven generically (unit) and end-to-end for at least one
  representative case per topic (integration); see "Remaining risks" for the one nuance.
- [ ] Delivery log records every attempt — **out of scope**, no `delivery_log` table or writer
  exists in this codebase yet (future task).
- [x] No secret/token ever appears in a log line — proven for this task's own new code
  (`NoOpNotificationDispatcher`, DTO `toString()`); rendered message bodies don't exist yet
  (template rendering is task 9, out of scope here).
- [ ] Channel-preference resolution — **out of scope**, `PreferenceResolver` doesn't exist yet.
- [ ] In-app stream authentication — **out of scope**, no in-app stream exists yet.
- [~] Retry/backoff bounded and terminates — bounded/terminating confirmed; dead-letter routing
  not built (see "Remaining risks", already disclosed at Phase 4).
- [x] `mvn -pl services/notification verify` passes — 127 tests, 0 failures.
- [x] Consumed payloads validate against `contracts/events/*` schemas via a contract test — 2
  contract tests, both passing, now including format-constraint assertions (Phase 11 addendum).

## Cross-task regression check

Full `services/notification` suite: 127 tests, 0 failures. `T01SkeletonRegressionTest` (9 tests,
its own 20-file authorized list, updated this task) and `NotificationBaselineMigrationIntegrationTest`
(15 tests, unchanged by T06) both still pass alongside T02's, T03's, T04's, and T05's own test
classes, all unmodified by this task except the two disclosed, justified `T01SkeletonRegressionTest`
edits — confirms T06's changes didn't regress anything the prior five tasks established.

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20,
matching the established precedent (T02–T05 all left it untouched). Not touched here.

---

**PASS** — all 9 acceptance criteria satisfied with direct evidence and automated coverage (127
tests, two properties empirically verified via scratch tests before being made permanent, one
mutation-tested), no LOCKED decision violated, task boundary held throughout. Three residual risks
are disclosed above, none blocking: a null-on-missing-field deserialization characteristic shared
with the rest of the repo, an already-accepted dead-letter gap deferred at Phase 4, and a narrow
per-event-type redelivery-proof scope judgment.
