# crypto · T03 · Phase 5 — Implementation Plan

Every file below traces to `artifacts/04-frozen-task-brief.md` (FROZEN) Files to Create/Modify. No
additional files are planned. No code is written in this phase.

## Files to create

1. `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java`
2. `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java`
3. `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java`
4. `services/crypto/src/main/java/com/themistra/crypto/common/config/KmsProperties.java`
5. `services/crypto/src/main/java/com/themistra/crypto/common/config/SnapshotProperties.java`
6. `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java`
7. `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java`
8. `services/crypto/src/test/java/com/themistra/crypto/common/InternalTestController.java` (test
   scope only — mirrors the real internal API paths per frozen-brief amendment #13; not shipped in
   `src/main`)
9. `services/crypto/src/test/java/com/themistra/crypto/common/config/ProviderPropertiesTest.java`
10. `services/crypto/src/test/java/com/themistra/crypto/common/config/FinalityPropertiesTest.java`
11. `services/crypto/src/test/java/com/themistra/crypto/common/config/ScreeningPropertiesTest.java`
12. `services/crypto/src/test/java/com/themistra/crypto/common/config/KmsPropertiesTest.java`
13. `services/crypto/src/test/java/com/themistra/crypto/common/config/SnapshotPropertiesTest.java`
14. `services/crypto/src/test/java/com/themistra/crypto/common/PublicEndpointsTest.java`
15. `services/crypto/src/test/java/com/themistra/crypto/common/ResourceServerConfigIntegrationTest.java`
    (contains the named test `shouldRequireInternalScopeForWatchAndAttestEndpoints`)

## Files to modify

1. `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
   `@ConfigurationPropertiesScan`.
2. `services/crypto/src/main/resources/application.properties` — add
   `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` and `themistra.crypto.*` keys with
   `local`-safe placeholders.

No files outside this list. `pom.xml`, T01/T02 migration and test files, and everything under `spec/`
are untouched, per frozen brief.

## Public methods (signatures)

Records expose their canonical accessors implicitly; only the record component shape and any
non-trivial public method are listed.

**`ProviderProperties`** (`@ConfigurationProperties(prefix = "themistra.crypto.providers")`, `@Validated`)
```java
public record ProviderProperties(
    @NotEmpty @Valid List<ChainProviders> chains,
    @Min(1) int quorumThreshold
) {
    public record ChainProviders(
        @NotBlank String chain,
        @NotEmpty @Valid List<ProviderEntry> providers
    ) {}
    public record ProviderEntry(
        @NotBlank String name,
        @NotBlank String url,
        @Min(1) int timeoutSeconds,
        @NotBlank String apiKeySecretName
    ) {}
}
```
Generic, vendor-name-agnostic structure per frozen brief (Q1 unresolved — placeholder chain/provider
values only in `local`).

**`FinalityProperties`** (`@ConfigurationProperties(prefix = "themistra.crypto.finality")`, `@Validated`)
```java
public record FinalityProperties(
    @NotEmpty List<@NotBlank String> enabledChains
) {}
```
No confirmation-count or threshold field — enforced by the frozen brief (L4/amendment #4) and by
`FinalityPropertiesTest`'s reflection-based negative assertion (no field name matching
`*[Cc]onfirmation*` or `*[Tt]hreshold*`).

**`ScreeningProperties`** (`@ConfigurationProperties(prefix = "themistra.crypto.screening")`, `@Validated`)
```java
public record ScreeningProperties(
    boolean enabled,
    String baseUrl,
    @Min(1) int connectTimeoutSeconds,
    @Min(1) int readTimeoutSeconds,
    @Min(0) int retryMaxAttempts,
    String apiKeySecretName
) {
    public ScreeningProperties {
        if (enabled && (baseUrl == null || baseUrl.isBlank())) {
            throw new IllegalStateException(
                "themistra.crypto.screening.base-url is required when screening.enabled=true");
        }
        if (enabled && (apiKeySecretName == null || apiKeySecretName.isBlank())) {
            throw new IllegalStateException(
                "themistra.crypto.screening.api-key-secret-name is required when screening.enabled=true");
        }
    }
}
```
Compact-constructor validation (not plain `@NotBlank`) because `baseUrl`/`apiKeySecretName` are only
conditionally required — `enabled=false` is the valid `local`-profile shape, vendor-agnostic per L12
(Q2 unresolved).

**`KmsProperties`** (`@ConfigurationProperties(prefix = "themistra.crypto.kms")`, `@Validated`)
```java
public record KmsProperties(
    @NotBlank String keyId
) {}
```
Exactly one key-identifying field per frozen brief amendment #7/AC8 — no ARN + region redundancy.

**`SnapshotProperties`** (`@ConfigurationProperties(prefix = "themistra.crypto.snapshot")`, `@Validated`)
```java
public record SnapshotProperties(
    @NotBlank String bucket,
    @NotBlank String prefix,
    @NotBlank String region
) {}
```

**`PublicEndpoints`**
```java
public final class PublicEndpoints {
    public static final String[] PATTERNS = {
        "/actuator/health/**",
        "/actuator/info",
        "/actuator/prometheus",
        "/.well-known/themistra-verification-keys"
    };
    private PublicEndpoints() {}
}
```
No `METHOD_SCOPED` list (unlike auth's) — this task's public surface has no method-scoped exceptions.

**`ResourceServerConfig`**
```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception;

    @Bean
    AuthenticationEntryPoint problemJsonAuthenticationEntryPoint(ObjectMapper objectMapper);

    @Bean
    AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper objectMapper);
}
```
`securityFilterChain` wires: `PublicEndpoints.PATTERNS` → `permitAll`; `/internal/v1/**` →
`hasAuthority("SCOPE_internal.crypto:write")`; everything else → `authenticated()`;
`.oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()).authenticationEntryPoint(...).accessDeniedHandler(...))`.
No custom `JwtDecoder` bean — Spring Boot autoconfigures it from
`spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (frozen brief amendment #8). No custom
`JwtAuthenticationConverter` — default `scope`-claim → `SCOPE_*` mapping is sufficient (audience is
intentionally not validated, amendment #1).

**`InternalTestController`** (test scope)
```java
@RestController
class InternalTestController {
    @PostMapping("/internal/v1/watches") ResponseEntity<Void> registerWatch();
    @DeleteMapping("/internal/v1/watches/{watchId}") ResponseEntity<Void> unregisterWatch(@PathVariable String watchId);
    @PostMapping("/internal/v1/attest") ResponseEntity<Void> attest();
}
```
Mirrors `design.md` §4c paths/methods exactly (amendment #13) so the security test exercises the real
request-matcher shape without depending on T15/T21's real controllers.

## Private methods

- `ResourceServerConfig`: a private helper `writeProblemJson(HttpServletResponse response, int status,
  String title, String detail)` (or equivalent lambda body inlined in the two handler beans) producing
  an RFC 9457 body — no separate class needed for this narrowly-scoped security-layer use (a general
  `ApiExceptionHandler` remains out of scope per frozen brief).
- No private methods needed in the properties records beyond the `ScreeningProperties` compact
  constructor shown above (compact constructors are not separately "private methods" but are the
  validation hook).

## Entities used

None. This task has no persistence.

## Repositories used

None.

## Services used

None — no `@Service` beans. `ResourceServerConfig` and the five properties classes are the entire
surface.

## Unit / integration tests required

Traced to frozen brief "Required Tests":

- **`ProviderPropertiesTest`** — bind-success with valid nested chains/providers; fail-fast (missing
  `chain`, empty `providers`, non-positive `timeoutSeconds`, missing `quorumThreshold`) using the
  `@ActiveProfiles("dev")` + supply-all-other-required-properties technique (amendment #3).
- **`FinalityPropertiesTest`** — bind-success with a valid `enabledChains` list; fail-fast on empty/
  missing list; reflection-based negative test asserting no confirmation-count/threshold-shaped field
  exists on the record (AC7).
- **`ScreeningPropertiesTest`** — bind-success with `enabled=false` and no `baseUrl`/`apiKeySecretName`
  (the `local`-profile shape); bind-success with `enabled=true` and both present; compact-constructor
  failure when `enabled=true` and either is blank/missing.
- **`KmsPropertiesTest`** — bind-success with `keyId` present; fail-fast when blank/missing; shape
  test asserting exactly one record component (AC8).
- **`SnapshotPropertiesTest`** — bind-success with all three fields; fail-fast on each missing field
  individually.
- **`PublicEndpointsTest`** — asserts `PATTERNS` equals exactly the 4-path list (AC4); a
  `ResourceServerConfigIntegrationTest` case (below) asserts no `permitAll` matcher exists outside it.
- **`ResourceServerConfigIntegrationTest`** (Spring Boot slice test, `@WebMvcTest` or full context with
  `InternalTestController` registered):
  - `shouldRequireInternalScopeForWatchAndAttestEndpoints` (named test, package.md §8 → R27): asserts
    all three mirrored internal paths reject missing/under-scoped tokens and accept
    `internal.crypto:write`.
  - Unauthenticated request → 401 with `Content-Type: application/problem+json` body (AC1).
  - Authenticated, wrong/missing scope → 403 with `application/problem+json` body (AC2).
  - Authenticated, `internal.crypto:write` present (alone, and with an extra scope) → reaches the
    controller (AC3, R27 at-least semantics).
  - Actuator/`.well-known` paths → reachable with no `Authorization` header (AC4).
  - Sweep assertion: every request matcher registered on the filter chain is either one of
    `PublicEndpoints.PATTERNS` or requires authentication — nothing else is `permitAll`.
- **`local` boot smoke check**: covered implicitly by `ResourceServerConfigIntegrationTest` running
  under the `local` profile with the placeholder `application.properties` values (AC6) — no separate
  test class needed since it's exercised every time the Spring context loads for the tests above.

## Execution order

Front-loads config surface (nothing here has a schema/dao layer to sequence ahead of it), then the
security layer that depends on it, then tests, then full verification:

1. `application.properties` — add the standard JWKS property and `themistra.crypto.*` placeholder
   keys first, so every properties class below has something to bind against immediately.
2. `ProviderProperties`, `FinalityProperties`, `ScreeningProperties`, `KmsProperties`,
   `SnapshotProperties` — independent of each other, any order.
3. `CryptoServiceApplication` — add `@ConfigurationPropertiesScan` (first task to need it; must follow
   step 2 so there's something to scan, though compiles fine either way).
4. `PublicEndpoints` — no dependencies.
5. `ResourceServerConfig` — depends on `PublicEndpoints` (step 4) existing.
6. `InternalTestController` (test scope) — depends on knowing the real internal API shape
   (`design.md` §4c), not on any main-scope class.
7. Properties tests (`ProviderPropertiesTest` … `SnapshotPropertiesTest`) — depend on steps 1-2.
8. `PublicEndpointsTest` — depends on step 4.
9. `ResourceServerConfigIntegrationTest` — depends on steps 1, 4, 5, 6.
10. `mvn -pl services/crypto verify` — full suite, confirms nothing in T01/T02 regressed and every
    AC1–AC8 test passes.
