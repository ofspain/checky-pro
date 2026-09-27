# notification · T04 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/notification/src/main/java/com/themistra/notification/consumer/ProcessedEvent.java`
2. `.../consumer/ProcessedEventRepository.java`
3. `.../consumer/IdempotencyGuard.java`
4. `services/notification/src/main/java/com/themistra/notification/common/ClockConfig.java`
5. `services/notification/src/main/resources/db/migration/V4__notification_app_processed_events_grant.sql`
6. `services/notification/src/test/java/com/themistra/notification/consumer/IdempotencyGuardUnitTest.java`
7. `services/notification/src/test/java/com/themistra/notification/consumer/IdempotencyGuardIntegrationTest.java`

## Files to modify

1. `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
   — `noExtraProductionClassesExistBeyondT03sOwnAuthorizedSet()`'s 8-file list becomes 12 (adds
   `consumer/IdempotencyGuard.java`, `consumer/ProcessedEvent.java`,
   `consumer/ProcessedEventRepository.java`, `common/ClockConfig.java`).
2. `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
   — `processed_events` removed from `UNGRANTED_TABLES`; added to `GRANTED_TABLES`-style coverage
   (its own `insertStatementFor`/`noWhereUpdateStatementFor`/`noWhereDeleteStatementFor` switch
   statements each gain a `"processed_events"` case); `allMigrationsAreRecordedAsSuccessfulInFlywayHistory`'s
   expected version list becomes `"1", "2", "3", "4"`.

No files outside this list. `V1-V3`, `pom.xml`, and everything under `spec/` are untouched, per
frozen brief.

## Public methods (signatures)

**`ProcessedEvent`** (`@Entity`, `@Table(name = "processed_events", schema = "notifications")`)
```java
@Entity
@Table(name = "processed_events", schema = "notifications")
public class ProcessedEvent {
    @Id
    @Column(name = "event_key", length = 200)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {} // JPA only

    public static ProcessedEvent create(String eventKey, String eventType, Instant processedAt);

    public String getEventKey();
    public String getEventType();
    public Instant getProcessedAt();
}
```
Client-assigned `@Id` (no `@GeneratedValue`) — `event_key` IS the natural key, unlike `OutboxEvent`'s
generated surrogate `Long`.

**`ProcessedEventRepository`** (package-private, mirrors `OutboxEventRepository`'s visibility)
```java
interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
```
No custom query methods — `existsById`/`saveAndFlush` from `JpaRepository` are sufficient.

**`IdempotencyGuard`**
```java
@Service
public class IdempotencyGuard {

    private final ProcessedEventRepository repository;
    private final Clock clock;

    public IdempotencyGuard(ProcessedEventRepository repository, Clock clock);

    @Transactional
    public boolean recordIfNew(String eventKey, String eventType) {
        try {
            repository.saveAndFlush(ProcessedEvent.create(eventKey, eventType, clock.instant()));
            return true;
        } catch (DataIntegrityViolationException alreadyProcessed) {
            return false;
        }
    }
}
```
**Design note (resolves Kimi Phase 3 Finding #1 concretely):** `saveAndFlush`, not `save` — a plain
`save` only queues the `INSERT` in Hibernate's own persistence context; without an immediate flush,
the real constraint violation might not surface until the *caller's* own transaction commits, well
outside this method's own `try`/`catch`, defeating the fix entirely. `saveAndFlush` forces the
`INSERT` (and therefore the `PRIMARY KEY` constraint check) to execute synchronously inside this
call. No `existsById` pre-check — relying solely on the DB's own constraint as the single source of
truth eliminates the TOCTOU race Finding #1 identified, rather than merely narrowing its window.

**`ClockConfig`** (mirrors `crypto-service`'s own identical class)
```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

## Private methods

None beyond the entity's protected no-arg constructor (JPA requirement, not a private method).

## Entities used

`ProcessedEvent` (new, this task).

## Repositories used

`ProcessedEventRepository` (new, this task).

## Services used

`IdempotencyGuard` (new, this task) — depends on `ProcessedEventRepository`, `Clock`.

## Unit / integration tests required

1. **`IdempotencyGuardUnitTest`** (plain JUnit + Mockito, fixed `Clock`, no Spring context, no
   Docker) — satisfies the task statement's own literal "unit-test the dedupe" instruction and both
   named tests:
   - `shouldDedupeDuplicateEventDeliveryByEventKey` — mocked `repository.saveAndFlush(...)` returns
     normally → `recordIfNew` returns `true`.
   - `shouldNotDoubleSendWhenSameEventRedelivered` — mocked `repository.saveAndFlush(...)` throws
     `DataIntegrityViolationException` → `recordIfNew` returns `false`.
   - A third test asserting `ProcessedEvent.create(...)`'s `processedAt` comes from the injected
     `Clock`, not `Instant.now()` (captures the argument passed to `saveAndFlush`, asserts it equals
     the fixed clock's instant).
2. **`IdempotencyGuardIntegrationTest`** (`@Testcontainers` + `@SpringBootTest`, real Postgres) —
   the first test class in this module needing a *real Spring context* (not just the Flyway-Java-API
   style `NotificationBaselineMigrationIntegrationTest` uses), since proving `@Transactional`'s real
   join-caller's-transaction behavior needs Spring's own transactional proxy. Design:
   - Static `@Container PostgreSQLContainer` (mirrors `NotificationBaselineMigrationIntegrationTest`).
   - `@BeforeAll`: pre-create `notifications` schema + `citext` (same fix as T02), run `V1-V4` via the
     Flyway Java API as admin, then `ALTER ROLE notification_app PASSWORD ...` (same pattern).
   - `@DynamicPropertySource`: overrides `spring.datasource.{url,username,password}` to point the
     real Spring Boot app at the migrated container, connecting as `notification_app` (the real
     runtime role, not admin) — proving the app's actual runtime credentials can use `IdempotencyGuard`
     end-to-end, not just that the schema exists.
   - Tests:
     - `recordIfNewJoinsAnExternallyOpenedTransactionAndRollsBackWithIt` — opens a transaction via
       `TransactionTemplate`, calls `recordIfNew` inside it, forces `setRollbackOnly()`, then asserts
       (in a fresh transaction) the row is gone — proves `REQUIRED` propagation, not an independent
       commit (Frozen Brief AC3 / Phase 1 Open Question #3, now the "Transaction-Join Question").
     - `concurrentCallsWithSameKeyResolveToExactlyOneTrue` — 8 threads, same event key, asserts
       exactly one `true` result, the rest `false`, no thread propagates an unchecked exception
       (Kimi Phase 3 Finding #1's own required proof).
     - `recordIfNewUsesDefaultRequiredPropagation` — reflection on `IdempotencyGuard.recordIfNew`'s
       `@Transactional` annotation, asserts `propagation() == Propagation.REQUIRED` (Kimi Phase 3
       Finding #6; this assertion holds whether the source states `@Transactional` bare or with an
       explicit `Propagation.REQUIRED` — reflection resolves the annotation's effective value either
       way).
3. **`T01SkeletonRegressionTest`** — re-run with its updated 12-file list.
4. **`NotificationBaselineMigrationIntegrationTest`** — re-run with `processed_events` moved to
   grant-proof coverage and the Flyway history expectation widened to `"1","2","3","4"`.

## Execution order

1. `ProcessedEvent` (no dependencies on anything else new).
2. `ProcessedEventRepository` (depends on `ProcessedEvent`).
3. `ClockConfig` (no dependencies).
4. `IdempotencyGuard` (depends on `ProcessedEventRepository`, `ClockConfig`'s `Clock` bean).
5. `V4__notification_app_processed_events_grant.sql` (independent of the Java changes; needed before
   step 7's integration test can connect as `notification_app` at all).
6. `IdempotencyGuardUnitTest` (steps 1-4's own proof, no DB needed).
7. `IdempotencyGuardIntegrationTest` (needs steps 1-5 all in place).
8. `T01SkeletonRegressionTest`'s updated file list (now true, since steps 1-4 exist).
9. `NotificationBaselineMigrationIntegrationTest`'s updated grant coverage (needs step 5's `V4` to be
   a real, migratable file).
