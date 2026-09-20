# crypto · T11 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`), package-by-feature under
`com.themistra.crypto`, owning the `chain` Postgres schema exclusively (Flyway, DDL-only, immutable-once-
merged migrations, `V1`-`V4` so far). Config is flat `application.properties` bound to validated
`@ConfigurationProperties` records (`common/config/`). Security/outbox/events are not directly relevant
to this task — `TokenValidator` introduces no HTTP endpoint and (per the task statement's own scope)
does not itself emit any event; `UNKNOWN_TOKEN` classification is a value the validator produces for
its caller to act on, not something this task publishes to Kafka.

## 2. Existing code this task touches

**Already exists, consumed but not modified:**
- `chain.token_allowlist` table (`V1__chain_baseline.sql:159-169`, frozen T02): `id, chain,
  contract_address, symbol (display-only, never identity), decimals, version, signature, created_at`,
  `UNIQUE (chain, contract_address, version)`. The table's own comment: "Signed, versioned canonical-
  token allowlist (L7). Seeded, never runtime-edited." The `UNIQUE` constraint's inclusion of `version`
  means the SAME `(chain, contract_address)` can appear under multiple version numbers over time — how
  the application decides which version(s) are "currently active" for a lookup is not specified
  anywhere in the schema or spec text (see §5).
- **Confirmed by rereading `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`, and
  `V4__crypto_app_provider_health_grant.sql` in full: `crypto_app` has NO grant whatsoever on
  `token_allowlist`** — the same gap pattern T10 found and closed for `provider_health`. This task will
  need its own new migration (`V5__...`, since `V1`-`V4` are immutable per agents.md) granting at least
  `SELECT` — the table is seeded, never runtime-edited by application code (package.md §10: "never
  hand-edited at runtime"), so unlike `provider_health`'s `INSERT, SELECT, UPDATE`, this table likely
  needs read access only, plus however seeding itself is done (see below).
- `quorum.QuorumOutcome` (T09, frozen): `enum QuorumOutcome { AGREED, HELD, UNKNOWN_TOKEN }`. T09's own
  Javadoc on this enum states explicitly: `"UNKNOWN_TOKEN — contract address not on the signed
  allowlist (L7, R14); this task [T09] never produces this value (token allowlist validation is task
  11)"` — strong evidence that `TokenValidator`'s own "non-allowlisted" outcome is meant to map to (or
  possibly directly return) this same, already-shipped `QuorumOutcome.UNKNOWN_TOKEN` value, not a new,
  parallel enum. Whether `TokenValidator` returns `QuorumOutcome` directly (a `token/` → `quorum/`
  import) or its own result type that some other, later caller maps to `QuorumOutcome.UNKNOWN_TOKEN` is
  not settled by anything read so far — a genuine design decision for Phase 1/2, not to be assumed here
  (see §5).
- `common/ClockConfig.java` (T04) — injectable `Clock`, the established pattern for any timestamped
  field this task's own code might set (relevant only if seeding happens via application code rather
  than a DML migration — see §5).

**New in this task (per design.md §6 `token/` package map):**
- `TokenAllowlist.java` / `TokenAllowlistRepository.java` — maps `token_allowlist`.
- `TokenValidator.java` — address-only identity lookup, `UNKNOWN_TOKEN` classification (R13/R14).

**Explicitly NOT in this task's scope, despite being listed under `token/` in design.md §6:**
- `AddressValidator.java` (EIP-55/Base58Check, L8) — task 12.
- `AddressPoisoningDetector.java` (prefix/suffix similarity, L9) — task 13.

## 3. Established patterns to follow

- **Persistence (JPA):** every append-only entity so far (`OutboxEvent`, `Observation`,
  `QuorumDecision`) follows the same shape — protected no-arg constructor, static `create(...)` factory,
  no setters, getters only. `token_allowlist` has no `updated_at`/mutable-flag column and its own
  comment says "never runtime-edited" — so `TokenAllowlist` is very likely append-only like the other
  three, not update-in-place like T10's `ProviderHealth`, though how rows get there at all (migration
  DML vs. application-code seeding) is still open (see §5).
- **Repositories:** package-private `interface X extends JpaRepository<Entity, Long>`, derived-query
  finders — established convention.
- **Grant-per-table migrations:** T10 set a fresh, direct precedent for exactly this task's own likely
  need — a table shipped in `V1` with no `V2` grant gets its own later, narrowly-scoped migration
  (`V4__crypto_app_provider_health_grant.sql`) rather than modifying `V1`/`V2`.
- **Config:** flat `application.properties`, validated `@ConfigurationProperties` — no existing record
  covers token-allowlist contents; whether one is needed at all depends on the migration-vs-config
  seeding decision (see §5).
- **Package boundaries:** `token/` is a sibling of `quorum/`, `observation/`, `adapter/`, `provider/`
  per design.md §6. T09 already established a precedent for a sibling feature package reusing another's
  small, stable enum (`quorum.QuorumDecision` reusing `observation.FactType`) when the alternative
  (duplicating the type) was worse — directly relevant to the `QuorumOutcome.UNKNOWN_TOKEN` question
  above.

## 4. Testing conventions

- Plain JUnit 5, fixed `Clock` where timestamps are set by application code, no real network/DB in unit
  tests.
- Testcontainers-Postgres integration tests exist for every prior task's persistence layer
  (`OutboxTransactionIntegrationTest`, `ObservationRepositoryIntegrationTest`,
  `QuorumDecisionRepositoryIntegrationTest`, `ProviderHealthRepositoryIntegrationTest`), all mirroring
  one fixed pattern (narrow `@Configuration`, static `@Container PostgreSQLContainer`, Flyway migrate +
  `crypto_app` password in `@BeforeAll`). `TokenAllowlistRepository` would very likely get the same
  treatment, plus (if seeding is migration-based) a test proving the seeded rows are actually present
  and correctly shaped after Flyway migrate.
- Docker has been unavailable throughout every prior task in this session — every Testcontainers-backed
  test compiles and is structurally sound but has never actually executed here; a pre-existing
  environment limitation, not something to fix as part of this task.
- Named test convention: `shouldIdentifyTokenByContractAddressNotSymbol` and
  `shouldSurfaceUnknownTokenForNonAllowlistedContract` (package.md §8) are written verbatim as test
  method names, per every prior task's own convention.

## 5. Known gaps / unknowns

- **How the allowlist is actually seeded is not settled.** The task statement itself says "via
  migration/config" (either/or wording) — package.md §10 similarly says "seeded by a companion
  migration/config." Nothing pins down which, or what the actual per-chain USDT/USDC contract
  addresses, decimals, symbols, or (especially) real signature values would be for a "local"/test
  profile, since no signing key or mechanism is described anywhere in this spec package (unlike the KMS
  attestation key, which at least has a named mechanism even if unresolved — Q7). A DML-migration
  approach (mirroring how schema itself is versioned) seems more consistent with this codebase's
  existing patterns than inventing a structured nested-list `@ConfigurationProperties` shape for
  potentially-many allowlist rows (the awkwardness `ProviderProperties`'s own nested
  chains/providers/entries structure already exhibits), but this is not decided here — Phase 2 must
  choose and justify.
- **No signature-verification mechanism is specified.** The `signature TEXT NOT NULL` column exists,
  but I do not know whether `TokenValidator` (or anything else in this task's scope) is expected to
  verify that signature against a known public key at read-time, or whether the signature is purely an
  out-of-band provenance/audit artifact populated once at seed time and never re-checked by the running
  application. No public key, algorithm, or verification requirement is named anywhere in `package.md`,
  `requirements.md`, or `design.md` for this specific signature (distinct from the KMS attestation-key
  signing path, which is a completely separate concern — task 20+).
- **Which allowlist version(s) are "active" for a lookup is not specified.** `UNIQUE (chain,
  contract_address, version)` permits the same contract under multiple version numbers over time; no
  spec text says whether a lookup should consider only the latest version per chain, the latest version
  overall, or all versions ever seeded as simultaneously valid.
- **Whether `TokenValidator`'s result is (or maps to) `quorum.QuorumOutcome.UNKNOWN_TOKEN` directly is
  not settled.** T09's own Javadoc strongly implies this is the eventual destination of a non-
  allowlisted classification, but nothing read so far describes the exact call shape or which package
  should own that mapping. Phase 1/2 must resolve this explicitly, including whether `token/` importing
  `quorum.QuorumOutcome` is the right direction (no cross-import currently exists between these two
  sibling packages) or whether `TokenValidator` should return its own, `quorum`-agnostic result type.
