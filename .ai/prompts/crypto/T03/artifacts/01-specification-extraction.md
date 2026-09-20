# crypto · T03 · Phase 1 — Specification Extraction

## Business Rules

- **R27.** Calls to the internal watch or attest endpoints SHALL require a valid service-to-service JWT
  bearing the `internal.crypto:write` scope; unauthenticated or under-scoped callers SHALL be rejected.

No other numbered requirement (R1–R26, R28) is testable by this task's own statement. R24
(publish verification keys at the well-known URL) belongs to T22 — this task's only obligation toward
it is that `PublicEndpoints` must already list that path as public, since the controller that will live
behind it doesn't exist yet. R28 (contract conformance) is out of scope — `contracts/api/crypto-internal.yaml`
doesn't exist until T23.

## Locked Decisions

Derived from `design.md` §4a (task header states none were cited inline):

- **L13. Secrets discipline** — the core locked decision this task implements. No provider API key, DB
  credential, or KMS key ARN may be committed; External Secrets Operator injects them; validated
  `@ConfigurationProperties` must fail startup on missing/invalid config in non-local profiles.
- **L11. KMS-only signing, single path** — this task only adds **KMS config properties** (e.g. key
  id/ARN, region), not the signer itself (T20). The properties class must not leak into or enable
  anything beyond config binding; the actual `kms:Sign` call boundary is a later task's ArchUnit
  concern (T20/T25), but naming/shape chosen here should not presuppose a package that violates it.
- **L12. Screening gates attestation, fail-closed** — this task adds screening config properties
  behind the still-open vendor choice (Q2); the *config* must not assume a specific vendor's field
  names, since `ScreeningClient`'s real adapter is deferred (O4).
- **L4. Finality is a per-chain policy object, not a global constant** — constrains the *shape* of the
  finality `@ConfigurationProperties`: it must be structured to hold per-chain values (at minimum
  Ethereum + Tron), never a single flat confirmation-count field.
- **L15. Module boundaries** — package-by-feature under `com.themistra.crypto`; shared plumbing lives
  only in `common` (where this task's classes live). No feature module import from `common` in the
  other direction.

Also directly governing (agents.md, authoritative, not restated in design §4a but load-bearing for this
task specifically):
- **Configuration** — flat `application.properties` only, never YAML; profiles are `local`, `dev`,
  `staging`, `prod`.
- **Security** — internal endpoints (`/internal/v1/*`) require a service-to-service JWT validated as an
  OAuth2 resource server against the **Auth JWKS**; the public-endpoint set is an exhaustive,
  CI-enforced allowlist (`PublicEndpoints`: actuator + the verification-keys well-known path).
- **Errors** — RFC 9457 `application/problem+json`, no stack traces, no internal detail (bears on
  whether `ApiExceptionHandler` is in this task's scope — see Open Questions).

## Files involved

**Existing — read/extend:**
- `services/crypto/pom.xml` — resource-server, validation, KMS starters already present (T01); no
  new dependency expected.
- `services/crypto/src/main/resources/application.properties` — add `themistra.crypto.*` keys; existing
  datasource/flyway/virtual-thread lines untouched.
- `services/crypto/src/main/java/com/themistra/crypto/CryptoServiceApplication.java` — add
  `@ConfigurationPropertiesScan` (first task to introduce any `@ConfigurationProperties` class, per its
  own doc comment).

**New — expected by design.md §6 package map, under `common/`:**
- `common/config/*Properties.java` — validated `@ConfigurationProperties` records for providers,
  finality, screening, KMS, and S3 snapshot keys (exact class names/shape are Phase 2 design work, not
  fixed by the spec text itself).
- `common/PublicEndpoints.java` — actuator + `/.well-known/themistra-verification-keys` only.
- `common/ResourceServerConfig.java` — `SecurityFilterChain` wiring JWT validation +
  `internal.crypto:write` scope requirement on `/internal/v1/*`.
- `common/ApiExceptionHandler.java` — listed in design §6's file map under `common/`, but not named in
  this task's own statement text; flagged as an open scoping question below rather than assumed in.

**Reference only (no import/dependency, pattern precedent from `services/auth`):**
`token/SecurityChainsConfig.java`, `token/JwksConfig.java`, `common/PublicEndpoints.java`,
`apikey/ApiKeyProperties.java`, `token/SigningKeysProperties.java` — auth is an issuer with local key
material; crypto is a pure resource server validating a remote JWKS, so these inform shape, not code.

## Dependencies

- **Config namespace:** `themistra.crypto.*` (mirrors auth's `themistra.auth.*` convention) — exact
  sub-keys for providers/finality/screening/kms/s3-snapshot are Phase 2 design decisions.
- **Auth JWKS endpoint:** crypto must be configured with the URI of auth-service's `/oauth2/jwks` (no
  repo precedent yet for the property key name or default value — see Open Questions).
- **Spring classes:** `JwtDecoder` (`NimbusJwtDecoder.withJwkSetUri(...)`), default
  `JwtAuthenticationConverter` (reads the `scope` claim, per `contracts/api/token-claims.md` Path 2 —
  JSON array of strings — into `SCOPE_*` authorities; no custom converter needed unlike auth's
  `JwtRoleAuthoritiesConverter`), `SecurityFilterChain`, `@EnableWebSecurity`, `@EnableMethodSecurity`
  (only if method-level `@PreAuthorize` is used instead of/alongside `authorizeHttpRequests`).
- **Jakarta Bean Validation:** `@NotBlank`, `@NotNull`, `@Min`/`@Max` etc. on the new
  `@ConfigurationProperties` records, `@Validated` on each class — same idiom as
  `ApiKeyProperties`/`SigningKeysProperties`.
- **Entities/repositories:** none — this task is pure config/security wiring, no persistence.
- **Contracts:** none consumed or produced by this task. `contracts/api/crypto-internal.yaml` (T23) and
  the well-known verification-keys response shape (`design.md` §4c) are referenced only to know which
  path must stay in `PublicEndpoints`, not implemented here.
- **No existing controller depends on `ResourceServerConfig` yet** — `WatchController` (T15) and
  `AttestController` (T21) don't exist. This task's security wiring necessarily has no real internal
  endpoint to protect until those land (see Open Questions and Tests below).

## Acceptance Criteria

Mapped to R27 (the only numbered requirement in scope) plus the task statement's own unnumbered
sub-clauses:

- **AC1 (R27).** A request to an `/internal/v1/*` path with no `Authorization` bearer token is
  rejected (401).
- **AC2 (R27).** A request with a structurally valid JWT (correct issuer/signature) but missing or
  under-scoped `internal.crypto:write` is rejected (403).
- **AC3 (R27).** A request with a valid JWT carrying `internal.crypto:write` passes the security layer
  (reaches the controller, or — absent a real controller yet — reaches a point provably past
  authentication/authorization).
- **AC4 (task statement, `PublicEndpoints`).** The public allowlist contains exactly the actuator
  paths and `/.well-known/themistra-verification-keys`, and nothing else is `permitAll`.
- **AC5 (task statement, config).** Each of the five `@ConfigurationProperties` areas (providers,
  finality, screening, KMS, S3 snapshot) binds successfully from valid properties and **fails
  application startup** when required fields are missing/invalid in a non-local profile (agents.md
  Configuration rule, L13).
- **AC6 (agents.md Configuration rule).** The `local` profile boots without requiring real
  provider/KMS/screening credentials (agents.md: "Local dev runs against Docker Compose ... and
  scripted fake provider adapters — real RPC providers are never called in tests or CI").

## Tests required

From `package.md` §8, scoped to this task:
- **`shouldRequireInternalScopeForWatchAndAttestEndpoints` → R27.**

Boundary tests implied by the task statement and agents.md, not separately named in §8 but necessary to
cover AC4–AC6 and the LOCKED decisions above:
- Config-validation-fails-fast test(s) for each new `@ConfigurationProperties` class (missing/invalid
  field → `ApplicationContextRunner` or profile-scoped boot fails) — covers L13/AC5.
- Config-binds-successfully test(s) for valid input, including the `local` profile booting with no
  real credentials — covers AC6.
- `PublicEndpoints` exhaustiveness/sweep test asserting no `permitAll` exists outside the declared list
  — mirrors auth's own stated intent ("a future sweep test asserts no permitAll exists outside this
  list") — covers AC4.
- Security-layer unauthenticated/under-scoped/correctly-scoped tests (401/403/pass) — covers AC1–AC3
  and is the substance of the named test, pending resolution of Open Question 1 below on what it
  actually points requests at.

## Open Questions

1. **The named test targets endpoints that don't exist yet.** `shouldRequireInternalScopeForWatchAndAttestEndpoints`
   implies real requests to `POST/DELETE /internal/v1/watches` and `POST /internal/v1/attest`, but
   `WatchController` (T15) and `AttestController` (T21) are not built until much later. Genuine blocker
   for literally satisfying the named test as worded; Phase 2 needs to decide whether this task (a)
   builds the security config against a minimal test-only protected route to prove the filter chain
   behavior now, with the full named test re-asserted/extended once the real controllers exist, or (b)
   defers any test with this exact name to T15/T21 and this task's own tests use different names. Not
   resolvable from the spec text alone — flagging rather than deciding, per the Phase 1 instruction not
   to design.
2. **No repo precedent for configuring a remote JWKS URI.** Auth-service builds its `JwtDecoder` from
   local key material (`JwksConfig`); no service in this repo has ever pointed at *another* service's
   JWKS endpoint. The property key name/default and whether it differs per profile (`local` vs
   `dev`/`staging`/`prod`) is undecided.
3. **Q1 (`package.md` §11) — provider set unresolved.** Blocks pinning vendor-specific fields on the
   providers `@ConfigurationProperties` class; a generic, vendor-agnostic structure (per O1's
   `ProviderSet` abstraction) can still be built without the answer.
4. **Q2 (`package.md` §11) — screening vendor unresolved.** Same shape risk as Q1 for the screening
   properties class; blocks exact field names (Chainalysis/TRM/Elliptic request shapes differ).
5. **Q7 (`package.md` §11) — KMS key algorithm unresolved.** Likely low-risk for this task specifically
   (a key id/ARN + region is enough to validate config presence); the algorithm choice is consumed by
   T20/T22, not by config validation itself — noted, not treated as a blocker.
6. **Is `ApiExceptionHandler` in scope for T03?** `design.md` §6 lists it under `common/` alongside the
   three files the task statement does name explicitly, but the task statement's own text is silent on
   RFC 9457 error handling. Needs an explicit scoping decision in Phase 2 rather than being assumed
   either way.
