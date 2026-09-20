# crypto · T09 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`) in the `checky-pro`
multi-module Maven build, package-by-feature under `com.themistra.crypto`. It owns the `chain` Postgres
schema exclusively (Flyway, DDL-only migrations, immutable once merged). Persistence uses plain JPA for
simple find/save. Domain state changes that need to reach Kafka go through a transactional outbox
(`events/OutboxEvent` + `OutboxPublisher` + `OutboxRelay`) — never a direct producer call — but this is
not universal: `observations` and (per schema) `quorum_decisions` are append-only tables written
directly via their own repositories, not through the outbox, because neither task 8 nor (per its own
task statement) task 9 emits a Kafka event. Security is OAuth2-resource-server-based
(`common/ResourceServerConfig`, `common/PublicEndpoints`), not yet exercised by anything in the `quorum`
module since T09 introduces no HTTP endpoint. Config is flat `application.properties` bound to validated
`@ConfigurationProperties` records under `common/config/` (`FinalityProperties`, `KmsProperties`,
`ProviderProperties`, `ScreeningProperties`, `SnapshotProperties`) — no property relevant to quorum
thresholds exists yet (2-of-3 is a fixed rule per L1, not configurable, so this is expected, not a gap).

## 2. Existing code this task touches

**Already exists (T01–T08), consumed but not modified:**
- `chain.quorum_decisions` table (`V1__chain_baseline.sql:121-133`) — `id, chain, tx_hash, fact_type,
  outcome (CHECK IN ('AGREED','HELD','UNKNOWN_TOKEN')), agreeing_count, provider_count, decided_at`,
  with `UNIQUE (chain, tx_hash, fact_type)`. `crypto_app` role grant is `INSERT, SELECT` only on this
  table (`V2__crypto_app_role_and_grants.sql:34`) — same append-only shape as `observations`, no
  `UPDATE`/`DELETE` possible at the DB layer. The `UNIQUE` constraint plus no-`UPDATE` grant together
  imply a quorum decision, once written for a given `(chain, tx_hash, fact_type)`, can never be revised
  by this service's own runtime role — a structural fact this task's design (Phase 2+) must reconcile
  with any notion of "re-evaluating" a fact.
- `observation/Observation.java` / `ObservationRepository.java` (T08) — the verbatim per-provider answer
  log this task's quorum evaluation is conceptually downstream of. Design.md §4c states the `quorum`
  module "fans a fact out across the provider adapters for a chain and compares" — i.e., quorum
  evaluation operates on live per-provider answers (from `ChainAdapter`s), with `Observation` as the
  parallel, independent verbatim record (T08, already required to be written first, R4/L3) — not
  necessarily reading back rows from `ObservationRepository` as its own input. Phase 1/2 must confirm
  the exact input shape from design.md/package.md rather than assume.
- `adapter/ChainAdapter.java`, `adapter/Chain.java`, `adapter/model/*` (T05), `adapter/eth/EthereumAdapter.java`
  (T06), `adapter/tron/TronAdapter.java` (T07) — the provider-answer sources quorum fans out across, per
  design.md §4c. `FactType` enum (`observation/FactType.java`, T08) — `EXISTENCE, AMOUNT, TOKEN,
  CONFIRMATIONS, FINALITY` — is very likely the same fact-type vocabulary `QuorumDecision.factType` maps
  to (`quorum_decisions.fact_type VARCHAR(32)`), mirroring `Observation.factType`'s own converter
  pattern (Phase 2 to confirm whether `QuorumDecision` reuses `observation.FactType` directly or needs
  its own type — `L15`/no-cross-feature-import concerns from T08 may bear on this, since `quorum` and
  `observation` are listed as sibling top-level packages in design.md §6, not one nested in the other).
- `common/ClockConfig.java` (T04) — injectable `Clock` bean, used by every timestamped entity so far
  (`OutboxEvent.createdAt`, `Observation.observedAt`); `QuorumDecision.decidedAt` will very likely follow
  the same pattern.
- `events/OutboxEvent.java` / `OutboxEventRepository.java` / `OutboxPublisher.java` / `OutboxRelay.java`
  (T04) — the established transactional-outbox pattern, **not directly used by this task**: T09's own
  task statement and R2 ("SHALL NOT emit a downstream event for it" on HELD) scope this task to
  persistence + alerting only, no Kafka event on either AGREED or HELD (event emission for `chain.tx.*`
  is task 15+, per `tasks.md`).

**New in this task (per design.md §6 package map, `quorum/`):**
- `QuorumEvaluator.java` — "pure 2-of-3 logic" (task statement) — `AGREED` needs ≥2 matching answers;
  disagreement → `HELD`.
- `QuorumOutcome.java` — enum `AGREED, HELD, UNKNOWN_TOKEN` (design.md §4c, verbatim) — `UNKNOWN_TOKEN`
  is explicitly out of this task's scope (`R14`/task 11, token allowlist), so T09 likely only ever
  produces `AGREED`/`HELD` at runtime, but the enum itself (design.md-frozen, `L`-adjacent shape) should
  not be redefined with fewer values just because this task doesn't populate the third.
- `QuorumDecision.java` / `QuorumDecisionRepository.java` — persists the per-fact outcome, mapping
  `quorum_decisions` exactly as shipped.
- `HeldFactAlerter.java` — "ops alert on HELD" (design.md §6, L2). **No alerting/paging integration
  exists anywhere in this codebase yet** (no PagerDuty/Opsgenie/Slack client, no `MeterRegistry`/
  Micrometer usage anywhere under `services/crypto/src/main`) — agents.md's Observability section states
  "provider-disagreement rate are paged metrics" as a platform-level aspiration, not something already
  wired. This is a known gap Phase 1/2 must resolve explicitly (see §5 below), not assume.

## 3. Established patterns to follow

- **Persistence (JPA):** every append-only entity so far (`OutboxEvent`, `Observation`) follows the same
  shape — protected no-arg constructor for JPA, a public static `create(...)` factory for production
  construction, effectively-final fields, getters only, no setters. `QuorumDecision` should mirror this
  exactly (T08's own frozen-brief amendment #9 explicitly named `OutboxEvent.create(...)` as the pattern
  to mirror; the same reasoning applies here given `quorum_decisions`' identical grant shape).
- **Repositories:** package-private `interface X extends JpaRepository<Entity, Long>`, derived-query
  finders only (`ObservationRepository.findByChainAndTxHashAndFactType`).
- **Immutability enforced by DB grant, not just convention:** both `observations` and `quorum_decisions`
  have `INSERT, SELECT`-only grants — any entity design that requires an `UPDATE` (e.g., "flip HELD to
  AGREED later") will fail at the database layer, not just violate a style rule.
- **Fixed `Clock`, no `Instant.now()` inline** — `common/ClockConfig`, consumed via constructor
  injection everywhere it's used so far.
- **Config:** flat `application.properties`, validated `@ConfigurationProperties` records; no such
  record exists yet for anything quorum-specific, and the 2-of-3 threshold is a LOCKED, non-tunable rule
  (L1: "not a tunable that can be disabled") — so a new `@ConfigurationProperties` record is likely NOT
  needed for the threshold itself, though Phase 1 should confirm there's no other config surface implied
  (e.g., alerting destination).
- **Error handling:** RFC 9457 `application/problem+json` for HTTP boundaries (agents.md) — not directly
  relevant to T09, which introduces no controller/endpoint.
- **Package boundaries:** package-by-feature, ArchUnit enforces `api → application → domain` within a
  module and forbids cross-module entity imports; `quorum/` is a sibling of `observation/`, `adapter/`,
  `provider/`, `finality/`, `watch/` per design.md §6 — no existing ArchUnit test file was found under
  `services/crypto/src/test` in this repository search, so whether that enforcement is already coded or
  still aspirational is unconfirmed (see §5).

## 4. Testing conventions

- Plain JUnit 5 (`@ExtendWith(MockitoExtension.class)` where mocks are needed), fixed `Clock` via
  `Clock.fixed(...)`, no real network/DB in unit tests.
- `FakeChainAdapter` (T05) is the established scripted-provider-answer test double — "can agree,
  disagree, lag, and reorg" per its own task statement — very likely the mechanism T09's own exhaustive
  agreement-matrix tests should script against, rather than raw manually-constructed value lists,
  matching the established test-double precedent (Phase 1/2 to confirm exact usage against the pure
  `QuorumEvaluator` signature once that signature is designed).
- Testcontainers-Postgres integration tests exist for every prior append-only-entity task
  (`OutboxTransactionIntegrationTest` T04, `ObservationRepositoryIntegrationTest` T08) mirroring one
  fixed pattern: narrow hand-built `@Configuration` (`@EntityScan`/`@EnableJpaRepositories` scoped to
  just the entity/repository under test, not the full app context), static `@Container
  PostgreSQLContainer`, `@DynamicPropertySource`, `@BeforeAll` Flyway migrate + `ALTER ROLE crypto_app
  PASSWORD ...`. `QuorumDecisionRepository` should very likely get the same treatment, including a test
  proving the DB-enforced no-UPDATE/DELETE grant (`ObservationRepositoryIntegrationTest`'s own
  `repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel` precedent).
- Docker has been unavailable throughout every prior task in this session — every Testcontainers-backed
  test written so far compiles and is structurally sound but has never actually executed here; this is a
  pre-existing environment limitation, not something to fix as part of this task.
- Named test convention: the three `package.md` §8 tests for this task
  (`shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree`,
  `shouldHoldFactAndAlertWhenProvidersDisagree`, `shouldNeverAutoResolveDisagreementInPayersFavor`) are
  written verbatim as test method names, per every prior task's own convention (T08's
  `shouldLogEveryProviderResponseVerbatimToObservationLog`).

## 5. Known gaps / unknowns

- **`HeldFactAlerter`'s actual alerting mechanism.** No PagerDuty/Opsgenie/webhook/email client exists
  anywhere in this codebase. I do not know whether "ops-alerted" (L2) is meant to be satisfied by a
  structured, distinctly-leveled log line (the pattern `ObservationSnapshotStore` already uses for its
  own "logged distinctly" S3-failure case, T08) plus a Micrometer counter for the "paged metrics"
  agents.md mentions, or whether an actual external alerting integration is expected in this task's own
  scope. This must be resolved explicitly in Phase 1/2 (spec extraction / design), not assumed.
- **Exact `QuorumEvaluator` input shape.** design.md §4c says the quorum module "fans a fact out across
  the provider adapters ... and compares," which suggests direct `ChainAdapter` answers as input, but the
  task statement calls it "pure 2-of-3 logic," which suggests it more likely takes an already-fetched
  collection of values (e.g., `List<T>` or a small per-provider-answer value type) and returns an
  outcome, with some other component (not named in this task) responsible for the actual fan-out I/O
  across adapters. I do not know which; Phase 1 must extract this precisely from `package.md`/`design.md`
  rather than invent a shape.
- **Whether `QuorumDecision.factType` reuses `observation.FactType` or needs its own type.** Both
  `observations.fact_type` and `quorum_decisions.fact_type` are `VARCHAR(32)` with the same five-value
  vocabulary implied by context, but `quorum/` and `observation/` are sibling packages (design.md §6),
  and T08's own `FactType.DbConverter` was deliberately not `autoApply` "so it can never accidentally
  attach to some other, unrelated enum-typed column later" — suggesting reuse across packages may not
  have been intended. Phase 1/2 must decide and justify, not default silently either way.
- **Whether ArchUnit module-boundary enforcement already exists in this codebase.** No ArchUnit test
  file was found under `services/crypto/src/test` in this session's searches so far; agents.md states
  the rule as a standing constraint but I do not know if it's currently enforced by an actual test or
  still aspirational for this early in the build-out. Not this task's job to add if missing (task 1's
  "skeleton" task, already long complete, would have been the natural place), but worth flagging if the
  design phase considers adding a `quorum`-scoped ArchUnit rule.
- **"Agreement" definition for non-trivial fact types.** R1 lists `tx existence, amount, token contract,
  confirmations, finality status` as the five fact kinds needing quorum. "≥2-of-3 agree" is
  straightforward for `EXISTENCE` (boolean-like) but I do not know yet what "agree" means precisely for
  `AMOUNT` (exact numeric equality only, given `agents.md`'s `NUMERIC`/`BigDecimal`-only money rule?) or
  `CONFIRMATIONS` (an exact match, given providers poll at different times and confirmation counts are
  inherently time-varying?) — this is very likely addressed in `package.md`'s fuller narrative or
  `design.md`, to be extracted precisely in Phase 1, not guessed here.
