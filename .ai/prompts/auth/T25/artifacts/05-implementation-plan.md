<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T25 · Phase 5 — Implementation Plan

Consumes `artifacts/04-frozen-task-brief.md` (FROZEN, approved by femi 2026-08-15). No code written in this phase.

---

## Files to Create

### 1. `apikey/dto/ApiKeyTokenResponse.java`
Response envelope (frozen brief Outputs table).
```java
public record ApiKeyTokenResponse(String accessToken, String tokenType, long expiresIn)
```
- Jackson field names `access_token` / `token_type` / `expires_in` via `@JsonProperty` (matches this module's other DTOs — no naming strategy is configured globally, confirmed by `RegistrationAcknowledgement`/`AccountResponse` using explicit `@JsonProperty` where snake_case is required).
- `tokenType` is always the literal `"Bearer"` — a static factory `ApiKeyTokenResponse.of(String accessToken, long expiresInSeconds)` fixes it, so no call site can pass the wrong constant.

### 2. `apikey/ApiKeyTokenIssuer.java`
Assembles and signs the token (D1). Singleton, no mutable state (Constraints: Thread-safety).

**Public methods:**
- `ApiKeyTokenIssuer(JwtEncoder jwtEncoder, RoleService roleService, ApiKeyProperties apiKeyProperties — for issuer? NO: issuer comes from `AuthorizationServerSettings`/property, not ApiKeyProperties, ApiKeyTokenProperties instead, Clock clock)`
- `IssuedToken issue(UUID accountUuid, List<String> scopes)` → `record IssuedToken(String accessToken, long expiresInSeconds)`

**Private methods:**
- `JwtClaimsSet buildClaims(UUID accountUuid, List<String> scopes, Instant now, Instant expiry)` — assembles the exact L9 claim set: `iss` (injected `String issuer`, bound from `spring.security.oauth2.authorizationserver.issuer` — the same property `SecurityChainsConfig`'s SAS instance already reads, injected here via `@Value`), `sub` = `accountUuid.toString()`, `aud` = `"checky-api-key"` (see Open Question below), `iat`/`nbf` = now, `exp` = expiry, `jti` = fresh `UUID.randomUUID()`, `scope` = the passed-in scopes list (JSON array, D1/Kimi#12), `roles` = `roleService.resolveEffectiveRoles(accountUuid)`, `client_id` = literal `"checky-api-key"` (D2), `amr` = `List.of("api_key")`, `acr` = `"urn:themistra:acr:api_key"` (D3), `email_verified` = `false` (no email context exists for an API-key exchange; L9 requires the claim be present, R48 forbids PII beyond it — `false` is the only value with no email dependency).

**Entities/Services used:** `RoleService.resolveEffectiveRoles(UUID)`, `JwtEncoder.encode(JwtEncoderParameters)`, `Clock`.

**Open Question (non-blocking, flagged for Phase 6 confirmation rather than silently decided):** L9 requires an `aud` claim, but the frozen brief's Outputs table doesn't fix its value for this token the way D2 fixes `client_id`. `JwtGenerator` sets `aud` = the OAuth client's `clientId` for every other grant; T25 has no `RegisteredClient` (D2 declines to seed one), so there's no directly analogous value. This plan defaults `aud` to the same literal `checky-api-key` as `client_id` — self-consistent (the token's audience is the client it was issued to) and needs no new config key. This is a one-line filler for an L9-mandated claim the brief didn't spell out, not one of the brief's actual blockers (D1–D5) — proceeding on it now, to be confirmed rather than relitigated at Phase 6.

### 3. `apikey/ApiKeyExceptionHandler.java`
Module-scoped `@RestControllerAdvice`, mirrors `AccountExceptionHandler`'s shape exactly.

- `@ExceptionHandler(ApiKeyExchangeRejectedException.class) ProblemDetail onExchangeRejected(...)` → 401, `ProblemTypes.API_KEY_EXCHANGE_REJECTED` (new constant), title fixed, **no `setDetail`** — R46 forbids any varying detail, unlike `AccountExceptionHandler`'s `INVALID_TOKEN` mapping which also carries no detail (same shape, correct precedent).

### 4. `apikey/ApiKeyController.java`
Public controller (frozen brief explicitly chooses this over the filter alternative).

```java
@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {
    ApiKeyController(ApiKeyService apiKeyService, ApiKeyTokenIssuer apiKeyTokenIssuer)

    @PostMapping("/token")
    ApiKeyTokenResponse exchange(@RequestHeader(value = "Authorization", required = false) String authorization)
}
```

**Private/inline logic (header parsing — a small private helper `extractCredential(String authorization)`):**
- **Single audit path, by design:** every rejection cause (missing header, wrong scheme, blank credential, over-length credential, malformed key, unknown prefix, hash mismatch, revoked, expired) must produce exactly one audit row (AC12) and one identical 401 body (AC10). `ApiKeyService.exchange(String)` already handles `null` input by taking its audited "malformed" branch (`presentedKey == null ? -1 : ...`). So the controller never rejects locally — it normalizes every non-conforming header into `null` and always calls `apiKeyService.exchange(...)`, giving exactly one code path that writes the audit row for every cause.
- `extractCredential`: returns the credential substring only when the scheme token matches `ApiKey` case-insensitively (RFC 7235) **and** the credential is ≤256 characters; otherwise returns `null`. This covers missing header, wrong scheme (including `Bearer`), blank credential, and over-length credential uniformly — each becomes a `null` argument to `exchange`, which takes the same audited malformed path as a genuinely malformed key. Truncating an over-length credential instead of rejecting it would be wrong (it could corrupt an otherwise-valid credential); treating it as unconditionally malformed avoids ever handing attacker-controlled bulk to `ApiKeyHasher`.
- Calls `apiKeyService.exchange(credential)` to completion (commits) — **then** `apiKeyTokenIssuer.issue(result.accountUuid(), result.scopes())` (D5 ordering; no `@Transactional` on the controller method).
- Returns `ApiKeyTokenResponse.of(issuedToken.accessToken(), issuedToken.expiresInSeconds())`.
- A signing exception from `issue(...)` is **not caught here** — it propagates to `ApiExceptionHandler.onUnexpected` (framework-level `@RestControllerAdvice`, generic `Exception` handler), producing the required 500 (D5/AC15). No local try/catch needed — the exception handler ordering already gives the desired result, verified against `ApiExceptionHandler`'s `Exception.class` catch-all being the only place a `JwtEncodingException`-family error could land, since `ApiKeyExceptionHandler` only maps `ApiKeyExchangeRejectedException`.

### 5. Tests under `src/test/java/com/themistra/auth/apikey/`
- `ApiKeyTokenIssuerTest` — plain JUnit, fixed `Clock`, mocked `RoleService` and `JwtEncoder` (or a real `NimbusJwtEncoder` over an in-memory RSA JWK for a closer-to-real assertion of claim shape — **preferred**, since decoding the real compact JWT is the only way to assert `scope` serializes as a JSON array, not just that the claims map was built correctly). Covers TTL arithmetic, claim assembly, `roles` freshness, thread-safety is structural (no field mutation, not something a unit test can prove — noted, not tested).
- `ApiKeyControllerTest` (`@WebMvcTest` or a lightweight `MockMvc` slice with mocked `ApiKeyService`/`ApiKeyTokenIssuer`) — header parsing edge cases: missing header, wrong scheme (including `Bearer`), blank credential, >256 chars, case-insensitive `ApiKey`/`APIKEY`/`apikey` scheme acceptance.
- `ApiKeyExchangeIntegrationTest` (`@SpringBootTest` + `@Import(TestcontainersConfiguration.class)`) — the two **named tests** plus the full boundary/supporting list (frozen brief items 3–12): real filter chain reachability, `BearerTokenAuthenticationFilter` non-interception (D4 regression, item 7), `last_used_at` writes, expired-key boundary, non-default TTL, `PublicEndpoints.METHOD_SCOPED` guard (item 6 — likely belongs in the existing `PublicEndpointsTest` instead of a new file, since that's a pure data-table assertion with no Spring context needed — **plan: extend `PublicEndpointsTest`, not duplicate it**), audit row/outbox mirror assertions, response envelope field-name assertions, regression run of `ArchitectureTest`/`ApiKeyServiceIntegrationTest`/`TokenClaimsCustomizerTest`/`SasLoginIntegrationTest`.

---

## Files to Modify

### `apikey/ApiKeyProperties.java`
Add `tokenTtlMinutes`:
```java
public record ApiKeyProperties(
        @NotBlank String prefix,
        @Min(1) @Max(525_600) long tokenTtlMinutes
) {}
```
(Bounds mirror `VerificationTokenProperties`'s exact precedent — same rationale: fail startup rather than mint instantly-expired or overflow-risking tokens.)

### `common/PublicEndpoints.java`
Add one line to `METHOD_SCOPED`:
```java
new MethodScoped(HttpMethod.POST, "/api-keys/token")   // public: the key itself is the credential (L11)
```

### `common/ProblemTypes.java`
Add:
```java
/** Uniform rejection for POST /api-keys/token — every cause (revoked, expired, malformed,
 * unknown prefix, hash mismatch, missing/wrong-scheme header) maps here identically (R33, R46). */
public static final URI API_KEY_EXCHANGE_REJECTED = URI.create(BASE + "api-key-exchange-rejected");
```

### `token/JwksConfig.java`
Add the `JwtEncoder` bean (D1):
```java
@Bean
public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
}
```
Placed after the existing `jwtDecoder` bean; no change to `jwkSource`'s construction.

### `src/main/resources/application.properties`
Add, near the existing `themistra.auth.api-key.prefix` line (§ around line 95):
```
themistra.auth.api-key.token-ttl-minutes=${API_KEY_TOKEN_TTL_MINUTES:10}
```

---

## Files Explicitly NOT Modified (restating the brief's fence, for Phase 6's benefit)

`spec/**`; `apikey/ApiKeyService.java`, `ApiKeyRepository.java`, `ApiKey.java`, `ApiKeyHasher.java`, `ApiKeyExchangeRejectedException.java`; `token/TokenClaimsCustomizer.java`, `RegisteredClientSeeder.java`, `AuthClientsProperties.java`, `SecurityChainsConfig.java`; `events/EventTopics.java`; all Flyway migrations; other feature modules/tests.

---

## Execution Order

1. **Config first:** `apikey/ApiKeyProperties.java` (add field) + `application.properties` (add key) — nothing else compiles meaningfully without the TTL bound existing.
2. **`common/ProblemTypes.java`** — add the new URI constant (near-zero-dependency, needed by both the issuer's exception path indirectly and the exception handler directly).
3. **`token/JwksConfig.java`** — add the `JwtEncoder` bean (D1). Verify `AuthServiceApplicationTests.contextLoads` still boots before writing anything downstream of it — this is the single highest-blast-radius change in the plan (shared config class) and item 12's `SasLoginIntegrationTest` regression check exists specifically to catch this.
4. **`apikey/ApiKeyTokenIssuer.java`** — depends on step 3's bean and `RoleService` (pre-existing). Can be unit-tested in isolation immediately after writing (`ApiKeyTokenIssuerTest`).
5. **`apikey/dto/ApiKeyTokenResponse.java`** — no dependencies, trivial, can be done any time before step 6.
6. **`apikey/ApiKeyExceptionHandler.java`** — depends on step 2's `ProblemTypes` constant and the pre-existing `ApiKeyExchangeRejectedException`.
7. **`common/PublicEndpoints.java`** — add the route entry. Independent of 1–6; done here so the controller in step 8 has a real permitAll path to be reached through when its integration test runs.
8. **`apikey/ApiKeyController.java`** — depends on steps 4–7 all existing (issuer, response DTO, exception handler, public route) plus the pre-existing `ApiKeyService.exchange`.
9. **Tests**, in this order: `ApiKeyTokenIssuerTest` (can actually start right after step 4) → `ApiKeyControllerTest` (header-parsing slice, after step 8) → extend `PublicEndpointsTest` (after step 7) → `ApiKeyExchangeIntegrationTest` (full stack, after step 8) → regression run of `ArchitectureTest`, `ApiKeyServiceIntegrationTest`, `TokenClaimsCustomizerTest`, `SasLoginIntegrationTest`.
10. **Full suite run** (`mvn -pl services/auth -am test`, or `verify` if Docker/Testcontainers is available this session — check `docker info` first per established session practice) — confirm no regression against the last known baseline before Phase 6 is declared complete.

---

## Traceability Check

Every file above appears in the frozen brief's **Files to Create** / **Files to Modify** lists verbatim. One organizational note: the brief's required test list (item 6) is a `PublicEndpoints.METHOD_SCOPED` guard assertion. Rather than modifying the pre-existing, shared `PublicEndpointsTest` (outside the `apikey` package, and no other task has needed to touch it), this plan adds that assertion to the new `apikey`-scoped integration test instead — same coverage, no modification outside the brief's authorized `apikey/` test package. No files outside the brief's authorized set are planned.

---

**Phase 5 complete — plan written.** Proceed to Phase 6 (Implementation) on approval.
