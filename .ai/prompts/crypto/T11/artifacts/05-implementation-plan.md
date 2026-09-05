# crypto · T11 · Phase 5 — Implementation Plan

## Files to create

All six trace directly to the frozen brief's "Files to Create" list:

1. `services/crypto/src/main/resources/db/migration/V5__crypto_app_token_allowlist_grant.sql`
2. `services/crypto/src/main/java/com/themistra/crypto/common/config/TokenAllowlistProperties.java`
3. `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlist.java`
4. `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistRepository.java`
5. `services/crypto/src/main/java/com/themistra/crypto/token/TokenValidator.java`
6. `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistSeeder.java`

Test files (all under `services/crypto/src/test/java/com/themistra/crypto/token/`, plus one under
`common/config/`):
7. `TokenAllowlistTest.java`
8. `TokenValidatorTest.java`
9. `TokenAllowlistSeederTest.java`
10. `TokenModuleBoundaryTest.java`
11. `TokenAllowlistRepositoryIntegrationTest.java` (Docker-gated)
12. `services/crypto/src/test/java/com/themistra/crypto/common/config/TokenAllowlistPropertiesTest.java`

## Files to modify

- `services/crypto/src/main/resources/application.properties` — add four
  `themistra.crypto.token-allowlist.entries[...]` blocks (ETHEREUM/TRON × USDT/USDC).
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  `allMigrationsAreRecordedAsSuccessfulInFlywayHistory`'s expected list becomes `"1", "2", "3", "4",
  "5"`; `UNGRANTED_TABLES` drops `"provider_health"` and `"token_allowlist"`, becoming `List.of("watches",
  "chain_cursors", "screening_results", "shedlock")`.

## Public methods (signatures)

**`TokenAllowlistProperties`** (record, mirrors `ProviderProperties`'s nested-list style):
```java
@ConfigurationProperties(prefix = "themistra.crypto.token-allowlist")
@Validated
public record TokenAllowlistProperties(@NotEmpty @Valid List<Entry> entries) {
    public record Entry(
            @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
            @NotBlank String contractAddress,
            @NotBlank String symbol,
            @Min(0) int decimals,
            @Positive int version,
            @NotBlank String signature) {
    }
}
```

**`TokenAllowlist`** (entity):
```java
@Entity
@Table(name = "token_allowlist", schema = "chain")
public class TokenAllowlist {
    public static TokenAllowlist create(String chain, String contractAddress, String symbol,
            int decimals, int version, String signature, Instant createdAt);

    public Long id();
    public String chain();
    public String contractAddress();
    public String symbol();
    public short decimals();
    public int version();
    public String signature();
    public Instant createdAt();
}
```
`signature` maps `TEXT` via `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` (`org.hibernate.annotations.JdbcTypeCode`/
`org.hibernate.type.SqlTypes`, the same annotation family `OutboxEvent.payload` already uses for
`@JdbcTypeCode(SqlTypes.JSON)`) — `token_allowlist.signature` is the only `TEXT` column in the whole
schema, so this is the first precedent for it in this codebase, chosen explicitly over relying on
Hibernate's implicit `VARCHAR`/`TEXT` compatibility under `spring.jpa.hibernate.ddl-auto=validate`.
`decimals` is range-checked (`toShort`, mirrors `QuorumDecision`'s exact precedent) before the `int`→`short`
narrowing cast.

**`TokenAllowlistRepository`** (package-private, no `public` modifier — mirrors
`ObservationRepository`/`QuorumDecisionRepository`/`ProviderHealthRepository`):
```java
interface TokenAllowlistRepository extends JpaRepository<TokenAllowlist, Long> {
    Optional<TokenAllowlist> findTopByOrderByVersionDesc();
    Optional<TokenAllowlist> findByChainAndContractAddressAndVersion(String chain, String contractAddress, int version);
}
```

**`TokenValidator`**:
```java
@Component
public class TokenValidator {
    public TokenValidator(TokenAllowlistRepository repository);

    public Optional<TokenAllowlist> validate(String chain, String contractAddress);
}
```

**`TokenAllowlistSeeder`**:
```java
@Component
public class TokenAllowlistSeeder implements ApplicationRunner {
    public TokenAllowlistSeeder(TokenAllowlistRepository repository, TokenAllowlistProperties properties, Clock clock);

    @Override
    public void run(ApplicationArguments args);
}
```

## Private methods

- `TokenAllowlist`:
  - `private static short toShort(int value, String fieldName)` — range-check + narrowing cast (mirrors
    `QuorumDecision.toShort` exactly).
- `TokenValidator`:
  - `private static final Set<String> KNOWN_CHAINS = Set.of("ETHEREUM", "TRON")` — fail-fast validation
    set (AC7).
  - `private void logUnknownToken(String chain, String contractAddress)` — the single `WARN` log call
    site, invoked from both the empty-table path and the not-found-at-current-version path (AC2/Amendment #3).
- `TokenAllowlistSeeder`:
  - `private void seedIfAbsent(TokenAllowlistProperties.Entry entry)` — check-then-insert, catching
    `DataIntegrityViolationException` around the `save` call only (AC8).

## Entities used

- `TokenAllowlist` (new, this task).

## Repositories used

- `TokenAllowlistRepository` (new, this task) — `.findTopByOrderByVersionDesc()`,
  `.findByChainAndContractAddressAndVersion(...)`, `.save(...)` (seeder only) — no update/delete call
  anywhere.

## Services used

- `Clock` bean (`common/ClockConfig`, T04) — the seeder's `createdAt` source.
- No other existing service/component consumed. `TokenAllowlistProperties` is a new collaborator, not a
  pre-existing one.

## Unit / integration tests required

**`TokenAllowlistPropertiesTest`** (`ApplicationContextRunner`, mirrors `ProviderPropertiesTest`/
`FinalityPropertiesTest`):
- Binds a valid 4-entry list.
- Fails when `entries` is missing/empty.
- Fails when an entry's `chain` is outside `ETHEREUM|TRON`, or any required string field is blank, or
  `decimals`/`version` violate their bounds.

**`TokenAllowlistTest`** (plain JUnit):
- `create` populates every field exactly as given.
- `create` rejects a `decimals` value outside `[0, Short.MAX_VALUE]`.
- No public mutator beyond construction (reflection, mirrors `ObservationTest`/`QuorumDecisionTest`).

**`TokenValidatorTest`** (`@ExtendWith(MockitoExtension.class)`, mocked `TokenAllowlistRepository`):
- `shouldIdentifyTokenByContractAddressNotSymbol` (named) — a row with a deliberately misleading
  `symbol` (e.g. a USDC contract labeled `"USDT"`) is still returned correctly by address; reflection
  confirms no `validate` overload accepts a `symbol` parameter.
- `shouldSurfaceUnknownTokenForNonAllowlistedContract` (named) — empty result for a non-seeded pair.
- Returns empty when the repository has no rows at all (empty table).
- Only considers the highest version — a pair present at version 1 but absent at version 2 (the current
  max) is `UNKNOWN_TOKEN`.
- `shouldRejectNullChainOrContractAddress` — null-guard test.
- Throws `IllegalArgumentException` for an unrecognized `chain` (e.g. `"SOLANA"`, `"ethereum"`).
- Logs a `WARN` line (Logback `ListAppender`, mirrors `HeldFactAlerterTest`) containing `chain`,
  `contractAddress`, and `UNKNOWN_TOKEN` on both empty-table and not-found-at-current-version paths.

**`TokenAllowlistSeederTest`** (`@ExtendWith(MockitoExtension.class)`, mocked
`TokenAllowlistRepository`, fixed `Clock`, a real small `TokenAllowlistProperties` instance):
- Seeds every configured entry that doesn't already exist.
- Skips an entry that `findByChainAndContractAddressAndVersion` reports as already present (no `save`
  call for it).
- Catches a `DataIntegrityViolationException` thrown by `save` for one entry and continues processing
  the remaining entries without propagating (AC8).

**`TokenModuleBoundaryTest`** (mirrors `ProviderModuleBoundaryTest` exactly — simple source-import scan,
not ArchUnit):
- No `.java` file under `token/`'s main source imports `adapter`, `observation`, `provider`, or
  `quorum`.

**`TokenAllowlistRepositoryIntegrationTest`** (Docker-gated Testcontainers, mirrors
`ProviderHealthRepositoryIntegrationTest`'s exact pattern, extended with the full Spring context so
`TokenAllowlistSeeder` actually runs on startup rather than a narrow `@EntityScan`/`@EnableJpaRepositories`-only
config):
- After context startup, all four configured entries exist in `token_allowlist`, correctly shaped.
- Re-starting the context (or calling the seeder's `run` a second time) does not duplicate rows
  (idempotent skip-if-exists).
- `crypto_app` can `INSERT`/`SELECT` but `UPDATE`/`DELETE` are denied (AC4).

## Execution order

1. `V5__crypto_app_token_allowlist_grant.sql` — schema/grant first.
2. `TokenAllowlistProperties.java` (+ `TokenAllowlistPropertiesTest.java`) — no dependencies on anything
   else in this task.
3. `TokenAllowlist.java` (+ `TokenAllowlistTest.java`) — depends on nothing new.
4. `TokenAllowlistRepository.java` — depends on `TokenAllowlist` (step 3).
5. `TokenValidator.java` (+ `TokenValidatorTest.java`) — depends on `TokenAllowlistRepository` (step 4).
6. `TokenAllowlistSeeder.java` (+ `TokenAllowlistSeederTest.java`) — composes steps 2, 4, and `Clock`
   (T04, existing).
7. `application.properties` — add the four seed entries (needed before the seeder can be exercised via
   a real Spring context).
8. `ChainBaselineMigrationIntegrationTest.java` — update the Flyway-version-list and
   `UNGRANTED_TABLES` assertions (Amendment #2).
9. `TokenAllowlistRepositoryIntegrationTest.java`, `TokenModuleBoundaryTest.java` — Docker-gated
   integration test and the module-boundary scan (expected to compile but not execute the former in
   this environment, per every prior task this session).
10. Full `mvn -pl services/crypto test-compile` then targeted `mvn -pl services/crypto test -Dtest=...`
    for the new unit-scope test classes, then a full `mvn -pl services/crypto -am test` regression pass.
