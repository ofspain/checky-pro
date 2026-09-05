<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T30 · Phase 5 — Implementation Plan

Consumes the frozen brief (`artifacts/04-frozen-task-brief.md`). Every file below traces to the
brief's own Files sections.

## Files to create

- `services/auth/src/main/java/com/themistra/auth/cleanup/CleanupJob.java`
- `services/auth/src/main/java/com/themistra/auth/cleanup/CleanupProperties.java`
- `services/auth/src/main/java/com/themistra/auth/cleanup/CleanupConfig.java` (the `LockProvider`
  bean, Finding 6)
- `services/auth/src/main/resources/db/migration/V6__cleanup_indexes.sql`

## Files to modify

- `services/auth/pom.xml` — add `shedlock-spring` and `shedlock-provider-jdbc-template` (both
  `7.7.0`).
- `AuthServiceApplication.java` — add `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`.
- `account/VerificationTokenService.java` — one new public method.
- `account/VerificationTokenRepository.java` — one new package-private method.
- `token/RefreshTokenTracker.java` — one new public method.
- `token/RefreshTokenFamilyRepository.java` — one new package-private method.
- `application.properties` — three `themistra.auth.cleanup.*` keys.

## Public methods (signatures)

**`VerificationTokenService`** (existing class, one new method, matching its own established
per-method `@Transactional` style):
```java
/** Cleanup job (T30, R40) - hard-deletes tokens whose expiry has passed. Returns the row count
 * deleted, for job-run logging. */
@Transactional
public int deleteExpiredTokens(Instant cutoff)
```

**`RefreshTokenTracker`** (existing class, one new method, matching its own established
per-method `@Transactional` style):
```java
/** Cleanup job (T30, R40) - hard-deletes families revoked before the retention cutoff. The
 * database's own ON DELETE CASCADE on refresh_token_archive.family_id (V2) removes each deleted
 * family's archive rows automatically; active (non-revoked) families and their archives are never
 * touched regardless of age (Phase 2 OQ1). Returns the row count deleted. */
@Transactional
public int deleteRevokedFamiliesOlderThan(Instant cutoff)
```

**`CleanupJob`** (new, `cleanup` package) — no public methods besides the scheduled entry point
itself, which Spring invokes rather than application code calling it directly:
```java
@Scheduled(cron = "${themistra.auth.cleanup.cron}")
@SchedulerLock(name = "auth-cleanup-job", lockAtLeastFor = "PT1M", lockAtMostFor = "PT10M")
public void run()
```

**`CleanupProperties`** (new, `cleanup` package) — a validated record, matching `ApiKeyProperties`'s
exact style:
```java
@ConfigurationProperties(prefix = "themistra.auth.cleanup")
@Validated
public record CleanupProperties(
        @NotBlank String cron,
        @Min(1) int tokenRetentionDays,
        @Min(1) int familyRetentionDays
)
```

**`CleanupConfig`** (new, `cleanup` package):
```java
@Configuration
public class CleanupConfig {
    @Bean
    public LockProvider lockProvider(DataSource dataSource)
}
```

## Private methods

**`CleanupJob`** — three private step methods, each independently try/catch-guarded (D2/AC7), each
delegating to a target method that owns its own transaction:
```java
private void deleteExpiredTokens()       // calls verificationTokenService.deleteExpiredTokens(clock.instant())
private void deleteOldRevokedFamilies()  // calls refreshTokenTracker.deleteRevokedFamiliesOlderThan(cutoff)
private void deleteStaleShedLockRows()   // raw jdbcTemplate.update(...) per D4's predicate
```
`run()`'s body is exactly three calls, one to each, each wrapped in its own try/catch inside the
private method itself (not in `run()`) so `run()` stays a simple, readable sequence and each step's
failure-handling lives next to the step it guards.

`deleteStaleShedLockRows`'s exact statement (D4):
```java
jdbcTemplate.update(
        "DELETE FROM shedlock WHERE lock_until < ? AND lock_until < now()",
        Timestamp.from(clock.instant().minus(properties.tokenRetentionDays(), ChronoUnit.DAYS)));
```

**`VerificationTokenRepository`** — no private methods; the new method is itself the interface
declaration:
```java
@Modifying
@Query("DELETE FROM VerificationToken t WHERE t.expiresAt < :cutoff")
int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
```

**`RefreshTokenFamilyRepository`** — likewise:
```java
@Modifying
@Query("DELETE FROM RefreshTokenFamily f WHERE f.revokedAt IS NOT NULL AND f.revokedAt < :cutoff")
int deleteRevokedBefore(@Param("cutoff") Instant cutoff);
```

Both new repository methods are `@Modifying` bulk JPQL deletes (one SQL statement each), not
entity-by-entity deletion — consistent with this codebase's existing precedent for bulk updates
(`VerificationTokenRepository.markConsumed`/`invalidateActive` already use the same
`@Modifying @Query` shape).

## Entities used

`VerificationToken`, `RefreshTokenFamily` (existing, unmodified — only new delete queries added, no
entity/column changes). `refresh_token_archive` has no JPA entity relationship to
`RefreshTokenFamily`; its cleanup is entirely the database's own `ON DELETE CASCADE` (V2),
triggered automatically by the bulk JPQL delete's underlying SQL, not by any JPA cascade
annotation.

## Repositories used

`VerificationTokenRepository` (new method only, `account` — package-private, only
`VerificationTokenService` calls it), `RefreshTokenFamilyRepository` (new method only, `token` —
package-private, only `RefreshTokenTracker` calls it).

## Services used

`VerificationTokenService`, `RefreshTokenTracker` (both existing, each gaining one new public
method per D1). `JdbcTemplate` (autowired directly into `CleanupJob` for the raw `shedlock` delete
— no entity/repository exists for that table). `Clock` (existing, autowired into `CleanupJob`).

## Unit / integration tests required

**`VerificationTokenServiceTest`** (existing, extend): `deleteExpiredTokens` delegates to the
repository with the given cutoff and returns its count.

**`RefreshTokenTrackerTest`** (existing, extend): `deleteRevokedFamiliesOlderThan` delegates to the
repository with the given cutoff and returns its count.

**`CleanupJobTest`** (new, unit, mocked `VerificationTokenService`/`RefreshTokenTracker`/
`JdbcTemplate`/fixed `Clock`):
1. `run()` calls all three steps with cutoffs correctly derived from `CleanupProperties` and the
   fixed `Clock`.
2. One step's mocked collaborator throws → the other two steps still execute (AC7/D2).
3. The ShedLock JDBC call uses exactly D4's two-clause predicate (verified via the SQL string
   argument captured, or by asserting the bound parameter value if the string itself isn't
   practical to assert exactly).

**`CleanupIntegrationTest`** (new, `@SpringBootTest` + Testcontainers + Awaitility, per the task
statement — likely placed in the new `cleanup` package):
4. Seed one expired verification token, one non-expired one, one old-revoked family (with an
   archive row), one recently-revoked family, one never-revoked family, one stale `shedlock` row,
   one fresh `shedlock` row.
5. Invoke `cleanupJob.run()` directly (a direct method call, not waiting for the real cron — a
   `@SpringBootTest`-scoped bean is trivially reachable via `@Autowired`, and running a job on its
   real 2am-daily schedule inside a test would be impractical) — Awaitility here polls for the
   *effects* of the run (row absence/presence) rather than the scheduling mechanism itself, since
   ShedLock's own concurrent-execution guarantee isn't meaningfully testable within one JVM/test
   process.
6. Assert the expired token, old-revoked family, its archive row, and the stale ShedLock row are
   all gone; assert the non-expired token, recently-revoked family, never-revoked family, and fresh
   ShedLock row all survive.

`shouldCleanupExpiredTokensAndFamilies` (named, `package.md` §8) is satisfied by test 6 above,
covering both AC1 and AC2 in one test per its own name.

## Execution order

1. `V6__cleanup_indexes.sql` (D3) — schema first, per this phase's own front-load convention; no
   code depends on it functionally, but it must exist before the job it supports is written.
2. `pom.xml` (ShedLock dependencies) — needed before any ShedLock-annotated code compiles.
3. `VerificationTokenRepository`/`RefreshTokenFamilyRepository` new methods (dao layer).
4. `VerificationTokenService`/`RefreshTokenTracker` new methods (service layer, depends on step 3).
5. `CleanupProperties`, `CleanupConfig` (config/support classes, independent of steps 3-4).
6. `AuthServiceApplication.java` (`@EnableSchedulerLock`) — needed before `CleanupJob` can be
   meaningfully exercised in a real Spring context.
7. `CleanupJob` (depends on steps 4-6).
8. `application.properties` (the three keys) — needed for `CleanupProperties`/`@Scheduled`'s cron
   placeholder to resolve; could equally be done alongside step 5, order between them is not load-
   bearing.
9. Tests: `VerificationTokenServiceTest`/`RefreshTokenTrackerTest` extensions, then `CleanupJobTest`,
   then `CleanupIntegrationTest`.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
