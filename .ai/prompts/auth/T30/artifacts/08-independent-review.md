<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T30 · Phase 8 — Independent Code Review

Consumes the Phase 6 implementation and Phase 7 self-review. Reviewed with fresh, adversarial eyes; findings only.

---

## Finding 1 — `deleteStaleShedLockRows` binds the cutoff through `java.sql.Timestamp`, creating a timezone/precision dependency

**Issue:** Latent timezone bug and unnecessary precision loss in the ShedLock cleanup step.

**Evidence:** `CleanupJob.java:86-88` computes an `Instant cutoff` and then binds it via `Timestamp.from(cutoff)`. `java.sql.Timestamp` has millisecond precision and, when bound to a PostgreSQL `TIMESTAMPTZ` column through the legacy `setTimestamp` path, is interpreted relative to the JVM default timezone unless a `Calendar` is supplied. This service pins no container/JVM timezone (`application.properties` and the Dockerfile contain no `user.timezone`/`TZ` setting). Every other timestamp comparison in the service goes through JPA/Hibernate, which handles this conversion consistently. The PostgreSQL JDBC driver (42.7.7) supports binding `java.time.Instant` directly via `setObject`, sidestepping both issues.

**Recommendation:** Replace `Timestamp.from(cutoff)` with the `Instant` directly:

```java
jdbcTemplate.update(
    "DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()",
    cutoff);
```

This matches the JPA repository methods that already bind `Instant` parameters.

**Confidence:** High.

---

## Finding 2 — `CleanupProperties` Javadoc hides the ShedLock retention reuse

**Issue:** Maintainability / discoverability gap.

**Evidence:** `CleanupProperties.java:8-12` describes `tokenRetentionDays` as governing "expired verification tokens," but `CleanupJob.deleteStaleShedLockRows` (`CleanupJob.java:85`) also uses `tokenRetentionDays()` for ShedLock row staleness. This reuse was a deliberate Phase 2/4 decision (OQ2) to avoid a fourth config key, but it is not documented on the property itself. A future reader looking only at the config record would reasonably assume the property is token-specific.

**Recommendation:** Update the class-level Javadoc (or add a Javadoc comment on `tokenRetentionDays`) to state that the value also governs stale ShedLock row retention.

**Confidence:** High.

---

## Finding 3 — `lockAtMostFor = "PT10M"` may allow concurrent execution if a cleanup run exceeds 10 minutes

**Issue:** Race condition under unexpectedly long cleanup runs.

**Evidence:** `CleanupJob.java:57` sets `lockAtMostFor = "PT10M"`. ShedLock releases the lock after this duration even if the annotated method is still running. The three cleanup steps are plain `DELETE ... WHERE` statements, which are normally fast, but a first run against years of unpruned data could exceed 10 minutes. If the lock expires while the first replica is still deleting, a second replica could acquire the lock and start the same cleanup, producing concurrent long-running deletes on the same tables.

**Recommendation:** Either extend `lockAtMostFor` to a more conservative duration (e.g., `PT1H` for a nightly job) or document that the value assumes normal nightly volumes and that operators should monitor cleanup duration. Adding a Micrometer timer around each step would also surface when the bound is at risk.

**Confidence:** Medium (the scenario is real but depends on backlog size).

---

## Finding 4 — No production-ready observability beyond log lines

**Issue:** Operational visibility gap.

**Evidence:** `CleanupJob` logs the number of rows deleted per step (`CleanupJob.java:67, 77, 89`) and logs failures, but it emits no metrics. A failed nightly cleanup (e.g., DB transiently unavailable for one step) would appear only in logs; there is no counter or gauge for pages/alerts. This is not a logic bug, but for a production scheduled job that prevents unbounded table growth, silent partial failures are operationally risky.

**Recommendation:** Add Micrometer counters for rows deleted per step and a counter for step failures, or at minimum document the log-based alerting pattern expected for this job. If out of scope for T30, note it explicitly as deferred work.

**Confidence:** Low (operational enhancement, not a correctness defect).

---

## Finding 5 — No cleanup-specific tests exist yet

**Issue:** Coverage gap / Phase 10 dependency.

**Evidence:** There are no test files under `services/auth/src/test/java/com/themistra/auth/cleanup/`. The implementation compiles and the existing test suite still passes, but the T30 acceptance criteria (AC1-AC7) are not yet verified by any automated test. The Phase 10 manifest will need to cover: correct cutoff calculation, step isolation (one failure doesn't stop others), ShedLock guard behavior, and the integration test `shouldCleanupExpiredTokensAndFamilies` with Awaitility.

**Recommendation:** Track as a known pending item for Phase 10; ensure the test plan includes a direct test of the `Timestamp` binding change if Finding 1 is accepted.

**Confidence:** High (that tests are missing) / N/A (that this is expected at this phase).

---

## Non-Issues Confirmed

- **Module boundaries (L12):** `CleanupJob` imports only public service/tracker types (`VerificationTokenService`, `RefreshTokenTracker`); no repository is imported directly.
- **Transaction isolation (AC7):** `run()` is not `@Transactional`; each step owns its own transaction (two via `@Transactional` service methods, one via auto-committing `JdbcTemplate.update`), so a failure in one step cannot roll back another.
- **Cascade correctness (AC3):** `RefreshTokenFamilyRepository.deleteRevokedBefore` is a bulk JPQL `DELETE`; the database's own `ON DELETE CASCADE` on `refresh_token_archive.family_id` (V2) removes archive rows without application code.
- **ShedLock self-deletion safety:** the `lock_until < now()` guard in `deleteStaleShedLockRows` prevents deletion of the job's own currently held lock row.
- **L1 compliance:** V1-V5 untouched; V6 is additive-only (two `CREATE INDEX IF NOT EXISTS` statements).
- **Index coverage:** V6 adds indexes on `verification_tokens.expires_at` and `refresh_token_family.revoked_at`, addressing the full-scan risk.
- **`@EnableSchedulerLock` wiring:** present on `AuthServiceApplication` with `defaultLockAtMostFor` supplied, and `CleanupConfig` provides the required `LockProvider` bean.
- **Config validation:** `CleanupProperties` uses `@NotBlank` for cron and `@Min(1)` for retention days, preventing invalid startup states.
- **Clock source:** `Clock.systemUTC()` bean ensures all cutoff calculations are timezone-safe except for the raw `JdbcTemplate` path noted in Finding 1.

---

## Open Questions

None that block the Phase 8 review. Findings 1 and 2 should be dispositioned at the Phase 9 human gate; Finding 1 in particular is a genuine portability defect that should be fixed before T30 is considered complete.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (Human Approval / Review Resolution) on approval.
