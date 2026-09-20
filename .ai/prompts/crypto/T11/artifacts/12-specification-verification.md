# crypto · T11 · Phase 12 — Specification Verification

**Task (verbatim, `tasks.md` #11):** Token allowlist + validator. Seed the signed, versioned allowlist
(per-chain official USDT/USDC contracts) via migration/config. Implement `TokenValidator` — identity by
`<chain, contractAddress>` only; non-allowlisted → `UNKNOWN_TOKEN` surfaced loudly (L7, R13/R14).

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R13 — identity by `<chain, contractAddress>`, never symbol | Yes | `TokenValidator.validate` (`TokenValidator.java:66`) takes no `symbol` parameter; `symbol` is read only via `TokenAllowlist.symbol()` for display | `TokenValidatorTest.shouldIdentifyTokenByContractAddressNotSymbol` (named), `.validateHasNoOverloadAcceptingASymbolParameter` (reflection) | No | No |
| R14 — non-allowlisted → `UNKNOWN_TOKEN`, surfaced loudly | Yes | `TokenValidator.validate`'s empty-result path calls `logUnknownToken` (`:80-81`), a `WARN` log line | `TokenValidatorTest.shouldSurfaceUnknownTokenForNonAllowlistedContract` (named), `.logsAWarnLineOnUnknownToken` | No | No |
| L7 — signed, versioned canonical allowlist, contract-address-only identity | Yes | `TokenAllowlist` maps every column of `chain.token_allowlist` exactly (T02); `signature` persisted (schema support), not cryptographically verified (explicitly scoped, see Amendments) | `TokenAllowlistTest`, `TokenAllowlistRepositoryIntegrationTest.seederPopulatesAllFourRealConfiguredEntriesOnStartup` | No | No — "signed" scoped per Amendment #6 (schema support only, no verification mechanism specified anywhere in this spec) |
| AC1 (identity, never symbol) | Yes | Same as R13 row | Same as R13 row | No | No |
| AC2 (`UNKNOWN_TOKEN` + `WARN` log) | Yes | Same as R14 row | Same as R14 row, plus `.doesNotLogWhenTheTokenIsFound` | No | No |
| AC3 (single current version, per chain) | Yes | `TokenAllowlistRepository.findCurrentVersionEntry` (`TokenAllowlistRepository.java:20-22`) — corrected at Phase 9 from an initial, incorrect global-version design | `TokenAllowlistRepositoryIntegrationTest.findCurrentVersionEntryScopesToPerChainMaxVersionIndependently`, `.sameTokenAcrossVersionsResolvesToTheLatestVersion`, `.mixedVersionsOnTheSameChainForDifferentTokensOnlyTheHigherVersionsTokenIsCurrent`, `.findCurrentVersionEntryOnAnEntirelyEmptyChainReturnsEmpty` | No | No |
| AC4 (`V5` grant, no `V1`-`V4` change) | Yes | `V5__crypto_app_token_allowlist_grant.sql` — `INSERT, SELECT` only | `TokenAllowlistRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel`, `.updateFailsAtTheDatabaseLevel` (raw JDBC — no JPA mutator exists to trigger one) | No | No |
| AC5 (four config-seeded entries) | Yes | `TokenAllowlistSeeder` + `application.properties`'s four `themistra.crypto.token-allowlist.entries[...]` blocks | `TokenAllowlistRepositoryIntegrationTest.seederPopulatesAllFourRealConfiguredEntriesOnStartup` (field values, not just presence), `.reRunningTheSeederIsIdempotent` | No | No |
| AC6 (module boundaries, L15) | Yes | `token/` imports only `com.themistra.crypto.common.config.TokenAllowlistProperties` | `TokenModuleBoundaryTest` | No | No |
| AC7 (fail-fast chain) | Yes | `TokenValidator.KNOWN_CHAINS` check (`:67-69`), thrown before any repository call | `TokenValidatorTest.throwsForAnUnrecognizedChain` | No | No |
| AC8 (seeder resilience) | Yes | `TokenAllowlistSeeder.seedIfAbsent`'s `catch (DataIntegrityViolationException)` with re-verification (`:69-80`) | `TokenAllowlistSeederTest.catchesAConcurrentDuplicateInsertWhenTheRowNowActuallyExists`, `.rethrowsWhenTheDataIntegrityViolationIsNotABenignConcurrentDuplicate`, `.continuesProcessingRemainingEntriesAfterOneEntryHitsTheBenignConcurrentRace` | No | No |

## Amendments (Phase 3, 14 findings; Phase 8, 12 findings; Phase 11, 10 gaps) — verification

**Phase 3 (design challenge), 13 in full + 1 partial, all verified implemented as decided:** the
headline fix — config-driven seeding (`TokenAllowlistProperties` + `TokenAllowlistSeeder`) replacing
what would have been an `agents.md`-violating DML migration — is fully in place; `V5` grants `INSERT,
SELECT` (not the originally-drafted `SELECT`-only); `ChainBaselineMigrationIntegrationTest`'s
Flyway-version-list and `UNGRANTED_TABLES` were both corrected (also fixing a pre-existing T10-era
staleness); `eventType`/payload concerns do not apply to this task (no event emission); `chain`
fail-fast validation, `WARN`-log "surfaced loudly" mechanism, exact-casing documentation, `decimals`
range guard, and the explicit "signed" scoping are all present exactly as decided.

**Phase 8 (independent review), 9 in full + 2 accepted-as-disclosed-risk + 1 moot, all verified:** the
most consequential — per-chain (not global) current-version semantics via a single atomic
`@Query` — is implemented in `TokenAllowlistRepository.findCurrentVersionEntry` and independently
proven by four dedicated integration tests. The bytecode-confirmed `SqlTypes.LONG32VARCHAR` fix (not
`LONGVARCHAR`) is in place (`TokenAllowlist.java`, the `signature` field's `@JdbcTypeCode`). The
re-verification-before-benign-race-logging fix is in `TokenAllowlistSeeder.seedIfAbsent`. The two
disclosed-but-unfixed risks (the seeder's non-atomic multi-entry visibility window; `WARN`-per-call log
volume on an unseeded allowlist) remain accurately documented in their respective class Javadocs, not
silently dropped.

**Phase 11 (test review), 9 full + 1 rejected, all verified:** all nine accepted test additions exist
and pass, including the raw-JDBC `UPDATE`-denial test (the correct technique given `TokenAllowlist` has
no mutator to trigger one via JPA) and the cross-test isolation fix I caught and corrected during that
same phase (two new integration tests originally used the literal `"ETHEREUM"` chain with version
numbers that could collide with other tests' chain-wide version state — fixed with synthetic,
per-test-unique chain values before being counted as passing).

## Files-to-create / Files-to-modify conformance

All six files listed under "Files to Create" in the frozen brief exist at their exact specified paths
(`V5__crypto_app_token_allowlist_grant.sql`, `TokenAllowlistProperties.java`, `TokenAllowlist.java`,
`TokenAllowlistRepository.java`, `TokenValidator.java`, `TokenAllowlistSeeder.java`). Both files under
"Files to Modify" were touched exactly as scoped: `application.properties` (the four seed entries) and
`ChainBaselineMigrationIntegrationTest.java` (Amendment #2). No file under "Files NOT to Modify" was
touched: `V1`-`V4` (T02/T10), `quorum/QuorumOutcome.java` (T09, referenced in documentation reasoning
only), and nothing under `spec/`.

## Required Tests conformance

All required tests from the frozen brief exist, plus the Phase 11 (Kimi)-driven additions layered on
top (all human-approved 2026-09-05): 3 blank-field validation tests, a `decimals=0` boundary test, a
case-folding-passthrough test, an enhanced seeder-field-assertion test, and 5 integration-test
additions/enhancements (raw-JDBC `UPDATE` denial, empty-chain `MAX()`-over-zero-rows, same-token-
spans-versions, mixed-version-different-tokens, and field-value assertions on the startup-seeded rows).
Current suite state (last full run, this session): 345 module tests total, 337 passing, 8 errors — all
Docker-environment-unavailable (`IllegalState: … Docker environment …`), a pre-existing, disclosed
environment limitation (7 pre-existing from T02/T04/T08/T09/T10, 1 new from this task's own
`TokenAllowlistRepositoryIntegrationTest`), not a code defect. Zero genuine failures.

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every class named in the frozen brief exists, is wired
together as specified, and every acceptance criterion has direct evidence and a passing test (subject
only to the environment's lack of Docker, which blocks *execution* of this task's own one integration
test, not its existence or correctness — it compiles cleanly and, notably, is the first integration
test in this service to run the full application-startup seeding path against the real
`application.properties` configuration, not just a narrow entity/repository slice).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC8, see matrix above, each with
file:line evidence and test coverage.

**(3) Does it violate any LOCKED decision?** No. L7 is implemented exactly as scoped: contract-address-
only identity is enforced (R13/AC1), the allowlist is versioned and the "signed" column exists and is
seeded/readable (schema support), with the explicit, human-approved scoping that cryptographic
verification of `signature` is out of this task's own scope (no key/algorithm is specified anywhere in
this spec). No cross-module import violation: `token/` imports only
`common.config.TokenAllowlistProperties`, nothing from `adapter/`, `observation/`, `provider/`, or
`quorum/` — independently verified by `TokenModuleBoundaryTest`.

**(4) Remaining risks?**
- Two concurrency-adjacent risks are explicitly accepted, disclosed, and not fixed: the seeder's
  per-entry (not batch-atomic) inserts mean a chain's new version can be transiently, partially visible
  during a rolling deploy (documented in `TokenAllowlistSeeder`'s class Javadoc, with the rejected
  batch-transaction alternative's own worse tradeoff explained); and `WARN`-per-`validate()`-call log
  volume on an unseeded or under-seeded allowlist is intentional, not rate-limited (R14's own "surfaced
  loudly" requirement).
- No cryptographic verification of `token_allowlist.signature` exists anywhere in this task's scope —
  an explicit, human-approved scoping decision (Amendment #6), not an oversight, since no signing
  key/algorithm/verification requirement is specified anywhere in the spec for this column.
- Real mainnet USDT/USDC contract addresses are not seeded — deliberately, clearly-fake placeholders
  are used instead (Amendment #12), with real production entries explicitly deferred to an
  operational/deployment-time configuration concern outside this task's own scope.
- `TokenAllowlistRepositoryIntegrationTest` has never actually executed in this environment (Docker
  unavailable throughout this session) — it compiles and is structurally sound, and is the first
  integration test in this service exercising a full seeding-at-startup path, but its assertions
  (including the headline per-chain-versioning fix) are unverified against a real Postgres until
  Docker is available.
- `{ETHEREUM, TRON}` remains hardcoded independently in three unlinked locations across the codebase
  (`ProviderProperties`, `FinalityProperties`, `TokenValidator`) — a pre-existing pattern this task
  did not introduce and, per Phase 9's own reasoning, did not attempt to consolidate (doing so would
  require touching T03's frozen files for a cross-cutting concern outside this task's scope).

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion for T11 is implemented with
file:line evidence and test coverage. This task's own review cycle caught and fixed a genuine,
independently-reproduced High-severity design bug (global vs. per-chain version scoping) at Phase 7/8,
and a bytecode-confirmed wrong Hibernate type mapping at Phase 8/9 — both resolved before this
verification, not deferred. Remaining risks are pre-existing environment limitations or explicitly
accepted-by-design/deferred-to-a-later-task risks, not defects.
