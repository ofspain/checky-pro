# auth · T22 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T22 — MFA integration tests |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 TIB. Findings only.

---

## 1. R24/R25 "full authorize flow" tests cannot use the described "login first, then authorize" pattern

- **Issue.** The TIB states the new infrastructure will let a "successful `/login` … be followed by an authenticated `/oauth2/authorize` call." But R24 and R25 are *failure* scenarios: the account either has no confirmed enrollment (R24) or supplies a wrong/missing code (R25). In those cases `/login` fails and returns `/login?error`; there is no successful session cookie to capture, so the described happy-path cookie-propagation mechanism does not apply.
- **Severity.** High — the implementation plan as written does not cover the two named tests it is meant to add.
- **Evidence.** `requirements.md` R24/R25 both gate issuance of an authorization code; `TotpAuthenticationProvider` throws `BadCredentialsException` for both cases, and `LoginFailureHandler` redirects to `/login?error` uniformly. `LoginSuccessHandler` only runs on successful authentication and is the only point at which a session cookie usable for `/oauth2/authorize` would be created.
- **Recommended brief amendment.** State explicitly that the R24/R25 tests (and the R26 success test) all begin with an **unauthenticated** `/oauth2/authorize` request, follow the SAS redirect to `/login`, submit credentials, and then inspect either the redirect back to `/login?error` (failure) or the redirect to the registered `redirect_uri` carrying `code=` (success). This matches `SavedRequestAwareAuthenticationSuccessHandler`'s behavior and is the only construction that genuinely exercises the `/oauth2/authorize`→`/login`→code path.

---

## 2. Requirement IDs for the named tests are inconsistent across spec documents

- **Issue.** `package.md` §8 maps `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` → R21, `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` → R22, and `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` → R23. `requirements.md` numbers the same acceptance criteria as R24, R25, and R26. The TIB uses R24/R25/R26. `package.md` also lists a separate `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` → R24, which collides with the TIB's use of R24 for the merchant enrollment gate.
- **Severity.** Medium — traceability between named tests and acceptance criteria becomes ambiguous, and the test class Javadoc/comments may cite the wrong requirement IDs.
- **Evidence.** Direct comparison of `spec/auth-service/package.md` §8 and `spec/auth-service/requirements.md` §3 (TOTP MFA section). `package.md` is older and appears to predate the renumbering in `requirements.md`.
- **Recommended brief amendment.** Add a note that the authoritative IDs for this task are those in `requirements.md` (R24/R25/R26) and that `package.md` §8 will be reconciled separately. Alternatively, if `package.md`'s numbering is authoritative, update the TIB to use R21/R22/R23 and adjust the recovery-code/positive-control references accordingly.

---

## 3. The referenced token-claims contract file does not exist

- **Issue.** L9 (`design.md` §4a), the TIB Dependencies section, and the `TokenClaimsCustomizer` Javadoc all reference `contracts/api/token-claims.md` as the source of truth for the access-token claim set. The file is absent from the repository. R48 further requires that every access token contain "exactly the claims listed" in that contract.
- **Severity.** Medium — the TIB scopes R26 to asserting `amr`/`acr`, but the broader L9/R48 contract cannot be verified without the artifact. An implementer may under- or over-assert.
- **Evidence.** `glob`/`find` for `token-claims.md` under `contracts/` and `services/auth/` returns no results. `contracts/api/auth.yaml` is also absent.
- **Recommended brief amendment.** Either (a) explicitly scope the R26 integration test to `amr`/`acr` only and record a follow-up to author `contracts/api/token-claims.md`, or (b) add the contract file as a deliverable of a prerequisite task and require the R26 test to assert the full claim set listed there. Do not leave L9/R48 as an unverifiable blanket requirement while the contract artifact is missing.

---

## 4. `nimbus-jose-jwt` presence on the classpath is assumed but not verified

- **Issue.** The TIB lists `com.nimbusds:nimbus-jose-jwt` as a dependency "already on the classpath," but it is not declared in `services/auth/pom.xml` and a repository-wide grep finds no direct dependency declaration.
- **Severity.** Medium — if the library is not pulled transitively by `spring-security-oauth2-authorization-server` at runtime, the R26 JWT-parsing helper will fail to compile.
- **Evidence.** `grep` for `nimbus` in `services/auth/pom.xml` and root `pom.xml` returns no matches. `spring-security-oauth2-jose` is part of SAS and typically brings Nimbus, but the exact transitive set should not be assumed without verification.
- **Recommended brief amendment.** Add a verification step: run the build and confirm `com.nimbusds.jwt.SignedJWT` (or `JWTClaimsSet`) resolves. If not, add an explicit `nimbus-jose-jwt` dependency to `services/auth/pom.xml` before implementation, noting that this is still test-only code and does not alter production security logic.

---

## 5. SPA redirect URI points to a different origin than the test server

- **Issue.** `RegisteredClientSeeder` seeds the SPA client with redirect URI `http://localhost:5173/auth/callback`. The integration test runs on a random port. A successful `/oauth2/authorize` will therefore 302 to `localhost:5173`, not back to the test server.
- **Severity.** Low-Medium — the test can still parse `code=` from the `Location` header with redirects disabled, but the implementation must not accidentally follow the redirect and then fail because no SPA is listening.
- **Evidence.** `AuthClientsProperties.Spa` defaults `redirectUris[0]` to `${SPA_REDIRECT_URI:http://localhost:5173/auth/callback}`; `RegisteredClientSeeder.spaClient` registers that URI verbatim.
- **Recommended brief amendment.** State explicitly that the test keeps `setInstanceFollowRedirects(false)` and extracts the authorization `code` from the query string of the `Location` header. Do not rely on `TestRestTemplate` automatically following the redirect to the SPA callback.

---

## 6. Positive-control boundary test is required but not named

- **Issue.** The TIB Required Tests section lists a boundary case — "a non-mandatory-role account with no enrollment completes the full flow successfully (`amr: ["pwd"]`)" — but says it is "not separately named." `package.md` §8 does not list this test either; it only lists `shouldIssueTokenWithPwdAmrWhenMfaNotRequired` → R24 (which is itself a numbering collision, per finding #2).
- **Severity.** Low — the test is straightforward, but unnamed acceptance criteria are easy to drop during implementation or review.
- **Evidence.** TIB §71: "Boundary, not separately named: a non-mandatory-role account with no enrollment completes the full flow successfully (`amr: ["pwd"]`)".
- **Recommended brief amendment.** Either promote the boundary to a named test (e.g., `shouldIssueTokenWithPwdAmrWhenMfaNotRequiredThroughFullAuthorizeFlow`) or explicitly accept it as a private helper assertion inside another test, and record which choice was made.

---

## 7. R26 full-flow test does not specify whether it exercises TOTP, recovery code, or both

- **Issue.** The TIB says "a correct code produces `amr: [pwd, otp]`" and "valid TOTP or recovery code" in the R25 scope, but the R26 acceptance criterion and named test do not state which factor type the full `/oauth2/authorize`→`/oauth2/token` flow will use. The existing `SasLoginIntegrationTest` already has separate TOTP and recovery-code login tests.
- **Severity.** Low.
- **Evidence.** TIB §65 R26 criterion and §70 named test mention only "completed password+TOTP flow." T20's `merchantCanLoginWithAnUnusedRecoveryCodeButNotWithItASecondTime` proves recovery codes through `/login` only.
- **Recommended brief amendment.** Clarify that the named R26 test uses a fresh TOTP code (the common path) and optionally add a second boundary assertion or test proving the same `amr`/`acr` outcome when a recovery code is used. If only TOTP is in scope, say so explicitly.

---

## 8. Public-client `/oauth2/token` exchange shape is assumed

- **Issue.** The TIB depends on exchanging the authorization code at `/oauth2/token` with PKCE, but it does not specify the exact request form for a public client (`client_id=checky-spa`, `grant_type=authorization_code`, `code`, `redirect_uri` matching the authorize request, `code_verifier`, and no client secret).
- **Severity.** Low-Medium — SAS's behavior for `ClientAuthenticationMethod.NONE` + `requireProofKey(true)` is standard, but the test must match SAS's exact expectations for `redirect_uri` equality and parameter encoding.
- **Evidence.** `RegisteredClientSeeder.spaClient` configures `.clientAuthenticationMethod(NONE)` and `requireProofKey(true)`; the TIB mentions PKCE generation but not the token-exchange request shape.
- **Recommended brief amendment.** Add the exact `/oauth2/token` request form to the brief, including the requirement that `redirect_uri` matches the `/oauth2/authorize` call byte-for-byte. Note that no `Authorization: Basic` header is used because the SPA client has no secret.

---

## 9. JWT claim parsing helper should clarify signature-verification expectations

- **Issue.** The TIB says the helper must "fail loudly … if a claim is missing" but does not say whether the JWT signature should be verified. `agents.md` requires RS256 tokens; verifying the signature would require fetching JWKS from `/.well-known/jwks.json`.
- **Severity.** Low.
- **Evidence.** TIB Constraints §78: "the JWT-claim-parsing helper must fail loudly (not silently return null/empty) if a claim is missing." No mention of signature verification.
- **Recommended brief amendment.** State whether R26 parses claims only (acceptable for a service-owned token endpoint test) or also verifies the signature against the live JWKS endpoint. If signature verification is out of scope, document that explicitly so the helper does not silently appear incomplete.

---

## 10. TOTP time-window crossing risk is higher in the full flow than in direct `/login` tests

- **Issue.** In the full `/oauth2/authorize`→`/login`→`/oauth2/token` flow, the TOTP code is generated at login time and the access token is issued shortly afterward. While typical TOTP verifiers accept ±1 window (~90 s), a slow Testcontainers run or a GC pause could in theory push the exchange across a window boundary. The existing tests generate the code immediately before the `/login` POST.
- **Severity.** Low — unlikely to flake in practice, but worth documenting so retries are not misinterpreted as product bugs.
- **Evidence.** `SasLoginIntegrationTest.attemptLoginWithFreshTotpCode` calls `referenceGenerateCode(secret, Instant.now())` immediately before posting to `/login`.
- **Recommended brief amendment.** Add a note that the TOTP code should be generated immediately before the `/login` POST and that the token exchange is expected to complete within the verifier's tolerance window. If flakiness appears, the helper should allow regeneration or use a test-time clock override, but production code must never be changed to widen the window.

---

## 11. `SavedRequestAwareAuthenticationSuccessHandler` behavior is the unverified linchpin of the full flow

- **Issue.** The R26 success path depends on Spring Security's `SavedRequestAwareAuthenticationSuccessHandler` (used by `LoginSuccessHandler`) redirecting back to the original `/oauth2/authorize` request after login, which then redirects to the SPA callback with the authorization code. The exact `Location` header shape and whether query parameters (client_id, redirect_uri, state, code_challenge) are preserved across the saved request are framework-internal details.
- **Severity.** Low-Medium — the TIB acknowledges "session-cookie name, exact `/oauth2/authorize` redirect shape" as implementation-time discoveries, but the success case depends on the same unverified behavior.
- **Evidence.** `LoginSuccessHandler` extends `SavedRequestAwareAuthenticationSuccessHandler` and calls `super.onAuthenticationSuccess`. `SecurityChainsConfig` configures form login without overriding the default success handler.
- **Recommended brief amendment.** Keep the existing "implementation-time discovery" note but extend it to cover both failure and success redirect shapes. Add a fallback: if the saved-request redirect does not preserve the original `/oauth2/authorize` parameters, the test should re-issue `/oauth2/authorize` with the captured session cookie rather than treating the saved-request redirect as mandatory.

---

## 12. No conflict found with LOCKED decisions or `agents.md`

- L10 (MFA mandatory for `MERCHANT`/`ADMIN`) is correctly reflected in the TIB's choice of role for R24.
- L9 (fixed claim set) is correctly treated as read-only; the test only inspects `amr`/`acr`.
- L5 (enumeration-safe responses) is respected because the test observes real HTTP redirects rather than inferring internal state.
- No production code changes are proposed, consistent with the "test-only" scope.
- No `spec/` files were modified.

(End of artifact)
