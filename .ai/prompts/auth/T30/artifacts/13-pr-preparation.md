<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T30 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: add ShedLock-guarded scheduled cleanup job (T30)
```

## Commit message

```
auth: add ShedLock-guarded scheduled cleanup job (T30)

Adds a nightly job that hard-deletes expired verification tokens, refresh-
token families revoked past the configured retention (their archive rows go
with them via the existing ON DELETE CASCADE), and stale ShedLock rows. The
job needs repositories from both the account and token modules; rather than
crossing either module's package-private boundary, it calls one new public
method each on VerificationTokenService and RefreshTokenTracker, added
specifically for this task.

Adds shedlock-spring/shedlock-provider-jdbc-template as new dependencies,
@EnableSchedulerLock on the application class, and a LockProvider bean wired
to the shedlock table that has existed in schema since T01's V5 migration.
V6__cleanup_indexes.sql adds the two indexes the job's own queries need, since
neither existed before.

Independent review caught a real timezone-dependent bug (binding the ShedLock
cleanup cutoff via java.sql.Timestamp instead of Instant) before merge, and a
lock-duration risk that could let two replicas run a cleanup pass concurrently
against a large first-run backlog. Both are fixed. Test review added a new
ArchUnit rule making the account/token module-boundary decision permanent
rather than only true by convention.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production code**
- `services/auth/pom.xml` (modified — 2 new dependencies)
- `services/auth/src/main/resources/db/migration/V6__cleanup_indexes.sql` (new)
- `services/auth/src/main/java/com/themistra/auth/cleanup/CleanupJob.java` (new)
- `services/auth/src/main/java/com/themistra/auth/cleanup/CleanupProperties.java` (new)
- `services/auth/src/main/java/com/themistra/auth/cleanup/CleanupConfig.java` (new)
- `services/auth/src/main/java/com/themistra/auth/AuthServiceApplication.java` (modified —
  `@EnableSchedulerLock`)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java` (modified
  — 1 new method)
- `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenRepository.java`
  (modified — 1 new method)
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java` (modified — 1
  new method)
- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenFamilyRepository.java`
  (modified — 1 new method)
- `services/auth/src/main/resources/application.properties` (modified — 3 new keys)

**Tests**
- `services/auth/src/test/java/com/themistra/auth/cleanup/CleanupJobTest.java` (new, 7 tests)
- `services/auth/src/test/java/com/themistra/auth/cleanup/CleanupPropertiesTest.java` (new, 5
  tests)
- `services/auth/src/test/java/com/themistra/auth/cleanup/CleanupConfigTest.java` (new, 1 test)
- `services/auth/src/test/java/com/themistra/auth/cleanup/CleanupIntegrationTest.java` (new, 1
  test — Docker-blocked, unexecuted)
- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java`
  (modified — 1 new test)
- `services/auth/src/test/java/com/themistra/auth/token/RefreshTokenTrackerTest.java` (modified —
  1 new test)
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` (modified — 1 new rule)

## Summary

Implements R40 (scheduled cleanup) under L1 (a new, narrow `V6` migration for two supporting
indexes — read as the established "numbered follow-up migration" pattern L1 itself sets, not a
one-time exception). The job hard-deletes expired verification tokens, old revoked refresh-token
families (archive rows cascade automatically), and stale ShedLock rows, guarded by
`@SchedulerLock` so ≥2 EKS replicas never run it concurrently. The module-boundary tension between
`account`'s and `token`'s package-private repositories was resolved by adding one new public method
to each module's existing service class rather than by exposing either repository — now permanently
enforced by a new ArchUnit rule. Two real defects were caught and fixed pre-merge: a
timezone-dependent timestamp-binding bug (independently found by both self-review and Kimi) and a
ShedLock lock-duration bound too tight for a worst-case first run against unpruned backlog.

## Testing performed

- `mvn -pl services/auth -am clean compile test-compile` — clean, no errors.
- Unit tests executed and green: `CleanupJobTest` 7/7, `CleanupPropertiesTest` 5/5,
  `CleanupConfigTest` 1/1, `VerificationTokenServiceTest` 23/23, `RefreshTokenTrackerTest` 18/18
  (54/54 total across the T30-relevant classes).
- `CleanupIntegrationTest` (1 test, the named `shouldCleanupExpiredTokensAndFamilies`) compiles
  cleanly but has **not executed** — Docker/Testcontainers has been unavailable this entire
  session, now spanning six consecutive tasks (T25–T30). This is also the only test in this task
  that would catch a real Spring context startup failure (ShedLock auto-wiring, the new V6
  migration applying) — a class of failure no unit test can detect. Whoever has Docker available
  should run the full accumulated integration suite in dependency order (T25's
  `SasLoginIntegrationTest`/`ApiKeyExchangeIntegrationTest` first) before this branch merges to
  `main`.
- `ArchitectureTest` was extended with 1 new rule but reports "0 tests run" in this sandbox — a
  known pre-existing environmental quirk (needs Docker to report non-zero counts), not something
  this change introduced.

## Specification references

- **Task:** T30 — Scheduled cleanup job (`spec/auth-service/tasks.md`, task 30)
- **Requirements:** R40
- **LOCKED decisions:** L1
- **Named tests (`package.md` §8):** `shouldCleanupExpiredTokensAndFamilies` (written, Docker-blocked)

---

**Phase 13 complete — PR preparation written. T30 is ready for merge pending an integration-test
run once Docker is available.**
