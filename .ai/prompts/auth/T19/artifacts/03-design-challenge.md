<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T19 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T19 — MFA self-service HTTP surface + recovery-code regeneration |
| **Spec section** | R22, R23, R28, R29, R49 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` + existing ApiKey/MFA code + `auth.yaml` |
| **Produces** | `artifacts/03-design-challenge.md` |

Phase 3 adversarial design review of the auth T19 brief before implementation.

---

## Finding 1 · `BeginEnrollResponse` must deliberately drop the raw secret

**Challenge:** `MfaService.beginEnroll` returns `BeginEnrollResult(byte[] secret, String provisioningUri)`. The raw secret must never leave the service boundary except inside the provisioning URI. If the controller returns the service result directly, the raw secret is serialized to the HTTP response.

**Resolution:** Introduce `BeginEnrollResponse(String provisioningUri)` and have the controller map `result.provisioningUri()` into it. The `secret` field is consumed only by the service internally (or by a future QR-code endpoint, out of scope here). Document this explicitly in the controller Javadoc.

---

## Finding 2 · `InvalidTotpCodeException` maps to 401 on an authenticated endpoint

**Challenge:** `POST /totp/confirm`, `DELETE /totp`, and `POST /recovery-codes` all require an authenticated caller (bearer token). A wrong TOTP code is therefore a failed *secondary* credential, not a missing authentication. Mapping it to `401 Unauthorized` is unconventional for an already-authenticated resource.

**Evidence:**
- The brief justifies this by mirroring `ApiKeyExceptionHandler.onExchangeRejected`, which maps an invalid API key to 401.
- `ApiKeyController.exchange` is an unauthenticated endpoint, so 401 is natural there.

**Resolution:** Accept the brief's chosen status code because it creates a consistent "presented credential rejected" semantic across the service, but document the rationale in `MfaExceptionHandler` so future maintainers do not misread it as "not logged in." The response body will still be RFC 9457 `application/problem+json` with type `MFA_INVALID_CODE`.

---

## Finding 3 · Request DTO validation should mirror `CreateApiKeyRequest`

**Challenge:** `TotpCodeRequest` and `PasswordAndTotpRequest` need validation so blank fields 400 via Spring's `MethodArgumentNotValidException` handler rather than falling through to service-level exceptions.

**Resolution:**
- `code`: `@NotBlank @Size(max = 20)` (TOTP codes are short; the upper bound catches garbage without being restrictive).
- `currentPassword`: `@NotBlank @Size(max = 128)` (mirrors NIST max password length already enforced elsewhere).
- Do not add a minimum length on `currentPassword` here; the service's password encoder handles verification.

This keeps invalid input out of the service without duplicating business rules.

---

## Finding 4 · `RegenerateRecoveryCodesResult` vs. reusing `ConfirmResult`

**Challenge:** The brief proposes a new `RegenerateRecoveryCodesResult(List<String> recoveryCodes)` record in `MfaService`. `ConfirmResult` already has the exact same shape.

**Resolution:** Keep the separate record as proposed. Although the payload shape is identical, the two operations are semantically distinct (confirm = first-time enrollment, regenerate = replacement of existing codes). A shared return type would invite callers to conflate them and could complicate future changes (e.g., confirm might one day return the provisioning URI again). The controller will map either result into the shared `RecoveryCodesResponse` DTO.

---

## Finding 5 · `auth.yaml` must document both success and error responses

**Challenge:** The brief says to follow `/accounts/me/password`'s style, which currently only documents a 204 success. However, the MFA endpoints have multiple domain-error outcomes (409, 404, 401, 403) that are part of the contract.

**Resolution:** Add explicit `responses` entries for every status the endpoints can return:
- `201` / `200` / `204` for success.
- `400` for validation errors (shared `ValidationError` schema).
- `401` for `MFA_INVALID_CODE`.
- `403` for `MFA_PASSWORD_MISMATCH`.
- `404` for `MFA_NOT_ENROLLED`.
- `409` for `MFA_ALREADY_ENROLLED`.

Each error response uses the standard problem-schema. This satisfies the verification checklist item "`contracts/api/auth.yaml` covers every new non-SAS endpoint and error response."

---

## Finding 6 · `InvalidRecoveryCodeException` is correctly left unmapped

**Challenge:** `InvalidRecoveryCodeException` is used only by `MfaService.verifyRecoveryCode`, which is part of task 20's login flow. None of the four T19 endpoints can throw it.

**Resolution:** Confirm no mapping in `MfaExceptionHandler`. The exception will continue to be handled by the global catch-all if it ever propagates from an unintended call site, which is the correct failure mode.

---

## Finding 7 · `MfaEncryptionException` falls through to the global handler

**Challenge:** `MfaEncryptionException` represents KMS infrastructure failure. It is not a caller error and cannot be tested meaningfully from the controller layer.

**Resolution:** Do not add a specific mapping. Let it propagate to `ApiExceptionHandler.onUnexpected` (or equivalent), which produces a safe 5xx with no internal detail (R46).

---

## Finding 8 · `package.md` §8 mappings must be updated

**Challenge:** The current `spec/auth-service/package.md` lines 100–106 mis-map the three MFA named tests to wrong requirement numbers and omits R49 entirely.

**Resolution:** Update the three lines and add the new line for `shouldRegenerateRecoveryCodesWithPasswordAndTotp` → `R49`. This is a spec-file change explicitly authorized by the brief.

---

## Finding 9 · Module-boundary and public-endpoint checks must stay green

**Challenge:** The new `MfaController` lives in the `mfa` module. `ArchitectureTest.shouldPreventCrossModuleEntityImports` and `shouldEnforcePublicEndpointAllowlist` will run against it.

**Resolution:**
- `MfaController` depends only on `MfaService` and framework/DTO types; never on `Account`, `MfaEnrollment`, or another module's entity.
- No `permitAll()` calls in `MfaController` or its config; endpoints rely on the resource-server chain.
- Add an ArchUnit/contract test asserting the new endpoints are not in `PublicEndpoints`.

---

## Finding 10 · No production files beyond the brief's list should change

**Challenge:** The brief lists exact files to create and modify. It is tempting to refactor `MfaService.confirm` to reuse `regenerateRecoveryCodes` or vice versa, but that would change behavior outside this task's scope.

**Resolution:** Implement `regenerateRecoveryCodes` as a new, self-contained method that reuses existing primitives (`requireActiveAccount`, `resolveAccountId`, password check, TOTP verify, recovery-code generation, audit). Do not alter `beginEnroll`, `confirm`, or `disable` logic.

---

## Decisions Made

1. **Files to create:**
   - `MfaController.java`
   - `MfaExceptionHandler.java`
   - `BeginEnrollResponse.java`
   - `TotpCodeRequest.java`
   - `PasswordAndTotpRequest.java`
   - `RecoveryCodesResponse.java`
2. **Files to modify:**
   - `MfaService.java` — add `regenerateRecoveryCodes` + `RegenerateRecoveryCodesResult`.
   - `ProblemTypes.java` — add four MFA problem-type constants.
   - `contracts/api/auth.yaml` — four new paths + schemas + explicit error responses.
   - `spec/auth-service/package.md` — correct three mappings, add R49 line.
3. **Security:** raw TOTP secret stays inside the service; `BeginEnrollResponse` carries only the provisioning URI.
4. **HTTP status mapping:**
   - `MfaAlreadyEnrolledException` → 409.
   - `MfaNotEnrolledException` → 404.
   - `InvalidTotpCodeException` → 401 (documented rationale).
   - `MfaCurrentPasswordMismatchException` → 403.
5. **DTO validation:** `@NotBlank @Size` on all request fields.
6. **Exception handler:** `@RestControllerAdvice @Order(Ordered.HIGHEST_PRECEDENCE)` in `mfa` package.
7. **OpenAPI:** full success + error response documentation.
8. **No changes to** `MfaEnrollment`, `RecoveryCode`, `AccountService`, migrations, `TokenClaimsCustomizer`, `PublicEndpoints`.
