# auth · T25 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T25 — API-key exchange endpoint |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the Phase 10 test suite against the frozen brief (`artifacts/04-frozen-task-brief.md`) and acceptance criteria AC1–AC15. Findings only — no test code changed in this phase.

---

## Gaps

### 1. No test exercises the `JwtEncoder` `jwkSelector` fix with multiple signing keys

- **Gap:** Every `ApiKeyTokenIssuerTest` builds a `JWKSource` with exactly one RSA key, and `ApiKeyExchangeIntegrationTest` will run with whatever single-key local dev config the test profile loads. The Phase 9 fix that added `encoder.setJwkSelector(List::getFirst)` to `JwksConfig.jwtEncoder` is therefore untested.
- **Why it matters:** This was the highest-severity finding from both Phase 7 and Phase 8. During any key-rotation window (CURRENT + PREVIOUS both configured), the encoder throws without the selector and signs with the wrong key with a bad selector. A regression here breaks every token the service issues, not just API-key tokens, and would only surface in a multi-key deployment or integration test.
- **Suggested test:** Add a focused plain-JUnit test (or extend `ApiKeyTokenIssuerTest`) that constructs a `NimbusJwtEncoder` from a `JWKSource` containing two RSA keys and verifies that encoding succeeds and the resulting JWT header `kid` equals the first key's `kid`. This directly exercises the selector behavior the Phase 9 fix introduced.

---

### 2. No test verifies the tightened `@Max(1440)` TTL bound

- **Gap:** `ApiKeyProperties.tokenTtlMinutes` was lowered from `@Max(525_600)` to `@Max(1440)` in Phase 9, but none of the new tests exercise the boundary. The unit tests use 10, 45, and 90 minutes, all well inside the bound.
- **Why it matters:** The bound is the defense against operator typos that would silently mint long-lived bearer tokens. Without a test, the annotation could be removed or reverted to `525_600` without CI noticing.
- **Suggested test:** Add a small property-validation test (mirroring `VerificationTokenPropertiesTest` / `PasswordPolicyPropertiesTest`) that constructs `ApiKeyProperties` with `1441` and asserts a `ConstraintViolation`. Also assert that `1440` is accepted.

---

### 3. `ApiKeyControllerTest` only exercises leading whitespace, not trailing whitespace, after the scheme

- **Gap:** The Phase 9 fix trims both leading and trailing whitespace around the credential, but `extraWhitespaceAroundCredentialIsTrimmed` only sends `"ApiKey  ck_live_x.y"` (extra leading space). A credential with a trailing space (`"ApiKey ck_live_x.y "`) is not covered.
- **Why it matters:** An HTTP client following RFC 7235 could add trailing whitespace; the fix is intended to tolerate incidental whitespace on both sides. Testing only one side leaves half the fix unproven.
- **Suggested test:** Add a trailing-space case to `extraWhitespaceAroundCredentialIsTrimmed` (e.g., `"ApiKey ck_live_x.y "`) and assert the credential passed to `apiKeyService.exchange` is the trimmed value.

---

### 4. The named integration test does not assert all L9-mandated claims

- **Gap:** `ApiKeyExchangeIntegrationTest.shouldExchangeValidApiKeyForMerchantJwt` asserts `sub`, `scope`, `amr`, `acr`, `roles`, `client_id`, `email_verified`, `exp`, and `iat`, plus the absence of `email`/`name`. It does not assert `iss`, `aud`, `nbf`, or `jti`.
- **Why it matters:** L9 locks the exact claim set as `iss, sub, aud, exp, iat, nbf, jti, scope, roles, client_id, amr, acr, email_verified`. The unit test covers these, but the named end-to-end test is the one that proves the real filter chain + real issuer produces them.
- **Suggested test:** Add assertions in `shouldExchangeValidApiKeyForMerchantJwt` for `claims.getIssuer()`, `claims.getAudience()`, `claims.getNotBeforeTime()`, and `claims.getJWTID()`. The issuer value can be read from `@LocalServerPort` + `.well-known` or simply asserted to be a non-empty URI.

---

### 5. No integration test asserts the response envelope serializes with snake_case field names

- **Gap:** `ApiKeyControllerTest.exchangeReturnsTokenResponseForAValidHeader` uses the record's Java accessors (`accessToken()`, `tokenType()`, `expiresIn()`) and never serializes the DTO. `ApiKeyExchangeIntegrationTest` reads the JSON fields, so it indirectly verifies snake_case, but if the integration test fails for unrelated reasons (Docker), nothing else guards the `@JsonProperty` annotations.
- **Why it matters:** The response envelope is the first DTO in the service to require `@JsonProperty` because it follows OAuth2 snake_case. A missing or mistyped annotation would serialize as camelCase and break OAuth2 clients.
- **Suggested test:** Add a plain unit test in `ApiKeyControllerTest` that serializes `ApiKeyTokenResponse.of("x", 600)` with `ObjectMapper` and asserts the JSON keys are exactly `access_token`, `token_type`, and `expires_in`.

---

### 6. `last_used_at` HTTP-layer test covers only one rejection cause and on a different key

- **Gap:** `lastUsedAtWrittenOnSuccessNeverOnRejection` asserts a wrong-secret attempt on a *decoy* key leaves the decoy's `last_used_at` null, then succeeds with the real key. It does not assert that the real key's `last_used_at` stays null when the same key is rejected for revoked/unknown-prefix/malformed/expired/wrong-secret, nor does it cover the other rejection paths.
- **Why it matters:** R32 / AC9 require `last_used_at` is updated on success and on *no* failure path. The current test only proves the property for a wrong-secret rejection against a different key.
- **Suggested test:** Expand `lastUsedAtWrittenOnSuccessNeverOnRejection` (or add a new test) that records the real key's `last_used_at` before and after each of revoked/unknown-prefix/malformed/wrong-secret/expired rejections, asserting it remains null, then succeeds and asserts it becomes non-null.

---

### 7. Integration test asserts `scope` contains `merchant.api`, not that it is exactly `["merchant.api"]`

- **Gap:** `shouldExchangeValidApiKeyForMerchantJwt` uses `contains("merchant.api")` for the scope claim. The unit test `issueEchoesScopesVerbatimAsAJsonArray` uses `containsExactly`, but the named end-to-end test does not.
- **Why it matters:** D1/Kimi#12 explicitly locks `scope` as the key row's scopes echoed verbatim as a JSON array, never widened. `contains` would pass if extra scopes were accidentally added.
- **Suggested test:** Change the assertion in `shouldExchangeValidApiKeyForMerchantJwt` to `containsExactly("merchant.api")`.

---

### 8. No test asserts the exact claim-set keys (no extra claims beyond L9)

- **Gap:** Tests check expected claims are present and that `email`/`name` are absent, but none assert that the JWT contains *only* the L9 claim set. An extra claim such as `azp`, `auth_time`, or an internal ID would slip through.
- **Why it matters:** L9 says the access-token claims are *exactly* the listed set. The unit test checks individual claims; the named test should enforce the exact set against the real issuer.
- **Suggested test:** In `ApiKeyTokenIssuerTest.issueProducesTheFullL9ClaimSet` (and optionally in the integration named test), assert `claims.getClaims().keySet()` equals exactly `{iss, sub, aud, exp, iat, nbf, jti, scope, roles, client_id, amr, acr, email_verified}`.

---

### 9. Response-envelope leak test does not check for internal `keyUuid` leakage

- **Gap:** `responseEnvelopeHasExactlyTheThreeExpectedFieldsAndNoSecretMaterial` asserts the plaintext key and email address are not in the body, but does not assert the key's internal `keyUuid` is absent.
- **Why it matters:** AC11 forbids "an internal id" in the response. The `keyUuid` is the internal handle for the key and should never be echoed.
- **Suggested test:** Assert `response.getBody()` does not contain `created.keyUuid().toString()` in `responseEnvelopeHasExactlyTheThreeExpectedFieldsAndNoSecretMaterial`.

---

### 10. `ApiKeyExchangeIntegrationTest` has not been executed

- **Gap:** The Phase 10 notes state Docker was unavailable, so the full-stack tests — including both named tests, the audit assertions, and the filter-chain regression — were compiled but not run.
- **Why it matters:** These tests cover AC1, AC3–AC12, and the Phase 9 CSRF / `jwkSelector` fixes. Compilation is not execution; logical errors in native-query parameter binding, Testcontainers wiring, or filter-chain interaction will only surface at runtime.
- **Suggested test:** No new test needed — run `ApiKeyExchangeIntegrationTest` (and `SasLoginIntegrationTest` for the SAS-grant regression) with a working Docker daemon before the Phase 12 final verification gate.

---

### 11. No test verifies a signing failure yields 500 at the HTTP layer

- **Gap:** `ApiKeyControllerTest.signingFailurePropagatesUncaught` proves the controller lets an `IllegalStateException` propagate, but no test simulates a real `JwtEncodingException` and asserts the HTTP response is a 500 with the opaque `ApiExceptionHandler` body rather than the uniform 401.
- **Why it matters:** AC15 / D5 state that a signing failure must surface as 500, never 401. The unit test only proves the controller doesn't swallow the exception.
- **Suggested test:** Low priority — acceptable to leave as a unit-level assertion because signing failures require broken key material, which is hard to simulate in an integration test without bean overriding. If coverage is desired, add a focused test that overrides the `JwtEncoder` bean with one that always throws and asserts 500 via `TestRestTemplate`.

---

## Non-Gaps (Confirmed Coverage)

- **Named test `shouldRejectRevokedOrUnknownApiKeyWithUniform401` duplicated at service and HTTP layers:** intentional and documented; the service-layer test in `ApiKeyServiceIntegrationTest` (T24) proves `ApiKeyService.exchange` uniformity, while the HTTP-layer test proves the controller + advice + filter chain preserve it.
- **Bearer-schemed request treated differently from other rejections:** already correctly tested in `reachableAnonymouslyAndApiKeySchemeAvoidsTheBearerFilter` with a status-only assertion and explanatory Javadoc.
- **CSRF fix:** `reachableAnonymouslyAndApiKeySchemeAvoidsTheBearerFilter` implicitly proves the Phase 9 CSRF exception works because the test uses a session-less `TestRestTemplate` POST that would 403 if CSRF were still enforced.
- **`ApiKeyExceptionHandler` byte-identical body:** covered by `ApiKeyExceptionHandlerTest.onExchangeRejectedResponseIsIdenticalRegardlessOfConstructionSite`.

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Final Verification) on approval.
