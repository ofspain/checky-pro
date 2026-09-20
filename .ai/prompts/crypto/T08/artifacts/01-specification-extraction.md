# crypto · T08 · Phase 1 — Specification Extraction

## Business Rules

- **R4.** WHEN any provider returns a response for a fact, THEN the system SHALL persist that
  response verbatim to the observation log (Postgres `chain` schema + S3) before the quorum decision
  is finalized. **This is the one requirement independently testable by this task's own deliverable.**
  `Observation`/`ObservationRepository`/`ObservationSnapshotStore` must together make "persist
  verbatim, before quorum" a real, provable capability — not the quorum decision itself (task 9's
  concern), but the persistence primitive task 9 will call.

No other numbered requirement is independently testable by this task's own deliverable:
- R1/R2/R3 (quorum agreement/disagreement logic) — task 9's `QuorumEvaluator`; this task supplies the
  log that logic reads from and writes alongside, not the logic itself.
- R5 (provider-degraded) — task 10.
- R25 (sidecar observations treated as just another provider answer) — this task's `Observation`
  entity has a `provider` column (`VARCHAR(64)`, comment: "e.g. alchemy | quicknode | trongrid |
  sidecar:solana") that structurally accommodates a sidecar's identity as a value, but R25's actual
  *quorum-authority* rule is task 9's to enforce, not this task's schema/persistence layer.

## Locked Decisions

Derived from `design.md` §4a, scoped to what this task's own deliverable must honor:

- **L3.** Observation log is verbatim and written first — each provider's raw response persisted
  verbatim (Postgres `chain` schema + S3 snapshot) *before* the quorum decision, so any past
  attestation can be re-derived and defended (7-year retention, per `ARCHITECTURE.md` §5, cited but
  not itself part of this task's read scope). **The primary decision this task exists to implement.**
- **L13.** No credential committed; AWS S3 access resolved via the SDK's own credential chain /
  External Secrets Operator injection, never a Spring property — already the explicit design of
  `SnapshotProperties` (T03), which carries no key/secret fields, only `bucket`/`prefix`/`region`.
- **L15.** Module boundaries — new files go under `observation/`, matching design.md §6's file map;
  `Observation`/`ObservationRepository`/`ObservationSnapshotStore` must not import from another
  feature module's entity package.

Not directly implicated by this task's own scope (noted only to rule out, mirroring T06/T07's own
Phase 1 practice of stating what does *not* apply): L1/L2 (quorum semantics, task 9), L4 (finality
policy, task 14), L5 (idempotency key on *emitted events* — this task persists observations, it does
not emit any `chain.*` event itself), L6 (reorg, task 16+), L7/L8/L9 (token/address validation, tasks
11/12), L10/L11/L12 (attestation/screening, later tasks), L14 (sidecars — this task's schema
accommodates a sidecar's `provider` value but implements no sidecar logic itself).

## Files involved

**Existing — read/extend:**
- `chain.observations` (`V1__chain_baseline.sql:24-34`) — the fixed table `Observation` must map onto
  exactly: `id`, `chain`, `tx_hash`, `provider`, `fact_type`, `raw_response` (JSONB), `s3_snapshot_key`
  (nullable), `observed_at`. Not modified — this task is a consumer of an already-shipped migration,
  the same relationship T06 had to `ProviderProperties`.
- `crypto_app`'s `GRANT INSERT, SELECT` on `chain.observations` (`V2__crypto_app_role_and_grants.sql:34`)
  — not modified; the JPA mapping must not produce an `UPDATE`/`DELETE` under any code path.
- `common/config/SnapshotProperties.java` (T03) — this task's first real consumer; not modified.
  `application.properties` already has `local`-profile fixture values.
- `common/ClockConfig.java` (T04) — already provides an injectable `Clock` bean
  (`Clock.systemUTC()`); `Observation.observedAt` should be set from this, matching
  `OutboxEvent.createdAt`'s established discipline, not a new Clock source.
- `events/OutboxEvent.java` / `OutboxEventRepository.java` (T04) — the direct structural precedent for
  entity/repository shape (protected no-arg constructor, static factory, `@JdbcTypeCode(SqlTypes.JSON)`
  for the JSONB column, package-private repository interface); not modified, referenced only.

**New — expected by design.md §6:**
- `observation/Observation.java` — the JPA entity.
- `observation/ObservationRepository.java` — the Spring Data repository.
- `observation/ObservationSnapshotStore.java` — the S3 WORM snapshot writer.
- **Not named in design.md §6's file map, but likely functionally necessary** (mirrors the recurring
  "wiring class the package map doesn't name" situation from T04/T06/T07): a `@Configuration` class
  building a real AWS S3 client from `SnapshotProperties` — exact name/shape is Phase 2/5 work.

## Dependencies

- `chain.observations`'s fixed schema (above) — `Observation`'s field set is not this task's choice to
  make; it is dictated by the already-shipped DDL.
- `SnapshotProperties` (T03) — `bucket`, `prefix`, `region`.
- **New dependency required**: an AWS S3 SDK artifact (`software.amazon.awssdk:s3`, or the S3
  transfer-manager variant) — confirmed absent from `pom.xml` today (only `software.amazon.awssdk:kms`
  is present, scoped by the pom's own comment to the not-yet-built attest module). This task must add
  it, unlike T06/T07 where the chain-client library was already pre-declared in T01.
- `Clock` bean (`common/ClockConfig`, T04) — for `observed_at`.
- Spring Data JPA / `JpaRepository` — already a project dependency (T02 onward).
- `com.fasterxml.jackson.databind.ObjectMapper` (already a dependency, used by `OutboxPublisher`) —
  likely needed if `raw_response` is stored as a serialized JSON string the way `OutboxEvent.payload`
  is, though the exact "verbatim" representation (raw provider JSON string passed straight through vs.
  re-serialized) is a Phase 2 design question, not decided here.

## Acceptance Criteria

Derived from the task statement's own clauses plus R4/L3:

- **AC1 (R4, L3).** A provider response can be persisted to `chain.observations` verbatim — the
  `raw_response` column carries the actual provider payload, not a reshaped/lossy summary of it.
- **AC2 (L3).** The Postgres persistence and the S3 snapshot write both happen, with the DB row
  eventually carrying a non-null `s3_snapshot_key` pointing at the actual S3 object — exact ordering
  between the two writes is a Phase 2 design question (see Open Questions), not fixed by the spec text
  itself.
- **AC3 (L3, INSERT/SELECT-only grant).** No code path in `Observation`/`ObservationRepository`
  produces an `UPDATE` or `DELETE` against `chain.observations` — the entity is immutable post-persist.
- **AC4 (task statement, "Test ordering").** A test proves observations are written before whatever
  represents "the quorum decision" in this task's own testable scope — see Open Questions for what
  that means precisely, since task 9's actual `QuorumEvaluator` doesn't exist yet for this task to
  order against.
- **AC5 (L13).** No AWS credential is hardcoded anywhere in `ObservationSnapshotStore` or its wiring;
  resolved via the SDK's own default credential chain.

## Tests required

- `shouldLogEveryProviderResponseVerbatimToObservationLog` (package.md §8, named, → R4) — the one
  pre-mapped test; exact shape (unit vs. Testcontainers-backed) is Phase 5 to decide.
- A test proving `Observation`'s JPA mapping never issues `UPDATE`/`DELETE` (AC3) — plausibly a
  Testcontainers-backed integration test attempting a save-after-mutation and asserting it either
  doesn't compile a mutator to call (no setters exist) or fails at the DB grant level if somehow
  attempted.
- A test proving the S3 snapshot key set on the persisted row round-trips to real content (AC2) —
  shape depends on the LocalStack-vs-mock decision (Open Questions).
- Whatever "Test ordering" (AC4) resolves to precisely, once Phase 2 pins it down.

## Open Questions

**Genuine blockers for a precise design, not yet resolved by anything already in the spec:**

1. **S3-write-vs-Postgres-write ordering and failure handling.** L3/R4 require both to happen "before
   the quorum decision," but say nothing about their order relative to *each other*, or what happens if
   one succeeds and the other fails (S3 and Postgres share no transaction). Phase 2 must design this
   explicitly.
2. **LocalStack (Testcontainers) vs. a mocked S3 client for this task's own tests.** No precedent
   exists either way in this codebase. Phase 2 must decide and justify, the same way T06 decided
   HTTP-polling for O2.
3. **What "Test ordering" in the task statement's own closing clause actually means precisely.**
   Plausible readings: (a) DB-insert-before-S3-write or vice versa, (b) observation-persisted-before
   some caller's quorum-decision step (which this task cannot itself construct, since `QuorumEvaluator`
   is task 9) — resolved as "AC4" above in the most defensible reading available from this task's own
   scope, but Phase 2 should confirm or refine this interpretation explicitly rather than let it stay
   implicit.
4. **`s3_snapshot_key`'s naming/partitioning scheme.** `SnapshotProperties.prefix` exists but nothing
   specifies the key structure beneath it. Not a blocker for building *a* working scheme, but Phase 2
   should pick one deliberately (e.g. `{prefix}{chain}/{txHash}/{factType}/{observationId}.json`) and
   document the reasoning, not leave it arbitrary.
