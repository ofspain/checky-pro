# notification · T14 · Phase 10 — Test Generation

T14's own tests were written incrementally across Phase 6 (implementation) and Phase 9 (review
resolution), matching T11/T12's own established pattern — not deferred wholesale to this phase, the
way T13's anomalous "no T13-specific tests exist" Kimi finding forced for that one task. This
phase's own job was therefore an **audit against the frozen brief's Required Tests list**, which
found two genuine gaps (closed below) rather than a wholesale missing suite. No production code
changed in this phase.

## Gaps found and closed in this phase

- **AC5/L5 (channel-agnostic retry scheduling) was only ever proven for EMAIL.** Every existing
  transient-failure/retry-scheduling test in `DeliveryOrchestratorTest` threw from `emailChannel`;
  none proved the identical classification/scheduling logic also works correctly when `inAppChannel`
  is the one that fails. Closed by `transientInAppChannelSendFailureAlsoSchedulesARetry`.
- **No test proved two channels failing transiently on the same dispatch schedule two independent
  retry rows.** Closed by `bothChannelsFailingTransientlyScheduleTwoIndependentRetryRows`.

## Test manifest

| Test method | File | Verifies | AC / Requirement |
|---|---|---|---|
| `transientChannelSendFailureInsertsAFirstRetryRowAtAttemptOneWithTheCorrectBackoff` | `DeliveryOrchestratorTest` | First transient failure → `FAILED` row + `delivery_retry` row at `attempt=1`, `nextAttemptAt = now + initialBackoffSeconds` | AC1, R12 |
| `transientInAppChannelSendFailureAlsoSchedulesARetry` | `DeliveryOrchestratorTest` | Same classification/scheduling for `IN_APP`, not only `EMAIL` | AC5, L5 |
| `bothChannelsFailingTransientlyScheduleTwoIndependentRetryRows` | `DeliveryOrchestratorTest` | Two channels failing independently each get their own retry row | AC1, AC5 |
| `permanentChannelSendFailureNeverInsertsARetryRow` | `DeliveryOrchestratorTest` | `IllegalArgumentException` never schedules a retry | AC10 |
| `transientFailureWithMaxAttemptsOfOneDeadLettersImmediatelyWithNoRetryRow` | `DeliveryOrchestratorTest` | Degenerate `maxAttempts=1` dead-letters on the first attempt, no retry row ever inserted | AC14 |
| `replaySuccessWritesASentRowAtTheIncrementedAttemptAndReturnsSent` | `DeliveryOrchestratorTest` | `replay` success path, real incremented `attempt` | AC4, AC13 |
| `replayResolvesDisplayNameFreshAndMergesItIntoRenderData` | `DeliveryOrchestratorTest` | `replay` re-resolves `displayName`, not only `email` (Phase 8 Finding #3) | — |
| `replayGuardsAgainstANullTemplateNameMirroringDispatchOneChannel` | `DeliveryOrchestratorTest` | Structural: the `templateName == null` guard exists in `replay` (Phase 8 Finding #4, behaviorally unreachable) | — |
| `replayHonorsAPreferenceDisabledSinceTheOriginalAttemptAndReturnsSuppressed` | `DeliveryOrchestratorTest` | `replay` re-checks preferences, honors a since-disabled channel | AC11 |
| `replayOfATransientFailureBelowMaxAttemptsReturnsTransientFailure` | `DeliveryOrchestratorTest` | `replay` below exhaustion → `FAILED` + `TRANSIENT_FAILURE` | AC1, R12 |
| `replayAtTheFinalAllowedAttemptDeadLettersAndReturnsTransientExhausted` | `DeliveryOrchestratorTest` | `replay` at the exhausting attempt → `DEAD_LETTERED` + `TRANSIENT_EXHAUSTED` | AC3, R13 |
| `replayOfAPermanentFailureReturnsPermanentFailureAndStopsRetrying` | `DeliveryOrchestratorTest` | A permanent failure discovered mid-retry stops, never dead-letters | AC10 |
| `replayWithAnUnknownNotificationKindWritesFailedAndReturnsPermanentFailure` | `DeliveryOrchestratorTest` | Defensive guard for an unresolvable `notificationKind` on replay | — |
| `recordUnrecoverableFailureWritesADeadLetteredRowAtTheGivenAttempt` | `DeliveryOrchestratorTest` | The poison-pill path's own `delivery_log` write | AC12 |
| (33 pre-existing T11 tests, mechanically unaffected) | `DeliveryOrchestratorTest` | Regression | — |
| `rescheduleMutatesAttemptAndNextAttemptAt` | `DeliveryRetryTest` | The one mutator updates both fields | — |
| `toStringNeverIncludesEventDataJson` | `DeliveryRetryTest` | The raw token in `eventDataJson` never appears in a default `toString()` | Phase 3 Finding #2 |
| `sentOutcomeDeletesTheRetryRowAndNeverReschedules` | `RetrySchedulerTest` | `processOne` deletes on `SENT` | AC5 |
| `suppressedOutcomeDeletesTheRetryRow` | `RetrySchedulerTest` | `processOne` deletes on `SUPPRESSED` | AC11 |
| `permanentFailureOutcomeDeletesTheRetryRow` | `RetrySchedulerTest` | `processOne` deletes on `PERMANENT_FAILURE` | AC10 |
| `transientExhaustedOutcomeDeletesTheRetryRowWithoutRescheduling` | `RetrySchedulerTest` | `processOne` deletes (not reschedules) on `TRANSIENT_EXHAUSTED` | AC3, R13 |
| `transientFailureBringingTheTotalToTwoAttemptsReschedulesAtDoubleTheInitialBackoff` | `RetrySchedulerTest` | Exact backoff value, pinned semantics | AC2, R12 |
| `transientFailureBringingTheTotalToFourAttemptsReschedulesWithExponentiallyGrownBackoff` | `RetrySchedulerTest` | Exact backoff value at a later attempt | AC2, R12 |
| `transientFailureBackoffNeverExceedsTheConfiguredMaximum` | `RetrySchedulerTest` | Backoff cap at `maxBackoffSeconds` | AC2, L7 |
| `unreadableEventDataJsonDeadLettersDirectlyWithoutEverCallingReplay` | `RetrySchedulerTest` | Poison-pill row dead-lettered, `replay` never called | AC12 |
| `passesTheDeserializedEventDataAndStoredAttemptCountToReplay` | `RetrySchedulerTest` | Correct deserialization + argument passthrough | AC5 |
| `sweepSkipsAFailingRowWithoutAbortingTheRest` | `RetrySchedulerTest` | One row's failure doesn't abort the sweep | AC9 |
| `processOneSwitchHasADefaultArmGuardingAFutureUnhandledOutcome` | `RetrySchedulerTest` | Structural: `default` throw exists (Phase 8 Finding #5) | — |
| `bindsFromTheRealPrefixAndKeyNames` | `RetryPropertiesTest` | All 4 fields bind from real property names | AC7, Q6 |
| `failsWhenMaxAttemptsIsNonPositive` / `failsWhenInitialBackoffIsNonPositive` / `failsWhenSchedulerIntervalSecondsIsNonPositive` | `RetryPropertiesTest` | `@Min(1)` on each field | — |
| `failsWhenMaxAttemptsExceedsSixtyTwo` / `succeedsWhenMaxAttemptsEqualsSixtyTwo` | `RetryPropertiesTest` | `@Max(62)` boundary (Phase 8 Findings #1/#2) | — |
| `failsWhenMaxBackoffIsBelowInitialBackoff` / `succeedsWhenMaxBackoffEqualsInitialBackoff` | `RetryPropertiesTest` | Cross-field check boundary | — |
| **`shouldMarkDeliveryFailedAndScheduleRetryOnTransientError`** (named) | `DeliveryOrchestratorIntegrationTest` | Real end-to-end FAILED→retry→SENT lifecycle, real Postgres | **R12** |
| **`shouldStopRetryingAndDeadLetterAfterMaxAttempts`** (named) | `DeliveryOrchestratorIntegrationTest` | Real end-to-end exhaustion to `DEAD_LETTERED`, real `maxAttempts=5` | **R13** |
| `notificationAppCanInsertSelectUpdateAndDeleteOnDeliveryRetry` | `NotificationBaselineMigrationIntegrationTest` | `V10` grant shape, real Postgres | AC7 |
| `notificationAppCanInsertSelectAndUpdateButNotDeleteOnShedlock` | `NotificationBaselineMigrationIntegrationTest` | `V11` grant shape (including the Phase 6 SELECT-requirement correction), real Postgres | AC6 |
| `allMigrationsAreRecordedAsSuccessfulInFlywayHistory` (extended) | `NotificationBaselineMigrationIntegrationTest` | `V9`-`V11` recorded successfully | — |
| `applicationClassIsBareWithOnlyTheMainMethod` (extended) | `T01SkeletonRegressionTest` | `@EnableScheduling`/`@EnableSchedulerLock` present | AC6 |
| `noExtraProductionClassesExistBeyondT14sOwnAuthorizedSet` (renamed/extended) | `T01SkeletonRegressionTest` | Exactly the 50 authorized production files, no stray additions | — |
| (ShedLock dependency assertions, extended) | `T01SkeletonRegressionTest` | `shedlock-spring`/`shedlock-provider-jdbc-template` present | — |

## Verification

`mvn -pl services/notification clean verify` — 348 tests, 0 failures, 0 errors (346 after Phase 9 +
2 new in this phase). No production code was modified in this phase.

## Addendum (post Phase 11) — 1 real documentation bug, 9 real coverage gaps, all closed

Kimi's Phase 11 review raised 10 gaps. All verified against actual source before acting — every one
was real, not overstated. No production code changed; the closures below were all testing-side, and
one (Gap #4) uncovered a genuine test-design flaw (not a production bug) along the way.

- **Gap #1** (a real documentation bug): `RetrySchedulerTest`'s own Javadoc claimed a
  `RetrySchedulerIntegrationTest` file existed proving `sweep`'s real `@Scheduled`/ShedLock timing —
  it never did. **Fixed**: the false claim removed, replaced with an honest statement of what's
  actually proven and why a live timing-based test is deliberately not attempted (Phase 5's own
  judgment, unchanged).
- **Gap #2** (no structural proof of `@SchedulerLock` on `sweep`) — **fixed**: added
  `RetrySchedulerTest.sweepLockAnnotationIsPresentWithANonEmptyName` (reflection-based, asserts the
  annotation and a non-blank `name`/`lockAtMostFor`).
- **Gap #3** (reschedule test never verified `retryRepository.save(retry)`) — **fixed, closes a real
  regression-guard hole**: the exact detached-entity bug Phase 6 found and fixed had no test that
  would catch its own reintroduction. Added `verify(retryRepository).save(retry)` to both reschedule
  tests in `RetrySchedulerTest`.
- **Gaps #4/#5** (no test proves the real due-rows query filters/orders correctly) — **fixed, after
  a real design correction**: an initial attempt called `retryScheduler.sweep()` directly and was
  genuinely flaky — Spring's `@Scheduled` fires once immediately at context startup regardless of
  the configured interval, and ShedLock's own `lockAtLeastFor="10s"` held that lock for a minimum of
  10 seconds afterward, silently skipping a fast-running test's own second `sweep()` call within
  that window (ShedLock's own documented behavior, not a bug). Redesigned to prove the real
  repository query directly instead —
  `DeliveryOrchestratorIntegrationTest.dueRowsQueryExcludesAFutureRowAndReturnsDueRowsOldestFirst` —
  which is deterministic and proves the exact mechanism that determines what `processOne` ever sees.
  Also added a `themistra.notification.retry.scheduler-interval-seconds` override for this whole
  test class (pushed far out) so no future test in it can be affected by the same real background
  firing.
- **Gap #6** (replay's other guards - missing email, render failure, missing channel bean - were
  untested) — **fixed**: added `replayWithMissingEmailWritesFailedAndReturnsPermanentFailure`,
  `replayWithRenderFailureWritesFailedAndReturnsPermanentFailure`,
  `replayWithMissingChannelBeanWritesFailedAndReturnsPermanentFailure` to `DeliveryOrchestratorTest`.
- **Gap #7** (no test proves one channel succeeding doesn't interfere with the other's own retry) —
  **fixed**: added
  `oneChannelSucceedingWhileTheOtherFailsTransientlyStillSchedulesOnlyTheFailingChannelsRetry`.
- **Gap #8** (`recordUnrecoverableFailure`'s redaction was never proven, only a non-secret-shaped
  detail was tested) — **fixed**: added
  `recordUnrecoverableFailureRedactsASecretShapedDetailBeforePersistence`.
- **Gap #9** (`scheduleFirstRetry`'s serialization-failure handling was untested) — **fixed**: added
  `aSerializationFailureWhileSchedulingTheFirstRetryNeverPropagatesAndInsertsNoRow` (a mocked,
  throwing `ObjectMapper` wired into a dedicated orchestrator instance).
- **Gap #10** (every `replay` test used EMAIL, none proved IN_APP) — **fixed**: added
  `replaySuccessForInAppChannelWritesSentRowWithAccountUuidAsRecipient`.

**Files touched this addendum:**
- `delivery/RetrySchedulerTest.java` — Javadoc fix (Gap #1); +1 test (Gap #2); +2 assertions on
  existing tests (Gap #3).
- `delivery/DeliveryOrchestratorTest.java` — +8 tests (Gaps #6 ×3, #7, #8, #9, #10, plus one fixed
  wrong assumption in the Gap #10 test itself, caught by a real test failure — `replay` resolves
  `displayName` for every channel, not only EMAIL, so `contactProjectionUpdater` does see one
  interaction even for an IN_APP replay).
- `delivery/DeliveryOrchestratorIntegrationTest.java` — +1 net test (Gaps #4/#5, after replacing a
  flaky first attempt); a `scheduler-interval-seconds` property override for the whole class.

**Verification:** `mvn -pl services/notification clean verify` — 357 tests, 0 failures, 0 errors.
