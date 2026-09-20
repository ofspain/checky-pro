<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T31 · Phase 11 — Test Review

Consumes the Phase 10 test manifest and the actual test files. All Docker-independent tests pass
(22/22). The integration suite (`RateLimitIntegrationTest`, 5 tests) could not be executed in this
environment because Docker/Testcontainers is unavailable. Findings only — no test or production
code changes in this phase.

---

## Executive Summary

The Phase 10 suite is well-layered and directly covers T31's acceptance criteria. Unit tests prove
token-bucket thresholds, key isolation, key derivation, fail-open behavior, and the 429 response
shape; integration tests target the named `package.md` §8 test and the three protected endpoints
over real HTTP. The gaps below are edge-case and observability gaps rather than correctness flaws.

---

## Findings

### Gap 1 — No test that invalid `RateLimitProperties` fail startup

**Why it matters:** `RateLimitProperties` is `@Validated` with `@Min(1)` on all three thresholds. An
invalid configuration is supposed to prevent startup, but without a test a future refactor could
drop `@Validated` or move the record out of `@ConfigurationPropertiesScan`. A zero threshold would
silently produce zero-capacity buckets and block all legitimate traffic on the protected paths; a
negative threshold would likely throw a less-obvious Bucket4j construction error later.

**Suggested test:** Add an `@SpringBootTest`-style test that sets
`themistra.auth.rate-limit.login-per-minute=0` (and similarly for the other two fields) and asserts
the context fails to start with a `BindException` / validation failure. Alternatively, test the
record directly with a Jakarta `Validator`.

**Evidence:** `ratelimit/RateLimitProperties.java:14-21`.

---

### Gap 2 — No test for sub-second `Retry-After` ceiling behavior

**Why it matters:** `RateLimitFilter.writeTooManyRequests(...)` computes
`Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L)`. Integer division truncates toward
zero, so any positive wait under one second is rounded up to 1 second by `Math.max(1, ...)`. The
unit test uses a mocked probe with a 30-second wait; no test documents or locks in the
sub-second/ceiling behavior. A future change that removed `Math.max(1, ...)` could emit `Retry-After:
0`, which is invalid for a still-throttled request.

**Suggested test:** Add a `RateLimitFilterTest` case that stubs `tryConsumeLogin` to return a
`ConsumptionProbe.rejected(...)` with a wait time between 1 ns and 999_999_999 ns, and assert the
`Retry-After` header is `"1"`.

**Evidence:** `ratelimit/RateLimitFilter.java:150`; `ratelimit/RateLimitFilterTest.java:202-221`.

---

### Gap 3 — No test for a `refresh_token` grant with a missing `refresh_token` parameter

**Why it matters:** `RateLimitFilter.isOAuthTokenRefreshRequest` matches on
`grant_type=refresh_token`, but `refreshTokenHash` returns `null` when the `refresh_token` parameter
is absent. The filter then skips rate limiting for that request. This is a plausible malformed
client request, and the current behavior (fail open, let the downstream OAuth2 endpoint return its
own error) is reasonable — but it is not asserted, so a future refactor could accidentally consult
the limiter with a null key or throw.

**Suggested test:** Add `oauthTokenRefreshGrantWithMissingTokenIsNeverRateLimited` to
`RateLimitFilterTest`: POST `/oauth2/token` with `grant_type=refresh_token` and no `refresh_token`
parameter, then verify `rateLimiter` is never interacted with and the chain proceeds.

**Evidence:** `ratelimit/RateLimitFilter.java:135-138`.

---

### Gap 4 — No test for a password-reset JSON body with no `token` field

**Why it matters:** `RateLimitFilter.passwordResetTokenHash` returns `null` when `body.token()` is
`null`, causing the filter to skip rate limiting. Like Gap 3, this is a valid fail-open boundary,
but it is untested. A future change that deserialized into a non-null DTO or threw on a missing
field would bypass the intended fail-open path.

**Suggested test:** Add `passwordResetRequestWithNoTokenFieldFailsOpen` to `RateLimitFilterTest`:
POST `/accounts/password-reset` with body `{"newPassword":"correct-horse-battery"}`, then verify
`tryConsumePasswordReset` is never called, the chain proceeds, and the response is not 429.

**Evidence:** `ratelimit/RateLimitFilter.java:129-133`.

---

### Gap 5 — No test verifies the filter is ordered before authentication in the security chain

**Why it matters:** The limiter's DoS-backstop value depends on running *before* credential
validation (Phase 4 D4). The integration tests indirectly rely on this because they POST wrong
passwords and still expect 429, but no test explicitly asserts that `RateLimitFilter` precedes the
authentication filters. If a future `SecurityChainsConfig` change moved the filter after
`UsernamePasswordAuthenticationFilter`, the unit tests would still pass but the backstop would be
weakened.

**Suggested test:** Add an ArchUnit or Spring-context test that verifies the security filter chain
contains `RateLimitFilter` before `UsernamePasswordAuthenticationFilter`/`AuthorizationFilter` (or
assert the `FilterRegistrationBean`/`SecurityFilterChain` order directly). Alternatively, strengthen
the integration test to assert that the elapsed time / request count is consistent with throttling
happening before password hashing.

**Evidence:** `config/SecurityChainsConfig.java` (filter registration); `ratelimit/RateLimitFilter.java:24-30`.

---

## Non-Issues Confirmed

- **Threshold enforcement:** `RateLimiterTest` proves login, password-reset, and oauth-token buckets
  enforce their own thresholds and reject the request immediately past capacity.
- **Key isolation:** `differentKeysHaveIndependentLoginBuckets` and
  `loginPasswordResetAndOauthTokenBucketsAreIndependentOfEachOtherForTheSameKey` cover D1/D2/D6.
- **Login key normalization:** `loginRequestConsumesLoginBucketWithNormalizedUsername` covers AC2
  (trim + lowercase, no DB lookup).
- **Token-hash keying:** `passwordResetRequestConsumesPasswordResetBucketWithTokenHash` and
  `oauthTokenRefreshGrantConsumesOauthTokenBucketWithTokenHash` cover D3.
- **Method/path exemptions:** `getRequestToLoginIsNotRateLimited`,
  `oauthTokenClientCredentialsGrantIsNeverRateLimited`,
  `oauthTokenAuthorizationCodeGrantIsNeverRateLimited`, and `unrelatedPathIsNeverRateLimited`
  cover AC3 and AC8.
- **Fail-open:** `passwordResetRequestWithMalformedJsonFailsOpen` and `rateLimiterThrowingFailsOpen`
  cover D5.
- **429 response shape:** `whenBucketIsExhaustedRequestIsRejectedWith429` covers AC7 (status,
  `application/problem+json`, `Retry-After`, and problem body fields).
- **Body replay:** `CachedBodyHttpServletRequestTest` proves the password-reset body can be read
  multiple times and remains available downstream.
- **Named test coverage:** `RateLimitIntegrationTest.shouldReturn429WhenPerAccountRateLimitExceeded`
  directly addresses `package.md` §8.

---

## Traceability Summary

| AC | Covered By | Gap |
|---|---|---|
| AC1 — threshold exceeded yields 429 | `RateLimiterTest.tryConsumeLoginRejectsRequestBeyondThreshold`, `RateLimitFilterTest.whenBucketIsExhaustedRequestIsRejectedWith429`, `RateLimitIntegrationTest.shouldReturn429WhenPerAccountRateLimitExceeded` (blocked) | None |
| AC2 — login key is normalized submitted username | `RateLimitFilterTest.loginRequestConsumesLoginBucketWithNormalizedUsername` | None |
| AC3 — GET `/login` and unrelated paths exempt | `RateLimitFilterTest.getRequestToLoginIsNotRateLimited`, `unrelatedPathIsNeverRateLimited` | None |
| AC4 — `/oauth2/token` refresh_token grant limited | `RateLimitFilterTest.oauthTokenRefreshGrantConsumesOauthTokenBucketWithTokenHash`, `RateLimitIntegrationTest.shouldReturn429ForOauthTokenRefreshGrantRateLimit` (blocked) | Gap 3 (missing `refresh_token` boundary) |
| AC5 *(if applicable)* | — | — |
| AC6 *(if applicable)* | — | — |
| AC7 — 429 with `Retry-After` and problem body | `RateLimitFilterTest.whenBucketIsExhaustedRequestIsRejectedWith429` | Gap 2 (sub-second ceiling untested) |
| AC8 — `client_credentials` and `authorization_code` exempt | `RateLimitFilterTest.oauthTokenClientCredentialsGrantIsNeverRateLimited`, `oauthTokenAuthorizationCodeGrantIsNeverRateLimited` | None |
| D1/D2 — per-key bucket isolation | `RateLimiterTest.differentKeysHaveIndependentLoginBuckets` | None |
| D3 — token-hash keying | `RateLimitFilterTest.passwordResetRequestConsumesPasswordResetBucketWithTokenHash`, `oauthTokenRefreshGrantConsumesOauthTokenBucketWithTokenHash` | Gap 4 (missing-token boundary) |
| D5 — fail-open | `RateLimitFilterTest.passwordResetRequestWithMalformedJsonFailsOpen`, `rateLimiterThrowingFailsOpen` | None |
| D6 — same-key independence across bucket types | `RateLimiterTest.loginPasswordResetAndOauthTokenBucketsAreIndependentOfEachOtherForTheSameKey` | None |
| Config validation | Production code only (`@Validated` + `@Min(1)`) | Gap 1 (no startup validation test) |
| Filter ordering | Production code only | Gap 5 (no order verification) |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval.
