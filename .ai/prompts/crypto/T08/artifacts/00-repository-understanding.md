# crypto · T08 · Phase 0 — Repository Understanding

## 1. Architecture summary of this service (as it exists today)

Seven tasks shipped: T01 (skeleton), T02 (`chain` schema — the full DDL for this service's entire
data model, including tables no task has consumed yet), T03 (`common/` config + resource server), T04
(`events/` outbox), T05 (`adapter/` contract + fakes), T06 (`EthereumAdapter`), T07 (`TronAdapter`).

**T08 is the first persistence-writing task since T02/T04**, and the first task whose target table
(`chain.observations`) already exists in full, DDL-complete form — unlike T06/T07, which designed
their own supporting types from scratch, this task's job is to build the JPA/S3 layer *onto* an
already-fixed shape, not to design one. It is also the first task to need a genuinely new external
dependency: no AWS S3 SDK module is present in `pom.xml` yet (only `software.amazon.awssdk:kms`,
scoped by the pom's own comment to "the future attest module's KmsSigner ... kms:Sign only" —
`ObservationSnapshotStore` needs `software.amazon.awssdk:s3` specifically, a separate artifact, not an
extension of the existing KMS one).

## 2. Existing code this task touches — what's already there vs. new

**Already exists (context, extend/consume, do not modify without cause):**
- **`chain.observations`** (`V1__chain_baseline.sql:24-34`) — the exact table `Observation`/
  `ObservationRepository` must map onto: `id BIGINT GENERATED ALWAYS AS IDENTITY`, `chain VARCHAR(32)`,
  `tx_hash VARCHAR(128)`, `provider VARCHAR(64)`, `fact_type VARCHAR(32)` (comment lists expected
  values — existence/amount/token/confirmations/finality — but, confirmed by reading the DDL directly
  rather than trusting recollection, there is **no DB-level CHECK constraint** on this column, unlike
  `watches.status`/`quorum_decisions.outcome`/`screening_results.outcome`/`attestations.outcome`, all
  of which do have one), `raw_response JSONB NOT NULL` (verbatim payload), `s3_snapshot_key
  VARCHAR(256)` (nullable — the WORM pointer), `observed_at TIMESTAMPTZ NOT NULL DEFAULT now()`. An
  index exists on `(chain, tx_hash, fact_type)`.
- **`crypto_app`'s grant on `chain.observations`** (`V2__crypto_app_role_and_grants.sql:34`) —
  **`GRANT INSERT, SELECT` only, no UPDATE, no DELETE**, enforced at the database privilege level (not
  just application discipline). This is a hard constraint on `Observation`'s own JPA mapping: any code
  path that causes Hibernate to issue an `UPDATE` against an already-persisted row (e.g. re-saving a
  managed/detached entity with a changed field) will fail at the database layer, not just violate a
  convention.
- **`SnapshotProperties`** (`common/config/SnapshotProperties.java`, T03) — **this task is its first
  real consumer**, mirroring T06's relationship to `ProviderProperties`. Already shipped, tested,
  generic shape: `bucket`, `prefix`, `region` (all `@NotBlank`), no credentials (External Secrets
  Operator / AWS SDK's own credential chain, L13, per the class's own Javadoc). `local` profile fixture
  values already exist in `application.properties` (fake bucket/prefix, `us-east-1`).
- **`OutboxEvent`/`OutboxEventRepository`** (`events/`, T04) — the established JPA entity/repository
  pattern this service already uses: `protected` no-arg constructor for JPA, a static factory method
  for construction (never a public setter-driven builder), `@JdbcTypeCode(SqlTypes.JSON)` for the JSONB
  column, `@PrePersist` as a Clock-discipline fallback only (agents.md: inject `Clock`, don't call
  `Instant.now()` directly in normal code paths), package-private `Repository` interface extending
  `JpaRepository`. `OutboxEvent` still has one mutation method (`markPublished`) because outbox rows
  *do* get updated later; `Observation` has no analogous need — nothing about it ever changes after
  insert, matching the INSERT-only grant exactly.
- **`quorum_decisions`, `provider_health`** tables (`V1__chain_baseline.sql`) — exist in the DDL but
  are task 9/10's concern, not referenced by this task's own scope beyond confirming `Observation`
  doesn't need to know about them.

**New in this task (per design.md §6 file map, scoped to T08's statement):**
- `observation/Observation.java` / `observation/ObservationRepository.java` — the JPA entity + Spring
  Data repository for `chain.observations`.
- `observation/ObservationSnapshotStore.java` — the S3 WORM snapshot writer.

## 3. Established patterns to follow

- **JPA entity shape**: mirror `OutboxEvent` exactly where applicable (protected no-arg ctor, static
  factory, `@JdbcTypeCode(SqlTypes.JSON)` for `raw_response`) — but `Observation` should end up
  *simpler* than `OutboxEvent`, since it has no mutable state at all post-persist (no
  `markPublished`-equivalent method), matching the DB's own INSERT/SELECT-only grant.
- **Repository style**: package-private interface extending `JpaRepository<Observation, Long>`,
  derived-query-method finders (mirrors `OutboxEventRepository`'s
  `findByPublishedAtIsNullOrderByCreatedAtAsc`-style naming), no custom `@Query` seen anywhere yet in
  this service.
- **Config-to-client wiring**: `SnapshotProperties` → a real AWS S3 client is the same shape of
  problem T06/T07 solved for `ProviderProperties` → `Web3j`/`ApiWrapper` — a `@Configuration` class
  building the real client from already-shipped, unused-until-now config. No established precedent yet
  for AWS SDK client construction specifically in this service (KMS's own wiring, ADR-0004-scoped to
  the not-yet-built attest module, doesn't exist yet either) — this task sets that precedent, the way
  T06 set the provider-wiring precedent for chain adapters.
- **Transactional-write ordering (R4/L3's actual hard part)**: "persist verbatim ... before any quorum
  decision" is straightforward for the Postgres half (a normal JPA save inside whatever transaction
  boundary the calling code — task 9's quorum evaluator — establishes). The S3 half is not
  transactional with Postgres at all — no established pattern in this codebase yet for coordinating a
  non-transactional external write (S3 `PutObject`) with a transactional one (the `observations` row
  carrying `s3_snapshot_key`). Phase 2's own design work, not resolved here.

## 4. Testing conventions

- agents.md: Testcontainers (Postgres + Kafka) for integration tests; fake/scripted providers, never
  real RPC calls, for unit tests of adapter-shaped code. **No established convention yet for testing an
  S3 client** — `pom.xml` has no `org.testcontainers:localstack` module today, and no other task in
  this service has needed to simulate AWS infrastructure yet (KMS's own real usage is still unbuilt,
  confined to the future attest module). Whether `ObservationSnapshotStore`'s own tests should pull in
  LocalStack (real S3 API behavior via Testcontainers, matching Postgres/Kafka's own treatment as real
  infra) or mock the AWS SDK's `S3Client` entirely (matching how T06/T07 treated RPC providers as
  something to fake, never really call) is a genuine open question — Phase 1/2's to answer, not
  assumed here either way.
- `ChainBaselineMigrationIntegrationTest`/`OutboxGrantMigrationIntegrationTest` (T02) are the
  established precedent for asserting a migration's actual DDL/grants took effect via Testcontainers —
  directly relevant if this task needs an equivalent assertion for `chain.observations`' own grants
  (though those tables/grants are already tested by T02's own suite; this task likely only needs to
  test its own new Java code against the already-verified schema, not re-verify the schema itself).
- Fixed `Clock` convention (agents.md) — directly relevant: `Observation.observedAt` (or however the
  entity ends up naming it) should come from an injected `Clock`, matching `OutboxEvent.createdAt`'s
  own established discipline, not `Instant.now()` inline.
- No ArchUnit test exists yet anywhere in this service (task 25's concern, unchanged since T05/T06/T07).

## 5. Known gaps / unknowns

- **I do not know the exact transactional/ordering strategy for the S3 write relative to the Postgres
  insert.** R4/L3 require the observation to be persisted "before any quorum decision," but say
  nothing about the relative order of the S3 `PutObject` call and the Postgres `INSERT` themselves, or
  what happens if one succeeds and the other fails (a genuinely hard distributed-write problem: S3 has
  no two-phase-commit story with Postgres). Phase 2 must design this explicitly, not leave it implicit.
- **I do not know whether this task's own tests should use LocalStack (Testcontainers) or a mocked S3
  client** — see Testing Conventions above. No precedent either way exists yet in this codebase.
- **I do not know exactly what `s3_snapshot_key`'s naming/partitioning scheme should be** —
  `SnapshotProperties.prefix` exists (`chain-observations/` in the local fixture) but nothing yet
  specifies the key structure beneath that prefix (e.g. by chain/date/tx_hash, or by DB row id).
- **I do not know who calls `Observation`'s persistence path in this task's own scope** — this task
  builds the entity/repository/S3-store, but the actual "receive a provider response and persist it"
  call site is presumably task 9's (quorum evaluator) or a shared helper this task might also need to
  define. `design.md` §6's package map lists no separate "observation service" class, only the entity/
  repository/store — whether a thin coordinating service belongs in this task's own scope or task 9's
  is a Phase 1/2 scoping question.
- **"Test ordering"** (the task statement's own closing clause) is not yet interpreted — could mean
  testing that persistence happens before quorum evaluation (an ordering this task alone cannot fully
  prove, since quorum evaluation is task 9), or testing the relative order of the S3 write vs. the
  Postgres write, or something else. Phase 1 must pin this down precisely from R4/L3's actual wording
  rather than guessing.
- No named test beyond `shouldLogEveryProviderResponseVerbatimToObservationLog` (package.md §8) is
  scoped to this task per the header.

Do not design and do not extract requirements yet — that is Phase 1. This artifact stops here.
