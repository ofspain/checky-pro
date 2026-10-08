<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# auth · T19 — Phase 8: Independent Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T19 — MFA self-service HTTP surface + recovery-code regeneration |
| **Spec section** | R22, R23, R28, R29, R49 |
| **Model** | Kimi 2.7 |
| **Consumes** | Implementation + `artifacts/07-self-review.md` + frozen brief |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the auth T19 implementation and self-review.

---

## Finding 1 · `MfaCurrentPasswordMismatchException` deviates from the frozen brief's status code and problem type

**Severity:** Medium

**Evidence:**
- Frozen brief (Scope / `MfaExceptionHandler` mappings): `MfaCurrentPasswordMismatchException` → `403 Forbidden`, new `ProblemTypes.MFA_PASSWORD_MISMATCH`.
- Implementation (`MfaExceptionHandler.java` lines 77–83): maps the same exception to `400 Bad Request` and reuses the existing `ProblemTypes.CURRENT_PASSWORD_MISMATCH`.
- Implementation rationale: a stable problem type cannot mean 400 in one endpoint (`/accounts/me/password`) and 403 in another (`/accounts/me/mfa/totp`).

**Assessment:** The implementation's rationale is sound and consistent with the existing problem-type discipline. However, it is a material deviation from the frozen brief. The dedicated test `onPasswordMismatchReusesTheExactSameProblemTypeAsAccountsMePassword` encodes this deviation permanently.

**Recommendation:** Either (a) update the frozen brief and Phase 12 specification verification to record the deliberate change, or (b) revert to the brief's original mapping if the product owner wants a distinct MFA password-mismatch type. If (b) is chosen, note that it will create the inconsistency the implementation avoided. Do not leave the brief and code in contradiction.

---

## Finding 2 · `contracts/api/auth.yaml` MFA paths lack explicit `security` and error responses

**Severity:** Medium

**Evidence:**
- Frozen brief: "all `security: [bearerAuth]` (none added to `PublicEndpoints.java`, per L11)" and "explicit response codes" for every status.
- `contracts/api/auth.yaml` lines 163–226: the four `/accounts/me/mfa/*` operations do not declare `security:`, and each documents only its success response (201/200/204).

**Assessment:** The endpoints are correctly authenticated at runtime (the controller relies on the resource-server chain), but the contract file does not say so. Generated clients and documentation will treat these paths as inheriting whatever default security the OpenAPI document provides. This file has no global `security`, so the contract is ambiguous. Additionally, the error responses (400, 401, 404, 409) that `MfaExceptionHandler` produces are not documented.

**Recommendation:** Add `security: [bearerAuth]` to each MFA operation and add the full set of error responses (400 validation/current-password mismatch, 401 invalid TOTP code, 404 not enrolled, 409 already enrolled) using the existing problem-schema pattern. This aligns the contract with the actual runtime behavior and the brief.

---

## Finding 3 · Only three of the four planned `ProblemTypes` constants were added

**Severity:** Low (direct consequence of Finding 1)

**Evidence:**
- Frozen brief listed `MFA_ALREADY_ENROLLED`, `MFA_NOT_ENROLLED`, `MFA_INVALID_CODE`, `MFA_PASSWORD_MISMATCH`.
- `ProblemTypes.java` lines 43–51 contains only the first three.

**Assessment:** This is consistent with the implementation's decision in Finding 1. If the brief's original four-type plan is restored, `MFA_PASSWORD_MISMATCH` must be added.

**Recommendation:** Resolve together with Finding 1; do not add a dead constant if the 400/CURRENT_PASSWORD_MISMATCH choice is kept.

---

## Finding 4 (concur with self-review Finding 3) · `regenerateRecoveryCodes` old-code invalidation is now proven

**Severity:** Low (already fixed)

**Evidence:**
- `MfaServicePersistenceIntegrationTest.regenerateRecoveryCodesInvalidatesOldCodesAndPersistsTenGenuinelyUsableNewOnes` verifies that an old code throws `InvalidRecoveryCodeException` after regeneration and a new code is accepted.

**Assessment:** Valid gap, closed. The test genuinely proves R49's "invalidate every existing recovery code" guarantee rather than just asserting the returned list differs.

**Recommendation:** No further action.

---

## Finding 5 (concur with self-review Finding 4) · Concurrent regenerate race is disclosed and empirically characterized

**Severity:** Low (accepted limitation)

**Evidence:**
- `MfaServicePersistenceIntegrationTest.concurrentRegenerateRecoveryCodesCallsResultInExactlyOneSuccessAndTenRecoveryCodes` shows one transaction wins and the loser rolls back via `ObjectOptimisticLockingFailureException`.
- Self-review discloses this as a known, accepted limitation requiring the account's own valid credentials used concurrently.

**Assessment:** The failure mode is safe (opaque 500, no data corruption). The disclosure is clear. A future enhancement could add explicit retry/backoff or a conditional delete guard, but that is out of this task's frozen scope.

**Recommendation:** Leave as-is; track as a future ergonomic improvement if double-clicks on this endpoint become a support issue.

---

## Finding 6 (concur with self-review Finding 2) · Stale Javadoc in `MfaServicePersistenceIntegrationTest` was corrected

**Severity:** Low (already fixed)

**Evidence:**
- Test class Javadoc now correctly states the class runs green against real Docker.

**Recommendation:** No further action.

---

## Finding 7 (concur with self-review Finding 1) · All 11 ArchUnit rules re-verified

**Severity:** Informational

**Evidence:**
- Self-review reran all 11 `ArchitectureTest` rules; `MfaController` satisfies `controllersDependOnlyOnTheirOwnModuleServices` with no allowlist entry.

**Recommendation:** No action; good practice.

---

## Finding 8 · `MfaControllerIntegrationTest.noneOfTheFourEndpointsArePublic` is a valuable complement to ArchUnit

**Severity:** Informational

**Evidence:**
- `MfaControllerIntegrationTest` lines 269–275 assert no `/accounts/me/mfa` prefix appears in `PublicEndpoints.METHOD_SCOPED`.

**Assessment:** This is a strong runtime/contract-level check that complements the static ArchUnit rule. It directly proves the brief's "none added to `PublicEndpoints.java`" requirement.

**Recommendation:** No action; keep.

---

## Cross-check against acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| AC1 — `POST /totp` returns 201 with provisioning URI | ✅ | Controller + integration tests verify. |
| AC2 — `POST /totp/confirm` returns recovery codes | ✅ | Controller + integration tests verify. |
| AC3 — `DELETE /totp` requires password + TOTP | ✅ | Integration tests verify. |
| AC4 — `POST /recovery-codes` regenerates codes | ✅ | New service method + persistence integration test prove old codes invalidated. |
| AC5 — raw secret never exposed | ✅ | `BeginEnrollResponse` carries only URI; service result has redacted `toString()`. |
| AC6 — RFC 9457 error responses | ⚠️ | Handler exists, but `auth.yaml` does not document error responses. |
| AC7 — no public endpoints | ✅ | Controller has no `permitAll`; `PublicEndpoints` assertion passes. |
| AC8 — module boundaries | ✅ | `MfaController` depends only on `MfaService`. |
| AC9 — contract updated | ⚠️ | Paths and schemas added, but missing `security` and error responses. |
| AC10 — package.md §8 updated | ✅ | Three mappings fixed, R49 line added, out-of-scope drift disclosed. |

---

## Verdict

The implementation is functionally correct and well-tested. The two material issues are both contract/documentation-level, not runtime-behavior defects:
1. The password-mismatch status/type deviation from the frozen brief (Finding 1).
2. The OpenAPI YAML's missing `security` and error-response documentation (Finding 2).

The runtime code, tests, and security posture satisfy the task's purpose. Resolve Findings 1 and 2 in Phase 9 before final sign-off.
