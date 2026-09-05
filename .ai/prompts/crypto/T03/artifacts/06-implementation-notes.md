# crypto · T03 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). Only `src/main` files touched — no tests (Phase 10 scope,
per this phase's own rule).

## Files created

- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java` —
  `@ConfigurationProperties(prefix = "themistra.crypto.providers")`, nested `chains[]` → `providers[]`
  records with `@Valid` cascading, `quorumThreshold`. Vendor-name-agnostic per L12/Q1.
- `.../common/config/FinalityProperties.java` — `enabledChains` list only; no confirmation-count
  field, per L4/frozen-brief amendment #4.
- `.../common/config/ScreeningProperties.java` — `enabled` + generic fields; compact-constructor
  validation (not `@NotBlank`) enforces `baseUrl`/`apiKeySecretName` only when `enabled=true`, since
  `local` must boot with screening disabled.
- `.../common/config/KmsProperties.java` — single `keyId` field only (L11/amendment #7 — no ARN +
  region redundancy).
- `.../common/config/SnapshotProperties.java` — `bucket`/`prefix`/`region`; no credentials.
- `.../common/PublicEndpoints.java` — exact 4-path allowlist (amendment #2/AC4): the three named
  actuator paths plus the well-known verification-keys path.
- `.../common/ResourceServerConfig.java` — one `SecurityFilterChain`: `PublicEndpoints.PATTERNS` →
  `permitAll`; `/internal/v1/**` → `hasAuthority("SCOPE_internal.crypto:write")`; everything else →
  `authenticated()`. Stateless session policy + CSRF disabled (bearer-only API, no cookies — see
  Deviations below for why this wasn't in the plan but is in scope). RFC 9457
  `AuthenticationEntryPoint`/`AccessDeniedHandler` beans replace Spring Security's HTML/plain-text
  401/403 defaults (amendment #9). No custom `JwtDecoder` — relies on Spring Boot's
  autoconfiguration from `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (amendment #8). No
  custom `JwtAuthenticationConverter` — default `scope`-claim → `SCOPE_*` mapping is used as-is;
  `aud` is not validated (amendment #1, documented in the class Javadoc).

## Files modified

- `CryptoServiceApplication.java` — added `@ConfigurationPropertiesScan` (first task needing it, per
  its own T01 doc comment); updated the comment to reflect that `@EnableScheduling`/
  `@EnableSchedulerLock` are still absent (unrelated to this task, untouched).
- `application.properties` — added `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (points at
  the local auth-service instance, `http://localhost:8080/oauth2/jwks`, matching
  `services/auth/application.properties`'s own `server.port=8080`) and the five
  `themistra.crypto.*` blocks with placeholder values, each commented with its rationale. Screening
  is `enabled=false` locally (the valid shape); providers use obviously-fake names/URLs/ports; KMS
  and snapshot use `local-only-fake-*`-prefixed placeholders per the naming convention (amendment
  #14).

## Mapping to acceptance criteria

- **AC1/AC2/AC3 (R27)** — `ResourceServerConfig`'s `authorizeHttpRequests` block + scope authority
  check. Not yet exercised by an automated test (Phase 10); reasoned through the Spring Security
  DSL directly: `/internal/v1/**` requires `SCOPE_internal.crypto:write`; any additional scope on the
  token is irrelevant to `hasAuthority`, which only checks presence (at-least semantics, amendment
  #12) — no code needed beyond the single `hasAuthority` call to satisfy this.
- **AC4** — `PublicEndpoints.PATTERNS` contains exactly the 4 required entries, verified by direct
  inspection (not yet by the Phase-10 sweep test).
- **AC5/L13** — every properties record has `@Validated` + Jakarta constraints (`@NotBlank`/
  `@NotEmpty`/`@Min`) on every field that must be present, so Spring's binder fails context
  refresh on a missing/invalid value regardless of active profile — "non-local profiles" isn't
  encoded as separate logic; the constraints are unconditionally enforced, and it's the `local`
  profile's own property *values* (below) that are what make it the profile where binding
  succeeds, not a code branch. `ScreeningProperties`'s conditional requirement is the one case
  needing a compact-constructor check instead of a plain annotation.
- **AC6** — `application.properties`'s new block supplies valid values for every new properties
  class, including `themistra.crypto.screening.enabled=false` for the only field with a
  conditional requirement, so `local` boots without needing real provider/screening/KMS credentials
  or a real JWKS endpoint reachable at build time (the JWKS URI only needs to be *reachable at
  runtime when a token actually arrives*, not at Spring context startup — `NimbusJwtDecoder` fetches
  lazily).
- **AC7 (L4)** — `FinalityProperties` has exactly one component (`enabledChains`); no
  confirmation/threshold field exists to violate the rule.
- **AC8 (L11)** — `KmsProperties` has exactly one component (`keyId`).

## Verification performed this phase

- `mvn -pl services/crypto -am compile` — clean, no errors.
- `mvn -pl services/crypto -am test-compile` — clean; confirms this change doesn't break T01/T02's
  existing test sources.
- Docker was not running in this environment, so a full `ApplicationContext` boot smoke test (which
  would need Postgres reachable for the Hikari pool) could not be run in this phase. This is a real
  limitation, not hidden: **Phase 10's test suite is what will actually prove the context loads and
  the security filter chain behaves as designed** — this phase's own verification is compilation +
  direct code inspection against each AC, not a running assertion. Flagging rather than claiming
  more than was actually checked.

## Deviations from the Phase 5 plan (flagged, not hidden)

1. **`ResourceServerConfig` gained a `sessionManagement(...STATELESS)` call not listed in the Phase 5
   plan's method-signature sketch.** The plan's sketch only showed the `authorizeHttpRequests` +
   `oauth2ResourceServer` wiring. Adding `SessionCreationPolicy.STATELESS` is standard practice for a
   pure bearer-token resource server with no login/session concept anywhere in this service (unlike
   auth-service, which genuinely has session-backed pages) — this is directly part of "wire
   service-to-service JWT validation" (the task statement's own words), not scope creep into another
   task. No brief section forbids it, and it doesn't touch any file outside what was authorized.
2. **CSRF is disabled globally** (`csrf.disable()`), rather than exempted per-path as auth does. Auth
   exempts specific paths because it *also* has session-backed login/authorize pages elsewhere in the
   same application; crypto-service has no such pages anywhere, so a global disable is the correct
   scope here, not a narrower mirror of auth's pattern. Documented inline in the config class.
3. **No `local` boot smoke test was actually run** (see Verification above) — Docker unavailable in
   this environment. This doesn't block the implementation itself (compilation + constraint
   inspection give reasonable confidence), but it means AC5/AC6's "fails/boots correctly" claims are
   reasoned, not yet empirically observed. Phase 10 must close this gap with a real test run.

No other deviations. Every file touched is on the Phase 5/frozen-brief list; no file outside it was
created or modified.
