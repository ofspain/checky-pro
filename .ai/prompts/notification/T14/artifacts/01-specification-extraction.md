# notification · T14 · Phase 1 — Specification Extraction

## Business Rules

- **R11** (context, not new — already implemented by T11). WHEN a delivery is attempted on any
  channel, THEN the system SHALL append a delivery-log record capturing recipient, channel, source
  event key, template version, outcome, and timestamp. Cited because this task's own new
  `DEAD_LETTERED` outcome and incremented `attempt` values are written into the exact same
  `delivery_log` row shape R11 already governs — this task extends R11's own implementation, it does
  not re-implement it.
- **R12**. IF a channel delivery fails with a transient error, THEN the system SHALL mark the
  attempt `FAILED`, retain it in the log, and schedule a bounded retry per the backoff policy.
- **R13**. IF a delivery has failed the maximum number of attempts, THEN the system SHALL stop
  retrying, record a terminal `DEAD_LETTERED` outcome, and SHALL NOT retry indefinitely.

## Locked Decisions

- **L7**. Bounded retry, then dead-letter. Transient failures retry on a bounded backoff and
  terminate in a `DEAD_LETTERED` outcome (R12, R13). No infinite retry loop; no silent drop.
- **L2** (constrains the retry-replay path, not only the original dispatch). Consume-only at launch
  — this service initiates no domain state and makes no synchronous cross-service call on the
  delivery path. A scheduled retry re-attempting a channel send must not introduce a new synchronous
  cross-service call this service didn't already make on the original attempt.
- **L3**. Dispute-grade delivery log, append-only — a retry adds a new `delivery_log` row, it never
  overwrites the prior attempt row. Directly constrains how a retried attempt's outcome is recorded.
- **L5**. Channels behind one interface; the delivery orchestrator is channel-agnostic. The retry
  mechanism must not require `RetryScheduler`/`DeliveryRetry` to know about `EmailChannel`/
  `InAppChannel` specifics — it replays through the same `NotificationChannel` seam, or through
  whatever orchestration entry point Phase 2 designs, without per-channel branching of its own.
- **L11**. Module boundaries — package-by-feature, no feature module imports another feature
  module's entity. `DeliveryRetry`/`RetryScheduler` live in `delivery/` (per `design.md` §6's own
  package map); they must not import another feature module's entity directly (the same class of
  violation T13 committed and fixed at Phase 9 for `InAppChannel`/`InappNotification`).

## Files involved

**Existing — to read/extend, not replace:**
- `delivery/DeliveryOrchestrator.java` — the sole place a channel's `send` call fails today; this
  task's own transient/permanent distinction and retry-scheduling trigger point most likely lives
  here or in a new seam it calls into (Phase 2's own design decision, not assumed here).
- `delivery/DeliveryLog.java` / `DeliveryLogRepository.java` — `outcome` already has `DEAD_LETTERED`
  reserved in its own DB `CHECK` constraint (`V1`); `attempt` is already a real column, hardcoded to
  `1` by every caller today, with its own Javadoc pointing at this exact task to start incrementing
  it.
- `common/config/RetryProperties.java` — already validated (`maxAttempts`, `initialBackoffSeconds`,
  `maxBackoffSeconds`), already bound to placeholder-labelled real values in
  `application.properties`, completely unconsumed anywhere today.
- `NotificationServiceApplication.java` — needs `@EnableScheduling` and (once the ShedLock starter
  is added) `@EnableSchedulerLock`, per its own Javadoc's explicit instruction to add each in the
  task that introduces the thing it enables.
- `NotificationBaselineMigrationIntegrationTest.java` — `UNGRANTED_TABLES` currently lists exactly
  `delivery_retry` and `shedlock`; this task removes both and adds dedicated grant-shape tests,
  mirroring the T13 precedent for `inapp_notifications`.
- `pom.xml` — no ShedLock dependency exists today; this task adds one.

**New, per `design.md` §6's own package map:**
- `delivery/DeliveryRetry.java` — entity over the already-existing `delivery_retry` table
  (`id`, `source_event_key`, `account_uuid`, `channel`, `attempt`, `next_attempt_at`, `created_at`).
- `delivery/DeliveryRetryRepository.java`.
- `delivery/RetryScheduler.java` — `@Scheduled` + ShedLock-guarded job that polls due rows and
  re-attempts delivery.
- A new Flyway migration (`V9`, following the established one-migration-per-table grant pattern) —
  `GRANT` the specific privileges `notification_app` needs on `delivery_retry` (and whatever
  `shedlock` access the chosen ShedLock JDBC provider itself requires — typically `SELECT`, `INSERT`,
  `UPDATE` for lock acquisition/renewal, confirmed against the provider's own documented SQL before
  writing the grant, not assumed).
- **Possible further migration** — only if Phase 2/3 concludes the existing `delivery_retry` schema
  genuinely cannot carry what a retry needs to re-send (see Open Questions below); not assumed here.

## Dependencies

- `channel/NotificationChannel.java` (interface, L5) — whatever the retry-replay path calls to
  actually resend, it almost certainly goes back through this same seam.
- `template/TemplateRenderer.java` (T09) — a retried send likely needs to re-render (the
  `delivery_retry` table stores no rendered message body today), which needs `templateName`/
  `channel`/the original event data.
- `preference/ContactProjectionUpdater.java` (T05) — if a retry re-resolves the recipient email
  rather than storing it, it needs this.
- `common/SecretSafeLogging.java` (T10) — any new error detail this task persists/logs must be
  redacted through the existing path, not a new one.
- `delivery/DeliveryLogRepository.java` — already exists; this task's new `DEAD_LETTERED`/
  incremented-`attempt` rows are written through it.
- A ShedLock Spring Boot starter + JDBC provider (new Maven dependency — exact artifact coordinates
  to be confirmed against real Maven Central metadata at Phase 2/3, not assumed from memory).
- `common/config/RetryProperties.java` — the bounded policy values (`maxAttempts`,
  `initialBackoffSeconds`, `maxBackoffSeconds`) this task is the first real consumer of.
- Clock (`java.time.Clock`, the existing `@Primary` fixed-clock-in-tests convention) — needed for
  both `next_attempt_at` computation and the fixed-time assertions this task's own tests will need.

## Acceptance Criteria

1. **AC1** (R12). A channel delivery failure classified as transient results in a `delivery_log` row
   with `outcome="FAILED"` (not immediately `DEAD_LETTERED`) AND a scheduled retry (a new
   `delivery_retry` row, or an updated `next_attempt_at` on an existing one) at a bounded backoff
   interval per `RetryProperties`.
2. **AC2** (R12, L7). The retry schedule is bounded — bounded by both an attempt ceiling
   (`RetryProperties.maxAttempts`) and a backoff ceiling (`RetryProperties.maxBackoffSeconds`); it
   must never retry indefinitely or schedule an ever-growing backoff past the configured maximum.
3. **AC3** (R13, L7). Once a delivery has failed `maxAttempts` times, the system stops retrying,
   writes a terminal `delivery_log` row with `outcome="DEAD_LETTERED"`, and removes/marks-terminal
   the corresponding `delivery_retry` row so it is never picked up again.
4. **AC4** (L3). Every retry attempt — including the final dead-lettering one — appends a **new**
   `delivery_log` row; no existing row is ever updated or overwritten.
5. **AC5** (L5). The retry-replay path reaches the channel exclusively through the existing
   `NotificationChannel` interface (or an orchestration entry point that itself only calls that
   interface) — no new per-channel branching inside `RetryScheduler`/`DeliveryRetry` themselves.
6. **AC6** (standing rule, `agents.md` line 62). `RetryScheduler`'s own `@Scheduled` method is
   ShedLock-guarded (`@SchedulerLock`), so a multi-replica deployment never runs two overlapping
   retry sweeps concurrently.
7. **AC7** (Q6/O4). The real `maxAttempts`/backoff-schedule values are confirmed (not left as T03's
   own placeholder defaults) and the dead-letter destination is confirmed as `delivery_log`'s own
   `DEAD_LETTERED` outcome (not a separate `dead_letter` table or Kafka DLQ topic) — or, if either
   default is overridden, the override is explicit and justified, not silently inherited.
8. **AC8** (L4/R15, standing pattern). Any error detail this task persists or logs goes through
   `SecretSafeLogging.redact()`, exactly like every existing `delivery_log.error_detail` write.
9. **AC9** (consistency with T11's own frozen AC9). Neither `RetryScheduler`'s own scheduled method
   nor whatever it calls into may let an exception escape uncaught — a failure to even attempt a
   retry must not crash the scheduled job for every other due row, and must not silently stop future
   runs.

## Tests required

- Named (`package.md` §8): `shouldMarkDeliveryFailedAndScheduleRetryOnTransientError` (R12),
  `shouldStopRetryingAndDeadLetterAfterMaxAttempts` (R13).
- Boundary / implied:
  - A transient failure below `maxAttempts` schedules a retry at the correct backoff interval
    (exact value, via a fixed `Clock`), not merely "some future time."
  - The backoff interval is bounded by `maxBackoffSeconds` once the computed exponential/linear
    value would otherwise exceed it (AC2).
  - The `maxAttempts`-th failure dead-letters instead of scheduling another retry (the exact
    boundary — one before is still a retry, the `maxAttempts`-th is terminal).
  - A successful retry (the channel send succeeds on re-attempt) writes `outcome="SENT"` and removes
    the `delivery_retry` row — no further retry is scheduled for an already-succeeded delivery.
  - `RetryScheduler`'s own ShedLock guard — a concurrency/wiring proof (the exact shape to be
    decided at Phase 2: either a real multi-instance Testcontainers proof, or a structural
    `@SchedulerLock` annotation-presence check, mirroring how lighter-weight wiring guarantees have
    been proven elsewhere in this codebase when a full concurrent-process proof would be
    disproportionate).
  - A `delivery_retry` grant-shape test (`notification_app` can read/write exactly what
    `RetryScheduler` needs, nothing more) — mirrors the T02/T13 established pattern.
  - A permanent (non-transient) failure never schedules a retry at all and dead-letters (or stays
    `FAILED`, depending on Phase 2's own resolved transient/permanent taxonomy) on the first attempt.

## Open Questions

- **What classifies a failure as "transient" vs. permanent is not defined anywhere in
  `requirements.md`/`design.md`.** Neither document enumerates exception types or conditions. This
  is a genuine blocker for AC1/AC3's own precise behavior and must be resolved at Phase 2 (a design
  decision, not an extraction) — not guessed at here.
- **Q6** (`package.md` §11): "Max attempts, backoff schedule, and dead-letter destination for
  transient email failures. Placeholder in `design.md` §4b-O4." The task statement itself says
  "Confirm policy via Q6" — this is this task's own explicit job, to be resolved at Phase 2/3, not a
  blocker to extraction itself since `RetryProperties`'s own already-set defaults give a concrete
  starting point to confirm or override.
- **The existing `delivery_retry` table schema has no column for the data needed to actually
  re-send** (no template name, no rendered body, no original event-data map) — flagged already at
  Phase 0. Whether this needs a new migration (adding columns) or is resolved by re-deriving
  everything from `delivery_log`'s own already-stored `template_name`/`template_version` plus a
  fresh `TemplateRenderer.render` call (meaning `delivery_retry` only ever needs to carry enough to
  look up the right `delivery_log` row, or to re-call `DeliveryOrchestrator.dispatch` from scratch
  given `accountUuid`/`notificationKind`) is a genuine open design question for Phase 2/3, not
  resolved here.
- **O4's own two options** (a `dead_letter` table vs. a Kafka DLQ topic) were never finalized in
  `design.md` itself — the already-reserved `DEAD_LETTERED` value in `delivery_log`'s own `CHECK`
  constraint strongly suggests "record it in `delivery_log` itself" is the intended answer, but this
  is Phase 2's own job to confirm explicitly against Q6, not assumed here.
- **No blocker to starting Phase 2** — every open item above is a design question this task's own
  adversarial-review-backed design phase (Phase 2/3) exists to resolve, not a missing prerequisite
  from an earlier task.
