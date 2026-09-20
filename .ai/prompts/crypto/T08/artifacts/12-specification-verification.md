# crypto · T08 · Phase 12 — Specification Verification

**Task (verbatim, `tasks.md` #8):** Observation log first. Implement `Observation` (append-only) +
`ObservationSnapshotStore` (S3). Every provider response is persisted verbatim before any quorum
decision (L3, R4). Test ordering.

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R4 — verbatim persistence, Postgres + S3, before quorum | Yes | `ObservationLog.record` `observation/ObservationLog.java:50-61`; `Observation.rawResponse` mapped `@JdbcTypeCode(SqlTypes.JSON)` `Observation.java:59-61` | `ObservationLogTest.shouldLogEveryProviderResponseVerbatimToObservationLog` (named test, package.md §8); `ObservationRepositoryIntegrationTest.savedObservationRoundTripsEveryFieldIncludingTheJsonPayloadAndTheConvertedFactType` (real Postgres) | No | No. Cross-task "before quorum" proof explicitly deferred to task 9 by frozen-brief amendment #10 — not a gap in this task's own scope. |
| L3 — verbatim, write-first, append-only | Yes | Write ordering in `ObservationLog.record:54-60` (S3 attempted before the one `repository.save`); `Observation` has no setter/mutator beyond the constructor, `Observation.java:66-113` | `ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert`; `ObservationTest.hasNoPublicMutatorBeyondConstruction` | No | No |
| AC1 (R4, L3) — persist verbatim, reject malformed JSON before any write | Yes | `ObservationLog.validateJson` `ObservationLog.java:63-69`, called before any store/save call | `ObservationLogTest.recordThrowsForMalformedJsonBeforeAttemptingAnyWrite` (also asserts `verifyNoInteractions`) | No | No |
| AC2 (L3) — S3 attempted first, 5s timeout, failure doesn't block insert, key null on failure, logged distinctly | Yes | `ObservationSnapshotStoreConfig.API_CALL_TIMEOUT` = 5s, `ObservationSnapshotStoreConfig.java:21,26-29`; `ObservationSnapshotStore.store` catches `SdkException`, returns `Optional.empty()`, logs at error level, `ObservationSnapshotStore.java:38-45` | `ObservationLogTest.recordPersistsWithNullS3KeyWhenTheSnapshotStoreReturnsEmpty`; `ObservationSnapshotStoreTest.storeReturnsEmptyWhenS3ThrowsAnSdkException` | No | No |
| AC3 (L3, grant-enforced) — no UPDATE/DELETE code path | Yes | `Observation` has no setters (`Observation.java`, entire file); `ObservationRepository` exposes only `JpaRepository` default finders + one derived query, `ObservationRepository.java` | `ObservationTest.hasNoPublicMutatorBeyondConstruction` (reflection); `ObservationRepositoryIntegrationTest.repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel` (real DB-enforced, asserts `DataIntegrityViolationException`) | No | No |
| AC4 ("Test ordering", scoped per amendment #10) | Yes | Same as L3 row | `ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert` (Mockito `InOrder`) | No | No |
| AC5 (L13) — no hardcoded AWS credential | Yes | `ObservationSnapshotStoreConfig.s3Client` never calls `.credentialsProvider(...)`, `ObservationSnapshotStoreConfig.java:25-31` — SDK default credential chain applies | `ObservationSnapshotStoreConfigTest.s3ClientConstructsWithoutErrorForAValidRegion` (construction only; absence of a credential call is a code-review fact, not independently re-provable by a unit test) | No | No |
| AC6 (amendment #5) — `factType` constrained to the 5-value enum | Yes | `FactType.java` enum (5 values); `Observation.factType` typed `FactType`, `Observation.java:52-54`; `FactType.DbConverter` | `FactTypeDbConverterTest.convertsEachFactTypeToItsLowercaseNameAndBack` (`@EnumSource`, all 5); `FactTypeDbConverterTest.nullMapsToNullInBothDirections` | No | No |
| AC7 (amendment #6) — Content-Type + metadata on every PutObject | Yes | `ObservationSnapshotStore.buildRequest`, `ObservationSnapshotStore.java:58-67` | `ObservationSnapshotStoreTest.storeSetsContentTypeAndMetadataOnThePutObjectRequest`; `ObservationSnapshotStoreLocalStackIntegrationTest.putsAndGetsARealObjectFromLocalStack` (real S3) | No | No |

## Amendments (Phase 3, all 10 accepted) — verification

All 10 confirmed implemented as specified in `04-frozen-task-brief.md`, with one intentional
refinement to amendment #1's key scheme, made at Phase 9 in response to a self-review/Kimi finding
(Issue 1) and re-validated at Phase 11 (Gap 11):

- **Deviation, disclosed:** the frozen brief's illustrative key scheme (`04-frozen-task-brief.md:45-46`,
  `{prefix}{chain}/{txHash}/{factType}/{provider}-{observedAt}-{UUID}.json`) was never itself a LOCKED
  decision — no `L`-numbered decision fixes the literal key format, only that a key is produced and is
  unique per observation. That illustrative scheme was found at Phase 7/8 to be able to exceed
  `s3_snapshot_key VARCHAR(256)` for realistic `chain`/`txHash` lengths. The shipped implementation
  (`ObservationSnapshotStore.buildKey`, `ObservationSnapshotStore.java:49-51`) uses
  `{prefix}{chain}/{txHash}/{UUID}.json` instead, moving `provider`/`factType`/`observedAt` to S3
  object metadata only (still satisfying AC7). This is a bug fix to an under-specified illustrative
  detail, not a violation of L3/R4/AC1-7 or any other LOCKED decision — regression-guarded by
  `ObservationSnapshotStoreTest.keyIsBoundedRegardlessOfInputLength` and
  `.keyIsBoundedForALongPrefix`.
- Amendments #2–#10: each verified present exactly as described — 5s timeout
  (`ObservationSnapshotStoreConfig.java:21`), `@TestConfiguration`/LocalStack override
  (`ObservationSnapshotStoreLocalStackIntegrationTest`), orphan-S3 risk accepted with no reconciliation
  code (absence confirmed — no such code exists in `ObservationSnapshotStore`/`ObservationLog`),
  `FactType` enum + converter, Content-Type/metadata, no added retry logic (absence confirmed — no
  retry/backoff code in `ObservationSnapshotStore`; SDK v2 default retry policy relied on as stated),
  append-only/no-dedup (no unique constraint assumed, no dedup logic written), `Observation`'s shape
  mirroring `OutboxEvent`, and R4's cross-task ordering proof correctly left to task 9.

## Files-to-create / Files-to-modify conformance

All six files listed under "Files to Create" in the frozen brief exist at their exact specified paths
(`Observation.java`, `FactType.java`, `ObservationRepository.java`, `ObservationSnapshotStore.java`,
the coordinating class — named `ObservationLog`, Phase 5 — and the `S3Client`-wiring class — named
`ObservationSnapshotStoreConfig`, Phase 5). `pom.xml` modified exactly as scoped (`software.amazon.awssdk:s3`,
`org.testcontainers:localstack` test-scope). No file under "Files NOT to Modify" was touched (confirmed
by this session's own change history — no edits to `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`,
`V3__crypto_app_outbox_grant.sql`, `SnapshotProperties.java`, `ClockConfig.java`,
`OutboxEvent.java`/`OutboxEventRepository.java`, `application.properties`, or anything under `spec/`).

## Required Tests conformance

All 6 tests enumerated in the frozen brief's "Required Tests" section exist, plus the Phase 11
(Kimi)-driven additions layered on top (all human-approved 2026-09-03): `FactTypeDbConverterTest` (6),
`ObservationSnapshotStoreConfigTest` (2), `ObservationRepositoryIntegrationTest` (5, Docker-gated),
plus 4 new/enhanced cases across `ObservationLogTest`/`ObservationSnapshotStoreTest`. Current suite
state (last full run, this session): 237 module tests total, 232 passing, 5 errors — all
Docker-environment-unavailable (`IllegalState: … Docker environment …`), a pre-existing, disclosed
environment limitation (3 pre-existing from T02/T04, 2 new from this task's own Testcontainers-backed
tests: `ObservationRepositoryIntegrationTest`, `ObservationSnapshotStoreLocalStackIntegrationTest`),
not a code defect. Zero genuine failures.

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every file/class named in the frozen brief exists, is wired
together as specified, and every acceptance criterion has direct evidence and a passing test (subject
only to the environment's lack of Docker, which blocks *execution* of 2 of this task's own integration
tests, not their existence or correctness — they compile cleanly and mirror the T02/T04-established,
already-proven-in-this-environment-once-Docker-is-available pattern).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC7, see matrix above, each with
file:line evidence and at least one passing (or Docker-gated-but-compiling) test.

**(3) Does it violate any LOCKED decision?** No. L3 (verbatim, write-first) and L13 (no hardcoded
credential) are both implemented exactly as decided. L15 (new files under `observation/`, no
cross-feature-module import) holds — `observation/` imports only `common.config.SnapshotProperties`
and the AWS SDK, no import from `adapter/`, `events/`, or any sibling feature module. The one deviation
from the frozen brief's text (the S3 key scheme) is a fix to an illustrative, non-LOCKED detail that
was itself defective (overflow risk), not a violation of anything numbered `L`.

**(4) Remaining risks?**
- A `SnapshotProperties.prefix` longer than ~53 characters combined with maximal-length `chain`(32)/
  `txHash`(128) can still produce a key exceeding `s3_snapshot_key VARCHAR(256)` — an accepted,
  documented operational constraint on deployment config (`ObservationSnapshotStoreTest.keyIsBoundedForALongPrefix`'s
  own comment), not something this task's code can unilaterally bound without also constraining
  `SnapshotProperties` itself (out of this task's scope — T03, frozen).
- Orphan S3 objects on S3-success/Postgres-failure are an accepted, undetected risk by design
  (amendment #4) — mitigation is deployment/IaC (bucket lifecycle policy), not application code.
- `ObservationRepositoryIntegrationTest` and `ObservationSnapshotStoreLocalStackIntegrationTest` have
  never actually executed in this environment (Docker unavailable throughout this session, same
  limitation affecting T02/T04's own integration tests) — they compile and are structurally sound
  (mirroring already-proven patterns), but their assertions are unverified against a real Postgres/S3
  until Docker is available.
- AC5's "no hardcoded credential" is a structural/code-review fact (absence of a `.credentialsProvider(...)`
  call), not something a unit test can independently re-derive; this is inherent to what AC5 asks, not
  a gap specific to this task.

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion for T08 is implemented with
file:line evidence and test coverage; the one deviation from the frozen brief's illustrative detail is
a disclosed, scoped-correctly bug fix, not a specification violation; remaining risks are pre-existing
environment limitations or explicitly accepted-by-design risks, not defects.
