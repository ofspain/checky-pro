# crypto · T10 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Provider health + degraded. Track `ProviderHealth`; emit `chain.provider.degraded` when a provider is
unhealthy or repeatedly disagrees, continuing if quorum is still achievable (R5).

## Purpose

The operational counterpart to T09's quorum arbitration: gives ops visibility into which provider is
degrading, so an unreliable provider can be investigated before it erodes quorum availability. The
first update-in-place entity in this service, and the first task to close a gap in T02's own migration
set (a missing grant).

## Scope

**In:**
- **`ProviderHealth`** — JPA entity mapping `chain.provider_health` exactly as shipped (T02): `id`,
  `chain`, `provider`, `healthy` (default `true`), `lastOkAt`, `lastDisagreementAt`, `updatedAt`. No raw
  setters; three named mutators: `markHealthy(Instant)` (`healthy=true`, `lastOkAt`, `updatedAt`).
  **Amendment (Kimi Issue 10, documentation only): `markHealthy` deliberately does NOT clear
  `lastDisagreementAt`** — it is a historical "last occurrence" marker (matching its own column name),
  not a current-state flag; `healthy` alone is the authoritative current-state signal. `markUnhealthy(Instant)`
  (`healthy=false`, `updatedAt`; does not touch `lastOkAt`). `recordDisagreement(Instant)`
  (`lastDisagreementAt`, `updatedAt`; never flips `healthy` itself).
- **`ProviderHealthRepository`** — `JpaRepository<ProviderHealth, Long>`, package-private,
  `findByChainAndProvider(String, String)`.
- **New migration, `V4__crypto_app_provider_health_grant.sql`** — grants `crypto_app` `INSERT, SELECT,
  UPDATE` (no `DELETE`) on `chain.provider_health`, closing the Phase 0-confirmed gap. `V1`-`V3`
  untouched.
- **`DegradationReason`** — enum `UNHEALTHY, LAGGING, REPEATED_DISAGREEMENT`, carried in the event
  payload only (no DB column). **Amendment (Kimi Issue 3, documentation only): the reason remains
  directly queryable via `chain.outbox.payload`** (the JSON column) even before Kafka relay — ops is not
  limited to consuming the Kafka topic to see why a provider degraded.
- **`ProviderDegradedPublisher`** — wraps `OutboxPublisher.publish(...)`. `aggregateType = "provider"`
  (already mapped in `EventTopics`). `aggregateId = "{chain}:{provider}"`. **Amendment (Kimi Issue 5):
  `eventType = "chain.provider.degraded"`** (matches the topic string, the one existing precedent for
  this parameter's convention — see `OutboxPublisherTest`'s own `"chain.tx.seen"` example).
  **Amendment (Kimi Issue 7): idempotencyKey =
  `"{chain}:{provider}:degraded:{transitionInstant}:{UUID.randomUUID()}"`** — a random UUID component
  added to eliminate any collision risk between two distinct transitions computed within the same clock
  tick (mirrors T08's `ObservationSnapshotStore.buildKey`'s identical use of a random UUID for the same
  purpose); the primary defense against duplicate emission for one episode remains the
  not-already-unhealthy application-level gate below, not key uniqueness. **Amendment (Kimi Issue 9):
  the payload includes `chain`, `provider`, `reason` (the `DegradationReason`), and `occurredAt`** (the
  same transition instant used in the key) — a concrete shape for task 23 (Contracts) to later formalize
  into a JSON Schema; **this task does NOT create
  `contracts/events/chain/provider-degraded.v1.schema.json` itself** (Kimi Issue 4's literal suggestion,
  rejected — confirmed via `tasks.md` task 23: "Author... the five `contracts/events/chain/*.v1.schema.json`"
  is an explicitly separate, later-scheduled task; R28's own EARS wording is conditional — "WHERE
  contracts/... are authored, THEN... SHALL conform" — not a mandate that every event-emitting task
  authors its own contract file).
- **`ProviderHealthTracker`** (coordinator) — three operations:
  - `recordHealthy(chain, provider)` — fetch-or-create, `markHealthy`, reset the disagreement counter to
    zero, save. No event ever emitted for recovery (no such event exists anywhere in this spec).
  - `recordUnhealthy(chain, provider, DegradationReason)` — fetch-or-create; **only if not already
    `healthy=false`**, `markUnhealthy`, reset the counter, save, then publish. Already-unhealthy calls
    update nothing and publish nothing (AC2).
  - `recordDisagreement(chain, provider)` — fetch-or-create, always call `recordDisagreement` on the
    entity (`lastDisagreementAt` updates regardless). **Amendment (Kimi Issue 6): the in-memory
    consecutive-disagreement counter increments ONLY while the provider is currently healthy.** If
    already unhealthy, this call updates `lastDisagreementAt` but never touches the counter — the
    counter is meaningful only for the healthy→unhealthy transition, never accumulates unbounded while
    already degraded. Reaching `ProviderHealthProperties.disagreementThreshold` while healthy delegates
    to the same transition path as `recordUnhealthy(..., REPEATED_DISAGREEMENT)`.
  - **Amendment (Kimi Issues 2 + 8, documentation only, merged — same root cause): the disagreement
    counter is process-local (`ConcurrentHashMap`-backed) and is explicitly disclosed to be lost on
    every restart (rolling deploy, crash, eviction), not only uncoordinated across replicas.** A
    provider that disagrees `threshold-1` times, survives a restart, then disagrees once more will NOT
    trip the threshold post-restart — this is an accepted, launch-scope limitation of an operational
    signal, not a correctness-critical one (T09's quorum evaluation remains the actual source of truth
    for `AGREED`/`HELD`). Persisting the count (schema change) or coordinating it cross-replica (design.md
    O5, requiring its own author approval) are both explicitly deferred, not solved here.
  - `"unhealthy"`/`"lagging"` remain direct, caller-supplied signals — this task provides the tracking
    primitive, not detection logic.
- **`ProviderHealthProperties`** — new `@ConfigurationProperties` record, one field:
  `int disagreementThreshold` (`@Positive`). **Amendment (Kimi Issue 1, confirmed factual error):
  bound under `themistra.crypto.provider-health.disagreement-threshold`**, matching the established
  `themistra.crypto.*` prefix convention used by every existing `@ConfigurationProperties` record in
  this service (`ProviderProperties`, `SnapshotProperties`, `ScreeningProperties`, `FinalityProperties`,
  `KmsProperties`) — the Phase 2 draft's `crypto.provider-health.*` prefix was simply wrong, not a
  deliberate deviation. Local-profile default: `3`.
- `lastOkAt`/`lastDisagreementAt`/`updatedAt` set from the injected `Clock` bean (T04).

**Out:** (unchanged from Phase 2)
- `ProviderSet` (never scheduled by any task).
- Detecting "unhealthy"/"lagging" from adapter/watcher internals.
- Wiring into `QuorumDecisionService`/`QuorumEvaluator`/any `ChainAdapter` (T05-T09, frozen, not
  modified).
- Any `chain.provider.recovered` event (not named anywhere in this spec).
- Cross-replica/distributed disagreement counting (design.md O5).
- `contracts/events/chain/provider-degraded.v1.schema.json` (task 23's explicit scope — see Amendment
  above).
- Any change to `V1`-`V3` migrations.

## Business Rules

- **R5.** A provider marked unhealthy, lagging, or repeatedly disagreeing (≥ the configured threshold,
  while currently healthy) causes a `chain.provider.degraded` event; quorum evaluation (T09, unmodified)
  already continues with the remaining providers whenever 2-of-3 is still achievable.

## Locked Decisions

- **L5.** Deterministic idempotency key — `"{chain}:{provider}:degraded:{transitionInstant}:{UUID}"`.
- **L15.** Module boundaries — `provider/` imports only `common` (`Clock`) and `events`
  (`OutboxPublisher`, `EventTopics`).

## Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

All 10 Phase 3 findings accepted, 1 with a partial rejection of its literal suggestion (both documented
above and in the numbered list):

1. Config prefix corrected to `themistra.crypto.provider-health.*`, matching the established convention
   — a confirmed factual error in the Phase 2 draft, not a deliberate choice (Kimi Issue 1).
2. Process-local counter's restart-loss explicitly disclosed, alongside the pre-existing multi-replica
   disclosure (Kimi Issue 2, merged with Issue 8).
3. `DegradationReason`'s queryability via `chain.outbox.payload` documented explicitly; no schema column
   added (Kimi Issue 3).
4. Event schema file creation **rejected** for this task — confirmed via `tasks.md` to be task 23's
   explicit, later-scheduled scope; the underlying payload-shape concern is addressed instead by pinning
   a concrete shape now (Kimi Issue 4, partial accept).
5. `eventType = "chain.provider.degraded"` pinned (Kimi Issue 5).
6. Disagreement-counter semantics while already unhealthy clarified: increments only while healthy (Kimi
   Issue 6).
7. Idempotency key strengthened with a random UUID component, eliminating same-instant collision risk
   (Kimi Issue 7).
8. Merged into #2 — cross-replica non-coordination documented alongside the restart-loss disclosure
   (Kimi Issue 8).
9. Event payload shape pinned: `chain`, `provider`, `reason`, `occurredAt` (Kimi Issue 9).
10. `lastDisagreementAt`'s historical (not current-state) semantics documented explicitly; `healthy`
    remains the sole authoritative current-state flag (Kimi Issue 10).

**10 accepted (9 in full, 1 with one sub-option rejected), 0 findings dismissed outright.**

## Dependencies

- `chain.provider_health` (T02, fixed schema; grant added by this task's own new migration).
- `Clock` bean (`common/ClockConfig`, T04).
- `OutboxPublisher`/`EventTopics` (T04).
- No new external library dependency.

## Inputs

- `(chain, provider)` plus, depending on the call, a `DegradationReason` — from whatever future caller
  first has a concrete health signal to report. No such caller exists in this task's own scope.

## Outputs

- An upserted `chain.provider_health` row reflecting the latest health signal.
- A `chain.provider.degraded` outbox event (payload: `chain`, `provider`, `reason`, `occurredAt`),
  emitted only on a healthy→unhealthy transition.

## State Changes

Upsert on `chain.provider_health`, keyed by `(chain, provider)`. New outbox rows for degraded
transitions only.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealth.java`
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/provider/DegradationReason.java`
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderDegradedPublisher.java`
- `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderHealthTracker.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderHealthProperties.java`
- `services/crypto/src/main/resources/db/migration/V4__crypto_app_provider_health_grant.sql`

## Files to Modify

- `services/crypto/src/main/resources/application.properties` — add
  `themistra.crypto.provider-health.disagreement-threshold=3`.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`
  (T02) — frozen.
- `common/ClockConfig.java`, `events/OutboxPublisher.java`, `events/EventTopics.java` (T04) — consumed,
  not modified.
- Anything under `adapter/`, `observation/`, `quorum/` (T05-T09).
- `contracts/events/chain/` — not created by this task (task 23's scope).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R5).** `ProviderHealthTracker` upserts a `ProviderHealth` row per `(chain, provider)` on every
  call.
- **AC2 (R5, L5).** `chain.provider.degraded` is emitted exactly on a `healthy=true→false` transition,
  never on a repeat unhealthy/disagreement signal while already unhealthy, never on recovery.
- **AC3 (R5).** `recordDisagreement` increments the in-memory counter only while currently healthy;
  resets on `recordHealthy`; reaching the configured threshold while healthy triggers the same
  transition path as `recordUnhealthy(..., REPEATED_DISAGREEMENT)`.
- **AC4 (migration).** `V4` grants `crypto_app` `INSERT, SELECT, UPDATE` (no `DELETE`) on
  `chain.provider_health`, without modifying `V1`-`V3`.
- **AC5 (L15).** No import in `provider/` reaches `adapter/`, `observation/`, or `quorum/`.
- **AC6 (idempotency).** The emitted event's idempotency key is deterministic per transition and unique
  across distinct transitions (UUID-strengthened).
- **AC7 (payload shape).** The event payload carries `chain`, `provider`, `reason`, `occurredAt`.

## Required Tests

- `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (package.md §8, named) — AC2.
- A test asserting `recordUnhealthy` on an already-unhealthy provider does not re-emit or re-update
  (AC2).
- A test asserting `recordDisagreement` below the threshold does not transition/emit, and reaching it
  does (AC3).
- A test asserting `recordDisagreement` while already unhealthy updates `lastDisagreementAt` but does
  not touch the counter or re-emit (AC3, Kimi Issue 6).
- A test asserting `recordHealthy` resets the disagreement counter (AC3).
- A test asserting the idempotency key differs across two transitions computed with the same
  `clock.instant()` (Kimi Issue 7, AC6).
- A test asserting the event payload contains `chain`, `provider`, `reason`, `occurredAt` (AC7).
- A test asserting no import in `provider/` reaches `adapter/`/`observation/`/`quorum/` (AC5).
- A test asserting `ProviderHealth` exposes no raw setter, only the three named mutators.
- A test (Docker-gated) proving `V4`'s grant lets `crypto_app` `INSERT`/`SELECT`/`UPDATE`
  `chain.provider_health` against a real Postgres, and that `DELETE` still fails (AC4).

## Constraints

- **Performance:** none beyond existing conventions.
- **Security:** no new secret; the threshold is a plain integer, safe to default in
  `application.properties`.
- **Thread-safety:** `ConcurrentHashMap`/`AtomicInteger`-backed counter; accepted narrow race on
  concurrent transitions for the same `(chain, provider)` (same category as T09's own accepted
  pre-flight-check race) — mitigated at the key level by the UUID amendment (Kimi Issue 7), not
  eliminated at the application level.
- **Transaction:** `ProviderHealthRepository.save` and `OutboxPublisher.publish` (in the transition
  path) occur in the same transaction — the first task since T04 to need this explicit pairing.
- **Module boundaries:** L15, see AC5.
- **Null handling:** `ProviderHealthTracker`'s public methods reject `null` `chain`/`provider`/`reason`
  fast via named `Objects.requireNonNull`.

## Open Questions

No blockers. All Phase 3 findings are resolved above.
