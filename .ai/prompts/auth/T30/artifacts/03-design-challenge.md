<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T30 · Phase 3 — Design Challenge

Consumes `artifacts/02-task-implementation-brief.md`. Adversarial review of the T30 scheduled cleanup job brief before the Phase 4 freeze. Findings only — no redesign, no implementation.

---

## Finding 1 — Package placement / module-boundary access is unresolved

**Severity:** Medium

**Evidence:** The brief's Constraints section notes that the new job class needs both `VerificationTokenRepository` (`account` package, package-private interface) and `RefreshTokenFamilyRepository` (`token` package, package-private interface). It states "Phase 5 must resolve this placement concretely" but offers no proposed resolution. Leaving this to Phase 5 without a Phase 4 decision risks an implementation that violates `agents.md` L12 (module boundaries) or invents a workaround (e.g., making repositories public) outside the frozen scope.

**Recommended brief amendment:** Add a Phase 4 decision (e.g., D1) choosing one of: (a) place the job in `token` and add a public service method in `account` that the job calls to delete expired tokens; (b) place it in `account` and add a public service method in `token` for old-family deletion; (c) create a new `cleanup` package with two small public facade methods on each module. Document which repositories stay package-private and which module boundary is intentionally crossed.

---

## Finding 2 — Transaction isolation of the three cleanup steps is ambiguous

**Severity:** Medium

**Evidence:** The brief says "The three cleanup actions ... run as three independent, individually try/catch-guarded steps" and "each of the three delete operations should be its own transactional unit (or the whole method `@Transactional` per step)." If the scheduled job method itself is annotated `@Transactional`, Spring wraps the entire method in one transaction; an exception thrown inside a `try/catch` block still marks the transaction for rollback-only, and any flush/commit will fail, undoing the earlier steps despite the catch. The phrase "or the whole method @Transactional per step" is internally contradictory.

**Recommended brief amendment:** Explicitly forbid an outer `@Transactional` on the job method. State that each step must run in its own transaction, achieved by either (a) no annotation (Spring Data's own `@Transactional` on repository delete methods handles each call), or (b) explicit `TransactionTemplate`/`@Transactional(REQUIRES_NEW)` helper methods. Clarify that the try/catch must be around a separate transactional boundary, not inside a shared one.

---

## Finding 3 — No indexes proposed for the cleanup target columns

**Severity:** Medium

**Evidence:** The cleanup job will execute `DELETE FROM verification_tokens WHERE expires_at < ?` and `DELETE FROM refresh_token_family WHERE revoked_at IS NOT NULL AND revoked_at < ?` nightly. Neither `verification_tokens.expires_at` nor `refresh_token_family.revoked_at` has an index (verified in `V1__auth_baseline_schema.sql` and `V2__refresh_token_family_tracking.sql`). As these tables grow, the DELETEs will degrade into full table scans and long-lived locks. V5 adds an index on `lockout_state.locked_until` for a similar scan pattern, so the precedent for indexing cleanup columns already exists.

**Recommended brief amendment:** Add two partial/composite indexes to the Files to Modify list (likely a new migration, but L1 forbids new migrations — so this may need an L1 exception or an amendment noting that indexes must go in a future migration). At minimum, add the index definitions as required follow-up work and note that without them the job is not production-ready at scale.

---

## Finding 4 — ShedLock row staleness criteria are underspecified

**Severity:** Low

**Evidence:** The brief proposes deleting `shedlock` rows where `lock_until < now() - token-retention-days`. The `shedlock` table has three timestamp columns: `lock_until`, `locked_at`, and `locked_by`. A row whose `lock_until` is in the past may simply be a lock that was acquired and released normally; a row whose `lock_until` is far in the future may be a currently held lock. Using `lock_until` as the staleness predicate is reasonable but not self-evident. The brief also does not state whether rows with `lock_until` in the future should ever be deleted (they should not).

**Recommended brief amendment:** Specify the exact predicate, e.g., `DELETE FROM shedlock WHERE lock_until < :cutoff AND lock_until < now()` — the second clause is a safety guard to ensure currently held locks are never pruned. Also note that the retention threshold is reused from `token-retention-days` because ShedLock rows are low-cardinality (OQ2).

---

## Finding 5 — Outbox table accumulation is not addressed

**Severity:** Low

**Evidence:** The task statement scopes cleanup to verification tokens, revoked families/archives, and ShedLock rows. The `outbox` table (D-009) is not mentioned. Outbox rows are typically deleted by the relay after successful publishing; if the relay fails or is slow, they can accumulate indefinitely — the same class of unbounded-growth problem this task exists to solve. The brief explicitly excludes "any change to how verification tokens or refresh-token families are created/consumed elsewhere" but does not address whether outbox cleanup is in or out.

**Recommended brief amendment:** Explicitly state that outbox cleanup is **out of scope** for T30 (it belongs to the outbox relay / a separate operational task), so the Phase 5/6 implementation does not silently add it and the test suite does not imply it should happen.

---

## Finding 6 — `LockProvider` bean configuration is not specified

**Severity:** Low

**Evidence:** The brief lists `shedlock-spring` and `shedlock-provider-jdbc-template` as dependencies and says `@EnableSchedulerLock` must be added to the application class, but it does not mention the required `LockProvider` bean that wires ShedLock to `JdbcTemplate`. Without this bean, `@SchedulerLock` will fail at runtime.

**Recommended brief amendment:** Add the `LockProvider` bean to the Files to Create/Modify list, e.g., a `@Bean JdbcTemplateLockProvider lockProvider(DataSource dataSource)` in a configuration class, so Phase 5 does not omit it.

---

## Non-Issues Confirmed

- **Cascade delete for archive rows:** verified in `V2__refresh_token_family_tracking.sql` — `refresh_token_archive.family_id` has `ON DELETE CASCADE`, so deleting old revoked families correctly removes their archives without additional application code.
- **ShedLock table exists:** verified in `V5__lockout_cleanup_and_shedlock.sql`, satisfying L1.
- **Config keys:** `themistra.auth.cleanup.{cron,token-retention-days,family-retention-days}` are specified in `design.md` with sensible defaults.
- **No new HTTP surface / no R46 implications:** this is a background job with no response body.
- **ShedLock version:** 7.7.0 is the current stable release and the brief's compatibility check against Spring Boot 3.5.4 is reasonable.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (Freeze Task Brief / Human Approval) on approval.
