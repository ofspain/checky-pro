# auth · T25 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T25 — API-key exchange endpoint |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/06-implementation-notes.md`, `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the completed implementation, with fresh eyes. Findings only — no code changes made in this phase.

---

## Findings

### 1. `JwtEncoder` bean will throw on every token issuance whenever CURRENT + PREVIOUS keys are both configured

- **Issue:** The new `jwtEncoder` bean in `token/JwksConfig.java` returns a bare `new NimbusJwtEncoder(jwkSource)` without calling `setJwkSelector(...)`. `NimbusJwtEncoder`'s default selector throws `JwtEncodingException` whenever more than one JWK matches the default RS256 header.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/token/JwksConfig.java:47-48` — `return new NimbusJwtEncoder(jwkSource);` with no selector.
  - Spring Security OAuth2 JOSE 6.5.2 source for `NimbusJwtEncoder` confirms the default `jwkSelector` field initializer throws `"Failed to select a key since there are multiple for the signing algorithm..."` when `jwks.size() > 1`.
  - `SigningKeyMaterial.fromPem(...)` never calls `.keyUse(...)` or `.algorithm(...)` on the `RSAKey.Builder`, so both CURRENT and PREVIOUS keys match the matcher built by `NimbusJwtEncoder.createJwkMatcher` (it accepts `keyUses(SIGNATURE, null)` and `algorithms(RS256, null)`).
  - Result: as soon as a deployment configures a previous key (normal rotation state), every token this service issues — SAS grants *and* API-key exchanges — fails with a 500.
- **Recommendation:** In `JwksConfig.jwtEncoder`, call `encoder.setJwkSelector(List::getFirst)` (or equivalent) before returning it, so the encoder honors `SigningKeyMaterial`'s documented CURRENT-key-first ordering. One line, confined to a file T25 already modifies. This also fixes the same latent bug in SAS's existing grant paths because SAS will reuse this bean.
- **Confidence:** High — verified against the actual 6.5.2 source.

---

### 2. Upper TTL bound of 525,600 minutes (one year) is inappropriate for a repeatedly reissued bearer token

- **Issue:** `ApiKeyProperties.tokenTtlMinutes` is bounded by `@Max(525_600)`, copied from `VerificationTokenProperties`. Verification tokens are single-use, emailed links; a long TTL there is low-risk. API-key access tokens are repeatedly reissued bearer credentials, and `agents.md` / L8 expect a 10-minute TTL. An operator typo such as `1000` instead of `10` would silently mint ~17-hour bearer tokens.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyProperties.java:20` — `@Min(1) @Max(525_600) long tokenTtlMinutes`.
- **Recommendation:** Reduce the upper bound to a value that still covers any reasonable operational need but fails absurd configs (e.g., `@Max(1440)` for 24 hours, or `@Max(180)` for 3 hours). The exact value is a Phase 9 judgment call, but the current bound is a different risk class than the precedent it was copied from.
- **Confidence:** High.

---

### 3. CSRF configuration may block anonymous programmatic POSTs to `/api-keys/token`

- **Issue:** The application security chain enables CSRF and only ignores `/api/**` (`SecurityChainsConfig.java:70`). `/api-keys/token` is not under `/api/**`, so a machine client POSTing `Authorization: ApiKey ...` with no session cookie may receive a 403 from `CsrfFilter` before reaching the controller. This contradicts AC1 / Required Test 7 ("reachable anonymously through the real filter chain") for a stateless machine endpoint.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java:70` — `.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))`.
  - `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java:47-55` — `POST /api-keys/token` with no CSRF token handling.
  - `SecurityChainsConfig` is frozen in the T25 brief, so the endpoint cannot be added to the ignore list without a gate decision.
- **Recommendation:** Either (a) seek a gate exception to add `/api-keys/token` to the CSRF ignore matchers in `SecurityChainsConfig`, or (b) ensure Required Test 7 explicitly obtains and sends a CSRF token (which is awkward for a machine-client endpoint). Verify behavior with a real `TestRestTemplate` POST before Phase 10 is finalized.
- **Confidence:** Medium — depends on exact Spring Security CsrfFilter behavior for anonymous/session-less requests; worth a runtime check because the path pattern strongly suggests `/api/**` was meant to cover machine endpoints and `/api-keys` was overlooked.

---

### 4. `ApiKeyTokenIssuer.issue` can throw `NullPointerException` if `ExchangeResult.accountUuid()` is null

- **Issue:** `ApiKeyTokenIssuer.issue` calls `accountUuid.toString()` without a null check. `ApiKeyService.exchange` populates the success `accountUuid` via `resolveAccountUuidQuietly`, a method documented as best-effort that can return `null` if the account row is missing ("should not happen in practice"). A null success result would surface as a 500, which is the correct externally-observable outcome, but via an NPE rather than a meaningful failure.
- **Evidence:**
  - `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyTokenIssuer.java:68` — `.subject(accountUuid.toString())`.
  - `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyService.java:197` — `UUID accountUuid = resolveAccountUuidQuietly(matched.getAccountId());` and `return new ExchangeResult(accountUuid, matched.getScopes());`.
- **Recommendation:** Add an explicit null guard in `ApiKeyTokenIssuer.issue` that throws a dedicated `IllegalStateException` ("exchanged key has no resolvable account") so the failure is intentional rather than accidental. `ApiKeyService` is frozen, so the guard belongs in the issuer. Severity is low because the outcome is already a 500.
- **Confidence:** High — pre-existing invariant, not introduced by T25, but the issuer is a new consumer of the nullable value.

---

### 5. Credential extraction does not trim whitespace, so a well-formed key with leading/trailing spaces is rejected as a hash mismatch

- **Issue:** `ApiKeyController.extractCredential` extracts the substring after the space verbatim and does not trim it. An `Authorization: ApiKey  ck_live_...` header (two spaces, or a space after the credential) reaches `ApiKeyService.exchange` with whitespace included, causing a hash mismatch. RFC 7235 allows optional whitespace around the credentials.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyController.java:79-83` — `credential = authorization.substring(separator + 1);` then length/blank checks, no `trim()`.
- **Recommendation:** Trim the credential before the length and blank checks. A valid `ck_live_...` key contains no internal whitespace, so trimming cannot create collisions. This makes the endpoint more tolerant of compliant HTTP clients without weakening security.
- **Confidence:** High — code behavior is clear; risk is low but real for hand-written client headers.

---

### 6. `aud` claim mirrors `client_id` (`checky-api-key`) despite different semantics

- **Issue:** `aud` is meant to identify the resource server(s) that should accept the token, while `client_id` identifies the OAuth2 client. Using the same synthetic literal for both is technically valid JSON but conflates two distinct claims and may break resource servers that validate `aud` against their own audience.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/apikey/ApiKeyTokenIssuer.java:35` and `:69` / `:76` — `CLIENT_ID` used for both `audience` and `client_id`.
- **Recommendation:** Ratify at the Phase 9 gate. If the platform intends API-key tokens to be accepted by all Themistra resource servers, consider `aud = ["checky-services"]` or similar; if audience validation is not used, document that `aud` is a placeholder matching `client_id`. Not a code change by itself.
- **Confidence:** High that this is a design question; Low that it causes immediate failure — depends on downstream resource-server configuration.

---

### 7. Bearer-schemed API-key requests still bypass the uniform problem body

- **Issue:** A request with `Authorization: Bearer <ck_live_...>` is intercepted by `BearerTokenAuthenticationFilter` on the `@Order(2)` chain and rejected with a filter-level 401 (`WWW-Authenticate: Bearer error=...`, no JSON body) before the controller runs. This is the same residual the Phase 7 self-review already documented.
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java:71` and the D4 analysis in `artifacts/04-frozen-task-brief.md`.
- **Recommendation:** No code change required — the scheme is locked to `ApiKey` and `Bearer` is intentionally not accepted. Ensure Required Test #8 asserts status-only equivalence for the `Bearer` case rather than a byte-identical `application/problem+json` body, and document this residual in the test Javadoc so a future maintainer does not spend time chasing it.
- **Confidence:** High — already disclosed; restated for Phase 10 traceability.

---

### 8. `SasLoginIntegrationTest` regression check for D1 was not executed

- **Issue:** The Phase 6 build verification notes that Docker was down, so all Testcontainers-backed tests (including `SasLoginIntegrationTest`, the designated D1 no-disturbance check) failed on context startup rather than on test logic. The new `JwtEncoder` bean is intended to be behaviorally identical to SAS's fallback, but this was not exercised.
- **Evidence:** `artifacts/06-implementation-notes.md:73-84`.
- **Recommendation:** Before Phase 9 approval, run `SasLoginIntegrationTest` and the full `mvn -pl services/auth verify` suite with Docker available. Once Finding #1 is fixed, this also becomes the regression check that SAS grants still work with the explicit encoder.
- **Confidence:** High — not a code defect, but a verification gap.

---

## Non-Issues Confirmed

- **Header parsing null-safety / bounds:** `extractCredential` guards every substring with preceding `indexOf(' ')` checks; no `IndexOutOfBoundsException` or NPE is reachable.
- **No secret leakage:** no path logs or echoes the presented credential, its hash, or an email.
- **Module boundaries:** no `apikey` class imports a foreign entity; the `RoleService` import is a service class, precedented by `TokenClaimsCustomizer`. The Javadoc `{@link PublicEndpoints}` in `ApiKeyController` is not a bytecode dependency.
- **Thread-safety:** `ApiKeyController`, `ApiKeyTokenIssuer`, and `ApiKeyExceptionHandler` hold only `final` fields and no mutable state.
- **Transaction ordering (D5):** controller calls `ApiKeyService.exchange` then `ApiKeyTokenIssuer.issue`, matching the frozen decision.
- **Uniform 401 body:** `ApiKeyExceptionHandler` returns a fixed `ProblemDetail` with no varying `detail` for every rejection cause.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (human gate) on approval.
