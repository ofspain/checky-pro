# crypto · T11 · Phase 11 — Test Review Findings

Reviewed: `TokenAllowlistPropertiesTest`, `TokenAllowlistTest`, `TokenValidatorTest`, `TokenAllowlistSeederTest`, `TokenModuleBoundaryTest`, `TokenAllowlistRepositoryIntegrationTest`, plus the Phase 9 production code they exercise.

---

### 1. `@NotBlank` validation on config string fields is not tested

- **Gap:** `TokenAllowlistProperties.Entry` annotates `chain`, `contractAddress`, `symbol`, and `signature` with `@NotBlank`, but `TokenAllowlistPropertiesTest` only exercises an invalid `chain` pattern and missing/invalid numeric fields. Blank values for the string fields are never rejected in a test.
- **Why it matters:** If Bean Validation binding were misconfigured or a relaxed validator were introduced, a blank contract address or signature could be accepted silently, leading to unusable allowlist entries.
- **Suggested test:** Add `failsWhenContractAddressIsBlank`, `failsWhenSymbolIsBlank`, and `failsWhenSignatureIsBlank` to `TokenAllowlistPropertiesTest`, each asserting the context fails to start.

---

### 2. AC4 UPDATE denial is not tested

- **Gap:** `TokenAllowlistRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel` proves `DELETE` is denied, but `UPDATE` is never exercised.
- **Why it matters:** `V5__crypto_app_token_allowlist_grant.sql` grants only `INSERT, SELECT`; a regression that accidentally granted `UPDATE` would break the append-only invariant without a failing test.
- **Suggested test:** Add `updateFailsAtTheDatabaseLevel` in `TokenAllowlistRepositoryIntegrationTest` that loads a seeded row, mutates a field via the repository, calls `flush`, and asserts a `DataIntegrityViolationException`/permission error.

---

### 3. `findCurrentVersionEntry` returning the latest version of the same token is not explicitly tested

- **Gap:** `findCurrentVersionEntryScopesToPerChainMaxVersionIndependently` asserts that a superseded entry returns empty and a new v2 entry is present, but it never asserts that a token existing at **both** v1 and v2 resolves to the v2 row.
- **Why it matters:** The core versioning behavior is "return the entry at the current (highest) version"; the current test proves chain isolation and superseding, but not that the latest row is actually selected for a token that spans versions.
- **Suggested test:** Save the same `(chain, contractAddress)` at versions 1 and 2 (with different symbols or signatures), then assert `findCurrentVersionEntry` returns the version-2 row.

---

### 4. Exact-string address matching is not tested

- **Gap:** The frozen brief (Amendment #4) states EVM placeholder addresses are lowercase and `validate` performs exact string matching with no case-folding. No test verifies that a differently-cased address returns `UNKNOWN_TOKEN`.
- **Why it matters:** Future callers or Task 12's `AddressValidator` integration could pass checksum-cased addresses; without a regression test, a later normalization change could silently succeed or fail against the allowlist.
- **Suggested test:** Add a unit test (mocked repository) or integration test calling `validate`/`findCurrentVersionEntry` with `0x1111111111111111111111111111111111111A` (uppercase final character) and assert an empty result.

---

### 5. `TokenAllowlistSeeder` save assertions are incomplete

- **Gap:** `seedsEveryConfiguredEntryThatDoesNotAlreadyExist` captures saved entities and asserts only `contractAddress` and `createdAt`, ignoring `chain`, `symbol`, `decimals`, `version`, and `signature`.
- **Why it matters:** A mapping or factory bug that swapped `symbol`/`decimals`/`version` would pass this test. AC5 requires the entries to be "correctly shaped".
- **Suggested test:** Assert every field of the two captured `TokenAllowlist` instances matches the configured `TokenAllowlistProperties.Entry` values.

---

### 6. `decimals=0` boundary is not tested

- **Gap:** `TokenAllowlistTest` tests negative decimals, `decimals=31`, and `decimals=30`, but not `decimals=0`. Zero is a valid real-world value (some tokens have no decimals) and is the lower bound of `@Min(0)`.
- **Why it matters:** A future refactor that accidentally used `@Positive` instead of `@Min(0)` would reject valid zero-decimal tokens; a boundary test catches this.
- **Suggested test:** Add `createAcceptsZeroDecimals` to `TokenAllowlistTest`.

---

### 7. Seeded entry field values are not asserted in the integration test

- **Gap:** `seederPopulatesAllFourRealConfiguredEntriesOnStartup` asserts only that the four `(chain, contractAddress, version)` rows exist, not that their `symbol`, `decimals`, or `signature` match `application.properties`.
- **Why it matters:** A silent mapping drift or typo in `application.properties` could seed wrong metadata that still passes presence checks.
- **Suggested test:** For each of the four entries, assert `symbol`, `decimals`, and `signature` equal the configured values.

---

### 8. `findCurrentVersionEntry` on an empty chain is not directly tested at the repository level

- **Gap:** `TokenValidatorTest.returnsEmptyWhenTheAllowlistHasNoRowsAtAllForThatChain` mocks the repository to return empty; `TokenAllowlistRepositoryIntegrationTest` tests populated chains but never calls `findCurrentVersionEntry` for a chain with zero rows.
- **Why it matters:** The JPQL subquery `(SELECT MAX(t2.version) FROM TokenAllowlist t2 WHERE t2.chain = :chain)` on an empty chain has nuanced behavior (NULL max); a direct test ensures it returns empty rather than throwing or returning an unexpected row.
- **Suggested test:** Add a test that calls `repository.findCurrentVersionEntry("ETHEREUM", "0xanything")` before the seeder has inserted anything and asserts an empty result.

---

### 9. `failsWhenEntriesMissing` is not specific about the failure cause

- **Gap:** `TokenAllowlistPropertiesTest.failsWhenEntriesMissing` only asserts `context.hasFailed()`.
- **Why it matters:** The context could fail for an unrelated reason (e.g., a missing bean or a different validation error), giving a false positive.
- **Suggested test:** Assert the failure cause is a `BindException`/`ValidationException` whose message references `entries`.

---

### 10. Mixed-version config behavior for the same chain is not documented/tested

- **Gap:** `TokenAllowlistProperties` allows each entry to declare its own `version`. No test verifies what happens when one entry for a chain is version 1 and another entry for the same chain is version 2.
- **Why it matters:** With per-chain current-version semantics, the version-1-only tokens become `UNKNOWN_TOKEN`. This is a subtle config hazard that should be locked in by a test.
- **Suggested test:** Add a test seeding `ETHEREUM` `0xold` at version 1 and `ETHEREUM` `0xnew` at version 2, then assert `findCurrentVersionEntry("ETHEREUM", "0xold")` is empty and `findCurrentVersionEntry("ETHEREUM", "0xnew")` is present.
