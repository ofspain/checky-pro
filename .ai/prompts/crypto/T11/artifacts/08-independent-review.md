# crypto · T11 · Phase 8 — Independent Code Review Findings

Reviewed: `TokenAllowlist.java`, `TokenAllowlistRepository.java`, `TokenValidator.java`, `TokenAllowlistSeeder.java`, `TokenAllowlistProperties.java`, `V5__crypto_app_token_allowlist_grant.sql`, `application.properties`, `ChainBaselineMigrationIntegrationTest.java`, plus `artifacts/07-self-review.md` and `artifacts/04-frozen-task-brief.md`.

---

### 1. Global "current version" silently breaks every chain that has not been bumped to the new global max

- **Issue:** `TokenValidator.validate` (`TokenValidator.java:53-60`) calls `findTopByOrderByVersionDesc()` with no chain filter, then looks up the requested token at that global maximum version. If any chain is seeded at a higher version than the others, every lookup for the lagging chains will search at the higher version, find nothing, and report `UNKNOWN_TOKEN` for otherwise-valid tokens.
- **Evidence:** The frozen brief explicitly chose global-version semantics (`artifacts/04-frozen-task-brief.md`, lines 56-59), but it does not require every chain to be seeded at the same version. A realistic operational mistake — bumping Ethereum to version 2 while Tron remains at version 1 — would instantly classify every Tron token as unknown. This is the same finding as `07-self-review.md` Finding 1, independently reproduced.
- **Recommendation:** Either (a) change `TokenValidator`/`TokenAllowlistRepository` to per-chain current version (`findTopByChainOrderByVersionDesc(chain)`) and update the brief accordingly, or (b) keep global version but add config/runtime validation that every configured chain has at least one entry at the current global max, plus an explicit operational runbook warning.
- **Confidence:** High.

---

### 2. All required test files are missing from the working tree

- **Issue:** The frozen brief lists eleven required tests (including `TokenAllowlistTest`, `TokenValidatorTest`, `TokenAllowlistSeederTest`, `TokenModuleBoundaryTest`, `TokenAllowlistRepositoryIntegrationTest`, and `TokenAllowlistPropertiesTest`). None exist under `services/crypto/src/test/java/com/themistra/crypto/token/` or `.../common/config/TokenAllowlistPropertiesTest.java`. `git status` is clean, so they were not created and left unstaged.
- **Evidence:** `find services/crypto/src/test/java/com/themistra/crypto/token -type f` returns no files; `find` for `TokenAllowlistPropertiesTest.java` returns nothing. The implementation notes (`artifacts/06-implementation-notes.md`) only claim `compile` and `test-compile` succeeded, not that tests were written.
- **Recommendation:** Create the missing test files per the frozen brief's Required Tests section before marking the task complete.
- **Confidence:** High.

---

### 3. Two-query `validate` has a read-committed race between version read and keyed lookup

- **Issue:** `TokenValidator` issues two sequential repository calls (`findTopByOrderByVersionDesc` then `findByChainAndContractAddressAndVersion`) without a transaction or snapshot. Under PostgreSQL `READ COMMITTED`, a concurrent seeder transaction can commit a new higher version between the two statements. The validator then returns a match at the now-stale version instead of the current one, violating AC3.
- **Evidence:** `TokenValidator.java:53-60`. Each repository call is a separate statement with its own snapshot.
- **Recommendation:** Replace the two calls with a single derived query, e.g. a repository method whose JPQL/SQL selects the entry whose `version` equals `(SELECT MAX(version) FROM token_allowlist)` (or `MAX(version) WHERE chain = ?` if per-chain) in one statement.
- **Confidence:** High.

---

### 4. `TokenAllowlistSeeder` inserts entries one-by-one, so a new version can be partially visible

- **Issue:** The seeder (`TokenAllowlistSeeder.java:44-48`) loops over configured entries and inserts each in its own transaction. If a rolling deploy seeds version 2, another replica can observe version 2 as the global max after only some version-2 rows have been committed. Validations for chains whose version-2 rows have not yet been inserted will return `UNKNOWN_TOKEN` until the seeder finishes.
- **Evidence:** No `@Transactional` on `run`; each `repository.save(...)` is a separate transaction. Combined with the global-max read in `TokenValidator`, an incomplete version 2 becomes immediately "current" for the whole table.
- **Recommendation:** Seed all entries of a new version inside a single transaction (annotate `run` with `@Transactional`), or change the validator to per-chain versioning so a partially-seeded chain does not affect others.
- **Confidence:** Medium.

---

### 5. `DataIntegrityViolationException` catch treats every constraint failure as a benign race

- **Issue:** `TokenAllowlistSeeder.seedIfAbsent` (`TokenAllowlistSeeder.java:61-64`) catches `DataIntegrityViolationException` and logs it as a concurrent-seeding race. The same exception is thrown for any integrity violation, including a null or out-of-range value that somehow bypasses validation. The log message would mislead operators.
- **Evidence:** Only `DataIntegrityViolationException` is caught; the message is always "already seeded concurrently".
- **Recommendation:** Inspect the exception/root cause and only log the benign concurrent-seeding message when the SQL state/error indicates a unique-constraint violation on `(chain, contract_address, version)`. Let other integrity errors propagate so startup fails loudly.
- **Confidence:** Medium.

---

### 6. Duplicate or conflicting config entries with the same key are silently ignored

- **Issue:** `TokenAllowlistSeeder.seedIfAbsent` checks only `(chain, contractAddress, version)`. If `application.properties` lists two entries with the same `(chain, contractAddress, version)` but different `symbol`, `decimals`, or `signature`, the second one is silently skipped.
- **Evidence:** `TokenAllowlistSeeder.java:51-56` and `TokenAllowlistProperties.java:27-35` (no duplicate-key validation on the list).
- **Recommendation:** Add a `@PostConstruct` or factory validation in `TokenAllowlistProperties` that rejects duplicate `(chain, contractAddress, version)` tuples with a clear message.
- **Confidence:** Medium.

---

### 7. Empty-table path logs `WARN` on every `validate` call, risking log flooding

- **Issue:** `TokenValidator.logUnknownToken` (`TokenValidator.java:67-68`) is invoked for both the empty-table path and the not-found-at-current-version path. If the allowlist is never seeded, every validation produces a `WARN` line.
- **Evidence:** `TokenValidator.java:54-56` and `61-63` both call `logUnknownToken`.
- **Recommendation:** Log once at `WARN` on first detection of an empty table (e.g., via an atomic boolean), and use a metric/counter for subsequent unknown-token events; or accept the flood but document it as intentional "loud" surfacing.
- **Confidence:** Medium.

---

### 8. `{ETHEREUM, TRON}` chain set is hardcoded in a third independent location

- **Issue:** `TokenValidator.KNOWN_CHAINS` (`TokenValidator.java:38`) hardcodes the same two-chain set already validated by `TokenAllowlistProperties.Entry.chain`'s `@Pattern` and by `ProviderProperties`/`FinalityProperties` elsewhere.
- **Evidence:** `TokenValidator.java:38`; `TokenAllowlistProperties.java:30`; `ProviderProperties.java:46`; `FinalityProperties.java` (pattern present in the same module).
- **Recommendation:** Derive the allowed set from `TokenAllowlistProperties` or from a shared constant/enum so a future chain addition cannot desynchronize the validator from the config schema.
- **Confidence:** High.

---

### 9. `TokenAllowlistSeeder.run` lacks a defensive null guard on `properties.entries()`

- **Issue:** `TokenAllowlistSeeder.run` (`TokenAllowlistSeeder.java:45`) iterates `properties.entries()` directly. Spring Bean Validation prevents null in the wired context, but the class has no independent guard.
- **Evidence:** No `Objects.requireNonNull(properties.entries(), "entries")` before the loop.
- **Recommendation:** Add a named `Objects.requireNonNull` guard so any future manual construction fails with a clear message.
- **Confidence:** High.

---

### 10. `decimals` range check accepts implausibly large values

- **Issue:** `TokenAllowlist.toShort` (`TokenAllowlist.java:91-96`) accepts any value in `[0, Short.MAX_VALUE]` (0..32767). A config typo such as `decimals=600` passes validation even though real ERC-20/TRC-20 tokens are virtually always 0-18.
- **Evidence:** `TokenAllowlist.java:92`.
- **Recommendation:** Tighten the upper bound to a justifiable value (e.g., 24 or 255) or document that the check only prevents short overflow, not semantic data-entry errors.
- **Confidence:** High.

---

### 11. `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` mapping for `signature` is unverified against `ddl-auto=validate`

- **Issue:** `TokenAllowlist.signature` (`TokenAllowlist.java:60-62`) uses `@JdbcTypeCode(SqlTypes.LONGVARCHAR)` to map the schema's only `TEXT` column. The implementation notes acknowledge this is the first such mapping and that validation against the real PostgreSQL `TEXT` column has not been exercised.
- **Evidence:** `TokenAllowlist.java:60-62`; `artifacts/06-implementation-notes.md` lines 47-51.
- **Recommendation:** Verify with the Docker-gated integration test that the application context starts cleanly (`ddl-auto=validate` passes) against a real Postgres `TEXT` column. If it fails, switch to the Hibernate type known to map to `TEXT` for PostgreSQL (e.g., `SqlTypes.LONG32VARCHAR`).
- **Confidence:** Medium.

---

### 12. Mixed versions within a single config are not rejected

- **Issue:** `TokenAllowlistProperties` allows each entry to declare its own `version`. Because `TokenValidator` uses the global maximum, entries with a lower version are immediately superseded and never active. This is a config footgun.
- **Evidence:** `TokenAllowlistProperties.java:27-35`; `TokenValidator.java:53-60`.
- **Recommendation:** Either validate that all configured entries share the same `version`, or document that config authors must treat each version as a complete cross-chain snapshot.
- **Confidence:** Medium.
