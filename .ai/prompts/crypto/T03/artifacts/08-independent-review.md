<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review) for crypto · T03. -->

# crypto · T03 · Phase 8 — Independent Code Review Findings

**Scope:** Review the Phase 6 implementation (`common/config/*Properties.java`, `common/PublicEndpoints.java`, `common/ResourceServerConfig.java`, `CryptoServiceApplication.java`, `application.properties`) and the Phase 7 self-review with fresh, adversarial eyes.

**Directive:** Do not rewrite. Report findings as **Issue · Evidence · Recommendation · Confidence**.

---

## Finding 1 — Required T03 tests are entirely missing

**Issue:** The frozen brief's Required Tests section mandates multiple test classes, including `shouldRequireInternalScopeForWatchAndAttestEndpoints`, one fail-fast test per properties class, bind-success tests, a `PublicEndpoints` sweep test, and security tests. None exist under `services/crypto/src/test/java/com/themistra/crypto/common/`.

**Evidence:**
- `services/crypto/src/test/java/com/themistra/crypto/` contains only `T01SkeletonRegressionTest.java` and `ChainBaselineMigrationIntegrationTest.java`.
- TIB §Required Tests explicitly lists:
  - `shouldRequireInternalScopeForWatchAndAttestEndpoints`
  - "Config fail-fast tests (one per properties class)"
  - "Config bind-success tests"
  - "`PublicEndpoints` sweep test"
  - "Security tests: unauthenticated → 401, wrong/missing scope → 403, correct scope → reaches the protected resource"
- TIB §Acceptance Criteria (AC1–AC6) cannot be verified without automated tests.

**Recommendation:** Add the missing tests before Phase 9 sign-off. For the security test, use `@WebMvcTest` with a test-only `@RestController` under `/internal/v1/**` and a mocked `JwtDecoder`/`JwtAuthenticationToken`. For fail-fast tests, use `@ActiveProfiles("dev")` with a test property source that supplies all *other* valid config so each test isolates one missing/invalid field.

**Confidence:** High.

---

## Finding 2 — `anyRequest().authenticated()` introduces a third, weaker access tier

**Issue:** `ResourceServerConfig` requires `SCOPE_internal.crypto:write` only on `/internal/v1/**` and falls back to `anyRequest().authenticated()` for everything else. Because `PublicEndpoints` is supposed to be the exhaustive public allowlist, the only other category of request should be internal endpoints requiring the scope. The current fallback allows any future non-public, non-internal endpoint to be accessed with a valid JWT that lacks `internal.crypto:write`.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java:52-55`:
  ```java
  auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll();
  auth.requestMatchers("/internal/v1/**").hasAuthority(INTERNAL_SCOPE_AUTHORITY);
  auth.anyRequest().authenticated();
  ```
- `agents.md` §Security: "Internal endpoints (`/internal/v1/*`) require a service-to-service JWT with the `internal.crypto:write` scope" and "The public-endpoint set is an exhaustive, CI-enforced allowlist."
- If a future controller is added at, e.g., `/admin/v1/foo` or even `/internal/v2/foo`, it would be reachable with any authenticated token, not necessarily one bearing `internal.crypto:write`.

**Recommendation:** Change `auth.anyRequest().authenticated()` to `auth.anyRequest().hasAuthority(INTERNAL_SCOPE_AUTHORITY)` (or `denyAll()` if no non-internal endpoints are expected). This makes the security model binary: public allowlist, or internal scope.

**Confidence:** High.

---

## Finding 3 — JWT issuer is not validated despite the brief explicitly requiring it

**Issue:** The TIB Constraints section states "JWT validated against auth's JWKS (signature + issuer)". The implementation only configures `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`, which gives Spring Boot a `NimbusJwtDecoder` that validates signature, expiry, and not-before, but does **not** validate the `iss` claim.

**Evidence:**
- `services/crypto/src/main/resources/application.properties:28`:
  ```properties
  spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8080/oauth2/jwks
  ```
- No `spring.security.oauth2.resourceserver.jwt.issuer-uri` property and no custom `JwtDecoder`/`OAuth2TokenValidator` bean is declared.
- Spring Boot's `OAuth2ResourceServerJwtConfiguration.JwtDecoderConfiguration` only adds an `IssuerValidator` when `issuer-uri` is set.
- `ResourceServerConfig.java` comment explicitly discusses why `aud` is not validated, but never mentions issuer validation.

**Recommendation:** Add `spring.security.oauth2.resourceserver.jwt.issuer-uri=<auth-service-issuer-uri>` (e.g., `http://localhost:8080` for local) alongside the JWKS URI, or declare a custom `JwtDecoder` with an `IssuerValidator`. This satisfies the brief's "signature + issuer" constraint.

**Confidence:** High.

---

## Finding 4 — Placeholder values are accepted in non-local profiles, undermining L13 fail-fast

**Issue:** `application.properties` contains obvious placeholders such as `local-only-fake-kms-key-id`, `local-only-fake-observation-snapshots`, and `local-only-fake-provider-key`. The `@ConfigurationProperties` records validate `@NotBlank` but do not reject placeholder-looking values. In a non-local profile, if these placeholders are not overridden, the application will boot with fake credentials instead of failing.

**Evidence:**
- `services/crypto/src/main/resources/application.properties:36,62,65,66` contain the placeholder values above.
- `ProviderProperties`, `KmsProperties`, and `SnapshotProperties` only enforce `@NotBlank` on the relevant fields.
- `agents.md` §Configuration: "startup FAILS on missing/invalid values in non-local profiles."
- `design.md` §4a L13: "No provider API key, DB cred, or KMS key ARN is committed. External Secrets Operator injects them; validated `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles."

**Recommendation:** Either (a) add a placeholder-rejection validator/regex that rejects values containing `local-only`, `fake-`, or `placeholder` when a non-local profile is active, or (b) leave secret-shaped fields empty in the base `application.properties` and rely on `@NotBlank` to fail in non-local profiles. Option (b) is simpler and avoids accidental boot with placeholder values.

**Confidence:** High.

---

## Finding 5 — Two declared-public actuator paths are unreachable without exposure config

**Issue:** `PublicEndpoints.PATTERNS` lists `/actuator/info` and `/actuator/prometheus` as public, and `/actuator/health/**` covers liveness/readiness probes. However, Spring Boot Actuator only exposes `health` over HTTP by default. `info` and `prometheus` will 404, and health probes require `management.endpoint.health.probes.enabled=true`.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java:12-17` declares all four actuator paths.
- `services/crypto/src/main/resources/application.properties` contains no `management.endpoints.web.exposure.include` or `management.endpoint.health.probes.enabled` property.
- `services/auth/src/main/resources/application.properties:113-115` sets exactly these properties for the same reason, establishing project precedent.
- This was already identified in Phase 7 (self-review Finding 1); it remains unfixed in the current code.

**Recommendation:** Add to `application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never
```
This aligns with auth-service precedent and makes AC4/AC6 actually true.

**Confidence:** High.

---

## Finding 6 — Chain identifiers are unconstrained, allowing silently invalid chain names

**Issue:** `ProviderProperties.ChainProviders.chain` and `FinalityProperties.enabledChains` are plain `@NotBlank String` fields. Because the launch scope is fixed to `ETHEREUM` and `TRON` (`package.md` §2 / `design.md` §2), a typo such as `ETHERUM` or `TRON_MAINNET` binds successfully as a "valid" value in any profile, including non-local.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java:25`:
  ```java
  @NotBlank String chain,
  ```
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java:20`:
  ```java
  @NotEmpty List<@NotBlank String> enabledChains
  ```
- `package.md` §2: "In scope (launch: Tron + Ethereum)."
- This was already identified in Phase 7 (self-review Finding 2); it remains unfixed.

**Recommendation:** Constrain chain values to the known launch set with `@Pattern(regexp = "ETHEREUM|TRON")` (or a shared enum/constant), applied to both fields. This does not preclude future chains; it just enforces the currently in-scope set.

**Confidence:** High.

---

## Finding 7 — No cross-field validation between quorum threshold and provider count

**Issue:** `ProviderProperties` has `quorumThreshold` (`@Min(1) int`) and a per-chain list of providers, but there is no validation that `quorumThreshold <= providers.size()` for each chain. A deployment could configure `quorum-threshold=5` with only two providers per chain, making quorum impossible to reach at runtime.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java:19-36`.
- `design.md` §4a L1: "2-of-3 quorum" and "Every emitted fact required ≥2 of 3 independent providers to agree."

**Recommendation:** Add a class-level validator or compact-constructor check that verifies `quorumThreshold` does not exceed the number of providers for any configured chain. This is a startup-time invariant that fits T03's "validated, fail-fast configuration" purpose.

**Confidence:** Medium.

---

## Finding 8 — Finality enabled-chains and provider chains could drift

**Issue:** `FinalityProperties.enabledChains` and `ProviderProperties.chains[*].chain` are validated independently. A deployment could enable finality for `TRON` while only configuring providers for `ETHEREUM` (or vice versa), leading to a runtime state where a chain is "watched" but can never produce facts or is never finalized.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java`
- No shared enum or cross-property validator links the two lists.

**Recommendation:** Either (a) derive enabled chains from the provider config and remove `FinalityProperties.enabledChains`, or (b) add a startup-time check that every chain in `FinalityProperties.enabledChains` also appears in `ProviderProperties.chains` (and ideally vice versa). Given the brief's emphasis on fail-fast config, option (b) is safer.

**Confidence:** Medium.

---

## Finding 9 — Screening `enabled=false` default may silently disable screening in non-local profiles

**Issue:** `ScreeningProperties.enabled` is a primitive `boolean` defaulting to `false`. In a non-local profile, if operators forget to set `themistra.crypto.screening.enabled=true` while supplying `base-url` and `api-key-secret-name`, screening remains disabled and the application boots without error. The conditional validation only fires when `enabled=true`.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java:17-34`.
- `services/crypto/src/main/resources/application.properties:55` sets `themistra.crypto.screening.enabled=false`.
- `agents.md` §Service-specific rules: "Screening fails closed (no signature)."

**Recommendation:** Treat `enabled` as required in non-local profiles, or add a cross-field rule: if `base-url` is non-blank, `enabled` must be `true`. Alternatively, make `enabled` default to `true` and allow `local` to explicitly disable it, reducing the chance of a silent production misconfiguration. This is a judgment call for the team.

**Confidence:** Medium.

---

## Finding 10 — 401 responses lack `WWW-Authenticate` header

**Issue:** The custom `AuthenticationEntryPoint` writes an RFC 9457 problem body but does not set a `WWW-Authenticate: Bearer` response header. RFC 6750 §3 requires this header on 401 responses for Bearer token schemes, and omitting it can break standard HTTP clients/edge proxies that expect it.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java:67-91`.
- The `writeProblemJson` helper sets status and `Content-Type` but never adds `WWW-Authenticate`.

**Recommendation:** Add `response.setHeader("WWW-Authenticate", "Bearer");` (or a more specific challenge including `error="invalid_token"` / `error_description="..."`) in the authentication entry point before writing the problem body.

**Confidence:** High.

---

## Finding 11 — Hardcoded `spring.profiles.active=local` is a deployment hazard

**Issue:** `application.properties` sets `spring.profiles.active=local`. If a dev/staging/prod deployment forgets to override this (e.g., via `SPRING_PROFILES_ACTIVE` or command-line flag), the service will run in `local` profile with placeholder values and may boot successfully when it should have failed.

**Evidence:**
- `services/crypto/src/main/resources/application.properties:8`:
  ```properties
  spring.profiles.active=local
  ```
- `agents.md` §Configuration: "Profiles: local, dev, staging, prod. ... startup FAILS on missing/invalid values in non-local profiles."

**Recommendation:** Remove `spring.profiles.active=local` from committed `application.properties` and document that local development must activate it explicitly (`-Dspring.profiles.active=local` or `SPRING_PROFILES_ACTIVE=local`). This prevents accidental local-profile deployment to higher environments.

**Confidence:** Medium.

---

## Finding 12 — `/internal/v1/*` in `agents.md` vs `/internal/v1/**` in implementation

**Issue:** `agents.md` §Security states internal endpoints are `/internal/v1/*`, while `ResourceServerConfig` secures `/internal/v1/**`. The implementation's comment argues that `/internal/v1/*` would not cover `DELETE /internal/v1/watches/{watchId}`, but this is a standing-rule deviation that has not been resolved by updating `agents.md`.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java:32-34` acknowledges the mismatch.
- `agents.md` §Security still reads "Internal endpoints (`/internal/v1/*`) ..."
- `design.md` §4c internal API paths: `POST /internal/v1/watches`, `DELETE /internal/v1/watches/{watchId}`, `POST /internal/v1/attest`.

**Recommendation:** Either update `agents.md` to `/internal/v1/**` (with CODEOWNERS review, since it is protected) or accept that the implementation intentionally uses a more permissive matcher and document the deviation in the brief/artifact. The current silent deviation creates a spec/implementation drift.

**Confidence:** High.

---

## Summary table

| # | Finding | Severity | Confidence |
|---|---------|----------|------------|
| 1 | Required T03 tests are missing | High | High |
| 2 | `anyRequest().authenticated()` weakens security model | High | High |
| 3 | JWT issuer not validated | High | High |
| 4 | Placeholder values accepted in non-local profiles | High | High |
| 5 | Actuator endpoints not exposed | High | High |
| 6 | Chain identifiers unconstrained | Medium | High |
| 7 | No quorum-threshold vs provider-count validation | Medium | Medium |
| 8 | Finality chains and provider chains may drift | Medium | Medium |
| 9 | Screening `enabled=false` default risk | Medium | Medium |
| 10 | 401 lacks `WWW-Authenticate` header | Low-Medium | High |
| 11 | Hardcoded `spring.profiles.active=local` | Medium | Medium |
| 12 | `/internal/v1/*` vs `/internal/v1/**` drift | Low-Medium | High |

(End of independent code review.)
