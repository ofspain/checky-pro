<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T29 · Phase 5 — Implementation Plan

Consumes the frozen brief (`artifacts/04-frozen-task-brief.md`). Every file below traces to the
brief's own Files sections; no new file is added beyond what it authorizes.

## Files to create

None.

## Files to modify

- `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java`
- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`

## Public methods (signatures)

**`RefreshTokenTracker`** — one new method, alongside the existing four:

```java
/**
 * Revokes the single family for this authorization if one exists and isn't already revoked.
 * Returns true only when it actually performed the revoke, so the caller (the audit-owning
 * decorator) knows whether to record an event — false on "not found" or "already revoked" is
 * this method's own no-op signal, matching every other revoke path's idempotency.
 */
@Transactional
public boolean revokeForAuthorization(String authorizationId, String reason)
```

**`ReuseDetectingAuthorizationService`** — no new public methods; `save(OAuth2Authorization)`'s
signature is unchanged (still overriding the interface method), body gains one new call.

## Private methods

**`RefreshTokenTracker`** — none new; `revokeForAuthorization` is implemented directly using
`familyRepository.findByAuthorizationId(...)`, `.filter(family -> !family.isRevoked())`,
`family.revoke(reason, clock.instant())`, `familyRepository.save(family)` (Finding 4: explicit
`save()`, matching `SessionService.revokeOne`'s style over the older dirty-checking style used by
`revokeAllForPrincipal`).

**`ReuseDetectingAuthorizationService`** — two new private methods, plus one existing method
refactored to share logic with the new one:

```java
private void revokeFamilyIfRefreshTokenInvalidated(OAuth2Authorization authorization) {
    OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
    if (refreshToken == null || !refreshToken.isInvalidated()) {
        return;
    }
    boolean revoked = tracker.revokeForAuthorization(authorization.getId(), "OAUTH2_REVOKE");
    if (revoked) {
        auditSessionRevoked(authorization.getPrincipalName());
    }
}

private void auditSessionRevoked(String principalName) {
    UUID accountUuid = parseAccountUuid(principalName);
    try {
        auditService.record(new RecordAuditEventRequest(
                "session.revoked", AuditOutcome.SUCCESS, accountUuid, accountUuid,
                null, null, null, null));
    } catch (Exception e) {
        // D2: an audit failure must never undo the revoke that already succeeded above.
        log.error("Failed to audit session revoke for authorization {}", /* id via closure or param */ null, e);
    }
}

// New shared helper — auditReuseDetected's existing inline try/catch is refactored to call this,
// removing a near-identical duplicate rather than copy-pasting it a second time.
private static UUID parseAccountUuid(String principalName) {
    if (principalName == null) {
        return null;
    }
    try {
        return UUID.fromString(principalName);
    } catch (IllegalArgumentException ignored) {
        return null;
    }
}
```

`save(...)` gains exactly one new line after the existing `trackRefreshTokenIfPresent(authorization)`
call:

```java
@Override
public void save(OAuth2Authorization authorization) {
    delegate.save(authorization);
    trackRefreshTokenIfPresent(authorization);
    revokeFamilyIfRefreshTokenInvalidated(authorization);
}
```

`auditReuseDetected(String principalName)` (existing) is refactored to call the new
`parseAccountUuid` helper instead of its current inline try/catch — same observable behavior, one
fewer duplicated block. This is a same-file, same-concern extraction directly serving this task's
own new code, not unrelated cleanup.

*(Implementation-time note: the `log.error` call above needs the authorization id for a useful log
line — `revokeFamilyIfRefreshTokenInvalidated` will pass `authorization.getId()` into
`auditSessionRevoked` as a second parameter, or `auditSessionRevoked` will be inlined into
`revokeFamilyIfRefreshTokenInvalidated` directly. Exact split is a Phase 6 implementation detail,
not a planning-level decision worth freezing further.)*

## Entities used

`RefreshTokenFamily` (existing, via `RefreshTokenTracker`; `revoke(reason, now)` and `isRevoked()`
reused unchanged, no entity change).

## Repositories used

`RefreshTokenFamilyRepository.findByAuthorizationId(String)` (existing, already used by
`familyMissingFor`/`trackRotation`; no new query method).

## Services used

`AuditService.record(RecordAuditEventRequest)` (existing, already injected into
`ReuseDetectingAuthorizationService`; no new dependency wiring).

## Unit / integration tests required

**`RefreshTokenTrackerTest`** (existing file, extend — direct coverage of the new method in
isolation, mirroring how `trackIssuance`/`trackRotation`/`revokeAllForPrincipal` are each already
tested there):
1. `revokeForAuthorization` on an existing, unrevoked family → returns `true`, family revoked with
   the given reason.
2. `revokeForAuthorization` on an already-revoked family → returns `false`, no double-revoke
   (Finding 2's "no-op" half).
3. `revokeForAuthorization` on an unknown `authorizationId` → returns `false`, no exception.

**`ReuseDetectingAuthorizationServiceTest`** (existing file, extend — decorator-level behavior,
mocked `tracker`/`auditService`/`delegate`), per the frozen brief's Required Tests 1-9:
4. Save with refresh token invalidated + `tracker.revokeForAuthorization(...)` returns `true` →
   exactly one `auditService.record(...)` call, `session.revoked`, `accountUuid = actorUuid =`
   parsed UUID from `authorization.getPrincipalName()`.
5. Save with refresh token invalidated + `tracker.revokeForAuthorization(...)` returns `false`
   (already revoked) → no audit call (Finding 2).
6. Save with only the access token invalidated (refresh token present, not invalidated) → `tracker`
   is never asked to revoke, no audit call.
7. Save with no refresh token on the authorization (`getRefreshToken() == null`) → no
   `NullPointerException`, `tracker.revokeForAuthorization` never called.
8. Ordinary rotation save (new, non-invalidated refresh token) → existing `trackRotation` path
   unaffected; new revoke logic not triggered.
9. Ordinary first-issuance save → existing `trackIssuance` path unaffected.
10. `authorization.getPrincipalName()` is not UUID-shaped → revoke still succeeds (mocked tracker
    returns `true`), audit call has `accountUuid = actorUuid = null`, no exception (Finding 6).
11. `auditService.record(...)` throws → assert (via the mocked `tracker`) that
    `revokeForAuthorization` was still called/completed before the throw, and that `save(...)`
    itself does not propagate the exception (D2 — the outer call succeeds regardless of audit
    failure).
12. Full existing 8-test suite in this file stays green (regression check, not a new test).

**`RefreshTokenFamilyIntegrationTest`** (existing file, extend — this is the file that already
exercises `RefreshTokenTracker` against the real V2 schema via Testcontainers; D3's rescoped
integration test fits here since it's testing the same family-lifecycle guarantee through a
different entry point):
13. Autowire the real `OAuth2AuthorizationService` bean (the `ReuseDetectingAuthorizationService`
    the app context wires) alongside the existing `RefreshTokenTracker` autowire. Seed a family via
    `tracker.trackIssuance(...)` (existing helper pattern in this file), build a minimal real
    `OAuth2Authorization` for the same `authorizationId`/principal with its refresh token already
    invalidated (`OAuth2Authorization.from(...).invalidate(refreshToken).build()`, mirroring the
    SAS-source-traced shape from Phase 0/3), call `authorizationService.save(...)` on it, then
    assert `tracker.checkAndRegisterPresentation(<the family's current hash>)` now returns
    `UNKNOWN` (the family is gone/revoked) — or, more directly, expose the revocation via a second
    `tracker` call if a lookup-by-id convenience exists, else assert through the already-public
    `checkAndRegisterPresentation` behavior change exactly as `issuanceRotationAndReuseRevokeTheWholeFamily`
    already does for the reuse path.

## Execution order

1. `RefreshTokenTracker.revokeForAuthorization` (new method) — no dependents yet, safe to write and
   unit-test first in isolation (`RefreshTokenTrackerTest` additions 1-3).
2. `ReuseDetectingAuthorizationService` changes (`revokeFamilyIfRefreshTokenInvalidated`,
   `auditSessionRevoked`, `parseAccountUuid` extraction, `save(...)`'s one new line) — depends on
   step 1 existing.
3. `ReuseDetectingAuthorizationServiceTest` additions (4-11) + regression check (12) — proves step 2
   in isolation with mocked collaborators.
4. `RefreshTokenFamilyIntegrationTest` addition (13) — proves the whole stack end-to-end against the
   real schema (Docker-permitting; same environmental caveat carried forward from T25-T28).

No schema/migration step exists for this task (no new columns, no new table) — execution starts
directly at the tracker/service layer, per the brief's own Files-to-Modify list.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
