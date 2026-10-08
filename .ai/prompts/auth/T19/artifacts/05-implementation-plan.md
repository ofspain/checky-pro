# auth · T19 — Phase 5: Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN). Plan only — no code in this artifact.

---

## A correction caught during this phase, before any code was written

`common/ProblemTypes.java` was read in full for the first time this phase (Phase 2/4 proposed a new
`MFA_PASSWORD_MISMATCH` constant without checking whether one already existed). **It already does**:
`CURRENT_PASSWORD_MISMATCH`, created for `/accounts/me/password`'s own wrong-current-password case,
with a Javadoc explicitly noting it is "not enumeration-sensitive (the caller is already authenticated
as this exact account)" — the exact same semantic `MfaCurrentPasswordMismatchException` carries, in
both `disable` and the new `regenerateRecoveryCodes`. **Corrected plan: reuse
`ProblemTypes.CURRENT_PASSWORD_MISMATCH`; do not add a new MFA-specific one.** Only three new
constants are needed: `MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED`, `MFA_INVALID_CODE` — each domain-
specific in the same way `API_KEY_NOT_FOUND`/`SESSION_NOT_FOUND` already are (the file's own
established convention: a generic type for truly generic cases, a named one when the 404/409/401
carries its own distinct meaning worth a reader finding by name).

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaController.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/BeginEnrollResponse.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/TotpCodeRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/PasswordAndTotpRequest.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/dto/RecoveryCodesResponse.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaControllerTest.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaExceptionHandlerTest.java`
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaServiceTest.java` (extended — add
  `regenerateRecoveryCodes` cases to the existing T18 test class, not a new file)
- `services/auth/src/test/java/com/themistra/auth/mfa/MfaControllerIntegrationTest.java`

## Files to Modify

- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` — add
  `regenerateRecoveryCodes`/`RegenerateRecoveryCodesResult`.
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` — add
  `MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED`, `MFA_INVALID_CODE` (3, not 4 — see correction above).
- `contracts/api/auth.yaml` — four new paths, success responses only (Phase 4's Finding 5
  correction).
- `spec/auth-service/package.md` — fix 3 mismapped §8 lines, add 1 new line for R49.

## Public Methods (signatures)

**`MfaController`** (`@RestController`, `@RequestMapping("/accounts/me/mfa")`):
```java
@PostMapping("/totp")
public ResponseEntity<BeginEnrollResponse> beginEnroll(Authentication authentication)

@PostMapping("/totp/confirm")
public RecoveryCodesResponse confirm(Authentication authentication, @Valid @RequestBody TotpCodeRequest request)

@DeleteMapping("/totp")
public ResponseEntity<Void> disable(Authentication authentication, @Valid @RequestBody PasswordAndTotpRequest request)

@PostMapping("/recovery-codes")
public RecoveryCodesResponse regenerateRecoveryCodes(Authentication authentication, @Valid @RequestBody PasswordAndTotpRequest request)
```
Each resolves `UUID.fromString(authentication.getName())`, calls the matching `MfaService` method,
and maps the result — never the raw `BeginEnrollResult`/`ConfirmResult`/`RegenerateRecoveryCodesResult`
— into its own response DTO. `beginEnroll` returns `201` (no `Location`, matching
`ApiKeyController.create`'s own precedent); `confirm`/`regenerateRecoveryCodes` return `200`
(plain return type, Spring's default); `disable` returns `204` via `ResponseEntity.noContent()`.

**`MfaExceptionHandler`** (`@RestControllerAdvice`, `@Order(Ordered.HIGHEST_PRECEDENCE)`):
```java
@ExceptionHandler(MfaAlreadyEnrolledException.class)
ProblemDetail onAlreadyEnrolled(MfaAlreadyEnrolledException e)   // 409, MFA_ALREADY_ENROLLED

@ExceptionHandler(MfaNotEnrolledException.class)
ProblemDetail onNotEnrolled(MfaNotEnrolledException e)           // 404, MFA_NOT_ENROLLED

@ExceptionHandler(InvalidTotpCodeException.class)
ProblemDetail onInvalidCode(InvalidTotpCodeException e)          // 401, MFA_INVALID_CODE
                                                                   // Javadoc: 401 here means
                                                                   // "the second credential was
                                                                   // rejected," not "not logged in"
                                                                   // (Phase 3 Finding 2) — the
                                                                   // caller is already authenticated.

@ExceptionHandler(MfaCurrentPasswordMismatchException.class)
ProblemDetail onPasswordMismatch(MfaCurrentPasswordMismatchException e)  // 403, reuses the EXISTING
                                                                           // ProblemTypes.CURRENT_PASSWORD_MISMATCH
```
`InvalidRecoveryCodeException` and `MfaEncryptionException` have no handler here (Phase 3 Findings
6/7, confirmed again) — they fall through to the existing global advice.

**`MfaService.regenerateRecoveryCodes`** — exact body already fixed at Phase 2, unchanged here.

**DTOs** (all `record`s):
```java
public record BeginEnrollResponse(String provisioningUri) {}
public record TotpCodeRequest(@NotBlank @Size(max = 20) String code) {}
public record PasswordAndTotpRequest(@NotBlank @Size(max = 128) String currentPassword,
                                      @NotBlank @Size(max = 20) String code) {}
public record RecoveryCodesResponse(List<String> recoveryCodes) {}
```

## Test Plan

**`MfaControllerTest`** (mirrors `ApiKeyControllerTest`'s own style exactly — direct construction,
mocked `MfaService`, no Spring dispatcher):
- Each of the four methods calls the correct `MfaService` method with the `UUID` resolved from
  `Authentication`, and maps the result into the correct response DTO with the correct status.
- `disable` and `regenerateRecoveryCodes` pass both `currentPassword` and `code` from the request
  body through, in the right argument order (the two fields share a name across both record types —
  an easy place to transpose by mistake, worth its own explicit assertion).
- An exception thrown by the mocked service propagates uncaught (not swallowed or wrapped) — mirrors
  `ApiKeyControllerTest`'s own `assertThatThrownBy` pattern.

**`MfaExceptionHandlerTest`** (mirrors a sibling `ApiKeyExceptionHandlerTest`, confirmed to exist):
- Each of the four mapped exceptions → the exact status code and `ProblemTypes` constant from the
  table above.
- `CURRENT_PASSWORD_MISMATCH` is asserted as the *same* `URI` instance/value `/accounts/me/password`'s
  own handler already uses — a direct, named proof that T19 reused it rather than silently defining
  a near-duplicate.

**`MfaServiceTest`** (extend the existing T18 file, not a new one) — add `regenerateRecoveryCodes`
cases mirroring `disable`'s own existing test shape exactly: happy path (old codes gone, 10 new
persisted, audit recorded), wrong password (no mutation, `mfa.recovery_codes_regenerate_failed`),
wrong code (no mutation, generic `mfa.failed`), no confirmed enrollment
(`MfaNotEnrolledException`), non-`ACTIVE` account rejected.

**`MfaControllerIntegrationTest`** (new — T19 is the first task where a real HTTP round trip through
all four endpoints is possible; T18 deferred persistence-integration testing, this task doesn't
defer the controller-level one, since that's this task's own literal job):
- Full real-HTTP happy path per endpoint: `201`/`200`/`204`/`200` with real bodies, against a real
  authenticated request (mirrors `ApiKeyExchangeIntegrationTest`'s own real-request pattern).
- One real failure path per endpoint (wrong code, wrong password, already-enrolled, not-enrolled)
  asserting the real HTTP status and `ProblemDetail` body, not just that *an* exception was thrown.
- A request with a blank `code`/`currentPassword` returns `400` via the framework's own validation,
  never reaching `MfaService` (confirms Phase 2/3's validation-bounds decision is wired correctly).
- Confirms none of the four paths are reachable unauthenticated (`401`/`403` without a bearer token,
  depending on the resource-server chain's own existing behavior for a missing token).

**Named tests** (corrected mapping, `package.md` §8): `shouldReturnTotpProvisioningUriOnEnrollmentBegin`
→ R22, `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` → R23,
`shouldRequirePasswordAndTotpToDisableMfa` → R28, `shouldRegenerateRecoveryCodesWithPasswordAndTotp`
(new) → R49 — satisfied at the controller-integration level this time, since this task's own literal
job is the HTTP surface these names describe.

**ArchUnit**: confirm `shouldPreventCrossModuleEntityImports` and `shouldEnforcePublicEndpointAllowlist`
both still pass with `MfaController` added (Phase 3 Finding 9) — no new test needed, the existing
canaries already cover any new class in scope.

## Execution Order

1. Three new `ProblemTypes` constants — no dependencies.
2. Four DTOs — no dependencies beyond `jakarta.validation`.
3. `MfaService.regenerateRecoveryCodes` + `RegenerateRecoveryCodesResult` — depends on nothing new,
   only existing T16–T18 collaborators already injected into `MfaService`.
4. `MfaServiceTest` additions — validate step 3 before building the controller on top of it.
5. `MfaController.java` — depends on steps 2–3.
6. `MfaControllerTest` — validate step 5 with mocks.
7. `MfaExceptionHandler.java` — depends on step 1 and the existing exception classes.
8. `MfaExceptionHandlerTest` — validate step 7.
9. `contracts/api/auth.yaml` + `spec/auth-service/package.md` — update in the same pass, since both
   describe the now-real endpoints.
10. `MfaControllerIntegrationTest` — validates the whole chain end to end, depends on everything
    above.
11. Run `ArchitectureTest` to confirm the two canaries still pass.

No schema/migration step — no schema change (frozen brief confirms this).

## Open Questions

None. All Phase 3 findings were resolved at Phase 4. One real correction surfaced while planning
(the `ProblemTypes` reuse above), not a new open question — it's fixed in this plan, not deferred.
