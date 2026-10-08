# auth · T19 — Phase 10: Test Generation

Consumes `artifacts/09-review-resolution.md`. Unlike some earlier tasks in this pipeline, T19's
test writing was front-loaded into Phase 6 (Implementation) and Phase 7 (Self-Review) rather than
deferred to this phase — each production file was written alongside its own tests, and two more
real coverage gaps were closed during self-review before Kimi's review ever ran. This phase's job
is the one still outstanding: a complete, consolidated test → requirement/AC/named-test
traceability map across all six touched test files, plus a final verification run. No new test
files are created here; no production code changes.

---

## Test → Requirement / Acceptance Criterion Mapping

### `MfaServiceTest` — 7 new tests (R49), 29 pre-existing (R22/R23/R28/R29, untouched)

| Test | Verifies |
|---|---|
| `regenerateRecoveryCodesRecordsRegenerateFailedAuditAndThrowsOnWrongPassword` | R49 wrong-password path, `mfa.recovery_codes_regenerate_failed` audit, no mutation |
| `regenerateRecoveryCodesTreatsMissingLoginViewAsPasswordMismatch` | Phase 3 Finding 6's precedent (disable), mirrored for regenerate |
| `regenerateRecoveryCodesThrowsWhenNoConfirmedEnrollmentExists` | R49 precondition |
| `regenerateRecoveryCodesRecordsMfaFailedAndThrowsOnWrongCodeWithoutMutating` | R49 wrong-code path, generic `mfa.failed` (R29), no mutation |
| `regenerateRecoveryCodesDeletesOldCodesGeneratesTenNewOnesAndRecordsSuccessAudit` | R49 happy path, `mfa.recovery_codes_regenerated` audit |
| `regenerateRecoveryCodesRejectsNonActiveAccount` | Account-status precondition, mirrors every other `MfaService` public method |
| `regenerateRecoveryCodesResultToStringNeverLeaksRecoveryCodes` | Secret-handling constraint, mirrors `ConfirmResult`'s own guard |

### `MfaControllerTest` — 13 new tests (all four endpoints)

| Test | Verifies |
|---|---|
| `beginEnrollReturns201WithProvisioningUriOnly` | R22, AC1/AC5 — 201, only the URI, never the raw secret |
| `beginEnrollResponseHasNoLocationHeader` | `ApiKeyController.create` precedent |
| `beginEnrollDerivesCallerFromAuthentication` | Caller identity source |
| `beginEnrollPropagatesAlreadyEnrolledUncaught` | Exception propagation (Finding 5 judgment: one example per endpoint is sufficient — see Phase 7) |
| `confirmReturnsRecoveryCodesResponse` | R23 |
| `confirmPassesCodeFromRequestBody` | Argument wiring |
| `confirmPropagatesInvalidCodeUncaught` | Exception propagation |
| `disableReturns204` | R28, AC3 |
| `disablePassesPasswordAndCodeInTheRightOrder` | Phase 5 plan's named risk — the shared-field-name transposition hazard |
| `disablePropagatesPasswordMismatchUncaught` | Exception propagation |
| `regenerateRecoveryCodesReturnsRecoveryCodesResponse` | R49, AC4 |
| `regenerateRecoveryCodesPassesPasswordAndCodeInTheRightOrder` | Same transposition hazard as `disable` |
| `regenerateRecoveryCodesPropagatesNotEnrolledUncaught` | Exception propagation |

### `MfaExceptionHandlerTest` — 6 new tests (every mapped exception)

| Test | Verifies |
|---|---|
| `onAlreadyEnrolledReturnsUniform409` | R22, `MFA_ALREADY_ENROLLED` |
| `onNotEnrolledReturnsUniform404` | R28/R49, `MFA_NOT_ENROLLED` |
| `onInvalidCodeReturnsUniform401` | R23/R28/R29/R49, `MFA_INVALID_CODE`, Phase 3 Finding 2's documented rationale |
| `onPasswordMismatchReturnsUniform400` | R28/R49, the Phase 5/9 400-not-403 correction |
| `onPasswordMismatchReusesTheExactSameProblemTypeAsAccountsMePassword` | Direct, named proof the type is reused, not duplicated |
| `everyMappedResponseIsIdenticalRegardlessOfConstructionSite` | No construction-site-dependent variance, mirrors `ApiKeyExceptionHandlerTest`'s own precedent |

### `MfaControllerIntegrationTest` — 14 new tests, real HTTP against real Postgres+Kafka

| Test | Verifies |
|---|---|
| `shouldReturnTotpProvisioningUriOnEnrollmentBegin` | **Named test**, R22 |
| `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` | **Named test**, R23 |
| `shouldRequirePasswordAndTotpToDisableMfa` | **Named test**, R28 |
| `shouldRegenerateRecoveryCodesWithPasswordAndTotp` | **Named test (new)**, R49 |
| `beginEnrollRejectsWhenAlreadyEnrolled` | R22 failure path, real 409 body |
| `confirmRejectsWrongCode` | R23/R29 failure path, real 401 body |
| `disableRejectsWrongPassword` | R28 failure path, real 400 body (post-correction) |
| `disableRejectsWhenNotEnrolled` | R28 precondition, real 404 body |
| `regenerateRejectsWhenNotEnrolled` | R49 precondition, real 404 body |
| `regenerateRejectsWrongCode` | R49/R29 failure path, real 401 body |
| `confirmRejectsBlankCodeWithValidationError` | DTO validation bound, never reaches `MfaService` |
| `disableRejectsBlankPasswordWithValidationError` | Same, `currentPassword` field |
| `allFourEndpointsReject401WithoutABearerToken` | AC7 — none reachable unauthenticated |
| `noneOfTheFourEndpointsArePublic` | AC7, direct `PublicEndpoints.METHOD_SCOPED` assertion, Kimi Phase 8 Finding 8 |

### `MfaServicePersistenceIntegrationTest` — 2 new tests (10 total in file)

| Test | Verifies |
|---|---|
| `regenerateRecoveryCodesInvalidatesOldCodesAndPersistsTenGenuinelyUsableNewOnes` | R49's literal "invalidate every existing recovery code" against a real DB, not just the returned list |
| `concurrentRegenerateRecoveryCodesCallsResultInExactlyOneSuccessAndTenRecoveryCodes` | Phase 7 Finding 4 / Kimi Phase 8 Finding 5 — the real, empirically-characterized concurrent-regenerate behavior |

### `AuthOpenApiContractTest` — extended, not new tests (10 total, unchanged count)

Each of the 10 existing test methods now also exercises the four new MFA routes/schemas, since
`MfaController` was added to `CONTROLLERS` and the four routes to both expected-schema tables —
closing the gap Phase 6 found (not anticipated by any earlier phase's file list).

## Named Tests (`package.md` §8) — final confirmation

All four are present, at the controller-integration level, exactly as Phase 5's plan anticipated
("satisfied at the controller-integration level this time, since this task's own literal job is
the HTTP surface these names describe"):

- `shouldReturnTotpProvisioningUriOnEnrollmentBegin` → R22 ✓
- `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` → R23 ✓
- `shouldRequirePasswordAndTotpToDisableMfa` → R28 ✓
- `shouldRegenerateRecoveryCodesWithPasswordAndTotp` → R49 ✓ (new, added this task)

## Final Verification Run

```
mvn -pl services/auth test -Dtest=MfaServiceTest,MfaControllerTest,MfaExceptionHandlerTest,\
MfaControllerIntegrationTest,MfaServicePersistenceIntegrationTest,AuthOpenApiContractTest
```

| Class | Tests | Result |
|---|---|---|
| `MfaServiceTest` | 36 | ✅ all pass |
| `MfaControllerTest` | 13 | ✅ all pass |
| `MfaExceptionHandlerTest` | 6 | ✅ all pass |
| `MfaControllerIntegrationTest` | 14 | ✅ all pass |
| `MfaServicePersistenceIntegrationTest` | 10 | ✅ all pass |
| `AuthOpenApiContractTest` | 10 | ✅ all pass |
| **Total, this task's own tests** | **89** | **✅ all pass** |

ArchUnit's 11 rules (standalone-runner, Surefire's known bug bypassed) remain independently
confirmed passing as of Phase 9 — no Java production code changed since, so not re-run this phase.

## Open Questions

None.
