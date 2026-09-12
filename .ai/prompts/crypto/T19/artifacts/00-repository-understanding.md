# crypto · T19 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/crypto` is a Spring Boot 3.5.4 / Java 21 Maven module (`com.themistra.crypto`), package-by-feature
under one package per module (`adapter`, `provider`, `quorum`, `finality`, `observation`, `watch`, `reorg`,
`token`, `events`, `common`). It owns one Postgres schema (`chain`), migrated via Flyway (`V1`…`V8`, immutable
once merged — new work is always a new `V<n>` file, never an edit). `V1__chain_baseline.sql` is a verbatim,
frozen transcription of `design.md` §4c's DDL, including a table this task's own scope activates:
`chain.screening_results` (already exists in the schema, unused by any code so far). The runtime role
`crypto_app` (created in `V2`) is least-privilege by construction — it owns nothing, is only ever a grantee,
and each table's grant is added in its own later migration as the feature that needs it lands (`V4` for
`provider_health`, `V5` for `token_allowlist`, `V6`/`V7`/`V8` for watch/cursor tables). **`V2` did not grant
anything on `screening_results`** — only `observations`, `attestations`, `quorum_decisions` got `INSERT, SELECT`
there. This task will need its own grant migration, following the `V5` pattern exactly.

Every emitted domain fact goes through the transactional outbox (`events.OutboxPublisher` → `chain.outbox` →
`OutboxRelay` → Kafka), never a direct producer call. Internal endpoints require a service-to-service JWT
with `internal.crypto:write` scope (`common.ResourceServerConfig`), validated as an OAuth2 resource server;
the public-endpoint allowlist is `common.PublicEndpoints`. Errors are RFC 9457 `problem+json`
(`common.ApiExceptionHandler`). `common.ClockConfig` supplies the single injectable `Clock` bean used
everywhere instead of `java.util.Date`/wall-clock calls, so tests can fix time deterministically.

## 2. Existing code this task touches

- **Already exists, task must not modify:** `chain.screening_results` table (`V1__chain_baseline.sql:86-97`)
  — columns `chain`, `address`, `tx_hash` (nullable), `outcome` (`CLEARED`/`BLOCKED`/`ERROR` check
  constraint), `provider`, `raw_response` (`JSONB`), `screened_at`. `events.OutboxPublisher` (no screening
  event is required by this task's scoped requirement, R21 — screening persistence only, `BLOCKED` wiring
  into `/attest`'s HTTP response is task 21's scope, not this one). `common.ClockConfig`.
- **New in this task (per `design.md`'s own package map, `screening/`):**
  - `screening/ScreeningClient.java` — interface; a fail-closed stub implementation until Q2 (vendor
    choice) is answered.
  - `screening/ScreeningResult.java` — JPA entity mapping `chain.screening_results` exactly as already
    shipped.
  - `screening/ScreeningResultRepository.java` — `JpaRepository`.
  - A new Flyway migration `V9__crypto_app_screening_results_grant.sql` granting `crypto_app` whatever
    privileges the persistence path needs on `chain.screening_results` (append-only shape, mirroring `V5`'s
    reasoning for `token_allowlist` unless the stub's own read pattern requires more — to be decided in
    Phase 2/5, not assumed here).
- **Not this task's scope (later tasks, do not touch):** `attest/` package (does not exist yet — task 21
  wires the real gate: quorum + finality + **screening** → sign/`BLOCKED`/`409`). `KmsSigner` (task 20).
  The real screening vendor adapter (blocked on Q2 per `package.md` §7 — "Wire the real vendor once Q2 is
  answered" is explicitly this task's own stated boundary, not a TODO to resolve now).

## 3. Established patterns to follow

- **Entity shape** (`TokenAllowlist`, `Observation`, `ChainCursor`): package-private or `protected`
  no-arg constructor for JPA only, a public static `create(...)` factory doing `Objects.requireNonNull`
  on every reference-typed parameter, plain accessor methods (no `Lombok`), no setters unless the domain
  genuinely needs a later mutation (`ChainCursor.advanceTo`/`invalidate` are the only precedent for that).
- **JSON column mapping**: `@JdbcTypeCode(SqlTypes.JSON)` for a `JSONB` column holding a pre-serialized
  `String` (`Observation.rawResponse`) — the caller serializes with `ObjectMapper` before calling into the
  entity; the entity/repository layer never touches a provider-specific Java type directly. This is the
  exact shape `screening_results.raw_response JSONB` will need.
- **Repository visibility**: package-private interface (`ObservationRepository`), `JpaRepository<T, Long>`,
  only the finder methods actually used are declared (no speculative query methods).
- **Migration/grant split**: `V1` is DDL-only and frozen; every grant is its own later, additive migration
  named for the table and consumer it unblocks, with a comment explaining exactly why V2 didn't already
  cover it and what privilege level is chosen and why (append-only vs. update-in-place). Plain `GRANT` is
  idempotent in Postgres — no `IF NOT EXISTS` guard needed, matching `V4`/`V5`/`V6` precedent.
- **Fail-closed as a first-class design shape**: `L12` calls for a fail-closed **stub** now, with the real
  vendor wired later behind the same interface — this mirrors `finality.FinalityPolicy`'s own
  interface-with-per-chain/per-stage-implementation shape (`design.md`'s package map lists `ScreeningClient`
  directly analogous to `FinalityPolicy`).

## 4. Testing conventions

- Plain JUnit unit tests with a fixed `Clock` (`Clock.fixed(...)`) for anything time-stamped
  (`screened_at`), no wall-clock reads in tests.
- Testcontainers (Postgres + Kafka) only for true integration tests (repository/migration-level); this
  task's own persistence path can likely be proven with a Testcontainers-backed
  `ScreeningResultRepository` round-trip test, mirroring `TokenAllowlistTest`/`ChainCursorTest`'s own
  split between plain-entity unit tests and repository integration tests.
  the `*IntegrationTest` suffix and Testcontainers convention already used for
  `ObservationRepositoryIntegrationTest`/`ProviderHealthRepositoryIntegrationTest`/
  `TokenAllowlistRepositoryIntegrationTest` (these four are the pipeline's currently-known, disclosed,
  pre-existing failing integration tests as of T18 — unrelated to this task, do not attempt to fix them).
- Module-boundary tests: a plain source-scan test per package (`WatchModuleBoundaryTest`,
  `ReorgModuleBoundaryTest`), not ArchUnit for this narrow shape (ArchUnit itself is used for the
  cross-cutting `L11`/`L15` rules per `agents.md` and `package.md` §9's checklist, e.g.
  `shouldPreventCrossModuleEntityImports`). A `screening` module-boundary test is the expected shape if
  `ScreeningClient`/`ScreeningResult` end up needing to guard against a cross-module entity import — to be
  confirmed once the actual dependency shape is designed (Phase 2/5).
- No real vendor call is ever made in tests or CI (`agents.md` — "real RPC providers are never called in
  tests or CI" — the same posture applies to the fail-closed stub's own tests: it must never attempt an
  actual network call).

## 5. Known gaps / unknowns

- **Q2 (screening vendor) is explicitly unanswered** — `package.md` §7 lists it as an open blocker
  ("Chainalysis, TRM Labs, or Elliptic — chosen on pricing... Until chosen, screening is behind an
  interface with a fail-closed stub"). This task's own task-statement text ("Wire the real vendor once Q2
  is answered") confirms the stub is the entire deliverable here — I do not know which vendor will
  eventually be chosen, and no code in this task should assume one.
- **Q3 (fail-open vs. fail-closed on screening outage) is asked in `package.md` §7 but `design.md` §4a-L12
  already states the LOCKED, non-negotiable answer**: "If the screening API is unreachable, attest fails
  closed (no signature) unless the author overrides via Q3." I do not know whether a prior task's author
  already exercised that override anywhere in the repo — I found no such override in the code searched so
  far (`attest/` doesn't exist yet), so I treat fail-closed as still fully in force for this task.
  Because the `AttestationService` gate itself is task 21's scope, this task's own fail-closed behavior is
  scoped to what `ScreeningClient`'s stub returns/throws — the exact shape (a `BLOCKED`/`ERROR` result vs.
  a checked exception) is a Phase 2 design decision, not yet made.
- **Whether `screening_results` rows are written by `ScreeningClient` itself or by a caller layer** — the
  package map lists `ScreeningResult.java` / `ScreeningResultRepository.java` alongside `ScreeningClient`
  in the same `screening/` package, but does not say which class owns the write. I do not know this yet;
  it is a Phase 2 design decision.
- **Exact grant level needed on `chain.screening_results`** — depends on whether this task ever needs to
  read back a prior screening result (e.g., to avoid re-screening the same address) or only ever inserts.
  I do not know this yet without the Phase 2 design.
