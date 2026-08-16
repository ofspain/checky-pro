<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T29 · Phase 8 — Independent Code Review

Consumes the Phase 6 implementation and Phase 7 self-review. Reviewed with fresh, adversarial eyes; findings only.

---

## Finding 1 — Revoke save on untracked authorization creates a phantom family

**Issue:** Ordering bug / incorrect state transition.

**Evidence:** `ReuseDetectingAuthorizationService.save(...)` calls `trackRefreshTokenIfPresent(authorization)` before `revokeFamilyIfRefreshTokenInvalidated(authorization)`. When no family exists yet, `trackRefreshTokenIfPresent` calls `tracker.trackIssuance(...)` and creates a new `RefreshTokenFamily`. The subsequent `revokeFamilyIfRefreshTokenInvalidated` then detects the invalidated refresh token and calls `tracker.revokeForAuthorization(...)`, which finds the just-created family and revokes it.

This produces a brand-new family row that is immediately revoked, plus a `session.revoked` audit event for a session that was never active in the family table. The frozen brief implies no-op when no family exists; the current implementation violates that spirit for the no-pre-existing-family case.

**Recommendation:** Detect the revoke condition before tracking. In `save(...)`, call `revokeFamilyIfRefreshTokenInvalidated(authorization)` first; if the refresh token is invalidated, skip `trackRefreshTokenIfPresent(...)` entirely for that save. This avoids writing a phantom family row and emitting a misleading audit event.

**Confidence:** High.

---

## Finding 2 — `tracker.revokeForAuthorization(...)` exceptions propagate uncaught

**Issue:** Missing edge-case error handling / caller-visible failure for an already-successful SAS operation.

**Evidence:** `ReuseDetectingAuthorizationService.save(...)` calls `delegate.save(authorization)` first, then tracking, then `revokeFamilyIfRefreshTokenInvalidated(...)`. Inside the latter, `tracker.revokeForAuthorization(...)` is called with no try/catch. The adjacent `auditSessionRevoked(...)` block is explicitly wrapped so an audit failure cannot undo the revoke. If `revokeForAuthorization` itself throws (e.g., a transient DB failure during `familyRepository.save(family)` inside its own `@Transactional` boundary), the exception propagates out of `save(...)` and into SAS's revocation provider. The client receives an error response even though SAS already successfully invalidated the token.

The frozen brief's D1 accepts that SAS invalidation and family revocation are non-atomic and that the family row may lag, but D1 does not address a caller-visible 500 for a call that SAS already treated as successful.

**Recommendation:** Apply the same defensive try/catch pattern used for `auditSessionRevoked` to the `tracker.revokeForAuthorization(...)` call. Log the failure and allow `save(...)` to return normally. This aligns with D1's safe-failure-direction philosophy and matches the audit-call hardening in D2.

**Confidence:** High.

---

## Finding 3 — Concurrent `/oauth2/revoke` calls can produce duplicate audit rows

**Issue:** Race condition / AC7 violation under concurrency.

**Evidence:** `RefreshTokenTracker.revokeForAuthorization(...)` reads the family, filters on `!family.isRevoked()`, calls `family.revoke(...)`, saves, and returns `true`. There is no pessimistic locking. Two concurrent calls can both observe the family as not-yet-revoked, both commit (idempotent at the row level), and both return `true`. Back in `ReuseDetectingAuthorizationService`, both `true` returns trigger `auditSessionRevoked(...)`, resulting in two `session.revoked` audit rows for a single family.

AC7 states exactly one audit row per family revoked via this path. The self-review notes the same unlocked pattern exists elsewhere, but that does not exempt T29 from its own acceptance criterion.

**Recommendation:** Choose one of:
1. Accept and document the duplicate-audit risk as a low-probability concurrency residual, updating AC7 to say at least one row under concurrency.
2. Harden the audit path to be idempotent by keying on family plus reason, though this is cross-cutting.
3. Use SELECT FOR UPDATE in `revokeForAuthorization` to serialize concurrent revokes.

Option 1 is least invasive and aligns with existing optimistic patterns; if chosen, AC7 should be amended.

**Confidence:** Medium.

---

## Finding 4 — `session.revoked` event-type literal is duplicated

**Issue:** Informational / future maintenance hazard.

**Evidence:** The string `session.revoked` appears as an inline literal in `SessionService.java` (T28) and `ReuseDetectingAuthorizationService.java` (T29). A future typo or divergence would silently split audit/analytics queries that group by event type. The same pattern exists for `token.reuse_detected`, so this is not a new problem introduced by T29.

**Recommendation:** Out of scope for this task. Note for a future cross-cutting cleanup to introduce a shared `AuditEventTypes` constants holder.

**Confidence:** High that it is a real concern / Low that it needs fixing here.

---

## Non-Issues Confirmed

- Audit `actorUuid` handling correctly matches T28's `SessionService.recordAudit` pattern.
- Audit failure rollback protection (D2) works as intended.
- Null-safety on `getRefreshToken()` is correct.
- Access-token-only invalidation is correctly excluded.
- `findByToken` reuse detection is untouched.
- Module boundaries are respected.
- Explicit `familyRepository.save(...)` in `revokeForAuthorization` matches the frozen brief.
- Already-revoked families return `false` and produce no audit.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (Human Approval / Review Resolution) on approval.
