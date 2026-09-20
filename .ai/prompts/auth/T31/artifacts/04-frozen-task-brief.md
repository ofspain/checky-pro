<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T31 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
9 findings — the largest single review round in this pipeline stretch so far). All 9 verified
against actual source before disposition. femi decided the six findings with genuine trade-off
weight via human gate; the remaining three are mechanical amendments folded in directly.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | MFA verification silently folded into login, contradicting R41's literal 4-path wording | High | **Resolved, femi's gate decision.** Fold into the login bucket; documented explicitly as D1 below, not silently reinterpreted. |
| 2 | Login key derivation self-contradicted (`findLoginView` IS an existence check) | Medium | **Resolved, femi's gate decision.** Key on the raw normalized email, no DB lookup (D2). |
| 3 | `/oauth2/token` keyed per-refresh-token, not per-account (session-count amplification) | Medium | **Resolved, femi's gate decision.** Accepted as a documented narrowing (D3). |
| 4 | Rate-limit check timing relative to credential validation unspecified | Medium | **Resolved, femi's gate decision.** Checked before validation (D4). |
| 5 | No `Retry-After` header on 429 responses | Low | **Accepted, folded in.** Added to Outputs/AC7 below. |
| 6 | Fail-open vs. fail-closed on limiter internal errors unspecified | Medium | **Resolved, femi's gate decision.** Fail-open with logging (D5), matching this codebase's own R13/HIBP precedent. |
| 7 | Bucket algorithm (fixed/sliding/token-bucket) unspecified | Low | **Accepted, folded in.** Token bucket, capacity = threshold, refill = threshold/60s — Bucket4j's most direct single-bandwidth configuration. |
| 8 | Unbounded per-key bucket registry growth | Low | **Resolved, femi's gate decision.** Accepted as a documented backstop limitation (D6), no bounded cache added. |
| 9 | Other grant types (e.g. `password`) not addressed in scoping | Low | **Accepted, clarified — no code change needed.** Verified against `target-design.md`: `password` grant, implicit, token exchange, and device code are all explicitly unsupported (D-002/D-008) in this SAS configuration. `client_credentials`, `authorization_code`, and `refresh_token` are the only three grant types that exist at all here, so AC8's scoping (naming the first two as unaffected) is already exhaustive. |

## Task

Rate limiting — per-account request-rate buckets for login (covering MFA verification),
`/oauth2/token` (refresh_token grant), and password-reset confirmation, rejecting with `429` as an
ingress backstop (R42).

## Purpose

Unchanged from Phase 2.

## Scope

**In:** a request-rate limiter on three real HTTP paths; Bucket4j as a new dependency;
429-status integration tests; the `Retry-After` header on 429 responses.

**Out:** `lockout_state`/`LockoutService`; any new MFA-specific endpoint; ingress/edge
configuration; `password`/implicit/device-code/token-exchange grants (confirmed unsupported by
this deployment, not merely unaddressed).

## Business Rules

- **R41.** Exceeding the configured per-account request rate on login (incl. MFA, D1),
  `/oauth2/token` (refresh_token grant, D3), or password-reset confirmation returns `429`.
- **R42.** Backstop, not primary defense.

## Locked Decisions

None.

## This Task's Own Design Decisions (D1-D6, decided at this gate)

- **D1 (Finding 1).** MFA (TOTP/recovery-code) verification is **not** independently rate-limited.
  It happens inside the same `/login` POST as password verification
  (`TotpAuthenticationProvider`), so it is covered by the login-path bucket by construction. This
  is a deliberate, documented reading of R41's four named items as three actually-distinct HTTP
  paths in this codebase's real architecture — not a silent reinterpretation. A future task adding
  a genuinely separate MFA-verification endpoint would need its own bucket at that time.
- **D2 (Finding 2).** The login-path bucket key is the **raw, normalized email/username string**
  submitted in the request — no `AccountService`/`findLoginView` lookup, no database access at
  all. True pre-check enumeration safety: a bucket is created and consumed identically whether the
  submitted email belongs to a real account or not.
- **D3 (Finding 3).** The `/oauth2/token` limiter (refresh_token grant only) is keyed by the
  **SHA-256 hash of the presented refresh token**, accepted as a per-session (not strictly
  per-account) granularity. A single account with N active sessions effectively gets N × the
  configured threshold. Documented as an accepted narrowing of "per-account," justified by R42's
  backstop framing and by avoiding an extra family/authorization lookup on this hot path.
- **D4 (Finding 4).** The rate-limit check runs **before** credential/password validation on
  `/login` and password-reset. This is what gives it real DoS-backstop value (R42) — checking
  after validation would let an attacker force unlimited password-hashing work before the limiter
  ever engages. Accepted trade-off: a legitimate user sharing an IP/timing coincidence with an
  attacker's junk requests could be briefly throttled — acceptable since the bucket is temporary
  (AC5), not a lockout.
- **D5 (Finding 6).** If the limiter itself throws (key derivation, Bucket4j internal error), the
  request is **allowed through** (fail-open) and the error is logged. Matches this codebase's own
  established precedent for a security-adjacent-but-non-critical-path dependency failing
  (R13/L2's HIBP breach-check: allow and audit when the external check can't run) — a bug in a
  backstop defense must not become an outage of the primary functionality it backstops.
- **D6 (Finding 8).** The per-key bucket registry is an **unbounded, non-evicting** map. Accepted
  as a documented limitation consistent with R42's "backstop, not primary defense" framing — a
  genuinely massive distributed-identity attack is exactly the class of threat ingress-level
  defenses exist to stop, and adding a bounded-cache dependency (Caffeine) for a lightweight
  backstop was judged not worth the added complexity at this stage.

## Dependencies

`com.bucket4j:bucket4j_jdk17-core:8.10.1` (new); `Hashing.sha256` (existing, D3's token key);
`ProblemTypes` (existing, new 429 constant); `Clock` (existing). `AccountService`/`findLoginView`
is explicitly **not** a dependency of this task per D2.

## Inputs

Each incoming request on the three in-scope paths.

## Outputs

`429 Too Many Requests`, `application/problem+json`, including a `Retry-After` header (seconds
until the bucket's next available token, Finding 5) when a bucket is exhausted.

## State Changes

None persisted (in-process, ephemeral buckets, per D6 unbounded for the process lifetime).

## Files to Create

- New limiter class (`token` or a new `ratelimit` package — Phase 5's call) holding three
  Bucket4j-backed, per-key bucket registries (login: raw email key; password-reset: raw email or
  account-identifying request field, Phase 5 to confirm shape; `/oauth2/token`: refresh-token
  hash), each fail-open per D5.
- A `Filter` (or two) writing the `application/problem+json` + `Retry-After` 429 body directly,
  wired before credential validation per D4.
- A validated `@ConfigurationProperties` record for the three thresholds (login 10/min,
  password-reset 5/min, `/oauth2/token` 30/min — Phase 2's proposed defaults, not further
  challenged at this gate since OQ1's own framing was "propose and confirm," and no alternative
  values were raised in review).

## Files to Modify

- `services/auth/pom.xml` (Bucket4j).
- `token/SecurityChainsConfig.java` (wire the filter(s) onto both chains, before existing
  authentication/credential-validation logic per D4).
- `common/ProblemTypes.java` (429 constant).
- `application.properties` (threshold keys).

## Files NOT to Modify

- `lockout_state`/`LockoutService`/`LoginFailureHandler`'s existing logic.
- `AccountService`, `AccountRepository` (per D2, not needed by this task at all).
- Any T25-T30 file. No new Flyway migration.

## Acceptance Criteria

- **AC1-AC3 (R41).** Each of the three in-scope paths (login incl. MFA per D1; password-reset;
  `/oauth2/token` refresh_token grant) returns 429 once its own threshold is exceeded, checked
  before credential validation (D4).
- **AC4.** Independent keys (D2/D3) never interfere with each other's buckets.
- **AC5.** Token-bucket refill (Finding 7) means a previously-throttled key succeeds again once
  its bucket has refilled — not a permanent block.
- **AC6 (R42).** No claim that this replaces ingress/IP-level protection.
- **AC7.** 429 body is `application/problem+json` with a `Retry-After` header (Finding 5).
- **AC8.** `client_credentials` and `authorization_code` requests to `/oauth2/token` are
  unaffected; `password`/implicit/device-code/token-exchange grants don't exist in this deployment
  (Finding 9) and need no explicit handling.
- **AC9 (D5).** A limiter internal error allows the request through and logs the failure, rather
  than rejecting or 500-ing.

## Required Tests

- `shouldReturn429WhenPerAccountRateLimitExceeded` (named, `package.md` §8).
- Per-path: under-threshold succeeds; at-threshold-plus-one returns 429 with `Retry-After`;
  different keys don't interfere; post-refill recovery succeeds again.
- D2: two different emails (one real, one fabricated) both get independently rate-limited
  identically — no behavioral difference reveals account existence.
- D3: two sessions (two refresh tokens) for the same account each get their own full budget
  (documents the accepted narrowing, doesn't hide it).
- D4: a request that would fail credential validation is still counted toward (and can trigger)
  the 429, proving the check happens first.
- D5: a forced limiter exception still allows the request through.
- AC8: a `client_credentials` request to `/oauth2/token` is never throttled by this mechanism.
- HTTP-layer, Testcontainers-backed, for at least login and password-reset (chain 2); a real
  refresh token obtained via the existing login-flow pattern for `/oauth2/token`.

## Constraints

- **Thread-safety:** Bucket4j buckets are thread-safe; the per-key registry must be safe under
  concurrent first-access for the same key (e.g. `ConcurrentHashMap.computeIfAbsent`).
- **Memory:** unbounded per D6, explicitly accepted.
- **Module boundaries (L12):** no `account`/`token` repository imports; per D2, not even
  `AccountService`.
- **Security:** true pre-check enumeration safety (D2); fail-open on internal error (D5).
- **Performance:** O(1) per request.

## Open Questions

No blockers. OQ1's proposed thresholds (10/5/30 per minute) stand as proposed at Phase 2, not
challenged in review — treated as confirmed by omission at this gate. All 9 Phase 3 findings
resolved above.

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
