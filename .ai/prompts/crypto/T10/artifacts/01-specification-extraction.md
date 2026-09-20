# crypto · T10 · Phase 1 — Specification Extraction

## Business Rules

- **R5.** If a provider is unhealthy, lagging, or repeatedly disagreeing with the quorum, the system
  emits a `chain.provider.degraded` event and continues with the remaining providers if quorum is still
  achievable.

## Locked Decisions

- **L5.** Deterministic idempotency key on every event (`chain:txhash:eventtype`) — every emitted event
  must carry a deterministic idempotency key so consumers can dedupe. A `chain.provider.degraded` event
  has no natural `txHash` (it concerns a provider's aggregate health, not one transaction); this task
  must derive an analogous deterministic key shape, not skip the requirement.
- **L15.** Module boundaries — package-by-feature under `com.themistra.crypto`; no feature module
  imports another feature module's entity; shared plumbing lives only in `common`.

(No other `L`-numbered decision in `design.md` §4a directly constrains this task's own scope; L1/L2
(quorum) are relevant *context* — R5's "continue if quorum is still achievable" presupposes L1's 2-of-3
rule — but this task does not modify quorum logic itself, T09's frozen scope.)

## Files involved

**Existing, to read/extend (no modification unless explicitly named):**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql:52-60` — `provider_health`
  table already shipped (T02, frozen): `id, chain, provider, healthy (BOOLEAN, default TRUE),
  last_ok_at, last_disagreement_at, updated_at`, `UNIQUE (chain, provider)`.
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` — **confirmed
  (Phase 0) to grant `crypto_app` nothing at all on `provider_health`** (its one table-level `GRANT`
  statement, line 34, names only `observations`/`attestations`/`quorum_decisions`). This task needs a
  new migration (`V4__...`, since V1-V3 are immutable per agents.md) granting `crypto_app` appropriate
  privileges on `provider_health` — almost certainly `INSERT, SELECT, UPDATE`, since the table's own
  shape (`updated_at`, mutable `healthy` flag, `UNIQUE (chain, provider)` — one row per provider,
  revised over time) is update-in-place, unlike every append-only table this service has built so far.
- `services/crypto/src/main/java/com/themistra/crypto/events/OutboxPublisher.java` (T04) — the only
  sanctioned event-emission path; this task's degraded-event publisher must call
  `OutboxPublisher.publish(...)`.
- `services/crypto/src/main/java/com/themistra/crypto/events/EventTopics.java` (T04) — aggregate type
  `"provider"` already mapped to topic `"chain.provider.degraded"`; this task's aggregate type is fixed
  as the literal string `"provider"`.
- `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` (T04) — injectable
  `Clock`, expected for `lastOkAt`/`lastDisagreementAt`/`updatedAt`.

**New, per design.md §6 (`provider/` package):**
- `provider/ProviderHealth.java` / `provider/ProviderHealthRepository.java` — maps `provider_health`.
- `provider/ProviderDegradedPublisher.java` — emits `chain.provider.degraded` via `OutboxPublisher`.

**Explicitly NOT in this task's scope, despite being listed under `provider/` in design.md §6:**
- `provider/ProviderSet.java` — design.md §4b-O1's proposed abstraction for "N adapters per chain,"
  tied to *which* providers are wired per chain (provider configuration/fan-out), not provider *health
  tracking*. **Confirmed (Phase 0/1): `ProviderSet` is never named in any of `tasks.md`'s 29 ordered
  tasks** — it exists only in `design.md` (§4b-O1, §6 package map), a genuine, disclosed gap between
  design.md's aspirational package map and the actual task schedule. T10's own task statement never
  mentions it. Building it here would be scope creep beyond "Track `ProviderHealth`; emit
  `chain.provider.degraded`..." — flagged, not built.

## Dependencies

- `chain.provider_health` (T02, fixed schema; grant gap must be closed by this task via a new
  migration).
- `Clock` bean (`common/ClockConfig`, T04).
- `OutboxPublisher`/`EventTopics` (T04).
- No new `@ConfigurationProperties` record confirmed yet — if a numeric health/degradation threshold is
  needed (see Open Questions), it may require one; Phase 2 decides.
- No contract file (`contracts/api/crypto-internal.yaml`, `contracts/events/chain/*`) is touched by this
  task in a way that requires reading one — `contracts/events/chain/` does not exist yet anywhere in
  this repository (confirmed: no schema file for any `chain.*` event exists yet, including
  `tx-finalized.v1.schema.json`, named in this task's own header but absent from disk). `OutboxPublisher`
  is domain-agnostic and takes a plain payload object with no contract-driven code generation currently
  wired for this service's own events (unlike auth-service, which does have `contracts/events/auth/`
  schemas). This task defines its own payload shape without a contract to validate against.

## Acceptance Criteria

- **AC1 (R5).** A `ProviderHealth` row exists per `(chain, provider)`, reflecting whether that provider
  is currently considered healthy, with `lastOkAt`/`lastDisagreementAt`/`updatedAt` tracked.
- **AC2 (R5, L5).** When a provider is marked unhealthy, `chain.provider.degraded` is emitted via
  `OutboxPublisher`, carrying a deterministic idempotency key derived analogously to
  `chain:txhash:eventtype` for a per-`(chain, provider)` event (exact shape: Phase 2 to propose).
  Emission is skipped for a provider already known unhealthy (no duplicate degraded events for a
  standing condition) — inferred from "no duplicate events" being the entire purpose of L5's
  idempotency-key requirement, not separately stated as its own rule.
- **AC3 (R5).** Evaluation "continues with the remaining providers if quorum is still achievable" — this
  task's own scope is to track and report health/degradation; it does not itself change quorum-
  evaluation call sites (T09's `QuorumDecisionService`/`QuorumEvaluator` are frozen, out of this task's
  Files to Modify) — Phase 2 must define precisely what "continuing" means as an artifact of *this*
  task's own code (most plausibly: this task never blocks or refuses to record other providers'
  activity because one provider is unhealthy) versus a claim requiring changes elsewhere.
- **AC4 (grant gap, Phase 0 finding).** A new Flyway migration grants `crypto_app` the privileges
  `provider_health` actually needs (at minimum `INSERT, SELECT, UPDATE`), without modifying any existing
  migration file.
- **AC5 (L15).** No code in `provider/` imports an entity from `adapter/`, `observation/`, `quorum/`, or
  `events/` beyond what's already established as acceptable cross-package reuse (e.g., `Clock`,
  `OutboxPublisher`, `EventTopics` from `common`/`events`, which every prior task already depends on
  similarly).

## Tests required

- `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (package.md §8, named) — AC2.
- A test asserting a `ProviderHealth` row can be created/updated reflecting a health-state transition
  (AC1).
- A test asserting the emitted event's idempotency key is deterministic for the same `(chain, provider)`
  degraded condition (AC2, L5) — exact assertion shape depends on Phase 2's key-format decision.
- A test asserting no duplicate `chain.provider.degraded` event is emitted for a provider already marked
  unhealthy (AC2).
- A test (Docker-gated, mirroring prior tasks' integration tests) proving the new migration's grant is
  sufficient for `crypto_app` to actually `INSERT`/`SELECT`/`UPDATE` `provider_health` against a real
  Postgres (AC4).

## Open Questions

No blockers cited in `package.md` §11 apply directly to this task (Q1-Q8 cover provider *selection*,
screening, Tron confirmation semantics, watcher transport, the anchor endpoint, KMS key spec, and the
agents.md follow-up — none address provider-health thresholds or this event's key shape). Two items are
genuine gaps the spec's author never addressed anywhere, requiring an implementer-proposed resolution
(Phase 2, subject to Kimi challenge + human sign-off) rather than a blocking question back to the
author, matching the precedent T08 (S3 key scheme) and T09 (exactly-3 threshold) already set for
similarly under-specified areas:

- **No numeric definition anywhere in the spec for "unhealthy," "lagging," or "repeatedly disagreeing."**
  R5's own wording is qualitative only; no `O`-numbered OPEN decision in design.md §4b addresses
  provider-health thresholds. Phase 2 must propose a concrete, justified rule (e.g., what specifically
  flips `healthy` to `false`) grounded in what this task's own scope can actually observe.
- **No natural `txHash`/`watchId` for this event's idempotency key or outbox `aggregateId`.** Both L5's
  literal template and `OutboxPublisher`'s documented partition-key convention assume a per-transaction/
  per-watch event shape. Phase 2 must propose an analogous deterministic shape for a per-`(chain,
  provider)` health event.
