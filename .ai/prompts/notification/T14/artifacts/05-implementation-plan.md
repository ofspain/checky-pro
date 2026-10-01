# notification · T14 · Phase 5 — Implementation Plan

Every file below traces to the frozen brief's own Files to Create/Modify. No file is added beyond
what Phase 4 authorized. No code — signatures and behavior only.

## A note on shared classification logic (the frozen brief's own explicit constraint)

`DeliveryOrchestrator` gains one new private helper, `attemptSend(...)`, that is the **sole** place
`channelBean.send`'s own result is turned into a `DeliveryLog` row and a `DeliveryOutcome`. Both the
original dispatch path (`dispatchOneChannel`) and the new `replay` method call it — satisfying the
frozen brief's own constraint that these two classification paths "MUST share one implementation, not
two parallel copies." Everything *before* the channel call (mapping lookup, recipient resolution,
preference check, template render, channel-bean lookup) is necessarily re-run independently by
`replay` — state may have changed since the original attempt (Finding #3) — and is not shared code,
mirroring the same accepted, disclosed duplication precedent `InappStreamController`/
`InappReadController`'s own `accountUuidFrom` set at T13.

## Files to create

### `delivery/DeliveryRetry.java`
```
@Entity
@Table(name = "delivery_retry", schema = "notifications")
public class DeliveryRetry {
    protected DeliveryRetry() // JPA only
    public DeliveryRetry(String sourceEventKey, UUID accountUuid, String channel, String notificationKind,
                          String eventDataJson, short attempt, Instant nextAttemptAt, Instant createdAt)
    public Long getId()
    public String getSourceEventKey()
    public UUID getAccountUuid()
    public String getChannel()
    public String getNotificationKind()
    public String getEventDataJson()
    public short getAttempt()
    public Instant getNextAttemptAt()
    public Instant getCreatedAt()
    public void reschedule(short attempt, Instant nextAttemptAt)
}
```
The **one genuinely mutable entity in this module** — every sibling (`DeliveryLog`,
`InappNotification`, `ProcessedEvent`) is an append-only or read-only record of something that already
happened; `DeliveryRetry` is a live scheduling queue entry, and `reschedule` is its own one deliberate
mutator (Finding #5's own pinned semantics), called only by `RetryScheduler` on a `TRANSIENT_FAILURE`
outcome — JPA's own dirty-checking persists the change on transaction commit, no explicit `save()`
call needed. `createdAt` is set once, at construction, from the injected `Clock` (Finding #9 —
mirrors `DeliveryLog`'s own established convention, never a DB-side default). `toString()` is
deliberately NOT overridden (default `Object.toString()`, no field interpolation) — Finding #2's own
explicit requirement that `eventDataJson` (which carries the same raw one-time token the original
event did) never appears in an accidental log line via a careless `log.info("{}", retry)` call
anywhere in this class's own lifetime.

### `delivery/DeliveryRetryRepository.java`
```
interface DeliveryRetryRepository extends JpaRepository<DeliveryRetry, Long> {
    List<DeliveryRetry> findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(Instant now);
}
```
Package-private — mirrors `DeliveryLogRepository`'s own established convention (T11 Phase 8
Finding #3's own rejected "matches siblings" justification already settled this; every repository in
this module is package-private, consumed only from within `delivery/`). Ordering per Finding #10.

### `delivery/RetryScheduler.java`
```
@Component
public class RetryScheduler {
    RetryScheduler(DeliveryRetryRepository retryRepository, DeliveryLogRepository deliveryLogRepository,
                    DeliveryOrchestrator orchestrator, RetryProperties retryProperties,
                    ObjectMapper objectMapper, Clock clock)
    @Scheduled(fixedDelayString = "${themistra.notification.retry.scheduler-interval-seconds}", timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "retry-scheduler", lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void sweep()
    @Transactional
    void processOne(DeliveryRetry retry)
    private Instant computeNextAttemptAt(short attemptsAlreadyMade)
}
```
`sweep()` itself is NOT `@Transactional` (the frozen brief's own explicit constraint: one transaction
per row, never one for the whole sweep) — it polls once, then loops, calling `processOne` for each due
row inside its own `try/catch` (AC9: one row's own failure, including an exception `processOne` itself
throws, is logged and skipped, never aborting the sweep for the rows after it). `processOne` is
`@Transactional` and package-visible only to let a test call it directly for a single row without
needing to wait for `@Scheduled`'s own real timing. It deserializes `retry.getEventDataJson()` via
`objectMapper`; a `JsonProcessingException` here is the poison-pill case (Finding #7) — logs it,
calls `orchestrator.recordUnrecoverableFailure(...)` (new method, below) to write a `DEAD_LETTERED`
row, deletes the `delivery_retry` row, and returns, never calling `replay`. Otherwise calls
`orchestrator.replay(...)` and switches on the returned `DeliveryOutcome`: `SENT`/`SUPPRESSED`/
`PERMANENT_FAILURE`/`TRANSIENT_EXHAUSTED` → `retryRepository.delete(retry)`; `TRANSIENT_FAILURE` →
`retry.reschedule(newAttempt, computeNextAttemptAt(newAttempt))`. `computeNextAttemptAt` implements
the pinned backoff formula: `clock.instant().plusSeconds(min(initialBackoffSeconds * 2^(n-1),
maxBackoffSeconds))`.

### `db/migration/V9__delivery_retry_add_replay_columns.sql`
```sql
ALTER TABLE notifications.delivery_retry
    ADD COLUMN notification_kind VARCHAR(64) NOT NULL,
    ADD COLUMN event_data_json TEXT NOT NULL;
```
No `DEFAULT`/backfill needed — `delivery_retry` has had zero rows since `V1`, confirmed directly (no
code anywhere has ever written to it before this task).

### `db/migration/V10__notification_app_delivery_retry_grant.sql`
```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON notifications.delivery_retry TO notification_app;
```
The first table in this module granted `UPDATE`/`DELETE` — every prior grant (`V4`-`V8`) was
`INSERT, SELECT` or `SELECT`-only, since every prior table was append-only or read-only from the
app's own perspective. `delivery_retry` is a genuine mutable queue: `RetryScheduler` updates a
rescheduled row in place and deletes a resolved one.

### `db/migration/V11__notification_app_shedlock_grant.sql`
```sql
GRANT SELECT, INSERT, UPDATE ON notifications.shedlock TO notification_app;
```
Matches the ShedLock JDBC-template provider's own documented SQL shape (insert-if-absent, then
update to extend/release the lock) — confirmed against the provider's own documentation during
implementation, not assumed. No `DELETE` — ShedLock never removes a lock row, it only ever extends or
expires one.

## Files to modify

- **`delivery/DeliveryLog.java`** (Finding #1) — constructor gains a `short attempt` parameter,
  replacing the hardcoded `this.attempt = 1`.
- **`delivery/DeliveryOrchestrator.java`** — the largest change in this task:
  - New constructor dependencies: `DeliveryRetryRepository`, `RetryProperties`, `ObjectMapper`.
  - `save(...)` gains a `short attempt` parameter (threaded through from every call site).
  - New private `DeliveryOutcome` enum: `SENT`, `SUPPRESSED`, `PERMANENT_FAILURE`,
    `TRANSIENT_FAILURE`, `TRANSIENT_EXHAUSTED`.
  - New private `attemptSend(UUID accountUuid, String recipient, String channel, String sourceEventKey,
    String templateName, Integer templateVersion, String category, NotificationChannel channelBean,
    TemplateRenderer.RenderedMessage message, short attemptNumber)` → `DeliveryOutcome` — the shared
    classification helper described above. `IllegalArgumentException` → `PERMANENT_FAILURE`; any other
    `Exception`, computed against `retryProperties.maxAttempts()`, → `TRANSIENT_FAILURE` or
    `TRANSIENT_EXHAUSTED` (pinned exhaustion-check semantics; writes `DEAD_LETTERED` itself when
    exhausted — no separate FAILED row for that same attempt).
  - `dispatchOneChannel`'s own `channelBean.send(...)` call site replaced by
    `attemptSend(..., (short) 1)`; on a `TRANSIENT_FAILURE` result, calls new private
    `scheduleFirstRetry(UUID accountUuid, String channel, String sourceEventKey, String notificationKind,
    Map<String,String> eventData)`, which serializes `eventData` via `objectMapper` and saves a new
    `DeliveryRetry` row (`attempt=1`, `nextAttemptAt = clock.instant().plusSeconds(initialBackoffSeconds)`).
  - New package-visible `DeliveryOutcome replay(UUID accountUuid, String channel, String notificationKind,
    String sourceEventKey, Map<String,String> eventData, short attemptsAlreadyMade)` — called only by
    `RetryScheduler`. Re-resolves the `NotificationMapping` (an unknown `notificationKind` → immediate
    `PERMANENT_FAILURE`, a defensive case that should not occur in practice), re-resolves the recipient,
    **re-checks** `preferenceResolver.resolve` (Finding #3 — a now-disabled channel →
    `SUPPRESSED`, retry row deleted, no further attempt), re-renders via `TemplateRenderer`, re-resolves
    the channel bean, then delegates to the same `attemptSend` with `(short) (attemptsAlreadyMade + 1)`.
    Every early-return guard writes its own `delivery_log` row exactly like `dispatchOneChannel`'s own
    established pattern, classified `PERMANENT_FAILURE` (a guard failure is never itself subject to
    `R12`'s own "channel delivery fails" wording — out of this task's retry scope, per the frozen
    brief's own scoping decision).
  - New package-visible `void recordUnrecoverableFailure(UUID accountUuid, String channel,
    String sourceEventKey, short attempt, String detail)` — thin wrapper around `save(...)` with
    `outcome="DEAD_LETTERED"`, called only by `RetryScheduler`'s own poison-pill path (Finding #7); kept
    on `DeliveryOrchestrator` so the one `SecretSafeLogging.redact()`-wrapping `save` helper is never
    duplicated.
- **`common/config/RetryProperties.java`** — add `@Min(1) int schedulerIntervalSeconds`.
- **`src/main/resources/application.properties`** — add
  `themistra.notification.retry.scheduler-interval-seconds=${RETRY_SCHEDULER_INTERVAL_SECONDS:30}`;
  update the existing retry-block comment to state the three pre-existing values (`max-attempts=5`,
  `initial-backoff-seconds=30`, `max-backoff-seconds=3600`) are now **confirmed**, not placeholders
  (AC7/Q6).
- **`NotificationServiceApplication.java`** — add `@EnableScheduling` and `@EnableSchedulerLock`
  (`defaultLockAtMostFor` set to a safe ceiling, e.g. `"PT10M"`); update its own Javadoc (which
  currently says both annotations are "still absent... task 14") to reflect they are now present.
- **`pom.xml`** — add `shedlock-spring` + `shedlock-provider-jdbc-template` (exact version coordinates
  confirmed against real Maven Central metadata during implementation).
- **`NotificationBaselineMigrationIntegrationTest.java`** — remove `delivery_retry`/`shedlock` from
  `UNGRANTED_TABLES`; add two dedicated grant-shape tests mirroring the T02/T13 established pattern
  (one proving the full `SELECT/INSERT/UPDATE/DELETE` grant on `delivery_retry`, one proving
  `SELECT/INSERT/UPDATE` — no `DELETE` — on `shedlock`); extend the Flyway-version-list assertion
  through `"11"`.
- **`T01SkeletonRegressionTest.java`** — authorized file-inventory list: add
  `delivery/DeliveryRetry.java`, `delivery/DeliveryRetryRepository.java`, `delivery/RetryScheduler.java`.

## Public methods (signatures)

`DeliveryRetry`: 8 getters, `reschedule(short, Instant)`. `DeliveryRetryRepository`:
`findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(Instant)`. `RetryScheduler`:
`sweep()`. `DeliveryLog`: constructor signature change only (no new getter — `getAttempt()` already
exists). `RetryProperties`: `schedulerIntervalSeconds()` (record component, auto-generated).

## Private / package-visible methods

`DeliveryOrchestrator`: `attemptSend(...)` (private), `scheduleFirstRetry(...)` (private),
`replay(...)` (package-visible), `recordUnrecoverableFailure(...)` (package-visible). `RetryScheduler`:
`processOne(DeliveryRetry)` (package-visible, `@Transactional`), `computeNextAttemptAt(short)`
(private).

## Entities used

`DeliveryRetry` (new). `DeliveryLog` (T11, constructor signature changed).

## Repositories used

`DeliveryRetryRepository` (new). `DeliveryLogRepository` (T11, unchanged interface, now also
consumed by `RetryScheduler`'s own poison-pill path via `DeliveryOrchestrator.recordUnrecoverableFailure`
— no, `recordUnrecoverableFailure` lives on `DeliveryOrchestrator`, which already holds
`DeliveryLogRepository`; `RetryScheduler` itself never touches `DeliveryLogRepository` directly).

## Services used

`PreferenceResolver` (T08, re-checked on replay), `TemplateRenderer` (T09, re-rendered on replay),
`ContactProjectionUpdater` (T05, re-resolved on replay), `SecretSafeLogging` (T10, unchanged
redaction path), `Clock` (existing bean), Jackson `ObjectMapper` (new dependency on
`DeliveryOrchestrator` and `RetryScheduler`), ShedLock's `@SchedulerLock` (new).

## Unit tests required

- `DeliveryOrchestratorTest` — every existing `save`-shaped assertion gains the new `attempt`
  argument (mechanical); new cases: a transient `channelBean.send` failure inserts a `DeliveryRetry`
  row with `attempt=1` and the correct `nextAttemptAt`; a permanent failure never inserts one; the
  degenerate `maxAttempts=1` case dead-letters the very first failure with no retry row at all (AC14);
  `replay` re-checks preferences and returns `SUPPRESSED` when now disabled (AC11); `replay` returns
  each of `SENT`/`PERMANENT_FAILURE`/`TRANSIENT_FAILURE`/`TRANSIENT_EXHAUSTED` for the corresponding
  scenario, each writing the correct `delivery_log.attempt`/`outcome` (AC13).
- `DeliveryRetryTest` (new, plain JUnit) — `reschedule` mutates both fields; `toString()` never
  includes `eventDataJson` (static/reflection guard, Finding #2).
- `RetryScheduler` unit-level: `computeNextAttemptAt`'s own exact backoff values at several attempt
  counts, including the `maxBackoffSeconds` cap (pinned semantics); `processOne`'s own poison-pill path
  (a deliberately malformed `eventDataJson` string, mocked repositories) writes `DEAD_LETTERED` and
  deletes the row without ever calling `orchestrator.replay` (Finding #7/AC12); `processOne`'s own
  switch over each `DeliveryOutcome` value does the correct delete-vs-reschedule action (mocked
  `orchestrator`/`retryRepository`).

## Integration tests required

- Named tests: `shouldMarkDeliveryFailedAndScheduleRetryOnTransientError` (R12),
  `shouldStopRetryingAndDeadLetterAfterMaxAttempts` (R13) — real Postgres, real `DeliveryOrchestrator`
  → real transient failure (a fake/mock channel bean that throws on its first N calls) → real
  `delivery_retry` row → real `RetryScheduler.processOne` (called directly, not waiting on
  `@Scheduled`'s own real timing) → eventual `SENT` or `DEAD_LETTERED`, with every intermediate
  `delivery_log` row's own `attempt`/`outcome` asserted in order (L3 append-only proof).
- `NotificationBaselineMigrationIntegrationTest`'s own two new grant-shape tests (above).
- A real end-to-end proof that a replay re-render actually uses the *current* `RetryProperties`/
  preference state, not a stale snapshot (AC11, real DB).
- `RetryScheduler`'s own `@SchedulerLock` wiring — a structural annotation-presence check (mirrors how
  this codebase has proven lighter-weight wiring guarantees elsewhere when a full multi-instance
  concurrency proof would be disproportionate for a single-replica local/CI test run); no real
  multi-replica ShedLock contention test, since nothing in this codebase runs multiple real instances
  in CI.

## Execution order

1. `db/migration/V9__delivery_retry_add_replay_columns.sql`.
2. `db/migration/V10__notification_app_delivery_retry_grant.sql`.
3. `db/migration/V11__notification_app_shedlock_grant.sql`.
4. `pom.xml` — add the ShedLock dependency (everything else needs it to compile).
5. `common/config/RetryProperties.java` — add `schedulerIntervalSeconds`.
6. `src/main/resources/application.properties` — new property + confirmed-values comment.
7. `delivery/DeliveryLog.java` — constructor signature change.
8. `delivery/DeliveryOrchestrator.java` — every existing `save` call site updated for the new
   `attempt` parameter; new `DeliveryOutcome` enum, `attemptSend`, `scheduleFirstRetry`, `replay`,
   `recordUnrecoverableFailure`; `dispatchOneChannel` adapted to call `attemptSend`.
9. `delivery/DeliveryRetry.java` (entity).
10. `delivery/DeliveryRetryRepository.java`.
11. `delivery/RetryScheduler.java` (depends on 8, 9, 10).
12. `NotificationServiceApplication.java` — `@EnableScheduling`/`@EnableSchedulerLock`.
13. `NotificationBaselineMigrationIntegrationTest.java` — grant-shape tests, `UNGRANTED_TABLES`
    update, Flyway-version-list extension.
14. `T01SkeletonRegressionTest.java` — authorized file-list update.
15. Tests, in the same dependency order as the files they cover: `DeliveryOrchestratorTest`
    (mechanical + new cases) → `DeliveryRetryTest` → `RetryScheduler` unit tests → the real
    end-to-end integration tests (named tests last, since they exercise the full chain).
16. Full suite: `mvn -pl services/notification clean verify`.
