# crypto · T08 · Phase 2 — Task Implementation Brief (TIB)

## Task

Observation log first. Implement `Observation` (append-only) + `ObservationSnapshotStore` (S3). Every
provider response is persisted verbatim before any quorum decision (L3, R4). Test ordering.

## Purpose

The defensible core of the platform (design.md §5's own words): the durable, verbatim record of what
each provider actually said, written before any quorum logic ever runs against it. The first task to
write into an already-fully-shaped table (`chain.observations`, T02) rather than design its own
schema, and the first task needing a genuinely new external dependency (AWS S3).

## Scope

**In:**
- `Observation` — a JPA entity mapping `chain.observations` exactly as shipped (T02): `id` (DB
  IDENTITY), `chain`, `txHash`, `provider`, `factType`, `rawResponse` (JSONB, verbatim payload),
  `s3SnapshotKey` (nullable), `observedAt`. **Fully immutable post-construction** — no setters, no
  mutation methods (unlike `OutboxEvent`'s `markPublished`) — matching `crypto_app`'s actual
  `INSERT, SELECT`-only grant on this table exactly, not just as a style choice.
- `ObservationRepository` — `JpaRepository<Observation, Long>`, package-private, derived-query finders
  only (mirrors `OutboxEventRepository`).
- `ObservationSnapshotStore` — writes the verbatim payload to S3 (`SnapshotProperties.bucket`/
  `prefix`), returns the resulting key. Key scheme: **`{prefix}{chain}/{txHash}/{factType}/
  {provider}-{observedAt-as-ISO-instant}-{random UUID}.json`** — deliberately not based on the DB
  row's auto-generated `id`, because (see Ordering decision below) the S3 write happens *before* that
  id exists. The random UUID component exists because multiple providers report the same
  `(chain, txHash, factType)` — that repetition is the entire point of the quorum model — so the key
  must be unique per *observation*, not per fact.
- **Write ordering (resolves Phase 1 Open Questions 1 and 3): S3 write attempted first, then exactly
  one Postgres `INSERT` carrying whatever `s3SnapshotKey` resulted.** This is not a preference — it is
  forced by `crypto_app`'s grant having no `UPDATE`: a "insert row, then backfill the S3 key via
  update" pattern is structurally impossible against this schema. If the S3 write fails, the Postgres
  insert still proceeds with `s3SnapshotKey = null` (the DB row — which already carries the full
  verbatim `rawResponse` JSONB — is the load-bearing, R4-testable persistence guarantee; S3 is a
  supplementary WORM durability layer, not something that may block persistence entirely if it's
  unavailable). An S3 failure is logged distinctly at error level, never silently swallowed, so a gap
  in the WORM mirror is operationally visible.
- A small coordinating class (exact name Phase 5 — **not** named in design.md §6's package map, same
  "functionally necessary, not spec-named" situation `OutboxRelay`/the adapter `*Config` classes were
  in for T04/T06/T07) composing `ObservationSnapshotStore` + `ObservationRepository` into the single
  "persist verbatim" operation R4 describes — neither low-level component should orchestrate the
  other, and *something* has to call both in the right order for R4 to be a usable capability, not
  just two disconnected building blocks.
- A `@Configuration` class building a real `software.amazon.awssdk.services.s3.S3Client` from
  `SnapshotProperties` — no credential ever hardcoded (L13); resolved via the AWS SDK's own default
  credential chain.
- `observedAt` set from the existing injected `Clock` bean (`common/ClockConfig`, T04) — not
  `Instant.now()` inline, matching `OutboxEvent.createdAt`'s established discipline.
- New dependency: `software.amazon.awssdk:s3` (no explicit version — inherits from the already-imported
  AWS SDK BOM, same pattern `kms` used). New test dependency: `org.testcontainers:localstack` (same
  pinned `testcontainers.version`).
- Testing: unit tests mock `S3Client` directly (fast, no container) for `ObservationSnapshotStore`'s
  own key-naming/error-handling logic; **one** LocalStack-backed Testcontainers integration test proves
  a real S3 `PutObject`/`GetObject` round-trip — chosen over an all-mocked approach because S3 here is
  core persistence infrastructure (like Postgres/Kafka, both real-Testcontainers-tested already in this
  service), not an external RPC provider being quorum-fanned-out (which *is* the right thing to fake,
  per T06/T07's own established convention) — these are different categories of dependency and warrant
  different testing treatment.
- **"Test ordering" (resolves Phase 1 Open Question 3):** a test proving the S3 write is attempted, and
  completes (success or failure), *before* the Postgres insert happens — entirely within this task's
  own code, not requiring `QuorumEvaluator` (task 9, doesn't exist yet) to exist.

**Out:**
- Any change to `V1__chain_baseline.sql`/`V2__crypto_app_role_and_grants.sql` (T02, frozen) — the
  `chain.observations` shape and its grants are consumed exactly as shipped.
- `QuorumEvaluator`/`QuorumDecision` (task 9) — this task supplies the log task 9 reads from and
  writes alongside; it does not implement quorum logic or call into it.
- `ProviderHealth`/`chain.provider.degraded` (task 10).
- Any `chain.*` event emission — `Observation` persistence does not, by itself, publish anything to the
  outbox; R4/L3 are about the log existing before a *decision*, not about an event being emitted (that
  is R8-R12's concern, later tasks).
- S3 object lifecycle/retention policy configuration (bucket-level WORM/Object Lock settings,
  7-year-retention infrastructure) — out of this application-code task's reach; a deployment/IaC
  concern, not something `ObservationSnapshotStore` itself configures.

## Business Rules

- **R4.** Every provider response is persisted verbatim to the observation log (Postgres + S3) before
  the quorum decision.

## Locked Decisions

- **L3.** Verbatim, write-first observation log — implemented via the ordering decision above.
- **L13.** No AWS credential committed; SDK default credential chain only.
- **L15.** New files under `observation/`; no cross-feature-module import.

## Dependencies

- `chain.observations` (T02, fixed schema) — see Scope for the exact column set.
- `SnapshotProperties` (T03) — `bucket`, `prefix`, `region`.
- `software.amazon.awssdk:s3` (new, this task) — no existing AWS-SDK-client-wiring precedent exists
  anywhere in this codebase yet (KMS's own real usage is still unbuilt, confined to the future attest
  module) — this task sets that precedent from a blank slate, unlike T06/T07's adapter-wiring pattern
  which had a direct sibling to mirror.
- `Clock` bean (`common/ClockConfig`, T04).
- `com.fasterxml.jackson.databind.ObjectMapper` (already a dependency) — if `rawResponse` needs
  re-serialization rather than passing an already-serialized provider payload straight through (exact
  call-site shape is Phase 5).
- `org.testcontainers:localstack` (new test dependency, this task).

## Inputs

- A provider's raw response payload (chain, txHash, provider name, factType, the verbatim payload
  itself) from whatever future caller task 9's `QuorumEvaluator` becomes — no such caller exists in
  this task's own scope; this task's own tests are the only caller.

## Outputs

- A persisted `chain.observations` row (verbatim `rawResponse`, `s3SnapshotKey` when the S3 write
  succeeded).
- An S3 object at the computed key, when the S3 write succeeds.

## State Changes

New rows in `chain.observations` (insert-only, per observation). New objects in the configured S3
bucket (write-once, never overwritten or deleted by this task's own code).

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/observation/Observation.java`
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStore.java`
- The coordinating class (exact name Phase 5) under `observation/`.
- An `S3Client`-wiring `@Configuration` class (exact name Phase 5) under `common/` or `observation/`.

## Files to Modify

- `services/crypto/pom.xml` — add `software.amazon.awssdk:s3` and `org.testcontainers:localstack`
  (test scope).
- `services/crypto/src/main/resources/application.properties` — no new property expected;
  `SnapshotProperties`' keys already exist (T03).

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`
  (T02) — frozen; `chain.observations`' shape and grants are consumed exactly as shipped.
- `common/config/SnapshotProperties.java` (T03) — consumed, not modified.
- `common/ClockConfig.java` (T04) — consumed, not modified.
- `events/OutboxEvent.java`/`OutboxEventRepository.java` — referenced as a pattern precedent only.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R4, L3).** A provider response can be persisted to `chain.observations` with `rawResponse`
  carrying the actual verbatim payload.
- **AC2 (L3).** The S3 write is attempted before the single Postgres insert; a successful S3 write's
  key is included in that same insert; a failed S3 write does not block the Postgres insert (which
  proceeds with `s3SnapshotKey = null`) but is logged distinctly.
- **AC3 (L3, grant-enforced).** No code path in `Observation`/`ObservationRepository` produces an
  `UPDATE` or `DELETE` against `chain.observations`.
- **AC4 ("Test ordering").** A test proves the S3-write-before-Postgres-insert ordering directly.
- **AC5 (L13).** No AWS credential hardcoded anywhere in `ObservationSnapshotStore` or its wiring.

## Required Tests

- `shouldLogEveryProviderResponseVerbatimToObservationLog` (package.md §8, named) — AC1.
- A test asserting `Observation` has no mutator beyond construction (AC3).
- A test asserting the coordinating class attempts the S3 write before calling the repository, and
  that the resulting entity's `s3SnapshotKey` matches what the (mocked, in this test) S3 write
  returned (AC2, AC4).
- A test asserting a failed S3 write still results in a persisted row with `s3SnapshotKey = null`,
  logged distinctly (AC2).
- A LocalStack-backed Testcontainers integration test proving a real S3 round-trip through
  `ObservationSnapshotStore` alone (AC1, AC5 — no real credential needed against LocalStack).

## Constraints

- **Transaction:** the Postgres insert should be the only database write in whatever transaction
  boundary the coordinating class establishes; the S3 write is not, and cannot be, part of that
  transaction (no distributed-transaction coordination exists between S3 and Postgres).
- **Thread-safety:** `ObservationSnapshotStore`/the coordinating class must be safe for concurrent use
  by multiple future callers (task 9's quorum evaluator will likely process multiple facts
  concurrently) — no shared mutable state beyond the injected `S3Client`/`Repository`/`Clock`, all of
  which are themselves expected to be thread-safe (AWS SDK clients and Spring Data repositories both
  are, by their own documented contracts).
- **Money (agents.md):** `rawResponse` is a verbatim payload, not a parsed monetary value — this task
  introduces no new `BigDecimal`/`NUMERIC` handling itself; whatever monetary content a provider
  response contains passes through as opaque JSON, exactly as received.
- **Secrets:** no AWS credential or S3 object content is ever logged; only the computed key (a
  structural identifier, not payload content) may appear in logs.
- **Null handling:** `s3SnapshotKey` is null whenever the S3 write failed — a normal, expected state,
  not an error condition on the Postgres row itself.

## Open Questions

No blockers. All four of Phase 1's open questions are resolved above (S3-write-ordering,
LocalStack-vs-mock testing, "Test ordering" interpretation, and the S3 key-naming scheme).
