<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T29 · Phase 9 — Review Resolution

**Human Approval gate.** Consumes the self-review (Phase 7, 2 findings) and independent review
(Phase 8, Kimi, 4 findings). Every finding verified against actual source before disposition
(standing pipeline discipline) — none were misreadings. femi decided the one finding with genuine
design trade-off via human gate; the rest were unambiguous bugs, applied directly.

## Self-review (Phase 7) findings

| # | Finding | Disposition |
|---|---|---|
| 1 | `tracker.revokeForAuthorization(...)`'s own exceptions not defended against, unlike the adjacent audit call | **Superseded by Kimi's Phase 8 Finding 2** (same finding, independently confirmed) — resolved together below. |
| 2 | `"session.revoked"` literal duplicated across `SessionService`/`ReuseDetectingAuthorizationService`, no shared constant | **Rejected — out of scope.** Matches existing codebase-wide convention (no audit event-type constants class exists anywhere); introducing one unilaterally in this task would be unrelated refactoring beyond T29's authorized files. Logged for a possible future cross-cutting cleanup, same disposition Kimi independently reached (its own Finding 4). |

## Independent review (Phase 8, Kimi) findings

| # | Finding | Confidence | Disposition |
|---|---|---|---|
| 1 | Revoke-shaped save on an untracked authorization creates a phantom family + spurious audit | High | **ACCEPTED, applied.** Verified against actual source: `save(...)` called `trackRefreshTokenIfPresent` before `revokeFamilyIfRefreshTokenInvalidated`, so a `/oauth2/revoke` call reaching this decorator for an authorization it had never tracked before (e.g. after a deploy/restore gap, or any operational discontinuity between original issuance and this tracker running) would create a brand-new family via `trackIssuance`, then immediately revoke that just-created row and audit a session that was never genuinely active. |
| 2 | `tracker.revokeForAuthorization(...)` exceptions propagate uncaught, surfacing a caller-visible error for a call SAS already treated as successful | High | **ACCEPTED, applied.** Verified `AuditService.record`'s `REQUIRES_NEW` propagation doesn't prevent an uncaught exception from rolling back the *caller's* transaction; the same class of risk applies to the tracker call itself, which had no defensive wrapping unlike the adjacent audit call. Same finding as my own Phase 7 Finding 1, independently confirmed. |
| 3 | Concurrent `/oauth2/revoke` calls can produce duplicate `session.revoked` audit rows (AC7 violation under concurrency) | Medium | **ACCEPTED as documented residual, femi's gate decision.** `revokeForAuthorization` has no pessimistic locking — two concurrent calls could both read "not yet revoked" and both return `true`, both triggering an audit. femi chose to accept this (matches T28's own unlocked pattern elsewhere in the codebase; a rare double-audit on a genuine client-side double-revoke is low-impact) over adding `SELECT FOR UPDATE` locking, which would be a bigger, more invasive change than this task's scope. **AC7 amended** (see below). |
| 4 | `"session.revoked"` literal duplicated, no shared constant | High (real) / Low (needs fixing here) | **Rejected — out of scope**, same disposition as my own Phase 7 Finding 2 above; Kimi reached the identical conclusion independently. |

## Exact changes made

**`services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`**
(resolves Findings 1 + 2 together, since fixing the ordering bug required restructuring the same
method the exception-handling fix also touches):

- `save(...)` no longer unconditionally calls `trackRefreshTokenIfPresent(...)` followed by a
  separate invalidation check. It now branches: `isRefreshTokenInvalidated(authorization)` decides
  between `revokeFamilyForInvalidatedRefreshToken(authorization)` (revoke path) and
  `trackRefreshTokenIfPresent(authorization)` (ordinary issuance/rotation path) — mutually
  exclusive, so a revoke-shaped save can never also run `trackIssuance` for the same authorization
  in the same call.
- The old `revokeFamilyIfRefreshTokenInvalidated`'s null/invalidated guard was extracted into a new
  `private static boolean isRefreshTokenInvalidated(OAuth2Authorization)`.
- The revoke-and-audit logic was renamed `revokeFamilyForInvalidatedRefreshToken` and now wraps the
  `tracker.revokeForAuthorization(...)` call itself in try/catch (mirroring the pre-existing
  protection on `auditSessionRevoked`): on failure, logs and returns without auditing, consistent
  with D1's accepted safe-failure-direction (SAS's own invalidation already committed via
  `delegate.save(...)` earlier in the same `save()` call; a family-persistence hiccup must not
  surface as a caller-visible error for a call SAS already treated as successful).

No change was made for Finding 3 (accepted as a documented residual, not a code change) or Finding
4 / self-review Finding 2 (rejected, out of scope).

## Acceptance criteria amendment

- **AC7** (frozen brief) changes from "Exactly one `session.revoked` audit row per family revoked
  via this path" to: **"Exactly one `session.revoked` audit row per family revoked via this path
  under normal (non-concurrent) operation; under a genuine concurrent double-revoke race on the
  same token, at least one row is guaranteed, with a documented (not defended-against) possibility
  of two."** This is the only frozen-brief amendment arising from this review round.

## Verification performed

- `mvn -pl services/auth -am clean compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ReuseDetectingAuthorizationServiceTest,RefreshTokenTrackerTest'`
  — all 21 pre-existing tests still pass; no regression from the restructured `save(...)` method.
  (Phase 10 will add dedicated tests proving the phantom-family fix and the exception-swallowing
  behavior specifically — none of the 21 existing tests exercise the "no pre-existing family +
  invalidated token" scenario Finding 1 identified, which is exactly why it slipped through Phase 6
  ungated.)

---

**Phase 9 complete — review resolved, femi signed off.** Proceed to Phase 10 (Test Generation).
