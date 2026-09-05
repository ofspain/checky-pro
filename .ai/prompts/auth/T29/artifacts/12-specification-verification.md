<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T29 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11) against `requirements.md`, `design.md`,
`tasks.md`, and the frozen brief for **T29 only**. `spec/auth-service/` confirmed byte-for-byte
unchanged since T29 began (`git diff dbbcadb...HEAD --stat -- spec/auth-service/` — empty,
`dbbcadb` being T28's final commit, "t28 done").

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R39** — `/oauth2/revoke` with a refresh token also revokes the family | Yes | `RefreshTokenTracker.java:91-101` (`revokeForAuthorization`); `ReuseDetectingAuthorizationService.java:42-48` (`save`), `:109-119` (`isRefreshTokenInvalidated`), `:121-137` (`revokeFamilyForInvalidatedRefreshToken`) | `RefreshTokenTrackerTest` (4 tests, executed, green); `ReuseDetectingAuthorizationServiceTest` (9 tests, executed, green); `RefreshTokenFamilyIntegrationTest` (2 tests, written, Docker-blocked) | No | No |
| **R43** *(referenced)* — every revoke audited | Yes | `ReuseDetectingAuthorizationService.java:138-149` (`auditSessionRevoked`, `session.revoked`) | `ReuseDetectingAuthorizationServiceTest.saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated`/`saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp` (executed, green); integration test's audit-row-count assertion (written, Docker-blocked) | No | No |
| **R46** *(referenced)* | N/A | No HTTP response surface introduced by this task | N/A | No | No |

## Traceability Matrix — Locked Decisions

**None constrain this task** (confirmed at Phase 0, 1, 2, and again here) — the second task in this
recent stretch (after T28) with no LOCKED decision at all.

## This Task's Own Design Decisions — honored?

| Decision | Honored? | Evidence |
|---|---|---|
| **D1** — SAS invalidation and family revocation are non-atomic; accepted, safe-side residual | Yes, undisturbed | `save(...)`'s order (`delegate.save` → branch) unchanged since Phase 6; no atomicity added |
| **D2** — audit failure inside `revokeFamilyForInvalidatedRefreshToken` must not undo the revoke | Yes | `ReuseDetectingAuthorizationService.java:143-148` (try/catch around `auditService.record`); `saveSwallowsAuditFailureWithoutPropagating` (executed, green) |
| **D3** — integration test scoped to a direct `OAuth2AuthorizationService.save(...)` call, not a full HTTP round-trip | Yes | `RefreshTokenFamilyIntegrationTest.savingAnInvalidatedRefreshTokenRevokesTheFamily`/`...DoesNotCreateAPhantomFamily` |
| *(Phase 9 fix)* Revoke-shaped saves must never also run `trackIssuance`/`trackRotation` for the same authorization (Kimi Finding 1) | Yes | `ReuseDetectingAuthorizationService.java:42-48` (mutually-exclusive branch); `saveNeverTracksIssuanceOrRotationWhenRefreshTokenIsInvalidated` (executed, green) + `savingAnInvalidatedRefreshTokenForAnUntrackedAuthorizationDoesNotCreateAPhantomFamily` (written, Docker-blocked, full-stack proof) |
| *(Phase 9 fix)* `tracker.revokeForAuthorization(...)`'s own exceptions must not surface to the `/oauth2/revoke` caller (Kimi Finding 2) | Yes | `ReuseDetectingAuthorizationService.java:122-135` (try/catch); `saveSwallowsRevokeFailureWithoutPropagatingOrAuditing`, `saveRetriesRevokeAfterTransientFailureAndThenAudits` (both executed, green) |
| **AC7 amendment** (Phase 9, Kimi Finding 3) — concurrent double-revoke may produce two audit rows, accepted as a documented residual | Yes, undisturbed | No locking added to `revokeForAuthorization`; matches T28's own unlocked precedent, femi's explicit gate decision |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | `revokeForAuthorizationRevokesExistingUnrevokedFamilyAndReturnsTrue`, `saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated`, `savingAnInvalidatedRefreshTokenRevokesTheFamily` (now asserts `revokedReason == "OAUTH2_REVOKE"` directly, Phase 11 Gap 1) |
| AC2 | **Met (amended, Phase 9)** — exactly one audit row under normal operation; at least one under a genuine concurrent race, documented not defended against | `revokeForAuthorizationIsANoOpOnAlreadyRevokedFamilyAndReturnsFalse`, `saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp` |
| AC3 | **Met** | `saveDoesNotRevokeWhenOnlyAccessTokenInvalidated`, `saveSkipsTrackingWhenAuthorizationHasNoRefreshToken` (strengthened, Phase 10) |
| AC4/AC5 | **Met** | All pre-existing `trackIssuance`/`trackRotation` tests (T07-era) still pass unmodified; `saveNeverTracksIssuanceOrRotationWhenRefreshTokenIsInvalidated` proves mutual exclusivity |
| AC6 | **Met** | `findByToken` untouched; its own 8-test suite still green |
| AC7 | **Met** | `saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated` (accountUuid=actorUuid=parsed UUID), `saveAuditsWithNullAccountWhenPrincipalIsNotAUuid` (non-UUID fallback); integration audit-row-count assertion written (Docker-blocked) |
| AC8 | **Met** | `saveSwallowsAuditFailureWithoutPropagating` |

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. Every file the frozen brief authorized (exactly two:
`RefreshTokenTracker.java`, `ReuseDetectingAuthorizationService.java`) was modified exactly as
planned, and — unusually for this task relative to T25-T28 — a genuine, high-confidence functional
bug (Kimi Phase 8 Finding 1: a phantom family plus a misleading audit event for any
never-before-tracked authorization reaching `/oauth2/revoke`) was caught and fixed before merge,
with a dedicated unit test and a dedicated full-stack integration test both proving the fix
specifically, not just the feature's happy path. A second real gap (Finding 2: an unguarded
exception path that would have surfaced as a caller-visible 500 for a SAS call that had already
succeeded) was fixed the same way. The one remaining known gap (Finding 3, concurrent double-audit)
was an explicit, informed trade-off at the human gate, not an oversight. The Docker-unavailability
constraint that has applied to every task since T25 continues to apply here: `RefreshTokenFamilyIntegrationTest`'s
4 tests (2 pre-existing, 2 new) compile clean but have not executed this session.

**(2) Does it satisfy every acceptance criterion?** Yes, including the one criterion (AC7) that was
knowingly and explicitly amended mid-review rather than either silently violated or left
unaddressed.

**(3) Does it violate any LOCKED decision?** No — none apply to this task, the second in this
stretch (after T28) with none at all.

**(4) Remaining risks:**
- **Unexecuted integration suite, now spanning FIVE consecutive tasks (T25-T29).** `RefreshTokenFamilyIntegrationTest`'s 4 tests, including both of T29's new ones (one proving the actual revoke end-to-end against the real JDBC-backed store with reason/audit-row assertions, one proving Kimi's Finding 1 fix at the full-stack level) have never executed. Whoever picks this up should run the full accumulated suite in dependency order (T25's `SasLoginIntegrationTest`/`ApiKeyExchangeIntegrationTest` first).
- **The concurrent-double-audit residual (AC7 amendment)** is a named, accepted, low-probability gap — not a defect, but worth remembering if `session.revoked` audit-row counts are ever used for anything more precise than approximate security monitoring.
- **D1's non-atomicity** (SAS invalidation and family revocation as two separate steps) remains an accepted residual, consistent with T28's own precedent for the analogous risk.
- Contract files (`contracts/api/auth.yaml`, `token-claims.md`) still don't exist — same gap noted at every task since T25; not blocking for a task with no HTTP surface of its own.

---

## Verdict

**PASS** — every requirement, design decision, and acceptance criterion has direct code evidence
and either an executed passing test or a written-but-Docker-blocked test with a clear account of
why it hasn't run; the review process caught and fixed a genuine functional bug before merge rather
than merely rubber-stamping the initial implementation, and the one residual trade-off (AC7's
concurrency amendment) was made explicitly and on the record.

---

**Phase 12 complete — PASS.** Proceed to Phase 13 (PR / Commit Preparation).
