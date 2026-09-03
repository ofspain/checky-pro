# crypto · T04 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/crypto/src/main/resources/db/migration/V3__crypto_app_outbox_grant.sql`
2. `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java`
3. `services/crypto/src/main/java/com/themistra/crypto/events/EventTopics.java`
4. `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java`
5. `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEventRepository.java`
6. `services/crypto/src/main/java/com/themistra/crypto/events/KafkaProducerConfig.java`
7. `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java`
8. `services/crypto/src/main/java/com/themistra/crypto/events/OutboxRelay.java`
9. `services/crypto/src/test/java/com/themistra/crypto/events/EventTopicsTest.java`
10. `services/crypto/src/test/java/com/themistra/crypto/events/OutboxPublisherTest.java`
11. `services/crypto/src/test/java/com/themistra/crypto/events/OutboxRelayTest.java`
12. `services/crypto/src/test/java/com/themistra/crypto/events/OutboxTransactionIntegrationTest.java`
13. `services/crypto/src/test/java/com/themistra/crypto/OutboxGrantMigrationIntegrationTest.java`
    (top-level package, mirrors `ChainBaselineMigrationIntegrationTest`'s location — a schema/grant
    test, not an `events`-package unit test)

## Files to modify

1. `services/crypto/src/main/resources/application.properties` — add
   `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}` and
   `themistra.crypto.outbox.relay-interval-ms` (default `2000`, matching auth).
2. `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
   `@EnableScheduling`.

No files outside this list. `V1`/`V2` migrations, T01/T02/T03's other files, and everything under
`spec/` are untouched, per frozen brief.

## Public methods (signatures)

**`ClockConfig`**
```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock(); // Clock.systemUTC()
}
```

**`EventTopics`** (VERBATIM per design §4c — copy exactly)
```java
public final class EventTopics {
    // private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
    //     "tx-seen", "chain.tx.seen", "tx-confirmed", "chain.tx.confirmed",
    //     "tx-finalized", "chain.tx.finalized", "tx-reorged", "chain.tx.reorged",
    //     "provider", "chain.provider.degraded");
    public static String forAggregateType(String aggregateType); // throws IllegalStateException if unmapped
}
```

**`OutboxEvent`** (`@Entity @Table(name = "outbox")`)
```java
public class OutboxEvent {
    public static OutboxEvent create(String aggregateType, String aggregateId, String eventType,
                                      String idempotencyKey, String payloadJson);
    public void markPublished(Instant publishedAt);
    public boolean isPublished();
    public Long getId();
    public String getAggregateType();
    public String getAggregateId();
    public String getEventType();
    public String getIdempotencyKey();
    public String getPayload();
    public Instant getCreatedAt();
    public Instant getPublishedAt();
}
```
`id` is `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` typed `Long` (amendment #1) — **not**
assigned in `create()`, unlike auth's client-assigned `UUID`; the DB's `BIGINT GENERATED ALWAYS AS
IDENTITY` assigns it on insert. `idempotencyKey` maps to the `idempotency_key` column (`NOT NULL`,
mapped `unique = true` to mirror the DB's own `UNIQUE` constraint declaratively). No `schemaVersion`/
`headers` fields (amendment #7 — schema versioning lives in the payload only).

**`OutboxEventRepository`** (package-private interface, matches auth's visibility)
```java
interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
}
```

**`KafkaProducerConfig`** (mirrors auth's `KafkaProducerConfig` exactly)
```java
@Configuration
public class KafkaProducerConfig {
    @Bean
    public ProducerFactory<String, String> outboxProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers);
        // BOOTSTRAP_SERVERS_CONFIG, StringSerializer x2, ACKS_CONFIG="all", ENABLE_IDEMPOTENCE_CONFIG=true
    @Bean
    public KafkaTemplate<String, String> outboxKafkaTemplate(
            ProducerFactory<String, String> outboxProducerFactory);
}
```

**`OutboxPublisher`** (`@Component`)
```java
public class OutboxPublisher {
    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper);
    public void publish(String aggregateType, String aggregateId, String eventType,
                         String idempotencyKey, Object payload);
        // Objects.requireNonNull on all 5 params (amendment #12), then serialize + repository.save(...)
        // Javadoc documents: (a) idempotencyKey format chain:txhash:eventtype (L5), (b) callers of
        // chain.tx.* events MUST pass watchId as aggregateId (amendment #6), (c) a duplicate
        // idempotencyKey propagates as an unchecked DataIntegrityViolationException from the DB,
        // uncaught here (amendment #11).
}
```

**`OutboxRelay`** (`@Component`)
```java
public class OutboxRelay {
    private static final int BATCH_SIZE = 100; // amendment #10
    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                        Clock clock);
    @Scheduled(fixedDelayString = "${themistra.crypto.outbox.relay-interval-ms:2000}")
    public void relay();
    // private void relayOne(OutboxEvent event) — EventTopics lookup (catch+log+skip if unroutable),
    // kafkaTemplate.send(topic, aggregateId, payload).get(), markPublished(clock.instant()) + save on
    // success, log+leave-unpublished on failure. No ordering guarantee beyond attempted-send sequence
    // (amendment #8) — continues to the next row in the batch after a failure, does not halt.
}
```

## Private methods

- `OutboxRelay.relayOne(OutboxEvent event)` — the only private method in this task's main code,
  exactly mirroring auth's own decomposition (`relay()` fetches the batch, `relayOne(...)` handles one
  row's topic lookup + send + mark).

## Entities used

- `OutboxEvent` (new, this task).

## Repositories used

- `OutboxEventRepository` (new, this task).

## Services used

- None beyond the `@Component`/`@Configuration` classes this task itself creates — no dependency on
  any other module's service (none exist yet outside `common/`).

## Unit / integration tests required

Traced to the frozen brief's "Required Tests":

- **`EventTopicsTest`** — 5 mapping assertions (one per aggregate type → topic pair) +
  `unmappedAggregateTypeFailsLoudRatherThanGuessing` (throws `IllegalStateException` naming the type) —
  covers AC1–AC6, is the named test `shouldRouteEachChainEventToItsTopic`.
- **`OutboxPublisherTest`** (Mockito, mocked `OutboxEventRepository`) — happy-path save asserts all
  fields including `idempotencyKey`; 5 tests asserting `NullPointerException` for each required
  parameter (`aggregateType`, `aggregateId`, `eventType`, `idempotencyKey`, `payload`) — covers
  AC7/AC8, amendment #12.
- **`OutboxRelayTest`** (Mockito, mocked repository + `KafkaTemplate`, fixed `Clock`) — successful
  send (marks published + saves), failed send (stays unpublished, no save), unroutable aggregate type
  (skipped, no send, no save), empty batch (no-op), and a page-size assertion (`Pageable` captor,
  `getPageSize() == 100`) — covers AC10, amendment #10.
- **`OutboxTransactionIntegrationTest`** (amendment #5, AC11) — a real, Testcontainers-Postgres-backed
  Spring context; a test-only `@Service` method wraps `OutboxPublisher.publish(...)` in
  `@Transactional`, then throws, forcing rollback; asserts (via the repository, outside that
  transaction) that no row was persisted. Needs Docker; see Known Limitations if unavailable at
  implementation time.
- **`OutboxGrantMigrationIntegrationTest`** (AC9, AC12) — mirrors
  `ChainBaselineMigrationIntegrationTest`'s technique exactly: real Testcontainers Postgres, runs
  `V1`+`V2`+`V3` via the Flyway Java API, connects as `crypto_app` over real TCP (not `docker exec`),
  asserts `INSERT` and `UPDATE` succeed on `outbox`, `DELETE` still fails, and the generated `id` binds
  as a `Long`. Needs Docker.

## Execution order

Front-loads config/schema (this task's migration + Kafka bootstrap property), then the independent
plumbing beans, then the classes that depend on them, then tests:

1. `application.properties` — add `spring.kafka.bootstrap-servers` and the relay-interval property
   first, so every class below has config to bind against.
2. `V3__crypto_app_outbox_grant.sql` — schema grant, independent of any Java class.
3. `ClockConfig` — no dependencies.
4. `EventTopics` — no dependencies.
5. `OutboxEvent` — no code dependencies (maps to the already-shipped `V1` `outbox` table).
6. `OutboxEventRepository` — depends on step 5.
7. `KafkaProducerConfig` — depends on step 1's property.
8. `OutboxPublisher` — depends on step 6.
9. `OutboxRelay` — depends on steps 4, 6, 7, 3 (`EventTopics`, `OutboxEventRepository`,
   `KafkaTemplate`, `Clock`).
10. `CryptoServiceApplication` — add `@EnableScheduling`, needed for step 9's `@Scheduled` method to
    actually run; placed after `OutboxRelay` exists but could compile in any order.
11. `EventTopicsTest` — depends on step 4 only.
12. `OutboxPublisherTest` — depends on steps 5, 6, 8.
13. `OutboxRelayTest` — depends on steps 5, 6, 9.
14. `OutboxTransactionIntegrationTest` — depends on steps 1, 2, 5, 6, 8.
15. `OutboxGrantMigrationIntegrationTest` — depends on step 2 (and `V1`/`V2`, unchanged).
16. `mvn -pl services/crypto verify` — full suite; steps 14/15 need Docker (see plan's own note under
    those tests) — the rest do not.
