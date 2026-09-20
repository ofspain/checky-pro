# crypto · T10 · Phase 2 — Task Implementation Brief (TIB)

## Task

Provider health + degraded. Track `ProviderHealth`; emit `chain.provider.degraded` when a provider is
unhealthy or repeatedly disagrees, continuing if quorum is still achievable (R5).

## Purpose

The operational counterpart to T09's quorum arbitration: while `QuorumEvaluator` decides per-fact
truth regardless of *why* providers disagree, this task gives ops visibility into *which* provider is
degrading, so a persistently unreliable provider can be investigated or replaced before it erodes
quorum availability. The first task in this service to build an update-in-place (not append-only)
persisted entity, and the first to close a real gap in T02's own migration set (a missing grant).

## Scope

**In:**
- **`ProviderHealth`** — a JPA entity mapping `chain.provider_health` exactly as shipped (T02): `id`
  (DB IDENTITY), `chain`, `provider`, `healthy` (default `true`), `lastOkAt`, `lastDisagreementAt`,
  `updatedAt`. **Unlike every entity built so far in this service (all append-only), this one is
  genuinely update-in-place** — one row per `(chain, provider)`, the DB's own `UNIQUE (chain,
  provider)` constraint. No raw setters; three narrow, named mutator methods instead:
  `markHealthy(Instant)` (sets `healthy=true`, `lastOkAt`, `updatedAt`), `markUnhealthy(Instant)`
  (sets `healthy=false`, `updatedAt`; does not touch `lastOkAt`), `recordDisagreement(Instant)` (sets
  `lastDisagreementAt`, `updatedAt`; does not itself flip `healthy` — that decision belongs to the
  tracker's policy, below).
- **`ProviderHealthRepository`** — `JpaRepository<ProviderHealth, Long>`, package-private, one derived
  finder: `findByChainAndProvider(String, String)`.
- **New migration, `V4__crypto_app_provider_health_grant.sql`** — grants `crypto_app` `INSERT, SELECT,
  UPDATE` on `chain.provider_health` (closing the Phase 0-confirmed gap: `V2` grants nothing on this
  table). No `DELETE` — a health row is never removed, only transitioned. `V1`-`V3` are not touched
  (immutable per agents.md); this is purely additive, mirroring `V3__crypto_app_outbox_grant.sql`'s own
  precedent of a later migration adding a grant `V2` omitted.
- **`DegradationReason`** — a small new enum, `UNHEALTHY, LAGGING, REPEATED_DISAGREEMENT`, carried only
  in the emitted event's payload (not persisted in `provider_health`, which has no column for it) —
  gives ops a reason without requiring a schema change.
- **`ProviderDegradedPublisher`** — wraps `OutboxPublisher.publish(...)` for exactly one event type.
  **Aggregate type is the literal string `"provider"`** (already mapped to topic
  `"chain.provider.degraded"` in `EventTopics`, T04). **Resolves Phase 1 Open Question #2 (idempotency
  key / aggregateId shape):** `aggregateId = "{chain}:{provider}"` (keeps one provider's health events
  on one Kafka partition, analogous to `watchId`'s role for `chain.tx.*` events);
  **idempotencyKey = "{chain}:{provider}:degraded:{transitionInstant}"** — including the specific
  transition instant (not a fixed key) is required because, unlike a `chain.tx.*` event (each a
  one-time-ever transition), a provider can degrade, recover, and degrade again; a fixed key would
  collide with itself (violating the outbox's own `UNIQUE(idempotency_key)`) on the second episode. The
  timestamp component distinguishes *episodes*; L5's dedupe purpose (protecting against redelivery of
  the *same* occurrence) is preserved because a given transition's instant is computed once and reused
  for both the entity update and the event key.
- **`ProviderHealthTracker`** (the coordinator, composing `ProviderHealthRepository` +
  `ProviderDegradedPublisher` + `Clock`) — exposes three operations:
  - `recordHealthy(chain, provider)` — fetch-or-create the row, call `markHealthy`, reset the
    in-memory disagreement counter (below) to zero, save. No event ever emitted for recovery (R5/
    `EventTopics`/package.md's own publish list name only `chain.provider.degraded`; no "recovered"
    event exists anywhere in this spec).
  - `recordUnhealthy(chain, provider, DegradationReason)` — fetch-or-create, and **only if not already
    `healthy=false`**, call `markUnhealthy`, reset the disagreement counter, save, then publish via
    `ProviderDegradedPublisher`. Already-unhealthy calls update nothing and publish nothing (AC2 — no
    duplicate events for a standing condition).
  - `recordDisagreement(chain, provider)` — fetch-or-create, call `recordDisagreement` on the entity
    (always updates `lastDisagreementAt`, regardless of threshold), increment an **in-memory**
    per-`(chain, provider)` consecutive-disagreement counter; once it reaches
    `ProviderHealthProperties.disagreementThreshold`, delegate to the same not-already-unhealthy-gated
    transition as `recordUnhealthy(..., REPEATED_DISAGREEMENT)`.
  - **Resolves Phase 1 Open Question #1 (no numeric threshold in the spec):** the disagreement counter
    is process-local (`ConcurrentHashMap`-backed, keyed by `(chain, provider)`), not persisted — no
    schema column exists for it, and multi-replica coordination (design.md O5) is explicitly deferred to
    a later task (the watcher-assignment task, not yet built) with its own required author approval.
    This is a disclosed, per-pod-scoped tradeoff appropriate for an operational/observability signal, not
    a correctness-critical one (T09's quorum evaluation remains the actual source of truth for
    `AGREED`/`HELD`; this task never influences that outcome).
  - `"unhealthy"` and `"lagging"` (R5) are both **direct, caller-supplied signals** —
    `recordUnhealthy(..., UNHEALTHY)` / `recordUnhealthy(..., LAGGING)` — this task does not itself
    detect adapter failure or lag (no watcher/adapter-error-tracking exists yet to detect either from);
    it provides the tracking primitive a future caller (adapter error handling, or the watcher layer,
    task 16) will invoke.
- **`ProviderHealthProperties`** — new `@ConfigurationProperties` record, one field:
  `int disagreementThreshold` (validated `@Positive`), bound under a new
  `crypto.provider-health.disagreement-threshold` key with a local-profile default (`3`, chosen as a
  reasonable, small, round number — no spec guidance exists to derive a more precise value from).
- `lastOkAt`/`lastDisagreementAt`/`updatedAt` all set from the existing injected `Clock` bean
  (`common/ClockConfig`, T04).

**Out:**
- `ProviderSet` — design.md §4b-O1's proposed abstraction, never named in any of `tasks.md`'s 29
  ordered tasks (Phase 0/1 finding); provider *configuration/fan-out*, not health *tracking*. Not built
  here.
- Actually detecting "unhealthy" or "lagging" from `ChainAdapter`/watcher internals — no such detection
  exists anywhere yet; this task only provides the primitive a future caller invokes.
- Wiring `recordDisagreement`/`recordUnhealthy` into `QuorumDecisionService`/`QuorumEvaluator` (T09,
  frozen) or any `ChainAdapter` (T05-T07, frozen) call site — those files are not modified by this
  task; the actual call sites belong to whichever future task (watcher layer, T16, or adapter-level
  error handling) first has a concrete unhealthy/lagging/disagreement signal to report.
- Any `chain.provider.recovered` (or similarly named) event — not named anywhere in this spec.
- Cross-replica/distributed disagreement counting (design.md O5 — explicitly deferred, requires its own
  author approval per O5's own text).
- Any change to `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`,
  `V3__crypto_app_outbox_grant.sql` (T02, frozen).

## Business Rules

- **R5.** A provider marked unhealthy, lagging, or repeatedly disagreeing (≥ the configured threshold)
  causes a `chain.provider.degraded` event; the surrounding quorum evaluation (T09, unmodified) already
  continues with the remaining providers whenever 2-of-3 is still achievable — this task tracks and
  reports, it does not gate or block quorum evaluation itself.

## Locked Decisions

- **L5.** Deterministic idempotency key on every event — implemented via the
  `"{chain}:{provider}:degraded:{transitionInstant}"` scheme above.
- **L15.** Module boundaries — `provider/` imports only `common` (`Clock`) and `events`
  (`OutboxPublisher`, `EventTopics`), nothing from `adapter/`, `observation/`, or `quorum/`.

## Dependencies

- `chain.provider_health` (T02, fixed schema; grant added by this task's own new migration).
- `Clock` bean (`common/ClockConfig`, T04).
- `OutboxPublisher`/`EventTopics` (T04).
- No new external library dependency — no `pom.xml` change.

## Inputs

- `(chain, provider)` plus, depending on the call, a `DegradationReason` — from whatever future caller
  first has a concrete health signal to report. No such caller exists in this task's own scope; its own
  tests are the only caller, exactly as T08/T09's own components had no real caller until their
  respective consuming tasks existed.

## Outputs

- An upserted `chain.provider_health` row reflecting the latest health signal.
- A `chain.provider.degraded` outbox event, emitted only on a healthy→unhealthy transition (never on an
  already-unhealthy repeat signal, never on recovery).

## State Changes

Upsert (insert-or-update) on `chain.provider_health`, keyed by `(chain, provider)` — the first
update-in-place table this service's application code writes to. New outbox rows for degraded
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
  `crypto.provider-health.disagreement-threshold=3` (local-profile default).

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`
  (T02) — frozen.
- `common/ClockConfig.java` (T04), `events/OutboxPublisher.java`, `events/EventTopics.java` (T04) —
  consumed, not modified.
- Anything under `adapter/`, `observation/`, `quorum/` (T05-T09) — consumed conceptually only
  (`Clock`/outbox already-established patterns); no call site wired into this task's tracker.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R5).** `ProviderHealthTracker` upserts a `ProviderHealth` row per `(chain, provider)` on every
  `recordHealthy`/`recordUnhealthy`/`recordDisagreement` call.
- **AC2 (R5, L5).** `chain.provider.degraded` is emitted exactly on a `healthy=true→false` transition,
  never on a repeat unhealthy/disagreement signal while already unhealthy, never on recovery.
- **AC3 (R5).** `recordDisagreement` increments a per-`(chain, provider)` in-memory counter that resets
  on `recordHealthy` or once already transitioned to unhealthy; reaching
  `ProviderHealthProperties.disagreementThreshold` triggers the same transition path as
  `recordUnhealthy(..., REPEATED_DISAGREEMENT)`.
- **AC4 (migration).** `V4__crypto_app_provider_health_grant.sql` grants `crypto_app` `INSERT, SELECT,
  UPDATE` (no `DELETE`) on `chain.provider_health`, without modifying `V1`-`V3`.
- **AC5 (L15).** No import in `provider/` reaches into `adapter/`, `observation/`, or `quorum/`.
- **AC6 (idempotency).** The emitted event's idempotency key is deterministic and distinct per
  degradation episode (`"{chain}:{provider}:degraded:{transitionInstant}"`).

## Required Tests

- `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (package.md §8, named) — AC2.
- A test asserting `recordUnhealthy` on an already-unhealthy provider does not re-emit or re-update
  (AC2).
- A test asserting `recordDisagreement` below the threshold does not transition/emit, and reaching the
  threshold does (AC3).
- A test asserting `recordHealthy` resets the disagreement counter (a provider that disagreed
  `threshold-1` times, recovered, then disagreed again does not immediately trip) (AC3).
- A test asserting the idempotency key differs across two separate degrade→recover→degrade episodes for
  the same `(chain, provider)` (AC6).
- A test asserting no import in `provider/` reaches `adapter/`/`observation/`/`quorum/` (AC5) — a
  simple static/reflection check, not a new ArchUnit convention (T09 Phase 11 deferred introducing
  ArchUnit itself to a future dedicated task).
- A test asserting `ProviderHealth` exposes no raw setter, only the three named mutators (mirrors
  `OutboxEvent`'s own precedent of narrow, named mutators rather than a public setter).
- A test (Docker-gated, mirroring prior tasks' integration tests) proving `V4`'s grant lets `crypto_app`
  actually `INSERT`/`SELECT`/`UPDATE` `chain.provider_health` against a real Postgres, and that
  `DELETE` still fails (AC4).

## Constraints

- **Performance:** none beyond existing conventions — this task adds no new hot path.
- **Security:** no new secret; no new HTTP endpoint; no `@ConfigurationProperties` value needs
  `External Secrets Operator` injection (a plain integer threshold, safe to default in
  `application.properties`).
- **Thread-safety:** the in-memory disagreement counter (`ProviderHealthTracker`) uses a
  `ConcurrentHashMap`/`AtomicInteger`-based structure safe for concurrent calls across virtual threads;
  the DB upsert itself relies on `SimpleJpaRepository.save`'s own per-call transaction (no explicit
  `@Transactional` needed, mirroring T08/T09's established reasoning) — a genuine race between two
  concurrent transitions for the same `(chain, provider)` could in principle both pass the
  "not-already-unhealthy" check before either saves, double-publishing two degraded events for the same
  logical episode (each with a distinct instant-suffixed key, so neither would collide at the DB level)
  — an accepted, disclosed risk given `UNIQUE(chain, provider)` makes this narrow and this is an
  operational, not correctness-critical, signal (same category of accepted risk T09's own
  pre-flight-check race was accepted for).
- **Transaction:** the `ProviderHealthRepository.save` and `OutboxPublisher.publish` calls (in
  `recordUnhealthy`'s transition path) must occur in the same transaction, so the event and the state
  change are atomically consistent — mirrors agents.md's outbox-in-same-transaction rule exactly (this
  is the first task since T04 to actually call `OutboxPublisher`, so this is also the first task to
  need an explicit `@Transactional` boundary around a `save` + `publish` pair; unlike T08/T09's
  single-write methods, this one genuinely needs it).
- **Module boundaries:** L15, see AC5.
- **Null handling:** `ProviderHealthTracker`'s three public methods reject `null`
  `chain`/`provider`/`reason` fast via named `Objects.requireNonNull`, mirroring every prior task's
  established discipline.

## Open Questions

No blockers. Both Phase 1 open items (disagreement threshold, idempotency-key/aggregateId shape) are
resolved above as implementer-proposed decisions, ready for Phase 3 (Kimi) challenge.
