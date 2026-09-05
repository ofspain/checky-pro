# crypto · T04 · Phase 1 — Specification Extraction

## Business Rules

- **R26.** WHEN a `chain.tx.seen|confirmed|finalized|reorged` or `chain.provider.degraded` event is
  emitted, THEN `EventTopics` SHALL route it to the `chain.tx.seen`, `chain.tx.confirmed`,
  `chain.tx.finalized`, `chain.tx.reorged`, and `chain.provider.degraded` topics respectively, via
  the outbox.

No other numbered requirement is in this task's scoped set. R12 (every `chain.tx.*` event carries the
`chain:txhash:eventtype` idempotency key) is closely related — it's the requirement-level restatement
of L5 — but T04 itself doesn't emit real `chain.tx.*` events (that's task 17); T04 only builds the
mechanism (`OutboxPublisher`) that a future caller will use to carry that key. R12 isn't cited in this
task's scoped IDs and isn't independently testable by this task's own deliverable, so it's referenced
here for context, not listed as a rule this task must itself satisfy. R28 (contract conformance) is
out of scope — `contracts/events/chain/*` don't exist yet (task 23).

## Locked Decisions

- **L5.** Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype`; the
  same tx will be observed multiple times and consumers dedupe on this key. This is the task
  statement's own explicit citation — `OutboxPublisher` must make this key mechanically unavoidable to
  omit, not just documented as a convention callers are trusted to follow.

Also load-bearing (agents.md "Events & messaging" section, not a numbered `L` but authoritative and
directly on point for this task):
- "Every emitted fact is published through the **outbox** in the same transaction as the DB write; a
  relay publishes to Kafka. **No direct producer call from domain code.**"
- "Topic naming `<domain>.<entity>.<event>`." (matches design §4c's literal topic strings exactly.)
- "Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype`; consumers
  dedupe." (restates L5.)
- "Services depend only on `libs/` and `contracts/` — **never on another service's source**." This
  means `services/auth`'s `events/` package (the real, working precedent identified in Phase 0) may be
  used as a *structural pattern to mirror*, never as an actual code dependency or import.

## Files involved

**Existing — read/extend:**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` — defines the `outbox`
  table's exact (immutable, already-shipped) shape: `BIGINT IDENTITY` PK, `idempotency_key
  VARCHAR(200) NOT NULL UNIQUE`, no `schema_version`/`headers` columns. Read-only reference — never
  edit a merged migration.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` — **confirmed
  by direct read in Phase 0: grants `crypto_app` INSERT+SELECT on exactly `observations`/
  `attestations`/`quorum_decisions`. `outbox` has no grant at all.** Since no other task in
  `tasks.md`'s 29 tasks touches grants again, a new `V3__...sql` migration granting `crypto_app`
  INSERT (and likely SELECT, matching the existing three tables' pattern) on `outbox` is very likely
  in this task's own scope — Flyway migrations are immutable once merged, so this can't be fixed by
  editing V2.
- `services/crypto/src/main/resources/application.properties` — will need Kafka bootstrap-server
  config (currently absent) if the relay-equivalent (see Open Questions) is built in this task.
- `services/crypto/pom.xml` — `spring-kafka` (main) and `org.testcontainers:kafka` (test) are already
  present (T01); no new dependency expected.
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — would need
  `@EnableScheduling` added if a `@Scheduled` relay is built here, per the class's own "add the
  annotation in the task that needs it" discipline established in T01/T03.

**New — expected by the task statement + design.md §6:**
- `events/OutboxPublisher.java` — named explicitly in the task statement.
- `events/EventTopics.java` — named explicitly; design §4c gives the aggregate-type → topic `Map`
  literal **verbatim** (copy exactly, do not paraphrase):
  ```java
  private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
          "tx-seen", "chain.tx.seen",
          "tx-confirmed", "chain.tx.confirmed",
          "tx-finalized", "chain.tx.finalized",
          "tx-reorged", "chain.tx.reorged",
          "provider", "chain.provider.degraded"
  );
  ```
- **Not named in the task statement or design §6's file map, but functionally required for the
  outbox to do anything** (Phase 0 finding, carried forward — see Open Questions): an `OutboxEvent`
  JPA entity, its Spring Data repository, and a relay component that reads unpublished rows and sends
  them to Kafka (auth's `OutboxRelay` is the structural precedent).
- `events/event/{TxSeen,TxConfirmed,TxFinalized,TxReorged,ProviderDegraded}Payload.java` — listed in
  design §6's file map under `events/event/`, but these are the actual event payload shapes task 17
  will emit; not named in T04's own task-statement text. Treated as **out of scope for T04** unless
  Phase 2 finds a concrete reason `OutboxPublisher`/its test needs a real payload type rather than a
  generic test fixture (auth's own `OutboxPublisherTest` uses a throwaway local `record SamplePayload`
  for exactly this reason — likely precedent for T04 too).

## Dependencies

- **Classes:** Jackson `ObjectMapper` (already on the classpath via `spring-boot-starter-web`), Spring
  Data JPA repository infrastructure (already in use since T02's schema), `KafkaTemplate<String,
  String>` (spring-kafka, already a `pom.xml` dependency) if the relay is in scope, `java.time.Clock`
  (agents.md's own fixed-`Clock` testing convention, needed by any relay that timestamps
  `publishedAt`).
- **Entities/repositories:** none exist yet in `crypto`; `OutboxEvent`/`OutboxEventRepository` are new
  (see Files involved).
- **Config keys:** none exist yet for Kafka in `application.properties`. If needed: a bootstrap-servers
  property (mirrors auth's `spring.kafka.bootstrap-servers`) and, if a relay is built, a poll-interval
  property (auth uses a plain `@Value("${themistra.auth.outbox.relay-interval-ms:2000}")` on the
  `@Scheduled` annotation itself, not a `@ConfigurationProperties` class — a deliberate style choice
  for Phase 2 to make, not assumed here, given T03 established the `@ConfigurationProperties` pattern
  for this service's other config).
- **Contracts:** none exist yet (`contracts/events/chain/*` is task 23) and none are consumed by this
  task — `EventTopics`' keys are plain aggregate-type strings the future emitting code (task 17) will
  supply, not schema-validated payloads.
- **No dependency on `services/auth`'s actual classes** — per agents.md's "never on another service's
  source" rule; auth's `events/` package informs shape only.

## Acceptance Criteria

Mapped to R26 and the task statement's own clauses:

- **AC1–AC5 (R26).** `EventTopics.forAggregateType(...)` returns the correct topic for each of the 5
  mapped aggregate types (`tx-seen` → `chain.tx.seen`, `tx-confirmed` → `chain.tx.confirmed`,
  `tx-finalized` → `chain.tx.finalized`, `tx-reorged` → `chain.tx.reorged`, `provider` →
  `chain.provider.degraded`).
- **AC6 (R26, "fail loudly" pattern from agents.md/auth precedent).** An unmapped aggregate type
  throws rather than silently dropping the event or guessing a topic name.
- **AC7 (L5, task statement).** A published outbox row (via `OutboxPublisher`) carries a deterministic
  idempotency key; the exact construction mechanism (caller-supplied vs. built internally from parts)
  is a Phase 2 design decision, not fixed by the spec text.
- **AC8 (agents.md Events rule).** `OutboxPublisher` only ever persists a row via its repository — it
  never calls a Kafka producer directly; publishing to Kafka happens exclusively through whatever
  reads the outbox later (in-transaction write, out-of-transaction relay).
- **AC9 (Phase 0 finding, if the missing grant is confirmed still absent at implementation time).**
  `crypto_app` can `INSERT` into `outbox` in the real database — currently false as shipped; this task
  likely needs to fix it via a new migration.

## Tests required

From `package.md` §8, scoped to this task:
- **`shouldRouteEachChainEventToItsTopic` → R26.**

Boundary tests implied by the task statement, R26, L5, and the established auth precedent (not
separately named in §8 but necessary to cover AC6–AC9):
- Unmapped-aggregate-type test: `EventTopics.forAggregateType("unmapped")` throws (mirrors auth's
  `unmappedAggregateTypeFailsLoudRatherThanGuessing`) — covers AC6.
- `OutboxPublisher` persistence test (Mockito-mocked repository, no real database): asserts the saved
  row carries the correct `aggregateType`/`aggregateId`/`eventType`/serialized `payload` **and the
  deterministic idempotency key** — covers AC7/AC8, extends auth's `OutboxPublisherTest` pattern with
  the one field auth's own version doesn't have.
- If a relay-equivalent is in scope (Open Question 1): tests mirroring auth's `OutboxRelayTest`
  (successful send marks published, failed send leaves unpublished for retry, unroutable aggregate
  type is skipped without throwing, empty batch is a no-op) — all Mockito-based, no Testcontainers,
  matching agents.md's stated unit-test-first convention and the fixed-`Clock` rule.
- If the missing `crypto_app` grant is fixed by a new migration (Open Question 2, now more "confirmed
  gap" than open question): a Testcontainers-based test proving `crypto_app` can actually `INSERT`
  into `outbox` over a real TCP connection, mirroring `ChainBaselineMigrationIntegrationTest`'s own
  established technique from T02 (a `docker exec` shell connection would not exercise real password
  auth — T02's own hard-won lesson).

## Open Questions

**Not genuine external blockers** — package.md §11's Q1–Q8 don't touch outbox/Kafka/messaging at all,
so there's no spec-author question actually gating this task. The following are scoping decisions
this pipeline's own precedent (e.g. T03 Phase 1's handling of its own named-test-target ambiguity)
resolves at Phase 2 by reasoned engineering judgment, not by waiting on external input — flagged here
so Phase 2 makes the call explicitly rather than by default:

1. **Is the relay (auth's `OutboxRelay` equivalent) in scope for T04?** Task 4 is the only
   outbox-related task in the entire 29-task list; without a relay, the outbox this task writes to
   never actually delivers anything to Kafka. Phase 2 needs to decide whether to build it now
   (most likely, since nothing else ever will) or explicitly defer it with a documented reason.
2. **The exact `OutboxPublisher.publish(...)` method signature and how the idempotency key is
   supplied** — design.md gives `EventTopics`' map verbatim but no equivalent signature for
   `OutboxPublisher`. Any reasonable, testable choice satisfies L5 as long as the key is mechanically
   required, not optional.
3. **Config style for any new Kafka/relay settings** — plain `@Value` + env-var default (auth's
   pattern) vs. a new `@ConfigurationProperties` class (T03's pattern for this service specifically).
