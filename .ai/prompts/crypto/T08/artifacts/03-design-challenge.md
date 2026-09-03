<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) for crypto · T08. -->

# crypto · T08 · Phase 3 — Design Challenge Findings

**Scope:** Adversarial review of the Phase 2 Task Implementation Brief for the observation log (T08) before it is frozen.

**Directive:** Do not redesign and do not implement. Surface hidden assumptions, ambiguous rules, missing edge cases, and conflicts with locked decisions / `spec/crypto-service/agents.md`. Each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Issue 1 — "Verbatim" provider payload is stored in a JSONB column, but the term "verbatim" is ambiguous for non-JSON responses

**Severity:** High

**Evidence:**
- `V1__chain_baseline.sql` defines `observations.raw_response` as `JSONB NOT NULL`.
- The brief repeatedly calls for the "verbatim" provider response to be persisted.
- Provider responses from `web3j`/`trident` are Java objects (e.g., `TransactionInfo`, `EthBlock`) or checked exceptions; they are not inherently JSON strings. Persisting them verbatim as bytes or protobuf is impossible in a JSONB column.
- The brief mentions `ObjectMapper` only as a possible dependency if `rawResponse` needs re-serialization, implying a transformation step.

A future caller (task 9) will not know whether to pass a JSON string, a Java object, or a provider-specific DTO, and whether the coordinator or the caller is responsible for serialization. This ambiguity risks either (a) storing a non-verbatim normalized shape or (b) runtime `JSONB` insertion failures.

**Recommended brief amendment:**
Define "verbatim" precisely: the provider's response serialized to JSON by the caller (or by the coordinator using `ObjectMapper`) before persistence. Specify the `Observation` constructor/`rawResponse` type (recommend `String` containing JSON, matching `OutboxEvent.payload`'s pattern with `@JdbcTypeCode(SqlTypes.JSON)`). Add an AC that invalid JSON must not be silently accepted.

---

## Issue 2 — S3 write attempted before Postgres insert can silently delay or block the quorum path; no S3 timeout is specified

**Severity:** High

**Evidence:**
- The brief's ordering decision requires the S3 write to be attempted *before* the Postgres insert.
- While the brief states an S3 *failure* does not block the insert, it does not address an S3 *timeout* or slow response. A hanging `PutObject` call would delay the insert (and therefore any downstream quorum decision) by however long the AWS SDK waits.
- There is no `S3Client` timeout configuration in the brief; `SnapshotProperties` only carries `bucket`, `prefix`, and `region`.
- The provider `timeoutSeconds` field is adapter-specific and not applicable here.

**Recommended brief amendment:**
Specify an S3 call timeout (e.g., 5 seconds) and enforce it in the `S3Client` wiring or per-request `PutObject` config. State explicitly whether the coordinator proceeds with `s3SnapshotKey = null` after a timeout, matching the failure semantics.

---

## Issue 3 — No strategy for overriding the S3 endpoint in the LocalStack integration test

**Severity:** Medium-High

**Evidence:**
- The brief requires a LocalStack-backed Testcontainers integration test that performs a real S3 round-trip.
- The planned `S3Client` wiring uses `SnapshotProperties.region` and the AWS SDK default credential chain, which targets real AWS endpoints.
- LocalStack requires an explicit endpoint override (e.g., `http://localhost:4566`) and a dummy region/credential pair.
- The brief does not specify how the production `S3Client` bean will be overridden for tests, nor where the LocalStack container lifecycle will live.

**Recommended brief amendment:**
Specify the test wiring strategy: either a `@TestConfiguration` that replaces the `S3Client` bean with one pointing at LocalStack, or constructor-level endpoint injection in `ObservationSnapshotStore`. Include the LocalStack container setup in the integration test class and document that no real AWS credentials are needed.

---

## Issue 4 — Orphan S3 objects are possible when the Postgres insert fails after a successful S3 write

**Severity:** Medium

**Evidence:**
- The ordering decision forces S3 first, then Postgres insert.
- If S3 succeeds but the insert fails (DB unavailable, validation error, grant violation), the S3 object exists with no corresponding `chain.observations` row.
- The brief says S3 is supplementary and the DB row is load-bearing, but it does not address orphan objects, their operational visibility, or cleanup.

**Recommended brief amendment:**
State whether orphan S3 objects are an accepted risk or require mitigation. If accepted, document that bucket lifecycle/retention policies (deployment/IaC) must handle eventual cleanup. If not accepted, consider including the DB `id` in the S3 key (impossible given the no-UPDATE grant) or adding a compensating audit/metric.

---

## Issue 5 — The set of valid `factType` values is not defined

**Severity:** Medium

**Evidence:**
- `V1__chain_baseline.sql` lists example values as comments: `existence | amount | token | confirmations | finality`.
- The brief does not specify whether `factType` is a free-form `String`, an enum, or a set of constants shared with task 9.
- A free-form string risks inconsistency across callers (e.g., `"confirmations"` vs `"CONFIRMATIONS"` vs `"confirmation_count"`), breaking queries that aggregate observations by fact type.

**Recommended brief amendment:**
Introduce a small, shared enum or constant class (e.g., `FactType`) with exactly the five values from the schema comment. Require the coordinator to accept only those values.

---

## Issue 6 — No S3 object metadata or content-type specified for the WORM snapshot

**Severity:** Low-Medium

**Evidence:**
- The brief defines the S3 key scheme but not the object's content-type, checksum, or metadata.
- For defensible audit storage (L3), an `application/json` content-type and an `x-amz-meta-*` tag indicating the observation's `(chain, txHash, provider, factType)` would make later retrieval and integrity checking easier.
- Without a content-type, a future retrieval tool may misinterpret the object.

**Recommended brief amendment:**
Specify that `PutObject` sets `Content-Type: application/json` and optionally includes metadata tags for `chain`, `txHash`, `provider`, and `factType`. This is a small addition with large audit/defensibility value.

---

## Issue 7 — No retry policy for S3 writes

**Severity:** Low-Medium

**Evidence:**
- The brief says an S3 failure is logged and the DB insert proceeds with `s3SnapshotKey = null`.
- Transient S3 errors (throttling, 503 Slow Down, temporary endpoint unavailability) are common in AWS; a single retry could significantly reduce gaps in the WORM mirror.
- The current design accepts a gap on the first transient failure.

**Recommended brief amendment:**
Decide and document whether `ObservationSnapshotStore` retries transient S3 failures (e.g., one or two retries with backoff) or fails fast. If retries are out of scope, state so explicitly and rely on the DB row + operational alerting.

---

## Issue 8 — No explicit deduplication or idempotency contract for repeated observations

**Severity:** Low-Medium

**Evidence:**
- The table has no unique constraint on `(chain, txHash, provider, factType)`; the schema is intentionally append-only.
- The brief does not state whether the coordinator should treat repeated calls for the same fact as separate observations or deduplicate them.
- For quorum, multiple observations of the same fact from the same provider could be either (a) useful signal over time or (b) noise/duplicates, depending on caller intent.

**Recommended brief amendment:**
Clarify that every call to the coordinator creates a distinct `Observation` row and S3 object (append-only, no deduplication). Document that the `observedAt` timestamp and random UUID make each observation unique, and that task 9's quorum logic is responsible for deciding how to use multiple observations.

---

## Issue 9 — `Observation` JPA constructor/field visibility is not specified

**Severity:** Low

**Evidence:**
- The brief requires `Observation` to be immutable with no setters and no mutation methods.
- JPA needs a no-args constructor (can be protected/package-private) and needs to populate fields, even if they are `private`.
- The brief does not specify whether fields are `final`, whether the constructor is public or package-private, or how the coordinating class constructs the entity.

**Recommended brief amendment:**
Specify that `Observation` uses a package-private no-args constructor for JPA, a public static factory method or all-args constructor for production code, `final` or effectively final fields, and getters only. Mirror `OutboxEvent.create(...)`'s factory pattern.

---

## Issue 10 — No end-to-end proof that observation persists before a quorum decision

**Severity:** Low

**Evidence:**
- AC4 requires a test proving S3-write-before-Postgres-insert ordering within this task's own code.
- R4's full intent is "persisted verbatim before any quorum decision" — but `QuorumEvaluator` (task 9) does not exist yet, so no test can prove the observation is written before a downstream quorum evaluation.
- The brief acknowledges there is no caller yet, but this leaves a cross-task verification gap.

**Recommended brief amendment:**
Add a note that the full R4 ordering invariant will be tested in task 9 (or an end-to-end integration test) by asserting that `ObservationRepository` contains the expected row(s) before `QuorumEvaluator` computes an outcome. AC4 remains scoped to this task's internal ordering.

---

## Summary table

| # | Issue | Severity | Recommended brief amendment |
|---|-------|----------|------------------------------|
| 1 | "Verbatim" vs JSONB ambiguity | High | Define serialization responsibility and `rawResponse` type |
| 2 | S3 timeout not specified | High | Add S3 call timeout and failure semantics |
| 3 | LocalStack S3 endpoint override strategy missing | Medium-High | Specify test-only `S3Client` override |
| 4 | Orphan S3 objects possible | Medium | Document acceptance or mitigation |
| 5 | `factType` values undefined | Medium | Introduce `FactType` enum/constants |
| 6 | S3 metadata/content-type unspecified | Low-Medium | Require `application/json` + optional metadata tags |
| 7 | No S3 retry policy | Low-Medium | Document retry-or-fail-fast decision |
| 8 | Deduplication/idempotency unclear | Low-Medium | State append-only, no deduplication |
| 9 | JPA constructor visibility unspecified | Low | Specify immutable JPA entity shape |
| 10 | Full R4 ordering not end-to-end testable yet | Low | Defer cross-task verification to task 9 |

(End of design challenge.)
