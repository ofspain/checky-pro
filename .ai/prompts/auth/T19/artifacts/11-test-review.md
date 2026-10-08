<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T19 — Phase 11: Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T19 — MFA self-service HTTP surface + recovery-code regeneration |
| **Spec section** | R22, R23, R28, R29, R49 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + final test/production files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T19 tests against the acceptance criteria and the final implementation.

---

## What is covered

- **`MfaServiceTest`** — 7 new unit tests for `regenerateRecoveryCodes`: happy path, wrong password, wrong code, missing enrollment, non-active account, missing login view, and `toString` redaction.
- **`MfaControllerTest`** — 13 unit tests covering all four endpoints: status codes, response shapes, caller derivation, argument order, and exception propagation.
- **`MfaExceptionHandlerTest`** — 6 unit tests mapping every handled exception to the correct `ProblemDetail` status/type/title.
- **`MfaControllerIntegrationTest`** — 14 integration tests including the 4 named tests from `package.md` §8, boundary/failure paths, DTO validation, and the `PublicEndpoints` assertion that none of the four endpoints are public.
- **`MfaServicePersistenceIntegrationTest`** — 2 new integration tests proving old recovery codes are genuinely invalidated after regeneration and characterizing the concurrent-regenerate behavior.
- **`AuthOpenApiContractTest`** — extended to include `MfaController` and the four new routes/schemas in its completeness and shape checks.

---

## Gap 1 · `auth.yaml` still does not document error responses

**Why it matters:** The frozen brief required explicit error-response documentation for every status the endpoints can return. The implementation only documents success responses (201/200/204) on the MFA paths.

**Evidence:**
- `contracts/api/auth.yaml` lines 163–234: each MFA operation has only a 2xx response block.
- `AuthOpenApiContractTest` class Javadoc lines 63–66 explicitly states it is "scoped to success responses only ... error responses are documented in `auth.yaml` for human/codegen value but are not verified here."

**Assessment:** The runtime behavior is proven by `MfaControllerIntegrationTest` (409, 401, 400, 404), but the contract file itself is incomplete. Because the contract test deliberately does not check error responses, this gap would only be caught by manual review.

**Suggested action:** Add 400, 401, 404, and 409 response blocks to each MFA operation in `auth.yaml`, referencing the shared problem-schema. Optionally extend `AuthOpenApiContractTest` to assert that every operation documents at least its expected error status codes.

---

## Gap 2 · No test verifies `security: [bearerAuth]` is present in `auth.yaml`

**Why it matters:** Phase 9 added `security: [bearerAuth: []]` to the four MFA operations (confirmed in the current YAML), but the contract test does not verify security declarations.

**Evidence:**
- `AuthOpenApiContractTest` verifies route existence, request/response schema refs, and component shape, but never inspects `operation.security`.
- Runtime authentication is proven by `MfaControllerIntegrationTest.allFourEndpointsReject401WithoutABearerToken`.

**Assessment:** This is a contract-vs-runtime parity gap, not a behavior gap. The runtime proof is solid; the contract proof is absent.

**Suggested action:** Add a contract test asserting every non-public controller route either declares `security: [bearerAuth]` or is explicitly listed in the public-endpoint allowlist. This would be a service-wide improvement, not only for T19.

---

## Gap 3 · The frozen brief and the implemented password-mismatch response are in contradiction

**Why it matters:** `MfaExceptionHandlerTest.onPasswordMismatchReturnsUniform400` tests the implemented behavior (400 + `CURRENT_PASSWORD_MISMATCH`), while the frozen brief specified 403 + `MFA_PASSWORD_MISMATCH`. The tests are correct for the code as written, but the spec artifact is stale.

**Evidence:**
- `MfaExceptionHandler.java` lines 77–83 returns 400 with `ProblemTypes.CURRENT_PASSWORD_MISMATCH`.
- `MfaExceptionHandlerTest.java` lines 62–71 asserts 400 and the reused type.
- Frozen brief Scope section: `MfaCurrentPasswordMismatchException` → `403 Forbidden`, new `MFA_PASSWORD_MISMATCH` constant.

**Assessment:** This is not a test defect; the tests accurately verify the implementation. It is a spec/documentation defect that must be resolved so the frozen brief does not contradict the code.

**Suggested action:** Update the frozen brief / Phase 12 verification to record the deliberate 400-not-403 correction, or change the implementation to match the brief (noting the problem-type inconsistency that creates).

---

## Gap 4 · Maven verification claim cannot be confirmed in this environment

**Why it matters:** Phase 10 reports 89 passing tests across 6 classes. This workspace does not have `mvn` available.

**Evidence:**
- `shell`/`mvn` returns `command not found` in this workspace.
- The test classes are syntactically consistent and the traceability map is complete, but no local execution was performed.

**Suggested action:** Run the Phase 10 Maven command in an environment with Maven before final sign-off.

---

## Gap 5 · `AuthOpenApiContractTest` does not cover the `security` field or error responses

**Why it matters:** The contract test is the named test (`shouldConformToAuthOpenApiContract`) for R40/R41. Its explicit scope excludes security and error responses.

**Evidence:**
- Class Javadoc lines 63–66.

**Assessment:** This is a known, documented limitation of the contract test, not a T19-specific gap. It is acceptable as long as the omissions are tracked and the runtime tests cover the behavior.

**Suggested action:** None for T19; consider a future cross-service contract-test enhancement to verify security and error responses.

---

## Summary

T19's test suite is comprehensive and traceable: 89 tests across six classes cover the service, controller, exception handler, HTTP integration, persistence, and OpenAPI contract. The four named tests from `package.md` §8 are present and correctly mapped. The remaining gaps are contract/documentation completeness (error responses in `auth.yaml`, `security` verification in contract tests) and an unverified Maven run claim, not behavior defects.
