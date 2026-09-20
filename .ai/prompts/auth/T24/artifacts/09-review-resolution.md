> **STATUS: RESOLVED.** Human sign-off given 2026-08-10 ("go ahead") on the resolution set below, presented alongside the Phase 8 findings. `mvn -pl services/auth -am compile` and `mvn -pl services/auth test -Dtest=ArchitectureTest` both pass after every change.

# auth · T24 · Phase 9 — Review Resolution

10 findings from Phase 8 (`08-independent-review.md`), 3 of which independently re-derived Phase 7 self-review findings.

---

**1. `ApiKey.prefix` still maps `length = 16` after `V7` widened the column to `VARCHAR(32)`.**
**Disposition: ACCEPTED — real bug.** `ApiKey.prefix`'s `@Column` annotation changed to `length = 32`, matching the actual widened column. This would otherwise have failed Hibernate's `ddl-auto=validate` schema check at Spring context startup — the migration and the entity mapping had silently drifted apart in Phase 6.

**2. `ApiKeyExceptionHandler` listed as required by the brief but missing.**
**Disposition: REJECTED — factually incorrect.** Verified directly against `04-frozen-task-brief.md` before accepting: the frozen brief explicitly lists `ApiKeyExceptionHandler` under **Scope → Out** ("deferred to T25/T26") and disposition #7 states outright "no handler class, no new `ProblemTypes` entries, this task." This finding appears to have been drawn from the earlier Phase 2 TIB (which did list the handler before Phase 3/4 deliberately deferred it), not the frozen brief that actually governs this task. No change made — the deferral was a deliberate, already-recorded decision, not an oversight.

**3. `ProblemTypes` has no API-key entries.**
**Disposition: REJECTED — follows from finding #2's incorrect premise.** Same frozen-brief disposition #7 explicitly says "no new `ProblemTypes` entries, this task" — this is the direct, intended consequence of deferring the handler, not a gap.

**4. `exchange`'s audit target misattributes rejections when multiple candidates share a prefix.**
**Disposition: ACCEPTED** (independently re-derived Phase 7 finding #1). **Change:** when a specific candidate's hash matched but it's revoked/expired (`matched != null`), the audit now targets that row's own account. Only when no candidate's hash matched at all (`matched == null`) does it fall back to the first prefix-sharing candidate — consistent with disposition #10's "a row was matched" meaning a prefix-level candidate exists, not that its hash specifically matched.

**5. `revoke` records an audit event even when no state change occurred.**
**Disposition: ACCEPTED** (independently re-derived Phase 7 finding #2). **Change:** `revoke` now checks `revokeIfActive`'s return value and only calls `recordAudit(...)` when it returns a positive count — matching `RoleService.removeRole`'s established "nothing changed, nothing to audit" precedent, which `ApiKeyRepository.revokeIfActive`'s own Javadoc had already (prematurely) claimed to follow.

**6. `exchange`'s malformed/unknown-prefix paths skip the constant-time comparison, creating a timing signal.**
**Disposition: ACCEPTED** (independently re-derived Phase 7 finding #3). **Change:** both paths now perform a dummy `apiKeyHasher.matches(...)` against a fixed, unattainable `DUMMY_HASH` constant before rejecting, normalizing their timing against the real-comparison path.

**7. `ApiKey`'s class Javadoc is stale about how `lastUsedAt`/`revokedAt` get updated.**
**Disposition: ACCEPTED.** Updated to describe the actual T24 design (conditional `@Modifying` repository queries, not entity mutators) instead of the T23-era forward-looking language that no longer matches what was built.

**8. No service-layer test coverage yet.**
**Disposition: Confirmed, no action needed this phase** (non-issue — Phase 10's job, as the finding itself acknowledges). Its specific recommendations (assert V7/entity mapping starts cleanly; assert prefix-collision audit targets the matched row; assert idempotent revoke emits no duplicate audit; assert exception→status mappings once `ApiKeyExceptionHandler` exists in T25/T26) are noted for Phase 10 to pick up.

**9. `exchange` doesn't verify the owning account is still `ACTIVE`.**
**Disposition: ACCEPTED — documentation only, no behavior change.** A suspended/deleted merchant's existing key remaining valid until explicitly revoked is a real, latent gap, but R30/R32/R33 (this task's scoped requirements) don't call for an account-status check in `exchange`, and adding one means an extra account lookup on every exchange call — a potentially hot path. `exchange`'s Javadoc now documents this explicitly as a known, deliberate scope limit for a future task to weigh in on, rather than leaving it silently unaddressed.

**10. No `agents.md`/LOCKED-decision conflicts.**
**Disposition: Confirmed, no action needed** (non-issue).

**2 of 10 findings rejected** (both based on a factual misreading of the frozen brief, verified against source before rejecting).

---

## Files changed this phase
- `apikey/ApiKey.java` — `prefix` column mapping widened to `length = 32`; class Javadoc updated to describe the actual `lastUsedAt`/`revokedAt` update mechanism.
- `apikey/ApiKeyService.java` — `revoke` now conditionally audits on actual state change; `exchange`'s audit-target resolution now prefers the specifically-matched row's account; `exchange`'s malformed/unknown-prefix paths perform a dummy constant-time comparison; new `DUMMY_HASH` constant; `exchange`'s Javadoc documents the known ACTIVE-status limitation.

No production code outside `apikey/` touched. No test file touched (none exists yet — Phase 10). No public API signature changed, no class renamed.
