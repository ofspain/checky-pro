# crypto · T04 · Phase 0 — Repository Understanding

## 1. Architecture summary of this service (as it exists today)

`services/crypto` (Java 21 / Spring Boot 3.5.4) has three tasks shipped so far:

- **T01** — Maven skeleton, bare `CryptoServiceApplication` (`@SpringBootApplication
  @ConfigurationPropertiesScan`, no `@EnableScheduling`/`@EnableSchedulerLock` yet).
- **T02** — `chain` Postgres schema (Flyway, `V1__chain_baseline.sql` + `V2__crypto_app_role_and_grants.sql`):
  10 tables including `outbox`, least-privilege `crypto_app` runtime role (INSERT+SELECT-only on
  `observations`/`attestations`/`quorum_decisions` — **`outbox` is not in that granted-table list**,
  see §2/§5 below).
- **T03** — config + resource-server foundation: 5 `@ConfigurationProperties` classes under
  `common/config/`, `PublicEndpoints`, `ResourceServerConfig` (JWT validation, `internal.crypto:write`
  scope on `/internal/v1/**`, RFC 9457 401/403). No feature (non-`common`) package exists yet.

**No `events` package and no Kafka config exist anywhere in `services/crypto` yet.** This task is the
first to touch messaging.

## 2. Existing code this task touches — what's already there vs. new

**Already exists (context, not to be modified unless the task requires it):**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — already defines the
  `outbox` table this task will write to. Its exact shape (verbatim, immutable — Flyway migrations
  are never edited once merged):
  ```sql
  CREATE TABLE outbox (
      id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      aggregate_type VARCHAR(64) NOT NULL,
      aggregate_id VARCHAR(128) NOT NULL,
      event_type VARCHAR(64) NOT NULL,
      idempotency_key VARCHAR(200) NOT NULL,       -- chain:txhash:eventtype (L5)
      payload JSONB NOT NULL,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      published_at TIMESTAMPTZ,
      CONSTRAINT uq_outbox_idempotency UNIQUE (idempotency_key)
  );
  CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;
  ```
  Notably: `id` is `BIGINT GENERATED ALWAYS AS IDENTITY` (not a client-assigned UUID), there is **no**
  `schema_version` column and **no** `headers` column, and there **is** a `idempotency_key` column
  with a `UNIQUE` constraint — see §3 for why this matters against the auth-service precedent.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` — grants
  `crypto_app` INSERT+SELECT on exactly `observations`/`attestations`/`quorum_decisions`. **`outbox`
  is not among the granted tables.** Whatever role the running application connects as
  (`spring.datasource.username=crypto_app` per `application.properties`) currently has **no
  documented grant to INSERT into `outbox` at all** — flagged as a known gap in §5, not assumed away.
- `services/crypto/pom.xml` — already declares `spring-kafka` (main scope) and
  `org.testcontainers:kafka` (test scope) as of T01. No Kafka-specific config exists in
  `application.properties` yet (no `spring.kafka.bootstrap-servers` or equivalent).
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — currently has
  no `@EnableScheduling`. If this task's relay-equivalent needs `@Scheduled` (see §3), this is the
  file that gains the annotation, per the same "add it the task that needs it" discipline T01/T03
  already established for this class's own doc comment.

**New in this task (per design.md §6 file map, scoped to T04's statement):**
- `events/OutboxPublisher.java` — explicitly named in the task statement.
- `events/EventTopics.java` — explicitly named in the task statement, design §4c gives the exact
  aggregate-type → topic `Map` literal to use (see §3).
- `events/event/{TxSeen,TxConfirmed,TxFinalized,TxReorged,ProviderDegraded}Payload.java` — listed in
  design §6's file map under `events/event/`, but **not** named in T04's own task-statement text
  (task 17 is the one that actually emits these events); flagged as a scoping question for Phase 1,
  not assumed in.
- **Not listed anywhere in design.md §6's file map, but functionally required**: an `OutboxEvent`
  JPA entity, its repository, and something that actually reads unpublished rows and sends them to
  Kafka (auth's equivalent is `OutboxRelay`). Task 4 is the *only* outbox-related task anywhere in
  `tasks.md`'s 29 tasks — flagged as a known gap in §5, not decided here.

## 3. Established patterns to follow

**`services/auth` already has a complete, working, real outbox implementation** at
`services/auth/src/main/java/com/themistra/auth/events/` — `OutboxPublisher`, `OutboxEvent`,
`OutboxEventRepository`, `OutboxRelay`, `EventTopics`. `libs/java/outbox` (which design.md §4c's
comment "mirrors libs/java/outbox" points at) is an empty `.gitkeep` placeholder — the shared library
hasn't been extracted yet, so **auth's own `events/` package is the real pattern to mirror**, the same
way T02 mirrored auth's schema/role structure. Key shapes:

- **`OutboxPublisher`** (`@Component`): one method, `publish(String aggregateType, String aggregateId,
  String eventType, int schemaVersion, Object payload)` — serializes `payload` via injected
  `ObjectMapper`, wraps failure in `IllegalStateException`, saves an `OutboxEvent` via the repository.
  Runs in whatever transaction is already open (no `@Transactional` of its own) — "the only sanctioned
  way to emit a domain event... appends a row in the caller's own transaction... never a dual write."
  **Crypto's version cannot be a literal copy**: the schema has no `schema_version` column, and it
  *does* have a required `idempotency_key` (L5, `chain:txhash:eventtype`) that auth's method signature
  has no parameter for at all — this is the one real shape divergence Phase 1/2 must design around, not
  paper over by mirroring auth verbatim.
- **`EventTopics`** (`final` utility class, private constructor): a `static final Map<String, String>
  TOPIC_BY_AGGREGATE_TYPE`, one lookup method that throws `IllegalStateException` on an unmapped type
  ("must fail loudly, not silently drop events or guess a topic name"). Design.md §4c already gives
  the exact literal crypto needs:
  ```java
  private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
          "tx-seen", "chain.tx.seen",
          "tx-confirmed", "chain.tx.confirmed",
          "tx-finalized", "chain.tx.finalized",
          "tx-reorged", "chain.tx.reorged",
          "provider", "chain.provider.degraded"
  );
  ```
  This is a **VERBATIM artifact** per design.md §4c's own heading — copy exactly, do not paraphrase.
- **`OutboxEvent`** (JPA `@Entity`, package-private outside `events/`): auth's uses `@Id UUID id`
  assigned in a static factory (`OutboxEvent.create(...)`), `@JdbcTypeCode(SqlTypes.JSON)` on the
  `payload` column, `@PrePersist` sets `createdAt`, plain getters, `markPublished(Instant)` /
  `isPublished()`. Crypto's entity must instead use `@GeneratedValue(strategy = IDENTITY)` for the
  `BIGINT` PK, has no `schemaVersion`/`headers` fields to map, and must carry the `idempotencyKey`
  field the DB column requires.
- **`OutboxRelay`** (`@Component`, `@Scheduled(fixedDelayString = "...")`): polls
  `findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable)` in fixed-size batches, looks up the topic via
  `EventTopics`, sends via `KafkaTemplate<String, String>` using `aggregateId` as the Kafka message
  key/partition key, marks published **after** a successful send (send-then-mark, at-least-once by
  design — "two replicas may occasionally race... the platform-wide rule that every Kafka consumer is
  idempotent... makes that a harmless duplicate, not a correctness bug, so no ShedLock is introduced").
  An unroutable aggregate type is logged and skipped (left unpublished), never thrown out of the
  scheduled method. This at-least-once + idempotent-consumer reasoning matches agents.md's own
  "Services... dedupe" instruction and L5's idempotency-key purpose directly.
- **Config precedent**: auth's `application.properties` has `spring.kafka.bootstrap-servers=
  ${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}` and `themistra.auth.outbox.relay-interval-ms=
  ${OUTBOX_RELAY_INTERVAL_MS:2000}` (a plain `@Value`-injected property on the `@Scheduled` annotation
  itself, not a `@ConfigurationProperties` class — notably different from T03's own pattern of always
  using validated `@ConfigurationProperties`, worth a Phase 1 note on which convention this task should
  follow).
- **Persistence/config boundary already established by T02/T03**: the runtime role is `crypto_app`
  (never the Flyway/migration owner `checky`), Flyway itself is disabled at runtime
  (`spring.flyway.enabled=false`), and `application.properties` is flat, never YAML (agents.md).

## 4. Testing conventions

Auth's own event-stack tests are the direct precedent, and this task's own instruction ("Unit-test
routing (R26)") matches this style exactly:
- `EventTopicsTest` — plain JUnit, no Spring context, no mocks: asserts each mapped aggregate type
  routes to its topic, plus one negative test asserting an unmapped type throws
  `IllegalStateException` with the offending type name in the message.
- `OutboxPublisherTest` — `@ExtendWith(MockitoExtension.class)`, mocks `OutboxEventRepository`,
  verifies the saved `OutboxEvent`'s fields via `ArgumentCaptor`, no real Spring context, no database.
- `OutboxRelayTest` — same Mockito style, mocks `OutboxEventRepository` + `KafkaTemplate`, uses a fixed
  `Clock` (agents.md's own stated convention — "Unit (plain JUnit, fixed `Clock`...)"), covers
  successful send, failed send (event stays unpublished, no save), unroutable aggregate type (skipped,
  no send, no save), and empty batch (no-op). No Testcontainers, no embedded Kafka broker.
- No integration/Testcontainers-Kafka test exists anywhere in `services/auth` for this stack — the
  unit-level Mockito approach above is the full extent of the established testing precedent for
  outbox/EventTopics work specifically. `services/crypto/pom.xml` already has `org.testcontainers:kafka`
  available (T01) for when a heavier test is warranted, but nothing in this task's own scope
  ("Unit-test routing (R26)") points at needing it.

## 5. Known gaps / unknowns

- **I do not know** whether T04 is meant to also build the relay (the equivalent of auth's
  `OutboxRelay`). The task statement names only `OutboxPublisher` and `EventTopics`; design.md §6's
  file map lists neither `OutboxRelay` nor any entity/repository under `events/`; and no other task in
  `tasks.md`'s 29 tasks mentions the outbox again. Without something reading unpublished rows and
  calling Kafka, the outbox this task writes to is inert — Phase 1 needs to resolve whether the relay
  is implicitly in scope here (most likely, since nothing else ever builds it) or is a genuine gap to
  raise as an Open Question.
- **I do not know** whether `crypto_app` (the runtime role T02 created) is actually granted
  `INSERT`/`SELECT` on `outbox`. `V2__crypto_app_role_and_grants.sql`'s documented grant list (per T02)
  covers only `observations`/`attestations`/`quorum_decisions`. If `outbox` truly has no grant, calling
  `OutboxPublisher.publish(...)` against the real database would fail with a permission error at
  runtime — this may mean a new Flyway migration (`V3__...`) granting `crypto_app` write access to
  `outbox` is itself in scope for this task, since nothing else in the task list touches grants again
  either. Flagging rather than assuming; Phase 1 should confirm by reading the actual committed
  `V2__crypto_app_role_and_grants.sql` content, not just this summary.
- **I do not know** the exact `idempotencyKey` construction contract for `OutboxPublisher.publish(...)`
  — whether the caller passes the already-formatted `chain:txhash:eventtype` string, or whether
  `OutboxPublisher` builds it itself from separate `chain`/`txHash`/`eventType` parameters. L5 only
  says every emitted event *carries* this key; design.md doesn't show an `OutboxPublisher` method
  signature (unlike `EventTopics`, which *is* given verbatim). This is Phase 1/2 design work, not
  answerable from the spec text alone.
- **I do not know** whether this task should follow T03's `@ConfigurationProperties` convention for
  any new config (e.g. relay interval, Kafka bootstrap servers) or auth's simpler `@Value` +
  `${ENV_VAR:default}` pattern on the `@Scheduled` annotation itself — both exist in this codebase now
  as precedent, for different reasons (T03 has non-trivial multi-field/cross-validated config; auth's
  relay interval is a single scalar).
- `contracts/events/chain/*.schema.json` do not exist yet (T23) — R28 (contract conformance) is not
  cited in this task's scoped requirement IDs (only R26 is), consistent with T04 not needing them.

Do not design and do not extract requirements yet — that is Phase 1. This artifact stops here.
