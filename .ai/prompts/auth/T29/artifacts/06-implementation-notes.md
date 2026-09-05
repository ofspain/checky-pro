<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T29 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) exactly per the Phase 5 plan.
Only the two authorized files were touched.

## What changed

**`RefreshTokenTracker.java`** — added `revokeForAuthorization(String authorizationId, String
reason)`, exactly as planned: `@Transactional`, looks up via the existing
`findByAuthorizationId`, filters out an already-revoked family (Finding 2's "no-op" half), calls
`family.revoke(reason, clock.instant())`, then an explicit `familyRepository.save(family)`
(Finding 4 — matches `SessionService.revokeOne`'s style, not `revokeAllForPrincipal`'s
dirty-checking style). Returns `true` only when it actually revoked, `false` on both "not found"
and "already revoked" — this boolean is the sole signal the decorator uses to decide whether to
audit, keeping the tracker itself audit-free exactly as before.

**`ReuseDetectingAuthorizationService.java`** — `save(...)` gained one new line calling
`revokeFamilyIfRefreshTokenInvalidated(authorization)`, placed after the existing
`trackRefreshTokenIfPresent(authorization)` call. New logic:
- `revokeFamilyIfRefreshTokenInvalidated` guards on `authorization.getRefreshToken()` being
  non-null and `isInvalidated()` — the exact SAS-source-traced signal from Phase 0/3 that
  distinguishes a `/oauth2/revoke`-of-a-refresh-token save from every other kind of save
  (ordinary rotation, ordinary issuance, access-token-only revocation, client-credentials grants
  with no refresh token at all). Calls `tracker.revokeForAuthorization(id, "OAUTH2_REVOKE")` and
  only audits when it returns `true`.
- `auditSessionRevoked` records a `session.revoked` event with `accountUuid`/`actorUuid` both set
  to the parsed account UUID (Finding 3, matching `SessionService.recordAudit`'s T28 precedent),
  wrapped in try/catch (D2) — an audit failure is logged and swallowed, never rethrown, so it
  cannot undo the revoke that already committed in the prior call.
- Extracted a shared `parseAccountUuid(String)` static helper and refactored the pre-existing
  `auditReuseDetected` to use it instead of its own inline try/catch — same observable behavior,
  removes a near-duplicate of the new logic's own UUID-parsing block. This is the one
  same-file, same-concern extraction flagged in the Phase 5 plan; no other refactoring was done.

## Mapping to the plan

Matches the Phase 5 plan's proposed signatures exactly, with one small implementation-time
resolution the plan explicitly left open: `auditSessionRevoked` takes `authorizationId` as an
explicit second parameter (used only in the log line on the failure path) rather than being
inlined into `revokeFamilyIfRefreshTokenInvalidated` — kept as a separate method for readability
and to mirror `auditReuseDetected`'s existing shape (a small, single-purpose audit-recording
method).

## Mapping to acceptance criteria

- **AC1** — `revokeFamilyIfRefreshTokenInvalidated` + `revokeForAuthorization` together revoke the
  family with reason `"OAUTH2_REVOKE"` whenever the guard condition is met.
- **AC2** — `revokeForAuthorization`'s `.filter(family -> !family.isRevoked())` makes re-processing
  a no-op; since it returns `false` in that case, `auditSessionRevoked` is never called either
  (satisfies Finding 2's audit-suppression half in the same code path, not a separate check).
- **AC3** — the `refreshToken == null || !refreshToken.isInvalidated()` guard means an
  access-token-only invalidation (refresh token present but not invalidated) or a
  client-credentials-shaped authorization (no refresh token at all) both return early with zero
  family interaction.
- **AC4/AC5** — `trackRefreshTokenIfPresent` is completely unchanged, still runs unconditionally
  before the new call; its own existing same-hash-is-a-no-op guard in `trackRotation` was already
  sufficient (verified at Phase 1/2), so no new special-casing was needed or added.
- **AC6** — `findByToken` was not touched at all in this phase.
- **AC7** — `accountUuid`/`actorUuid` are both set from the same `parseAccountUuid(principalName)`
  call; a non-UUID principal yields both `null`, matching the existing fallback pattern.
- **AC8** — the try/catch in `auditSessionRevoked` guarantees an audit exception cannot propagate
  back through `revokeFamilyIfRefreshTokenInvalidated` into `save(...)`, so it can never trigger a
  rollback of work already done by `tracker.revokeForAuthorization` in its own prior, already-
  committed transaction.

## Deviations from the plan

None forced by reality. The plan's own "implementation-time note" (whether `auditSessionRevoked`
takes the authorization id as a parameter or gets inlined) was resolved as described above —
this was explicitly left open in Phase 5 as a non-blocking implementation-level detail, not a
deviation from anything frozen.

## Verification performed this phase

- `mvn -pl services/auth -am clean compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ReuseDetectingAuthorizationServiceTest,RefreshTokenTrackerTest'`
  — all 21 pre-existing tests (8 + 13) still pass unchanged; no regression from the new code paths
  being merely present but not yet exercised by dedicated new tests (that's Phase 10).

No test code was written in this phase, per the guardrails — Phase 10's job.

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
