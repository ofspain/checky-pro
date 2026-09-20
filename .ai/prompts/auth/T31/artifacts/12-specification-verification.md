<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T31 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11, plus the Kimi Phase 11 gap closures and
the AC5 gap found during this phase) against `requirements.md`, `design.md`, `tasks.md`, and the
frozen brief (`artifacts/04-frozen-task-brief.md`) for **T31 only**. `spec/auth-service/` confirmed
unchanged since T31 began (no commits touching `spec/auth-service/` in this task's window).

---

## Traceability Matrix — Requirements

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R41** — per-account rate on login (incl. MFA), `/oauth2/token`, password-reset confirmation exceeding threshold → `429` | Yes | `RateLimitFilter.java:66-96` (three-way dispatch), `RateLimiter.java:42-57` (per-path buckets), `RateLimitProperties.java` (thresholds) | `RateLimiterTest` (8), `RateLimitFilterTest` (15), `RateLimitIntegrationTest` (6) — all green | No | No — MFA is folded into the login path per D1, a documented reading of R41's wording against this codebase's real architecture (no separate MFA endpoint exists), not a silent narrowing |
| **R42** — service-level limit is a backstop, not the primary defense | Yes | Frozen brief's own framing; D4 (checks before credential validation, real DoS-backstop value); D6 (unbounded registry accepted specifically because ingress is the primary defense against a genuinely distributed attack) | No dedicated test (R42 is a design-posture requirement, not a behavior) — correctly untested, matching AC6's own framing | No | No |

## This Task's Own Design Decisions — honored?

| Decision | Honored? | Evidence |
|---|---|---|
| **D1** — MFA folded into the login bucket, no separate endpoint | Yes | `RateLimitFilter.isLoginRequest` matches only `/login`; `TotpAuthenticationProvider` runs inside that same POST, per Phase 0's repository trace |
| **D2** — login key is the raw normalized (trim+lowercase) submitted username, no DB lookup | Yes | `RateLimitFilter.java:113-127` (`normalizedUsername`); no `AccountService`/`AccountRepository` import anywhere in the `ratelimit` package; `RateLimitFilterTest.loginRequestConsumesLoginBucketWithNormalizedUsername` |
| **D3** — password-reset and `/oauth2/token` keyed by SHA-256 hash of the submitted token, accepted per-session (not per-account) narrowing | Yes | `RateLimitFilter.java:129-138` (`passwordResetTokenHash`, `refreshTokenHash`); proven in both `RateLimitFilterTest` (unit) and `RateLimitIntegrationTest` (real HTTP, real hash) |
| **D4** — rate check runs before credential/password validation | Yes | `RateLimitFilter` wired via `.addFilterBefore(rateLimitFilter, DisableEncodeUrlFilter.class)` (chain 1) / `.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)` (chain 2) — `SecurityChainsConfig.java:56,102`; `RateLimitIntegrationTest` attempts use a deliberately wrong password and still trigger 429; ordering now also asserted directly by `RateLimitIntegrationTest.rateLimitFilterPrecedesAuthenticationInBothSecurityChains` (Kimi Phase 11 Gap 5 closure) |
| **D5** — limiter internal error fails open, logs the failure | Yes | `RateLimitFilter.java:85-88` (catch-all around the dispatch, `probe = null` on exception); `RateLimitFilterTest.rateLimiterThrowingFailsOpen` |
| **D6** — unbounded, non-evicting per-key registry, accepted as a documented backstop limitation | Yes | `RateLimiter.java:28-30` (plain `ConcurrentHashMap`, no eviction); documented in the class Javadoc; no bounded-cache dependency added, matching the frozen brief's explicit "no code change needed" disposition |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1-AC3 (R41, three in-scope paths) | **Met** | `RateLimiterTest` (per-path threshold enforcement), `RateLimitFilterTest` (key derivation + dispatch), `RateLimitIntegrationTest` (all three paths driven past threshold over real HTTP) |
| AC4 (independent keys don't interfere) | **Met** | `RateLimiterTest.differentKeysHaveIndependentLoginBuckets`, `.loginPasswordResetAndOauthTokenBucketsAreIndependentOfEachOtherForTheSameKey`; `RateLimitIntegrationTest.differentAccountsHaveIndependentLoginBuckets` |
| AC5 (refill recovers a throttled key, not a permanent block) | **Met** | `RateLimiterTest.exhaustedBucketAllowsARequestAgainAfterItsWindowRefills` — **added during this phase**; see Findings below |
| AC6 (R42 — no claim of replacing ingress/IP-level protection) | **Met** | Posture-only; no code or test claims otherwise; D6's Javadoc explicitly frames the limitation in these terms |
| AC7 (429 body shape: `application/problem+json` + `Retry-After`) | **Met** | `RateLimitFilterTest.whenBucketIsExhaustedRequestIsRejectedWith429` (full shape incl. `type`/`title`/`instance`); `.subSecondWaitRoundsUpToOneSecondRetryAfter` (Kimi Phase 11 Gap 2 closure — the `Math.max(1, ...)` ceiling); `RateLimitIntegrationTest`'s named test (real HTTP) |
| AC8 (`client_credentials`/`authorization_code` unaffected; other grants don't exist) | **Met** | `RateLimitFilterTest.oauthTokenClientCredentialsGrantIsNeverRateLimited`, `.oauthTokenAuthorizationCodeGrantIsNeverRateLimited`; `RateLimitIntegrationTest.oauthTokenClientCredentialsGrantIsNeverRateLimited` (35 real requests, never throttled) |
| AC9 (D5 — limiter internal error allows the request through, logs) | **Met** | `RateLimitFilterTest.rateLimiterThrowingFailsOpen` |

## Dependency and File-Scope Check

| Constraint | Honored? | Evidence |
|---|---|---|
| Bucket4j dependency | Yes, with a corrected coordinate | `pom.xml`: `com.bucket4j:bucket4j_jdk17-core:8.14.0` — the frozen brief named `8.10.1`, which does not exist for this artifact ID; corrected during Phase 6 implementation (documented in `artifacts/06-implementation-notes.md`), not a silent deviation |
| No `lockout_state`/`LockoutService`/`LoginFailureHandler` changes | Yes | `git diff` scope for T31 touches none of these files |
| No `AccountService`/`AccountRepository` dependency (D2) | Yes | No import of either in the `ratelimit` package |
| No T25-T30 file touched, no new Flyway migration | Yes | Only `SecurityChainsConfig.java`, `ProblemTypes.java`, `application.properties`, `pom.xml` modified outside the new `ratelimit` package; no `db/migration` file added |

---

## Findings from this phase

**AC5 had no test until this phase.** `RateLimiterTest`'s original class-level Javadoc claimed
refill/recovery was "proven end-to-end in `RateLimitIntegrationTest`," while
`RateLimitIntegrationTest`'s own Javadoc said the opposite — "only exhaustion is proven here, not
post-window recovery." Reading both together while building this traceability matrix surfaced that
neither file actually tested it: a named Acceptance Criterion (AC5) and an explicit line in the
frozen brief's own Required Tests section ("post-refill recovery succeeds again") had silently gone
untested through Phases 10 and 11 — Kimi's Phase 11 review did not catch this one either. Verified
against Bucket4j 8.14.0's actual `LocalBucketBuilder`/`Bandwidth` behavior (greedy refill is
continuous, not only at minute boundaries) before fixing: added
`RateLimiterTest.exhaustedBucketAllowsARequestAgainAfterItsWindowRefills`, using a 60/minute
threshold so the refill rate is one token per second — proving genuine Bucket4j refill behavior
with a real ~1.1-second wait instead of the full 60-second window the production defaults would
require. Both files' docstrings were corrected to match reality. Rerun: **37/37 pass** (up from
36/36), Docker-backed, no regressions.

No other gaps found. Kimi's five Phase 11 findings (Gaps 1-5) were verified against source and all
closed with new tests in Phase 10's manifest (`RateLimitPropertiesTest`, three `RateLimitFilterTest`
additions, one `RateLimitIntegrationTest` addition) — no production code changed in response to any
of them, consistent with their own characterization as coverage gaps, not defects.

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. All three in-scope HTTP paths are protected, wired ahead
of credential validation, fail open on internal error, and return a correctly-shaped 429. The one
real coverage gap this phase found (AC5) was a documentation/test-writing oversight, not a
production defect — Bucket4j's own refill mechanics were never in doubt, only whether this
codebase's own test suite actually exercised them, and it now does.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC9 are all Met, with AC5 met
only after this phase's own fix (see Findings).

**(3) Does it violate any LOCKED decision?** No LOCKED decision is scoped to this task (frozen brief
confirms this explicitly).

**(4) Remaining risks?**
- **D3's per-session (not strictly per-account) granularity** on `/oauth2/token` and
  password-reset remains an accepted, documented narrowing — an account with many active sessions
  gets a proportionally larger effective budget. Explicitly accepted at the Phase 4 gate, not a
  defect.
- **D6's unbounded bucket registry** has no eviction; a sustained attack using a very large number
  of distinct keys grows this map for the process lifetime. Explicitly accepted as a backstop-only
  limitation, with ingress-level defense assumed to be the actual stop for that threat class (R42).
- **Bucket4j coordinate correction** (`8.10.1` → `8.14.0`) was necessary and is now consistent
  everywhere (`pom.xml`, all test files); no residual references to the wrong coordinate remain.

**Verdict: PASS** — every requirement, design decision, and acceptance criterion for T31 traces to
implemented, tested code; the one gap found during verification (AC5) was closed within this phase,
not merely noted.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
