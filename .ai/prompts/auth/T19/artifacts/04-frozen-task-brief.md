STATUS: FROZEN

# auth · T19 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 10 findings verified directly against actual source before disposition. 9 are **ACCEPTED**. 1
(Finding 5) is **REJECTED AS WRITTEN**, corrected below — its premise does not hold.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `BeginEnrollResponse` must drop the raw secret | — | **ACCEPTED** | Already the brief's own design; Kimi reconfirms it independently. |
| 2 | `InvalidTotpCodeException` → 401 on an authenticated endpoint | Low | **ACCEPTED** | Matches the brief's own `ApiKeyExchangeRejectedException` precedent for a rejected credential. Document the rationale in `MfaExceptionHandler`'s own Javadoc so a future reader doesn't misread 401 as "not logged in." |
| 3 | DTO validation (`@NotBlank @Size`) | — | **ACCEPTED** | `code` max 20, `currentPassword` max 128 (matches R8's own 128 ceiling). No minimum on `currentPassword` here — the password encoder is the authority. |
| 4 | Keep `RegenerateRecoveryCodesResult` separate from `ConfirmResult` | — | **ACCEPTED** | Confirmed sound: identical shape today, semantically distinct operations: a shared type would invite exactly the kind of confusion T18's own per-operation record naming (`BeginEnrollResult`, `ConfirmResult`) was already designed to avoid. |
| 5 | `auth.yaml` must document every error response, using a shared `ValidationError`/problem schema | Medium | **REJECTED AS WRITTEN** | Verified directly: all 30 existing endpoints in `auth.yaml` document only their success response; zero document any 4xx/5xx; no `ValidationError` or problem schema is defined anywhere in the file. Kimi's own finding cites a schema that does not exist and a convention the file has never used. Adopting it as written would make T19 the first and only endpoint set with full error documentation, inconsistent with every other path, and would require inventing a new schema outside this task's own scope. **Corrected resolution: document only the real success response per endpoint** (`201`/`200`/`204`), exactly matching the brief's own original "mirror `/accounts/me/password`'s style" instruction and the file's own 100%-consistent existing convention. If the author wants a repo-wide error-documentation convention, that is a separate, larger decision — not this task's to make unilaterally. |
| 6 | `InvalidRecoveryCodeException` correctly left unmapped | — | **ACCEPTED** | Matches the brief exactly. |
| 7 | `MfaEncryptionException` falls through to the global handler | — | **ACCEPTED** | Matches the brief exactly. |
| 8 | `package.md` §8 mappings must be corrected | — | **ACCEPTED** | Matches Phase 1/2's own already-proposed fix. |
| 9 | Module-boundary and public-endpoint checks must stay green | — | **ACCEPTED** | Verified directly: `ArchitectureTest.shouldPreventCrossModuleEntityImports` (`:105`) and `shouldEnforcePublicEndpointAllowlist` (`:283`) are real, existing ArchRule fields in `services/auth`'s own test suite. `MfaController` must not import another module's entity and must not call `permitAll()`. |
| 10 | No production files beyond the brief's list, no refactor of `confirm`/`disable`/`beginEnroll` | — | **ACCEPTED** | `regenerateRecoveryCodes` is new and self-contained; existing methods are untouched. |

## Task, Purpose, Scope, Business Rules, Locked Decisions

Unchanged from Phase 2, with Finding 5's correction folded into the `auth.yaml` scope item: document
only each endpoint's real success response, matching the file's own existing, exceptionless
convention. No error schema is introduced.

## Dependencies

Unchanged from Phase 2.

## Files to Create

Unchanged from Phase 2: `MfaController.java`, `MfaExceptionHandler.java`,
`dto/BeginEnrollResponse.java`, `dto/TotpCodeRequest.java`, `dto/PasswordAndTotpRequest.java`,
`dto/RecoveryCodesResponse.java`.

## Files to Modify

Unchanged from Phase 2: `MfaService.java`, `common/ProblemTypes.java`, `contracts/api/auth.yaml`
(success responses only, per Finding 5's correction), `spec/auth-service/package.md`.

## Files NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

AC1–AC10, unchanged from Phase 1, with AC10 ("contract documents all four endpoints") now read as
"documents all four endpoints' real success responses," matching the file's own existing convention
rather than inventing error-response documentation this task was never asked to add.

## Required Tests

Unchanged from Phase 2, with Finding 2's addition: `MfaExceptionHandler`'s own Javadoc documents why
`InvalidTotpCodeException` maps to 401 despite the endpoint requiring authentication, so a future
reader doesn't misread the status code.

## Constraints

Unchanged from Phase 2, plus Finding 3's exact validation bounds (`code`: `@NotBlank @Size(max = 20)`;
`currentPassword`: `@NotBlank @Size(max = 128)`, no minimum).

## Open Questions

No blockers. Finding 5's rejection is disclosed above, not silently absorbed — if the author wants
full error-response documentation across `auth.yaml`, that is a separate, repo-wide decision for a
dedicated task, not something T19 introduces unilaterally for only its own four endpoints.
