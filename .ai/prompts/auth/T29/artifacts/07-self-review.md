<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T29 · Phase 7 — Self Review

Reviews the Phase 6 diff (`RefreshTokenTracker.java` + `ReuseDetectingAuthorizationService.java`)
against the frozen brief and `agents.md`, across correctness, boundary conditions, null-safety,
thread-safety, transaction boundaries, module boundaries, idempotency, enumeration-safety, and
readability. Findings only — no fixes applied here.

---

## Finding 1 — `tracker.revokeForAuthorization(...)`'s own exceptions are not defended against, unlike the audit call right next to it

**Severity:** Medium

**Evidence:** `ReuseDetectingAuthorizationService.java:112` calls
`tracker.revokeForAuthorization(authorization.getId(), "OAUTH2_REVOKE")` with no try/catch, while
the very next block (`auditSessionRevoked`, lines 118-130) wraps its own `auditService.record(...)`
call specifically so a failure there can't undo the revoke (D2). If `revokeForAuthorization`
itself throws — e.g. a transient DB failure during `familyRepository.save(family)` inside its own
`@Transactional` boundary — the exception propagates uncaught through
`revokeFamilyIfRefreshTokenInvalidated` and out of `save(...)` entirely, into SAS's
`OAuth2TokenRevocationAuthenticationProvider.authenticate(...)`. Since `delegate.save(authorization)`
(line 43) already committed the SAS-side token invalidation earlier in the same `save()` call before
this new code even runs, the practical effect is: the client's `/oauth2/revoke` call would surface
as an error (likely a 500) even though the token has, in fact, already been genuinely revoked
SAS-side — a caller-visible failure describing a state that isn't quite true. This is a materially
different consequence from D1 (which accepts the family row silently *lagging*, still returning
success to the caller) — D1 doesn't cover an *error response* being returned for what was, from
SAS's perspective, a successful revoke.

**Recommendation:** This is retry-safe either way (a client that gets a 500 and retries will
re-invalidate an already-invalidated token harmlessly, and eventually succeed once the transient
failure clears), so it may be an acceptable trade-off — but it should be an explicit Phase 9
decision, not an accidental asymmetry between the two new call sites. Options: (a) leave as-is and
document that a family-revoke persistence failure surfaces as an ordinary 500 to the revoke caller,
retry-safe; or (b) wrap the `tracker.revokeForAuthorization(...)` call in the same defensive
try/catch pattern as the audit call, accepting a silently-lagging family row (matching D1's stated
preference) in exchange for never surfacing an error for a call that SAS itself already treats as
successful.

---

## Finding 2 — `"session.revoked"` is now a duplicated literal across two files with no shared constant

**Severity:** Low (informational)

**Evidence:** The event-type string `"session.revoked"` appears as an inline literal both in
`SessionService.java:131` (T28) and now `ReuseDetectingAuthorizationService.java:122` (T29). A
future typo in either location (e.g. a copy-paste drift) would silently fragment audit/analytics
queries grouping by event type, since nothing enforces the two strings staying identical.

**Recommendation:** Not a regression specific to this task — the codebase has no shared constants
class for any audit event-type string today (`"token.reuse_detected"` is similarly a bare literal,
unchanged by this task), so this follows existing convention rather than deviating from it. Worth
noting for a possible future cross-cutting cleanup (a shared `AuditEventTypes` constants holder),
but out of this task's authorized scope to introduce unilaterally.

---

## Non-Issues Confirmed

- **Ordering/atomicity (Kimi Finding 1, D1):** re-confirmed at the implementation level —
  `delegate.save(...)` → `trackRefreshTokenIfPresent(...)` → `revokeFamilyIfRefreshTokenInvalidated(...)`
  is exactly the frozen order; no change needed, this residual is already accepted.
- **No double-audit on the no-op path (Finding 2 from Phase 3, AC2):** confirmed —
  `revokeForAuthorization` returns `false` on an already-revoked family (line 94's filter), and
  `auditSessionRevoked` is only called when `revoked` is `true` (line 113), so the two are correctly
  coupled through the single boolean, not two independently-checked conditions that could drift.
- **Null-safety:** `refreshToken == null || !refreshToken.isInvalidated()` (line 108) correctly
  short-circuits before ever calling `.isInvalidated()` on a null token; `parseAccountUuid` guards
  its own null input.
- **Existing `trackRefreshTokenIfPresent`/`trackRotation` interaction:** re-verified — a revoke-
  triggered save presents the *same* refresh-token hash as the family's current one, so
  `trackRotation`'s existing `family.getCurrentTokenHash().equals(newRefreshTokenHash)` guard
  already makes it a no-op; nothing new needed there.
- **Module boundaries (L12):** both changed files stay within `com.themistra.auth.token`; no new
  imports outside the package.
- **Enumeration-safety / R46:** not applicable — this task has no HTTP response surface of its own.
- **Thread-safety pattern (read-then-write race in `revokeForAuthorization`):** the
  `findByAuthorizationId` → check-not-revoked → `revoke` → `save` sequence has no pessimistic
  locking, so two concurrent `/oauth2/revoke` calls for the same token could theoretically both
  read "not yet revoked" and both proceed — but this is the exact same optimistic, unlocked pattern
  already used by `SessionService.revokeOne`/`revokeAll` (T28) and `checkAndRegisterPresentation`
  (pre-existing) throughout this codebase, not a new class of risk introduced by this task.

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
