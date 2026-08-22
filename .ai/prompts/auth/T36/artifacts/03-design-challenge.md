<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T36 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T36 — End-to-end integration test |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-review.md` |

Below are adversarial findings on the Phase 2 TIB. Each finding is presented as **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## Finding 1 — TOTP enrollment has no HTTP surface; the brief leaves the blocker unresolved

**Issue.** The task statement says "enroll TOTP" as a flow step, but no HTTP endpoint exists for MFA enrollment (`services/auth/.../mfa/` has no `Controller`; `contracts/api/auth.yaml` documents no enrollment path). The brief correctly identifies this as a blocker but does not recommend a path forward, only two options with a request for a human-gate decision. A Phase 3 challenge cannot freeze a brief whose central flow step is undefined.

**Severity.** High — implementation planning is blocked until this is resolved.

**Evidence.**
- TIB §116-127: "Open Questions — Blocker. No LOCKED decision or requirement specifies an HTTP transport for TOTP enrollment."
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaService.java` lines 79-125: enrollment is `beginEnroll`/`confirm` service methods only.
- `contracts/api/auth.yaml`: no path matches `/mfa/**` or any TOTP enrollment endpoint.
- Precedent: `SasLoginIntegrationTest.seedConfirmedTotpEnrollment` (lines 722-727) already calls `mfaService.beginEnroll`/`confirm` directly.

**Recommended brief amendment.** Add a concrete recommendation with rationale, e.g.:

> "Resolve the blocker by selecting option (a): the test calls `MfaService.beginEnroll`/`confirm` directly, matching the already-accepted precedent in `SasLoginIntegrationTest` and keeping this task test-only. Option (b) (adding a production HTTP endpoint) is rejected as out of scope for T36."

---

## Finding 2 — How the interactive-login JWT is obtained is unspecified

**Issue.** Steps after "login with TOTP" (create API key, session list, session revoke) require a Bearer JWT, but the brief does not state how the test obtains one. Simply posting `/login` returns a 302 and a session cookie; it does not yield an access token. The test must either do the full `/oauth2/authorize` → `/login` → `/oauth2/authorize` → `/oauth2/token` round trip (as `SasLoginIntegrationTest` does) or take a test-only shortcut. The "real HTTP" constraint makes this a significant design decision.

**Severity.** High — the shape of the test changes materially depending on this choice.

**Evidence.**
- TIB §106-108: "real HTTP for every step with an HTTP surface."
- `SasLoginIntegrationTest.attemptFullAuthorizeFlow` (lines 613-650) and `exchangeCodeForToken` (lines 664-696) demonstrate the only existing real-HTTP path to an access token.
- `ApiKeyLifecycleIntegrationTest` uses `ApiKeyTokenIssuer.issue()` directly, but that bypasses the interactive login and would not satisfy the "real HTTP" framing for the login steps.

**Recommended brief amendment.** State explicitly:

> "After the TOTP login succeeds, the test re-issues `/oauth2/authorize` with the authenticated session and exchanges the resulting authorization code for an access token via `/oauth2/token` (reusing `SasLoginIntegrationTest`'s full-flow helpers), producing the JWT used for subsequent API-key and session HTTP calls."

---

## Finding 3 — Admin identity for the MERCHANT role grant is not described

**Issue.** The flow includes "admin assigns MERCHANT" over HTTP (`POST /admin/accounts/{accountUuid}/roles/MERCHANT`). That endpoint requires an authenticated principal with `ADMIN` role. The brief mentions "an ADMIN-role test identity" as an input but does not say how this identity is created, activated, granted ADMIN, or authenticated for the HTTP call.

**Severity.** High — the test cannot be written without a bootstrap strategy for the admin caller.

**Evidence.**
- TIB §58-59: "Inputs: One test-generated email/password pair; an ADMIN-role test identity to perform the role grant."
- `AdminAccountRoleController.assignRole` (lines 33-39) requires `@PreAuthorize("hasRole('ADMIN')")`.
- Existing integration tests either use service-layer role assignment directly (`SasLoginIntegrationTest` line 230) or test role resolution in isolation (`RoleAssignmentIntegrationTest`).

**Recommended brief amendment.** Specify the admin bootstrap, for example:

> "The test creates an admin account via `AccountService.register`/`activateEmail`, grants it `ADMIN` role via `RoleService.assignRole`, and mints a Bearer JWT for that admin via `ApiKeyTokenIssuer.issue()` (the same technique `ApiKeyCrudIntegrationTest` established) to authenticate the `POST /admin/accounts/{accountUuid}/roles/MERCHANT` call."

---

## Finding 4 — AC2's "blocked pre-enrollment" assertion layer is ambiguous

**Issue.** AC2 says the freshly MERCHANT-assigned account is "blocked (no auth code) at next login, pre-enrollment." The brief does not clarify whether the test asserts this at the `/login` form layer (redirect to `/login?error`, as `SasLoginIntegrationTest.merchantWithoutMfaEnrollmentCannotLogIn` does) or at the `/oauth2/authorize` layer (no authorization code returned, as `merchantWithoutEnrollmentCannotFinishAuthorizeFlow` does). The two are not equivalent: the latter is the stronger, contractually meaningful assertion.

**Severity.** Medium — affects which helper code is reused and what the test actually proves.

**Evidence.**
- TIB §92: "AC2 (R24, L10) — freshly `MERCHANT`-assigned account is blocked (no auth code) at next login, pre-enrollment."
- `SasLoginIntegrationTest` lines 227-238 (`/login` only) vs. lines 350-363 (full authorize flow).

**Recommended brief amendment.** Clarify:

> "AC2 is asserted at the `/oauth2/authorize` layer: after password-only login, the re-issued authorization request returns no `code` query parameter and the login response redirects to `/login?error`, matching the stronger full-flow pattern in `SasLoginIntegrationTest`."

---

## Finding 5 — R4 event emission is listed as a scoped requirement but explicitly not tested

**Issue.** The Phase 3 challenge prompt lists `R4` as a scoped requirement ID, and R4's text includes "emits `auth.user.registered`." The brief, however, says "no consumer-side assertion required" and scopes Kafka event verification out. This is either a conflict with the requirement or an unstated rationale for why the event half of R4 is not verified.

**Severity.** Medium — traceability gap; an auditor may expect an R4 event assertion.

**Evidence.**
- Challenge prompt header: "Scoped requirement IDs: `R1`, `R4`, `R24`, `R30`, `R31`, `R36`."
- TIB §30-31: "R4 — valid verification token → `ACTIVE`, emits `auth.user.registered`."
- TIB §70-71: "publishes to Kafka topics via the outbox (verified only insofar as R4's event is in scope — no consumer-side assertion required)."

**Recommended brief amendment.** Either:
- Remove R4 from the scoped requirement IDs and state that only its status-transition half is in scope, or
- Add a test-side assertion that the outbox contains the `auth.user.registered` event after verification (without requiring a running Kafka consumer).

---

## Finding 6 — Contract assertion mechanism is undefined

**Issue.** The brief says the test will assert "real assertions against `contracts/api/auth.yaml` / `token-claims.md` shapes." It does not say whether this means full OpenAPI schema validation, field-existence checks, or claim-key checks. Existing integration tests use hand-rolled JsonNode assertions, not schema validators.

**Severity.** Medium — "assert against auth.yaml" is not directly executable without choosing a tool or convention.

**Evidence.**
- TIB §21-22: "real assertions against `contracts/api/auth.yaml` / `token-claims.md` shapes at the create-API-key, exchange, session-list, and session-revoke steps."
- `ApiKeyLifecycleIntegrationTest` lines 116-121, 134-137, 163-168: hand-rolled JsonNode/JWT-claim assertions, no OpenAPI schema validator.

**Recommended brief amendment.** Specify the assertion style:

> "Contract-shape assertions are field-existence and value-pattern checks against the response JsonNode/JWT claims (e.g., `ck_live_` prefix, `scope` contains `merchant.api`, `amr` contains `api_key`, required session-list fields are present). Full OpenAPI schema validation is out of scope for this task."

---

## Finding 7 — Named test mapping is inconsistent with the challenge prompt header

**Issue.** The challenge prompt header lists the named test as `shouldConformToAuthOpenApiContract` (a task 33 contract-test name), while the brief correctly notes that `package.md` §8 has no valid named test for T36. This mismatch will confuse implementers and reviewers about which spec test this task satisfies.

**Severity.** Medium — spec traceability issue.

**Evidence.**
- Challenge prompt header: "Named tests (`package.md` §8): `shouldConformToAuthOpenApiContract`."
- TIB §76-77: "package.md has no valid named test for this task, per Phase 1."
- `spec/auth-service/package.md` §8 does not list an end-to-end integration test name for task 36.

**Recommended brief amendment.** Add:

> "Task 36 carries no distinct named test in `package.md` §8. The test method name will be chosen in Phase 5 to match the `*IntegrationTest` convention (e.g., `shouldCompleteFullIdentityLifecycle`) and will not reuse `shouldConformToAuthOpenApiContract`, which belongs to task 33."

---

## Finding 8 — Scoped LOCKED decisions header says none, but the brief depends on six

**Issue.** The challenge prompt header says "Scoped LOCKED decisions: none — no LOCKED decision constrains this task," yet the brief lists L6, L8, L9, L10, L11, and L12 as directly governing the test's behavior. This is a factual inconsistency in the prompt header, but the brief should make its own scoped LOCKED decisions explicit rather than silently inheriting them.

**Severity.** Low/Medium — standing-rule traceability.

**Evidence.**
- Challenge prompt header: "Scoped LOCKED decisions: none."
- TIB §38-46: lists L6, L8, L9, L10, L11, L12.

**Recommended brief amendment.** Add a "Scoped LOCKED decisions" subsection:

> "L6 (TOTP parameters), L8 (API-key JWT contract), L9 (access-token claim set), L10 (MFA mandatory for MERCHANT/ADMIN), L11 (public-endpoint list), L12 (module boundaries — test-only, no new dependency)."

---

## Finding 9 — It is unclear which JWT authenticates the API-key and session calls

**Issue.** The flow produces two access tokens: one from the interactive login (authorization-code grant) and one from the API-key exchange (client-credentials-style grant). The brief says "exchange key for JWT → call session list → revoke session," suggesting the exchanged JWT is used for the session calls. However, the session under test must be the one created by the interactive login; using the exchanged JWT for session management is the meaningful assertion that the API-key token is usable service-wide.

**Severity.** Medium — the intended coverage could be misread.

**Evidence.**
- TIB §7: "create API key → exchange key for JWT → call session list → revoke session."
- `SessionIntegrationTest` authenticates session endpoints with a JWT minted via `ApiKeyTokenIssuer`.

**Recommended brief amendment.** Clarify:

> "The JWT obtained from `POST /api-keys/token` is used as the Bearer token for `GET /accounts/me/sessions` and `DELETE /accounts/me/sessions/{familyId}`. The session list is expected to contain the refresh-token family created by the earlier interactive login; revocation of that family is verified by a follow-up list returning empty."

---

## Finding 10 — TOTP determinism strategy is not specified

**Issue.** The brief states that "TOTP code generation must use a fixed/controllable time source consistent with L6's 30s step, not a live wall-clock race." The server, however, uses `Clock.systemUTC()`. The existing precedent is to generate the code with `Instant.now()` immediately before submitting the login form, not to fix the server's clock. The brief does not adopt this precedent explicitly.

**Severity.** Low/Medium — flakiness risk if a different strategy is chosen.

**Evidence.**
- TIB §112-113: "TOTP code generation must use a fixed/controllable time source."
- `SasLoginIntegrationTest.attemptLoginWithFreshTotpCode` (lines 470-472) calls `referenceGenerateCode(secret, Instant.now())` at submission time.
- `SasLoginIntegrationTest` line 156: server uses `Clock.systemUTC()`.

**Recommended brief amendment.** Replace the vague "fixed/controllable time source" with the concrete, proven pattern:

> "TOTP codes are generated from the enrollment secret using `Instant.now()` at the moment the login form is submitted, matching `SasLoginIntegrationTest`'s `attemptLoginWithFreshTotpCode` pattern. This relies on the server's 90-second verification tolerance window and avoids the need to control the server's clock."

---

## Finding 11 — AC7 "equivalent" verification is vague

**Issue.** AC7 says session revoke is "verified by a follow-up list or equivalent." The brief does not define what "equivalent" means, leaving room for a weak assertion (e.g., only checking the 204 status) that would not catch a revocation that failed to persist.

**Severity.** Low.

**Evidence.**
- TIB §96: "AC7 (R37) — `DELETE /accounts/me/sessions/{familyId}` revokes the family (verified by a follow-up list or equivalent)."
- `SessionIntegrationTest.shouldRevokeSingleSessionFamily` (lines 137-163) verifies both the empty list and the revoked family row.

**Recommended brief amendment.** Specify:

> "Verification is a follow-up `GET /accounts/me/sessions` returning an empty array, plus a direct repository reload confirming the family's `revokedAt`/`revokedReason` are set."

---

## Summary

The brief correctly identifies the largest unknown (TOTP enrollment transport) but should resolve it before freezing. The next most important gaps are the unspecified JWT acquisition path for post-login HTTP calls and the unspecified admin bootstrap. Once those are decided, the brief will be implementable.

(End of Phase 3 design challenge.)
