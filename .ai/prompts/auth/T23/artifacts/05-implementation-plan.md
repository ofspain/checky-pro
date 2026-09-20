# auth · T23 · Phase 5 — Implementation Plan

Every file below traces directly to the frozen brief's Files to Create section (`artifacts/04-frozen-task-brief.md`); no additional files are planned.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKey.java`
2. `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyRepository.java`
3. `services/auth/src/test/java/com/themistra/auth/apikey/ApiKeyPersistenceIntegrationTest.java`

## Files to modify

None.

## Public methods (signatures)

**`ApiKey`**
```java
public static ApiKey create(Long accountId, String prefix, String keyHash, String name,
                             List<String> scopes, Instant createdAt)

public Long getId()
public UUID getKeyUuid()
public Long getAccountId()
public String getPrefix()
public String getKeyHash()
public String getName()
public List<String> getScopes()
public Instant getLastUsedAt()
public Instant getExpiresAt()
public Instant getRevokedAt()
public Instant getCreatedAt()
```
No setters/mutators — per the frozen brief, T23 provides only the mapping and factory; `lastUsedAt`/`revokedAt`/`name` mutation is explicitly left for T24–T26.

**`ApiKeyRepository`** (package-private interface, not public — `ArchitectureTest.repositories_are_never_public`)
```java
interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {
    List<ApiKey> findByPrefix(String prefix);
}
```

## Private methods

**`ApiKey`** — none beyond the protected no-arg JPA constructor (`protected ApiKey() {}`).

**`ApiKeyPersistenceIntegrationTest`** — one private helper:
```java
private Long insertAccount(String email)
```
Runs a direct `JdbcTemplate` insert (`INSERT INTO accounts (account_uuid, email) VALUES (?, ?) RETURNING id`) and returns the generated id — per the frozen brief's resolution of Phase 3 finding #5, this avoids any dependency on `AccountService`/`AccountRepository` and needs no breach-check property override (that call path is never invoked).

## Entities used
- `ApiKey` (new, this task).
- `accounts` table only via a raw JDBC insert in the test — the `Account` JPA entity itself is never touched, imported, or depended on (L12).

## Repositories used
- `ApiKeyRepository` (new, this task) — autowired directly into the persistence test, same pattern as `MfaPersistenceIntegrationTest` autowiring `MfaEnrollmentRepository`/`RecoveryCodeRepository`.

## Services used
None. T23 adds no service class — that is explicitly out of scope (T24 owns `ApiKeyService`).

## Unit/integration tests required

No plain-JUnit unit test is planned: `ApiKey` has no branching logic beyond `Objects.requireNonNull` calls and a default-if-null on `scopes`, which is adequately covered by the integration test's field assertions plus one deliberate `assertThatThrownBy` case per required argument. This matches this codebase's precedent — `MfaEnrollment`'s equivalent unit test (`MfaEnrollmentTest`) exists because `MfaEnrollment` has a real state-transition method (`confirm`); `ApiKey` has no equivalent behavior to unit-test in isolation.

- `ApiKeyPersistenceIntegrationTest` (Testcontainers, Postgres + Kafka via `@Import(TestcontainersConfiguration.class)`, no breach-check property needed):
  1. `apiKeyRoundTripsEveryColumnIncludingTheScopesArray()` — `insertAccount(...)`, `ApiKey.create(...)` with every optional field populated and a non-empty `scopes` list, `saveAndFlush`, reload via `findById`, assert every field equals what was set, explicitly including `scopes` (proves Phase 3 finding #3's mapping choice against the real `text[]` column — the actual point of this task, per the frozen brief's AC5).
  2. `apiKeyPersistsWithNullableFieldsAbsent()` — `lastUsedAt`/`expiresAt`/`revokedAt` left null at creation, reload, assert all three are null (proves the nullable columns are mapped correctly, not just the populated case).
  3. `findByPrefixReturnsMatchingKeys()` — save one `ApiKey`, call `findByPrefix` with its exact prefix, assert the list contains exactly that key.
  4. `findByPrefixReturnsEmptyListForUnknownPrefix()` — call `findByPrefix` with a prefix nothing was saved under, assert the returned list is empty (not null, not an exception) — pins down `List` semantics per Phase 3 finding #4's resolution, distinguishing "no match" from the `Optional` shape the brief explicitly rejected.
  5. `createRejectsNullRequiredArguments()` — one `assertThatThrownBy(...).isInstanceOf(NullPointerException.class)` per required factory argument (`accountId`, `prefix`, `keyHash`, `name`, `createdAt`), confirming AC's null-handling constraint without needing five separate test methods.

## Execution order

1. `ApiKey.java` — entity first; nothing else compiles without it.
2. `ApiKeyRepository.java` — depends only on the entity.
3. `ApiKeyPersistenceIntegrationTest.java` — depends on both; written last, run against real Postgres to validate the `scopes` array mapping and `findByPrefix` behavior this task's whole value rests on.

No schema/migration step — the frozen brief is explicit that T23 introduces no Flyway migration; the table is mapped as-is.
