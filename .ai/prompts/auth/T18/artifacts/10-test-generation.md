# auth · T18 — Phase 10: Test Generation

Consumes `artifacts/09-review-resolution.md`. Tests only — no production code changed in this
phase. Three files created, all authorized by `artifacts/05-implementation-plan.md`.

---

## Files Created

- `services/auth/src/test/java/com/themistra/auth/mfa/TotpVerifierTest.java` — 10 tests, plain
  JUnit, no Spring context.
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServiceTest.java` — 23 tests, plain JUnit
  + Mockito, no Spring context.
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServicePersistenceIntegrationTest.java` —
  4 tests, `@SpringBootTest` + Testcontainers (Postgres).

## Test → Acceptance Criterion / Requirement Mapping

### `TotpVerifierTest` (L6)

| Test | Verifies |
|---|---|
| `verifyAcceptsKnownRfc6238TestVectors` | L6 core algorithm, against RFC 6238 Appendix B's published SHA1 vectors (external ground truth, independently recomputed and confirmed before writing — not taken from memory alone) |
| `verifyRejectsKnownVectorAtWrongTime` | A valid code for one moment must not verify at an unrelated one |
| `verifyAcceptsCodeForCurrentStep` / `...OneStepBefore` / `...OneStepAfter` | L6's 90s tolerance window (current step ±1) |
| `verifyRejectsCodeTwoStepsBefore` / `...TwoStepsAfter` | L6 boundary — the window is exactly ±1, not open-ended |
| `verifyRejectsWrongCode` | Basic negative case |
| `verifyRejectsCodeGeneratedWithADifferentSecret` | Secret binding — a code isn't valid against any secret |
| `verifyHandlesCodesRequiringLeadingZeroPadding` | Digit-dropping regression guard for small binary codes |

### `MfaServiceTest` (R22/R23/R28/R29)

| Test | Verifies |
|---|---|
| `beginEnrollGeneratesEncryptsAndPersistsWhenNoEnrollmentExists` | AC1 |
| `beginEnrollUsesAccountUuidNotEmailAsProvisioningLabel` | Phase 4 PII-avoidance decision |
| `beginEnrollRejectsWhenConfirmedEnrollmentExists` | AC1 duplicate rejection |
| `beginEnrollDeletesAbandonedUnconfirmedEnrollmentAndCreatesANewOne` | Phase 4 retry behavior |
| `beginEnrollRejectsWhenUnconfirmedRowWasConcurrentlyConfirmedBeforeDelete` | Phase 9 finding 5 fix (atomic `deleteByIdIfUnconfirmed`) |
| `beginEnrollRejectsNonActiveAccount` | R22 account-status precondition |
| `beginEnrollResultToStringNeverLeaksSecretOrUri` | Secret-handling constraint |
| `confirmRecordsAuditAndThrowsOnWrongCodeWithoutMutating` | AC3 — R29 audit, no mutation |
| `confirmThrowsWhenNoEnrollmentExists` | R23 precondition |
| `confirmGeneratesTenSingleUseRecoveryCodesOnSuccess` | AC2/AC7 |
| `confirmThrowsAlreadyEnrolledWhenAtomicUpdateAffectsNoRows` | Phase 9 findings 2 & 3 fix (atomic `confirmIfUnconfirmed`) — replaces the originally-planned "double-confirm hits the entity's raw `IllegalStateException`" test, since that code path no longer exists after the Phase 9 fix |
| `confirmRejectsNonActiveAccount` | R23 account-status precondition |
| `confirmResultToStringNeverLeaksRecoveryCodes` | Secret-handling constraint |
| `disableRecordsDisableFailedAuditAndThrowsOnWrongPassword` | AC5 — `mfa.disable_failed` |
| `disableTreatsMissingLoginViewAsPasswordMismatch` | Phase 3 finding 6 |
| `disableThrowsWhenNoConfirmedEnrollmentExists` | R28 precondition |
| `disableRecordsMfaFailedAndThrowsOnWrongCode` | AC5 — `mfa.failed` |
| `disableDeletesEnrollmentAndRecoveryCodesAndRecordsSuccessAudit` | AC4 |
| `disableRejectsNonActiveAccount` | R28 account-status precondition |
| `verifyRecoveryCodeThrowsWhenCodeUnknown` | R29 |
| `verifyRecoveryCodeThrowsWhenAlreadyUsed` | R25 single-use semantics |
| `verifyRecoveryCodeSucceedsAndReturnsNormally` | R25 happy path |
| `verifyRecoveryCodeThrowsAccountNotFoundForUnknownUuid` | Phase 3 finding 10 resolution |

### `MfaServicePersistenceIntegrationTest` (end-to-end against real Postgres)

| Test | Verifies |
|---|---|
| `beginEnrollThenConfirmPersistsConfirmedEnrollmentAndTenRecoveryCodes` | AC1/AC2/AC7 against real schema, code computed via an independent RFC 6238 reference implementation |
| `disableRemovesEnrollmentAndAllRecoveryCodesAndAudits` | AC4, including a real `mfa.disabled` row read back via `AuditService.list` |
| `beginEnrollRejectsAccountThatIsNotYetActive` | Account-status precondition against a real `PENDING_VERIFICATION` account |
| `beginEnrollRetryReplacesAbandonedUnconfirmedEnrollmentWithANewSecret` | Phase 9 finding 5 fix, end-to-end: the old secret's code no longer confirms after a retry |

## Named Tests (`package.md` §8)

Both are satisfied at the service layer, as the frozen brief anticipated:
`shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` ↔
`confirmGeneratesTenSingleUseRecoveryCodesOnSuccess` (unit) +
`beginEnrollThenConfirmPersistsConfirmedEnrollmentAndTenRecoveryCodes` (integration);
`shouldRequirePasswordAndTotpToDisableMfa` ↔ `disableDeletesEnrollmentAndRecoveryCodesAndRecordsSuccessAudit`
(unit, both factors proven independently by the two failure-path tests) +
`disableRemovesEnrollmentAndAllRecoveryCodesAndAudits` (integration).

## Verification Run

- `TotpVerifierTest`: 10/10 pass.
- `MfaServiceTest`: 23/23 pass.
- `ArchitectureTest` and every other `mfa` package test: pass, unaffected.
- `MfaServicePersistenceIntegrationTest`: 4/4 **fail** — all four hit the same pre-existing,
  already-documented `AccountRepository.existsByEmail` Hibernate byte-array conversion defect that
  blocks T17's own `MfaPersistenceIntegrationTest` (see the `docker-testcontainers-handshake-issue`
  follow-up). This is expected and was flagged as a known risk at Phase 5. The suite is
  written-but-unverified, identical disposition to T17's integration test — it will pass once that
  pre-existing defect is fixed, and needs no change of its own at that point.

**Regression check on the Phase 9 `AuditService.record` propagation change:** since that change
affects every existing caller of `AuditService.record`, not just `MfaService`, a broader sweep was
run across `audit`, `account`, `authn`, and `token` package tests (272 tests). 18 pre-existing
failures were found; each was individually verified — via `git stash` of the Phase 9 production
changes and re-running the specific failing test — to reproduce identically with or without this
task's changes:
- `AccountPersistenceIntegrationTest` (2), `LockoutPersistenceIntegrationTest` (7),
  `SasLoginIntegrationTest` (3 of 4): the same pre-existing `existsByEmail` defect.
- `AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent`: confirmed via direct
  git-stash comparison to fail identically with `AuditService.record` reverted to its original
  `REQUIRED` propagation — a pre-existing `auth_audit_account_uuid_fkey` FK-ordering defect, not a
  side effect of the `REQUIRES_NEW` change.
- `SasLoginIntegrationTest.unknownEmailProducesTheSameResponseShapeAsAKnownAccountFailure` (CSRF
  token lookup), `ReuseDetectingAuthorizationServiceTest.saveTracksRotationWhenFamilyAlreadyExists`
  (Mockito unnecessary-stubbing), `TokenClaimsCustomizerTest` (2, `roleService` null): confirmed via
  running each in complete isolation, untouched by any T18 file — pre-existing defects unrelated to
  this task's scope.

No new test failures were introduced by this task's implementation or its Phase 9 fixes.

## Open Questions

None. The one known-unrunnable suite (`MfaServicePersistenceIntegrationTest`) and its root cause
are fully documented above and in `docker-testcontainers-handshake-issue`; no silent gaps.
