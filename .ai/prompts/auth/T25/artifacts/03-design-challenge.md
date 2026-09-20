# auth · T25 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T25 — API-key exchange endpoint |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |
| **Status** | Findings for Phase 4 human gate |

## Summary

The brief is internally consistent for the parts it controls directly (T24-frozen service behavior, `PublicEndpoints` registration, RFC 9457 problem-type mapping), but it leaves four load-bearing design blockers unresolved and treats several T25-specific decisions as "Phase 5" concerns even though they determine whether the acceptance criteria are testable and secure. The most serious gap is that **L8's mandate to issue via the existing `TokenClaimsCustomizer` path is currently impossible**: no standalone `JwtEncoder` bean exists, and the customizer's logic is bound to a Spring Authorization Server `JwtEncodingContext` that cannot be synthesized for an API-key grant.

Below are findings only; accepted amendments should be folded into the Phase 2 brief in Phase 4.

---

## Findings

### 1. No `JwtEncoder` bean exists to satisfy L8's "existing TokenClaimsCustomizer path"

- **Severity:** Blocker
- **Evidence:** `services/auth/src/main/java/com/themistra/auth/token/JwksConfig.java` declares only `JWKSource<SecurityContext>` and `JwtDecoder`. `services/auth/src/main/java/com/themistra/auth/token/TokenClaimsCustomizer.java` is an `OAuth2TokenCustomizer<JwtEncodingContext>` and is invoked only by Spring Authorization Server's internal `JwtEncoder`. No `@Bean JwtEncoder` is declared in the codebase; the SAS `JwtEncoder` is not exposed for direct injection. The brief acknowledges this as OQ-1 but still lists L8 as a LOCKED decision that must be implemented exactly.
- **Recommended brief amendment:** Either (a) add an explicit implementation task to declare a standalone `JwtEncoder` bean in `token/` that consumes `JWKSource`, or (b) amend L8 to permit a direct Nimbus JWS signing path inside `ApiKeyTokenIssuer` and require that the resulting claim set still conforms to L9. Do not leave "reuse TokenClaimsCustomizer" as a locked requirement while the infrastructure to do so is absent.

### 2. `TokenClaimsCustomizer` cannot be reused as-is for an API-key JWT

- **Severity:** Blocker (consequence of #1)
- **Evidence:** `TokenClaimsCustomizer.customizeAccessToken` branches on `context.getAuthorizationGrantType()` and `context.getPrincipal().getAuthorities()`. An API-key exchange has no SAS authorization grant, no `Authentication` principal, and no `JwtEncodingContext`. The customizer also derives `email_verified` from `context.getAuthorizedScopes().contains(OidcScopes.EMAIL)`, which has no meaning for API keys. Therefore, even if a `JwtEncoder` existed, the customizer's current logic would not produce the required `amr`, `acr`, `roles`, or `email_verified` values.
- **Recommended brief amendment:** Add an explicit decision that `ApiKeyTokenIssuer` is responsible for assembling the full claim set (including `roles` resolved from `RoleService`) and that `TokenClaimsCustomizer` will be updated in a later task to include an `api_key` branch, OR amend L8 to state that the API-key path does not go through `TokenClaimsCustomizer`.

### 3. No `client_id` value is defined for the API-key JWT

- **Severity:** Blocker
- **Evidence:** L8 and L9 both require a `client_id` claim. `services/auth/src/main/java/com/themistra/auth/token/RegisteredClientSeeder.java` seeds only `checky-spa`, `payment-service`, `notification-service`, and `crypto-service`; none represent an API-key holder. `TokenClaimsCustomizer` does not set `client_id` today for any grant type. The brief flags this as OQ-2 but does not resolve it.
- **Recommended brief amendment:** Lock a `client_id` value (e.g., a new registered client `checky-api-key`, or reuse of the SPA client) and update AC8 to assert it. If a new registered client is introduced, add it to the seeder and to the required test assertions.

### 4. No `acr` value is defined for the API-key credential

- **Severity:** Blocker
- **Evidence:** L9 requires `acr` in the exact access-token claim set. L8 requires `amr` to contain `api_key` but is silent on `acr`. The existing `TokenClaimsCustomizer` emits `urn:themistra:acr:pwd` or `urn:themistra:acr:otp` only for interactive grants. No ACR URI is defined for API-key authentication. The brief flags this as OQ-3.
- **Recommended brief amendment:** Add a LOCKED decision defining the API-key ACR, e.g., `urn:themistra:acr:api_key`, and update AC6/AC8 to assert it. If `acr` must be omitted for API keys, L9 must be amended because it currently lists `acr` as mandatory.

### 5. `Authorization` header scheme is unspecified and creates a filter-chain hazard

- **Severity:** Blocker
- **Evidence:** The brief states (OQ-6) that the header scheme is "not fixed by the spec" and notes that on the `@Order(2)` chain a `Bearer`-schemed value will be picked up by the resource-server JWT filter and decoded as a JWT before any handler runs. `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` applies `.oauth2ResourceServer(rs -> rs.jwt(...))` to the application chain. If the endpoint accepts `Authorization: Bearer <api-key>`, the JWT filter will attempt to parse the raw API key as a JWT and reject it with its own 401, bypassing the controller and the uniform problem-body mapping.
- **Recommended brief amendment:** Lock the header scheme. The safe choices are (a) a custom scheme such as `ApiKey ck_live_...`, or (b) `Basic` with the key as the password. Add the chosen scheme to AC1/AC10 and to the contract tests. Reject `Bearer` explicitly.

### 6. Transaction ordering between `last_used_at` update and JWT signing is unspecified

- **Severity:** High
- **Evidence:** `ApiKeyService.exchange` is `@Transactional` and calls `apiKeyRepository.updateLastUsedAt(...)` inside that transaction. The brief says JWT minting "must not widen or nest that transaction" and warns that a signing failure must not commit a `last_used_at` update unmatched by an issued token, but defers the ordering to Phase 5. However, the decision determines whether the controller calls `exchange()` then `issuer.issue()` (success: `last_used_at` committed even if signing later fails) or whether `exchange()` returns an `ExchangeResult` and the controller mints the JWT before the transaction commits. The brief says `ApiKeyService.exchange` is T24-frozen and must be consumed as-is, but T25 cannot satisfy AC3 + AC9 + the signing-failure constraint without deciding this ordering.
- **Recommended brief amendment:** Lock the ordering. Recommended: `ApiKeyTokenIssuer.issue(...)` is invoked from within `ApiKeyService.exchange` (or a new `@Transactional` wrapper in `apikey`) **after** `updateLastUsedAt` but before the transaction commits, so a signing failure rolls back both. Document that this is a T25 design decision, not a T24 modification.

### 7. Success path performs a DB `UPDATE` that failure paths skip, creating a timing side-channel

- **Severity:** High
- **Evidence:** `ApiKeyService.exchange` performs `updateLastUsedAt` only on the success path. The brief correctly requires "no timing-revealing early return added on top of exchange's deliberately non-short-circuiting comparison," but the success-only `UPDATE` itself introduces a measurable timing difference between valid and invalid keys. This is visible to an attacker with sufficient precision and directly contradicts R33's goal of byte-identical rejection.
- **Recommended brief amendment:** Either (a) accept the timing leak as a known limitation and document it, or (b) require that the success path perform a fixed amount of post-validation work so that success and failure timings overlap within an acceptable window. Do not claim the 401 is "byte-identical for every rejection cause" while silently allowing a timing oracle.

### 8. No rate-limiting or brute-force defense is specified for the public exchange endpoint

- **Severity:** Medium-High
- **Evidence:** R41 lists login, `/oauth2/token`, password-reset confirm, and MFA verify for per-account rate limiting, but omits `/api-keys/token`. The endpoint is public, unauthenticated, and accepts a high-entropy secret. While guessing a valid key is hard, an attacker can still (a) probe for revoked/expired keys, (b) consume compute/DB resources via constant-time hash comparisons, and (c) exfiltrate metadata through timing. The brief's security constraints mention "treat every input as hostile" but specify no rate limit.
- **Recommended brief amendment:** Add a per-source-IP or global rate limit for `POST /api-keys/token`, or at minimum require that the implementation log a security metric and cap the maximum `Authorization` header length. Update the acceptance criteria or constraints accordingly.

### 9. Maximum `Authorization` header length is not specified

- **Severity:** Medium
- **Evidence:** L7 defines the key as 32 (prefix) + 1 (dot) + 32 (secret) = 65 characters for `ck_live_...`. The brief says "treat every input as hostile and bound its size before parsing," but no bound is locked. A malicious client could send a multi-megabyte header, causing memory pressure or DoS before parsing rejects it.
- **Recommended brief amendment:** Lock a maximum header length (e.g., 128 or 256 bytes) and reject anything longer with the same uniform 401. Add a boundary test.

### 10. `roles` resolution contradicts the "no extra account lookups" performance constraint

- **Severity:** Medium
- **Evidence:** The brief requires `roles` in the JWT (L8/AC8) and depends on `authz: RoleService.resolveEffectiveRoles(UUID)`. `ExchangeResult` already provides `accountUuid`, so calling `RoleService` is an additional query per exchange. The performance constraint says "no extra account lookups," but `resolveEffectiveRoles` is exactly that. For a hot machine-client path, this matters.
- **Recommended brief amendment:** Either (a) remove the "no extra account lookups" constraint as unrealistic for the `roles` requirement, (b) cache effective roles at key creation time and store them on the `api_keys` row, or (c) make `roles` optional for API-key JWTs. Do not leave a performance constraint that conflicts with a locked claim requirement.

### 11. `EventTopics` mapping for aggregate `"api-key"` routes to `auth.email.requested`

- **Severity:** Medium
- **Evidence:** `spec/auth-service/design.md` §4c lists `Map.of(..., "api-key", "auth.email.requested")`. API-key events (`api_key.created`, `api_key.revoked`, `api_key.exchanged`, `api_key.exchange_failed`) are not email-requested events. The brief correctly states T25 emits no new aggregate type because audit events use the `"audit"` aggregate, but the verbatim artifact in `design.md` remains inconsistent.
- **Recommended brief amendment:** Remove the `"api-key"` → `auth.email.requested` mapping from the VERBATIM artifact, or explicitly state that API-key lifecycle events are published only through the `audit` aggregate and that the `api-key` mapping is reserved for future email notifications (if any). This is a spec issue, but the brief should not repeat the mapping without flagging it.

### 12. `scope` claim format is ambiguous for resource-server compatibility

- **Severity:** Medium
- **Evidence:** `ExchangeResult` carries `List<String> scopes`. The brief says "`scope` contains `merchant.api`". Spring Authorization Server emits `scope` as a space-delimited string by default. Resource servers typically expect space-delimited scopes. If `ApiKeyTokenIssuer` writes a JSON array, downstream validation may break.
- **Recommended brief amendment:** Lock the `scope` claim format (space-delimited string per RFC 6749) and add an assertion in `shouldExchangeValidApiKeyForMerchantJwt`.

### 13. Token response envelope is not specified

- **Severity:** Medium
- **Evidence:** The brief says the 200 response carries "the signed JWT, its token type, and its expiry" but does not specify field names. OAuth2 clients expect `access_token`, `token_type`, `expires_in`. The named test will assert on decoded JWT claims but not on the envelope unless specified.
- **Recommended brief amendment:** Lock the response DTO fields (`access_token`, `token_type` = "Bearer", `expires_in` seconds) and update `ApiKeyTokenResponse.java` and the named test accordingly.

### 14. Spec `package.md` maps the named tests to wrong requirement IDs

- **Severity:** Low
- **Evidence:** `spec/auth-service/package.md` §8 maps `shouldExchangeValidApiKeyForMerchantJwt` → R28 and `shouldRejectRevokedOrUnknownApiKeyWithUniform401` → R29, which are TOTP MFA requirements. The brief correctly proceeds on R31–R33 but cannot modify `spec/`.
- **Recommended brief amendment:** Since `spec/` is immutable for this task, add a non-blocking note that the named tests will be traced to R31–R33 in the implementation's test Javadoc and that no code-level mapping to R28/R29 should be introduced. Ensure the verification checklist in the brief references R31–R33, not R28/R29.

### 15. `ApiKeyService.exchange` audit target for unidentifiable keys uses `null` account UUID

- **Severity:** Low
- **Evidence:** For malformed keys and unknown prefixes, `exchange` records `api_key.exchange_failed` with `null` actor/target. The brief says `AuditService` supports this, but the outbox mirror will use a random partition key and the security-audit event will lack a correlation anchor.
- **Recommended brief amendment:** Accept the null target for unidentifiable keys as an enumeration-safety trade-off, but add a test assertion verifying that the outbox still receives exactly one `auth.security.audit` row per attempt (already in required tests 8), and ensure the audit schema tolerates a null `accountUuid`.

---

## Non-findings (deliberately not raised)

- **Account-status gap (suspended merchant keys remain valid).** The brief explicitly documents this as out of scope and adds a Javadoc note in `ApiKeyService.exchange`. No challenge.
- **DDL / migration scope.** L1 and the brief correctly state no schema change. No challenge.
- **Cross-module entity imports.** The T24 implementation already avoids importing `Account` in `apikey`. No challenge.
