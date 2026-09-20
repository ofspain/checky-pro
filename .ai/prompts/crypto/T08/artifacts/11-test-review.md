<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) for crypto · T08. -->

# crypto · T08 · Phase 11 — Test Review Findings

**Scope:** Review the Phase 10 test suite (`ObservationTest`, `ObservationSnapshotStoreTest`, `ObservationLogTest`, `ObservationSnapshotStoreLocalStackIntegrationTest`) against the frozen brief's acceptance criteria and `spec/crypto-service/agents.md`.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap · Why it matters · Suggested test.**

---

## Gap 1 — No test that `ObservationSnapshotStore.store` survives non-`SdkException` runtime failures

**Why it matters:** The production code catches only `SdkException`. If `s3Client.putObject` throws a different runtime exception (e.g., `NullPointerException` from a misconfigured client or an unexpected AWS wrapper), the exception propagates out of `store(...)` and the Postgres insert is skipped — violating AC2's requirement that an S3 failure must not block persistence.

**Suggested test:** Add `storeReturnsEmptyWhenS3ThrowsAnUnexpectedRuntimeException` that stubs `s3Client.putObject` to throw a plain `RuntimeException` and asserts `store(...)` returns `Optional.empty()` without throwing.

---

## Gap 2 — No test that the `S3Client` bean is closed on Spring context shutdown

**Why it matters:** AWS SDK v2 clients hold native resources and should be closed. The production `ObservationSnapshotStoreConfig` creates a real `S3Client` bean but provides no shutdown hook. Without a test, a regression that removes a future `@PreDestroy`/close path would not be caught.

**Suggested test:** Add a unit test using a disposable Spring context or a `DisposableBean` assertion that verifies `s3Client.close()` is invoked when the context closes. If the production code is updated to close the client, this test guards it; if not, it documents the gap.

---

## Gap 3 — No test for `ObservationSnapshotStoreConfig` bean wiring

**Why it matters:** AC5 requires no hardcoded AWS credentials and the brief requires the S3 client to be built from `SnapshotProperties`. The existing tests either mock `S3Client` entirely or construct one manually for LocalStack; nothing verifies the production `@Configuration` class wires region and timeout correctly.

**Suggested test:** Add `ObservationSnapshotStoreConfigTest` that boots a minimal Spring context with mocked `SnapshotProperties` and asserts the `S3Client` bean is created with the configured region and a 5-second timeout (e.g., by reflecting on `ClientOverrideConfiguration`).

---

## Gap 4 — No `@DataJpaTest` or integration test proving `Observation` maps to `chain.observations` and `ObservationRepository` queries work

**Why it matters:** `Observation` uses a custom `FactType` JPA converter and `@JdbcTypeCode(SqlTypes.JSON)` for `rawResponse`. These mappings are not exercised in the current test suite, which mocks the repository or uses plain JUnit. A converter bug or JSONB mapping issue would only surface at integration-test time.

**Suggested test:** Add a `@DataJpaTest`-style test that inserts an `Observation` via `ObservationRepository`, reads it back, and asserts all fields (including the JSONB payload and the lowercased `factType`) round-trip correctly. Also exercise `findByChainAndTxHashAndFactType`.

---

## Gap 5 — No integration test for `ObservationLog.record` with a real database and real S3

**Why it matters:** `ObservationLogTest` mocks both collaborators, so it proves ordering and wiring but not actual persistence. The LocalStack test covers S3 in isolation. The critical path — "S3 write attempted first, then exactly one Postgres insert" — has not been proven with real infrastructure on both sides.

**Suggested test:** Add an integration test (Testcontainers Postgres + LocalStack S3) that calls `ObservationLog.record(...)` and asserts the row exists in the database and the object exists in S3. Add a second variant where S3 is unavailable (e.g., point the S3 client at a bad endpoint) and assert the DB row is still inserted with `s3SnapshotKey = null`.

---

## Gap 6 — No test for the orphan-S3-object case when the Postgres insert fails after S3 succeeds

**Why it matters:** The ordering decision (S3 first, then DB) means a DB failure after a successful S3 write creates an orphan S3 object with no corresponding row. The brief acknowledges this risk but does not test the behavior. A regression that changed error handling could either lose the DB insert or retry incorrectly.

**Suggested test:** Add `recordLogsAndRethrowsWhenPostgresInsertFails` that stubs `snapshotStore.store` to return a key and `repository.save` to throw a `DataAccessException`, asserting the exception propagates and the S3 key is mentioned in logs. This documents the orphan-object outcome rather than hiding it.

---

## Gap 7 — No test verifying the `PutObjectRequest.key` matches the key returned by `store(...)`

**Why it matters:** `ObservationSnapshotStoreTest.storeReturnsTheComputedKeyOnSuccess` only asserts the returned key's prefix/suffix. It does not capture the `PutObjectRequest` and assert that the key actually sent to S3 is identical to the returned value. A bug that generated one key for the request and a different key for the return value would not be caught.

**Suggested test:** Enhance `storeReturnsTheComputedKeyOnSuccess` (or add a dedicated test) that captures the `PutObjectRequest` and asserts `request.key().equals(returnedKey)`.

---

## Gap 8 — No regression guard ensuring `ObservationLog.record` remains non-`@Transactional`

**Why it matters:** The Phase 9 fix removed `@Transactional` from `ObservationLog.record` to avoid holding a DB connection during the S3 call. A future refactor could re-add it. There is no test that would fail if the annotation returns.

**Suggested test:** Add a reflection-based test (`ObservationLogTest.recordIsNotAnnotatedTransactional`) that asserts neither `ObservationLog.record` nor the class itself carries `@Transactional`. Include a comment explaining why.

---

## Gap 9 — No direct test for `FactType.DbConverter` round-trip

**Why it matters:** The converter maps `FactType` to lowercase strings and back. It is only tested indirectly through `ObservationLogTest`. If the converter drifts (e.g., starts storing uppercase values), the failure mode is a DB-level mismatch that is harder to diagnose than a unit test failure.

**Suggested test:** Add `FactTypeDbConverterTest` that asserts each `FactType` converts to its lowercase name and converts back from the lowercase string, and that `null` maps to `null` in both directions.

---

## Gap 10 — No test that `Observation.create` rejects null required fields

**Why it matters:** `Observation.create` currently accepts nulls silently; JPA/database constraints catch them later. Failing fast at construction time would make unit tests clearer and prevent invalid domain objects from being created in memory.

**Suggested test:** Add parameterized tests asserting `Observation.create(...)` throws `NullPointerException` (or `IllegalArgumentException`) when each required argument is null. If the production code is not changed to add such guards, this gap documents the decision.

---

## Gap 11 — No test that the S3 key scheme remains bounded for long `SnapshotProperties.prefix` values

**Why it matters:** `keyIsBoundedRegardlessOfInputLength` tests maximal `chain`/`txHash` lengths but uses the fixed prefix `"chain-observations/"`. `SnapshotProperties.prefix` has no maximum length validation; a deployment with a longer prefix could push the key over 256 characters.

**Suggested test:** Add `keyIsBoundedForALongPrefix` that uses a prefix close to the practical limit (e.g., 100 characters) with maximal `chain`/`txHash` and asserts the resulting key is still ≤ 256 characters.

---

## Gap 12 — No test asserting the S3 failure log message does not contain payload content

**Why it matters:** The brief's Security constraint says "no AWS credential or S3 object content is ever logged; only the computed key ... may appear in logs." The current failure log includes `bucket` and `key` but does not log the raw payload. A regression that added the payload to the log would be a secret-leak risk.

**Suggested test:** Add `storeDoesNotLogRawResponseOnFailure` that stubs an S3 failure, captures logs with a test appender, and asserts the raw JSON payload does not appear in the logged output.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | Non-`SdkException` S3 failures not tested | S3 failure blocks DB insert | Stub `RuntimeException` in `store` |
| 2 | `S3Client` shutdown not tested | Resource leak | Context-close/disposable bean test |
| 3 | `ObservationSnapshotStoreConfig` wiring untested | Wrong region/timeout | Minimal Spring context test |
| 4 | `Observation` JPA mapping untested | Converter/JSONB bugs | `@DataJpaTest` round-trip |
| 5 | `ObservationLog` end-to-end integration untested | Ordering not proven with real infra | Postgres + LocalStack integration test |
| 6 | Orphan S3 object on DB failure untested | Undocumented failure mode | Stub `repository.save` to throw |
| 7 | Request key vs returned key untested | Mismatched S3 key | `ArgumentCaptor` on `PutObjectRequest` |
| 8 | No guard against re-adding `@Transactional` | Connection-pool regression | Reflection annotation test |
| 9 | `FactType.DbConverter` untested directly | DB string mismatch | Round-trip converter test |
| 10 | `Observation.create` null guards untested | Invalid in-memory objects | Parameterized null tests |
| 11 | Long prefix key bound untested | Key > 256 in real deployment | Long-prefix key test |
| 12 | Payload-not-logged constraint untested | Secret leak risk | Log appender assertion |

(End of test review.)
