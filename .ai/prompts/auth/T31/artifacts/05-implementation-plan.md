<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T31 · Phase 5 — Implementation Plan

Consumes the frozen brief (`artifacts/04-frozen-task-brief.md`). This phase resolves the brief's own
deferred implementation-shape questions (password-reset key derivation shape; where the filter is
registered and how it avoids double-registration) concretely, since those were explicitly left to
Phase 5.

## Files to create

- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitProperties.java`
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimiter.java`
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitFilter.java`
- `services/auth/src/main/java/com/themistra/auth/ratelimit/CachedBodyHttpServletRequest.java`
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitFilterConfig.java`

New `ratelimit` package (mirrors T30's `cleanup` precedent for a small, cross-cutting concern that
doesn't belong inside `account` or `token`).

## Files to modify

- `services/auth/pom.xml` — add `com.bucket4j:bucket4j_jdk17-core:8.10.1`.
- `token/SecurityChainsConfig.java` — inject `RateLimitFilter` into both chain-builder methods,
  `.addFilterBefore(...)` on each.
- `common/ProblemTypes.java` — add `RATE_LIMIT_EXCEEDED`.
- `application.properties` — three new keys.

## Public methods (signatures)

**`RateLimitProperties`** (validated record, matches `ApiKeyProperties`/`CleanupProperties` style):
```java
@ConfigurationProperties(prefix = "themistra.auth.rate-limit")
@Validated
public record RateLimitProperties(
        @Min(1) int loginPerMinute,
        @Min(1) int passwordResetPerMinute,
        @Min(1) int oauthTokenPerMinute
)
```

**`RateLimiter`** (`@Component`) — one `tryConsume*` method per path, each backed by its own
`ConcurrentHashMap<String, Bucket>` and `Bandwidth` (token bucket, capacity = threshold, refill =
threshold/60s, per Phase 3 Finding 7):
```java
public ConsumptionProbe tryConsumeLogin(String key)
public ConsumptionProbe tryConsumePasswordReset(String key)
public ConsumptionProbe tryConsumeOauthToken(String key)
```
Returns Bucket4j's own `ConsumptionProbe` directly (carries `isConsumed()` and
`getNanosToWaitForRefill()`) rather than a custom result type — no value in wrapping it.

**`RateLimitFilter`** (`@Component extends OncePerRequestFilter`) — no new public API beyond the
inherited `doFilter`; all logic is in `doFilterInternal` (protected, overridden).

**`RateLimitFilterConfig`** (`@Configuration`):
```java
@Bean
public FilterRegistrationBean<RateLimitFilter> preventAutoRegistration(RateLimitFilter filter)
```

## Private methods

**`RateLimiter`**:
```java
private ConsumptionProbe tryConsume(Map<String, Bucket> registry, Bandwidth bandwidth, String key) {
    Bucket bucket = registry.computeIfAbsent(key, k -> Bucket.builder().addLimit(bandwidth).build());
    return bucket.tryConsumeAndReturnRemaining(1);
}
```
Three `Bandwidth` instances are computed once in the constructor from `RateLimitProperties`
(`Bandwidth.classic(limit, Refill.greedy(limit, Duration.ofMinutes(1)))`), not recomputed per call.

**`RateLimitFilter`** — this is the class resolving the brief's deferred implementation-shape
questions concretely:
```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                 FilterChain chain) throws ServletException, IOException {
    HttpServletRequest effectiveRequest = request;
    ConsumptionProbe probe = null;
    try {
        if (isLoginRequest(request)) {
            probe = rateLimiter.tryConsumeLogin(normalizedUsername(request));
        } else if (isPasswordResetRequest(request)) {
            CachedBodyHttpServletRequest cached = new CachedBodyHttpServletRequest(request);
            effectiveRequest = cached;
            String tokenHash = passwordResetTokenHash(cached);
            if (tokenHash != null) {
                probe = rateLimiter.tryConsumePasswordReset(tokenHash);
            }
        } else if (isOAuthTokenRefreshRequest(request)) {
            String tokenHash = refreshTokenHash(request);
            if (tokenHash != null) {
                probe = rateLimiter.tryConsumeOauthToken(tokenHash);
            }
        }
    } catch (Exception e) {
        // D5: a bug in this backstop must never become an outage of what it backstops.
        log.error("Rate limiter check failed; failing open", e);
        probe = null;
    }

    if (probe != null && !probe.isConsumed()) {
        writeTooManyRequests(response, probe);
        return;
    }
    chain.doFilter(effectiveRequest, response);
}
```

- `isLoginRequest`: `"POST".equals(method) && "/login".equals(requestURI)`.
- `isPasswordResetRequest`: `"POST".equals(method) && "/accounts/password-reset".equals(requestURI)`.
- `isOAuthTokenRefreshRequest`: `"POST".equals(method) && "/oauth2/token".equals(requestURI) &&
  "refresh_token".equals(request.getParameter("grant_type"))` — form-encoded parameter read,
  identical technique to `LoginFailureHandler`'s existing `request.getParameter("username")`
  precedent, safe to call from a filter for a form-encoded body.
- `normalizedUsername`: `request.getParameter("username")` (same parameter-name coupling comment
  `LoginFailureHandler` already carries), trimmed + lower-cased inline — **no `AccountService` call
  at all** (D2). Null/blank submissions are treated as a fixed sentinel key (e.g. `""`) so a
  missing-username request is still bucketed, not silently exempted.
- `refreshTokenHash`: `request.getParameter("refresh_token")` → `Hashing.sha256(...)`, or `null`
  if absent (AC8: `client_credentials`/`authorization_code` never reach this branch at all since
  `grant_type` won't be `refresh_token`).
- `passwordResetTokenHash`: reads the cached body's JSON via the injected `ObjectMapper`, extracts
  `PasswordResetConfirmRequest.token()`, hashes it — this is the concrete resolution of the brief's
  own "account-identifying request field, Phase 5 to confirm shape" deferral: this endpoint's body
  is JSON with a `token` field, not a username, so the key is the **hash of the reset token**
  (mirroring D3's exact token-hash-keying pattern for `/oauth2/token`, not a new pattern).
- `writeTooManyRequests(response, probe)`: builds a `ProblemDetail` (`ProblemTypes.RATE_LIMIT_EXCEEDED`,
  title "Too many requests"), sets status 429, sets `Retry-After` from
  `probe.getNanosToWaitForRefill()` (converted to whole seconds, minimum 1), serializes via the
  injected `ObjectMapper` — reusing Spring's own `ProblemDetail` type means the JSON shape is
  byte-identical to every controller-thrown rejection in this codebase (Spring Boot auto-registers
  `ProblemDetail`'s Jackson mixin on the primary `ObjectMapper`), even though this response
  originates from a filter, not a `@RestControllerAdvice`.

**`CachedBodyHttpServletRequest`** (package-private `HttpServletRequestWrapper`) — resolves the
real, concrete problem the brief only gestured at ("Phase 5 to confirm shape"): the password-reset
endpoint's body is JSON, and a filter reading it directly would exhaust the stream before Spring
MVC's own `@RequestBody` deserialization runs downstream. Buffers the full body into a byte array
in its constructor (`StreamUtils.copyToByteArray(request.getInputStream())`), then serves
`getInputStream()`/`getReader()` from that buffer on every call — safely re-readable any number of
times, unlike the stock `ContentCachingRequestWrapper` (which only caches what's read through it
after the fact, not before). The filter passes this wrapper (not the original request) down
`chain.doFilter(...)` for exactly this one path so the real controller's own JSON binding still
works normally.

## Entities used

None — no persistence.

## Repositories used

None (per D2, not even `AccountService`).

## Services used

`Hashing.sha256` (existing, `common`), `ObjectMapper` (existing Spring Boot bean, for both reading
the cached password-reset body and writing the 429 `ProblemDetail`), `Clock` is **not** needed —
Bucket4j's own internal `TimeMeter` handles bucket timing; introducing this codebase's injectable
`Clock` into Bucket4j would require a custom `TimeMeter` adapter for no testable benefit, since
these tests assert bucket *count* behavior (threshold crossed or not), not wall-clock-dependent
assertions the way `Clock.fixed(...)` tests elsewhere in this codebase do.

## Unit / integration tests required

**`RateLimiterTest`** (new, unit, no Spring context): per-bucket-type threshold enforcement
(under/at/over limit), independent keys don't interfere, refill after advancing Bucket4j's own
test clock (via Bucket4j's `TimeMeter` test support) proves recovery (AC5).

**`RateLimitFilterTest`** (new, unit, mocked `RateLimiter`/`FilterChain`, real
`MockHttpServletRequest`/`MockHttpServletResponse`): path/method matching correctness for all
three routes and their negatives; D2's raw-email keying (two different emails, one real-shaped one
fabricated, produce independent buckets with no code path distinguishing them); D3's
token-hash keying for both `/oauth2/token` and password-reset; AC8 (`client_credentials`/no
`refresh_token` parameter never triggers a bucket check); D4 (the filter's check happens
unconditionally before `chain.doFilter`, i.e. before any downstream authentication); D5 (a
`RateLimiter` mock configured to throw still calls `chain.doFilter`, not an error response); the
429 body's exact `ProblemDetail` shape and `Retry-After` header value.

**`CachedBodyHttpServletRequestTest`** (new, unit): the body can be read twice in full and produces
identical bytes both times.

**`RateLimitIntegrationTest`** (new, `@SpringBootTest(RANDOM_PORT)` + Testcontainers +
`TestRestTemplate`, per the task statement's own "Add 429 tests" and this codebase's established
real-filter-chain pattern): `shouldReturn429WhenPerAccountRateLimitExceeded` (named test) drives
the login path past its threshold and asserts 429 with `Retry-After`; a second test does the same
for `POST /accounts/password-reset`; a third obtains a real refresh token via the existing
login-flow pattern (mirrors `SasLoginIntegrationTest`) and drives `/oauth2/token` past its
threshold; a fourth proves a `client_credentials` request to `/oauth2/token` is never throttled by
this mechanism (AC8) regardless of call volume.

## Execution order

1. `RateLimitProperties` + `application.properties` keys — no dependents, safe first.
2. `pom.xml` (Bucket4j) — needed before any code referencing `Bucket`/`Bandwidth` compiles.
3. `RateLimiter` (depends on 1) — unit-testable in isolation immediately (`RateLimiterTest`).
4. `common/ProblemTypes.java` (`RATE_LIMIT_EXCEEDED`) — independent, can happen any time before step 5.
5. `CachedBodyHttpServletRequest` — independent, unit-testable alone (`CachedBodyHttpServletRequestTest`).
6. `RateLimitFilter` (depends on 3, 4, 5) — unit-testable with mocked `RateLimiter`
   (`RateLimitFilterTest`).
7. `RateLimitFilterConfig` (depends on 6).
8. `token/SecurityChainsConfig.java` wiring (depends on 6, 7) — needed before the real filter chain
   can be exercised at all.
9. `RateLimitIntegrationTest` — proves the whole stack end-to-end (Docker-permitting).

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
