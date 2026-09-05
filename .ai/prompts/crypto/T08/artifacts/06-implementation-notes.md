# crypto · T08 · Phase 6 — Implementation Notes

## Files changed

- `services/crypto/src/main/java/com/themistra/crypto/observation/FactType.java` (new, 37 lines).
- `services/crypto/src/main/java/com/themistra/crypto/observation/Observation.java` (new, 114 lines).
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationRepository.java` (new,
  10 lines).
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStore.java` (new,
  68 lines).
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationLog.java` (new, 63 lines).
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStoreConfig.java`
  (new, 39 lines).
- `services/crypto/pom.xml` (modified) — added `software.amazon.awssdk:s3` (no explicit version,
  inherits the existing BOM import) and `org.testcontainers:localstack` (test scope, existing
  `testcontainers.version` property).

All seven were already on the frozen brief's Files-to-Create/Modify list — no file outside that list
was touched. `mvn -pl services/crypto -am compile` — `BUILD SUCCESS` on the first attempt, zero
warnings. `mvn -pl services/crypto -am test` — 208 tests, 0 failures, same 3 pre-existing
Docker-unavailable errors as before this task; no regression.

## Mapping to the frozen brief and acceptance criteria

| Frozen brief item | Implementation |
|---|---|
| AC1 — `rawResponse` verbatim JSON, malformed rejected | `Observation.rawResponse` (`String`, `@JdbcTypeCode(SqlTypes.JSON)`); `ObservationLog.validateJson` |
| AC2 — S3-before-Postgres ordering, 5s timeout, null-key on failure, logged distinctly | `ObservationLog.record` (S3 attempt then single `repository.save`); `ObservationSnapshotStoreConfig`'s `apiCallTimeout`; `ObservationSnapshotStore.store`'s `catch (SdkException)` |
| AC3 — no `UPDATE`/`DELETE` code path | `Observation` has no setters/mutators anywhere; `ObservationRepository` only ever receives new (transient) entities from `ObservationLog` |
| AC4 — "Test ordering" | Structurally guaranteed by `ObservationLog.record`'s own statement order (S3 call precedes `repository.save`) — Phase 10 to add the explicit `InOrder`-verified test |
| AC5 — no hardcoded AWS credential | `ObservationSnapshotStoreConfig.s3Client` sets no `credentialsProvider(...)` — SDK default chain applies |
| AC6 — `factType` constrained to the enum | `FactType` (5 values) + `FactType.DbConverter`, applied via explicit `@Convert` on `Observation.factType` |
| AC7 — S3 object `Content-Type`/metadata | `ObservationSnapshotStore.buildRequest` |

## Design decisions carried through exactly as frozen

- **Explicit `@Convert` over `autoApply`, one refinement beyond the Phase 5 plan's own sketch.** The
  plan's signature sketch showed `@Convert(converter = FactType.Converter.class)`; while writing the
  actual converter, `autoApply = true` was considered first (matching a common JPA convenience
  pattern) but rejected in favor of an explicit `@Convert` reference on `Observation.factType` — safer
  against the converter ever accidentally attaching to some other, unrelated enum-typed column
  elsewhere in this growing codebase. A small, deliberate improvement over the sketch, not a deviation
  from the frozen brief's actual intent (AC6).
- **`ObservationSnapshotStore.store` never throws** — every failure path (a genuine S3 service error,
  a timeout via the `ClientOverrideConfiguration.apiCallTimeout`, since both surface as `SdkException`
  or a subtype) is caught internally, logged at error level with the bucket/key (never the payload
  content — Secrets constraint), and reported as `Optional.empty()`. `ObservationLog` never imports any
  AWS-SDK-specific exception type at all, keeping the S3-specific failure vocabulary fully contained.

## Deviations forced by reality (none to report this phase)

Unlike T07, this phase found no deviations between the Phase 5 plan and what actually compiled — every
verified AWS SDK v2 / JPA API signature from Phase 5's own inspection (`S3Client.putObject`,
`PutObjectRequest.Builder.contentType`/`.metadata`, `ClientOverrideConfiguration.Builder.apiCallTimeout`,
`jakarta.persistence.AttributeConverter`) matched exactly on first compile.

## Known limitation carried forward (not a defect in this phase's own work)

**No Spring context boot or Hibernate entity-mapping validation has been exercised against a real
Postgres instance in this environment** — Docker has been unavailable throughout this session (the
same pre-existing limitation T02/T04/T06/T07 all carried forward for their own Testcontainers-backed
tests). `Observation`'s column mapping was cross-checked carefully, field-by-field, directly against
`V1__chain_baseline.sql`'s actual DDL (not from memory or the earlier Phase 0 summary) rather than
verified by an actual Hibernate boot. `ObservationSnapshotStoreConfig`'s `S3Client` bean construction
has likewise not been exercised inside a real Spring context yet. Phase 10's LocalStack-backed
integration test (frozen brief's own Required Tests) is where this gets real, executable verification
for the first time — assuming Docker becomes available by then; if not, the same known,
already-disclosed limitation applies, not a new one.

## Not yet done (explicitly out of this phase)

Tests (`ObservationTest`, `ObservationSnapshotStoreTest`, `ObservationLogTest`,
`ObservationSnapshotStoreLocalStackIntegrationTest`) are Phase 10's job per the Phase 6 directive —
none were written in this phase.
