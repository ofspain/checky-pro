<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T29 · Phase 11 — Test Review

Consumes the Phase 10 test manifest and the actual test files. All Docker-independent tests pass (31/31). Findings only — no test or production code changes in this phase.

---

## Executive Summary

The Phase 10 suite is thorough and directly addresses the T29 acceptance criteria, including the two Kimi Phase 8 findings that were folded into the implementation. The unit-level coverage in `ReuseDetectingAuthorizationServiceTest` and `RefreshTokenTrackerTest` is strong, and the integration tests mirror SAS's actual revocation-provider shape. The remaining gaps are minor and concentrated in integration-level assertion depth.

---

## Findings

### Gap 1 — Integration test does not assert the revocation reason

**Why it matters:** `RefreshTokenFamilyIntegrationTest.savingAnInvalidatedRefreshTokenRevokesTheFamily` proves the family is revoked by asserting `checkAndRegisterPresentation` returns `UNKNOWN` for the former current hash. This is a solid end-to-end signal that the family is no longer active, but it does not verify *why* it was revoked. A bug that revoked the family with the wrong reason (e.g., `"REUSE_DETECTED"` instead of `"OAUTH2_REVOKE"`) would still pass this test, even though the reason is part of R39/AC1 and is required for correct audit/analytics attribution.

**Suggested test:** Load the `RefreshTokenFamily` row directly via `EntityManager` after the invalidated save and assert `revokedReason` equals `"OAUTH2_REVOKE"` (and that `revokedAt` is non-null, as a stronger replacement for or addition to the `UNKNOWN` check).

**Evidence:** `token/RefreshTokenFamilyIntegrationTest.java:100-121`.

---

### Gap 2 — No integration-level audit verification

**Why it matters:** AC7 requires exactly one `session.revoked` audit row per family revoked via this path. `ReuseDetectingAuthorizationServiceTest.saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated` verifies the audit call at the decorator level with a mocked `AuditService`, but no test verifies that the audit row actually lands in the database (or outbox) when the real `AuditService` runs. This is the same environment-limited gap that affected T28's integration suite; the test is written but cannot execute without Docker.

**Suggested test:** In the integration test, after the invalidated save, query `auth_audit` (or the outbox table) and assert one row with `event_type = 'session.revoked'`, `outcome = 'SUCCESS'`, and `account_uuid`/`actor_uuid` matching the authorization's principal. This can only be run once Docker/Testcontainers is available.

**Evidence:** `token/ReuseDetectingAuthorizationServiceTest.java:138-154` (unit coverage) and `token/RefreshTokenFamilyIntegrationTest.java:100-121` (integration coverage gap).

---

### Gap 3 — No test that `revokeForAuthorization` does not archive the superseded hash

**Why it matters:** `revokeForAuthorization` is a new tracker method that sits next to `trackRotation`, which *does* archive the old hash. A future refactor could accidentally reuse rotation logic inside the revoke method, causing an archive entry to be created for a token that was not superseded. The existing tests verify the positive outcomes (revoked, reason, save called) but do not assert that `RefreshTokenArchiveRepository.save(...)` is never called.

**Suggested test:** Add `revokeForAuthorizationDoesNotArchiveOldHash` to `RefreshTokenTrackerTest` that stubs an active family, calls `revokeForAuthorization`, and verifies `archiveRepository.save(...)` is never invoked.

**Evidence:** `token/RefreshTokenTrackerTest.java:228-241`.

---

### Gap 4 — No test that a swallowed revoke failure can be retried successfully

**Why it matters:** `saveSwallowsRevokeFailureWithoutPropagatingOrAuditing` proves that a transient `tracker.revokeForAuthorization(...)` failure does not propagate and does not audit. It does not prove that a subsequent `save(...)` (retry) can succeed and then audit. A subtle bug where the swallowing logic leaves the service in a state that prevents future revokes would not be caught.

**Suggested test:** Add `saveRetriesRevokeAfterTransientFailureAndThenAudits` to `ReuseDetectingAuthorizationServiceTest`: first save throws on `revokeForAuthorization`, second save returns `true`, and the second save emits the audit row.

**Evidence:** `token/ReuseDetectingAuthorizationServiceTest.java:206-214`.

---

## Non-Issues Confirmed

- **Mutual exclusivity of revoke and tracking:** `saveNeverTracksIssuanceOrRotationWhenRefreshTokenIsInvalidated` directly guards against the Phase 8 Finding 1 regression.
- **Failure swallowing:** both `saveSwallowsAuditFailureWithoutPropagating` and `saveSwallowsRevokeFailureWithoutPropagatingOrAuditing` cover the Phase 8 Finding 2 regression.
- **No-op audit suppression:** `saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp` verifies AC2.
- **Non-UUID principal fallback:** `saveAuditsWithNullAccountWhenPrincipalIsNotAUuid` covers the Phase 3 Finding 6 boundary.
- **Access-token-only invalidation:** `saveDoesNotRevokeWhenOnlyAccessTokenInvalidated` and the strengthened `saveSkipsTrackingWhenAuthorizationHasNoRefreshToken` cover AC3.
- **Existing regression suite:** all 8 pre-existing `ReuseDetectingAuthorizationServiceTest` tests and all 13 pre-existing `RefreshTokenTrackerTest` tests still pass.

---

## Traceability Summary

| AC | Covered By | Gap |
|---|---|---|
| AC1 — revoke active family with reason `OAUTH2_REVOKE` | `RefreshTokenTrackerTest.revokeForAuthorizationRevokesExistingUnrevokedFamilyAndReturnsTrue`, `ReuseDetectingAuthorizationServiceTest.saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated`, `RefreshTokenFamilyIntegrationTest.savingAnInvalidatedRefreshTokenRevokesTheFamily` | Gap 1 (reason not asserted in integration) |
| AC2 — idempotent no-op, no duplicate audit | `RefreshTokenTrackerTest.revokeForAuthorizationIsANoOpOnAlreadyRevokedFamilyAndReturnsFalse`, `ReuseDetectingAuthorizationServiceTest.saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp` | None |
| AC3 — access-token-only / no refresh token does nothing | `ReuseDetectingAuthorizationServiceTest.saveDoesNotRevokeWhenOnlyAccessTokenInvalidated`, `saveSkipsTrackingWhenAuthorizationHasNoRefreshToken` | None |
| AC4/AC5 — ordinary rotation/issuance unaffected | Existing pre-T29 tests + `saveDoesNotRevokeWhenOnlyAccessTokenInvalidated` | None |
| AC6 — `findByToken` reuse detection untouched | Existing pre-T29 reuse tests still pass | None |
| AC7 — exactly one audit row | `ReuseDetectingAuthorizationServiceTest.saveRevokesFamilyAndAuditsWhenRefreshTokenIsInvalidated`, `saveDoesNotAuditWhenRevokeForAuthorizationReportsNoOp` | Gap 2 (no DB-level verification) |
| AC8 — audit failure does not undo revoke | `ReuseDetectingAuthorizationServiceTest.saveSwallowsAuditFailureWithoutPropagating` | None |
| D1/D2 — safe failure direction | `saveSwallowsRevokeFailureWithoutPropagatingOrAuditing` | Gap 4 (no retry-success verification) |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval.
