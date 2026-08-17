<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T31 · Phase 9 — Review Resolution

**Human Approval gate.** Consumes the self-review (Phase 7, 1 finding) and independent review
(Phase 8, Kimi, 6 findings). All findings verified against actual source before disposition. femi
decided the one finding with genuine trade-off weight via human gate; the rest were mechanical
fixes, expected pre-Phase-10 gaps, or documentation-only.

## Self-review (Phase 7) findings

| # | Finding | Disposition |
|---|---|---|
| 1 | 429 response's `instance` field always absent, unlike every other rejection | **Superseded by Kimi's Phase 8 Finding 2** (identical finding, independently confirmed) — resolved below. |

## Independent review (Phase 8, Kimi) findings

| # | Finding | Confidence | Disposition |
|---|---|---|---|
| 1 | `Bandwidth.classic(long, Refill)` is deprecated in the resolved Bucket4j 8.14.0 | High | **ACCEPTED, applied.** Verified via `javap -v` against the actual resolved jar's bytecode (`Deprecated: true` on that exact method) before fixing — not just trusting the compiler warning's text. |
| 2 | 429 body omits `instance`, diverging from every other problem response | High | **ACCEPTED, applied.** Same finding as my own Phase 7 Finding 1. |
| 3 | Login-path keying assumes form-encoded `username`, would break a hypothetical JSON login | Medium | **Accepted, documented — no functional change.** Verified this is not a new assumption: `SecurityChainsConfig`'s `.formLogin(...)` has no JSON-body support configured at all, so `UsernamePasswordAuthenticationFilter` itself already only authenticates form-encoded submissions today — there is no JSON login capability in this codebase for the rate limiter to have broken. Documented explicitly so a future JSON-login addition doesn't silently forget to update this method too. |
| 4 | Password-reset confirmation keyed by token hash gives no real per-account limit (many tokens = many independent budgets) | Medium | **ACCEPTED as a documented narrowing, femi's gate decision.** Matches the precedent already accepted for `/oauth2/token` (D3) — a reset token is already single-use and time-limited, so this limit's real value is slowing brute-force guessing of one token's confirmation, not capping how many tokens an account can request. |
| 5 | `CachedBodyHttpServletRequest` is created before checking whether the body actually contains a token | Low | **Rejected — inherent to the design, not avoidable.** Whether the body contains a usable token can only be known by reading it; wrapping is a prerequisite to that check, not optional overhead that could be deferred. |
| 6 | No tests exist yet for the `ratelimit` package | High (missing) / N/A (expected) | **No action — expected.** Kimi itself confirmed this is Phase 10's job. |

## Exact changes made

**`services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimiter.java`** (Finding 1):
`bandwidthFor` now builds via `Bandwidth.builder().capacity(perMinute).refillGreedy(perMinute,
Duration.ofMinutes(1)).build()` instead of the deprecated `Bandwidth.classic(...)`/`Refill.greedy(...)`
pairing; removed the now-unused `Refill` import.

**`services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitFilter.java`** (Findings 2, 3, 4):
- `writeTooManyRequests` now takes the `HttpServletRequest` and calls
  `problem.setInstance(URI.create(request.getRequestURI()))`, matching every other rejection's
  response shape.
- `normalizedUsername`'s Javadoc now explicitly documents the form-encoded-only assumption and
  why it isn't a new gap.
- The class Javadoc now explicitly documents the password-reset per-token (not per-account)
  narrowing and the reasoning femi confirmed at this gate.

## Verification performed

- `mvn -pl services/auth -am clean compile test-compile` — clean, no errors.
- `mvn -pl services/auth -am compile` — no deprecation warnings (confirms Finding 1 is fully
  resolved, not just suppressed).

---

**Phase 9 complete — review resolved, femi signed off.** Proceed to Phase 10 (Test Generation).
