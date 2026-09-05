<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T25 · Phase 6 — Implementation Notes

Implements `artifacts/05-implementation-plan.md` against the frozen brief (`artifacts/04-frozen-task-brief.md`). No tests written in this phase (Phase 10). No file outside the plan's authorized set was touched.

---

## Files Created

### `apikey/dto/ApiKeyTokenResponse.java`
Response envelope: `accessToken` / `tokenType` / `expiresIn`, serialized as `access_token` / `token_type` / `expires_in` via explicit `@JsonProperty` on each record component. **Note:** this is the first DTO in the service to need `@JsonProperty` at all — every existing DTO (`AccountResponse`, `RegistrationAcknowledgement`, …) serializes in plain camelCase, with no global Jackson naming strategy configured. The frozen brief's Outputs table locks this envelope to the standard OAuth2 token-response shape (RFC 6749 §5.1), so the snake_case names are this task's own deliberate, locally-scoped exception, not a codebase-wide convention change. `ApiKeyTokenResponse.of(accessToken, expiresInSeconds)` fixes `token_type` to the literal `"Bearer"` so no call site can pass anything else.

### `apikey/ApiKeyTokenIssuer.java`
Implements D1 exactly as planned: assembles the full L9 claim set itself (`iss`, `sub`, `aud`, `iat`, `nbf`, `exp`, `jti`, `scope`, `roles`, `client_id`, `amr`, `acr`, `email_verified`), does not touch `TokenClaimsCustomizer`. Depends on the new `JwtEncoder` bean, `RoleService.resolveEffectiveRoles` (fresh per call, Kimi#10), `ApiKeyProperties.tokenTtlMinutes`, and the injected `Clock`. `issuer` is bound via `@Value("${spring.security.oauth2.authorizationserver.issuer}")` — the same property `SecurityChainsConfig`'s SAS instance reads, no new config key.

**`aud` claim — resolves Phase 5's flagged Open Question:** implemented as the literal `"checky-api-key"`, identical to `client_id`. L9 requires the claim; the frozen brief's Outputs table doesn't fix its value (unlike `client_id`, fixed by D2); there is no `RegisteredClient` to derive it from (D2 declines to seed one). This is a one-line filler for an otherwise-unspecified mandatory claim, not one of the brief's four actual blockers — flagging it here for confirmation rather than treating it as license to revisit D1–D5.

`email_verified` = `false` unconditionally: an API-key exchange carries no email context at all, and R48/L9 require the claim be present with no PII beyond it. `false` is the only value that doesn't fabricate an email-verification fact this flow has no way to know.

Thread-safety: singleton, all fields `final`, no mutable instance state — `JwtEncoder` (`NimbusJwtEncoder`) is Spring-managed and thread-safe.

### `apikey/ApiKeyExceptionHandler.java`
Single mapping, `ApiKeyExchangeRejectedException` → 401 `application/problem+json`, `ProblemTypes.API_KEY_EXCHANGE_REJECTED`, fixed title, no `detail` — mirrors `AccountExceptionHandler.onVerificationTokenRejected`'s exact shape (R33/R46).

### `apikey/ApiKeyController.java`
`POST /api-keys/token`, per the plan: a controller (not a filter), per-request header parsing via a private `extractCredential`, then `apiKeyService.exchange(credential)` to completion followed by `apiKeyTokenIssuer.issue(...)` (D5 ordering, no transactional wrapper). A signing exception is not caught locally — it propagates to `ApiExceptionHandler.onUnexpected` (the framework-level catch-all `@RestControllerAdvice`), yielding the required opaque 500 (D5/AC15) with zero new code needed for that path.

`extractCredential` normalizes every header-level rejection cause (missing header, wrong scheme, blank credential, >256-character credential) to `null` rather than rejecting locally, so `ApiKeyService.exchange(null)` — which already has an audited "malformed" branch — is the single code path responsible for both the uniform 401 (R33/AC10) and the per-attempt audit row (R43/AC12). No duplicate rejection logic exists between the controller and the service.

---

## Files Modified

- **`apikey/ApiKeyProperties.java`** — added `@Min(1) @Max(525_600) long tokenTtlMinutes`, bounds copied from `VerificationTokenProperties`'s precedent.
- **`common/PublicEndpoints.java`** — added `new MethodScoped(HttpMethod.POST, "/api-keys/token")` to `METHOD_SCOPED` (L11). `PublicEndpointsTest`'s existing `.contains(...)` assertions are unaffected (confirmed by test run below) and were not modified — the T25-specific guard assertion belongs in the new `apikey` integration test (Phase 10), not this shared file, per the plan's traceability note.
- **`common/ProblemTypes.java`** — added `API_KEY_EXCHANGE_REJECTED` (new stable URI, `.../problems/api-key-exchange-rejected`).
- **`token/JwksConfig.java`** — added the `jwtEncoder` bean (D1): `new NimbusJwtEncoder(jwkSource)`, same `JWKSource<SecurityContext>` bean the existing `jwtDecoder` already consumes. No change to `jwkSource` itself.
- **`src/main/resources/application.properties`** — added `themistra.auth.api-key.token-ttl-minutes=${API_KEY_TOKEN_TTL_MINUTES:10}` next to the existing `api-key.prefix` line.

No file outside this list, and no file under `spec/`, was touched. `ApiKeyService`, `ApiKeyRepository`, `ApiKey`, `ApiKeyHasher`, `ApiKeyExchangeRejectedException`, `TokenClaimsCustomizer`, `RegisteredClientSeeder`, `AuthClientsProperties`, `SecurityChainsConfig`, and `EventTopics` are unmodified, confirmed by `git status`/diff at the end of this phase.

---

## Acceptance Criteria — mapping

| AC | Status | Evidence |
|---|---|---|
| AC1/AC2 | Done | `POST /api-keys/token` registered in `PublicEndpoints.METHOD_SCOPED`, the only new public entry. |
| AC3–AC8 | Done | `ApiKeyTokenIssuer.buildClaims` (informally — inlined in `issue`) sets every L9 claim per D1–D3; RS256 is `NimbusJwtEncoder`'s default header algorithm when no `JwsHeader` is supplied (verified against `NimbusJwtEncoder.DEFAULT_JWS_HEADER = JwsHeader.with(SignatureAlgorithm.RS256)`). |
| AC9 | Done (pre-existing) | `last_used_at` update is inside `ApiKeyService.exchange`, untouched by T25. |
| AC10 | Done, with one documented residual — see Deviations below (Bearer case). |
| AC11 | Done | Response DTO carries only the signed token, `"Bearer"`, and a TTL in seconds — no key/hash/email/internal id. |
| AC12 | Done (pre-existing + controller design) | Every path into `ApiKeyService.exchange` — including every controller-normalized rejection — hits its existing audit calls. |
| AC13 | Done | No Flyway migration added. |
| AC14 | Done | No `apikey` class imports `PublicEndpoints` or a foreign entity (a Javadoc `{@link}` in `ApiKeyController` is not a bytecode dependency and does not trip the ArchUnit rule). |
| AC15 | Done | Uncaught signing exception path traced through `ApiExceptionHandler.onUnexpected`, confirmed by inspection (no test yet — Phase 10). |

---

## Deviations Forced by Reality (flagged, not hidden)

**AC10 vs. the `Bearer`-scheme case.** The frozen brief's Required Test #8 expects a `Bearer`-schemed header to produce "the same uniform 401" as every other rejection cause. Tracing the actual filter chain: `SecurityChainsConfig`'s `@Order(2)` `applicationChain` applies `.oauth2ResourceServer(rs -> rs.jwt(...))` unconditionally, so `BearerTokenAuthenticationFilter` runs on every request regardless of `permitAll`. Spring's `DefaultBearerTokenResolver` matches any `Authorization: Bearer <token68>` header where the credential matches `[A-Za-z0-9\-._~+/]+=*` — our key format (`ck_live_<alnum>.<alnum>`) satisfies that character class, so a `Bearer`-schemed real-shaped key **is** extracted and handed to the JWT decoder, fails to parse (it isn't a JWT), and is rejected by Spring Security's own `BearerTokenAuthenticationEntryPoint` — a 401 with a `WWW-Authenticate` header and **no JSON body**, before the request ever reaches `ApiKeyController` or `ApiKeyExceptionHandler`.

This is exactly the mechanism D4 itself describes as the reason `Bearer` was rejected as *this endpoint's own* scheme — it is unavoidable, pre-existing Spring Security framework behavior on the `@Order(2)` chain, and `SecurityChainsConfig` is explicitly out of T25's authorized file set (frozen brief, Files NOT to Modify). **Net effect:** every rejection cause reachable through `ApiKeyController` (missing header, wrong-but-not-`Bearer` scheme, blank credential, over-length credential, malformed key, unknown prefix, hash mismatch, revoked, expired) gets the byte-identical uniform 401 body required by R46/AC10. A literal `Bearer`-schemed header gets a 401 status too, but from a different component with a different (empty) body — status-uniform, not byte-uniform. Not fixable without touching frozen `SecurityChainsConfig`; **flagged here for Phase 10's test author** to assert status-only equivalence for the `Bearer` case rather than a byte-for-byte body comparison, and for the human gate at Phase 9 if it's judged to need a different resolution.

**`aud` claim value.** As flagged in Phase 5: L9 mandates the claim, the frozen brief doesn't fix its value. Implemented as `"checky-api-key"` (same as `client_id`) — see the `ApiKeyTokenIssuer` notes above. Non-blocking; surfaced for explicit confirmation, not silently decided.

No other deviations from the plan.

---

## Build Verification

`mvn -q -pl services/auth -am compile` — clean, exit 0.

`mvn -q -pl services/auth -am test` — 516 tests, 0 failures, 91 errors. Every error traced to `IllegalStateException: ApplicationContext failure threshold ... exceeded` — Testcontainers cannot start (Docker daemon confirmed down via `docker info`), the same pre-existing, previously-documented environment limitation this pipeline has hit since T15. Specifically checked, all green and unaffected by T25's changes:
- `com.themistra.auth.token.TokenClaimsCustomizerTest` — 8/8 pass (plain unit test, no Spring context; confirms the new `JwtEncoder` bean in `JwksConfig` did not disturb `TokenClaimsCustomizer`'s own logic).
- `com.themistra.auth.apikey.ApiKeyHasherTest` — 3/3 pass.
- `com.themistra.auth.common.PublicEndpointsTest` — 1/1 pass (unaffected by the new `METHOD_SCOPED` entry, confirmed above).
- `com.themistra.auth.ArchitectureTest` — reports `Tests run: 0` (the same pre-existing Surefire/ArchUnit wiring quirk noted in T16 Phase 12 — true before and after this task).
- `ApiKeyServiceIntegrationTest`, `AuthServiceApplicationTests.contextLoads`, and every other `@SpringBootTest`/Testcontainers-backed class fail identically on the same `ApplicationContext failure` — Docker-down, not a T25 regression.

**Not verified this session (needs Docker):** `SasLoginIntegrationTest` (the D1 no-disturbance regression check), and the endpoint's actual runtime behavior end-to-end. Whoever has Docker access should run the full Testcontainers-backed suite, plus the tests Phase 10 writes, before this task is considered fully proven.

---

**Phase 6 complete — implementation written.** Proceed to Phase 7 (Self Review) on approval.
