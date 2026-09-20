# crypto · T11 · Phase 6 — Implementation Notes

## What changed

Six new files, two modified files — exactly as planned (Phase 5), no file the frozen brief did not
authorize was touched.

- **`V5__crypto_app_token_allowlist_grant.sql`** — grants `crypto_app` `INSERT, SELECT` (no
  `UPDATE`/`DELETE`) on `chain.token_allowlist`, closing the Phase 0-confirmed gap in `V2`.
- **`TokenAllowlistProperties.java`** — `@ConfigurationProperties(prefix =
  "themistra.crypto.token-allowlist")`, `List<Entry> entries`, nested `Entry` record mirroring
  `ProviderProperties`'s own proven nested-list style.
- **`TokenAllowlist.java`** — JPA entity, append-only, `signature` mapped via `@JdbcTypeCode(SqlTypes.LONGVARCHAR)`
  (the schema's only `TEXT` column, no prior precedent), `decimals` range-checked via `toShort` (mirrors
  `QuorumDecision`), package-private no-arg constructor, public static `create(...)`.
- **`TokenAllowlistRepository.java`** — package-private (no `public` modifier) `JpaRepository<TokenAllowlist,
  Long>` plus `findTopByOrderByVersionDesc()` and `findByChainAndContractAddressAndVersion(...)`.
- **`TokenValidator.java`** — `validate(chain, contractAddress)` returns `Optional<TokenAllowlist>`;
  fail-fasts (`IllegalArgumentException`) on an unrecognized `chain`; logs `WARN` on `UNKNOWN_TOKEN`
  (both the empty-table and not-found-at-current-version paths).
- **`TokenAllowlistSeeder.java`** — `ApplicationRunner`, idempotent skip-if-exists per configured entry,
  catches `DataIntegrityViolationException` around the insert so a concurrent-replica-startup race
  cannot fail application boot.
- **`application.properties`** — added four `themistra.crypto.token-allowlist.entries[...]` blocks
  (ETHEREUM/TRON × USDT/USDC), clearly-fake placeholder addresses and signature.
- **`ChainBaselineMigrationIntegrationTest.java`** — Flyway-version-list assertion extended to `"1",
  "2", "3", "4", "5"`; `provider_health` (T10) and `token_allowlist` (T11) both removed from
  `UNGRANTED_TABLES`, since neither is true anymore.

## Mapping to plan and acceptance criteria

| AC | Satisfied by |
|---|---|
| AC1 (identity by address only, never symbol) | `TokenValidator.validate`'s signature takes no `symbol` parameter; `symbol` is only ever read via `TokenAllowlist.symbol()` for display |
| AC2 (UNKNOWN_TOKEN + WARN log) | `TokenValidator.validate` — empty-table and not-found-at-current-version paths both call `logUnknownToken` |
| AC3 (single global current version) | `TokenAllowlistRepository.findTopByOrderByVersionDesc()` + the keyed lookup at that version |
| AC4 (V5 grant, no V1-V4 change) | `V5__crypto_app_token_allowlist_grant.sql` |
| AC5 (four seed entries) | `application.properties` + `TokenAllowlistSeeder` |
| AC6 (module boundaries) | `token/` imports only `com.themistra.crypto.common.config.TokenAllowlistProperties` — no import from `adapter/`, `observation/`, `provider/`, `quorum/` anywhere in the new files |
| AC7 (fail-fast chain) | `TokenValidator.KNOWN_CHAINS` check, thrown before any repository call |
| AC8 (seeder resilience) | `TokenAllowlistSeeder.seedIfAbsent`'s `catch (DataIntegrityViolationException)` |

## Deviations forced by reality

None. `mvn -pl services/crypto compile` and `mvn -pl services/crypto test-compile` both succeeded on
the first attempt after implementation, with zero warnings beyond pre-existing ones. The
`@JdbcTypeCode(SqlTypes.LONGVARCHAR)` choice for `signature` was a genuine first-precedent decision
(flagged and reasoned explicitly in Phase 5), not a surprise discovered mid-coding — Phase 6 confirms it
compiles cleanly; whether it satisfies `spring.jpa.hibernate.ddl-auto=validate` against the real `TEXT`
column can only be confirmed once Docker is available (Phase 10/12's own integration test will exercise
this for the first time).
