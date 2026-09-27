# notification · T04 · Phase 1 — Specification Extraction

## Business Rules

- **R7.** WHEN a consumed event carrying a stable event key is processed, THEN the system SHALL
  record that key so the same event is processed at most once.
- **R8.** IF the same event is redelivered (at-least-once Kafka semantics or a consumer-group
  rebalance), THEN the system SHALL NOT produce a second delivery for it.

## Locked Decisions

- **L1.** Idempotent by event key — every consumer dedupes on the source event's stable key; a
  `processed_events` table with the event key as PK is written in the same transaction as the
  delivery-log append; duplicate Kafka delivery must never double-send. Kafka is at-least-once —
  this is not optional.

## Files involved

**Already exists, read-only precedent/dependency:**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql` (T02) —
  `notifications.processed_events` table, already migrated: `event_key VARCHAR(200) PRIMARY KEY`,
  `event_type VARCHAR(64) NOT NULL`, `processed_at TIMESTAMPTZ NOT NULL DEFAULT now()`. This task
  maps an entity onto this exact existing schema — no new migration for the table itself.
- `services/notification/src/main/resources/db/migration/V2__notification_app_role_and_grants.sql`
  (T02) — grants `notification_app` `INSERT, SELECT` on `delivery_log` only. `processed_events` is
  currently fully ungranted (verified: `NotificationBaselineMigrationIntegrationTest`'s own
  `UNGRANTED_TABLES` list includes it, and asserts SELECT/INSERT/UPDATE/DELETE are all denied).
- `services/crypto/src/main/java/com/themistra/crypto/events/{OutboxEvent,OutboxEventRepository}.java`,
  `common/ClockConfig.java` — structural precedent for entity/repository/`Clock`-bean conventions
  (not a functional precedent — that task solves producer-side outbox, not consumer-side dedupe).

**New, this task's own deliverable (per `design.md` §6, `consumer/` package):**
- `services/notification/src/main/java/com/themistra/notification/consumer/ProcessedEvent.java`
- `.../consumer/ProcessedEventRepository.java`
- A grant migration for `processed_events` (`V4__...`), if Phase 2 decides real DB access is needed
  by this task's own required tests (see Open Questions) — not yet decided.
- The "dedupe helper" the task statement names — exact shape (a `@Service` method, or logic inlined
  on the repository) is undecided; `design.md` §6 does not name a third class here explicitly.

**Not touched:** any file under `spec/`; `services/auth`, `services/crypto` (precedent only);
`delivery/DeliveryLog.java` and any other not-yet-existing feature-module file (out of this task's
own scope — see Open Questions on the "same transaction as delivery-log append" requirement).

## Dependencies

`spring-boot-starter-data-jpa` (T01, present), `notification_app`'s DB role (T02), an injectable
`Clock` bean (not yet added anywhere in `notification-service` — `services/crypto`'s own
`ClockConfig` is the pattern to mirror, per `agents.md`'s "no `java.util.Date`; use `java.time` with
an injectable `Clock`" rule). No Kafka consumer exists yet to depend on (task 6).

## Acceptance Criteria

1. **AC1.** `ProcessedEvent` entity maps exactly onto `processed_events`'s existing columns
   (`event_key` as `@Id`, client-assigned — not a generated surrogate, unlike `OutboxEvent`'s own
   `Long`/`IDENTITY` shape; `event_type`; `processed_at`).
2. **AC2.** A repository/helper exposes a way to check-and-record a given event key exactly once —
   a second attempt with the same key must not silently succeed as if it were new.
3. **AC3.** The dedupe mechanism is usable from within an existing (caller-owned) transaction, not a
   transaction it opens and commits itself — so a future caller can, in the same transaction, also
   append to `delivery_log` (L1's own "same transaction" requirement) without this task needing
   `DeliveryLog` to exist yet.
4. **AC4.** `processed_at` is set from an injected `Clock`, never `Instant.now()` inline.
5. **AC5.** The dedupe logic itself is unit-tested (per the task statement's own literal words),
   using a fixed `Clock` — matching `package.md` §8's own stated testing convention for idempotency
   logic specifically.

## Tests required

Named (`package.md` §8): `shouldDedupeDuplicateEventDeliveryByEventKey` → R7,
`shouldNotDoubleSendWhenSameEventRedelivered` → R8. Both are unit-level per the task statement's own
"Unit-test the dedupe" instruction and `package.md` §8's own intro ("Idempotency and preference logic
are unit-tested with a fixed `Clock`") — not necessarily a Testcontainers-Postgres integration test,
though Phase 2 must decide whether a real-DB proof of the `event_key PRIMARY KEY` constraint's own
enforcement is also warranted (mirroring T02/T03's own proactive-test-writing precedent whenever real
DB/schema behavior is involved), given this is the first task to actually read/write
`processed_events` at runtime.

## Open Questions

1. **The "dedupe helper"'s own shape is unspecified.** `design.md` §6 names only
   `ProcessedEvent.java`/`ProcessedEventRepository.java` under `consumer/` — no third class. Phase 2
   must decide whether the dedupe check-and-record logic lives in a small `@Service` (e.g. a
   `DedupeGuard`/`IdempotencyGuard`-style class) or is simple enough to inline directly wherever a
   future consumer calls it. Not a blocker for this extraction, but the single most consequential
   open decision for Phase 2.
2. **Whether this task needs its own `processed_events` grant migration is undecided.** The task
   statement's own "Unit-test the dedupe" wording suggests no real DB access is required to satisfy
   this task's literal scope (a unit test can use a mocked/in-memory repository), but if Phase 2
   follows the established proactive-integration-test precedent, the grant becomes load-bearing.
   Not a blocker — Phase 2's own call, informed by whichever test strategy it picks.
3. **How "in the same transaction as the delivery-log append" is meant to be verified when
   `DeliveryLog` doesn't exist yet.** Not a blocker for *this* task's own scope (the requirement is
   about the dedupe mechanism's own transactional *compatibility*, provable by showing it declares no
   transaction-boundary-closing behavior of its own — e.g. no `@Transactional(propagation =
   REQUIRES_NEW)` — not by actually joining a real `DeliveryLog` write, which doesn't exist until a
   later task). Phase 2 should state this explicitly rather than leave it implicit.
