<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T29 · Phase 3 — Design Challenge

Consumes `artifacts/02-task-implementation-brief.md`. Adversarial review of the T29 SAS revoke integration brief before the Phase 4 freeze. Findings only — no redesign, no implementation.

---

## Finding 1 — The SAS authorization invalidation and the family revocation are not atomic

**Severity:** Medium

**Evidence:** `ReuseDetectingAuthorizationService.save(...)` (`token/ReuseDetectingAuthorizationService.java:42-45`) is not `@Transactional`. It calls `delegate.save(authorization)` first, then `trackRefreshTokenIfPresent(...)`, and the brief proposes adding `tracker.revokeForAuthorization(...)` afterward. The new `revokeForAuthorization` method is `@Transactional`, but because the decorator's `save()` has no transaction, the tracker call runs in its own transaction after the delegate's save has already completed. If `revokeForAuthorization` fails (e.g., DB write or audit failure), the SAS authorization remains invalidated while the family row stays unrevoked. The brief discusses this exact failure direction for T28 (a family that *looks* revoked but still works is worse than the inverse), but it does not pin down T29's analogous risk: a family that *looks* active but whose tokens no longer work.

**Recommended brief amendment:** Add a decision (D1) accepting that the SAS-authorization invalidation and the family-row revocation are best-effort and not atomic, with the chosen ordering being the safer direction (token unusable even if the row lags). Explicitly state that this is a known, accepted residual display inconsistency, analogous to T28's bulk-revoke Finding 1.

---

## Finding 2 — "No-op if already revoked" must also mean "no duplicate audit"

**Severity:** Medium

**Evidence:** The brief states AC2 as "Re-processing an already-revoked family via this path is a no-op (no exception, no reason/timestamp overwrite)" and AC7 as "Exactly one `session.revoked` audit row is recorded per family revoked via this path." The proposed `revokeForAuthorization` shape is described as "no-ops if not found or already revoked" but the brief does not explicitly say that the no-op path suppresses the audit call. If the implementation audits unconditionally after calling `family.revoke(...)`, an already-revoked family would still produce a second audit row on re-processing, violating AC2/AC7.

**Recommended brief amendment:** Add an explicit constraint: `revokeForAuthorization` must check `family.isRevoked()` before revoking and before auditing; when already revoked, it returns without calling `AuditService.record(...)`. Add a required test explicitly asserting no second audit call on the already-revoked path.

---

## Finding 3 — Audit `actorUuid` is not specified

**Severity:** Low

**Evidence:** The brief resolves OQ3 by recording event type `"session.revoked"` with outcome `SUCCESS` and `accountUuid` parsed from `principalName`. It does not specify the `actorUuid` field of `RecordAuditEventRequest`. T28's `SessionService.recordAudit` sets both `accountUuid` and `actorUuid` to the same caller UUID for self-service revocation; T29 is also a caller-initiated revocation (via `/oauth2/revoke`), so the actor is the same account. An inconsistent choice (e.g., `actorUuid = null`) would create divergent audit rows for the same conceptual event.

**Recommended brief amendment:** State that `actorUuid` equals the parsed account UUID (same as T28's self-service revoke), with the same non-UUID fallback to `null`.

---

## Finding 4 — Persistence mechanism inside `revokeForAuthorization` is ambiguous

**Severity:** Low

**Evidence:** The brief says `revokeForAuthorization` should mirror `revokeAllForPrincipal`'s "naming/shape (`@Transactional`, looks up via repository, calls `family.revoke(reason, clock.instant())`, no-ops if not found or already revoked)." `revokeAllForPrincipal` relies on JPA dirty-checking and does not call `familyRepository.save(...)`. For a single-family operation, the same dirty-checking works, but `SessionService.revokeOne` (T28) explicitly calls `familyRepository.save(family)` for clarity. The brief does not state whether `revokeForAuthorization` should call `save()` or rely on dirty-checking, leaving room for an inconsistent implementation.

**Recommended brief amendment:** Explicitly state whether `revokeForAuthorization` calls `familyRepository.save(family)` or relies on dirty-checking at transaction commit. Either is acceptable; the brief should choose one and align the acceptance criteria / tests accordingly.

---

## Finding 5 — A family-revoke success followed by an audit failure can leave the family unrevoked

**Severity:** Medium

**Evidence:** If `revokeForAuthorization` performs the revoke and the audit inside the same `@Transactional` method (as the brief implies), an exception from `auditService.record(...)` after `family.revoke(...)` will roll back the entire transaction, undoing the family revocation. The SAS authorization, however, was already invalidated in the separate `delegate.save(...)` transaction. The result is the same failure direction as Finding 1: a token that no longer works but a family row that still looks active.

**Recommended brief amendment:** Decide whether the audit must be inside the same transaction as the revoke (simple, but carries this failure mode) or outside it (e.g., via a `REQUIRES_NEW` audit call, matching `AuditService.record`'s own propagation as changed in T18). Document the chosen trade-off explicitly; do not leave it to implementation-time accident.

---

## Finding 6 — Missing required test for non-UUID principal audit fallback

**Severity:** Low

**Evidence:** The required tests list covers active family, already-revoked family, access-token-only invalidation, no refresh token, ordinary rotation, and ordinary issuance. None cover the case where `principalName` is not a UUID (e.g., a non-interactive client credential or a malformed principal). The brief says parsing should fall back to `null` account attribution, mirroring `auditReuseDetected`, but without a test this fallback is unprotected.

**Recommended brief amendment:** Add a required unit test: "Save with refresh token invalidated + family whose principalName is not a UUID → revoke succeeds, audit row has `accountUuid = null` (no exception)."

---

## Finding 7 — Integration test glosses over `/oauth2/revoke` client authentication

**Severity:** Low

**Evidence:** The brief proposes an integration test that calls `POST /oauth2/revoke` over real HTTP and asserts the family row is revoked. The OAuth2 revocation endpoint is typically protected by client authentication (client credentials, basic auth, or mTLS depending on SAS configuration). The brief does not state how the test will authenticate the client, obtain the refresh token value, or handle the token endpoint vs. revocation endpoint wiring. This makes the test design underspecified and potentially unimplementable without revisiting SAS client configuration.

**Recommended brief amendment:** Either scope the integration test down to a Spring-level test that directly invokes `OAuth2AuthorizationService.save(...)` with an invalidated refresh token (verifying the decorator behavior without the full SAS HTTP handshake), or explicitly document the assumed client credentials / token-acquisition steps needed to call the real revocation endpoint.

---

## Finding 8 — Proposed method is not a pure mirror of `revokeAllForPrincipal`

**Severity:** Low

**Evidence:** The brief describes `revokeForAuthorization` as mirroring `revokeAllForPrincipal`'s naming/shape, but it also assigns it an audit responsibility that `revokeAllForPrincipal` does not have. This is a reasonable design choice, but calling it a "mirror" is slightly misleading and could lead a future reader to expect identical semantics (including no audit).

**Recommended brief amendment:** Rephrase to say `revokeForAuthorization` *follows the same lookup/revoke pattern* as `revokeAllForPrincipal` but additionally records a `session.revoked` audit event, since it is the integration point between SAS revocation and the audit log.

---

## Non-Issues Confirmed

- **No conflict with reuse detection:** the brief correctly notes that an archived/superseded token presented to `/oauth2/revoke` is already handled by `findByToken`'s reuse check before `save()` is reached.
- **No change to `findByToken` behavior:** the proposed change is confined to `save(...)` and the new tracker method, leaving reuse detection untouched.
- **Access-token-only invalidation is correctly excluded:** checking `getRefreshToken() != null && getRefreshToken().isInvalidated()` naturally handles access-token-only and no-refresh-token cases.
- **Module boundaries are respected:** all proposed changes stay inside `com.themistra.auth.token`.
- **Reason string distinctness:** `"OAUTH2_REVOKE"` is appropriately distinct from `"USER_REVOKED"`, `"USER_REVOKED_ALL"`, and `"REUSE_DETECTED"`.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (Freeze Task Brief / Human Approval) on approval.
