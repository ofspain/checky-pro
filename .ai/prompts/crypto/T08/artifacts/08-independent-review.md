<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T08. -->

# crypto · T08 · Phase 8 — Independent Review Findings

**Scope:** Independent adversarial review of the implemented observation-log code (`FactType`, `Observation`, `ObservationRepository`, `ObservationSnapshotStore`, `ObservationLog`, `ObservationSnapshotStoreConfig`, `pom.xml`) and the Phase 7 self-review.

**Directive:** Do not rewrite code. Return findings as **Issue · Evidence · Recommendation · Confidence.**

---

## Issue 1 — The computed S3 key can exceed `observations.s3_snapshot_key VARCHAR(256)` and fail the Postgres insert

**Evidence:**
- `ObservationSnapshotStore.buildKey` (`:49-53`) constructs
  `prefix + chain + "/" + txHash + "/" + factType + "/" + provider + "-" + observedAt + "-" + UUID + ".json"`.
- `V1__chain_baseline.sql` caps `chain` at 32, `tx_hash` at 128, `provider` at 64, and `s3_snapshot_key` at 256.
- Worst-case realistic lengths: prefix ~20 (`chain-observations/`), chain 32, txHash 128, factType 11 (`confirmations`), provider 64, observedAt ~30 (`2026-09-03T18:49:04.123456Z`), UUID 36, `.json` 4 — total ~355 characters, well over 256.
- Postgres raises a hard error on `INSERT` for an over-length `VARCHAR`, so a successful S3 write would be followed by a failed DB write — the opposite of the brief's intent.

**Recommendation:**
Replace the variable-length key with a scheme bounded independently of input lengths. For example: `prefix + chain + "/" + txHash + "/" + UUID + ".json"` (UUID alone guarantees uniqueness). Move the human-readable provider/factType/timestamp components into S3 object metadata (already present) instead of the key itself.

**Confidence:** High

---

## Issue 2 — `@Transactional` on `ObservationLog.record` holds a database connection/transaction open during the S3 network call

**Evidence:**
- `ObservationLog.record` (`:41-53`) is annotated `@Transactional` at the method level.
- The method body calls `snapshotStore.store(...)` (a network call with a 5-second timeout) before `repository.save(...)`.
- Spring's transaction proxy opens the transactional resource (and acquires a pooled DB connection) for the entire method duration. A slow or timed-out S3 call therefore holds that connection for up to 5 seconds while doing no transactional work.
- The frozen brief explicitly states "the S3 write is not, and cannot be, part of that transaction" — but the current code couples S3 latency to a held transaction/connection.

**Recommendation:**
Narrow the `@Transactional` boundary to cover only `repository.save(...)`. Move the insert into a private `@Transactional` helper method or use `TransactionTemplate`, ensuring `snapshotStore.store(...)` executes outside any open transaction/connection. Document that the S3 call is intentionally non-transactional.

**Confidence:** High

---

## Issue 3 — `S3Client` bean is never closed, risking resource leakage on context shutdown

**Evidence:**
- `ObservationSnapshotStoreConfig.s3Client` (`:25-33`) creates a real `S3Client` bean.
- AWS SDK v2 clients are thread-safe and reusable, but they hold connections and threads that should be released by calling `close()` when no longer needed.
- There is no `@PreDestroy` method, no `AutoCloseable` registration, and no `DisposableBean` implementation that calls `s3Client.close()` on Spring context shutdown.
- `ObservationLog.close()` does not exist; only `TronAdapter`/`EthereumAdapter` have close paths in this service so far.

**Recommendation:**
Make the `S3Client` bean implement cleanup on context close — either by returning a `DisposableBean` lambda, annotating a shutdown method with `@PreDestroy`, or wrapping the client in a small component that closes it. Ensure this also closes any test override beans cleanly.

**Confidence:** High

---

## Issue 4 — Non-`SdkException` runtime exceptions from S3 propagate unchecked, breaking the non-blocking failure contract

**Evidence:**
- `ObservationSnapshotStore.store` (`:39-46`) catches `SdkException` and returns `Optional.empty()`.
- It does not catch broader `RuntimeException` (e.g., `NullPointerException`, unexpected AWS client bugs, or custom runtime wrappers).
- If such an exception is thrown, it propagates out of `ObservationLog.record` before the Postgres insert, so the observation is not persisted at all — contradicting the brief's requirement that an S3 failure must not block the DB write.

**Recommendation:**
Catch `RuntimeException` in `store(...)` (or at least `Exception`) and treat it the same way as `SdkException`: log at error level and return `Optional.empty()`. The Postgres insert should then proceed normally.

**Confidence:** Medium

---

## Issue 5 — `ObservationSnapshotStore.buildKey` uses locale-dependent `toLowerCase()` inconsistent with `FactType.DbConverter`

**Evidence:**
- `ObservationSnapshotStore.java:51`: `factType.name().toLowerCase()` with no `Locale` argument.
- `FactType.DbConverter.convertToDatabaseColumn` (`:29`) correctly uses `toLowerCase(java.util.Locale.ROOT)`.
- Under a Turkish (`tr`/`tr-TR`) default locale, `"FINALITY".toLowerCase()` produces `"fınalıty"` (dotless 'ı'), not `"finality"`. The two call sites would produce different strings for the same enum value.

**Recommendation:**
Change `buildKey` to use `factType.name().toLowerCase(java.util.Locale.ROOT)`, matching `FactType.DbConverter`.

**Confidence:** High

---

## Issue 6 — No automated enforcement that `Observation` has no UPDATE/DELETE code path

**Evidence:**
- AC3 requires "No code path in `Observation`/`ObservationRepository` produces an `UPDATE` or `DELETE` against `chain.observations`."
- The implementation relies on immutability by convention: no setters, `s3SnapshotKey` supplied at construction, and `updatable = false` on `observedAt`.
- There is no ArchUnit rule, custom Hibernate interceptor check, or SQL-grant-based test that would fail if a future change accidentally introduced a mutable field or a `delete*` call.

**Recommendation:**
Add an ArchUnit test (or a repository-level test) asserting that no method in `com.themistra.crypto.observation` invokes JPA `save` on an existing entity in a mutating way, calls `delete*` on `ObservationRepository`, or generates an `UPDATE`/`DELETE` SQL statement. The existing `crypto_app` grant already enforces this at the DB layer, but a fast unit-level guard would catch mistakes before integration tests run.

**Confidence:** Medium

---

## Issue 7 — `Instant.toString()` embeds colons in the S3 key

**Evidence:**
- `ObservationSnapshotStore.java:51-52` interpolates `observedAt` directly into the key; `Instant.toString()` produces ISO-8601 strings containing `:` (e.g., `2026-09-03T18:49:04.123456Z`).
- S3 object keys can contain colons, but AWS documentation lists them as characters that "might require special handling" in URLs and presigned links.
- This is a low-severity issue because the key is used via SDK calls, not directly in URLs, but it introduces friction for console navigation and presigned-link generation.

**Recommendation:**
Use a colon-free timestamp representation in the key, such as `DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(UTC)` or epoch millis. Keep the full ISO-8601 timestamp in S3 metadata if human readability is needed.

**Confidence:** Low

---

## Issue 8 — `ObservationSnapshotStore.store` has no documented non-null contract and relies on `Map.of` to throw

**Evidence:**
- `ObservationSnapshotStore.store` is a public method with no Javadoc `@param` non-null contract.
- `buildRequest` (`:55-67`) uses `Map.of(...)` for metadata, which throws `NullPointerException` if any value is null.
- The only current caller (`ObservationLog.record`) never passes nulls, but a future caller could receive an unhelpful `NullPointerException` from deep inside the metadata map construction.

**Recommendation:**
Add explicit `Objects.requireNonNull` guards at the top of `store(...)` for all parameters, with clear messages naming the offending argument. This documents the contract and fails fast with a meaningful exception.

**Confidence:** Low

---

## Summary table

| # | Issue | Severity | Confidence |
|---|-------|----------|------------|
| 1 | S3 key can exceed `VARCHAR(256)` | High | High |
| 2 | `@Transactional` holds DB connection during S3 call | High | High |
| 3 | `S3Client` never closed on shutdown | Medium-High | High |
| 4 | Non-`SdkException` S3 failures propagate unchecked | Medium | Medium |
| 5 | Locale-dependent `toLowerCase()` in S3 key | Medium | High |
| 6 | No automated enforcement of no UPDATE/DELETE | Low-Medium | Medium |
| 7 | Colons in S3 key from `Instant.toString()` | Low | Low |
| 8 | No documented null contract in `store(...)` | Low | Low |

(End of independent review.)
