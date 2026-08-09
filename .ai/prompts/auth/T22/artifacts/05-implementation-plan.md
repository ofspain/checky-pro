# auth · T22 · Phase 5 — Implementation Plan

Single file traces to the frozen brief's Files to Modify: `authn/SasLoginIntegrationTest.java`. No new files, no production code, matching the frozen brief exactly.

## Files to Create
None.

## Files to Modify

### `authn/SasLoginIntegrationTest.java`

**New fields:**
- `@Autowired private AuthClientsProperties authClientsProperties;` — SPA `client_id`/`redirect_uri`, read from config rather than hardcoded, so the test tracks reality if the client config ever changes.
- `@Autowired private ObjectMapper objectMapper;` — already a Spring Boot–provided bean; used to parse `/oauth2/token`'s JSON response body rather than hand-rolled string parsing.

**New private methods (signatures + behavior, no bodies):**

- `private PkcePair generatePkce()` — returns a new local record `PkcePair(String verifier, String challenge)`: `verifier` a random URL-safe string (same `SecureRandom` + `Base64.getUrlEncoder().withoutPadding()` shape already used elsewhere in this file for TOTP-adjacent randomness); `challenge` = `Base64.getUrlEncoder().withoutPadding().encodeToString(SHA-256(verifier))`, per PKCE S256.

- `private String authorizeUrl(String codeChallenge, String state)` — builds the full `/oauth2/authorize` query string via `UriComponentsBuilder` (proper encoding for `redirect_uri`): `response_type=code`, `client_id=<authClientsProperties.spa().clientId()>`, `redirect_uri=<authClientsProperties.spa().redirectUris().getFirst()>`, `scope=openid`, `state`, `code_challenge`, `code_challenge_method=S256`.

- `private ResponseEntity<String> getWithCookies(String url, List<String> cookies)` — a generic cookie-carrying GET, reused across every step of the flow that isn't already covered by the existing `attemptLogin`; factors out the `HttpHeaders` cookie-attaching logic already inline in `attemptLogin` so it isn't duplicated three times.

- `private static List<String> mergeCookies(List<String> existing, List<String> newSetCookies)` — combines cookies across a redirect chain (a session cookie minted at `/oauth2/authorize` must still be sent at `/login`, and `/login`'s own `Set-Cookie` — if any — must carry forward to the resumed `/oauth2/authorize`). Null-safe (either side may be empty on a given hop).

- `private record FullFlowResult(ResponseEntity<String> loginResponse, Optional<String> authorizationCode)` — the outcome of one full attempt. `authorizationCode` is empty for every failure case (R24, R25's negative branch) and present only when the flow actually completed.

- `private FullFlowResult attemptFullAuthorizeFlow(String email, String password, String mfaCode)` — the governing-mechanism implementation, in order:
  1. `generatePkce()`, random `state`.
  2. `GET authorizeUrl(...)`, unauthenticated — captures the `Set-Cookie` from this response (SAS's pending-authorization session) and the `Location` redirect to `/login`.
  3. `getWithCookies(baseUrl + "/login", ...)` — fetches the CSRF-bearing login page, reusing `extractCsrfToken` unchanged.
  4. Submits the login form (same shape `attemptLogin`'s 3-arg overload already builds — `username`/`password`/`_csrf`/optional `mfaCode` — but posted with the accumulated cookies from steps 2–3, not a fresh session).
  5. If the resulting `Location` is `/login?error` (or absent) → return `FullFlowResult` with `authorizationCode` empty. **Stop here for R24/R25's negative branch — this redirect *is* the proof, no further request is made or needed.**
  6. Otherwise (success) → `getWithCookies` on the post-login `Location` (the saved-request resume back into `/oauth2/authorize`, per `SavedRequestAwareAuthenticationSuccessHandler`), carrying forward the merged cookies. Extract `code` from *that* response's `Location` query string (the final redirect to the SPA's `redirect_uri`) — return it as `authorizationCode`.
  - **Contingency (frozen brief's governing correction):** if step 6's redirect doesn't land back on `/oauth2/authorize` as expected (observable once this runs), the fallback is issuing `GET authorizeUrl(...)` again directly with the post-login cookies, instead of trusting the saved-request replay. Implemented only if the primary path is empirically found not to work — not speculative code written up front.

- `private String exchangeCodeForToken(String code, String codeVerifier, String redirectUri)` — `POST /oauth2/token`, form-encoded, no `Authorization` header (public client): `grant_type=authorization_code`, `code`, `redirect_uri` (must equal what `authorizeUrl` used), `client_id`, `code_verifier`. Parses the JSON response via `objectMapper` and returns the `access_token` field.

- `private JWTClaimsSet parseClaims(String jwt)` — `com.nimbusds.jwt.JWTParser.parse(jwt).getJWTClaimsSet()`. Claims only, no signature verification (frozen brief, out of scope).

**New test methods (all in this file, per the frozen brief):**

- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization()` — MERCHANT, no confirmed enrollment (`ensureRoleExists`/`roleService.assignRole`, no `seedConfirmedTotpEnrollment` call). `attemptFullAuthorizeFlow(email, PASSWORD, null)` → assert `authorizationCode` empty, `loginResponse` redirects to `/login?error`.
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled()` — MERCHANT, confirmed enrollment. Password-only attempt → `authorizationCode` empty. Fresh-TOTP attempt (mirrors `attemptLoginWithFreshTotpCode`'s "generate immediately before use" discipline, applied to this method) → `authorizationCode` present.
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa()` — same seeding as above; takes the successful attempt's `authorizationCode`, calls `exchangeCodeForToken` with the *same* `PkcePair.verifier()` and `redirectUri` used to build that attempt's `authorizeUrl` call, parses claims, asserts `amr == ["pwd","otp"]` and `acr == "urn:themistra:acr:otp"`.
- `shouldIssueTokenWithPwdAmrThroughFullAuthorizeFlowWhenMfaNotRequired()` — no role assignment, no enrollment. Full flow succeeds on password alone; token exchange asserts `amr == ["pwd"]`, `acr == "urn:themistra:acr:pwd"`.

Since `attemptFullAuthorizeFlow` currently returns only the code (not the `PkcePair`/`redirectUri` used to obtain it), the two token-exchange tests generate the `PkcePair` and `state` *before* calling it and pass them in directly (adjusting `attemptFullAuthorizeFlow`'s signature to accept a caller-supplied `PkcePair` rather than generating its own) — this is what lets the same verifier reach `exchangeCodeForToken` afterward without threading extra state through the return type.

## Entities used
None directly — this task is HTTP-level only.

## Repositories used
None directly.

## Services used
`AccountService`, `RoleService`, `MfaService` — all existing, reused via this file's already-established `registerAndActivate`/`ensureRoleExists`/`seedConfirmedTotpEnrollment` helpers, unchanged.

## Unit tests required
None — this task is integration-only by its own statement ("Add Testcontainers tests").

## Integration tests required
The four listed above, all in `SasLoginIntegrationTest.java`, all Testcontainers-backed (Postgres + Kafka + real HTTP server via `RANDOM_PORT`), matching this file's existing pattern exactly.

## Execution order
1. `PkcePair` record + `generatePkce()` — pure utility, no dependencies on anything else new.
2. `authorizeUrl(...)` — depends on `authClientsProperties` (existing bean, just needs autowiring).
3. `getWithCookies(...)` / `mergeCookies(...)` — pure HTTP/cookie plumbing, no dependencies on 1–2.
4. `FullFlowResult` record + `attemptFullAuthorizeFlow(...)` — depends on 1–3 plus the existing `extractCsrfToken` and the existing 3-arg login-form-building logic (reused, not duplicated).
5. `exchangeCodeForToken(...)` — depends on `objectMapper` (autowiring) and nothing else new.
6. `parseClaims(...)` — depends only on the `nimbus-jose-jwt` dependency already confirmed present.
7. The four test methods — depend on everything above; written last since they're what exercises the fully-assembled mechanism.
8. Run against real Testcontainers (Docker confirmed working this session) — verify each test individually first (isolating any surprises in the saved-request redirect behavior the frozen brief's contingency plan anticipates) before running the whole class together.
