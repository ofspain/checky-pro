# auth · T22 · Phase 1 — Specification Extraction

## Business Rules
- **R24.** MERCHANT/ADMIN, no confirmed TOTP enrollment → must be required to complete enrollment before an authorization code is issued (task statement's "merchant without MFA cannot finish authorize flow").
- **R25.** Confirmed TOTP enrollment → a valid TOTP code or unused recovery code required during the SAS interactive flow before an authorization code is issued (task statement's "confirmed MFA requires code").
- **R26.** Interactive login completed with password + TOTP → the issued access token contains `amr: ["pwd","otp"]` and `acr: urn:themistra:acr:otp` (task statement's "correct code produces `amr: [pwd, otp]`").

Not pulled in: R27/R29 (already covered by T20's `TokenClaimsCustomizerTest`/`SasLoginIntegrationTest` at the layers appropriate to each — this task's header scopes only R24/R25/R26, and its task statement's three named scenarios map onto exactly these three, one each).

## Locked Decisions
- **L10.** MFA mandatory for MERCHANT/ADMIN, optional for USER/COMPLIANCE — governs which role T22's "merchant without MFA" scenario must use to exercise R24 (MERCHANT, not USER).
- **L9** (referenced, not owned). Fixed access-token claim set — the issued JWT this task inspects must still conform to it; T22 doesn't change what's issued, only proves it end-to-end.

## Files involved

**Existing — read (this task changes no production code):**
- `authn/TotpAuthenticationProvider.java` — the component under test; T22 proves its behavior through the full HTTP stack rather than unit-testing it directly (already done in T20).
- `token/TokenClaimsCustomizer.java` — the component whose `amr`/`acr` output T22 inspects on a genuinely-issued token, closing the exact gap T20 (Phase 10) and `auth-decisions.md` D-023 both deliberately left open rather than writing unverified.
- `token/RegisteredClientSeeder.java` / `AuthClientsProperties` — source of the SPA client's `client_id` (`checky-spa`) and `redirect_uri` (`http://localhost:5173/auth/callback` by default) T22's `/oauth2/authorize` calls must use; confirms PKCE is mandatory (`requireProofKey(true)`) and consent is disabled (`requireAuthorizationConsent(false)` — no extra round trip to handle).
- `mfa/MfaService.java`, `authz/RoleService.java` — seed confirmed enrollment / assign MERCHANT, same pattern T20 already established in `SasLoginIntegrationTest`.

**Existing — extend:**
- `authn/SasLoginIntegrationTest.java` — this task's tests are added here, reusing its `TestRestTemplate`/CSRF/cookie-handling infrastructure and `registerAndActivate`/`seedConfirmedTotpEnrollment`/`ensureRoleExists` helpers (all now verified working against a real server, confirmed today). Needs one new capability the current helpers don't have: capturing and reusing the session cookie from a successful `/login` into a follow-up `/oauth2/authorize` call — the existing `attemptLogin` discards it after building the `LoginAttempt` record.

**New:** none expected. The task statement says "Add Testcontainers tests" — test-only scope, no new production classes, no new endpoint, no schema change.

## Dependencies
- `TestRestTemplate` (redirect-following disabled, matching this file's established pattern) for `/login` → `/oauth2/authorize` → `/oauth2/token`.
- PKCE `code_verifier`/`code_challenge` (S256) — standard `MessageDigest.getInstance("SHA-256")` + `Base64.getUrlEncoder().withoutPadding()`, no new dependency.
- `com.nimbusds:nimbus-jose-jwt` (already on the classpath transitively via `spring-security-oauth2-jose`, confirmed in Phase 0) — for parsing the issued JWT's claims (`JWTParser.parse(token).getJWTClaimsSet()`).
- `mfa/MfaService.beginEnroll`/`confirm` (public) — enrollment seeding, no self-service HTTP endpoint exists (T19 gap, unchanged).
- `authz/RoleService.createRole`/`assignRole` (public) — role seeding, same pattern T20 established.

## Acceptance Criteria
| ID | Criterion | Task-statement scenario |
|---|---|---|
| R24 | MERCHANT/ADMIN, no confirmed enrollment → the `/oauth2/authorize` flow never yields an authorization code | "merchant without MFA cannot finish authorize flow" |
| R25 | Confirmed enrollment → password alone does not complete the flow; a valid TOTP/recovery code does | "confirmed MFA requires code" |
| R26 | Correct code → the JWT obtained from `/oauth2/token` has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` | "correct code produces `amr: [pwd, otp]`" |

## Tests required
From `package.md` §8 / this task's header, matched directly to the task statement's three scenarios:
1. `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` (R24) — full-stack version of what T20 already proved at the `/login` layer; T22's version must show the *authorize* flow itself never yields a code, not just that `/login` redirects to an error.
2. `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` (R25) — full-stack.
3. `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (R26) — the one genuinely new capability: inspecting an *actually-issued* JWT, which T20 deliberately did not attempt (unit-tested `TokenClaimsCustomizer` directly instead, per `auth-decisions.md` D-023's precedent against unverified full-flow tests). This is the test that gives that precedent's underlying concern ("cannot be verified without running it") an actual answer, now that it can be run.

Boundary test implied, not explicitly named by the task statement, but a natural complement given every other scenario needs a positive control: a USER/other-role account with no confirmed enrollment completes the full `/login`→`/oauth2/authorize`→`/oauth2/token` flow successfully, `amr: ["pwd"]`. Optional — Phase 2 decides whether to include it.

## Open Questions
None that are genuine blockers. Two things Phase 0 flagged as unknowns are no longer open, now that Docker/Testcontainers actually work in this environment and can be used to find out empirically rather than guess:
- The session-cookie name and `/oauth2/authorize`'s exact redirect shape for a PKCE public client with consent disabled — discoverable by running the test, the same way today's session already found and fixed `SasLoginIntegrationTest`'s CSRF-regex bug empirically rather than by inspection alone.
- Whether writing this test at all is consistent with this codebase's risk posture (`auth-decisions.md` D-023) — resolved by the same fact: D-023's stated objection was specifically that the risk "cannot be verified without running it." That's no longer true. The objection doesn't transfer to an environment where it *can* be run, debugged, and iterated on.
