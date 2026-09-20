<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T30 · Phase 7 — Self Review

Reviews the Phase 6 diff (pom.xml, `V6__cleanup_indexes.sql`, the two repository methods, the two
service methods, `AuthServiceApplication.java`, `application.properties`, and the new `cleanup`
package) against the frozen brief and `agents.md`, across correctness, boundary conditions,
null-safety, thread-safety, transaction boundaries, module boundaries, idempotency,
enumeration-safety, and readability.

---

## Finding 1 — `deleteStaleShedLockRows` converts the cutoff to `java.sql.Timestamp`, introducing an avoidable timezone dependency

**Severity:** Medium

**Evidence:** `CleanupJob.java:83-93` computes `Instant cutoff` and then binds it via
`Timestamp.from(cutoff)` in a raw `JdbcTemplate.update(...)` call. `java.sql.Timestamp` carries no
timezone of its own; when the PostgreSQL JDBC driver binds a plain `Timestamp` against a
`TIMESTAMPTZ` column via the legacy `setTimestamp` path (which is what a `Timestamp`-typed argument
triggers), the value is interpreted relative to the JVM's *default* timezone unless a `Calendar` is
explicitly supplied — and this codebase pins no JVM/container timezone anywhere (checked
`application.properties` and the Dockerfile; no `user.timezone`/`TZ` setting exists). Every other
timestamp comparison in this service goes through JPA/Hibernate, which manages this conversion
consistently regardless of JVM timezone — this is the first place in the codebase's production code
that binds a timestamp via raw `JdbcTemplate`, so there's no existing precedent this follows, and
it's the one path that doesn't get JPA's automatic safety net. The resolved driver
(`org.postgresql:postgresql:42.7.7`, confirmed in the local Maven cache) fully supports binding a
raw `java.time.Instant` directly via `setObject` (supported since pgjdbc 42.2), which sidesteps the
ambiguity entirely since `Instant` has no calendar/timezone interpretation question at all.

**Recommendation:** Bind `cutoff` (the `Instant`) directly —
`jdbcTemplate.update("DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()", cutoff)`
— instead of converting through `Timestamp.from(...)`. Removes both the conversion step and the
latent timezone dependency.

---

## Finding 2 — `CleanupProperties`'s Javadoc doesn't mention that `tokenRetentionDays` is reused for ShedLock row cleanup

**Severity:** Low

**Evidence:** `CleanupProperties.java`'s class Javadoc describes `tokenRetentionDays` only as
governing "expired verification tokens," but `CleanupJob.deleteStaleShedLockRows`
(`CleanupJob.java:85`) also reads `properties.tokenRetentionDays()` for the ShedLock staleness
cutoff — a deliberate, documented design decision (Phase 2's OQ2, confirmed at Phase 4) to avoid a
fourth config key, but the reuse isn't mentioned anywhere in `CleanupProperties` itself. A future
reader looking only at this record (not the design artifacts) would reasonably assume the property
governs verification-token cleanup exclusively.

**Recommendation:** Add a one-line Javadoc note on `tokenRetentionDays` (or the class-level doc)
stating it also governs ShedLock row retention, so the reuse is discoverable from the code alone.

---

## Non-Issues Confirmed

- **Cascade correctness (AC3):** `deleteRevokedBefore`'s bulk JPQL `DELETE` issues a real SQL
  `DELETE` statement regardless of Hibernate's persistence-context bookkeeping, so the database's
  own `ON DELETE CASCADE` (V2) fires exactly as it would for any other deletion path — no
  application-level cascade logic needed or present.
- **Self-referential ShedLock deletion risk:** verified `deleteStaleShedLockRows`'s own
  `lock_until < now()` guard correctly prevents the job's own currently-held lock row (whose
  `lock_until` is set into the future for the duration of `lockAtMostFor`) from ever matching its
  own cleanup predicate — the exact scenario the guard exists to prevent, confirmed safe by tracing
  through ShedLock's own lock-acquisition mechanics.
- **Transaction isolation (D2/AC7):** `run()` carries no `@Transactional`; each of the three steps'
  target method (`VerificationTokenService.deleteExpiredTokens`,
  `RefreshTokenTracker.deleteRevokedFamiliesOlderThan`) owns its own transaction via its own
  `@Transactional` annotation, and the raw `JdbcTemplate.update` call is inherently a single
  auto-committing statement outside any Spring-managed transaction — none of the three steps can
  roll back another's already-committed work.
- **Module boundaries (D1/AC8/L12):** `CleanupJob` imports only `VerificationTokenService`
  (`account`) and `RefreshTokenTracker` (`token`), both public; neither repository is imported
  directly.
- **`@EnableSchedulerLock`'s required attribute:** `defaultLockAtMostFor` has no default value in
  ShedLock 7.7.0's own annotation definition (verified against its source) — correctly supplied.
- **L1 compliance:** V1-V5 untouched; V6 is additive-only (two `CREATE INDEX IF NOT EXISTS`
  statements, no table/column changes).

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
