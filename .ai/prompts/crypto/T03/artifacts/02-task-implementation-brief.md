# crypto · T03 · Phase 2 — Task Implementation Brief (TIB)

## Task

Config & resource server. Validated `@ConfigurationProperties` for providers, finality, screening,
KMS, and S3 snapshot keys (design §4c). Wire service-to-service JWT validation requiring
`internal.crypto:write` on internal endpoints (R27); `PublicEndpoints` allows only actuator + the
verification-keys well-known path.

## Purpose

Give crypto-service the foundation every later task builds on: a validated, fail-fast configuration
surface (so a misconfigured deployment never boots silently) and the resource-server security wiring
that gates every `/internal/v1/*` endpoint before any of those endpoints exist. This is the second
"foundation" task after schema (T02) and precedes everything adapter/quorum/watch/attest-related.

## Scope

**In:**
- Five `@ConfigurationProperties` record classes: providers, finality, screening, KMS, S3 snapshot.
- `PublicEndpoints` (actuator + `/.well-known/themistra-verification-keys` only).
- `ResourceServerConfig` (`SecurityFilterChain`) validating a service-to-service JWT against auth's
  JWKS and requiring `internal.crypto:write` on `/internal/v1/**`.
- `@ConfigurationPropertiesScan` added to `CryptoServiceApplication`.

**Out:**
- `WatchController`, `AttestController`, `VerificationKeysController` (T15/T21/T22).
- `ScreeningClient`, `KmsSigner`, adapter/quorum/finality/token logic (later tasks) — this task only
  validates their *config*, not their behavior.
- `ApiExceptionHandler` / RFC 9457 error handling — listed under `common/` in design §6 but not named
  in this task's own statement; deferred to whichever task first needs a real error response body
  (not decided here — do not build it in this task).
- `contracts/api/crypto-internal.yaml` and any contract tests (T23).

## Business Rules

- **R27.** Calls to `/internal/v1/*` SHALL require a valid service-to-service JWT bearing
  `internal.crypto:write`; unauthenticated or under-scoped callers SHALL be rejected.

## Locked Decisions

- **L13.** No secret (provider key, DB credential, KMS ARN) is committed; validated
  `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles.
- **L11.** This task adds KMS *config* only (key id/ARN, region) — never the signer; nothing here may
  reach toward `kms:Sign`.
- **L12.** Screening config must stay vendor-agnostic (Q2 unresolved) — no vendor-specific field names.
- **L4.** Finality config must be per-chain (Ethereum, Tron), never one flat global value.
- **L15.** Package-by-feature; these classes live under `common/` only (shared plumbing), no feature
  package.

Also load-bearing (agents.md, not restated elsewhere): flat `application.properties` only; profiles
`local`/`dev`/`staging`/`prod`; internal endpoints validated as an OAuth2 resource server against the
**Auth JWKS**; `PublicEndpoints` is an exhaustive, CI-enforced allowlist.

## Dependencies

- `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server`,
  `spring-boot-starter-validation` (both already in `pom.xml`, T01).
- Auth-service's JWKS endpoint (`/oauth2/jwks`) — external HTTP dependency at runtime, no code
  dependency. Token shape validated against: `contracts/api/token-claims.md` Path 2
  (`client_credentials`) — `scope` is a JSON array of strings; Spring's default
  `JwtGrantedAuthoritiesConverter` derives `SCOPE_internal.crypto:write` from it, no custom converter
  needed.
- Jakarta Bean Validation (`@NotBlank`, `@Min`/`@Max`, etc.) on every new properties record.

## Inputs

- `application.properties` values under a new `themistra.crypto.*` namespace (providers, finality,
  screening, KMS, S3-snapshot, and the auth JWKS URI).
- Active Spring profile (`local`/`dev`/`staging`/`prod`) — governs whether missing config is tolerated.
- Inbound HTTP requests to `/internal/v1/**` carrying (or missing) a bearer JWT.

## Outputs

- Application context that fails to start in `dev`/`staging`/`prod` when required config is
  missing/invalid, and starts cleanly in `local` with fake/placeholder values.
- 401 for unauthenticated requests to `/internal/v1/**`; 403 for authenticated-but-under-scoped;
  request reaches past the security layer when scope is present.
- Actuator + `/.well-known/themistra-verification-keys` remain reachable without authentication.

## State Changes

None. No entities, no persistence, no outbox writes — this task is pure config/security wiring.

## Files to Create

- `services/crypto/src/main/java/com/themistra/crypto/common/config/ProviderProperties.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/FinalityProperties.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/ScreeningProperties.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/KmsProperties.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/config/SnapshotProperties.java` (S3
  snapshot keys)
- `services/crypto/src/main/java/com/themistra/crypto/common/PublicEndpoints.java`
- `services/crypto/src/main/java/com/themistra/crypto/common/ResourceServerConfig.java`
- Test classes under `services/crypto/src/test/java/com/themistra/crypto/common/` (one per properties
  class for fail-fast binding, plus a resource-server security test — see Required Tests).

(Exact class names/fields are proposed here for concreteness; Phase 3 design-challenge may adjust.)

## Files to Modify

- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
  `@ConfigurationPropertiesScan`.
- `services/crypto/src/main/resources/application.properties` — add `themistra.crypto.*` keys with
  `local`-safe placeholder/fake defaults.

## Files NOT to Modify

- `V1__chain_baseline.sql`, `V2__crypto_app_role_and_grants.sql`, `ChainBaselineMigrationIntegrationTest`,
  `T01SkeletonRegressionTest` — T01/T02 deliverables, unrelated to this task.
- `pom.xml` — all required dependencies already present (T01); do not add new ones without cause.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R27).** Unauthenticated request to `/internal/v1/**` → 401.
- **AC2 (R27).** Authenticated request lacking `internal.crypto:write` → 403.
- **AC3 (R27).** Authenticated request with `internal.crypto:write` → passes the security layer.
- **AC4 (task statement).** `PublicEndpoints` allowlist = actuator paths +
  `/.well-known/themistra-verification-keys`, exhaustively — nothing else is `permitAll`.
- **AC5 (task statement, L13).** Each of the 5 properties classes fails application boot when a
  required field is missing/invalid in a non-local profile.
- **AC6 (agents.md).** `local` profile boots with no real provider/KMS/screening credentials.

## Required Tests

- **`shouldRequireInternalScopeForWatchAndAttestEndpoints` (package.md §8, → R27).** No
  `WatchController`/`AttestController` exist yet (T15/T21). Resolved scoping decision for this task:
  exercise `ResourceServerConfig`'s filter chain directly against a **test-only** `@RestController`
  mapped under `/internal/v1/**` (test scope only, not shipped in `src/main`), proving the security
  wiring enforces R27 end-to-end now; T15/T21 re-verify the same behavior against the real controllers
  when they land. This is a scoping decision, not a blocker (see Open Questions).
- Config fail-fast tests (one per properties class): missing/invalid required field + non-local active
  profile → context fails to load.
- Config bind-success tests: valid values bind correctly; `local` profile boots with placeholder/fake
  values and no real credentials.
- `PublicEndpoints` sweep test: assert no `permitAll` request matcher exists outside the declared list.
- Security tests: unauthenticated → 401, wrong/missing scope → 403, correct scope → reaches the
  protected resource.

## Constraints

- **Security:** JWT validated against auth's JWKS (signature + issuer), not any locally-held key
  material — crypto-service holds no signing keys. Scope check must reject on missing OR wrong scope,
  not just missing token.
- **Module boundaries (L15):** all new classes under `common/` (or `common/config/`); no feature-module
  package created prematurely.
- **Null handling:** every `@ConfigurationProperties` field that is required must be annotated
  (`@NotBlank`/`@NotNull`/etc.) so binding failure is explicit, not a later `NullPointerException`.
- **Thread-safety / transactions:** not applicable — no mutable shared state, no persistence in this
  task.
- **No secrets committed:** any example/local value in `application.properties` must be an obvious
  placeholder (mirrors T02's `crypto-app-local-only` convention), never a real credential shape.
- **Config format:** flat `application.properties` only — never YAML (agents.md).

## Open Questions

No blockers. (Q1/Q2/Q7 from `package.md` §11 — provider set, screening vendor, KMS key algorithm — are
unresolved but do not block this task: config classes are built vendor/algorithm-agnostic per L12/O1/O4,
and Q7 only affects tasks that consume the algorithm, T20/T22.)
