<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T31 · Phase 2 — Task Implementation Brief

## Task

Rate limiting — implement per-account request-rate buckets for the paths named in R41, rejecting
with `429 Too Many Requests` when exceeded, as a backstop behind ingress-level IP limiting (R42).

## Purpose

Today, `/login`, `POST /accounts/password-reset`, and `/oauth2/token` have no request-rate defense
of their own (`lockout_state`'s failure-count-based lockout is a different, complementary
mechanism). This task adds that missing layer.

## Scope

**In:** a request-rate limiter applied to three real HTTP paths (resolved at Phase 0/1 — see
Business Rules), a new Maven dependency for the limiting mechanism, and 429-status integration
tests.

**Out:** `lockout_state`/`LockoutService` (untouched — a different, pre-existing mechanism); any
new MFA-specific endpoint (none exists; see D2 below); ingress/edge-level configuration (R42 is
about *this service's* limits being a backstop *to* that, not about configuring the edge itself,
which is infrastructure outside this repo).

## Business Rules

- **R41.** Exceeding the configured per-account request rate on login, `/oauth2/token`, or
  password-reset confirmation returns `429`.
- **R42.** These limits are a backstop, not the primary (ingress/IP-level) defense.

## Locked Decisions

None — confirmed at Phase 0 and 1.

## Resolutions to Phase 1's Open Questions (proposed for Phase 3/4 to confirm or redirect)

- **OQ1 (thresholds — the one genuine spec-author gap, `package.md` §11 Q2 unresolved).** Proposed
  defaults, each independently confirmable/adjustable at the gate:
  - Login (`/login`, which also covers MFA verification per D2): **10 requests/minute per
    account**.
  - Password-reset confirmation (`POST /accounts/password-reset`): **5 requests/minute per
    account**.
  - `/oauth2/token` (refresh_token grant only, per D3): **30 requests/minute per bucket key** —
    higher, since legitimate refresh traffic (10-minute access-token TTL per `agents.md`) and
    normal client retry/backoff behavior both look like frequent-but-legitimate calls to this
    specific endpoint.
  These are genuinely a security/UX policy call, not a technical fact — presented for explicit
  confirmation, not silently assumed.
- **OQ2 (MFA scoping).** Resolved as: rate-limit `/login` once. MFA (TOTP/recovery-code)
  verification happens inside that same request (`TotpAuthenticationProvider`), so it is covered
  by construction, not by any separate instrumentation. No new MFA-specific route is invented.
- **OQ3 (`/oauth2/token` per-account keying).** Resolved as: only the **refresh_token** grant gets
  a dedicated bucket, keyed by the **SHA-256 hash of the presented refresh token** (via the
  existing `Hashing.sha256` utility — the same hashing already used for refresh tokens everywhere
  else in this codebase) rather than a resolved account UUID. `client_credentials` requests
  (internal service-to-service, no account at all) and `authorization_code` exchanges (low-frequency,
  and an attacker must already have defeated the login-path limiter to reach this step) are **not**
  independently rate-limited at this endpoint — R42's "backstop, not primary defense" framing makes
  this narrower scope acceptable, and a token-hash key is a legitimate account-proxy for the one
  grant type that actually represents repeated, automatable per-principal traffic.
- **OQ4 (library).** **Bucket4j 8.10.1** — confirmed via `curl`'s Maven Central metadata query to be
  the actual current latest release (not a guess), and resolved into the local Maven cache to
  verify the coordinates are correct. A hand-rolled concurrent-map implementation would need to
  reimplement token-bucket refill math Bucket4j already provides, tested, for free. Artifact:
  `com.bucket4j:bucket4j_jdk17-core:8.10.1` (the JDK17-targeted variant, not the general
  `bucket4j-core` artifact) — appropriate since this service already requires Java 21, and the
  JDK17 variant avoids the reflection-based CAS fallback (and its `--add-opens` requirement) the
  general artifact needs on newer JDKs.

## Dependencies

`com.bucket4j:bucket4j_jdk17-core:8.10.1` (new); `AccountService.findLoginView` (existing, for the
login-path bucket key); `Hashing.sha256` (existing, for the `/oauth2/token` bucket key);
`ProblemTypes` (existing, new constant); `Clock` (existing).

## Inputs

Each incoming request on the three in-scope paths; the account/token-derived bucket key; the
configured per-path threshold.

## Outputs

A `429 Too Many Requests` `application/problem+json` response when a bucket is exhausted; no
outbox/event emission (R41/R42 don't call for one, unlike R43's audited-action list, which doesn't
name rate-limit rejections).

## State Changes

None persisted — Bucket4j buckets are in-process, per-replica, ephemeral (O2's own stated
preference), lost on restart/redeploy. This is a deliberate, spec-endorsed trade-off, not an
oversight: the durable `lockout_state` table already covers the "must survive a restart" security
concern for credential-guessing specifically; this task's buckets are a lighter backstop layered on
top (R42).

## Files to Create

- A new class holding the three `Bucket4j`-backed limiters (one per path) and the key-derivation
  logic per path — exact name/package is Phase 5's call, but it must live somewhere that can be
  wired into both filter chains (chain 1 for `/oauth2/token`, chain 2 for `/login` and
  `/accounts/password-reset`), so likely `token` (chain 1 already lives there) or a new small
  `ratelimit` package, not `account` (would create an unwanted dependency the other direction).
- A `Filter` (or two, one per chain) that consults the limiter and writes the 429
  `application/problem+json` body directly when exhausted — this codebase has no existing
  precedent for a filter writing an error body itself (every other rejection cause goes through a
  `@RestControllerAdvice`), so this is new but necessary given `/oauth2/token` has no controller
  this service owns to instrument any other way.
- A validated `@ConfigurationProperties` record for the three thresholds (new config key names,
  Phase 5's call, following the `themistra.auth.<feature>.*` convention).

## Files to Modify

- `services/auth/pom.xml` (the new Bucket4j dependency).
- `token/SecurityChainsConfig.java` — wire the rate-limit filter onto both chains (chain 1 via
  `.addFilterBefore(...)` scoped to its already-narrow `securityMatcher`; chain 2 similarly, scoped
  to just the two in-scope paths so it doesn't run on every request).
- `common/ProblemTypes.java` (new 429 problem-type constant).
- `application.properties` (new threshold config keys).

## Files NOT to Modify

- `lockout_state` schema/`LockoutService`/`LoginFailureHandler`'s existing lockout logic (a
  different, untouched mechanism).
- Any T25-T30 file.
- No new Flyway migration — nothing here needs persistence.

## Acceptance Criteria

- **AC1-AC3 (R41).** Each of the three in-scope paths returns 429 once its own configured
  threshold is exceeded by a single account/key, and not before.
- **AC4.** Independent accounts/keys never affect each other's buckets.
- **AC5.** Once the window/refill elapses, a previously-throttled account/key succeeds again (this
  is rate limiting, not a permanent block — distinct from `lockout_state`'s explicit unlock
  semantics).
- **AC6 (R42).** No code or documentation in this task claims the service-level limiter replaces
  ingress/IP-level protection.
- **AC7.** The 429 body is `application/problem+json`, consistent with every other rejection cause
  in this codebase.
- **AC8 (OQ3 scope).** `client_credentials` and `authorization_code` grant requests to
  `/oauth2/token` are unaffected by this task's limiter (not rate-limited by it, not broken by it).

## Required Tests

- `shouldReturn429WhenPerAccountRateLimitExceeded` (named, `package.md` §8).
- Per-path: under-threshold succeeds, at-threshold-plus-one returns 429, two different
  accounts/keys don't interfere, post-window recovery succeeds again.
- `/oauth2/token`: a `client_credentials` request never triggers the refresh-token-keyed bucket
  (AC8).
- HTTP-layer, Testcontainers-backed (per the task statement's own "Add 429 tests" and this
  codebase's established real-filter-chain integration-test pattern) for at least the login and
  password-reset paths (both on chain 2, this service's own controllers); `/oauth2/token`'s own
  integration test needs a real refresh token, following `ApiKeyExchangeIntegrationTest`/
  `SasLoginIntegrationTest`'s existing real-login-flow patterns to obtain one.

## Constraints

- **Thread-safety:** Bucket4j's own bucket implementations are thread-safe by design; the
  per-key bucket registry (a `ConcurrentHashMap`-backed cache) must be safe under concurrent
  first-access for the same key.
- **Memory:** an unbounded map of per-account buckets could grow indefinitely under many distinct
  accounts — Phase 5 should consider a bounded/expiring cache (e.g., Caffeine, or Bucket4j's own
  distributed-cache integrations are overkill here) rather than a plain unbounded `ConcurrentHashMap`,
  though this is a secondary concern relative to correctness and may be deferred if out of hand-rolled
  scope; flagged for Phase 3 to weigh in on rather than silently deciding either way here.
- **Module boundaries (L12):** the limiter class must not import `account`'s or `token`'s
  package-private repositories; only `AccountService` (public) and `Hashing` (already
  cross-module-safe, `common`) are needed.
- **Security:** the 429 body must not leak whether the *account itself* exists (enumeration-safety,
  R46's spirit) — the response should be identical in shape regardless of whether the attempted
  account/token is real or fabricated, since the bucket key derivation happens before any
  existence check.
- **Performance:** in-process bucket checks are O(1) per request; negligible overhead.

## Open Questions

No blockers. OQ1-OQ4 all have a proposed resolution above; OQ1 in particular is flagged for
explicit Phase 4 sign-off given its unresolved-spec-question origin, not silently treated as
already settled.

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
