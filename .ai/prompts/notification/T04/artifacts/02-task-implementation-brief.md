# notification · T04 · Phase 2 — Task Implementation Brief

## Task

Add `ProcessedEvent` (mapped onto T02's already-migrated `processed_events` table) + its repository,
plus a small `IdempotencyGuard` service exposing the dedupe check-and-record operation, under
`consumer/`. Add a grant migration so `notification_app` can actually use it at runtime. Unit-test
the dedupe logic and prove its real-DB/transaction behavior.

## Purpose

Gives every later consumer task (6+) a single, already-correct place to call for "have I seen this
event key before, and if not, record it" — so no individual consumer reimplements dedupe logic or
gets the transaction boundary wrong.

## Scope

**In:**
- `ProcessedEvent` entity — `@Id` on `eventKey` (client-assigned `String`, no `@GeneratedValue` —
  unlike `crypto-service`'s own `OutboxEvent`, which uses a DB-generated surrogate `Long`; this
  entity's own primary key IS the natural business key, `processed_events.event_key`), `eventType`,
  `processedAt` (`Instant`, set via factory method from an injected `Clock`, never `Instant.now()`
  inline). Protected no-arg constructor (JPA only), static `create(...)` factory, no public setters —
  mirrors `OutboxEvent`'s own shape.
- `ProcessedEventRepository extends JpaRepository<ProcessedEvent, String>` — package-private, no
  custom query methods needed (`existsById`/`save` from `JpaRepository` are sufficient).
- `IdempotencyGuard` — a small `@Service`, resolving Phase 1's Open Question #1: `public boolean
  recordIfNew(String eventKey, String eventType)`, `@Transactional` (default `REQUIRED` propagation —
  deliberately not `REQUIRES_NEW`/`NOT_SUPPORTED`, so it always joins whatever transaction the caller
  already has open, never opens/commits one of its own). Checks `repository.existsById(eventKey)`;
  if true, returns `false` (already processed, caller must not deliver again); if false, saves a new
  `ProcessedEvent` via `clock.instant()` and returns `true` (caller may proceed to deliver, and in a
  future consumer task, append to `delivery_log` in that same transaction).
- `common/ClockConfig.java` — `@Bean public Clock clock() { return Clock.systemUTC(); }`, this
  service's first `Clock` bean (mirrors `crypto-service`'s own identical class; T04 is the first task
  in `notification-service` needing an injectable clock, same relationship T04 had to `ClockConfig`
  in `crypto-service`).
- `V4__notification_app_processed_events_grant.sql` — resolves Phase 1's Open Question #2: `INSERT,
  SELECT` only (mirrors `V2`'s own literal-scope narrowness; no `UPDATE`/`DELETE` — a processed event
  key is never revised or removed), following crypto's own incremental-grant migration style exactly
  (passwordless-role guard already exists from T02; this migration is a single `GRANT` line, no new
  `CREATE ROLE`).
- Both a unit test (mocked repository + fixed `Clock`, satisfying the task's own literal "unit-test
  the dedupe" instruction) and a Testcontainers integration test (resolves Phase 1's Open Question
  #3: proves `recordIfNew` genuinely joins an externally-opened transaction — call it inside a
  transaction the test itself opens and then rolls back, assert the row is gone — and separately
  proves the real `event_key PRIMARY KEY` constraint is what makes concurrent/duplicate `recordIfNew`
  calls resolve to exactly one `true`), mirroring T02/T03's own precedent of proactively writing a
  real-DB test whenever genuine schema/transactional behavior is exercised for the first time.

**Out:**
- `DeliveryLog` entity/repository or any code that actually writes to `delivery_log` — a later task's
  own scope (`design.md` §6 places it in a separate `delivery/` module). `IdempotencyGuard` is
  provably *transaction-compatible* with a future `delivery_log` append (via `REQUIRED` propagation),
  not integrated with one — that integration happens when a real consumer calls both in one method.
- Any Kafka consumer, listener, or event-payload deserialization — task 6's own scope.
- `contracts/events/*` validation — no consumer exists yet to validate against them.
- ArchUnit module-boundary enforcement (L11) — task 16's own scope.

## Business Rules

R7, R8 (both stated in full in Phase 1's own extraction) — implemented by `IdempotencyGuard`'s
`recordIfNew`: R7's "record that key" is the `repository.save(...)` call; R8's "SHALL NOT produce a
second delivery" is `recordIfNew` returning `false` on a key it has already recorded, which every
future caller must treat as "skip delivery."

## Locked Decisions

- **L1.** Idempotent by event key — `processed_events` (PK = event key) + `IdempotencyGuard`'s
  join-caller's-transaction behavior together are this decision's schema-level and code-level halves;
  full realization (dedupe check actually gating a real delivery + `delivery_log` append in one
  transaction) completes once a real consumer exists (task 6+).

## Dependencies

`spring-boot-starter-data-jpa` (T01, present), `notification_app`'s DB role (T02) + this task's own
new `V4` grant, `java.time.Clock` (new bean, this task). No Kafka/consumer dependency yet.

## Inputs

`processed_events`'s already-migrated DDL (`V1__notifications_baseline.sql`); `services/crypto`'s own
`OutboxEvent`/`OutboxEventRepository`/`ClockConfig`/`V3__crypto_app_outbox_grant.sql` as structural
precedent (entity/repository/Clock-bean/incremental-grant conventions only — not a functional
precedent, since that task solves producer-side outbox, not consumer-side dedupe).

## Outputs

`ProcessedEvent.java`, `ProcessedEventRepository.java`, `IdempotencyGuard.java`, `ClockConfig.java`,
`V4__notification_app_processed_events_grant.sql`; one new unit test class, one new Testcontainers
integration test class.

## State Changes

Real state change: `notification_app` gains `INSERT, SELECT` on `notifications.processed_events` (a
grant, not a schema change — no new migration touches table shape).

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/consumer/ProcessedEvent.java`
- `.../consumer/ProcessedEventRepository.java`
- `.../consumer/IdempotencyGuard.java`
- `services/notification/src/main/java/com/themistra/notification/common/ClockConfig.java`
- `services/notification/src/main/resources/db/migration/V4__notification_app_processed_events_grant.sql`

## Files to Modify

None. (`NotificationServiceApplication.java` needs no new annotation — `@ConfigurationPropertiesScan`
already present from T03; no scheduled job exists yet to need `@EnableScheduling`.)

## Files NOT to Modify

- `services/notification/src/main/resources/db/migration/V1-V3` (T02's own VERBATIM/grant files —
  `V4` is additive, never edits a prior migration).
- `T01SkeletonRegressionTest.java`, `NotificationBaselineMigrationIntegrationTest.java` (T01/T02's own
  files) — though `NotificationBaselineMigrationIntegrationTest`'s own `UNGRANTED_TABLES` list will
  become stale for `processed_events` once `V4` lands; Phase 6 must flag this precisely (mirrors T03's
  own disclosed, justified `T01SkeletonRegressionTest` updates) rather than silently leave a
  now-incorrect assertion in place or silently rewrite T02's file without disclosure.
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` (precedent only / not started).

## Acceptance Criteria

1. **AC1.** `ProcessedEvent` maps exactly onto `processed_events`'s existing 3 columns;
   `event_key` is the client-assigned `@Id`, no `@GeneratedValue`.
2. **AC2.** `IdempotencyGuard.recordIfNew` returns `true` exactly once per distinct event key and
   `false` on every subsequent call with the same key.
3. **AC3.** `recordIfNew` is `@Transactional` with default (`REQUIRED`) propagation — provably joins
   an already-open caller transaction rather than committing independently.
4. **AC4.** `processedAt` is set from an injected `Clock`, never `Instant.now()` inline.
5. **AC5.** `notification_app` can `INSERT`/`SELECT` on `processed_events` at runtime (new `V4`
   grant); still cannot `UPDATE`/`DELETE`.
6. **AC6.** Both a unit test (fixed `Clock`, mocked repository) and a Testcontainers integration test
   (real Postgres, real transaction rollback, real `PRIMARY KEY` constraint) pass.

## Required Tests

`shouldDedupeDuplicateEventDeliveryByEventKey`, `shouldNotDoubleSendWhenSameEventRedelivered`
(`package.md` §8, unit-level) plus the Testcontainers-backed transaction-join and real-constraint
proofs described in Scope above.

## Constraints

- **Transaction propagation:** `IdempotencyGuard` must never declare `REQUIRES_NEW` or
  `NOT_SUPPORTED` — doing so would silently break L1's own "same transaction as the delivery-log
  append" requirement for every future caller, permanently, in a way no later task could fix without
  touching this class again.
- **Least privilege:** `V4` grants only `INSERT, SELECT` — never `UPDATE`/`DELETE` (a processed event
  key is immutable once recorded; matches `delivery_log`'s own append-only grant philosophy from T02).
- **Null handling:** `recordIfNew`'s two `String` parameters are never null in any real call path
  (a Kafka event always carries a key and a type) — no explicit null-guard is added beyond what a
  `NOT NULL`/non-nullable column mapping already enforces at the JPA/DB layer.
- **Money types / thread-safety:** not applicable.

## Open Questions

No blockers. All 3 of Phase 1's own open questions are resolved as working decisions above, subject
to Phase 3 challenge like any other design choice in this brief.
