# crypto · T11 · Phase 1 — Specification Extraction

## Business Rules

- **R13.** When identifying the token of a transfer, the system identifies it by `<chain,
  contractAddress>` and does not rely on a token symbol.
- **R14.** If a transfer's contract address is not on the signed, versioned canonical-token allowlist
  for its chain, the system classifies it as `UNKNOWN_TOKEN` and surfaces it loudly rather than
  guessing an identity.

## Locked Decisions

- **L7.** Token identity is contract address only — tokens are matched by `<chain, contractAddress>`
  against a signed, versioned canonical allowlist; anything else is `UNKNOWN_TOKEN`, surfaced loudly. A
  token symbol is never used to decide identity.

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql:159-169` — `token_allowlist`
  table already shipped (T02, frozen): `id, chain, contract_address, symbol, decimals, version,
  signature, created_at`, `UNIQUE (chain, contract_address, version)`.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql`,
  `V3__crypto_app_outbox_grant.sql`, `V4__crypto_app_provider_health_grant.sql` — **confirmed (Phase 0)
  to grant `crypto_app` nothing at all on `token_allowlist`.** This task needs a new migration
  (`V5__...`) granting at least `SELECT`.
- `services/crypto/src/main/java/com/themistra/crypto/quorum/QuorumOutcome.java` (T09) — `enum {
  AGREED, HELD, UNKNOWN_TOKEN }`. T09's own Javadoc explicitly names task 11 (this task) as the future
  producer of `UNKNOWN_TOKEN`. Whether `TokenValidator` returns this type directly or its own result
  type is a Phase 2 decision (see Open Questions).
- `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` (T04) — relevant only if
  seeding happens via application code rather than a DML migration.

**New, per design.md §6 (`token/` package):**
- `token/TokenAllowlist.java` / `token/TokenAllowlistRepository.java` — maps `token_allowlist`.
- `token/TokenValidator.java` — address-only identity lookup, `UNKNOWN_TOKEN` classification.

**Explicitly NOT in this task's scope, despite being listed under `token/` in design.md §6:**
- `token/AddressValidator.java` (EIP-55/Base58Check, L8) — task 12.
- `token/AddressPoisoningDetector.java` (prefix/suffix similarity, L9) — task 13.

## Dependencies

- `chain.token_allowlist` (T02, fixed schema; grant added by this task's own new migration).
- `quorum.QuorumOutcome` (T09) — candidate reuse for the `UNKNOWN_TOKEN` result, decision deferred to
  Phase 2.
- No new `@ConfigurationProperties` record confirmed yet — depends on the migration-vs-config seeding
  decision (Phase 2).
- No contract file (`contracts/api/crypto-internal.yaml`, `contracts/events/chain/*`) is touched by
  this task — `TokenValidator` introduces no HTTP endpoint and emits no event; the header's listed
  contracts are the section's general scope, not specific to this task (same conclusion every prior
  token/quorum/provider task already reached for the identical header list).

## Acceptance Criteria

- **AC1 (R13, L7).** A transfer's token is identified solely by `<chain, contractAddress>`; no code
  path in `TokenValidator` accepts or branches on a symbol to determine identity.
- **AC2 (R14, L7).** A `<chain, contractAddress>` pair not present in `token_allowlist` (for whichever
  version(s) are considered active — Phase 2 to define) is classified `UNKNOWN_TOKEN`, not silently
  accepted or guessed.
- **AC3 (schema-conformance).** `TokenAllowlist` maps every column of `token_allowlist` exactly as
  shipped, with `symbol` explicitly documented/enforced as display-only (never consulted for identity
  decisions, per L7 and the column's own `-- display only, never used for identity` comment).
- **AC4 (migration).** A new migration grants `crypto_app` the privileges `token_allowlist` actually
  needs (at minimum `SELECT`; `INSERT` too if seeding happens via application code rather than DML),
  without modifying `V1`-`V4`.
- **AC5 (seeding).** The signed, versioned per-chain USDT/USDC allowlist entries are present after this
  task's own migration/seed step runs — exact mechanism (migration DML vs. config-driven) is a Phase 2
  decision.

## Tests required

- `shouldIdentifyTokenByContractAddressNotSymbol` (package.md §8, named) — AC1.
- `shouldSurfaceUnknownTokenForNonAllowlistedContract` (package.md §8, named) — AC2.
- A test asserting a symbol is never used to resolve identity — e.g., two different `(chain,
  contractAddress)` entries with the same `symbol` are still treated as distinct, and a lookup by
  symbol alone (if such a method existed) is not exposed at all (AC1).
- A test (Docker-gated, mirroring prior tasks' integration tests) proving the migration/seed step
  actually populates `token_allowlist` with the expected per-chain entries after Flyway migrate, and
  that `crypto_app`'s new grant is sufficient to read (and, if applicable, write) it (AC4, AC5).

## Open Questions

No blockers cited in `package.md` §11 apply directly to this task (Q1-Q8 cover provider selection,
screening, Tron confirmation semantics, watcher transport, the anchor endpoint, KMS key spec, and the
agents.md follow-up — none address the token allowlist's seeding mechanism, signature verification, or
versioning semantics). Three items are genuine gaps the spec's author never addressed anywhere,
requiring an implementer-proposed resolution (Phase 2, subject to Kimi challenge + human sign-off)
rather than a blocking question back to the author, matching the precedent T08/T09/T10 already set for
similarly under-specified areas:

- **Seeding mechanism (migration DML vs. config-driven) is unresolved** — the task statement itself
  offers both ("via migration/config"), and no signing key/mechanism is named anywhere to actually
  produce a real `signature` value, so any concrete implementation will need placeholder/local-profile
  values regardless of which mechanism is chosen (mirroring `KmsProperties`'s own
  `local-only-fake-kms-key-id` precedent).
- **No signature-verification mechanism is specified** — whether `TokenValidator` re-verifies
  `signature` at read-time, or the column is a passive, seed-time-only provenance artifact, is
  undecided.
- **Which allowlist version(s) are "active" for a lookup is unresolved** — `UNIQUE (chain,
  contract_address, version)` permits multiple versions of the same contract to coexist; no rule for
  "current" is stated anywhere.
