# notification · T14 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T14 — Bounded retry |
| **Consumes** | All prior T14 artifacts (Phases 0-11, including the Phase 11 addendum) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R12** — a transient channel-delivery failure marks the attempt `FAILED`, retains it in the log, and schedules a bounded retry per the backoff policy | Yes | `DeliveryOrchestrator.java:257-281` (`attemptSend`, classifies and writes `FAILED` for a transient failure below exhaustion); `DeliveryOrchestrator.java:287-298` (`scheduleFirstRetry`, inserts the first `delivery_retry` row) | Named test `shouldMarkDeliveryFailedAndScheduleRetryOnTransientError` (`DeliveryOrchestratorIntegrationTest`, real end-to-end FAILED→retry→SENT); `transientChannelSendFailureInsertsAFirstRetryRowAtAttemptOneWithTheCorrectBackoff`, `transientInAppChannelSendFailureAlsoSchedulesARetry`, `replayOfATransientFailureBelowMaxAttemptsReturnsTransientFailure` | No | No |
| **R13** — exhaustion stops retrying, records a terminal `DEAD_LETTERED` outcome, never retries indefinitely | Yes | `DeliveryOrchestrator.java:271-276` (exhaustion check inside `attemptSend`, `nextAttemptNumber > retryProperties.maxAttempts()`) | Named test `shouldStopRetryingAndDeadLetterAfterMaxAttempts` (real end-to-end exhaustion, real `maxAttempts=5`); `replayAtTheFinalAllowedAttemptDeadLettersAndReturnsTransientExhausted`; `transientFailureWithMaxAttemptsOfOneDeadLettersImmediatelyWithNoRetryRow` (degenerate boundary) | No | No |
| **L7** — bounded retry, then dead-letter; no infinite retry loop, no silent drop | Yes | `RetryProperties.java:28` (`@Max(62)` closes the one latent path to an effectively-unbounded retry — a bit-shift wraparound at extreme `maxAttempts`); the exhaustion check is evaluated on every attempt including the very first | `RetryPropertiesTest.failsWhenMaxAttemptsExceedsSixtyTwo`/`succeedsWhenMaxAttemptsEqualsSixtyTwo`; `transientFailureBackoffNeverExceedsTheConfiguredMaximum` | No | No |
| **L2** (constrains the replay path) — consume-only, no synchronous cross-service call | Yes | `DeliveryOrchestrator.java:311-356` (`replay`) resolves recipient/`displayName` exclusively via the local `contact_projection` projection (`ContactProjectionUpdater`), never a live Auth call | Implicit — no new dependency on any cross-service client was introduced | No | No |
| **L3** — append-only delivery log, a retry adds a new row, never overwrites | Yes | `DeliveryLog.java:51,67` (`attempt` now a real constructor parameter, no longer hardcoded); every `attemptSend`/`replay`/`recordUnrecoverableFailure` call path only ever calls `deliveryLogRepository.save(new DeliveryLog(...))`, never an update | `shouldStopRetryingAndDeadLetterAfterMaxAttempts` (asserts `maxAttempts` distinct rows, one per attempt, none overwritten) | No | No |
| **L5** — channels behind one interface, orchestrator channel-agnostic | Yes | `RetryScheduler`/`DeliveryRetry` never import or reference `NotificationChannel`/`EmailChannel`/`InAppChannel` directly (verified by grep — no such import in either file); `replay` reaches a channel exclusively through the same `channelsByName`/`attemptSend` path `dispatchOneChannel` uses | `transientInAppChannelSendFailureAlsoSchedulesARetry`, `replaySuccessForInAppChannelWritesSentRowWithAccountUuidAsRecipient`, `bothChannelsFailingTransientlyScheduleTwoIndependentRetryRows`, `oneChannelSucceedingWhileTheOtherFailsTransientlyStillSchedulesOnlyTheFailingChannelsRetry` (all prove the retry path is genuinely channel-agnostic, not only exercised against EMAIL) | No | No |
| **L11** — module boundaries, no feature module imports another feature module's entity | Yes | `DeliveryRetry`/`DeliveryRetryRepository`/`RetryScheduler` all live in `delivery/`, the same package as `DeliveryLog`/`DeliveryOrchestrator`; no new feature-module import anywhere in this task's own files (verified by direct inspection) | No dedicated ArchUnit test yet — T16's own future scope, same caveat every prior task before it has carried | No | No |
| **AC1** — a transient failure writes `FAILED` + inserts the first `delivery_retry` row at `attempt=1` with `nextAttemptAt = now + initialBackoffSeconds` | Yes | `DeliveryOrchestrator.java:287-298` | `transientChannelSendFailureInsertsAFirstRetryRowAtAttemptOneWithTheCorrectBackoff` | No | No |
| **AC2** — backoff is bounded: `min(initialBackoffSeconds * 2^(n-1), maxBackoffSeconds)` | Yes | `RetryScheduler.java:109-112` (`computeNextAttemptAt`) | `transientFailureBringingTheTotalToTwoAttemptsReschedulesAtDoubleTheInitialBackoff`, `...ToFourAttempts...`, `transientFailureBackoffNeverExceedsTheConfiguredMaximum` | No | No |
| **AC3** — the exhausting attempt writes `DEAD_LETTERED` and deletes the retry row, no further retry | Yes | `DeliveryOrchestrator.java:271-276`; `RetryScheduler.java:90` (`TRANSIENT_EXHAUSTED` → delete) | `replayAtTheFinalAllowedAttemptDeadLettersAndReturnsTransientExhausted`; `transientExhaustedOutcomeDeletesTheRetryRowWithoutRescheduling` | No | No |
| **AC4** — every replay (success or failure) appends a new row, real attempt number, never updates | Yes | `DeliveryLog.java:51,67`; `attemptSend` (`DeliveryOrchestrator.java:257-281`) | `replaySuccessWritesASentRowAtTheIncrementedAttemptAndReturnsSent`; the two named integration tests | No | No |
| **AC5** — the replay path calls only through `NotificationChannel`, no channel-specific branching in `RetryScheduler`/`DeliveryRetry` | Yes | `RetryScheduler.java` has zero references to any concrete channel class; `replay` delegates to the shared `attemptSend` | `sentOutcomeDeletesTheRetryRowAndNeverReschedules` and siblings (channel-agnostic by construction, `channel` is just a `String` key); the channel-agnosticism tests listed under L5 above | No | No |
| **AC6** — `RetryScheduler`'s scheduled method is `@SchedulerLock`-guarded | Yes | `RetryScheduler.java:54` | `RetrySchedulerTest.sweepLockAnnotationIsPresentWithANonEmptyName` (Phase 11 addendum — a real gap until then: only `@EnableSchedulerLock` at the class level had ever been checked, never the method-level annotation itself) | No | No |
| **AC7** — `RetryProperties`'s real values are confirmed, not placeholders; `DEAD_LETTERED` via `delivery_log` confirmed as the dead-letter destination | Yes | `application.properties`'s own retry block comment now says "CONFIRMED... no longer placeholders"; no separate `dead_letter` table or Kafka topic was built — `DEAD_LETTERED` is written through the same `deliveryLogRepository` every other outcome uses | `RetryPropertiesTest.bindsFromTheRealPrefixAndKeyNames` | No | No |
| **AC8** — error detail persisted on a retry's own `delivery_log` row is redacted via `SecretSafeLogging.redact()` | Yes | `DeliveryOrchestrator.java`'s own `save` helper (unchanged from T11, still the one and only write path) wraps every `errorDetail` | `recordUnrecoverableFailureRedactsASecretShapedDetailBeforePersistence` (Phase 11 addendum — the pre-existing test used a non-secret-shaped detail, giving zero real evidence redaction occurred) | No | No |
| **AC9** — a single due row's own failure never aborts the sweep for the rows after it | Yes | `RetryScheduler.java:55-65` (`sweep`'s own per-row `try/catch`, not `@Transactional` itself — one transaction per row) | `sweepSkipsAFailingRowWithoutAbortingTheRest` | No | No |
| **AC10** — a permanent (`IllegalArgumentException`-shaped) failure never schedules a retry; one discovered mid-retry deletes the row and writes `FAILED`, not `DEAD_LETTERED` | Yes | `DeliveryOrchestrator.java:266-269` (`attemptSend`'s own `catch (IllegalArgumentException e)` branch) | `permanentChannelSendFailureNeverInsertsARetryRow`; `replayOfAPermanentFailureReturnsPermanentFailureAndStopsRetrying`; `permanentFailureOutcomeDeletesTheRetryRow` | No | No |
| **AC11** — `replay` re-checks `preferenceResolver.resolve`; a now-disabled channel records `SUPPRESSED` and deletes the row | Yes | `DeliveryOrchestrator.java` `replay`'s own preference check (unconditional, every call) | `replayHonorsAPreferenceDisabledSinceTheOriginalAttemptAndReturnsSuppressed`; `suppressedOutcomeDeletesTheRetryRow` | No | No |
| **AC12** — a `delivery_retry` row whose `event_data_json` cannot be deserialized is dead-lettered and removed on its first encountered sweep | Yes | `RetryScheduler.java:69-81` (`processOne`'s own poison-pill `catch (JsonProcessingException e)`) | `unreadableEventDataJsonDeadLettersDirectlyWithoutEverCallingReplay` | No | No |
| **AC13** — `DeliveryLog.getAttempt()` reflects the real attempt number for every row this task writes, including replays | Yes | `DeliveryLog.java:51,67` | `replaySuccessWritesASentRowAtTheIncrementedAttemptAndReturnsSent` and every `replay*`/`transient*` test's own explicit `attempt` assertion | No | No |
| **AC14** — `maxAttempts=1` dead-letters the very first failure immediately, no retry row ever inserted | Yes | `DeliveryOrchestrator.java:271-276` (the exhaustion check is evaluated identically on the original attempt, via `attemptSend(..., (short) 1)`) | `transientFailureWithMaxAttemptsOfOneDeadLettersImmediatelyWithNoRetryRow` | No | No |

## Answers

**(1) Is the task fully complete?** Yes. Every file the frozen brief's own "Files to Create/Modify"
list named exists in its final form (plus one small, disclosed, necessary addition —
`common/config/ShedLockConfig.java`, the `LockProvider` bean `@SchedulerLock` needs, which the
frozen brief's own text anticipated as "exact Maven coordinates confirmed during implementation"
without spelling out). The task went through 3 rounds of adversarial review (Kimi Phases 3, 8, 11)
plus this session's own self-review (Phase 7), with every finding fixed, correctly disposed with a
stated reason, or explicitly documented as an accepted, out-of-scope trade-off. Three genuine,
empirically-discovered bugs were found and fixed along the way: ShedLock's own grant needing
`SELECT` too (Phase 6, a real `permission denied` error), a detached-entity mutation silently never
persisting (Phase 6, a real refetched-row contradiction), and a false Javadoc claim about a test file
that never existed (Phase 11, caught while investigating Kimi's own Gap #1).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC14, see matrix above. AC6
(the `@SchedulerLock` guard) and AC8 (redaction) are the two criteria that were *implemented*
correctly from Phase 6 onward but had **no test that would have caught a regression** until the
Phase 11 addendum closed that gap — a real distinction between "correct today" and "correct and
protected," both now true.

**(3) Does it violate any LOCKED decision?** No. L2/L3/L5/L7/L11 all hold, per the matrix. L5's own
channel-agnostic property was proven more thoroughly than the original Phase 6 implementation alone
would have shown — the Phase 10/11 additions specifically closed the gap where every retry-scheduling
test had only ever exercised `EMAIL`, never proving the identical code path also works for `IN_APP`.
L11 holds — no feature-module entity import was introduced; automated ArchUnit enforcement remains
T16's own future scope, consistent with every prior task before it.

**(4) Remaining risks?**
- **No automated ArchUnit enforcement of L11 yet** (T16's own scope) — same caveat every prior task
  has carried; L11 compliance here was verified manually.
- **No live, multi-replica ShedLock contention test exists** — a deliberate, twice-reaffirmed choice
  (Phase 5's own plan, then re-confirmed at Phase 11 after a direct `sweep()`-calling test attempt
  turned out to be genuinely flaky against the real `lockAtLeastFor` timing). AC6 is proven
  structurally (the annotation is present and correctly configured) rather than via live contention,
  which remains proportionate given nothing in this codebase's own CI runs multiple real instances.
- **`RetryScheduler.sweep`'s own due-rows query has no pagination** and its own execution time is
  not bounded against `lockAtMostFor="5m"` — both explicitly accepted, documented, launch-scale
  risks (Phase 7/8 Findings #7/#9), revisit only if real operational data ever shows either becoming
  a genuine problem.
- **`DeliveryOrchestrator.replay` duplicates `dispatchOneChannel`'s own pre-send guard sequence**
  (~25 lines) — a real, disclosed maintenance-coupling risk (Phase 7/8 Finding #2/#6), accepted as
  disproportionate to extract within this task's own scope.
- **A real channel send now also runs inside `RetryScheduler.processOne`'s own open DB transaction**
  — T11/T12's own already-accepted architectural risk, now confirmed to extend to the replay path
  too; a real fix would mean revisiting T11's own frozen transaction boundary, out of scope here.
- **`event_data_json` persists the same raw one-time token the original event carried** — disclosed
  at Phase 3/4, a new persistence surface (not a new class of exposure) with a bounded lifetime,
  explicitly accepted for launch.

## Verdict

**PASS** — T14 fully satisfies R12 and R13, and every locked decision (L2, L3, L5, L7, L11) and
acceptance criterion (AC1-AC14) it touches. The bounded-retry-then-dead-letter mechanism is proven
not only to work for the channel it was first built against (`EMAIL`) but to be genuinely
channel-agnostic, proven correct at the exact backoff-boundary values the pinned semantics specify,
and protected against the two real bugs this task's own empirical testing discipline caught before
they could ship silently. The full suite is green at 357 tests, 0 failures, 0 errors.
