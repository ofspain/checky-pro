# crypto · T08 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

AWS SDK v2 method signatures below were confirmed via direct class inspection of the cached
`s3-2.25.16.jar` / `sdk-core-2.20.40.jar` (the exact `2.50.2` version this project's BOM will resolve
is not cached locally, but the core `S3Client`/`PutObjectRequest.Builder`/
`ClientOverrideConfiguration.Builder` surface used here has been stable and identical across AWS SDK
v2's service clients for years — Phase 6 will get the final, version-exact compile-time check). No
`org.testcontainers:localstack` jar is cached locally yet; it resolves on first `mvn` invocation once
added to `pom.xml` (Phase 6).

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/observation/FactType.java`
2. `services/crypto/src/main/java/com/themistra/crypto/observation/Observation.java`
3. `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationRepository.java`
4. `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStore.java`
5. `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationLog.java` (the
   coordinating class — named for design.md's own "observation log" phrasing)
6. `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStoreConfig.java`
   (`S3Client` wiring — mirrors `EthereumAdapterConfig`/`TronAdapterConfig`'s `<ClassItWires>Config`
   naming convention)
7. `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationTest.java`
8. `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationLogTest.java`
9. `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreTest.java`
10. `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreLocalStackIntegrationTest.java`

## Files to modify

1. `services/crypto/pom.xml` — add `software.amazon.awssdk:s3` (no explicit version, inherits the
   existing BOM import) and `org.testcontainers:localstack` (test scope, existing
   `testcontainers.version` property).

No files outside this list.

## Public methods (signatures)

**`FactType`**
```java
public enum FactType {
    EXISTENCE, AMOUNT, TOKEN, CONFIRMATIONS, FINALITY;

    // Nested, not a separate top-level file - the frozen brief's Files-to-Create list names only
    // FactType.java, not a second converter file.
    static class Converter implements AttributeConverter<FactType, String> {
        @Override public String convertToDatabaseColumn(FactType factType); // lowercase name()
        @Override public FactType convertToEntityAttribute(String dbValue); // uppercase then valueOf
    }
}
```

**`Observation`**
```java
@Entity
@Table(name = "observations", schema = "chain")
public class Observation {
    public static Observation create(String chain, String txHash, String provider, FactType factType,
                                      String rawResponseJson, String s3SnapshotKey, Instant observedAt);
        // s3SnapshotKey nullable (amendment #2's failure/timeout case) - passed in already resolved,
        // never set after construction (no mutator exists for it).

    public Long id();
    public String chain();
    public String txHash();
    public String provider();
    public FactType factType();
    public String rawResponse();
    public String s3SnapshotKey(); // nullable
    public Instant observedAt();
}
```
No setters, no `@PrePersist` fallback (unlike `OutboxEvent`) — `observedAt` is always supplied by the
caller (`ObservationLog`, from its injected `Clock`), never defaulted inside the entity itself, since
this entity must never be constructed without every field already known (amendment #9).

**`ObservationRepository`**
```java
interface ObservationRepository extends JpaRepository<Observation, Long> {
    List<Observation> findByChainAndTxHashAndFactType(String chain, String txHash, FactType factType);
}
```

**`ObservationSnapshotStore`**
```java
public class ObservationSnapshotStore {
    public ObservationSnapshotStore(S3Client s3Client, SnapshotProperties properties);

    public Optional<String> store(String chain, String txHash, String provider, FactType factType,
                                   String rawResponseJson, Instant observedAt);
        // Builds the key (amendment's scheme), calls S3Client.putObject(...) with Content-Type +
        // metadata (amendment #6). Catches SdkException (covers both service errors and the
        // ClientOverrideConfiguration timeout), logs at error level, returns Optional.empty() -
        // never throws. ObservationLog never needs to know about AWS-specific exception types.
}
```

**`ObservationLog`**
```java
@Component
public class ObservationLog {
    public ObservationLog(ObservationSnapshotStore snapshotStore, ObservationRepository repository,
                           ObjectMapper objectMapper, Clock clock);

    @Transactional
    public Observation record(String chain, String txHash, String provider, FactType factType,
                               String rawResponseJson);
        // 1. Validate rawResponseJson parses as JSON (ObjectMapper.readTree) - throws
        //    IllegalArgumentException before any write is attempted if it doesn't (amendment #1).
        // 2. observedAt = clock.instant().
        // 3. snapshotStore.store(...) - the S3 attempt (amendment: before Postgres, "Test ordering").
        // 4. Observation.create(..., s3Key.orElse(null), observedAt).
        // 5. repository.save(...) - the single, only Postgres write (@Transactional scopes this,
        //    not the S3 call, which happens before the transaction is even meaningfully needed).
}
```

**`ObservationSnapshotStoreConfig`**
```java
@Configuration
public class ObservationSnapshotStoreConfig {
    @Bean
    public S3Client s3Client(SnapshotProperties properties);
        // Region.of(properties.region()); ClientOverrideConfiguration.builder()
        // .apiCallTimeout(Duration.ofSeconds(5)).build() (amendment #2) - no explicit
        // credentialsProvider(...) call, so the SDK's own default credential chain applies (L13).

    @Bean
    public ObservationSnapshotStore observationSnapshotStore(S3Client s3Client, SnapshotProperties properties);
}
```

## Private methods

**`ObservationSnapshotStore`**:
- `private String buildKey(String chain, String txHash, String provider, FactType factType, Instant observedAt)`
  — `{prefix}{chain}/{txHash}/{factType-lowercase}/{provider}-{observedAt.toString()}-{UUID.randomUUID()}.json`.
- `private PutObjectRequest buildRequest(String bucket, String key, String chain, String txHash,
  String provider, FactType factType)` — sets `.contentType("application/json")` and
  `.metadata(Map.of("chain", chain, "txHash", txHash, "provider", provider, "factType",
  factType.name()))` (amendment #6).

**`ObservationLog`**:
- `private void validateJson(String rawResponseJson)` — `objectMapper.readTree(rawResponseJson)`
  wrapped to rethrow as `IllegalArgumentException` with context on `JsonProcessingException`.

## Entities / Repositories / Services used

- `Observation` (new, this task) — the entity.
- `ObservationRepository` (new, this task) — the only repository.
- No other entity/repository from any other module is touched (L15) — this task does not read or
  write `quorum_decisions`, `provider_health`, or any other `chain` schema table.

## Unit / integration tests required

Traced to the frozen brief's Required Tests:

**`ObservationTest`** (plain JUnit, no Spring context):
- `createPopulatesEveryFieldExactlyAsGiven` — AC1.
- `hasNoPublicMutatorBeyondConstruction` — reflection-based: asserts no public method on `Observation`
  other than `create` (static) and the getters returns `Observation`/`void` in a way that could mutate
  state; concretely, asserts the class declares no method matching a setter naming/shape pattern (AC3).

**`ObservationSnapshotStoreTest`** (Mockito-mocked `S3Client`, no container):
- `storeReturnsTheComputedKeyOnSuccess` (AC2).
- `storeSetsContentTypeAndMetadataOnThePutObjectRequest` (AC7) — `ArgumentCaptor<PutObjectRequest>`.
- `storeReturnsEmptyAndLogsWhenS3ThrowsAnSdkException` (AC2) — mocks `s3Client.putObject(...)` to
  throw `S3Exception`/`SdkClientException`; asserts `Optional.empty()`, no exception propagates.
- `keyIncludesChainTxHashFactTypeProviderAndAUniqueComponent` — asserts two calls with identical
  inputs produce two *different* keys (the random-UUID/timestamp uniqueness amendment #8 relies on).

**`ObservationLogTest`** (Mockito-mocked `ObservationSnapshotStore`/`ObservationRepository`, fixed
`Clock`):
- `shouldLogEveryProviderResponseVerbatimToObservationLog` (package.md §8 named test, → R4) — asserts
  the saved `Observation.rawResponse()` equals the input verbatim.
- `recordAttemptsTheS3WriteBeforeThePostgresInsert` ("Test ordering", AC4) — `InOrder` verification
  across the two mocks.
- `recordUsesTheReturnedS3KeyWhenPresent` (AC2).
- `recordPersistsWithNullS3KeyWhenTheSnapshotStoreReturnsEmpty` (AC2) — proves a failed S3 write still
  reaches the Postgres insert.
- `recordThrowsForMalformedJsonBeforeAttemptingAnyWrite` (AC1) — asserts neither mock is invoked
  (`verifyNoInteractions`) when the input isn't valid JSON.
- `recordUsesTheInjectedClockNotWallClockTime` — fixed `Clock`, asserts `observedAt()` matches exactly.

**`ObservationSnapshotStoreLocalStackIntegrationTest`** (Testcontainers, `LocalStackContainer` with
`Service.S3`, real `S3Client` pointed at the container's endpoint override, path-style access, static
placeholder credentials):
- `putsAndGetsARealObjectFromLocalStack` — round-trips through the real S3 API (AC1, AC5, AC7 against
  a real, if fake-backed, S3 service).

## Execution order

1. `pom.xml` — add the two new dependencies first.
2. `FactType` — no dependencies beyond `jakarta.persistence.AttributeConverter`.
3. `Observation` — depends on `FactType` (2).
4. `ObservationRepository` — depends on `Observation` (3).
5. `ObservationSnapshotStore` — depends on `FactType` (2), `SnapshotProperties` (T03, unmodified).
6. `ObservationSnapshotStoreConfig` — depends on `ObservationSnapshotStore` (5).
7. `ObservationLog` — depends on `ObservationSnapshotStore` (5), `ObservationRepository` (4), the
   existing `Clock` bean (T04).
8. `ObservationTest`, `ObservationSnapshotStoreTest`, `ObservationLogTest` — depend on steps 2–7.
9. `ObservationSnapshotStoreLocalStackIntegrationTest` — depends on step 5/6 and the new
   `org.testcontainers:localstack` dependency (1).
10. `mvn -pl services/crypto -am compile / test-compile / test` — full verification; this task is the
    first since T02/T04 with a real Docker/Testcontainers dependency in its own new tests (the
    LocalStack container) — if Docker is unavailable in this environment (as it has been for T02–T04's
    own integration tests throughout this session), that one test class will show the same
    pre-existing "Docker environment" error already carried forward, not a new failure.
