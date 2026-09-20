# crypto · T11 · Phase 3 — Design Challenge Findings

Consumed: `artifacts/02-task-implementation-brief.md`
References: `spec/crypto-service/agents.md`, `spec/crypto-service/package.md` §8, `spec/crypto-service/requirements.md` (R13/R14), `spec/crypto-service/design.md` (L7), `spec/crypto-service/tasks.md` (T11), existing `services/crypto` migrations V1–V4 and integration tests.

---

### 1. DML seed migration conflicts with `agents.md` "DDL-only migrations" standing rule

- **Severity:** High
- **Evidence:** `agents.md` Platform rules / Persistence & schema state: "Flyway, DDL-only migrations. A merged migration is immutable; new work is a new `V<n>__...` file." The TIB proposes `V6__token_allowlist_seed.sql` containing `INSERT` statements.
- **Recommended brief amendment:** Either (a) add a T11-specific §4a LOCKED decision that explicitly overrides `agents.md` for this seeded reference-data table, or (b) switch seeding to the config-driven path allowed by the raw task statement ("via migration/config") so that Flyway remains DDL-only. If (a) is chosen, cite `agents.md` §4a override procedure.

---

### 2. Existing `ChainBaselineMigrationIntegrationTest` will fail when V5/V6 are added

- **Severity:** High
- **Evidence:**
  - `ChainBaselineMigrationIntegrationTest.allMigrationsAreRecordedAsSuccessfulInFlywayHistory()` asserts `containsExactly("1", "2", "3")`; V5 and V6 will add versions `5` and `6`.
  - `UNGRANTED_TABLES` includes `"token_allowlist"` and asserts `SELECT` is denied; `V5` grants `crypto_app` `SELECT` on it.
  The TIB lists "Files to Modify: None expected".
- **Recommended brief amendment:** Add `ChainBaselineMigrationIntegrationTest` to "Files to Modify", update the expected Flyway version list, and move `token_allowlist` from `UNGRANTED_TABLES` into a SELECT-only verification list (or create a dedicated `TokenAllowlistGrantMigrationIntegrationTest` mirroring `OutboxGrantMigrationIntegrationTest`).

---

### 3. "Surfaced loudly" (R14) is untestable without an observable surfacing mechanism

- **Severity:** Medium
- **Evidence:** R14 says non-allowlisted tokens are "classif[ied] as `UNKNOWN_TOKEN` and surface it loudly". The TIB maps this to `Optional.empty()` for future conversion to `QuorumOutcome.UNKNOWN_TOKEN`, but defines no log line, metric, alert, or event within T11's scope. The named test `shouldSurfaceUnknownTokenForNonAllowlistedContract` can therefore only assert `Optional.empty()`.
- **Recommended brief amendment:** Define what "loudly" means for this task — e.g., a structured WARN log with fields `(chain, contractAddress, reason=UNKNOWN_TOKEN)`, and/or a Micrometer counter `token.unknown`. Add a corresponding AC and required test asserting the observable surfacing occurs.

---

### 4. Exact-string address matching creates a hidden integration hazard with Task 12

- **Severity:** Medium
- **Evidence:** The TIB states "Address matching is an exact string comparison — no case-folding, no chain-aware normalization" and that callers must supply the exact casing until Task 12 is wired. Task 12 (L8) makes EIP-55/Base58Check validation mandatory at the boundary. It is undefined whether the allowlist stores checksum-cased addresses, lowercase addresses, or something else, and whether Task 12's `AddressValidator` will pass addresses to `TokenValidator` in the same form the allowlist stores them. A mismatch would turn legitimate tokens into `UNKNOWN_TOKEN`.
- **Recommended brief amendment:** Specify the exact casing of the placeholder contract addresses in `V6` (e.g., all lowercase or EIP-55), and document that any future wiring of `AddressValidator` must preserve that casing when calling `TokenValidator`, or that a normalization step must be added as a separate task.

---

### 5. Unvalidated `chain` string risks silent misclassification

- **Severity:** Medium
- **Evidence:** The TIB follows `ProviderProperties` in using a plain `String chain`, but `ProviderProperties` enforces `ETHEREUM|TRON` via Bean Validation at config-binding time. `TokenValidator.validate(String chain, String contractAddress)` has no validation. Passing `"ethereum"`, `"ETH"`, or a typo will return `Optional.empty()` rather than fail fast, which violates the spirit of "surfaced loudly" and makes typos indistinguishable from spoofed tokens.
- **Recommended brief amendment:** Either (a) validate `chain` against the allowed set inside `validate()` and throw `IllegalArgumentException` for unknown values, or (b) document that callers must pass the exact canonical chain name and that any other value intentionally maps to `UNKNOWN_TOKEN`.

---

### 6. Placeholder `signature` conflicts with the plain meaning of L7 "signed"

- **Severity:** Medium
- **Evidence:** L7 LOCKED states tokens are matched against a "**signed, versioned** canonical-token allowlist". The TIB seeds `signature` as `'local-only-unsigned-placeholder'` and explicitly does not verify it, trusting the migration boundary. This deviates from the locked decision's plain wording and leaves the allowlist integrity dependent on migration-access control rather than cryptography.
- **Recommended brief amendment:** Add a LOCKED decision entry stating that T11 interprets "signed" as "the `signature` column exists for future cryptographic verification, but no signing key, algorithm, or verification requirement is defined in this phase". Alternatively, schedule a follow-up task to design and implement the signature scheme.

---

### 7. `TokenAllowlistRepository` described as "package-private" is ambiguous

- **Severity:** Low
- **Evidence:** The TIB says the repository is "package-private". Interface methods in Java are public by default, and Spring Data JPA generally proxies public interfaces. A package-private repository interface may or may not be picked up by component scanning depending on the scanner configuration. The intent (keep it inside `token/`) is clear, but the wording is technically incorrect and could mislead implementation.
- **Recommended brief amendment:** State that the interface is `public` (so Spring Data can create the proxy) but resides in `com.themistra.crypto.token` and is not imported outside that package except by tests; or, if truly package-private, add an explicit `@EnableJpaRepositories(basePackageClasses = TokenAllowlistRepository.class)` configuration in `token/` and verify it works.

---

### 8. Version cutover semantics under rolling deployment are unstated

- **Severity:** Low
- **Evidence:** `validate` uses the global maximum version present in the table. A new seed migration adding version `2` will become visible to all running application instances as soon as Flyway executes, including old-code instances during a rolling deploy. The TIB does not state whether old code must tolerate newer versions, or whether version transitions must be deployment-synchronized.
- **Recommended brief amendment:** Document that allowlist version transitions are tied to Flyway migration execution and therefore require a coordinated deployment; state that running instances are expected to read the latest version immediately after migration.

---

### 9. `TokenAllowlist.create(...)` parameters and `createdAt` semantics are not specified

- **Severity:** Low
- **Evidence:** The TIB says the factory "mirrors `Observation`/`QuorumDecision`" but does not list parameters. `created_at` has a database default of `now()`. Tests may need to set `createdAt` deterministically. `decimals` maps to `SMALLINT`; a range guard like `QuorumDecision.toShort` may be appropriate but is not mentioned.
- **Recommended brief amendment:** List the factory signature explicitly, e.g. `create(chain, contractAddress, symbol, decimals, version, signature, createdAt)`, and state whether `createdAt` is nullable (defaults to DB `now()`) or required. Include a range validation rule for `decimals` if mirroring `QuorumDecision`'s discipline.

---

### 10. The `shouldIdentifyTokenByContractAddressNotSymbol` test scenario is undefined

- **Severity:** Low
- **Evidence:** AC1 and the named test require proving that `symbol` is never consulted for identity, but the TIB does not describe how to construct that proof. It is unclear whether the test should seed two rows with the same `(chain, contractAddress)` and different symbols, or pass a misleading symbol parameter that the validator ignores.
- **Recommended brief amendment:** Add a concrete scenario: seed a row where `symbol` is intentionally misleading (e.g., a USDC contract labeled `"USDT"`) and assert that `validate(chain, contractAddress)` returns the row based solely on the address; additionally assert that no overload accepting a `symbol` parameter exists.

---

### 11. No ArchUnit test is required for AC6 module-boundary compliance

- **Severity:** Low
- **Evidence:** AC6 states that no import in `token/` reaches `adapter/`, `observation/`, `provider/`, or `quorum/`. The codebase already has `ProviderModuleBoundaryTest` enforcing provider-package boundaries. The TIB does not list a corresponding `TokenModuleBoundaryTest`.
- **Recommended brief amendment:** Add a required ArchUnit test, e.g. `TokenModuleBoundaryTest`, asserting that classes in `com.themistra.crypto.token` do not depend on `..adapter..`, `..observation..`, `..provider..`, or `..quorum..`.

---

### 12. Real mainnet contract addresses are deferred without explicit acceptance criteria

- **Severity:** Low
- **Evidence:** `tasks.md` T11 says seed "per-chain official USDT/USDC contracts". The TIB deliberately uses "clearly-fake, syntactically-shaped placeholders" and states real production entries are an operational concern. This is a reasonable scope boundary, but it is not captured as an explicit decision.
- **Recommended brief amendment:** Add an explicit note under "Open Questions" or "Locked Decisions" stating that real mainnet contract addresses are out of scope for T11 and will be supplied by operations at deployment time; otherwise, obtain and seed the real addresses now to satisfy the task statement verbatim.

---

### 13. No required test for null-parameter rejection

- **Severity:** Low
- **Evidence:** The Constraints section says `validate` rejects `null` `chain`/`contractAddress` via `Objects.requireNonNull`, but no required test is listed.
- **Recommended brief amendment:** Add a required test `shouldRejectNullChainOrContractAddress` to the Required Tests section.

---

### 14. Superseded-version test relies on test-only data creation path

- **Severity:** Low
- **Evidence:** The AC3 test must insert a version-2 row that removes a token present in version-1. Since the application layer never mutates the allowlist, tests must persist fixtures via the repository. The TIB does not explicitly state that `TokenAllowlistRepository` inherits `save` from `JpaRepository` and that tests may use it.
- **Recommended brief amendment:** Clarify in the Required Tests section that tests may call `tokenAllowlistRepository.save(TokenAllowlist.create(...))` to construct versioned fixtures.
