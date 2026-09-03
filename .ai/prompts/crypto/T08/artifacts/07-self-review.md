# crypto · T08 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`FactType.java`, `Observation.java`, `ObservationRepository.java`,
`ObservationSnapshotStore.java`, `ObservationLog.java`, `ObservationSnapshotStoreConfig.java`,
`pom.xml`) against the frozen brief and `agents.md`. No code changed in this phase — findings only,
per the phase directive.

---

## Finding 1 — The computed S3 key can exceed `s3_snapshot_key VARCHAR(256)`, which would fail the Postgres insert it's meant to accompany

**Severity:** High

**Evidence:** `ObservationSnapshotStore.java:49-53` (`buildKey`) concatenates
`prefix + chain + "/" + txHash + "/" + factType + "/" + provider + "-" + observedAt + "-" + UUID +
".json"`. Against `chain.observations`' own declared column widths (`V1__chain_baseline.sql`:
`chain VARCHAR(32)`, `tx_hash VARCHAR(128)`, `provider VARCHAR(64)`), a maximally-sized combination —
plus a realistic prefix (~20 chars), the longest `FactType` name, an ISO-8601 `Instant` (~30 chars),
and a 36-character UUID — comfortably exceeds 256 characters, the width of
`observations.s3_snapshot_key` itself. Postgres does not silently truncate an over-length `VARCHAR`
value; it raises a hard error. A successful S3 write followed by a key too long for its own destination
column would fail the very Postgres insert this task exists to make reliable — the opposite of what
amendment #2/#4 intended (S3 as a non-blocking supplement to a load-bearing DB write).

**Recommendation:** Shorten the key scheme so it can never exceed 256 characters regardless of input
lengths — e.g. hash `(chain, txHash, provider, factType, observedAt)` into a fixed-length component
(a UUID alone already guarantees uniqueness; the human-readable prefix segments could be truncated or
dropped from the key itself and carried only in the object's metadata tags, which have no such length
coupling to this column).

---

## Finding 2 — `@Transactional` scopes the S3 network call, not just the Postgres write

**Severity:** High

**Evidence:** `ObservationLog.java:41-53` (`record`) is annotated `@Transactional` at the method level,
but the method's body calls `snapshotStore.store(...)` (a network call to S3, with up to a 5-second
timeout per `ObservationSnapshotStoreConfig`) *before* `repository.save(...)`. Spring opens the
transactional resource (and, in the common case, acquires a pooled DB connection) for the full method
duration, meaning a slow or timed-out S3 call holds that connection for up to 5 seconds doing nothing
transactional at all. The frozen brief's own Constraints section states "the S3 write is not, and
cannot be, part of that transaction" — true in the two-phase-commit sense (S3 has no such
participation), but the current code still couples the S3 call's latency to a held database
transaction/connection, risking connection-pool exhaustion under load precisely when S3 is slow (the
worst possible time for this coupling to bite).

**Recommendation:** Narrow the `@Transactional` boundary to cover only the Postgres write — e.g. by
moving `repository.save(...)` into its own `@Transactional`-annotated method (self-invocation caveats
aside, doable via a small internal collaborator or `TransactionTemplate`) so the S3 call in `record`
happens entirely outside any open transaction/connection.

---

## Finding 3 — Locale-dependent `.toLowerCase()` in the S3 key, inconsistent with `FactType`'s own converter

**Severity:** Medium

**Evidence:** `ObservationSnapshotStore.java:51`: `factType.name().toLowerCase()` — no `Locale`
argument. `FactType.DbConverter` (`FactType.java:29`) correctly uses
`toLowerCase(java.util.Locale.ROOT)` for the identical kind of conversion. Under a JVM running with a
Turkish (`tr`/`tr-TR`) default locale, `"FINALITY".toLowerCase()` produces `"fınalıty"` (a dotless
'ı', not 'i') rather than `"finality"` — a real, deterministic, well-known Java gotcha, not a
theoretical one. The two call sites should behave identically and currently don't.

**Recommendation:** Add `Locale.ROOT` to `buildKey`'s `.toLowerCase()` call, matching
`FactType.DbConverter`'s own already-correct usage.

---

## Finding 4 — `Instant.toString()` embeds colons in the S3 key

**Severity:** Low

**Evidence:** `ObservationSnapshotStore.java:52` interpolates `observedAt` (an `Instant`) directly into
the key string; `Instant.toString()`'s ISO-8601 format contains `:` characters (e.g.
`2026-09-03T18:49:04.123456Z`). S3 object keys can technically contain colons, but AWS's own guidance
lists them among characters that "might require special handling" in URLs (presigned links, console
navigation) without encoding.

**Recommendation:** Low priority; consider a colon-free timestamp representation (e.g.
`DateTimeFormatter.ISO_INSTANT` with colons stripped, or epoch millis) if this key is ever expected to
appear directly in a URL rather than only as an SDK-level key string.

---

## Finding 5 — No defensive null-checks on `ObservationSnapshotStore`'s own public method

**Severity:** Low

**Evidence:** `ObservationSnapshotStore.buildRequest` (`:61-65`) calls `Map.of("chain", chain, ...)`,
which throws `NullPointerException` on any null value — with no upstream null-check in `store` itself.
`ObservationLog` never passes nulls today, but `ObservationSnapshotStore` is a standalone, public class
with no documented non-null contract of its own.

**Recommendation:** Low priority given the only current caller is trusted; worth a `Objects.
requireNonNull` guard or Javadoc contract if this class is ever called from elsewhere.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | Computed S3 key can exceed `s3_snapshot_key VARCHAR(256)` | High |
| 2 | `@Transactional` scopes the S3 network call, not just the DB write | High |
| 3 | Locale-dependent `.toLowerCase()` inconsistent with `FactType`'s own converter | Medium |
| 4 | Colons in the S3 key from `Instant.toString()` | Low |
| 5 | No defensive null-checks in `ObservationSnapshotStore` | Low |

(End of self-review.)
