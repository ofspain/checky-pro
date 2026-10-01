# notification · T14 · Phase 2 — Task Implementation Brief

## Task

Implement bounded retry for a transient channel-delivery failure: `DeliveryRetry` (a new entity over
the already-migrated `delivery_retry` table) schedules a bounded-backoff re-attempt; `RetryScheduler`
(a ShedLock-guarded `@Scheduled` job) polls due rows and replays the failed channel send through
`DeliveryOrchestrator`, recording a new `delivery_log` row per attempt; exhaustion records a terminal
`DEAD_LETTERED` outcome instead of scheduling another retry.

## Purpose

Closes the one gap `DeliveryOrchestrator` (T11) has carried since its own Javadoc first said so:
every channel failure today is a dead end — one `FAILED` row, no retry, ever. This task gives a
genuinely transient failure (an SES throttle, a dead DB connection on the in-app write) a bounded
second (third, fourth...) chance, and gives a permanently broken one (bad input data) a clean,
honest terminal state, per L7.

## Scope

**In:**
- `delivery/DeliveryRetry.java` / `DeliveryRetryRepository.java` — entity/repository over
  `delivery_retry`.
- `delivery/RetryScheduler.java` — `@Scheduled` + `@SchedulerLock`, polls due rows, replays, reschedules
  or dead-letters.
- `DeliveryOrchestrator.dispatchOneChannel`'s existing `channelBean.send(...)` catch (the one and only
  place R12/R13 apply — a failure earlier in the pipeline, e.g. `preferenceResolver.resolve` or
  `templateRenderer.render` throwing, is unchanged, out of this task's own scope) gains a
  transient/permanent classification and, for a transient failure, inserts the first `DeliveryRetry`
  row.
- A new package-visible `DeliveryOrchestrator` method the scheduler calls to actually replay one
  channel's send and record its own `delivery_log` row (reusing the existing render → send → log
  sequence, not duplicating it).
- Schema amendment (disclosed, necessary — see Constraints): `delivery_retry` gains
  `notification_kind` and `event_data_json` columns, since the already-migrated `V1` shape has no
  column carrying what a retry needs to actually re-render and re-send. A new Flyway migration (not
  an edit to `V1`), plus the already-pending grant migrations for `delivery_retry`/`shedlock`.
- `RetryProperties` (T03) gains one field, `schedulerIntervalSeconds` — the existing three fields
  (`maxAttempts`, `initialBackoffSeconds`, `maxBackoffSeconds`) say how to back off; none says how
  often the scheduler polls.
- `pom.xml` — add a ShedLock Spring Boot starter + JDBC-template provider (exact Maven coordinates
  confirmed against real repository metadata during implementation, not guessed here).
- `NotificationServiceApplication.java` — add `@EnableScheduling` + `@EnableSchedulerLock`.

**Out:**
- Any change to `EmailChannel`/`InAppChannel`/`NotificationChannel` (L5 — the retry path replays
  through the existing seam, no channel-specific retry logic).
- Any change to `preferenceResolver.resolve`/`templateRenderer.render` failure handling — unchanged,
  still an immediate `FAILED` with no retry (a rendering/preference failure is a code/data defect, not
  a channel-delivery transient error — R12's own wording is "channel delivery fails", not "dispatch
  fails").
- A separate `dead_letter` table or a Kafka DLQ topic (O4) — `DEAD_LETTERED` is already a reserved
  `delivery_log.outcome` value; this task writes to it, it does not build new infrastructure for it.
- Any retroactive retry of an already-`FAILED` row from before this task existed.

## Business Rules

- **R12.** A transient channel-delivery failure is marked `FAILED`, retained in the log, and a
  bounded retry is scheduled per the backoff policy.
- **R13.** Once `maxAttempts` is reached, retrying stops and a terminal `DEAD_LETTERED` outcome is
  recorded — never an indefinite retry.

## Locked Decisions

- **L7.** Bounded retry, then dead-letter — enforced by `RetryProperties.maxAttempts`/
  `maxBackoffSeconds` and `RetryScheduler`'s own terminal-state write.
- **L2.** Consume-only — the replay path re-resolves the recipient email via the existing local
  `ContactProjectionUpdater` projection, never a live cross-service call.
- **L3.** Append-only delivery log — every replay attempt, including the dead-lettering one, writes a
  **new** `delivery_log` row (with the real, incremented `attempt` value — no longer hardcoded to `1`).
- **L5.** Channels behind one interface — `RetryScheduler`/`DeliveryRetry` never call a channel bean
  directly; they replay exclusively through `DeliveryOrchestrator`'s own existing render→send→log
  sequence.
- **L11.** Module boundaries — `DeliveryRetry`/`RetryScheduler` live in `delivery/`, the same package
  as `DeliveryLog`/`DeliveryOrchestrator`; no new feature-module import.

## Dependencies

`channel/NotificationChannel.java`, `template/TemplateRenderer.java`,
`preference/ContactProjectionUpdater.java`, `delivery/DeliveryLogRepository.java`,
`common/SecretSafeLogging.java`, `common/config/RetryProperties.java` (extended), `java.time.Clock`
(existing `@Primary` bean), a new ShedLock starter + JDBC provider, Jackson's `ObjectMapper`
(already transitively available via `spring-boot-starter-web`) to serialize/deserialize
`event_data_json`.

## Inputs

`RetryScheduler`'s own scheduled method takes no external input — it polls
`DeliveryRetryRepository` for rows where `next_attempt_at <= clock.instant()`. Each due row supplies
everything the replay needs: `accountUuid`, `channel`, `notificationKind`, `sourceEventKey`,
`eventDataJson` (deserialized back to `Map<String,String>`), and the most recent `attempt` number.

## Outputs

A successful replay: one new `delivery_log` row (`outcome="SENT"`), the `delivery_retry` row deleted.
A transient failure below `maxAttempts`: one new `delivery_log` row (`outcome="FAILED"`), the
`delivery_retry` row updated (`attempt` incremented, `next_attempt_at` recomputed). A transient
failure at `maxAttempts`: one new `delivery_log` row (`outcome="DEAD_LETTERED"`), the `delivery_retry`
row deleted. A *newly-discovered permanent* failure on a retry attempt (e.g. a different exception
shape than the one that originally triggered the retry): one new `delivery_log` row
(`outcome="FAILED"`), the `delivery_retry` row deleted, no dead-letter (retrying again would never
have fixed it, but it also never "exhausted" a bounded retry budget — a distinct terminal case from
R13's own exhaustion wording).

## State Changes

Inserts one `delivery_retry` row on a channel's first transient failure. Each scheduler sweep either
updates that row (reschedule) or deletes it (success, dead-letter, or a newly-discovered permanent
failure). Every outcome also appends exactly one new, append-only `delivery_log` row (L3) — no
existing row anywhere is ever updated.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryRetry.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryRetryRepository.java`
- `services/notification/src/main/java/com/themistra/notification/delivery/RetryScheduler.java`
- `services/notification/src/main/resources/db/migration/V9__delivery_retry_add_replay_columns.sql`
- `services/notification/src/main/resources/db/migration/V10__notification_app_delivery_retry_grant.sql`
- `services/notification/src/main/resources/db/migration/V11__notification_app_shedlock_grant.sql`

## Files to Modify

- `services/notification/src/main/java/com/themistra/notification/delivery/DeliveryOrchestrator.java`
  — classify `channelBean.send`'s own failure, schedule the first retry on a transient one, add the
  package-visible replay method.
- `services/notification/src/main/java/com/themistra/notification/common/config/RetryProperties.java`
  — add `schedulerIntervalSeconds`.
- `services/notification/src/main/resources/application.properties` — add the new property's
  default; confirm/override the existing three (Q6).
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
  — `@EnableScheduling`, `@EnableSchedulerLock`.
- `services/notification/pom.xml` — ShedLock dependency.
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  — remove `delivery_retry`/`shedlock` from `UNGRANTED_TABLES`; add dedicated grant-shape tests.
- `T01SkeletonRegressionTest.java` — expected, per every prior task's own precedent.

## Files NOT to Modify

- `channel/EmailChannel.java`, `channel/InAppChannel.java`, `channel/NotificationChannel.java` (L5).
- `preference/PreferenceResolver.java`, `template/TemplateRenderer.java` — their own failure modes
  are unchanged by this task.
- `V1__notifications_baseline.sql` through `V8__*.sql` — additive-only; no prior migration is edited.
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment`.

## Acceptance Criteria

1. **AC1** (R12). A transient `channelBean.send` failure writes a `FAILED` `delivery_log` row AND
   inserts a `delivery_retry` row with `next_attempt_at` = now + `initialBackoffSeconds`.
2. **AC2** (R12, L7). Backoff is bounded: `next_attempt_at` for attempt *n* is
   `min(initialBackoffSeconds * 2^(n-1), maxBackoffSeconds)` — never unbounded growth.
3. **AC3** (R13, L7). The attempt that would exceed `maxAttempts` instead writes `DEAD_LETTERED` and
   deletes the `delivery_retry` row — no further retry is ever scheduled for it.
4. **AC4** (L3). Every replay, success or failure, appends a new `delivery_log` row with the correct,
   real `attempt` number; no row is ever updated.
5. **AC5** (L5). The replay path calls only `DeliveryOrchestrator`'s own existing render→send→log
   sequence through the `NotificationChannel` interface — no channel-specific branching in
   `RetryScheduler`/`DeliveryRetry`.
6. **AC6** (`agents.md`). `RetryScheduler`'s scheduled method is `@SchedulerLock`-guarded.
7. **AC7** (Q6/O4). `RetryProperties`'s real values are confirmed explicitly in this task's own
   commit message/artifact (not silently left as T03's placeholder); `DEAD_LETTERED` via
   `delivery_log` is confirmed as the dead-letter destination, not a new table/topic.
8. **AC8** (L4/R15). Any error detail persisted on a retry's own `delivery_log` row goes through
   `SecretSafeLogging.redact()`, identically to every existing write.
9. **AC9** (consistency with T11's own frozen AC9). A single due row's own replay failure (including
   an exception thrown by the replay logic itself, not just the channel send) never aborts the
   sweep for every other due row, and never crashes the scheduled job.
10. **AC10.** A permanent (`IllegalArgumentException`-shaped) failure on the *original* attempt never
    inserts a `delivery_retry` row at all (unchanged from today's behavior). A permanent failure
    discovered mid-retry deletes the existing `delivery_retry` row and writes `FAILED`, not
    `DEAD_LETTERED`.

## Required Tests

Named: `shouldMarkDeliveryFailedAndScheduleRetryOnTransientError` (R12),
`shouldStopRetryingAndDeadLetterAfterMaxAttempts` (R13). Plus: exact backoff-value assertions at
several attempt numbers (fixed `Clock`) including the `maxBackoffSeconds` cap; the exact
`maxAttempts`-th-attempt boundary (one before retries, the `maxAttempts`-th dead-letters); a
successful mid-sequence retry deletes the row and schedules nothing further; a permanent failure
(original and mid-retry) never schedules/continues a retry; `RetryScheduler`'s own `@SchedulerLock`
annotation presence (structural) plus a real-DB integration test that it only processes due rows;
the `V9`/`V10`/`V11` grant-shape tests (mirrors T02/T13's established pattern); a real end-to-end
integration test (Testcontainers) proving a transient `InAppChannel`/`EmailChannel` failure is
actually replayed and eventually either succeeds or dead-letters.

## Constraints

- **Classification rule (proposed, subject to Phase 3 challenge):** `IllegalArgumentException` from
  `channelBean.send` is permanent (deterministic input-validation failure — identical on every
  retry); every other exception is transient. This is the simplest rule consistent with both real
  channels' own existing validation-first design (T12/T13) and is flagged here explicitly as a design
  proposal, not an extracted requirement — neither spec document enumerates exception types.
- **Schema amendment is necessary, not optional:** the already-applied `V1` `delivery_retry` shape
  cannot carry what a retry needs to re-render/re-send. Adding `notification_kind`/`event_data_json`
  columns via a new migration is the minimal fix; storing the full rendered message instead was
  rejected as redundant (a retry must re-render to get a fresh `createdAt`/any time-sensitive content
  correctly versioned).
- **Transaction boundary:** `RetryScheduler`'s own per-row replay must be `@Transactional` per row
  (not one transaction for the whole sweep), so one row's rollback can never affect another's already-
  committed outcome — mirrors `DeliveryOrchestrator.dispatch`'s own existing per-call transactionality.
- **Thread-safety:** irrelevant beyond what ShedLock itself already guarantees (at most one replica
  runs a sweep at a time); within one sweep, rows are processed sequentially.
- **Null handling:** `event_data_json` is always a valid JSON object (`{}` at minimum, never `null`/
  unparseable) — serialized once, at the point the first retry is scheduled, from the same
  `Map<String,String>` `dispatchOneChannel` already has in hand.

## Open Questions

No blockers. The transient/permanent classification rule, the backoff formula, the `delivery_retry`
schema amendment, and the new `RetryProperties` field are all concrete proposals for Phase 3's own
adversarial review to challenge — the same class of internally-resolved, review-tested decision every
prior task in this pipeline has made without escalation to the user.
