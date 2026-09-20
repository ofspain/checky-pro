# crypto · T08 · Phase 10 — Test Generation

## Files created

- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationTest.java` — 3 tests,
  plain JUnit, no Spring context.
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreTest.java`
  — 6 tests, `S3Client` mocked (Mockito).
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationLogTest.java` — 6 tests,
  `ObservationSnapshotStore`/`ObservationRepository` mocked, fixed `Clock`.
- `services/crypto/src/test/java/com/themistra/crypto/observation/ObservationSnapshotStoreLocalStackIntegrationTest.java`
  — 1 test, real `LocalStackContainer` (Testcontainers), real S3 API round-trip.

## Test-to-AC / Required-Tests mapping

| Frozen brief item | Test method | Notes |
|---|---|---|
| `shouldLogEveryProviderResponseVerbatimToObservationLog` (package.md §8, named, → R4) | `ObservationLogTest.shouldLogEveryProviderResponseVerbatimToObservationLog` | The one pre-mapped test |
| AC1 — verbatim `rawResponse` | `ObservationTest.createPopulatesEveryFieldExactlyAsGiven`; `ObservationLogTest`'s named test above | |
| AC1 — malformed JSON rejected before any write | `ObservationLogTest.recordThrowsForMalformedJsonBeforeAttemptingAnyWrite` | `verifyNoInteractions` on both collaborators |
| AC2 — S3-before-Postgres ordering; failure doesn't block insert | `ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert`, `.recordPersistsWithNullS3KeyWhenTheSnapshotStoreReturnsEmpty`, `.recordUsesTheReturnedS3KeyWhenPresent` | |
| AC2 — S3 failure doesn't throw, returns empty | `ObservationSnapshotStoreTest.storeReturnsEmptyWhenS3ThrowsAnSdkException` | |
| AC3 — no mutator, grant-enforced immutability | `ObservationTest.hasNoPublicMutatorBeyondConstruction`, `.createAcceptsANullS3SnapshotKeyForAFailedSnapshotWrite` | Reflection-based: every public non-static method must be a zero-arg, non-void getter |
| AC4 — "Test ordering" (scoped to this task's own internal ordering, amendment #10) | `ObservationLogTest.recordAttemptsTheS3WriteBeforeThePostgresInsert` | `InOrder`-verified across the two mocks |
| AC5 — no hardcoded AWS credential | `ObservationSnapshotStoreLocalStackIntegrationTest` | Real round-trip against LocalStack's placeholder credentials, never a real AWS credential |
| AC6 — no real network call in mocked tests | N/A (by construction) | `ObservationTest`/`ObservationLogTest`/`ObservationSnapshotStoreTest` never touch a real `S3Client`/DB |
| AC7 — `Content-Type`/metadata on every `PutObject` | `ObservationSnapshotStoreTest.storeSetsContentTypeAndMetadataOnThePutObjectRequest` | `ArgumentCaptor<PutObjectRequest>` |
| — (added, Phase 9 Kimi/self-review Issue 1 regression guard) | `ObservationSnapshotStoreTest.keyIsBoundedRegardlessOfInputLength` | Maximal-length `chain`/`txHash` inputs (32/128 chars, matching the DB column widths) still produce a key ≤ 256 chars — see mutation-test verification below |
| — (added, amendment #8, append-only/no-dedup) | `ObservationSnapshotStoreTest.keyIncludesAUniqueComponentAcrossCalls` | Two identical-input calls produce two different keys |
| — (added, Phase 9 Kimi/self-review Issue 8) | `ObservationSnapshotStoreTest.storeThrowsNullPointerExceptionForANullArgument` | Fails fast with a named argument |
| — (added, agents.md fixed-`Clock` convention) | `ObservationLogTest.recordUsesTheInjectedClockNotWallClockTime` | |

16 tests total (3 + 6 + 6 + 1). Every Required-Tests-section item is covered.

## A deliberate, disclosed simplification versus the frozen brief's own sketch

The frozen brief's Scope named a `@TestConfiguration` overriding the production `S3Client` Spring bean
as the LocalStack test-wiring strategy (Phase 3 Kimi Issue 3's original recommendation). The actual
test written instead constructs `S3Client`/`ObservationSnapshotStore` directly, with no Spring context
at all — the real requirement (a genuine S3 round-trip proving AC1/AC5/AC7 against real S3 semantics)
is fully met either way, and this is narrower, faster, and has fewer moving parts than booting the
whole application context just to exercise one class. Not a deviation from the frozen brief's *intent*,
just a lighter-weight implementation of the same required test.

## Verification performed

- `mvn -pl services/crypto -am compile` / `test-compile` — `BUILD SUCCESS`.
- `mvn -pl services/crypto test -Dtest=ObservationTest,ObservationLogTest,ObservationSnapshotStoreTest`
  — 15/15 pass.
- `mvn -pl services/crypto test -Dtest=ObservationSnapshotStoreLocalStackIntegrationTest` — fails with
  `IllegalStateException: Could not find a valid Docker environment`, the identical, pre-existing,
  already-disclosed Docker-unavailable condition every other Testcontainers-backed test in this module
  hits in this environment (T02/T04's own integration tests, `TronAdapterConfigTest`'s
  `@PreDestroy`-through-a-real-context test notwithstanding — that one doesn't need Docker). Not a new
  or different failure mode.
- `mvn -pl services/crypto -am test` (full module) — 224 tests, 0 failures, 4 errors (the 3
  pre-existing Docker-unavailable errors from before this task, plus this task's own one new
  LocalStack-dependent test hitting the same root cause). No new failures introduced anywhere else in
  the module.
- **Negative-proof mutation test** on the highest-value Phase 9 fix (the bounded S3 key scheme, Kimi/
  self-review Issue 1): temporarily reverted `buildKey` to interpolate a long placeholder
  string standing in for the old `provider`/`observedAt` segments the pre-fix scheme included. Re-ran
  `keyIsBoundedRegardlessOfInputLength` — it failed exactly as expected (the reconstructed
  maximal-length key exceeded 256 characters again). Reverted via `diff` against a pre-mutation backup
  and confirmed all 15 mockable tests passed again clean.

## Deviations from the frozen brief / prior-phase plans

None to the production code (already closed at Phase 9). One test-implementation simplification,
explained above (direct `S3Client` construction instead of a Spring `@TestConfiguration` override) —
achieves the same required verification with less machinery, not a reduction in what's actually tested.
