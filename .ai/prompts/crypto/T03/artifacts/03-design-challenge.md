<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) adversarial review for crypto · T03. -->

# crypto · T03 · Phase 3 — Design Challenge Findings

**Scope:** Review `artifacts/02-task-implementation-brief.md` (TIB) against `spec/crypto-service/agents.md`, `spec/crypto-service/design.md` §4a/§4c, `spec/crypto-service/requirements.md` R27, and `contracts/api/token-claims.md`.

**Directive:** Do not redesign or implement. Surface hidden assumptions, ambiguities, untestable rules, missing edge cases, conflicts with locked decisions or `agents.md`, unstated dependencies, ordering hazards, and contract mismatches. For each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Finding 1 — Audience validation is unspecified, leaving cross-service token replay undefined

**Issue:** The TIB says the resource server validates "signature + issuer" and requires `internal.crypto:write`, but it does not state whether the JWT `aud` claim must be validated or what the expected audience is.

**Severity:** High.

**Evidence:**
- `contracts/api/token-claims.md` §Path 2 states that for `client_credentials` tokens `aud` is "the same client id" (the caller's client id), as a bare JSON string.
- `agents.md` §Security requires "a valid service-to-service JWT" on internal endpoints, but does not define how a resource server determines the token is intended for *this* service.
- If `aud` is the caller's client id and not a resource indicator, a token minted for another Themistra service could be replayed against crypto-service whenever it carries `internal.crypto:write`.
- The TIB lists only a JWKS URI under `themistra.crypto.*` and omits any `audience` or `issuer-uri` property.

**Recommended brief amendment:**
- Explicitly state whether audience validation is required. If yes, add a config property (e.g., `themistra.security.oauth2.audience=crypto-service` or `themistra.crypto.auth.audience`) and require the resource server to assert it.
- If audience validation is intentionally omitted (platform-wide internal scope model), call that out as an accepted risk with a follow-up task/ADR, because it contradicts the usual "valid service-to-service JWT" semantics.

---

## Finding 2 — `PublicEndpoints` "actuator" is underspecified and dangerously over-permissive

**Issue:** The TIB and `agents.md` both state the public-endpoint allowlist is "actuator + the verification-keys well-known path," but "actuator" is not enumerated. Exposing all `/actuator/**` endpoints would leak secrets and configuration.

**Severity:** High.

**Evidence:**
- Spring Boot Actuator exposes `/actuator/env`, `/actuator/configprops`, `/actuator/beans`, `/actuator/loggers`, `/actuator/threaddump`, and `/actuator/heapdump` by default when enabled.
- `agents.md` §Security says "Secrets (provider API keys, DB creds, KMS key references): injected by External Secrets Operator; none committed" — yet `/actuator/env` would surface injected values at runtime if actuator is public.
- The TIB's AC4 demands an "exhaustive, CI-enforced allowlist," but without an exact list the `PublicEndpoints` test cannot be exhaustive.

**Recommended brief amendment:**
- Replace "actuator" with the exact set of permitted actuator paths, e.g., `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`.
- Require the `PublicEndpoints` test to assert that **no other** `permitAll` matcher exists and that the listed paths match exactly.

---

## Finding 3 — Non-local fail-fast tests cannot run without a documented test config strategy

**Issue:** The TIB requires that missing/invalid config fails startup in "non-local profiles" (dev/staging/prod), and requires one fail-fast test per properties class. But `application.properties` currently pins `spring.profiles.active=local`, and a dev-profile test would also fail on unrelated missing config (DB, Kafka, JWKS), making the tests unstable.

**Severity:** High (testability blocker).

**Evidence:**
- `agents.md` §Configuration: "Config is bound to validated `@ConfigurationProperties` records; startup FAILS on missing/invalid values in non-local profiles. Profiles: local, dev, staging, prod."
- `services/crypto/src/main/resources/application.properties` currently sets `spring.profiles.active=local` and provides only datasource config.
- The TIB does not say how a test should activate a non-local profile while supplying the *other* required properties (DB URL, JWKS URI, etc.) so that only the target properties class fails.

**Recommended brief amendment:**
- Specify the test technique: `@ActiveProfiles("dev")` plus a test-only `@TestPropertySource` or `ApplicationContextInitializer` that supplies valid values for all *other* required config, so each test isolates one missing/invalid field.
- Alternatively, permit fail-fast tests to run with `local` profile and a test-specific override that disables local-only leniency for the class under test, but document the mechanism explicitly.

---

## Finding 4 — `FinalityProperties` conflicts with the L4 locked decision that finality is a policy object, not configurable

**Issue:** The TIB lists a `FinalityProperties` class, but `design.md` L4 states finality is a per-chain *policy object* hardcoded in code (Ethereum = beacon `finalized`; Tron = solidified block). It is unclear what values `FinalityProperties` would even hold.

**Severity:** Medium.

**Evidence:**
- `design.md` §4a L4: "Finality is a per-chain policy object, not a global constant. ... Adding a chain adds a policy object; no confirmation count is hardcoded across chains."
- `design.md` §4c shows the finality policy table as verbatim behavior, not as external configuration.
- If `FinalityProperties` contains confirmation thresholds per chain, it would violate L4. If it contains only an enabled-chains list, the class name is misleading.

**Recommended brief amendment:**
- Clarify what finality-related values are actually configurable in T03. Plausible options: (a) only a list of enabled chains, with finality policy objects still hardcoded; (b) a chain-to-policy-type mapping that selects among hardcoded policy implementations; (c) nothing at all, in which case remove `FinalityProperties` from this task.
- Ensure any configurable values do not allow a global confirmation count.

---

## Finding 5 — `ProviderProperties` shape is undefined while the provider set (Q1) remains unresolved

**Issue:** The TIB asks for validated provider configuration but does not define the structure. Q1 (provider set and quorum N per chain) is unresolved, so implementers cannot know what fields are required.

**Severity:** Medium.

**Evidence:**
- `package.md` §11 Q1: "Provider set & quorum N per chain. Which 3 commercially-independent providers per launch chain ... is N fixed at 3 with 2-of-3, or configurable per chain?"
- `design.md` §4b O1: "Propose the 3 launch providers per chain and whether N/threshold is fixed (3 / 2-of-3) or per-chain configurable."
- The TIB only says "providers" with no field list, cardinality, or per-chain structure.

**Recommended brief amendment:**
- Define the minimum structural shape now, even if provider names are placeholders. For example: `themistra.crypto.providers.<chain>[0].name`, `.url`, `.timeout-seconds`, `.api-key-secret-name` (External Secrets reference, not the key itself), plus a global `quorum.threshold` or `quorum.required-agreement`.
- State that real provider names and URLs are placeholders until Q1 is resolved.

---

## Finding 6 — `ScreeningProperties` has no vendor-agnostic field definition

**Issue:** The TIB requires vendor-agnostic screening configuration (L12), but does not specify what fields `ScreeningProperties` contains. Without Q2 resolved (screening vendor), the class cannot be meaningfully validated.

**Severity:** Medium.

**Evidence:**
- `design.md` §4a L12: "Screening gates attestation, fail-closed. ... Chainalysis/TRM/Elliptic per Q2."
- `package.md` §11 Q2: "Screening provider ... Confirm the vendor and the exact request/response and error semantics so the `screening` client can be pinned."
- The TIB says only "validated `@ConfigurationProperties` for ... screening" with no fields.

**Recommended brief amendment:**
- List the vendor-agnostic fields, e.g., `enabled`, `base-url`, `connect-timeout`, `read-timeout`, `retry.max-attempts`, `api-key-secret-name`. Keep the actual vendor adapter behind an interface, per O4.
- Document that these fields are intentionally generic and will be consumed by a later `ScreeningClient` implementation.

---

## Finding 7 — KMS and snapshot property fields are ambiguous

**Issue:** The TIB describes KMS config as "key id/ARN, region" and S3 snapshot config as "S3 snapshot keys," which is too vague to write validation rules against.

**Severity:** Medium.

**Evidence:**
- `design.md` §4a L11: "Attestation keys are generated in AWS KMS ... Receipts embed the key id."
- A KMS key ARN already embeds region and account; asking for both ARN and region creates redundancy and validation ambiguity (which one wins if they conflict?).
- "S3 snapshot keys" is ambiguous between (a) S3 access credentials (which `agents.md` says must be injected, not committed), (b) bucket/region/prefix config, or (c) encryption key ids.

**Recommended brief amendment:**
- For KMS: specify exactly one identifier property, e.g., `key-id` (for alias/key-id) or `key-arn`, plus `region` only when `key-id` is used. Define validation (ARN regex or key-id pattern).
- For snapshots: rename to `SnapshotProperties` fields such as `bucket`, `prefix`, `region`, `storage-class`, and `endpoint-override` (for local/CI). Clarify that no access keys are committed.

---

## Finding 8 — Auth JWKS URI is placed under the `themistra.crypto.*` namespace

**Issue:** The TIB proposes putting the auth JWKS URI under `themistra.crypto.*`, mixing service-wide security configuration with crypto-domain configuration.

**Severity:** Low-Medium.

**Evidence:**
- The TIB Inputs section: "`application.properties` values under a new `themistra.crypto.*` namespace (providers, finality, screening, KMS, S3-snapshot, and the auth JWKS URI)."
- Auth/security configuration is not crypto-domain specific; it belongs under `themistra.security.*` or `themistra.auth.*`, consistent with how the Auth service names its own config.
- Co-locating the JWKS URI under `crypto` makes it harder to audit which properties are security-critical vs. domain-specific.

**Recommended brief amendment:**
- Move the JWKS URI (and any future audience/issuer properties) to a separate namespace, e.g., `themistra.security.oauth2.resourceserver.jwt.jwk-set-uri` or `themistra.auth.jwks-uri`, while keeping crypto-domain properties under `themistra.crypto.*`.

---

## Finding 9 — Deferring RFC 9457 error handling conflicts with `agents.md` for 401/403 responses

**Issue:** The TIB defers `ApiExceptionHandler` and RFC 9457 error handling to a later task, but Spring Security's default 401/403 responses are plain JSON/HTML, not `application/problem+json`. `agents.md` mandates RFC 9457 for *all* errors.

**Severity:** Medium.

**Evidence:**
- `agents.md` §Security: "Errors are RFC 9457 `application/problem+json` — no stack traces, no internal detail."
- The TIB explicitly excludes `ApiExceptionHandler` from this task and says "defer ... to whichever task first needs a real error response body."
- Because T03 creates the security filter chain, the first 401/403 responses produced by crypto-service will be Spring Security defaults, directly violating the standing rule.

**Recommended brief amendment:**
- Either add a minimal `AuthenticationEntryPoint` and `AccessDeniedHandler` to `ResourceServerConfig` in T03 that emit RFC 9457 problem responses, or explicitly log a temporary deviation with a follow-up task committed before any endpoint is reachable in dev/staging/prod.

---

## Finding 10 — Endpoint matcher `/internal/v1/**` differs from `agents.md`'s `/internal/v1/*`

**Issue:** The TIB uses `/internal/v1/**` while `agents.md` uses `/internal/v1/*`. The two patterns have different AntPathMatcher semantics for nested paths.

**Severity:** Low-Medium.

**Evidence:**
- `agents.md` §Security: "Internal endpoints (`/internal/v1/*`) require a service-to-service JWT ..."
- TIB: "requiring `internal.crypto:write` on `/internal/v1/**`."
- `/internal/v1/*` matches one path segment; `/internal/v1/**` matches zero or more segments. The internal API (watch/attest) has only one-level paths today, but a mismatch risks future endpoints being unintentionally unprotected or over-protected.

**Recommended brief amendment:**
- Align the brief with `agents.md` and use `/internal/v1/*` if the API contract never nests paths, or explicitly justify `/internal/v1/**` and update `agents.md` if nested paths are expected.

---

## Finding 11 — Local profile boot requirements omit security/actuator configuration needed for the resource server

**Issue:** The TIB says `local` must boot with no real credentials, but it only instructs adding `themistra.crypto.*` properties. The resource server cannot start without at least a JWKS URI, and the public-endpoint allowlist cannot be tested without actuator paths configured.

**Severity:** Medium.

**Evidence:**
- Current `services/crypto/src/main/resources/application.properties` contains only datasource and virtual-thread config.
- Spring Security OAuth2 resource server requires `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` (or issuer-uri) to construct a `JwtDecoder`.
- The TIB's AC6 says "`local` profile boots with no real provider/KMS/screening credentials," but does not say the same for auth/security config, nor does it mention that security config must also be placeholder-safe.

**Recommended brief amendment:**
- Add to `application.properties` in T03: a placeholder JWKS URI for local (e.g., `http://localhost:9000/oauth2/jwks` or a local test JWKS), plus actuator exposure settings limited to the paths in Finding 2.
- Clarify AC6 to cover security config placeholders as well.

---

## Finding 12 — Scope semantics (exact vs. at-least) are not stated

**Issue:** The TIB says internal endpoints "require" `internal.crypto:write`, but does not say whether a token carrying additional scopes is allowed.

**Severity:** Low.

**Evidence:**
- `requirements.md` R27: "require a valid service-to-service JWT bearing the `internal.crypto:write` scope and SHALL reject unauthenticated or under-scoped callers."
- "Under-scoped" implies at-least semantics, but the TIB should make this explicit because Spring Security `hasAuthority('SCOPE_internal.crypto:write')` is at-least and a test could be written to require exact match.

**Recommended brief amendment:**
- State explicitly: the endpoint requires the caller to possess `internal.crypto:write`; additional scopes are permitted. Update the security test to include a token with extra scopes that still succeeds.

---

## Finding 13 — Test-only controller may not mirror real endpoint path patterns

**Issue:** The TIB proposes exercising the security filter chain via a test-only `@RestController` mapped under `/internal/v1/**`. If the test controller uses a simpler path than the real watch/attest endpoints, the security wiring might pass the test but fail on the real controllers.

**Severity:** Low.

**Evidence:**
- `design.md` §4c specifies the internal API paths: `POST /internal/v1/watches`, `DELETE /internal/v1/watches/{watchId}`, `POST /internal/v1/attest`.
- The TIB says "a test-only `@RestController` mapped under `/internal/v1/**`" without prescribing paths that exercise HTTP method and path-variable patterns.

**Recommended brief amendment:**
- Require the test controller to expose paths and HTTP methods that mirror the real internal API contract (e.g., `POST /internal/v1/watches`, `DELETE /internal/v1/watches/{watchId}`, `POST /internal/v1/attest`) so the security test validates the actual request-matcher behavior.

---

## Finding 14 — Placeholder values must be distinguishable from real secrets in validation

**Issue:** The TIB says local boots with placeholder/fake values, and L13 says no secrets committed. But a placeholder ARN or API key can still look real and accidentally satisfy validation in non-local profiles.

**Severity:** Low-Medium.

**Evidence:**
- `agents.md` §Configuration: "validated `@ConfigurationProperties` records; startup FAILS on missing/invalid values in non-local profiles."
- `agents.md` §Security: "Secrets ... injected by External Secrets Operator; none committed; gitleaks gate in CI."
- A local placeholder like `arn:aws:kms:us-east-1:123456789:key/00000000-0000-0000-0000-000000000000` matches the ARN format and could be copied into a higher profile without failing validation.

**Recommended brief amendment:**
- Establish a placeholder convention (e.g., values containing `local-only`, `fake-`, or `placeholder`) and require validators to reject placeholder-looking values when a non-local profile is active.
- Alternatively, require all secret-shaped local values to be empty or contain an explicit sentinel so that `@NotBlank` fails them in dev/staging/prod.

---

## Summary of requested brief amendments

| # | Amendment | Priority |
|---|-----------|----------|
| 1 | Decide and document audience validation / expected `aud` value. | High |
| 2 | Enumerate exact actuator paths in the public allowlist. | High |
| 3 | Document the test strategy for non-local fail-fast config tests. | High |
| 4 | Clarify what `FinalityProperties` actually configures. | Medium |
| 5 | Define minimum `ProviderProperties` structure with placeholder names. | Medium |
| 6 | Define vendor-agnostic `ScreeningProperties` fields. | Medium |
| 7 | Pin KMS and snapshot property fields precisely. | Medium |
| 8 | Move auth/security config out of `themistra.crypto.*`. | Low-Medium |
| 9 | Add RFC 9457 entry/denied handlers or log a deviation task. | Medium |
| 10 | Align `/internal/v1/*` vs `/internal/v1/**` with `agents.md`. | Low-Medium |
| 11 | Add placeholder security/actuator config to `application.properties`. | Medium |
| 12 | State scope semantics are at-least. | Low |
| 13 | Require test controller paths to mirror real internal API. | Low |
| 14 | Define a placeholder convention that validators reject in non-local profiles. | Low-Medium |

(End of design challenge review.)
