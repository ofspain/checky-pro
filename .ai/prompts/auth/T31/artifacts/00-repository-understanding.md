<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T31 · Phase 0 — Repository Understanding

## 1. Architecture summary

Same `auth-service` stack as every prior task. Two Spring Security filter chains
(`SecurityChainsConfig.java`), traced directly from the actual bean wiring (not assumed from
comments — see §5 for a discrepancy found this way):

- **Chain 1 (`@Order(1)`, `authorizationServerChain`)** — `securityMatcher` scoped to
  `OAuth2AuthorizationServerConfigurer.authorizationServer().getEndpointsMatcher()`, i.e. the real
  SAS protocol surface: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/jwks`,
  `/userinfo`, `/.well-known/*`. This is SAS's own internal filter/provider machinery.
- **Chain 2 (`@Order(2)`, `applicationChain`)** — no `securityMatcher`, so it's the catch-all for
  everything chain 1 doesn't claim. This is where `.formLogin(...)` is actually registered
  (`LoginFailureHandler`/`LoginSuccessHandler`/`TotpAuthenticationDetailsSource`), along with every
  `AccountController` endpoint and this service's own resource-server JWT validation.

**Corrects an existing comment**: `PublicEndpoints.java`'s own Javadoc says "SAS protocol endpoints
(`/oauth2/*`, `/.well-known/*`, `/login`) are governed by their own filter" — grouping `/login`
with the SAS-protocol set. Tracing the actual `SecurityFilterChain` bean definitions shows `/login`
is on **chain 2**, not chain 1: chain 1's `securityMatcher` is exactly SAS's endpoints matcher
(which doesn't include `/login`), and `.formLogin(...)` is registered on chain 2's `http` builder.
Not a T31 concern to fix, but important to get right for this task's own design, since it means the
four R41 paths are split across two different filter chains, not one.

## 2. Existing code this task touches

**R41's four named paths, resolved against actual routes (not assumed):**

- **"Login"** → `POST /login` (chain 2, `formLogin`'s default processing URL, unconfigured
  elsewhere in `SecurityChainsConfig`).
- **"`/oauth2/token`"** → chain 1, SAS's own token endpoint, internal to
  `OAuth2AuthorizationServerConfigurer` — this service does not own a controller for it.
- **"Password-reset confirmation"** → `POST /accounts/password-reset` (chain 2,
  `AccountController.passwordReset`, already in `PublicEndpoints.METHOD_SCOPED`).
- **"MFA verification"** → **there is no separate MFA-verification endpoint.** Grepped the entire
  `mfa` package: no `@RestController`/`@PostMapping` exists there at all. Traced
  `TotpAuthenticationProvider.authenticate(...)` (`authn` package): password AND
  TOTP/recovery-code are both verified inside the **same** `/login` POST, in one
  `AuthenticationProvider` call. So R41's "login" and "MFA verification" are, in this codebase's
  actual implementation, **the same HTTP path** — not two independently rate-limitable routes.
  This is a real, structural finding for Phase 1/2, not an assumption.

**Existing per-account-identification precedent, directly reusable**: `LoginFailureHandler.recordFailure`
(`authn` package) already resolves "which account" from a raw login POST via
`request.getParameter("username")` → `accountService.findLoginView(email)` — the exact
before-authentication-completes account-resolution problem T31's login-path rate limiting would
also need to solve, already solved once for L4's lockout mechanism.

**No rate-limiting code or dependency exists anywhere in this codebase today** — grepped `pom.xml`
and all of `services/auth/src/main/java` for `bucket4j`/`ratelimit`/`RateLimiter`: no hits. Also
checked the local Maven cache: no Bucket4j artifacts cached (unlike T30's ShedLock, which happened
to already be resolved locally) — this will be a genuinely fresh dependency resolution if Bucket4j
is the chosen library.

**`lockout_state` table (V1) already exists** and already provides a *related but distinct*
defense: L4's failure-count-based account lockout (5 failed attempts/30 min → 15 min lock,
doubling). R41's rate limiting is a *request-rate* throttle (429 on exceeding a request-per-window
threshold), not a failure-count lock — the two mechanisms are complementary, not the same thing,
and R41 doesn't ask this task to touch `lockout_state` or `LockoutService` at all.

## 3. Established patterns to follow

- **Configuration**: flat `application.properties`, `@ConfigurationProperties` records with
  Jakarta Validation (`ApiKeyProperties`, `CleanupProperties` precedent).
- **Error responses**: RFC 9457 `application/problem+json` via a `@RestControllerAdvice` +
  `ProblemTypes` constant, for controller-thrown exceptions (`AccountExceptionHandler`,
  `ApiKeyExceptionHandler`, `SessionExceptionHandler` precedent) — but a 429 triggered by a
  **filter** (before any controller method runs, likely necessary for `/login` and `/oauth2/token`
  since neither is a controller method this service owns) can't go through that same
  advice-based mechanism; it would need to write the `application/problem+json` body directly onto
  the `HttpServletResponse`, a different established pattern this codebase doesn't have a filter
  precedent for yet (its filters — `TotpAuthenticationDetailsSource`, the JWT resource-server
  filter — don't currently need to write error bodies themselves).
- **`Clock`-based, never `Instant.now()` inline** — any rate-limiter's own time source must be
  injectable/fixed for tests, same as every other time-sensitive component in this codebase.
- **Module boundaries (L12)**: a filter/interceptor spanning both chains and needing per-account
  identification would likely need `AccountService` (public, `account` module) — no new
  cross-module tension expected beyond what already exists.

## 4. Testing conventions

- Unit tests: plain JUnit + Mockito, fixed `Clock`.
- Integration tests: `@SpringBootTest(RANDOM_PORT)` + Testcontainers + `TestRestTemplate`
  (`SasLoginIntegrationTest`, `ApiKeyExchangeIntegrationTest` precedent) — the task statement's own
  "Add 429 tests" strongly implies HTTP-layer tests proving the actual status code, following this
  established real-filter-chain pattern rather than a unit test in isolation.
- **Environmental note carried forward from T25-T30**: Docker has been unavailable this entire
  session; six consecutive tasks now carry fully-written-but-never-executed Testcontainers suites.
  A 429 test is specifically an HTTP-layer, real-filter-chain proof — it cannot be meaningfully
  written any other way, so this task's own required tests will very likely extend that backlog by
  at least one more Docker-blocked file.

## 5. Known gaps / unknowns

- **I do not know** what request-per-window thresholds to use for any of the (now three distinct,
  not four) paths. `design.md` §4b-O2 explicitly frames this as an OPEN decision
  ("implementer/Claude MAY propose... proceed only if low-risk or after author approval") and
  `package.md` §11 Q2 is an **unresolved** open question to the spec author (unlike Q1, which has a
  "Resolved" annotation) asking for exactly these thresholds. This is a genuine blocker requiring
  either author confirmation or a clearly-flagged Phase 3/4 proposed-and-approved default — not
  something to silently invent.
- **I do not know** how to derive "the account" for `/oauth2/token` before the grant succeeds.
  Unlike `/login` (a raw form POST with a `username` parameter, precedent already exists via
  `LoginFailureHandler`), `/oauth2/token`'s refresh_token grant only reveals its principal after
  SAS internally resolves the presented token via `ReuseDetectingAuthorizationService` — this
  service doesn't own a controller for this endpoint to intercept at the HTTP layer the way it does
  for its own `AccountController` routes. Whether R42's own framing ("service-level limits act as a
  per-account backstop") tolerates a coarser-grained limiter for this specific path (e.g., keyed on
  the presented token's hash rather than a resolved account UUID) is a genuine design question for
  Phase 1/2, not decided here.
- **I do not know** whether "MFA verification" being the same path as "login" (§2 above) means R41
  effectively scopes to three paths, not four, or whether the spec author intended a future,
  not-yet-built separate MFA-verify endpoint. Flagged as a structural fact for Phase 1/2 to address
  explicitly, not silently resolved either way here.
- **I do not know** which rate-limiting mechanism to use. `design.md` O2 names Bucket4j or "a
  simple concurrent-map implementation" as the two candidates, with a stated preference for
  in-process per-replica buckets (not a shared/distributed store) — backed by nothing cached
  locally for Bucket4j, confirmed at §2 above, so either choice starts from zero in this repo.
- **I do not know** how a filter-level 429 should be written to the response to match this
  codebase's existing `application/problem+json` convention, since no existing filter in this
  codebase currently writes an error response body directly (§3 above) — this is a real
  implementation-shape question for Phase 2/5, not assumed here.

Do not design and do not extract requirements yet — that is Phase 1.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
