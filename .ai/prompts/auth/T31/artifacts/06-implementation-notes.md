<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T31 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan. One
deviation from the plan was forced by reality — flagged below, not hidden.

## Deviation forced by reality: Bucket4j dependency coordinates

The frozen brief and Phase 5 plan both specified `com.bucket4j:bucket4j_jdk17-core:8.10.1`. That
exact combination **does not exist on Maven Central** — verified two ways: `mvn dependency:get`
failed with a 404, and Maven Central's search API confirms `bucket4j_jdk17-core` is versioned
independently from the general `bucket4j-core` artifact (the one that actually reaches 8.10.1) and
its own latest is **8.14.0**. The earlier Phase 2 verification had genuinely confirmed Bucket4j
8.10.1 exists and resolves — just for the wrong artifact ID (`bucket4j-core`, not
`bucket4j_jdk17-core`); the version number was carried over to the JDK17-specific artifact without
re-checking it independently. Corrected to `com.bucket4j:bucket4j_jdk17-core:8.14.0` in `pom.xml`,
resolved and verified against the jar's actual class files (`io.github.bucket4j.Bucket`,
`Bandwidth`, `Refill`, `ConsumptionProbe` — note the Java package is `io.github.bucket4j`, distinct
from the `com.bucket4j` Maven groupId) before writing any code against it, so every API call in
`RateLimiter`/`RateLimitFilter` is confirmed against the real, resolved jar, not assumed.

## What changed

**`pom.xml`** — added the corrected Bucket4j dependency.

**`application.properties`** — added the three `themistra.auth.rate-limit.*` keys with the
Phase 2/4-approved defaults (10/5/30 per minute), each with an env-var override matching this
file's existing convention.

**`common/ProblemTypes.java`** — added `RATE_LIMIT_EXCEEDED`.

**New `ratelimit` package:**
- `RateLimitProperties.java` — validated record, matches `ApiKeyProperties`/`CleanupProperties` style.
- `RateLimiter.java` — three independent `ConcurrentHashMap<String, Bucket>` registries with
  token-bucket `Bandwidth`s computed once at construction (Phase 3 Finding 7's resolution).
- `CachedBodyHttpServletRequest.java` — package-private request wrapper solving the concrete
  problem Phase 5 identified: reads and fully buffers the body once, so both this filter and the
  real controller's `@RequestBody` binding can each read it independently afterward.
- `RateLimitFilter.java` — the `OncePerRequestFilter` implementing D1-D5's exact resolutions:
  login/password-reset/`/oauth2/token` path matching; D2's zero-DB-access raw-email login key; D3's
  token-hash keys for password-reset and `/oauth2/token`; the fail-open try/catch around all
  key-derivation and limiter calls (D5); the `ProblemDetail` + `Retry-After` 429 response (Finding 5).
- `RateLimitFilterConfig.java` — declares the filter as a bean and immediately disables Spring
  Boot's automatic global-filter registration for it via a `FilterRegistrationBean` with
  `setEnabled(false)` — the standard, documented technique for a filter meant to run only inside
  explicitly-wired `SecurityFilterChain`s.

**`token/SecurityChainsConfig.java`** — both chain-builder methods now take `RateLimitFilter` as a
parameter. Chain 1 (`authorizationServerChain`) adds it via
`.addFilterBefore(rateLimitFilter, DisableEncodeUrlFilter.class)` — the earliest possible position,
so an exhausted `/oauth2/token` bucket is rejected before SAS does any token-processing work at
all. Chain 2 (`applicationChain`) adds it via
`.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)` — precisely before
the filter that actually invokes `TotpAuthenticationProvider`'s password/TOTP verification,
satisfying D4 exactly for `/login`, and incidentally early enough for `/accounts/password-reset`
(a public endpoint with no credential-validation step of its own) too.

## Mapping to the plan

Matches the Phase 5 plan's file list and method signatures exactly, aside from the Bucket4j
version correction above. The plan's own deferred questions (password-reset key shape, filter
double-registration prevention) are resolved exactly as the plan itself specified.

## Mapping to acceptance criteria

- **AC1-AC3** — `RateLimitFilter`'s three path-matching branches, each calling the corresponding
  `RateLimiter.tryConsume*` method.
- **AC4** — each `tryConsume*` method's `ConcurrentHashMap` is keyed independently per call; no
  shared state between different keys.
- **AC5** — `Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)))` gives
  each bucket a genuine token-bucket refill, not a one-time exhaustion.
- **AC6** — no code or comment in this task claims to replace ingress-level protection; the class
  Javadocs explicitly describe it as a backstop (R42).
- **AC7** — `writeTooManyRequests` builds a `ProblemDetail` (byte-identical JSON shape to every
  other rejection in this codebase, since Spring Boot's own Jackson mixin handles the
  serialization) and sets `Retry-After` from `probe.getNanosToWaitForRefill()`.
- **AC8** — `isOAuthTokenRefreshRequest` only matches when `grant_type=refresh_token`; a
  `client_credentials` or `authorization_code` request never reaches the bucket-check branch at
  all. `password`/implicit/device-code/token-exchange grants don't exist in this deployment
  (Finding 9), needing no handling.
- **AC9 (D5)** — the entire key-derivation-and-consume block is inside one try/catch; any
  exception logs and leaves `probe` as `null`, which `doFilterInternal`'s subsequent check treats
  identically to "never checked" — the request proceeds normally.

## Verification performed this phase

- Bucket4j API calls (`Bucket.builder().addLimit(...).build()`, `Bandwidth.classic(...)`,
  `Refill.greedy(...)`, `ConsumptionProbe.isConsumed()`/`getNanosToWaitForRefill()`) verified
  directly against the resolved 8.14.0 jar's class files via `javap`, not assumed from memory of
  an older Bucket4j version — the package name (`io.github.bucket4j`) in particular differs from
  what a naive guess based on the Maven groupId (`com.bucket4j`) would produce.
- `DisableEncodeUrlFilter`/`UsernamePasswordAuthenticationFilter` confirmed to exist in the
  resolved `spring-security-web` jar before using them as `addFilterBefore` anchors.
- `mvn -pl services/auth -am clean compile` — clean, no errors.
- `mvn -pl services/auth test -fn` (full suite, fail-never) — **643 tests run, 0 failures**, 124
  errors, all traced individually to the same 17 pre-existing Testcontainers-backed integration
  test classes (`SessionIntegrationTest`, `SasLoginIntegrationTest`, every `*PersistenceIntegrationTest`,
  every `ApiKey*IntegrationTest`, etc.) — the exact, expected Docker-unavailability signature
  carried through this entire session, not a new regression. No test class outside that known set
  reported any error or failure.

No test code was written in this phase, per the guardrails — Phase 10's job.

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
