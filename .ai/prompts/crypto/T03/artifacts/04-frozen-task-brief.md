# crypto · T03 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Human-approved 2026-09-03. Downstream phases (5+) may not renegotiate this brief. Supersedes
`artifacts/02-task-implementation-brief.md`, with the Phase 3 (Kimi) design-challenge amendments below
folded in.

## Task

Config & resource server. Validated `@ConfigurationProperties` for providers, finality, screening,
KMS, and S3 snapshot keys (design §4c). Wire service-to-service JWT validation requiring
`internal.crypto:write` on internal endpoints (R27); `PublicEndpoints` allows only actuator + the
verification-keys well-known path.

## Purpose

Give crypto-service the foundation every later task builds on: a validated, fail-fast configuration
surface (so a misconfigured deployment never boots silently) and the resource-server security wiring
that gates every `/internal/v1/*` endpoint before any of those endpoints exist. Second "foundation" task
after schema (T02), precedes everything adapter/quorum/watch/attest-related.

## Scope

**In:**
- Five `@ConfigurationProperties` record classes: providers, finality (enabled-chains only, see
  Locked Decisions), screening, KMS, S3 snapshot.
- `PublicEndpoints` — exact enumerated paths (see Files to Create), not a blanket actuator allow.
- `ResourceServerConfig` (`SecurityFilterChain`) validating a service-to-service JWT against auth's
  JWKS (via the standard Spring Boot property, not a custom one) and requiring
  `internal.crypto:write` on `/internal/v1/**`, with RFC 9457 entry-point/denied-handler responses.
- `@ConfigurationPropertiesScan` added to `CryptoServiceApplication`.
- `local`-profile placeholder values for JWKS URI, actuator exposure, and every new properties
  namespace, following a documented placeholder naming convention.

**Out:**
- `WatchController`, `AttestController`, `VerificationKeysController` (T15/T21/T22).
- `ScreeningClient`, `KmsSigner`, adapter/quorum/finality-policy/token logic (later tasks) — this task
  only validates their *config*, not their behavior.
- Real provider names/URLs, real screening vendor fields, real KMS key values (Q1/Q2 unresolved —
  generic/placeholder structure only).
- Full RFC 9457 `ApiExceptionHandler` for business-logic errors — only the security
  entry-point/denied-handler pair is in scope here (see Locked Decisions amendment #9); a general
  `@ControllerAdvice` for domain errors is deferred to whichever task first has domain errors to
  report.
- Audience (`aud`) claim validation — explicitly out of scope; see Locked Decisions amendment #1.
- `contracts/api/crypto-internal.yaml` and any contract tests (T23).
- Automated detection/rejection of "placeholder-looking" secret values in non-local profiles (Phase 3
  Finding 14's stronger recommendation) — rejected as unnecessary scope beyond this task; only a
  documented naming convention is in scope.

## Business Rules

- **R27.** Calls to `/internal/v1/*` SHALL require a valid service-to-service JWT bearing
  `internal.crypto:write`; unauthenticated or under-scoped callers SHALL be rejected. Additional
  scopes beyond `internal.crypto:write` on the token are permitted (at-least semantics, not exact
  match) — **[amendment #12]**.

## Locked Decisions

- **L13.** No secret (provider key, DB credential, KMS ARN) is committed; validated
  `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles.
- **L11.** This task adds KMS *config* only (a single `keyId` field — not both ARN and region, which
  are redundant/ambiguous together, **[amendment #7]**) — never the signer; nothing here may reach
  toward `kms:Sign`.
- **L12.** Screening config must stay vendor-agnostic (Q2 unresolved) — no vendor-specific field
  names; generic fields only: `enabled`, `base-url`, `connect-timeout`, `read-timeout`,
  `retry.max-attempts`, `api-key-secret-name` **[amendment #6]**.
- **L4.** Finality config must be per-chain, never one flat global value. **[amendment #4 —
  resolved]:** `FinalityProperties` in this task holds **only an enabled-chains list**
  (e.g. `ETHEREUM`, `TRON`); it must NOT contain confirmation-count or threshold fields — those stay
  hardcoded in the per-chain policy objects built in T14. A test must assert no confirmation-count
  field exists on this class.
- **L15.** Package-by-feature; these classes live under `common/` only (shared plumbing), no feature
  package.

Also load-bearing (agents.md, not restated elsewhere): flat `application.properties` only; profiles
`local`/`dev`/`staging`/`prod`; internal endpoints validated as an OAuth2 resource server against the
**Auth JWKS**; `PublicEndpoints` is an exhaustive, CI-enforced allowlist; **all errors are RFC 9457
`application/problem+json`** — this last rule is why amendment #9 below is in scope, not deferred.

### Amendments folded in from Phase 3 (Kimi) design challenge, human-approved 2026-09-03

1. **Audience validation** — out of scope for T03, by design, not oversight. Verified against
   `contracts/api/token-claims.md` Path 2: for `client_credentials` tokens, `aud` is the **calling
   client's own id** (identical mechanism to `sub`), not a resource-indicator — auth-service's Spring
   Authorization Server default never issues audience-restricted tokens. Requiring `aud ==
   crypto-service` would reject every legitimately-issued token today and would require an
   auth-service change, which is out of scope here. **Authorization for R27 is scope-only.** This is
   a genuine platform-level gap (a token scoped `internal.crypto:write` for one caller could in
   principle be presented by a different internal caller) — flagged as an open question for the
   auth-service/platform spec owner, not a T03 blocker.
2. **Actuator paths enumerated exactly** — `/actuator/health/**`, `/actuator/info`,
   `/actuator/prometheus` (mirrors `services/auth`'s own `PublicEndpoints.PATTERNS`). No blanket
   `/actuator/**`.
3. **Non-local fail-fast test technique specified** — each config fail-fast test uses
   `@ActiveProfiles("dev")` (or equivalent `ApplicationContextRunner` profile activation) plus
   test-supplied valid values for every *other* required property (DB URL, JWKS URI, etc.), so only
   the target field's absence/invalidity is under test.
6. Screening fields — see L12 above.
7. KMS/snapshot fields — KMS: single `keyId` property only. Snapshot: `bucket`, `prefix`, `region`,
   rename class intent stays `SnapshotProperties`. No access keys/credentials as properties (L13).
8. **JWKS URI uses the standard Spring Boot property**, not a custom `themistra.*` key:
   `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`. Spring Boot autoconfigures the
   `JwtDecoder` from this property directly — no custom `JwtDecoder` bean is needed unless the RFC
   9457 entry-point wiring (amendment #9) requires one. This also resolves the namespace-mixing
   concern (JWKS URI is not crypto-domain config).
9. **RFC 9457 for security responses, in this task.** `ResourceServerConfig` must configure a minimal
   `AuthenticationEntryPoint` (401) and `AccessDeniedHandler` (403) that emit
   `application/problem+json` bodies (no stack trace, no internal detail) instead of Spring Security's
   HTML/plain-JSON defaults. This is narrowly scoped to the security layer's own responses — a general
   business-error `ApiExceptionHandler` remains out of scope (see Scope/Out).
10. **`/internal/v1/**` (not `/internal/v1/*`) is correct and intentional.** `DELETE
    /internal/v1/watches/{watchId}` is two path segments below `/internal/v1`; a literal
    single-segment `/internal/v1/*` matcher would not protect it. agents.md's `/internal/v1/*` wording
    is descriptive shorthand, not a strict AntPathMatcher spec — this brief uses `/internal/v1/**`
    deliberately and documents why in code.
11. **Local-profile placeholders required for security config too**, not just `themistra.crypto.*`:
    a placeholder `jwk-set-uri` value and the enumerated actuator exposure must be present in
    `application.properties` so `local` boots cleanly (extends AC6).
12. Scope semantics — see Business Rules above (at-least, not exact).
13. **Test-only controller mirrors the real internal API** exactly: `POST /internal/v1/watches`,
    `DELETE /internal/v1/watches/{watchId}`, `POST /internal/v1/attest` (paths/methods per
    `design.md` §4c), so the named test validates the real request-matcher shape.
14. Placeholder-value convention: local-only values in `application.properties` are named/commented to
    make them obviously non-real (mirrors T02's `crypto-app-local-only` precedent), reviewed by
    humans/gitleaks — **no automated placeholder-detection validation logic** is built (rejected as
    unneeded scope for this task).

## Dependencies

- `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server`,
  `spring-boot-starter-validation` (both already in `pom.xml`, T01).
- Auth-service's JWKS endpoint (`/oauth2/jwks`) — external HTTP dependency at runtime, configured via
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (amendment #8).
- Token shape validated against: `contracts/api/token-claims.md` Path 2 (`client_credentials`) —
  `scope` is a JSON array of strings; Spring's default `JwtGrantedAuthoritiesConverter` derives
  `SCOPE_internal.crypto:write` from it, no custom converter needed. `aud` is explicitly NOT validated
  (amendment #1).
- Jakarta Bean Validation (`@NotBlank`, `@Min`/`@Max`, etc.) on every new properties record.

## Inputs

- `application.properties` values: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (standard
  key) plus `themistra.crypto.*` for providers/finality(enabled-chains)/screening/KMS(`keyId`)/S3
  snapshot — crypto-domain config only, security config kept separate (amendment #8).
- Active Spring profile (`local`/`dev`/`staging`/`prod`) — governs whether missing config is tolerated.
- Inbound HTTP requests to `/internal/v1/**` carrying (or missing) a bearer JWT.

## Outputs

- Application context that fails to start in `dev`/`staging`/`prod` when required config is
  missing/invalid, and starts cleanly in `local` with documented placeholder values.
- `application/problem+json` 401 for unauthenticated requests to `/internal/v1/**`; `application/
  problem+json` 403 for authenticated-but-under-scoped; request reaches past the security layer when
  `internal.crypto:write` (or a superset) is present.
- Actuator (`/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`) and
  `/.well-known/themistra-verification-keys` remain reachable without authentication; nothing else is
  `permitAll`.

## State Changes

None. No entities, no persistence, no outbox writes — this task is pure config/security wiring.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java` —
  generic per-chain provider list (name, url, timeout, api-key-secret-name) + quorum threshold field;
  placeholder values only (Q1 unresolved).
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java` —
  enabled-chains list ONLY (amendment #4/L4). No confirmation-count field.
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java` —
  generic fields per amendment #6/L12.
- `services/crypto/src/main/java/com/themistra/crypto/common/config/KmsProperties.java` — single
  `keyId` field (amendment #7/L11).
- `services/crypto/src/main/java/com/themistra/crypto/common/config/SnapshotProperties.java` —
  `bucket`, `prefix`, `region` (amendment #7).
- `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java` — exact enumerated
  paths (amendment #2).
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java` — includes
  RFC 9457 `AuthenticationEntryPoint`/`AccessDeniedHandler` (amendment #9).
- Test classes under `services/crypto/src/test/java/com/themistra/crypto/common/` (one per properties
  class for fail-fast binding using the `@ActiveProfiles` technique from amendment #3, plus a
  resource-server security test using a test-only controller mirroring the real internal API per
  amendment #13).

## Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
  `@ConfigurationPropertiesScan`.
- `services/crypto/src/main/resources/application.properties` — add
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (placeholder for `local`) and
  `themistra.crypto.*` keys with `local`-safe placeholder/fake defaults, all following the naming
  convention in amendment #14.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `ChainBaselineMigrationIntegrationTest`,
  `T01SkeletonRegressionTest` — T01/T02 deliverables, unrelated to this task.
- `pom.xml` — all required dependencies already present (T01); do not add new ones without cause.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R27).** Unauthenticated request to `/internal/v1/**` → 401, `application/problem+json` body.
- **AC2 (R27).** Authenticated request lacking `internal.crypto:write` → 403,
  `application/problem+json` body.
- **AC3 (R27).** Authenticated request with `internal.crypto:write` (with or without extra scopes) →
  passes the security layer.
- **AC4 (task statement, amendment #2).** `PublicEndpoints` allowlist = exactly
  `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus`,
  `/.well-known/themistra-verification-keys` — nothing else is `permitAll`.
- **AC5 (task statement, L13).** Each of the 5 properties classes fails application boot when a
  required field is missing/invalid in a non-local profile, isolated per amendment #3's technique.
- **AC6 (agents.md, amendment #11).** `local` profile boots with no real provider/KMS/screening
  credentials AND no real JWKS URI/actuator secrets.
- **AC7 (L4, amendment #4).** `FinalityProperties` contains no confirmation-count/threshold field —
  asserted by test.
- **AC8 (L11, amendment #7).** `KmsProperties` exposes exactly one key-identifying field (`keyId`).

## Required Tests

- **`shouldRequireInternalScopeForWatchAndAttestEndpoints` (package.md §8, → R27).** Exercised against
  a test-only `@RestController` (test scope only) whose paths/methods mirror the real internal API
  exactly: `POST /internal/v1/watches`, `DELETE /internal/v1/watches/{watchId}`,
  `POST /internal/v1/attest` (amendment #13). T15/T21 re-verify the same behavior against the real
  controllers when they land.
- Config fail-fast tests (one per properties class): missing/invalid required field + non-local active
  profile, all other required config supplied → context fails to load (amendment #3 technique).
- Config bind-success tests: valid values bind correctly; `local` profile boots with placeholder/fake
  values and no real credentials, including JWKS URI and actuator config (amendment #11).
- `PublicEndpoints` sweep test: assert no `permitAll` request matcher exists outside the exact
  4-path list in AC4.
- Security tests: unauthenticated → 401 problem+json, wrong/missing scope → 403 problem+json, correct
  scope (exact or with extra scopes) → reaches the protected resource (AC1–AC3, amendment #12).
- `FinalityProperties` negative test: asserts the class has no confirmation-count-shaped field (AC7).
- `KmsProperties` shape test: asserts exactly one key-identifying field (AC8).

## Constraints

- **Security:** JWT validated against auth's JWKS (signature + issuer) via the standard Spring Boot
  property; audience is explicitly NOT validated (amendment #1 — documented decision, not an
  oversight). Scope check must reject on missing OR wrong scope, accept on the required scope plus any
  extras.
- **Module boundaries (L15):** all new classes under `common/` (or `common/config/`); no feature-module
  package created prematurely.
- **Null handling:** every `@ConfigurationProperties` field that is required must be annotated
  (`@NotBlank`/`@NotNull`/etc.) so binding failure is explicit, not a later `NullPointerException`.
- **Thread-safety / transactions:** not applicable — no mutable shared state, no persistence in this
  task.
- **No secrets committed:** any example/local value in `application.properties` must be an obvious
  placeholder per the naming convention (amendment #14), never a real credential shape.
- **Config format:** flat `application.properties` only — never YAML (agents.md).
- **Error format:** RFC 9457 `application/problem+json` for the security layer's own 401/403
  responses (amendment #9); no stack traces, no internal detail.

## Open Questions

**Resolved or explicitly deferred — none block T03 implementation:**
- Q1/Q2/Q7 (`package.md` §11 — provider set, screening vendor, KMS key algorithm): unresolved but
  non-blocking; config classes are vendor/algorithm-agnostic per L12/O1/O4.
- **Platform-level gap (new, from amendment #1):** auth-service's `client_credentials` tokens carry no
  resource-audience claim, so scope alone is the only authorization boundary between internal callers.
  Deferred to the auth-service/platform spec owner as a follow-up question — not a T03 blocker, and not
  fixable within this task's scope.
