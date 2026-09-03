# crypto · T08 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS** (`artifacts/12-specification-verification.md`). Proceeding to prepare T08
for merge. Branches off `main`; `main` remains deployable throughout — no commit in this task touches
anything outside `services/crypto/` (plus this task's own `.ai/prompts/crypto/T08/` artifacts).

## Commit title

```
crypto: add verbatim observation log with S3 snapshot (T08)
```

## Commit message

```
crypto: add verbatim observation log with S3 snapshot (T08)

Implement Observation (append-only JPA entity over chain.observations,
T02) and ObservationSnapshotStore (S3), composed by ObservationLog into
the single "persist a provider response verbatim" operation R4/L3
describe. Every observation is written to S3 first, then exactly one
Postgres insert carries whatever key resulted - forced by crypto_app's
INSERT/SELECT-only grant, which makes an insert-then-backfill pattern
structurally impossible. A failed or timed-out S3 write (5s timeout)
never blocks the Postgres insert; it proceeds with s3SnapshotKey = null
and is logged distinctly. Malformed JSON is rejected before either
write is attempted.

FactType constrains fact_type to its five known values via a JPA
AttributeConverter rather than a free-form string. ObservationSnapshotStoreConfig
wires the S3Client from SnapshotProperties (T03) with no hardcoded
credential (L13) and no application-level retry beyond the AWS SDK v2
default policy.

Kimi design/independent/test review findings (Phases 3, 8, 11) were
triaged and folded in, most notably: bounding the S3 key length against
s3_snapshot_key's own VARCHAR(256) (the original illustrative key scheme
could overflow it for realistic chain/txHash lengths), and removing
@Transactional from ObservationLog.record so the S3 network call no
longer holds a pooled DB connection open (Spring Data's own
SimpleJpaRepository.save is already individually transactional).

Testing gated on Docker (ObservationRepositoryIntegrationTest,
ObservationSnapshotStoreLocalStackIntegrationTest) has not executed in
this environment - a pre-existing limitation already affecting T02/T04's
own integration tests, disclosed throughout this task's artifacts, not
a defect in this change.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Main:**
- `services/crypto/pom.xml` — modified (adds `software.amazon.awssdk:s3`, `org.testcontainers:localstack` test-scope)
- `services/crypto/src/main/java/com/themistra/crypto/observation/FactType.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/observation/Observation.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationLog.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationRepository.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStore.java` — new
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStoreConfig.java` — new

**Test:**
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationLogTest.java` — new, extended at Phase 11 (+2)
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreTest.java` — new, extended at Phase 11 (+2, 1 enhanced)
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreLocalStackIntegrationTest.java` — new
- `services/crypto/src/test/java/com/themistra/crypto/observation/FactTypeDbConverterTest.java` — new (Phase 11)
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreConfigTest.java` — new (Phase 11)
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationRepositoryIntegrationTest.java` — new (Phase 11)

**Pipeline artifacts:**
- `.ai/prompts/crypto/T08/artifacts/00-repository-understanding.md` through `13-pr-preparation.md` — all 14 phase artifacts

## Summary

T08 adds the platform's defensible core: a durable, verbatim, append-only record of every provider
response, written before any quorum decision ever runs against it (R4, L3). `Observation` maps
`chain.observations` exactly as shipped by T02, immutable post-construction to match `crypto_app`'s
INSERT/SELECT-only grant. `ObservationSnapshotStore` is this codebase's first S3 integration, storing
the same verbatim payload as a WORM-durable object alongside the Postgres row. `ObservationLog`
composes the two into one operation with a forced write order (S3, then Postgres) and a documented,
accepted failure mode (S3 failure never blocks persistence; the resulting orphan-object risk on the
inverse failure is accepted, mitigated at the deployment/IaC layer, not application code).

## Testing performed

- `mvn -pl services/crypto test-compile` — BUILD SUCCESS, 36 source files, no new warnings.
- `mvn -pl services/crypto test -Dtest=ObservationTest,ObservationLogTest,ObservationSnapshotStoreTest,FactTypeDbConverterTest,ObservationSnapshotStoreConfigTest` — 27/27 passing.
- `mvn -pl services/crypto -am test` (full module suite) — 237 tests, 232 passing, 5 errors, all
  `IllegalState: … Docker environment …` (3 pre-existing from T02/T04's own Testcontainers integration
  tests, 2 new from this task's own `ObservationRepositoryIntegrationTest` and
  `ObservationSnapshotStoreLocalStackIntegrationTest`) — zero genuine failures.
- Negative-proof (mutation) testing applied to the Phase 9 S3-key-length-bound fix: reverted the fix,
  confirmed the regression test fails, restored the fix, confirmed green again.
- Docker unavailable throughout this session — the two Testcontainers-backed T08 tests compile cleanly
  and mirror already-established, previously-proven-once-Docker-is-available patterns
  (`OutboxTransactionIntegrationTest`), but have not themselves executed against a real
  Postgres/LocalStack in this environment.

## Specification references

- **Task:** T08 — Observation log first (`spec/crypto-service/tasks.md` #8).
- **Requirement:** R4 (`spec/crypto-service/requirements.md:10`).
- **Locked decisions:** L3 (`spec/crypto-service/design.md:7`) — verbatim, write-first observation log;
  L13 — no hardcoded AWS credential; L15 — new files confined to `observation/`, no cross-feature-module
  import.
- **Named test:** `shouldLogEveryProviderResponseVerbatimToObservationLog` (`package.md` §8).
- **Contracts:** none of `contracts/api/crypto-internal.yaml`, `contracts/events/chain/`,
  `contracts/events/chain/tx-finalized.v1.schema.json` are touched by this task — `Observation` is
  purely internal persistence, no API/event surface (consistent with T08's own scope: task 9's
  `QuorumEvaluator` is the first task to reach an event/contract boundary from this data).
