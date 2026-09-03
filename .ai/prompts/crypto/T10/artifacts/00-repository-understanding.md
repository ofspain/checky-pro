# crypto · T10 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module (`services/crypto`), package-by-feature under
`com.themistra.crypto`, owning the `chain` Postgres schema exclusively (Flyway, DDL-only, immutable-once-
merged migrations). State changes that need to reach Kafka go through a transactional outbox
(`events/OutboxEvent` + `OutboxPublisher` + `OutboxRelay`, T04) — every domain module calls
`OutboxPublisher.publish(aggregateType, aggregateId, eventType, idempotencyKey, payload)` in the same
transaction as its own state change; `OutboxRelay` is the only Kafka producer. `EventTopics`
(`events/EventTopics.java`, T04) already maps aggregate type `"provider"` → topic
`"chain.provider.degraded"` — this mapping was added in T04, before any feature module existed to use
it, exactly mirroring how `"tx-seen"`/`"tx-confirmed"`/`"tx-finalized"`/`"tx-reorged"` are pre-mapped for
tasks not yet built either. Config is flat `application.properties` bound to validated
`@ConfigurationProperties` records (`common/config/`) — no property relevant to provider-health
thresholds exists yet. Security (`common/ResourceServerConfig`, `common/PublicEndpoints`) is not
directly relevant: T10 introduces no HTTP endpoint.

## 2. Existing code this task touches

**Already exists, consumed but not modified:**
- `chain.provider_health` table (`V1__chain_baseline.sql:52-60`, frozen T02) — `id, chain, provider,
  healthy (BOOLEAN, default TRUE), last_ok_at, last_disagreement_at, updated_at`, `UNIQUE (chain,
  provider)`. Unlike `observations`/`quorum_decisions`, this table's own comment ("Per-provider health
  for `chain.provider.degraded` (R5)") and its `updated_at`/mutable-`healthy`-flag shape strongly imply
  it is **update-in-place**, not append-only — a materially different persistence pattern from every
  entity built so far in this service (T04/T08/T09 all built append-only, `INSERT`-only entities).
  **Confirmed by reading `V2__crypto_app_role_and_grants.sql` in full (not just line 34): `crypto_app`
  has NO grant whatsoever on `provider_health`** — the file's only table-level grant statement
  (line 34) names exactly `observations`, `attestations`, `quorum_decisions`, nothing else; no other
  statement in the file touches `provider_health`, `watches`, `chain_cursors`, `token_allowlist`, or
  `screening_results`. This means the running application currently cannot `SELECT`, `INSERT`, or
  `UPDATE` `provider_health` at all — a real, load-bearing gap this task must resolve (almost certainly
  via a new `V4__...` migration adding an appropriate grant, since merged migrations are immutable per
  agents.md), not something to route around in application code.
- `events/OutboxPublisher.java` (T04) — the domain-agnostic single entry point for emitting any event,
  including this task's `chain.provider.degraded`. Its own Javadoc documents a "Partition-key
  convention" only for `chain.tx.*` events (`aggregateId` = `watchId`) — it says nothing about what
  `aggregateId` should be for a `chain.provider.degraded` event, which has no natural `watchId` (it's
  about a provider's health state per `(chain, provider)`, not a specific watch/transaction). This is an
  open question, not something to invent silently.
- `events/EventTopics.java` (T04) — `"provider"` → `"chain.provider.degraded"` already mapped; this
  task's aggregate type is therefore fixed as the string `"provider"`, not a free choice.
- `L5` (deterministic idempotency key `chain:txhash:eventtype`) — every emitted event must carry this
  format per agents.md and `OutboxPublisher`'s own required parameter, but a provider-health event has
  no natural `txHash` component (it concerns a provider's aggregate health, not one transaction). The
  literal `chain:txhash:eventtype` template does not fit this event without either omitting the
  txHash segment or substituting something else in its place — this is a genuine open question for
  Phase 1/2, not something the literal L5 text answers directly for this specific event type.
- `quorum/QuorumEvaluator.java`/`QuorumDecisionService.java` (T09) — R5's "repeatedly disagreeing with
  the quorum" condition is conceptually about a provider whose answers keep landing in the *minority*
  group across repeated `QuorumEvaluator.evaluate` calls. Neither `QuorumEvaluator` nor
  `QuorumDecisionService` currently expose per-provider identity to any caller in a way that a health
  tracker could consume directly — `QuorumDecision` only persists `agreeingCount`/`providerCount`, not
  which providers were in the minority. `HeldFactAlerter.alert` *does* receive
  `List<ProviderAnswer<T>>` (full per-provider detail) but only on `HELD`, and nothing currently
  subscribes to or intercepts that call. How this task observes "repeated disagreement" per provider is
  an open design question, not yet answered by existing code.
- `observation/Observation.java`/`ObservationRepository.java` (T08) — each `Observation` row carries a
  `provider` field; conceptually a source of "did this provider answer, and when" data, though nothing
  in T08's own scope aggregates that into a health signal.
- `adapter/ChainAdapter.java`, `adapter/Chain.java` (T05) — "unhealthy"/"lagging" (R5) most likely
  relate to whether/how promptly a given `ChainAdapter` instance responds, but no existing code in
  `adapter/` currently tracks latency, error rate, or responsiveness — `ChainAdapter` is a pure
  synchronous interface with no built-in health signal.
- `common/ClockConfig.java` (T04) — injectable `Clock`, the established pattern for every timestamped
  field so far (`last_ok_at`/`last_disagreement_at`/`updated_at` on `provider_health` all imply this).

**New in this task (per design.md §6 `provider/` package map):**
- `ProviderHealth.java` / `ProviderHealthRepository.java` — maps `provider_health`.
- `ProviderDegradedPublisher.java` — emits `chain.provider.degraded` (R5) via `OutboxPublisher`.
- `ProviderSet.java` — also listed under `provider/` in design.md §6 ("N adapters per chain — O1"), but
  `ProviderSet` is design.md's own OPEN decision O1's proposed abstraction, tied to *which 3 providers
  are wired per chain* — a concern about provider **configuration/fan-out**, not provider **health
  tracking**. T10's own task statement ("Track `ProviderHealth`; emit `chain.provider.degraded`...")
  does not mention `ProviderSet` at all. Whether `ProviderSet` is in scope for T10 or belongs to a
  different, not-yet-reached task is an open question for Phase 1 to resolve by re-reading `tasks.md`'s
  full ordered list, not assumed here.

## 3. Established patterns to follow

- **Persistence (JPA):** every entity built so far (`OutboxEvent`, `Observation`, `QuorumDecision`) is
  append-only — protected no-arg constructor, static `create(...)` factory, no setters. `ProviderHealth`
  is the **first entity in this service whose own table is designed to be updated in place**
  (`healthy`/`last_ok_at`/`last_disagreement_at`/`updated_at`, `UNIQUE (chain, provider)` — one row per
  provider, mutated over time, not a new row per observation). This is a real precedent gap: no existing
  entity in this codebase demonstrates the mutable-JPA-entity-with-setters (or equivalent
  update-in-place) pattern; Phase 2 will need to establish it fresh, including how a repository issues
  an `UPDATE` (`save()` on a managed/re-fetched entity vs. a custom `@Modifying` query) given no sibling
  pattern exists to mirror.
- **Repositories:** package-private `interface X extends JpaRepository<Entity, Long>`, derived-query
  finders — established convention, expected to carry over regardless of the append-only-vs-mutable
  question above.
- **Fixed `Clock`, no `Instant.now()` inline** — established, expected to carry over for
  `last_ok_at`/`last_disagreement_at`/`updated_at`.
- **Outbox for all Kafka publishing** — `OutboxPublisher.publish(...)` is the only sanctioned emission
  path (agents.md); this task's `ProviderDegradedPublisher` must go through it, never a direct Kafka
  producer call.
- **Config:** flat `application.properties`, validated `@ConfigurationProperties` — no existing record
  covers provider-health thresholds; if this task needs a numeric threshold (e.g., how many consecutive
  disagreements constitute "repeatedly disagreeing"), a new `@ConfigurationProperties` record may be
  needed, or a fixed, justified constant, depending on what Phase 1/2 decide — nothing in the spec
  itself numerically defines "unhealthy," "lagging," or "repeatedly disagreeing" (confirmed: no `O`-
  numbered OPEN decision in design.md §4b addresses this either).
- **Package boundaries:** `provider/` is a sibling of `quorum/`, `observation/`, `adapter/` per design.md
  §6 — cross-package reuse of `observation.FactType`'s converter already hit a package-private
  visibility wall in T09 (worked around by local duplication); a similar check should be done early in
  this task's own Phase 5/6 for anything it might want to reuse from `quorum/`.

## 4. Testing conventions

- Plain JUnit 5, fixed `Clock`, `FakeChainAdapter` (T05) for any provider-facing scripting — its own
  Javadoc explicitly states "lag" and "disagree" are not special modes but emergent from scripting
  different/stale data across instances, which is directly relevant to how this task's tests might
  simulate an "unhealthy"/"lagging" provider.
- Testcontainers-Postgres integration tests exist for every prior task's persistence layer
  (`OutboxTransactionIntegrationTest`, `ObservationRepositoryIntegrationTest`,
  `QuorumDecisionRepositoryIntegrationTest`), all mirroring one fixed pattern (narrow `@Configuration`,
  static `@Container PostgreSQLContainer`, Flyway migrate + `crypto_app` password in `@BeforeAll`).
  `ProviderHealthRepository` would very likely get the same treatment — though if the grant on
  `provider_health` really does include `UPDATE` (see §2/§5), its integration test would need to prove
  update-then-reread behavior, not the no-UPDATE/DELETE-enforcement pattern T08/T09's integration tests
  proved for their own append-only tables.
- Docker has been unavailable throughout every prior task in this session — every Testcontainers-backed
  test compiles and is structurally sound but has never executed here; a pre-existing environment
  limitation, not something to fix as part of this task.
- Named test convention: `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (package.md §8) is written
  verbatim as a test method name, per every prior task's own convention.

## 5. Known gaps / unknowns

- **Resolved during this phase (not left as speculation): `crypto_app` has zero grant on
  `provider_health`.** Confirmed by reading `V2__crypto_app_role_and_grants.sql` in full — its only
  table-level `GRANT` statement (line 34) names `observations`, `attestations`, `quorum_decisions`
  exclusively. `provider_health` needs at minimum `INSERT, SELECT, UPDATE` (it is genuinely
  update-in-place, unlike the three append-only tables) for this task to function at all. This is a real
  gap in T02's own migration set that this task must close via a new `V4__...` migration (merged
  migrations are immutable per agents.md — `V2` itself cannot be edited), not an application-code
  workaround.
- **No numeric definition anywhere in the spec for "unhealthy," "lagging," or "repeatedly disagreeing."**
  R5's own wording is qualitative only; no `O`-numbered OPEN decision in design.md §4b addresses
  provider-health thresholds (O1-O6 cover provider *set* selection, watcher transport, cursor
  granularity, screening client shape, multi-replica assignment, and an anchor-write endpoint — none of
  them provider-health scoring). This is squarely an implementer decision requiring proposal + Kimi
  challenge + human sign-off, the same shape as several under-specified decisions T08/T09 already made
  (e.g., T09's exactly-3 threshold).
- **No natural `txHash` or `watchId` for a `chain.provider.degraded` event.** L5's idempotency-key
  template (`chain:txhash:eventtype`) and `OutboxPublisher`'s documented partition-key convention
  (`aggregateId` = `watchId` for `chain.tx.*` events) both assume a per-transaction/per-watch event
  shape that a provider-health event does not naturally have. I do not know what `aggregateId` or
  idempotency-key shape is correct for this event type; Phase 1/2 must derive or propose one, not
  silently reuse the `chain.tx.*` convention as-is.
- **Whether `ProviderSet` (design.md §6, listed under `provider/`) is in this task's scope.** The task
  statement itself never mentions `ProviderSet`; O1 (provider set & quorum N per chain) reads as a
  distinct concern (provider *configuration*, not health *tracking*). I do not know whether a later
  task (not found among T01-T10 read so far) is meant to build it, or whether it was expected to be
  built earlier (T06/T07's adapters) and simply wasn't. Flagging for Phase 1 to resolve by rereading
  `tasks.md`'s full list rather than assuming inclusion or exclusion here.
- **How "repeated disagreement" is observed per-provider.** Neither `QuorumEvaluator` nor
  `QuorumDecisionService` (T09) currently exposes which specific provider(s) were in the disagreeing
  minority to any caller outside `HeldFactAlerter`. I do not know whether this task is expected to
  modify `QuorumDecisionService` to also report per-provider disagreement (a change to an already-frozen,
  already-shipped T09 file) or to derive this signal some other way (e.g., re-deriving from
  `Observation` rows). Phase 1/2 must resolve this explicitly — modifying a previous task's frozen file
  is not something to do without deliberate, disclosed justification (mirroring the discipline T09 itself
  applied when it chose *not* to modify T08's `FactType.java`).
