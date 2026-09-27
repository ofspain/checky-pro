# notification · T04 · Phase 0 — Repository Understanding

## 1. Architecture summary

`notification-service` is a consume-only Kafka fan-out layer (L2). As of T03, the module has:
schema (`notifications`, 8 tables via T02's `V1-V3`), validated config (4
`@ConfigurationProperties` records), and JWT resource-server wiring (`ResourceServerConfig`,
`PublicEndpoints`). No package under `com.themistra.notification` other than `common`/`common.config`
exists yet — no `consumer`, `preference`, `template`, `delivery`, `channel`, or `inapp` package, no
`@Entity`, no `@Repository`, no Kafka listener, anywhere in this module.

T04 is the **first task in this entire codebase's history to implement a consumer-side idempotency
ledger** — `services/auth` and `services/crypto` are both event *producers* (via an outbox pattern),
never event *consumers*; `services/payment` has no source files at all yet. There is no existing
"dedupe a consumed event" pattern anywhere in the repository to mirror directly.

## 2. Existing code this task touches

**Already exists (read-only, not to be duplicated or reworked):**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql` (T02) —
  `notifications.processed_events` table already exists: `event_key VARCHAR(200) PRIMARY KEY`,
  `event_type VARCHAR(64) NOT NULL`, `processed_at TIMESTAMPTZ NOT NULL DEFAULT now()`. This task's
  own `ProcessedEvent` entity must map onto this exact, already-migrated, VERBATIM schema — no new
  migration is implied by the task statement (it says "Add `ProcessedEvent` + repository", not "add
  a table").
- `services/notification/src/main/resources/application.properties` (T03) — datasource, JPA
  (`ddl-auto=validate`, currently harmless since zero entities exist; this task's `ProcessedEvent`
  will be the first entity Hibernate actually validates against the real schema), Flyway disabled at
  runtime.
- `notification_app`'s DB grants (T02, `V2__notification_app_role_and_grants.sql`): **only
  `INSERT, SELECT` on `delivery_log`** — `processed_events` is not yet granted to `notification_app`
  at all. `NotificationBaselineMigrationIntegrationTest.notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope`
  (T02, extended T02 Phase 9/11) currently asserts `notification_app` has zero access — SELECT,
  INSERT, UPDATE, and DELETE all denied — to `processed_events`. If this task's own `ProcessedEvent`
  repository needs to `INSERT`/`SELECT` on `processed_events` at runtime, a new grant migration is
  needed (mirrors `crypto-service`'s own incremental-grant pattern, one grant migration per
  newly-consuming task) — **this is a real, load-bearing dependency Phase 1/2 must account for, not
  something to design here.**

**Not yet built, referenced by this task's own text but out of scope:**
- `delivery_log` / `DeliveryLog` entity — the task statement says the dedupe helper must record the
  event key "in the same transaction as the delivery-log append," but no `DeliveryLog` entity,
  repository, or any code that writes to `delivery_log` exists yet anywhere in `src/main`. This
  task's own scope (per `design.md` §6's package map, which places `ProcessedEvent` in `consumer/`
  and `DeliveryLog` in a separate `delivery/` module) appears to be the ledger and its dedupe
  mechanism only — the actual joint-transaction call site (a real consumer that also writes
  `delivery_log`) is a later task's own integration point, not something T04 can fully realize
  end-to-end today. Confirming this boundary precisely is Phase 1/2's own job, not this phase's.
- No `AuthEventConsumer`/`PaymentEventConsumer`, no Kafka listener, no `@KafkaListener` anywhere —
  task 6 ("Auth event consumer") and later.

## 3. Established patterns to follow

**JPA entity/repository shape** — `services/crypto`'s own `OutboxEvent`/`OutboxEventRepository`
(`events/` package, T04 of that service) is the closest working precedent for *entity/repository
conventions* in this codebase, though it solves a different problem (producer-side outbox, not
consumer-side dedupe): protected no-arg constructor (JPA-only), a static factory method (`create(...)`)
rather than public setters, package-private repository interface (`interface OutboxEventRepository
extends JpaRepository<OutboxEvent, Long>`), `@Column(name = ..., nullable = ..., length = ...)`
mapping every column explicitly, and a defensive `@PrePersist` fallback only for a timestamp that's
normally set explicitly via the factory method from an injected `Clock`. Unlike `OutboxEvent`
(`Long id` with `GenerationType.IDENTITY`), `processed_events.event_key VARCHAR(200) PRIMARY KEY` is
a **client-assigned natural key, not a generated surrogate** — `ProcessedEvent`'s own `@Id` will need
`@Id` with no `@GeneratedValue` at all, mapped directly to the event key string. No existing entity
in this codebase currently uses a client-assigned `@Id`; `services/auth`'s own outbox-equivalent
entity is cited (T01 Javadoc) as using a client-assigned UUID, which may be the closer shape to
check in Phase 1/2 — not read in this phase.

**Injectable `Clock`** — `services/crypto`'s own `common/ClockConfig.java` (`@Bean public Clock
clock() { return Clock.systemUTC(); }`) is the exact pattern `agents.md`'s own "no `java.util.Date`;
use `java.time` with an injectable `Clock`" rule expects; `processed_at`/similar timestamp fields
should be set from an injected `Clock`, not `Instant.now()` inline.

**Least-privilege DB grants** — `crypto-service`'s own incremental-grant pattern (one grant
migration per newly-consuming task, e.g. its own `V3__crypto_app_outbox_grant.sql`) is the direct
precedent for whatever grant `processed_events` needs for this task, mirroring T02's own explicit
disposition that each of the 6 ungranted tables gets its own grant migration "in the task that first
needs runtime access to them."

**Testing split** — `agents.md`'s own testing convention: "Idempotency and preference logic are
unit-tested with a fixed `Clock`" (from `package.md` §8's own intro) — matches this task's own
literal instruction ("Unit-test the dedupe"), not a Testcontainers-Postgres integration test. The
named tests this task owns (`shouldDedupeDuplicateEventDeliveryByEventKey`,
`shouldNotDoubleSendWhenSameEventRedelivered`) are listed under `package.md` §8 as unit-level,
consistent with plain JUnit + a mocked/in-memory repository rather than a real database — the real,
concurrent-safe, DB-enforced proof of no-double-send is a later integration task's own job (the
`processed_events.event_key PRIMARY KEY` constraint is what makes it DB-enforced at all).

**No tests in Phase 6** — mirrors T02/T03's own established discipline: Phase 6 writes production
code only; Phase 10 writes tests. Given this task's own literal instruction to "unit-test the dedupe,"
Phase 10's role here is more central than it was for T03 (a pure-wiring task) — worth keeping in mind
at Phase 2/5, not deciding now.

## 4. Testing conventions

Unit tests: plain JUnit, fixed `Clock` (`Clock.fixed(...)`), per `agents.md`. No ArchUnit yet (task
16's own scope — no feature module exists yet to police cross-module entity imports). Contract tests
validate consumed payloads against `contracts/events/*` — not this task's own scope (no consumer
exists yet; `ProcessedEvent`'s own key is a string the caller supplies, not something this task
derives from a real Kafka payload).

## 5. Known gaps / unknowns

- **I do not know** the exact signature/shape the task statement's own "dedupe helper" should take —
  a `@Service` class with a method like `boolean markProcessedIfNew(String eventKey, String
  eventType)`, or something else. `design.md` §6 only names `ProcessedEvent.java` /
  `ProcessedEventRepository.java` explicitly under `consumer/` — it does not name a third
  "dedupe helper" class. Whether the helper is a new, separate class or logic that belongs directly
  on the repository/entity is Phase 1/2's own decision, not resolved here.
- **I do not know** how "in the same transaction as the delivery-log append" is meant to be verified
  by *this* task specifically, given `DeliveryLog` doesn't exist yet. Whether T04's own required
  unit test can meaningfully prove a transactional-join guarantee without a real `DeliveryLog`
  counterpart to join with, or whether that guarantee is only provable once task 6 (or later) wires
  the two together, is a genuine open question for Phase 1.
- **I do not know** whether `processed_events` needs a grant migration in *this* task or can wait —
  the task statement says "Unit-test the dedupe" (implying no real DB access is required to satisfy
  this task's own literal scope), but if Phase 2 decides a real Testcontainers-backed proof is also
  warranted (mirroring T02/T03's own proactive-test-writing precedent when schema/DB behavior is
  involved), the grant becomes load-bearing. Flagged, not resolved, here.
