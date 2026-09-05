# crypto · T08 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Observation log first. Implement `Observation` (append-only) + `ObservationSnapshotStore` (S3). Every
provider response is persisted verbatim before any quorum decision (L3, R4). Test ordering.

## Purpose

The defensible core of the platform: the durable, verbatim record of what each provider actually
said, written before any quorum logic ever runs against it. The first task to write into an
already-fully-shaped table (`chain.observations`, T02) rather than design its own schema, and the
first task needing a genuinely new external dependency (AWS S3).

## Scope

**In:**
- `Observation` — a JPA entity mapping `chain.observations` exactly as shipped (T02): `id` (DB
  IDENTITY), `chain`, `txHash`, `provider`, `factType`, `rawResponse`, `s3SnapshotKey` (nullable),
  `observedAt`. **Amendment #1: `rawResponse` is a `String` containing JSON**, mapped via
  `@JdbcTypeCode(SqlTypes.JSON)` — the identical pattern `OutboxEvent.payload` already uses. The
  **caller** is responsible for serializing the actual provider response (a `web3j`/`trident` Java
  object, or whatever shape a future sidecar sends) into that JSON string before it ever reaches this
  task's code; this task's own scope never touches a provider-specific type. Malformed/non-JSON input
  must fail loudly (rejected before any write is attempted), not be silently accepted into the column.
  **Fully immutable post-construction** — no setters, no mutation methods (unlike `OutboxEvent`'s
  `markPublished`) — matching `crypto_app`'s actual `INSERT, SELECT`-only grant on this table exactly,
  not just as a style choice. **Amendment #9: package-private no-arg constructor for JPA, a public
  static factory method for production construction (mirroring `OutboxEvent.create(...)` exactly),
  effectively-final fields, getters only.**
- **Amendment #5: `FactType`, a small enum** (`EXISTENCE`, `AMOUNT`, `TOKEN`, `CONFIRMATIONS`,
  `FINALITY` — the exact five values named in `V1__chain_baseline.sql`'s own column comment), with a
  JPA `AttributeConverter` mapping to/from the DB's lowercase string values. `Observation`'s factory
  method takes a `FactType`, not a bare `String`, closing the free-form-string inconsistency risk
  (mirrors `Chain`'s own T05 precedent).
- `ObservationRepository` — `JpaRepository<Observation, Long>`, package-private, derived-query finders
  only (mirrors `OutboxEventRepository`).
- `ObservationSnapshotStore` — writes the verbatim JSON payload to S3 (`SnapshotProperties.bucket`/
  `prefix`), returns the resulting key. Key scheme: `{prefix}{chain}/{txHash}/{factType}/
  {provider}-{observedAt-as-ISO-instant}-{random UUID}.json` — not based on the DB row's
  auto-generated `id`, because the S3 write happens before that id exists (see Ordering below); the
  random UUID exists because multiple providers report the same `(chain, txHash, factType)` by design
  (that repetition is the entire point of quorum), so the key must be unique per *observation*, not
  per fact. **Amendment #6: each `PutObject` call sets `Content-Type: application/json` and object
  metadata tags for `chain`, `txHash`, `provider`, `factType`** — cheap, directly serves L3's own
  stated defensibility/audit purpose.
- **Write ordering: S3 write attempted first, then exactly one Postgres `INSERT` carrying whatever
  `s3SnapshotKey` resulted.** Not a preference — forced by `crypto_app`'s grant having no `UPDATE`: an
  "insert row, then backfill the S3 key via update" pattern is structurally impossible against this
  schema. If the S3 write fails, the Postgres insert still proceeds with `s3SnapshotKey = null` (the
  DB row — which already carries the full verbatim `rawResponse` — is the load-bearing, R4-testable
  persistence guarantee; S3 is a supplementary WORM durability layer, not something that may block
  persistence if unavailable). An S3 failure is logged distinctly at error level, never silently
  swallowed. **Amendment #2: the `S3Client` is configured with a fixed 5-second API-call timeout**
  (`ClientOverrideConfiguration`/`apiCallTimeout`) — `SnapshotProperties` (T03, frozen) has no timeout
  field to consume, so this value is hardcoded in the wiring class, documented as deliberate given the
  frozen config shape, not user-configurable in this task's own scope. A timeout is treated identically
  to any other S3 failure (insert proceeds with `s3SnapshotKey = null`, logged distinctly). **Amendment
  #7: no additional application-level retry logic is added** — the AWS SDK v2 default client
  configuration already retries transient failures (throttling, 503 Slow Down) per its own standard
  retry policy; Phase 5/6 must confirm explicitly (not assume) that the timeout override in amendment
  #2 does not disable this default behavior. **Amendment #4: orphan S3 objects (a successful S3 write
  followed by a failed Postgres insert) are an accepted risk**, not mitigated by application code —
  bucket lifecycle/retention policy (deployment/IaC, already out of this task's scope) is the correct
  layer for eventual cleanup, not something `ObservationSnapshotStore` itself detects or reconciles.
  **Amendment #8: every call creates a distinct `Observation` row and S3 object — no deduplication.**
  The table has no unique constraint on `(chain, txHash, provider, factType)` by design (append-only);
  `observedAt` + the random UUID key component make each observation unique; task 9's quorum logic
  decides how to use multiple observations of the same fact, not this task.
- A small coordinating class (exact name Phase 5 — not named in design.md §6's package map, same
  "functionally necessary, not spec-named" situation `OutboxRelay`/the adapter `*Config` classes were
  in for T04/T06/T07) composing `ObservationSnapshotStore` + `ObservationRepository` into the single
  "persist verbatim" operation R4 describes.
- A `@Configuration` class building a real `software.amazon.awssdk.services.s3.S3Client` from
  `SnapshotProperties` — no credential ever hardcoded (L13); resolved via the AWS SDK's own default
  credential chain. **Amendment #3: test wiring uses a `@TestConfiguration` that replaces the
  production `S3Client` bean with one targeting the LocalStack container's dynamic endpoint** (path-style
  access enabled, static placeholder credentials — LocalStack accepts any non-empty values, no real AWS
  credential is ever needed against it).
- `observedAt` set from the existing injected `Clock` bean (`common/ClockConfig`, T04) — not
  `Instant.now()` inline.
- New dependency: `software.amazon.awssdk:s3` (no explicit version — inherits from the already-imported
  AWS SDK BOM). New test dependency: `org.testcontainers:localstack` (same pinned
  `testcontainers.version`).
- Testing: unit tests mock `S3Client` directly for `ObservationSnapshotStore`'s own key-naming/error-
  handling logic; one LocalStack-backed Testcontainers integration test proves a real S3
  `PutObject`/`GetObject` round-trip — S3 here is core persistence infrastructure (like Postgres/Kafka,
  both real-Testcontainers-tested already in this service), not an external RPC provider being
  quorum-fanned-out.
- **"Test ordering"**: a test proves the S3 write is attempted, and completes (success or failure),
  before the Postgres insert — entirely within this task's own code. **Amendment #10: the full R4
  intent ("before any quorum decision") is not end-to-end provable within this task's own scope**,
  since `QuorumEvaluator` (task 9) does not yet exist — task 9 (or a later end-to-end integration test)
  must itself assert `ObservationRepository` already contains the expected row(s) before it computes an
  outcome. AC4 here is deliberately scoped to this task's own internal ordering only.

**Out:**
- Any change to `V1__chain_baseline.sql`/`V2__crypto_app_role_and_grants.sql` (T02, frozen).
- `QuorumEvaluator`/`QuorumDecision` (task 9).
- `ProviderHealth`/`chain.provider.degraded` (task 10).
- Any `chain.*` event emission.
- S3 object lifecycle/retention policy configuration (bucket-level WORM/Object Lock, 7-year retention)
  — deployment/IaC concern.

## Business Rules

- **R4.** Every provider response is persisted verbatim to the observation log (Postgres + S3) before
  the quorum decision.

## Locked Decisions

- **L3.** Verbatim, write-first observation log — implemented via the ordering decision above.
- **L13.** No AWS credential committed; SDK default credential chain only (production); LocalStack's
  placeholder credentials only in tests (amendment #3).
- **L15.** New files under `observation/`; no cross-feature-module import.

## Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

All 10 Phase 3 findings accepted; see the numbered amendments (#1–#10) woven into Scope above.

1. `rawResponse` type pinned to `String` (JSON), caller-serialized; malformed input rejected (Kimi
   Issue 1).
2. Fixed 5-second `S3Client` API-call timeout, hardcoded given `SnapshotProperties`' frozen shape
   (Kimi Issue 2).
3. `@TestConfiguration`-based LocalStack `S3Client` override specified (Kimi Issue 3).
4. Orphan S3 objects accepted as a risk, mitigated at the deployment/IaC layer, not application code
   (Kimi Issue 4).
5. `FactType` enum + JPA converter introduced (Kimi Issue 5).
6. `Content-Type`/metadata tags specified for every `PutObject` (Kimi Issue 6).
7. No additional application-level S3 retry — AWS SDK v2's own default retry policy relied on,
   explicitly to be confirmed (not assumed) at Phase 5/6 (Kimi Issue 7).
8. Append-only, no-deduplication contract stated explicitly (Kimi Issue 8).
9. `Observation`'s JPA entity shape specified explicitly, mirroring `OutboxEvent` (Kimi Issue 9).
10. Cross-task R4 ordering proof explicitly deferred to task 9; AC4 scoped to this task's own internal
    ordering only (Kimi Issue 10).

**10 accepted, 0 rejected.**

## Dependencies

- `chain.observations` (T02, fixed schema).
- `SnapshotProperties` (T03) — `bucket`, `prefix`, `region`.
- `software.amazon.awssdk:s3` (new, this task) — no existing AWS-SDK-client-wiring precedent exists
  anywhere in this codebase yet.
- `Clock` bean (`common/ClockConfig`, T04).
- `com.fasterxml.jackson.databind.ObjectMapper` — only if the coordinating class itself needs to
  validate/parse the caller-supplied JSON string (amendment #1's "malformed input must fail loudly"),
  not to serialize a provider object (that remains the caller's job).
- `org.testcontainers:localstack` (new test dependency, this task).

## Inputs / Outputs / State Changes

Inputs: `(chain, txHash, provider, FactType, verbatim JSON payload string)` from whatever future
caller task 9's `QuorumEvaluator` becomes — no such caller exists in this task's own scope. Outputs: a
persisted `chain.observations` row; an S3 object at the computed key when the S3 write succeeds.
State: new insert-only rows in `chain.observations`; new write-once objects in the configured S3
bucket.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/observation/Observation.java`
- `services/crypto/src/main/java/com/themistra/crypto/observation/FactType.java`
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationRepository.java`
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStore.java`
- The coordinating class (exact name Phase 5) under `observation/`.
- An `S3Client`-wiring `@Configuration` class (exact name Phase 5) under `common/` or `observation/`.

## Files to Modify

- `services/crypto/pom.xml` — add `software.amazon.awssdk:s3` and `org.testcontainers:localstack`
  (test scope).

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `V3__crypto_app_outbox_grant.sql`
  (T02) — frozen.
- `common/config/SnapshotProperties.java` (T03) — consumed, not modified.
- `common/ClockConfig.java` (T04) — consumed, not modified.
- `events/OutboxEvent.java`/`OutboxEventRepository.java` — referenced as a pattern precedent only.
- `services/crypto/src/main/resources/application.properties` — no new property expected;
  `SnapshotProperties`' keys already exist (T03).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R4, L3).** A provider response can be persisted to `chain.observations` with `rawResponse`
  carrying the actual verbatim JSON payload; malformed JSON is rejected before any write (amendment
  #1).
- **AC2 (L3).** The S3 write is attempted before the single Postgres insert, with a 5-second timeout
  (amendment #2); a successful write's key is included in that insert; a failed/timed-out write does
  not block the insert (proceeds with `s3SnapshotKey = null`) but is logged distinctly.
- **AC3 (L3, grant-enforced).** No code path in `Observation`/`ObservationRepository` produces an
  `UPDATE` or `DELETE` against `chain.observations`.
- **AC4 ("Test ordering", scoped per amendment #10).** A test proves the S3-write-before-Postgres-
  insert ordering directly, within this task's own code only.
- **AC5 (L13).** No AWS credential hardcoded anywhere in `ObservationSnapshotStore` or its wiring.
- **AC6 (amendment #5).** `factType` is constrained to the `FactType` enum's five values, not a
  free-form string.
- **AC7 (amendment #6).** Every S3 object written carries `Content-Type: application/json` and
  `chain`/`txHash`/`provider`/`factType` metadata.

## Required Tests

- `shouldLogEveryProviderResponseVerbatimToObservationLog` (package.md §8, named) — AC1.
- A test asserting malformed/non-JSON `rawResponse` input is rejected before any write (AC1).
- A test asserting `Observation` has no mutator beyond construction (AC3).
- A test asserting the coordinating class attempts the S3 write before calling the repository, and
  that the resulting entity's `s3SnapshotKey` matches what the (mocked) S3 write returned (AC2, AC4).
- A test asserting a failed/timed-out S3 write still results in a persisted row with
  `s3SnapshotKey = null`, logged distinctly (AC2).
- A test asserting the `Content-Type`/metadata are set on the `PutObject` call (AC7 — mocked `S3Client`
  capturing the request).
- A LocalStack-backed Testcontainers integration test proving a real S3 round-trip (AC1, AC5, AC7).

## Constraints

- **Transaction:** the Postgres insert is the only database write in whatever transaction boundary the
  coordinating class establishes; the S3 write is not, and cannot be, part of that transaction.
- **Thread-safety:** no shared mutable state beyond the injected `S3Client`/`Repository`/`Clock`, all
  themselves thread-safe by their own documented contracts.
- **Money (agents.md):** `rawResponse` is a verbatim opaque JSON payload; this task introduces no new
  `BigDecimal`/`NUMERIC` handling.
- **Secrets:** no AWS credential or S3 object content is ever logged; only the computed key (a
  structural identifier) may appear in logs.
- **Null handling:** `s3SnapshotKey` is null whenever the S3 write failed or timed out — a normal,
  expected state, not an error condition on the Postgres row itself.

## Open Questions

No blockers.
