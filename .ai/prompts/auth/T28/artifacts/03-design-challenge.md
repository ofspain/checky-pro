# auth · T28 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T28 — Session listing/revocation |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |
| **Status** | Findings for Phase 4 human gate |

## Summary

The TIB correctly identifies the module-boundary and authorization-removal requirements, but it leaves three load-bearing design points ambiguous: the repository query shape for idempotent single revoke, the behavior when the SAS authorization is already gone, and the bulk-revoke transaction/failure semantics. These need to be locked before implementation.

---

## Findings

### 1. The repository query for single revoke must not filter by revoked_at if idempotent 204 is required

- **Severity:** High
- **Evidence:**
  - The TIB states DELETE /{familyId} returns 204 including idempotent success on an already-revoked family.
  - It also states SessionNotFoundException (404) is for a familyId not owned by the caller or not existing at all.
  - The TIB says the repository needs one query method to resolve a family by id scoped to or checked against a principal. This is ambiguous between (a) findByFamilyIdAndPrincipalName and (b) findByFamilyIdAndPrincipalNameAndRevokedAtIsNull.
  - If option (b) is chosen, an already-revoked but owned family will not be found, and the service will throw SessionNotFoundException -> 404 instead of the promised idempotent 204.
- **Recommended brief amendment:** Lock the repository method as findByFamilyIdAndPrincipalName (no revokedAt filter), and specify that SessionService.revokeOne loads the row, verifies ownership via the principal match, and then calls family.revoke (which is already idempotent). Only genuinely nonexistent or unowned families produce 404.

---

### 2. OAuth2AuthorizationService.findById may return null; the service must remain idempotent

- **Severity:** Medium
- **Evidence:**
  - The TIB says revoke removes the live SAS authorization via OAuth2AuthorizationService.
  - SAS authorizations can disappear independently of this task — cleanup jobs, reuse detection, or manual DB operations can remove the oauth2_authorization row while the refresh_token_family row remains.
  - If SessionService calls authorizationService.findById(authorizationId) and then unconditionally dereferences the result or treats null as an error, an idempotent re-revoke (or a revoke after cleanup) could throw a 500.
- **Recommended brief amendment:** Add a constraint: SessionService must treat a null result from OAuth2AuthorizationService.findById as a no-op (the authorization is already gone) and continue to revoke the family row and audit. This preserves idempotency and matches the already-revoked -> 204 contract.

---

### 3. Bulk revoke failure semantics are unspecified

- **Severity:** Medium
- **Evidence:**
  - The TIB says bulk revoke's per-family operations should be considered as a unit but also notes audit itself is already REQUIRES_NEW and defers exact transaction boundaries to Phase 5.
  - If SessionService.revokeAll iterates over N families inside one @Transactional method, a failure on the k-th family (e.g., a transient DB error or authorization-service hiccup) will roll back the entire transaction, leaving earlier families unrevoked and earlier audit rows already committed because audit is REQUIRES_NEW.
  - This creates a partial-revoke state that is hard to reason about and may not match user expectations.
- **Recommended brief amendment:** Lock one of the following behaviors:
  - All-or-nothing: a failure anywhere rolls back the DB family revokes; audit rows from successful earlier iterations remain committed; the endpoint returns 500. Document this partial-audit outcome.
  - Best-effort with full audit: catch per-family exceptions, continue to the next family, and return 204 after attempting all; log errors for any failed individual revokes.
  - Make the choice explicit and add the corresponding AC/test.

---

### 4. Active sessions definition is ambiguous

- **Severity:** Low
- **Evidence:**
  - Decision D-D says GET /accounts/me/sessions returns only non-revoked families.
  - A non-revoked family may still correspond to an SAS authorization that has already expired (e.g., refresh-token lifetime elapsed but the row has not been cleaned up yet).
  - R36 says list active sessions — users would not consider an expired session active.
- **Recommended brief amendment:** Either (a) define active strictly as revoked_at IS NULL (current D-D) and document that expired-but-not-yet-cleaned families may appear, or (b) add a join/filter against oauth2_authorization to exclude expired authorizations. Option (a) is simpler and avoids cross-table coupling; option (b) is more user-accurate but requires touching the SAS authorization table.

---

### 5. principalName is assumed to be the account UUID, but SAS allows other principal shapes

- **Severity:** Low
- **Evidence:**
  - RefreshTokenFamily.principalName is a String.
  - For interactive grants it is the account UUID, but for client-credentials or other grants it could be a client_id.
  - AccountController derives the caller via UUID.fromString(authentication.getName()) and passes that string to SessionService. If a caller somehow authenticates with a non-UUID principal, UUID.fromString will throw and the endpoint will 500.
- **Recommended brief amendment:** Accept this as the same named limitation already documented at T26's D7, or add a brief note in T28's Constraints: These endpoints are intended for interactive account sessions; non-UUID principals produce the same pre-existing 500 as GET /accounts/me. This prevents a future maintainer from treating it as a T28 regression.

---

### 6. deviceLabel will be null for all sessions until O3 is resolved

- **Severity:** Low / informational
- **Evidence:**
  - The TIB explicitly scopes out resolving design.md O3 (device-label source) and notes that ReuseDetectingAuthorizationService.trackRefreshTokenIfPresent passes null as the device label.
  - R36 and the Outputs table require deviceLabel in the response, so the API will return deviceLabel: null for every session at launch.
- **Recommended brief amendment:** No code change required, but add an AC or note confirming that null device labels are the expected initial behavior and that populating them is deferred to the spec author (O3). This avoids a false bug report when the first integration test sees nulls.

---

## Non-Findings

- Module home for SessionService: placing it in token is correct because RefreshTokenFamilyRepository is package-private there; cross-module service dependency is precedented and allowed.
- Controller home: extending AccountController for /accounts/me/sessions is consistent with the existing /accounts/me resource family.
- Exception handler in token: SessionExceptionHandler will be picked up globally by Spring's RestControllerAdvice resolution, consistent with T26's cross-module exception experience.
- Not reusing RefreshTokenTracker.revokeAllForPrincipal: correct — that method does not remove live SAS authorizations, which R38 requires.
- No Flyway migration: existing V2 schema already supports the required state changes.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (human gate) on approval.
