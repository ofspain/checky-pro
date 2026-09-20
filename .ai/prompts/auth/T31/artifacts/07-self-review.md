<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T31 · Phase 7 — Self Review

Reviews the Phase 6 diff (`ratelimit` package, `SecurityChainsConfig.java`, `ProblemTypes.java`,
`pom.xml`, `application.properties`) against the frozen brief and `agents.md`, across correctness,
boundary conditions, null-safety, thread-safety, transaction boundaries, module boundaries,
idempotency, enumeration-safety, and readability.

---

## Finding 1 — The 429 response's `instance` field is always absent, unlike every other rejection in this codebase

**Severity:** Low

**Evidence:** `RateLimitFilter.writeTooManyRequests` (`RateLimitFilter.java:125-136`) builds a
`ProblemDetail` and serializes it directly via `objectMapper.writeValue(...)`, entirely outside
Spring MVC's own dispatch. Every other rejection in this service (`SessionExceptionHandler`,
`AccountExceptionHandler`, `ApiKeyExceptionHandler`, etc.) returns a `ProblemDetail` from a
`@RestControllerAdvice`/`@ExceptionHandler` method, and Spring's own MVC infrastructure
auto-populates `instance` with the request path for those — confirmed directly this session by
running the real HTTP-layer tests (`SessionIntegrationTest`, `ApiKeyCrudIntegrationTest`) and
observing `instance` present in their actual response bodies, with no application code anywhere
calling `setInstance`. Since this filter's response is built and written manually, bypassing that
same MVC machinery, its `instance` will always be `null` — the one rejection body in the entire
service missing a field every other one has.

**Recommendation:** Either call `problem.setInstance(URI.create(request.getRequestURI()))`
explicitly for consistency with every other response shape, or accept and document the omission as
a deliberate, cosmetic-only difference (the field carries no distinguishing information either
way — R46/enumeration-safety is unaffected regardless of the choice).

---

## Non-Issues Confirmed

- **SAS parameter-reading compatibility (verified, not assumed):** traced SAS 1.5.1's own
  `OAuth2RefreshTokenAuthenticationConverter` → `OAuth2EndpointUtils.getFormParameters` and
  confirmed it reads via `request.getParameterMap()` — the same servlet parameter-caching
  mechanism `RateLimitFilter.isOAuthTokenRefreshRequest`/`refreshTokenHash` already use via
  `request.getParameter(...)`. Calling `getParameter(...)` first, from this filter, does not
  exhaust or interfere with SAS's own later reads of the same form-encoded body — confirmed
  against actual source, not just general servlet-spec knowledge, given how consequential getting
  this wrong would have been (it would have broken every real `/oauth2/token` refresh call).
- **`CachedBodyHttpServletRequest` correctness:** each caller (this filter, then Spring MVC's
  `@RequestBody` binding) calls `getInputStream()` exactly once and gets the full body from the
  start each time — correct for this exact single-read-each usage pattern, even though it doesn't
  implement strict continuation semantics a raw servlet stream would have.
- **Fail-open coverage (D5):** the entire key-derivation-and-consume block for all three paths is
  inside one try/catch in `doFilterInternal`; any exception leaves `probe` as `null`, which the
  subsequent check treats identically to "not rate-limited this request" — the request proceeds
  normally in every failure mode, including a malformed JSON body on the password-reset path
  (`passwordResetTokenHash` throwing is caught the same way).
- **Bucket thread-safety:** `ConcurrentHashMap.computeIfAbsent` guarantees at-most-once bucket
  creation per key under concurrent first access; Bucket4j's default `Bucket.builder().build()`
  bucket implementation is itself thread-safe.
- **AC8 scoping:** `isOAuthTokenRefreshRequest`'s explicit `grant_type=refresh_token` check means
  `client_credentials`/`authorization_code` requests never reach the bucket-check branch at all;
  `password`/implicit/device-code/token-exchange grants don't exist in this deployment (Phase 4
  Finding 9), needing no explicit exclusion.
- **Module boundaries (L12):** `RateLimitFilter` imports only `PasswordResetConfirmRequest` (a
  DTO, cross-module-safe by established convention) and `Hashing`/`ProblemTypes` (`common`) — no
  repository or entity import from any module.
- **Filter double-registration:** indirectly confirmed via this session's full Docker-backed test
  run — `AuthServiceApplicationTests` (a plain context-load test) and every integration test that
  exercises the real application context started up successfully with both new beans
  (`RateLimitFilter`, the disabling `FilterRegistrationBean`) present, with no startup error. The
  specific 429-triggering behavior itself is still unproven end-to-end — that's Phase 10's job.

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
