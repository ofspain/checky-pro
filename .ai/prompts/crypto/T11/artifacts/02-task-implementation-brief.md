# crypto · T11 · Phase 2 — Task Implementation Brief (TIB)

## Task

Token allowlist + validator. Seed the signed, versioned allowlist (per-chain official USDT/USDC
contracts) via migration/config. Implement `TokenValidator` — identity by `<chain, contractAddress>`
only; non-allowlisted → `UNKNOWN_TOKEN` surfaced loudly (L7, R13/R14).

## Purpose

The identity boundary that stops a spoofed or unofficial token contract from ever being treated as a
known asset: R13/L7's "never a symbol" rule exists precisely because a symbol is trivially forgeable
(anyone can deploy an ERC-20 called "USDT"), while a contract address on a signed, versioned allowlist
is not.

## Scope

**In:**
- **`TokenAllowlist`** — JPA entity mapping `chain.token_allowlist` exactly as shipped (T02): `id`,
  `chain`, `contractAddress`, `symbol` (display-only, never consulted for identity), `decimals`,
  `version`, `signature`, `createdAt`. Append-only (matches the table's own "never runtime-edited"
  comment) — no setters, protected no-arg constructor, public static `create(...)` factory (mirrors
  `Observation`/`QuorumDecision`). **Kept even though no production code path calls it (see Seeding
  below)** — for test-construction convenience and API consistency with every other entity in this
  codebase; documented explicitly as such.
- **`TokenAllowlistRepository`** — `JpaRepository<TokenAllowlist, Long>`, package-private:
  `findTopByOrderByVersionDesc()` and `findByChainAndContractAddressAndVersion(String, String, int)`.
- **`TokenValidator`** — `public Optional<TokenAllowlist> validate(String chain, String
  contractAddress)`. **Resolves Phase 1 Open Question ("does `token/` return/import
  `quorum.QuorumOutcome`?"): NO.** `TokenValidator` returns `Optional<TokenAllowlist>` — empty means
  "not on the allowlist" (R14), present means the resolved entry (whose `symbol`/`decimals` a caller
  may display, never use for identity). `token/` stays `quorum`-agnostic; whichever future task
  actually wires token validation into the quorum/attest pipeline (none currently scheduled/visible in
  `tasks.md`'s own list) is responsible for mapping an empty result to `QuorumOutcome.UNKNOWN_TOKEN` at
  that integration point — this mirrors every prior task's own "no real caller yet" pattern
  (`Observation`/`QuorumDecision` had none either until their respective consuming tasks existed).
  `chain` is a plain `String`, not `adapter.Chain` — **matches the existing precedent**
  `ProviderProperties` (T03) already set: it validates chain identity via a `@Pattern(regexp =
  "ETHEREUM|TRON")` `String`, not by importing `adapter.Chain` across the sibling package boundary.
- **Resolves Phase 1 Open Question ("which allowlist version is active?"): a single, global "current
  version" for the whole signed allowlist artifact** — not per-chain. `validate` first calls
  `findTopByOrderByVersionDesc()` to read the current version (the highest `version` value present
  across the entire table), then looks up `(chain, contractAddress, thatVersion)`. An older, superseded
  version's rows remain in the table for audit/history but are never considered active. If the table is
  completely empty (no allowlist ever seeded), every lookup is `UNKNOWN_TOKEN` — a fail-loud, not
  fail-open, default (matches R14's own "surfaced loudly" framing).
- **Resolves Phase 1 Open Question ("how is the allowlist seeded?"): a new Flyway DML migration, not
  application-code/config-driven seeding.** Matches this codebase's established pattern of schema *and*
  reference data both flowing through versioned migrations (no nested-list `@ConfigurationProperties`
  shape is introduced for what would otherwise be an awkward, `ProviderProperties`-style structure for
  potentially many rows). Two new migrations (mirrors T10's own precedent of one file per concern):
  - `V5__crypto_app_token_allowlist_grant.sql` — grants `crypto_app` `SELECT` only (no `INSERT`,
    `UPDATE`, or `DELETE` — the table is genuinely read-only from the running application's own
    perspective; Flyway migrations run as the schema-owning role, which bypasses `GRANT`/`REVOKE`
    entirely per `V2`'s own comment, so the seed migration itself needs no prior grant).
  - `V6__token_allowlist_seed.sql` — seeds `version = 1` rows for Ethereum USDT/USDC and Tron
    USDT/USDC. **Resolves Phase 1 Open Question ("no signature mechanism is specified"): no real
    signing key or algorithm exists anywhere in this spec, so `signature` is seeded as the explicit,
    clearly-labeled placeholder `'local-only-unsigned-placeholder'`** (mirrors `KmsProperties`'s own
    `local-only-fake-kms-key-id` precedent) — no code in this task verifies it (see Out, below).
    **Contract addresses are clearly-fake, syntactically-shaped placeholders, not hand-typed real
    mainnet addresses** — this task has no way to verify a real contract address's correctness from
    memory, and this codebase's own established convention (`ProviderProperties`'s local-profile fake
    provider URLs, never real Alchemy/QuickNode endpoints) is to never embed a possibly-wrong
    "real-looking" value where a placeholder is honest and safe. Real production allowlist entries are
    an operational/deployment concern outside this task's own scope (mirrors `V2`'s own "real
    environments set `crypto_app`'s password out-of-band" precedent for secrets).
- **Address matching is an exact string comparison — no case-folding, no chain-aware normalization.**
  EIP-55 checksum handling (Ethereum) and Base58Check validation (Tron) are explicitly `AddressValidator`'s
  own, later, separately-scheduled scope (L8, task 12). Documented as a known caveat: until task 12 is
  wired in front of this validator, callers must supply `contractAddress` in the exact casing/form the
  allowlist itself stores it.
- **No signature verification.** `signature` is persisted and readable but not cryptographically
  checked by any code in this task — no verification key, algorithm, or requirement is named anywhere
  in the spec for this specific signature (distinct from the KMS attestation-key signing path, an
  unrelated, much-later concern). Trust is placed in the same reviewed-migration boundary already
  relied on for every other frozen schema/seed change in this codebase.

**Out:**
- `AddressValidator` (EIP-55/Base58Check, L8) — task 12.
- `AddressPoisoningDetector` (L9) — task 13.
- Any signature-verification logic for `token_allowlist.signature`.
- Wiring `TokenValidator` into `QuorumEvaluator`/`QuorumDecisionService` (T09, frozen, not modified) or
  any adapter (T05-T07, frozen).
- Any change to `V1`-`V4` migrations.
- Any HTTP endpoint or Kafka event — `TokenValidator` is a pure lookup service.

## Business Rules

- **R13.** Token identity is `<chain, contractAddress>` only; `symbol` is never consulted for identity.
- **R14.** A `<chain, contractAddress>` not on the current allowlist version is classified as
  `UNKNOWN_TOKEN` (represented here as an empty `Optional<TokenAllowlist>`) and surfaced loudly, never
  guessed.

## Locked Decisions

- **L7.** Token identity is contract address only, against a signed, versioned canonical allowlist — a
  symbol is never used to decide identity.

## Dependencies

- `chain.token_allowlist` (T02, fixed schema; grant added by this task's own new migration).
- No new `@ConfigurationProperties` record — seeding is migration-based, not config-driven.
- No new external library dependency.

## Inputs

- `(chain, contractAddress)` — from whatever future caller first needs token identity resolution (no
  such caller exists in this task's own scope; its own tests are the only caller).

## Outputs

- `Optional<TokenAllowlist>` — present (the resolved, current-version entry) or empty (`UNKNOWN_TOKEN`).

## State Changes

None from application code (the table is read-only from `crypto_app`'s own perspective). New rows only
via this task's own one-time seed migration (`V6`), run as the schema-owning role.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlist.java`
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenAllowlistRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/token/TokenValidator.java`
- `services/crypto/src/main/resources/db/migration/V5__crypto_app_token_allowlist_grant.sql`
- `services/crypto/src/main/resources/db/migration/V6__token_allowlist_seed.sql`

## Files to Modify

None expected.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`,
  `V4__crypto_app_provider_health_grant.sql` (T02/T10) — frozen.
- `quorum/QuorumOutcome.java` (T09) — referenced in documentation reasoning only; not imported, not
  modified.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R13, L7).** No field, parameter, or code path in `TokenValidator`/`TokenAllowlist` accepts or
  branches on `symbol` to determine identity — identity is `(chain, contractAddress)` only.
- **AC2 (R14, L7).** `validate` returns an empty `Optional` for any `(chain, contractAddress)` not
  present at the current (highest) `version`, including when the table is entirely empty.
- **AC3 (versioning).** `validate` only ever considers rows at the single highest `version` value
  present in the table; a superseded-version row for the same `(chain, contractAddress)` does not make
  `validate` return present if that pair is absent from the current version.
- **AC4 (migration grant).** `V5` grants `crypto_app` `SELECT` only (no `INSERT`/`UPDATE`/`DELETE`) on
  `chain.token_allowlist`, without modifying `V1`-`V4`.
- **AC5 (seed data).** `V6` seeds exactly one `version = 1` row each for Ethereum USDT, Ethereum USDC,
  Tron USDT, Tron USDC, with placeholder (not real) contract addresses and a placeholder `signature`.
- **AC6 (module boundaries, L15).** No import in `token/` reaches `adapter/`, `observation/`,
  `provider/`, or `quorum/`.

## Required Tests

- `shouldIdentifyTokenByContractAddressNotSymbol` (package.md §8, named) — AC1.
- `shouldSurfaceUnknownTokenForNonAllowlistedContract` (package.md §8, named) — AC2.
- A test asserting `validate` returns empty when the repository has no rows at all (AC2).
- A test asserting `validate` only considers the highest version — seed two versions of the same
  `(chain, contractAddress)` pair where the newer version has since removed it, and confirm `validate`
  reports `UNKNOWN_TOKEN` (AC3).
- A test asserting `TokenAllowlist` has no public mutator beyond construction.
- A test (Docker-gated, mirroring prior tasks' integration tests) proving `V6`'s seed rows exist and are
  correctly shaped after Flyway migrate, and that `crypto_app`'s `V5` grant is sufficient to `SELECT`
  but that an `INSERT`/`UPDATE`/`DELETE` attempt fails (AC4, AC5).

## Constraints

- **Performance:** two sequential queries per `validate` call (max-version lookup, then the keyed
  lookup) — acceptable for a low-volume, non-hot-path check, not a per-block scanning loop.
- **Security:** no secret is introduced; the placeholder `signature`/contract-address values are
  explicitly non-sensitive, clearly-fake data, safe in any profile including a committed migration.
- **Thread-safety:** `TokenValidator` holds only an injected, individually thread-safe repository — no
  shared mutable state.
- **Transaction:** read-only; `TokenAllowlistRepository`'s inherited `JpaRepository` methods run under
  Spring Data's own default read semantics — no explicit `@Transactional` needed for a pure lookup.
- **Module boundaries:** L15/AC6 — `token/` imports nothing from `adapter/`, `observation/`,
  `provider/`, or `quorum/`.
- **Null handling:** `validate` rejects `null` `chain`/`contractAddress` fast via named
  `Objects.requireNonNull`.

## Open Questions

No blockers. All three Phase 1 open items (seeding mechanism, signature verification, active-version
semantics) are resolved above as implementer-proposed decisions, ready for Phase 3 (Kimi) challenge.
