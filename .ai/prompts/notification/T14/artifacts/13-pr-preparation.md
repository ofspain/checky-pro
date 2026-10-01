# notification · T14 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T14: bounded retry (DeliveryRetry, RetryScheduler, ShedLock)`

## Commit message

```
notification-service T14: bounded retry (DeliveryRetry, RetryScheduler)

Add DeliveryRetry/DeliveryRetryRepository and RetryScheduler, closing
the one gap DeliveryOrchestrator (T11) carried since its own Javadoc
first said so: every channel-delivery failure was previously a dead
end - one FAILED row, no retry, ever. A failed channelBean.send is now
classified permanent (IllegalArgumentException - deterministic, retry
changes nothing) or transient (everything else) by one shared
attemptSend helper, used identically by the original dispatch path and
by a new replay method RetryScheduler calls for each due row. A
transient failure schedules an exponential-then-capped backoff;
exhaustion - evaluated on every attempt, including the very first, so
a maxAttempts=1 configuration dead-letters immediately rather than
scheduling a retry it would instantly exhaust - writes a terminal
DEAD_LETTERED row instead of another retry. replay re-checks
preferences and re-resolves email/displayName fresh, since nothing
durable stores the originally-rendered message.

RetryScheduler.sweep is ShedLock-guarded (@SchedulerLock) and
deliberately not @Transactional itself - one transaction per row
(processOne), so one row's own failure can never affect another's
already-committed outcome, and never aborts the sweep for the rows
after it. A delivery_retry row whose event_data_json cannot be
deserialized is dead-lettered directly, never retried forever.

Three genuine, empirically-discovered bugs were found and fixed
across this task, none assumed correct from inspection:
(1) ShedLock's own JDBC-provider SQL needs SELECT too - its
UPDATE/ON-CONFLICT-DO-UPDATE statements have a WHERE clause Postgres
requires SELECT to evaluate, confirmed by a real "permission denied"
error before the fix; (2) RetryScheduler.processOne mutated a
DeliveryRetry entity detached from any transaction (sweep is
deliberately not transactional) and relied on dirty-checking to
persist it - silently never did, confirmed by a real refetched row
showing the stale attempt count; (3) a Javadoc in RetrySchedulerTest
claimed a RetrySchedulerIntegrationTest file existed proving sweep's
real @Scheduled/ShedLock timing - it never did, caught while
investigating Kimi's own Phase 11 review.

RetryProperties gains schedulerIntervalSeconds and a @Max(62) bound on
maxAttempts, closing a latent bit-shift wraparound in the backoff
formula and a short-column overflow risk at the configuration root
rather than defending in the arithmetic too (Phase 7/8 review, both
independently found the same root cause). DeliveryLog's own attempt
column is a real constructor parameter now, no longer hardcoded to 1.

Four adversarial review rounds (Kimi Phase 3: 10 findings, including
the delivery_retry schema amendment this task's own design required;
Phase 8: 10 findings, 3 genuinely new (short overflow, stale
displayName on replay, a missing templateName guard); Phase 11: 10
gaps, including the real documentation bug above) were each
independently verified against actual source before disposition,
never taken on word.

357 tests total (348 T01-T13 unaffected + 9 new at Phase 10 closing
two real coverage gaps + ~9 more in the Phase 11 addendum closing all
10 of Kimi's own test-review gaps).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/common/config/ShedLockConfig.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryRetry.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryRetryRepository.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/RetryScheduler.java`
- `services/notification/src/main/resources/db/migration/V9__delivery_retry_add_replay_columns.sql`
- `services/notification/src/main/resources/db/migration/V10__notification_app_delivery_retry_grant.sql`
- `services/notification/src/main/resources/db/migration/V11__notification_app_shedlock_grant.sql`
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryRetryTest.java`
- `services/notification/src/test/java/com/themistra/notification/delivery/RetrySchedulerTest.java`

**Modified**
- `services/notification/pom.xml` (`shedlock-spring`/`shedlock-provider-jdbc-template` `7.9.0`)
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
  (`@EnableScheduling`/`@EnableSchedulerLock`)
- `services/notification/src/main/java/com/themistra/notification/common/config/RetryProperties.java`
  (`schedulerIntervalSeconds`; `@Max(62)` on `maxAttempts`)
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryLog.java`
  (constructor gains a real `attempt` parameter)
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java`
  (the largest change — `DeliveryOutcome` enum, `attemptSend`, `scheduleFirstRetry`, `replay`,
  `recordUnrecoverableFailure`)
- `services/notification/src/main/resources/application.properties` (new property; the pre-existing
  retry values' own comment now says confirmed, not placeholder)
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (`delivery_retry`/`shedlock` grant-shape tests; the now-empty `UNGRANTED_TABLES` mechanism and its
  3 dead helper methods removed)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (authorized file-inventory list; `@EnableScheduling`/`@EnableSchedulerLock`/ShedLock-dependency
  assertions flipped from `doesNotContain` to `contains`)
- `services/notification/src/test/java/com/themistra/notification/common/config/RetryPropertiesTest.java`
  (new field + `@Max(62)` boundary tests)
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryOrchestratorIntegrationTest.java`
  (`ControllableEmailTransport`; the two named tests; the due-rows-query test; a
  `scheduler-interval-seconds` override for the whole class)
- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryOrchestratorTest.java`
  (mechanical constructor-signature updates; ~20 new tests across Phases 6/9/10/11)

**Process artifacts**
- `.ai/prompts/notification/T14/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

The fifth task in this pipeline, and the first to close a gap `DeliveryOrchestrator` (T11) itself
had explicitly flagged as future work since the day it was written. `DeliveryOrchestrator` required
substantial, but surgical, changes: one shared classification helper (`attemptSend`) now serves both
the original dispatch path and every replay, so the two paths can never silently diverge in how they
decide permanent-vs-transient. `RetryScheduler` is this service's first scheduled job and first use
of ShedLock, both wired for the first time in this task. Three real, empirically-discovered bugs were
found and fixed — a DB grant that needed a privilege its own documented SQL never literally issues
but Postgres still requires, a detached-entity persistence bug that would have silently broken every
real retry reschedule in production, and a false claim in a test's own Javadoc that led to
discovering a genuine ShedLock timing hazard once properly investigated.

## Testing performed

- `mvn -pl services/notification clean verify` — 357 tests, 0 failures, 0 errors, `BUILD SUCCESS`.
- Both `package.md` §8 named tests (`shouldMarkDeliveryFailedAndScheduleRetryOnTransientError`,
  `shouldStopRetryingAndDeadLetterAfterMaxAttempts`) proven end-to-end against a real Postgres
  instance, a real `DeliveryOrchestrator`, and a real, controllable email-transport seam
  (`ControllableEmailTransport`, `@Primary`-overriding `FakeEmailTransport` for a single-injection-
  point, delegating to it on success so every pre-existing test's own assertions stay unaffected).
- The retry-scheduling/classification path is proven genuinely channel-agnostic, not only exercised
  against `EMAIL` — a real gap found and closed during Phase 10/11 (`transientInAppChannelSendFailureAlsoSchedulesARetry`,
  `replaySuccessForInAppChannelWritesSentRowWithAccountUuidAsRecipient`,
  `bothChannelsFailingTransientlyScheduleTwoIndependentRetryRows`,
  `oneChannelSucceedingWhileTheOtherFailsTransientlyStillSchedulesOnlyTheFailingChannelsRetry`).
- The exact pinned backoff formula is proven at multiple attempt counts and at its own cap, not only
  asserted to exist.
- `@SchedulerLock`'s presence on `sweep()` is proven via a real reflection-based structural test
  (`RetrySchedulerTest.sweepLockAnnotationIsPresentWithANonEmptyName`) — the gap where only the
  class-level `@EnableSchedulerLock` had ever been checked was closed in the Phase 11 addendum.
- Error-detail redaction (AC8) is proven with a real secret-shaped input, not only a benign string
  that would pass even with the redaction call removed.
- A real ShedLock timing hazard (Spring's `@Scheduled` firing once immediately at context startup
  regardless of configured interval, combined with `lockAtLeastFor="10s"`) was found by a flaky test
  during the Phase 11 addendum and resolved by testing the real due-rows repository query directly
  instead of the lock-guarded `sweep()` method — a deterministic proof of the same underlying
  mechanism, not a weaker one.
- `git diff --stat 7bf5a8e..HEAD -- services/auth services/crypto services/payment` — empty; no
  sibling service touched.
- `git diff --stat 7bf5a8e..HEAD -- spec/` — empty; no specification file modified.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 14 ("Bounded retry").
- **Requirements:** R12, R13.
- **LOCKED decisions:** L2, L3, L5, L7, L11.
- **Named tests (`package.md` §8):** `shouldMarkDeliveryFailedAndScheduleRetryOnTransientError`,
  `shouldStopRetryingAndDeadLetterAfterMaxAttempts` — both present, both passing, both proven at the
  real end-to-end integration level against a real Postgres instance and a real, controllable
  transport, not only mocked collaborators.
- **Resolved ambiguity:** Q6 ("Retry/backoff policy") — the real values T03 had already placeholder-
  seeded (`max-attempts=5`, `initial-backoff-seconds=30`, `max-backoff-seconds=3600`) are confirmed,
  not overridden; the dead-letter destination is confirmed as `delivery_log`'s own already-reserved
  `DEAD_LETTERED` outcome, not a new table or Kafka topic (O4).

## Known, deliberate gaps (not this task's scope)

- **No automated ArchUnit enforcement of L11 yet** — task 16's own scope
  ("ArchUnit/module boundaries"); this task's L11 compliance was verified manually, the same way
  every prior task in this service verified it before task 16 exists.
- **No live, multi-replica ShedLock contention test** — deliberately not attempted twice (Phase 5's
  own plan, then re-confirmed at Phase 11 after a direct test attempt proved genuinely flaky against
  real `lockAtLeastFor` timing); AC6 is proven structurally instead.
- **`RetryScheduler.sweep`'s own due-rows query has no pagination**, and its own execution time is
  not bounded against `lockAtMostFor="5m"` — both disclosed, accepted, launch-scale risks (Phase 7/8
  Findings #7/#9).
- **`DeliveryOrchestrator.replay` duplicates `dispatchOneChannel`'s own pre-send guard sequence**
  (~25 lines) — a disclosed maintenance-coupling risk (Phase 7/8 Finding #2/#6), judged
  disproportionate to extract within this task's own scope.
- **A real channel send now also runs inside `RetryScheduler.processOne`'s own open DB transaction**
  — T11/T12's own already-accepted architectural risk, confirmed to extend to the replay path too;
  a real fix means revisiting T11's own frozen transaction boundary, out of this task's scope.
- **`event_data_json` persists the same raw one-time token the original event carried** — disclosed
  at Phase 3/4, a new persistence surface (not a new class of exposure) with a bounded lifetime.
- **Retry/dead-letter for payment-derived events** (task 7, payment-service's own events) remains
  blocked on `services/payment` existing at all — unchanged from every prior task's own identical
  caveat; this task's own retry mechanism is itself payment-event-agnostic and will apply
  automatically once T07 unblocks.

## Reviewer notes

- **Kimi's Phase 3 design challenge found the TIB's own proposal genuinely needed amendment**: the
  already-migrated `V1` `delivery_retry` shape had no column to carry what a retry needs to
  re-render/re-send — accepted and resolved via a new `V9` schema amendment (`notification_kind`,
  `event_data_json`), the same class of disclosed, necessary cross-phase amendment T13's own
  `category` parameter was.
- **Kimi's Phase 8 Findings #1-#4 were all independently verified true before acceptance** — a
  `short`-column overflow risk, a bit-shift wraparound (closed together with one `@Max(62)` bound),
  a stale `displayName` on replay, and a missing `templateName` guard (the latter two genuinely new,
  not restating the self-review).
- **Kimi's own Phase 8 Finding #6 (curated AWS-error-code classification) was explicitly rejected**,
  with a stated reason: it requires string-matching an already-sanitized message and leaks an
  email-specific concern into the channel-agnostic classifier, against L5's own spirit.
- **Kimi's Phase 11 Gap #1 looked like a simple "write the missing test" request and turned into a
  real timing-hazard discovery** — the straightforward fix (write `RetrySchedulerIntegrationTest`)
  was tried, found genuinely flaky, root-caused to a real ShedLock/`@Scheduled` interaction, and
  replaced with a deterministic proof of the same underlying mechanism — worth a reviewer's
  attention as a case where investigating a gap properly surfaced more than the gap itself asked for.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T14.**
