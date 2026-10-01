# notification · T14 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/notification` is a Spring Boot 3 / Java 21 Kafka-consuming, package-by-feature monolith
(`com.themistra.notification`), non-custodial, consume-only (L2) — it reacts to events from
`services/auth` (and, once built, `services/payment`) and never makes a synchronous cross-service
call. Persistence is Postgres (schema `notifications`), migrated via Flyway at build time only
(`spring.flyway.enabled=false` at runtime — migrations run exclusively via the Maven plugin,
mirroring both sibling services). The running application connects as a scoped `notification_app`
role, never the migration/table-owner role (T02's own established pattern — every new table needs an
explicit incremental `GRANT` migration before the app can touch it). Security is OAuth2
resource-server (T03): JWTs minted by `auth-service` are validated against its JWKS; `PublicEndpoints`
allows only actuator health/info/prometheus. Delivery is channel-agnostic behind one
`NotificationChannel` interface (L5): `EmailChannel` (T12, real Amazon SES v2) and `InAppChannel`
(T13, persists + SSE push) are the two real implementations; `DeliveryOrchestrator` (T11) is the
single orchestration point — resolve preferences → render template → dispatch per channel → append
one `delivery_log` row per attempt (L3, append-only, dispute-grade evidence).

## 2. Existing code this task touches

**Already exists, to be read/extended, not replaced:**
- `delivery/DeliveryOrchestrator.java` — the sole caller of every `NotificationChannel.send`. Today
  it treats every channel-level exception identically: catches, writes a `FAILED` row
  (`DeliveryLog`, `outcome="FAILED"`), and **stops** — there is no retry scheduling of any kind yet,
  and no distinction anywhere in this class between a transient failure (should retry) and a
  permanent one (should not). This task's own scope is exactly that missing distinction plus the
  scheduling it should trigger — I do not yet know (Phase 1/2's own job) whether this means
  `DeliveryOrchestrator` itself gains new logic, or whether a new seam is inserted between it and
  the channel call.
- `delivery/DeliveryLog.java` / `DeliveryLogRepository.java` (T11) — the append-only attempt log.
  Its own `outcome` column has a DB `CHECK` constraint (`V1__notifications_baseline.sql:58-59`)
  allowing exactly `SENT`, `FAILED`, `SUPPRESSED`, `DEAD_LETTERED` — `DEAD_LETTERED` is a real,
  already-reserved outcome value this task is the first to actually write. Its own Javadoc
  (`DeliveryLog.java:20-21`) explicitly says `attempt` is hardcoded to `1` today and "incrementing
  it for a real retry is task 14's own scope" — a direct, intentional pointer to this task.
- `delivery_retry` / `shedlock` tables — already created by the `V1` baseline migration
  (`V1__notifications_baseline.sql:79-96`), exactly as specified in `design.md` §4c. Neither has
  ever been granted to `notification_app` — both are currently the sole two entries in
  `NotificationBaselineMigrationIntegrationTest.UNGRANTED_TABLES`
  (`NotificationBaselineMigrationIntegrationTest.java:53`), which asserts `notification_app` is
  explicitly **denied** `SELECT` on both today. This task will need its own incremental grant
  migration (`V9`, following the established V4-V8 one-migration-per-table pattern) and will need to
  move both tables out of `UNGRANTED_TABLES` into dedicated grant-shape tests, mirroring exactly how
  T13 handled `inapp_notifications` at its own Phase 6.
- `common/config/RetryProperties.java` (T03) — already a validated `@ConfigurationProperties`
  record (`maxAttempts`, `initialBackoffSeconds`, `maxBackoffSeconds`), with its own compact
  constructor already enforcing `maxBackoffSeconds >= initialBackoffSeconds`. Already bound to real
  default values in `application.properties` (`themistra.notification.retry.max-attempts=5`,
  `initial-backoff-seconds=30`, `max-backoff-seconds=3600`), explicitly commented "confirm real
  values via Q6" — this task's own job per the task statement ("Confirm policy via Q6"). This class
  is otherwise completely unconsumed anywhere in the codebase today (verified by grep — no reference
  outside its own file and `application.properties`).
- `NotificationServiceApplication.java` — its own Javadoc explicitly states `@EnableScheduling`/
  `@EnableSchedulerLock` are "still absent — no scheduled job exists yet (task 14)" and instructs
  adding each annotation only in the task that introduces the thing it enables. This is this task's
  own first scheduled job in this service.
- `agents.md` (lines 62, 78, 86) already states the standing rule this task must satisfy:
  "multi-replica scheduled jobs are ShedLock-guarded" and "Retries are bounded and terminate in a
  dead-letter outcome, never an infinite loop."

**New, per `design.md` §6's own package map:**
- `delivery/DeliveryRetry.java` / `DeliveryRetryRepository.java` — maps onto the already-existing
  `delivery_retry` table (`id`, `source_event_key`, `account_uuid`, `channel`, `attempt`,
  `next_attempt_at`, `created_at` — no `outcome`/status column of its own; it is purely a scheduling
  queue, distinct from `delivery_log`'s own attempt-outcome record).
- `delivery/RetryScheduler.java` — a ShedLock-guarded `@Scheduled` job. No ShedLock Maven dependency
  exists in `pom.xml` today (verified by grep — absent) — this task is the one that adds it.

## 3. Established patterns to follow

- **Persistence:** JPA entities under `delivery/` (mirroring `DeliveryLog`'s own shape: a package-
  private or public repository depending on cross-package need — `DeliveryLogRepository` is
  package-private, per Kimi Phase 8 Finding #3's own rejected "matches siblings" justification,
  since every sibling repository in this module actually *is* package-private). Flyway migrations
  are additive-only, one incremental `GRANT` migration per newly-touched table
  (`V4`-`V8` precedent), never editing an already-applied migration.
- **Outbox/idempotency:** `ProcessedEvent`/`IdempotencyGuard` (T04) dedupes inbound Kafka events;
  not directly relevant to this task's own retry-of-an-already-dispatched-attempt concern, since a
  scheduled retry re-attempts a channel send, it does not re-consume a Kafka message.
- **Resource-server security (L8):** irrelevant to this task — `RetryScheduler` is an internal
  scheduled job, not an HTTP endpoint.
- **Error handling:** `DeliveryOrchestrator`'s own two-level `try/catch` (outer method-level, inner
  per-channel) that converts every failure into a best-effort `delivery_log` row rather than letting
  an exception propagate and roll back the caller's own transaction (T11's own frozen AC9) — this
  task's own new retry-scheduling logic will need the same "never throw out of this call path"
  discipline, since it is reached from the exact same `AuthEventConsumer`-owned transaction boundary
  today, and from a standalone `@Scheduled` method's own separate transaction for the retry-replay
  path.
- **Configuration:** validated `@ConfigurationProperties` records with a compact-constructor
  cross-field check where `@Min`/`@Max` alone can't express the constraint (`RetryProperties`'s own
  existing precedent, already built in T03 for this exact task).
- **Secret-safe logging (L4/R15):** `SecretSafeLogging.redact()` (T10) already wraps every
  `errorDetail` written to `delivery_log` (`DeliveryOrchestrator.java:220`) — any new error detail
  this task's own retry path logs or persists must go through the same redaction, not a new
  ad hoc path.

## 4. Testing conventions

- Unit tests: plain JUnit + Mockito for class-level logic with no real Spring context (e.g.
  `DeliveryOrchestratorTest`'s own shape).
- Integration tests: Testcontainers Postgres + real Spring context for anything touching a real
  transaction boundary or real schema grants (e.g. `DeliveryOrchestratorIntegrationTest`,
  `NotificationBaselineMigrationIntegrationTest`). A fixed `Clock` bean is overridden via
  `@TestConfiguration`/`@Primary` in every integration test that needs a deterministic `createdAt`/
  `next_attempt_at` value — directly relevant here, since this task's own backoff math
  (`next_attempt_at = now + backoff`) needs a fixed, assertable "now."
- No ArchUnit test exists yet in this service (that is task 16's own future scope) — L11 module-
  boundary compliance has been verified manually in every task so far (most recently T13's own real,
  caught-and-fixed violation) and will need the same manual discipline here.
- `NotificationBaselineMigrationIntegrationTest`'s own `UNGRANTED_TABLES`/per-table grant-shape test
  pattern (T02, extended at T13) is the exact template this task's own `V9` grant migration and its
  test coverage should follow.

## 5. Known gaps / unknowns

- **ShedLock is not yet a dependency anywhere in this codebase.** I do not know which ShedLock
  provider module (`shedlock-provider-jdbc-template` is the obvious fit, given this service already
  uses plain JDBC/JPA against Postgres and the `shedlock` table's own schema already matches
  ShedLock's own documented JDBC-provider table shape) is the right one until Phase 2/3 confirms the
  exact Maven coordinates against the actual Maven Central metadata — not assumed from memory.
- **How a scheduled retry re-invokes delivery is not yet designed.** `DeliveryOrchestrator.send`'s
  own `dispatchOneChannel` is `private` and tightly coupled to a `Map<String, String> eventData` it
  receives from the caller — I do not know yet whether `RetryScheduler` needs eventData to be
  persisted somewhere (today nothing durable stores the rendered message or the original event
  payload once `dispatch` returns) or whether it re-renders from `DeliveryRetry`'s own stored
  columns. The current `delivery_retry` table schema (`source_event_key`, `account_uuid`, `channel`,
  `attempt`, `next_attempt_at`) has **no column for the original event data/template name needed to
  actually retry a send** — this looks like a real gap between the already-fixed `V1` schema and
  what a working retry needs, and is exactly the kind of thing Phase 3's adversarial review exists to
  catch. I do not know yet whether the resolution is a schema addition (would need a new migration,
  since `V1` is already applied) or a different re-fetch strategy (e.g. re-deriving from
  `delivery_log`'s own stored `template_name`/`template_version` plus a fresh render).
- **What counts as "transient" vs. permanent is not yet defined anywhere.** Neither `design.md` nor
  `requirements.md` enumerates which exception types/conditions are retryable (e.g. is a
  `TemplateRenderer` failure — a broken template — ever transient? Almost certainly not. Is an SES
  throttling error transient? Almost certainly yes.) I do not know the answer yet — this is squarely
  Phase 1/2's own job to extract/propose, not something to guess here.
- **O4's own two options (a `dead_letter` table vs. a Kafka DLQ topic) were never finalized** —
  `design.md` §4b-O4 only says "propose... recommend one", and the already-reserved `DEAD_LETTERED`
  `delivery_log` outcome value (already in the `V1` CHECK constraint) strongly suggests the chosen
  answer is "record it in `delivery_log` itself," not a separate table or topic — but I do not know
  this for certain until Phase 1 extracts it explicitly.
- **Q6's own real max-attempts/backoff-schedule values** are still only placeholder defaults
  (`application.properties`'s own comment says so explicitly) — the task statement's own "Confirm
  policy via Q6" is this task's job, not already resolved elsewhere.
