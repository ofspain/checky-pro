<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T31 · Phase 8 — Independent Code Review

Consumes the Phase 6 implementation and Phase 7 self-review. Reviewed with fresh, adversarial eyes; findings only.

---

## Finding 1 — `RateLimiter` uses a deprecated Bucket4j API

**Issue:** Build warning / future breakage risk.

**Evidence:** `mvn compile` reports: `/Users/oluwafemi.ayeni/personal/checky-pro/services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimiter.java uses or overrides a deprecated API.` The likely source is `Bandwidth.classic(long, Refill)` (`RateLimiter.java:61`), which is deprecated in Bucket4j 8.10.1 in favor of `Bandwidth.builder()`.

**Recommendation:** Replace `Bandwidth.classic(perMinute, Refill.greedy(perMinute, Duration.ofMinutes(1)))` with `Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)).build()`. This removes the deprecation warning and aligns with Bucket4j's current API.

**Confidence:** High.

---

## Finding 2 — 429 response body omits `instance`, diverging from every other problem response

**Issue:** Contract inconsistency / RFC 9457 completeness.

**Evidence:** `RateLimitFilter.writeTooManyRequests` (`RateLimitFilter.java:125-136`) builds a `ProblemDetail` and writes it manually, never calling `setInstance(...)`. Every other rejection path in this service goes through Spring MVC's `@RestControllerAdvice`/`@ExceptionHandler`, which auto-populates `instance` with the request path. The self-review (Finding 1) already identified this; it is worth treating as a real contract gap because `contracts/api/auth.yaml` and the generated client models likely expect `instance` to be present on all problem responses.

**Recommendation:** Add `problem.setInstance(URI.create(request.getRequestURI()))` in `writeTooManyRequests`, mirroring Spring's default behavior. If the request URI is unavailable at that point (the method does not currently receive the request), change the method signature to accept `HttpServletRequest` and use it.

**Confidence:** High.

---

## Finding 3 — Login-path keying assumes form-encoded `username`, breaks JSON login

**Issue:** Coupling / endpoint-shape assumption.

**Evidence:** `RateLimitFilter.normalizedUsername` (`RateLimitFilter.java:109-112`) calls `request.getParameter("username")`. This works for Spring Security's default form-login (form-encoded POST), but fails if the SPA or a future client sends credentials as JSON. The filter then keys on the empty string for every JSON login request, collapsing all JSON login traffic into a single shared bucket and effectively disabling per-account rate limiting for that shape.

**Recommended brief amendment:** Either document that `/login` is strictly form-encoded (and test the rate limiter only for that shape), or make `normalizedUsername` fall back to reading the JSON body when `username` is absent — likely reusing `CachedBodyHttpServletRequest` and the same object-mapper approach used for password-reset.

**Confidence:** Medium (depends on whether the frontend actually sends JSON; the current form-login configuration suggests form-encoded is the intended shape, but the assumption is unstated).

---

## Finding 4 — Password-reset confirmation is keyed by token hash, not per-account

**Issue:** Scope deviation / weaker rate-limiting than R41's "per-account" wording.

**Evidence:** `RateLimitFilter.passwordResetTokenHash` (`RateLimitFilter.java:114-118`) derives the bucket key from the SHA-256 hash of the submitted reset token. Each password-reset request issues a *different* token, so each confirmation request gets its own independent bucket. A single account can request 100 reset tokens and confirm 100 password resets (5 attempts per token × 100 tokens) without ever hitting a per-account limit. R41 says "per-account request rates"; the Phase 4 D3 decision accepted this narrowing for `/oauth2/token` refresh-token keying, but applying the same token-hash pattern to password-reset confirmation means there is effectively no per-account rate limit on this path.

**Recommendation:** Decide whether this is acceptable. If the goal is only to prevent brute-forcing a single token (which is already single-use and time-limited), per-token keying is sufficient. If the goal is per-account throttling, the key must be account-derived. Document the chosen semantics explicitly in the brief/AC and add a test that proves the intended behavior (e.g., two different tokens for the same account share a bucket, or do not).

**Confidence:** Medium (the implementation matches the brief's stated pattern, but the pattern's effect on this specific path is a meaningful narrowing of "per-account").

---

## Finding 5 — `CachedBodyHttpServletRequest` is created even when the password-reset body will not be parsed

**Issue:** Minor unnecessary work / potential side effect.

**Evidence:** `RateLimitFilter.doFilterInternal` (`RateLimitFilter.java:64-70`) wraps the request in `CachedBodyHttpServletRequest` as soon as it sees `POST /accounts/password-reset`, before checking whether the body contains a `token`. If the body is malformed or empty, the wrapper still buffers the entire body. For small JSON bodies this is harmless, but it is a side effect that could be avoided by checking the content type or only wrapping when needed.

**Recommendation:** Low priority. Consider moving the wrap inside the branch where the token is actually needed, or at minimum assert via test that a large (but still bounded) password-reset body does not cause issues.

**Confidence:** Low.

---

## Finding 6 — No tests exist for the new `ratelimit` package

**Issue:** Coverage gap / Phase 10 dependency.

**Evidence:** There are no test files under `services/auth/src/test/java/com/themistra/auth/ratelimit/`. The implementation compiles, but the T31 acceptance criteria (AC1-AC8) and the named `shouldReturn429WhenPerAccountRateLimitExceeded` test are not yet verified. Phase 10 will need to cover: per-path threshold behavior, key isolation, bucket refill/recovery, fail-open behavior, `Retry-After` header, 429 body shape, and the filter's placement in both security chains.

**Recommendation:** Track as a known pending item for Phase 10; ensure the test plan includes the JSON-login and per-account-vs-per-token concerns raised in Findings 3 and 4.

**Confidence:** High (that tests are missing) / N/A (expected at this phase).

---

## Non-Issues Confirmed

- **Filter ordering:** `RateLimitFilter` is correctly placed before credential validation in both chains (`DisableEncodeUrlFilter` in chain 1, `UsernamePasswordAuthenticationFilter` in chain 2).
- **Filter double-registration prevention:** `RateLimitFilterConfig.preventAutoRegistration` correctly disables Spring Boot's automatic global filter registration.
- **Fail-open behavior:** the entire key-derivation-and-consume block is inside one try/catch; any exception leaves `probe == null` and the request proceeds.
- **Enumeration safety:** login keying uses raw normalized username with no DB lookup; password-reset and `/oauth2/token` keys are token hashes, which reveal nothing about account existence.
- **`/oauth2/token` grant scoping:** `isOAuthTokenRefreshRequest` explicitly checks `grant_type=refresh_token`, so `client_credentials`/`authorization_code` are correctly unaffected.
- **Thread safety:** `ConcurrentHashMap.computeIfAbsent` plus Bucket4j's own thread-safe buckets is correct.
- **Module boundaries:** `RateLimitFilter` imports only a DTO and `common` classes; `RateLimiter` has no cross-module imports.
- **`Retry-After` header:** correctly computed from `ConsumptionProbe.getNanosToWaitForRefill()`.
- **ProblemTypes:** `RATE_LIMIT_EXCEEDED` is added and used consistently.

---

## Open Questions

None that block the Phase 8 review. Findings 1 and 2 are mechanical fixes; Findings 3 and 4 are design/scope questions that should be dispositioned at the Phase 9 human gate.

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (Human Approval / Review Resolution) on approval.
