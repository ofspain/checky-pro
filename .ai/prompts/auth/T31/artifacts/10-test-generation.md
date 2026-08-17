<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T31 · Phase 10 — Test Generation

Test manifest for the resolved implementation (`artifacts/09-review-resolution.md`). No production
code changed in this phase. All tests below were written and executed against real Docker
(Postgres + Kafka via Testcontainers) — this is the first T-task this session generated with Docker
available from the outset, so the integration suite is proven green, not merely compiled.

## `RateLimiterTest.java` (new — 7 tests, plain JUnit, no Spring context)

| Test | Verifies |
|---|---|
| `tryConsumeLoginAllowsRequestsWithinThreshold` | R41 — requests at/under the configured login threshold are all consumed. |
| `tryConsumeLoginRejectsRequestBeyondThreshold` | R41/AC1 — the request immediately past the threshold is rejected. |
| `rejectedProbeReportsAPositiveBoundedWaitTime` | AC7 — a rejected probe's `getNanosToWaitForRefill()` is positive and bounded by the 1-minute window, feeding the `Retry-After` header. |
| `differentKeysHaveIndependentLoginBuckets` | D1/D2 — per-key bucket isolation: exhausting one login key's bucket never affects another key. |
| `loginPasswordResetAndOauthTokenBucketsAreIndependentOfEachOtherForTheSameKey` | D6 — the three registries (login/password-reset/oauth-token) are isolated from each other even when consulted with the identical key value. |
| `tryConsumePasswordResetRespectsItsOwnConfiguredThreshold` | R41/D3 — the password-reset bucket honors its own independently-configured threshold. |
| `tryConsumeOauthTokenRespectsItsOwnConfiguredThreshold` | R41/D3 — the oauth-token bucket honors its own independently-configured threshold. |

## `CachedBodyHttpServletRequestTest.java` (new — 3 tests, plain JUnit)

| Test | Verifies |
|---|---|
| `getInputStreamCanBeReadInFullMoreThanOnce` | Design precondition for D3's password-reset keying — the filter's own body read must not exhaust the stream for downstream `@RequestBody` binding. |
| `getReaderReturnsTheFullBodyText` | Same guarantee via the `getReader()` path, which Spring MVC's JSON binding may use instead of `getInputStream()`. |
| `handlesAnEmptyBodyWithoutError` | Boundary — an empty body (no request content) doesn't throw during buffering. |

## `RateLimitFilterTest.java` (new — 12 tests, Mockito + real `MockHttpServletRequest`/`MockHttpServletResponse`)

| Test | Verifies |
|---|---|
| `loginRequestConsumesLoginBucketWithNormalizedUsername` | D1/AC2 — the login key is the submitted username, trimmed and lowercased, no DB lookup (enumeration-safe). |
| `loginRequestWithMissingUsernameUsesEmptyStringKey` | Boundary — a missing `username` parameter degrades to an empty-string key rather than throwing. |
| `getRequestToLoginIsNotRateLimited` | AC3 — only POST `/login` is subject to the login limiter; GET is exempt. |
| `passwordResetRequestConsumesPasswordResetBucketWithTokenHash` | D3/Kimi Phase 8 Finding 4 — the password-reset key is the SHA-256 hash of the submitted token; also proves the request is rewrapped in `CachedBodyHttpServletRequest` so the body remains readable downstream. |
| `passwordResetRequestWithMalformedJsonFailsOpen` | D5 — malformed JSON in the password-reset body fails open (no 429, no exception, request proceeds) rather than blocking or erroring. |
| `oauthTokenRefreshGrantConsumesOauthTokenBucketWithTokenHash` | D3/AC4 — `grant_type=refresh_token` on `/oauth2/token` is keyed by the SHA-256 hash of the submitted `refresh_token`. |
| `oauthTokenClientCredentialsGrantIsNeverRateLimited` | AC8 — `client_credentials` grants are never subject to this per-account mechanism. |
| `oauthTokenAuthorizationCodeGrantIsNeverRateLimited` | AC8 — `authorization_code` exchanges are likewise unaffected. |
| `oauthTokenRequestWithNoGrantTypeIsNeverRateLimited` | Boundary — a request to `/oauth2/token` with no `grant_type` at all is not limited. |
| `unrelatedPathIsNeverRateLimited` | AC3 — paths outside the three protected endpoints never touch the limiter. |
| `whenBucketIsExhaustedRequestIsRejectedWith429` | AC7 — the full 429 response shape: status, `application/problem+json` content type, `type`/`title`/`instance` fields, and a positive integer `Retry-After` header. |
| `rateLimiterThrowingFailsOpen` | D5 — any internal `RateLimiter` exception fails open, mirroring the codebase's R13/HIBP precedent. |

## `RateLimitIntegrationTest.java` (new — 5 tests, Testcontainers + `TestRestTemplate`, real HTTP)

| Test | Verifies |
|---|---|
| `shouldReturn429WhenPerAccountRateLimitExceeded` (**named test, `package.md` §8**) | AC1/AC2/AC7 — driving real `/login` POSTs past the configured threshold for one account yields a real 429 with `application/problem+json` content type, proven end-to-end through the real filter chain (not mocked). |
| `differentAccountsHaveIndependentLoginBuckets` | D1/D2 — a second, distinct account's login attempts are unaffected by the first account's exhausted bucket, proven over real HTTP. |
| `shouldReturn429ForPasswordResetConfirmationRateLimit` | D3/Kimi Phase 8 Finding 4/AC1 — repeated `/accounts/password-reset` submissions of the *same* fabricated token exceed the password-reset threshold and receive 429; the token is held constant across the loop since the bucket key is the token's own hash — a fresh token per attempt would independently consume a different bucket every time and could never accumulate toward the threshold. |
| `shouldReturn429ForOauthTokenRefreshGrantRateLimit` | D3/AC4 — repeated `refresh_token` grant submissions of the same refresh token value against `/oauth2/token` exceed the oauth-token threshold and receive 429. |
| `oauthTokenClientCredentialsGrantIsNeverRateLimited` | AC8 — end-to-end confirmation that `client_credentials` grants against the real `/oauth2/token` endpoint are never throttled, even under repeated real requests. |

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='RateLimiterTest,CachedBodyHttpServletRequestTest,RateLimitFilterTest,RateLimitIntegrationTest'`
  — **27/27 pass** (7 + 3 + 12 + 5), run against real Docker Postgres + Kafka via Testcontainers.
  This is the first T-task this session whose integration suite executed successfully on first
  attempt against real infrastructure rather than being written-but-unexecuted.
- Two real test-code bugs were found and fixed during this verification (not production bugs):
  the 429 `Content-Type` assertion was too strict (didn't account for Tomcat appending
  `;charset=ISO-8859-1`) and the password-reset integration test originally generated a fresh
  random token per attempt, so each attempt independently consumed a different token-hash-keyed
  bucket and could never reach the threshold. Both are reflected in the manifest above as the
  tests now stand.

The named `package.md` §8 test (`shouldReturn429WhenPerAccountRateLimitExceeded`) is fully written,
passing, and scoped exactly to its own name.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi test review) on approval.
