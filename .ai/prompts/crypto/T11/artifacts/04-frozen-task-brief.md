# crypto · T11 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-05. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in — most significantly, a full redesign of the seeding mechanism (Amendment #1).

## Task

Token allowlist + validator. Seed the signed, versioned allowlist (per-chain official USDT/USDC
contracts) via migration/config. Implement `TokenValidator` — identity by `<chain, contractAddress>`
only; non-allowlisted → `UNKNOWN_TOKEN` surfaced loudly (L7, R13/R14).

## Purpose

The identity boundary that stops a spoofed or unofficial token contract from ever being treated as a
known asset — R13/L7's "never a symbol" rule exists because a symbol is trivially forgeable, while a
contract address on a signed, versioned allowlist is not.

## Scope

**In:**
- **`TokenAllowlist`** — JPA entity mapping `chain.token_allowlist` exactly as shipped (T02): `id`,
  `chain`, `contractAddress`, `symbol` (display-only, never consulted for identity), `decimals`,
  `version`, `signature`, `createdAt`. Append-only — no setters, protected no-arg constructor, public
  static factory. **Amendment #9 (Kimi Issue 9): exact factory signature —
  `create(String chain, String contractAddress, String symbol, int decimals, int version, String
  signature, Instant createdAt)`, with `createdAt` required (non-null)** — unlike a DML migration
  (which would rely on the DB's own `DEFAULT now()`), the seeder (below) is application code with an
  injected `Clock`, so it always supplies `createdAt` explicitly, matching `Observation`/`QuorumDecision`'s
  own established discipline. **`decimals` is range-checked before the `int`→`short` narrowing cast**
  (mirrors `QuorumDecision.toShort`'s exact precedent from T09/T10), throwing `IllegalArgumentException`
  for a negative value or one exceeding `Short.MAX_VALUE`. This factory IS called by production code now
  (the seeder), not only by tests, unlike the Phase 2 draft's "test-construction-only" framing.
- **`TokenAllowlistRepository`** — `JpaRepository<TokenAllowlist, Long>`. **Amendment #7 (Kimi Issue 7,
  clarification only): the interface itself carries no `public` modifier (package-private)**, mirroring
  `ObservationRepository`/`QuorumDecisionRepository`/`ProviderHealthRepository`'s own already-proven-
  working convention — Spring Data JPA's proxy generation does not require the interface itself to be
  `public`, only its methods (implicitly public in any interface); this is a wording clarification, not
  a new mechanism. Two finders: `findTopByOrderByVersionDesc()`,
  `findByChainAndContractAddressAndVersion(String, String, int)`.
- **`TokenValidator`** — `public Optional<TokenAllowlist> validate(String chain, String
  contractAddress)`. Returns `Optional<TokenAllowlist>` (empty = `UNKNOWN_TOKEN`, R14); stays
  `quorum`-agnostic — no import of `quorum.QuorumOutcome` (`token/` → `quorum/` import not introduced;
  no caller currently needs it). `chain` is a plain `String` (matches `ProviderProperties`'s own
  established precedent of validating chain identity as a `String`, not `adapter.Chain`).
  **Amendment #5 (Kimi Issue 5): `validate` fail-fasts on an unrecognized `chain`** —
  `IllegalArgumentException` for anything other than `"ETHEREUM"`/`"TRON"` — distinguishing a caller
  bug/typo from a genuine `UNKNOWN_TOKEN` classification, consistent with R14's own "surfaced loudly"
  intent (a typo silently becoming indistinguishable from a spoofed-token signal would undermine that).
  **Amendment #3 (Kimi Issue 3): a non-allowlisted lookup logs a structured `WARN` line**
  (`chain`, `contractAddress`, `reason=UNKNOWN_TOKEN`) before returning empty — the concrete "surfaced
  loudly" mechanism for this task's own scope, mirroring `HeldFactAlerter`'s (T09) interim, log-based
  precedent; no metric/event is added (no `MeterRegistry` usage exists anywhere in this codebase yet).
- **Global "current version" semantics (unchanged from Phase 2):** `validate` reads the single highest
  `version` present across the whole table via `findTopByOrderByVersionDesc()`, then looks up `(chain,
  contractAddress, thatVersion)`. A superseded version's rows remain for audit/history but are never
  active. An empty table means every lookup is `UNKNOWN_TOKEN` (fail-loud default).
- **Amendment #1 (Kimi Issue 1, High — supersedes Phase 2's DML-migration design entirely): seeding is
  config-driven, not a Flyway DML migration.** The Phase 2 draft's `V6__token_allowlist_seed.sql`
  (containing `INSERT` statements) directly violated agents.md's standing "Flyway, DDL-only migrations"
  rule — a real process error, not a deliberate, disclosed deviation, so it is fixed rather than
  overridden. Replaced with:
  - **`TokenAllowlistProperties`** (new `@ConfigurationProperties(prefix =
    "themistra.crypto.token-allowlist")` record) — `List<Entry> entries`, each `Entry(chain,
    contractAddress, symbol, decimals, version, signature)`, validated (`@NotBlank`/`@Pattern` on
    `chain`, `@NotBlank` on the string fields, `@Min(0)` on `decimals`, `@Positive` on `version`) —
    mirrors `ProviderProperties`'s own already-proven nested-list `@ConfigurationProperties` shape (the
    "awkward in flat properties" concern the Phase 2 draft raised against config-driven seeding does not
    hold up against this existing, working precedent).
  - **`TokenAllowlistSeeder`** (new `@Component implements ApplicationRunner`) — for each configured
    entry, checks `findByChainAndContractAddressAndVersion(...)`; if absent, inserts via
    `repository.save(TokenAllowlist.create(...))` using the injected `Clock` for `createdAt`. Idempotent
    across repeated restarts (skip-if-exists). **Catches `DataIntegrityViolationException` around the
    insert** and logs at `INFO` rather than propagating — a multi-replica rolling deploy can have two
    instances both see an entry absent and both attempt to insert it; the loser's insert failing on the
    `UNIQUE (chain, contract_address, version)` constraint is benign (the data ends up correct either
    way) and must not crash application startup, unlike T10's own analogous accepted race (which only
    affects one runtime request, not the whole app's boot sequence).
  - `V5__crypto_app_token_allowlist_grant.sql` (the only new migration — no DML migration exists) grants
    `crypto_app` **`INSERT, SELECT`** (not `SELECT`-only, per the Phase 2 draft) — the running
    application itself now writes the seed rows at startup. No `UPDATE`/`DELETE` — an entry is inserted
    once per `(chain, contractAddress, version)` and never revised; a new version is a new row, matching
    the append-only shape already established for `observations`/`attestations`/`quorum_decisions`
    (T02's own `INSERT, SELECT`-only grant precedent — this table's actual grant shape now matches that
    pattern more closely than T10's update-in-place `provider_health` did).
  - **Amendment #8 (Kimi Issue 8): version-cutover semantics under this new mechanism, documented
    explicitly.** As soon as any single replica boots with a config declaring a new (higher) `version`
    and successfully seeds it, `validate`'s live, uncached `findTopByOrderByVersionDesc()` read makes
    that version immediately "current" for **every** replica's subsequent calls — including replicas
    still running old code that haven't yet restarted. There is no caching layer and no deployment-
    synchronization mechanism; this is an accepted, disclosed property of the design, not a defect.
- **Amendment #4 (Kimi Issue 4): exact casing pinned for seed data.** Ethereum (EVM) placeholder
  contract addresses are seeded **lowercase**; Tron placeholders keep their natural Base58Check-shaped
  form (case is semantically meaningful for Base58Check, so no folding is applied there).
  **`validate` performs an exact string match — no case-folding, no chain-aware normalization** (EIP-55/
  Base58Check handling is `AddressValidator`'s own, later, separately-scheduled scope, L8, task 12).
  Documented explicitly: until task 12 is wired in front of this validator, any caller must supply
  `contractAddress` in exactly the casing form the allowlist stores it (lowercase for EVM chains, exact
  Base58Check form for Tron) — task 12's own `AddressValidator` must preserve this form when it
  eventually calls `TokenValidator`, or a normalization step must be added at that integration point.
- **Amendment #6 (Kimi Issue 6, documentation only): explicit scoping of L7's "signed" requirement.**
  No signing key, algorithm, or verification requirement is named anywhere in this spec for
  `token_allowlist.signature` (distinct from the unrelated, much-later KMS attestation-key signing
  path). This task interprets L7's "signed" as: the `signature` column exists and is persisted/readable
  for future cryptographic verification, but no verification is implemented in this task's own scope —
  trust is placed in the same reviewed-code/reviewed-config boundary already relied on for every other
  frozen change in this codebase. `signature` is seeded as the explicit, clearly-labeled placeholder
  `"local-only-unsigned-placeholder"` (mirrors `KmsProperties`'s own `local-only-fake-kms-key-id`
  precedent).
- **Amendment #12 (Kimi Issue 12, documentation only): real mainnet contract addresses are explicitly
  out of this task's own scope**, formalized as a stated decision rather than only prose: this task
  seeds clearly-fake, syntactically-shaped placeholder addresses (not hand-typed real mainnet
  addresses this task has no way to verify from memory); real production allowlist entries are an
  operational/deployment-time configuration concern (mirrors `V2`'s own "real environments set
  `crypto_app`'s password out-of-band" precedent for secrets).
- **Amendment #2 (Kimi Issue 2): `ChainBaselineMigrationIntegrationTest` (T02) is updated** —
  `allMigrationsAreRecordedAsSuccessfulInFlywayHistory`'s expected version list becomes `"1", "2", "3",
  "4", "5"` (accounting for both T10's `V4` and this task's `V5` — neither had been reflected, a
  pre-existing T10-era gap this task also closes since it touches the same list); `"provider_health"`
  and `"token_allowlist"` are both removed from `UNGRANTED_TABLES` (T10 already granted the former;
  this task grants the latter) — each table's own actual grant shape remains verified by its own
  dedicated integration test (`ProviderHealthRepositoryIntegrationTest`, and this task's new
  `TokenAllowlistRepositoryIntegrationTest`), not folded into `ChainBaselineMigrationIntegrationTest`'s
  own `tx_hash`-keyed `GRANTED_TABLES` helper (which does not fit `token_allowlist`'s different column
  shape).
- **Amendment #11 (Kimi Issue 11): `TokenModuleBoundaryTest`** — mirrors `ProviderModuleBoundaryTest`
  (T10) exactly: a simple source-scan, not a new ArchUnit convention, asserting no import in `token/`
  reaches `adapter/`, `observation/`, `provider/`, or `quorum/`.

**Out:**
- `AddressValidator` (EIP-55/Base58Check, L8) — task 12.
- `AddressPoisoningDetector` (L9) — task 13.
- Any cryptographic verification of `token_allowlist.signature`.
- Wiring `TokenValidator` into `QuorumEvaluator`/`QuorumDecisionService` (T09) or any adapter
  (T05-T07) — all frozen, none modified.
- Any change to `V1`-`V4` migrations.
- Real mainnet contract addresses (Amendment #12).
- Any HTTP endpoint or Kafka event.

## Business Rules

- **R13.** Token identity is `<chain, contractAddress>` only; `symbol` is never consulted for identity.
- **R14.** A `<chain, contractAddress>` not on the current allowlist version is `UNKNOWN_TOKEN`
  (empty `Optional`), logged at `WARN` (Amendment #3), never guessed.

## Locked Decisions

- **L7.** Token identity is contract address only, against a signed, versioned canonical allowlist.
  "Signed" scoped per Amendment #6 above — schema support only, no verification implemented.

## Dependencies

- `chain.token_allowlist` (T02, fixed schema; grant added by `V5`).
- `common/ClockConfig` (T04) — the seeder's `createdAt` source.
- No new external library dependency.

## Inputs

- `(chain, contractAddress)` — from whatever future caller first needs token identity resolution (none
  exists in this task's own scope).
- `TokenAllowlistProperties.entries` — from `application.properties`, consumed by `TokenAllowlistSeeder`
  at startup.

## Outputs

- `Optional<TokenAllowlist>` from `validate` (present or empty/`UNKNOWN_TOKEN`).
- Idempotently-seeded `chain.token_allowlist` rows, one per configured entry, on application startup.

## State Changes

`INSERT`-only rows in `chain.token_allowlist`, written by `TokenAllowlistSeeder` at startup (idempotent,
skip-if-exists). No `UPDATE`/`DELETE` from application code, ever.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlist.java`
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenValidator.java`
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistSeeder.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/TokenAllowlistProperties.java`
- `services/crypto/src/main/resources/db/migration/V5__crypto_app_token_allowlist_grant.sql`

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add
  `themistra.crypto.token-allowlist.entries[...]` (4 seed entries: ETHEREUM/TRON × USDT/USDC).
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java`
  (Amendment #2) — update the Flyway-version-list assertion; remove `provider_health` and
  `token_allowlist` from `UNGRANTED_TABLES`.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`,
  `V4__crypto_app_provider_health_grant.sql` (T02/T10) — frozen.
- `quorum/QuorumOutcome.java` (T09) — referenced in documentation reasoning only.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R13, L7).** No field, parameter, or code path in `TokenValidator`/`TokenAllowlist` accepts or
  branches on `symbol` to determine identity.
- **AC2 (R14, L7).** `validate` returns empty for any `(chain, contractAddress)` not present at the
  current (highest) version, including an entirely empty table; logs a `WARN` line on that path.
- **AC3 (versioning).** `validate` only ever considers rows at the single highest `version` value
  present.
- **AC4 (migration grant).** `V5` grants `crypto_app` `INSERT, SELECT` only, without modifying `V1`-`V4`.
- **AC5 (seed data).** Four config-declared entries seed successfully and idempotently on startup:
  Ethereum USDT, Ethereum USDC, Tron USDT, Tron USDC — placeholder addresses/signature, not real ones.
- **AC6 (module boundaries, L15).** No import in `token/` reaches `adapter/`, `observation/`,
  `provider/`, or `quorum/`.
- **AC7 (fail-fast chain).** `validate` throws `IllegalArgumentException` for a `chain` outside
  `{"ETHEREUM", "TRON"}`.
- **AC8 (seeder resilience).** A concurrent-duplicate-insert during seeding (simulating two replicas
  racing at startup) is caught and logged, not propagated — application startup must not fail because
  of this race.

## Required Tests

- `shouldIdentifyTokenByContractAddressNotSymbol` (package.md §8, named) — AC1. **Amendment #10 (Kimi
  Issue 10): concrete scenario** — seed a row where `symbol` is intentionally misleading (e.g., a USDC
  contract labeled `"USDT"`); assert `validate` returns it based on address alone; assert no overload of
  `validate` accepts a `symbol` parameter (reflection).
- `shouldSurfaceUnknownTokenForNonAllowlistedContract` (package.md §8, named) — AC2.
- A test asserting `validate` returns empty when the repository has no rows at all (AC2).
- A test asserting `validate` only considers the highest version — **Amendment #14 (Kimi Issue 14,
  clarification): fixtures for this test, and the seeder's own production inserts, both use
  `tokenAllowlistRepository.save(TokenAllowlist.create(...))`** — seed two versions of the same
  `(chain, contractAddress)` pair where the newer version has since removed it, confirm `UNKNOWN_TOKEN`
  (AC3).
- **Amendment #13 (Kimi Issue 13): `shouldRejectNullChainOrContractAddress`** — null-guard test (AC1's
  own null-handling constraint).
- A test asserting `validate` throws `IllegalArgumentException` for an unrecognized `chain` (AC7).
- A test asserting a `WARN` log line is emitted on `UNKNOWN_TOKEN` (AC2, Logback `ListAppender`, mirrors
  `HeldFactAlerterTest`'s pattern).
- A test asserting `TokenAllowlistSeeder` catches a `DataIntegrityViolationException` during insert and
  does not propagate it (AC8).
- A test asserting `TokenAllowlist` has no public mutator beyond construction.
- `TokenModuleBoundaryTest` (Amendment #11) — AC6.
- A test (Docker-gated, mirroring prior tasks' integration tests) proving: the seeder's four entries
  exist and are correctly shaped after startup; `crypto_app`'s `V5` grant allows `INSERT`/`SELECT` but
  denies `UPDATE`/`DELETE` on `token_allowlist` (AC4, AC5).

## Constraints

- **Performance:** two sequential queries per `validate` call — acceptable, not a hot-path/per-block
  loop.
- **Security:** no secret introduced; placeholder signature/address values are explicitly non-sensitive.
- **Thread-safety:** `TokenValidator` holds only an injected, thread-safe repository. `TokenAllowlistSeeder`
  runs once at startup; its `DataIntegrityViolationException` catch handles the only realistic
  concurrency concern (Amendment #1/#8).
- **Transaction:** `validate` is read-only, no explicit `@Transactional` needed. The seeder's
  per-entry `save` is individually transactional via `SimpleJpaRepository.save` (mirrors T08/T09's
  established reasoning) — no explicit `@Transactional` needed there either, since each entry's
  check-then-insert is not required to be atomic with any other entry's.
- **Module boundaries:** L15/AC6.
- **Null handling:** `validate` rejects `null` `chain`/`contractAddress` fast via named
  `Objects.requireNonNull`.

## Open Questions

No blockers. All 14 Phase 3 findings are resolved above.
