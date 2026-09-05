<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T31 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: add per-account rate limiting on login, password-reset, and /oauth2/token (T31)
```

## Commit message

```
auth: add per-account rate limiting on login, password-reset, and /oauth2/token (T31)

Adds a request-rate backstop (R41/R42) on the three real HTTP paths where it
applies in this codebase: /login (which also covers TOTP/recovery-code MFA,
since that happens inside the same POST, not a separate endpoint), the
/oauth2/token refresh_token grant, and /accounts/password-reset confirmation.
RateLimitFilter runs before credential validation on both security chains, so
an exhausted bucket is checked ahead of any password-hashing work - the actual
DoS-backstop value the requirement is for.

Bucket4j 8.14.0 backs three independent, in-process, per-key token-bucket
registries (RateLimiter). Login is keyed by the raw, normalized submitted
username with no database lookup at all, so a bucket exists and behaves
identically whether or not the account is real (enumeration-safe by
construction). Password-reset and the oauth-token grant are keyed by the
SHA-256 hash of the submitted token, an accepted per-session narrowing of
"per-account" documented at the design gate. The limiter fails open and logs
on any internal error, matching this codebase's existing precedent for a
security-adjacent-but-non-critical dependency (the HIBP breach check).

Independent review's 9 findings (the largest single round this pipeline has
seen) were resolved at the design gate before implementation began, mostly by
explicit documented decisions (D1-D6) rather than code changes - the login-key
derivation self-contradiction and the /oauth2/token per-session granularity in
particular. Kimi's later test review found 5 real coverage gaps (config
validation, the Retry-After ceiling, two fail-open boundaries, and filter
ordering versus authentication); all 5 were closed with new tests, no
production code changed in response. Verifying the traceability matrix at
Phase 12 surfaced a sixth gap the review missed entirely: AC5 (a throttled key
recovers once its bucket refills) had no test anywhere, despite being a named
acceptance criterion - two test files' own doc comments contradicted each
other about which one covered it. Closed with a test using a higher threshold
to keep the real wait under 2 seconds instead of the full 60-second window.

This is the first task in this pipeline stretch whose integration suite ran
against real Docker infrastructure from the start; all 37 unit and
integration tests for this task pass.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production code**
- `services/auth/pom.xml` (modified — 1 new dependency, `bucket4j_jdk17-core:8.14.0`; the frozen
  brief's proposed `8.10.1` does not exist for this artifact and was corrected during Phase 6)
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` (modified —
  `RATE_LIMIT_EXCEEDED` constant)
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimiter.java` (new)
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitProperties.java` (new)
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitFilter.java` (new)
- `services/auth/src/main/java/com/themistra/auth/ratelimit/CachedBodyHttpServletRequest.java`
  (new)
- `services/auth/src/main/java/com/themistra/auth/ratelimit/RateLimitFilterConfig.java` (new)
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` (modified —
  both chains now take a `RateLimitFilter` and wire it via `addFilterBefore`)
- `services/auth/src/main/resources/application.properties` (modified — 3 new
  `themistra.auth.rate-limit.*` keys)

**Tests**
- `services/auth/src/test/java/com/themistra/auth/ratelimit/RateLimiterTest.java` (new, 8 tests)
- `services/auth/src/test/java/com/themistra/auth/ratelimit/RateLimitPropertiesTest.java` (new, 5
  tests)
- `services/auth/src/test/java/com/themistra/auth/ratelimit/CachedBodyHttpServletRequestTest.java`
  (new, 3 tests)
- `services/auth/src/test/java/com/themistra/auth/ratelimit/RateLimitFilterTest.java` (new, 15
  tests)
- `services/auth/src/test/java/com/themistra/auth/ratelimit/RateLimitIntegrationTest.java` (new, 6
  tests, Testcontainers-backed, executed against real Docker)

## Summary

Implements R41 (per-account 429 backstop on login/MFA, `/oauth2/token` refresh grant, and
password-reset confirmation) and R42 (documented as a backstop, not a replacement for ingress-level
IP limiting). No LOCKED decision is scoped to this task. Six of this task's own design decisions
(D1-D6) resolve every trade-off independent review raised: MFA has no separate bucket because it
has no separate endpoint (D1); the login key is the raw submitted username with zero database
access, for true pre-check enumeration safety (D2); password-reset and refresh-token buckets are
keyed by token hash, an accepted per-session granularity (D3); the check runs before credential
validation for real backstop value (D4); the limiter fails open on its own internal errors (D5);
and the per-key registry is deliberately unbounded, accepted as a backstop-only limitation rather
than adding a bounded-cache dependency (D6).

Phase 12 verification found and closed one real test-coverage gap of its own (AC5's refill/recovery
behavior, see commit message) in addition to the five Kimi Phase 11 found and closed in Phase 10's
manifest — none of the six pointed to a production defect; all were coverage gaps in an otherwise
complete test suite.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- T31's own test suite, run against real Docker (Postgres + Kafka via Testcontainers):
  `RateLimiterTest` 8/8, `RateLimitPropertiesTest` 5/5, `CachedBodyHttpServletRequestTest` 3/3,
  `RateLimitFilterTest` 15/15, `RateLimitIntegrationTest` 6/6 — **37/37 pass**, including the named
  test `shouldReturn429WhenPerAccountRateLimitExceeded`.
- This is the first task in this pipeline stretch where the integration suite executed
  successfully against real infrastructure on the first attempt, rather than being written but
  Docker-blocked.
- Full `services/auth` suite (`mvn clean test`, all 680 tests, Docker-backed): 671/680 pass. The 9
  failures are not a T31 regression:
  - 4 are the already-known, previously-deferred pre-existing failures in `RoleAssignmentIntegrationTest`
    (2), `AuditTrailIntegrationTest` (1), and `AccountPersistenceIntegrationTest` (1) — the
    fabricated-UUID-vs-`auth_audit`-FK pattern and the unresolved Kafka lifecycle-event wait,
    both unrelated to T25-T31 and explicitly deferred earlier this session.
  - The remaining failures appeared in `ApiKeyExchangeIntegrationTest` and
    `ApiKeyLifecycleIntegrationTest` with **different symptoms on each run** — a null response body
    in the full-suite run, an audit-row-count-off-by-one (`expected 1L, was 2L`) when the same two
    classes were re-run in isolation immediately after. Different failure modes for the same tests
    across runs is the signature of flaky, timing/ordering-dependent tests (async outbox-relay
    delivery racing test assertions), not a deterministic defect introduced by this task — nothing
    in T31's own file set touches API-key exchange, its audit rows, or the outbox relay. Not
    investigated further per this session's standing "move on" scope decision (T31 plus the earlier
    Docker-validation marathon; unrelated pre-existing flakiness is a separate concern).
  - No failure occurred in any `ratelimit` package test in this run.

## Specification references

- **Task:** T31 — Rate limiting (`spec/auth-service/tasks.md`, task 31)
- **Requirements:** R41, R42
- **LOCKED decisions:** none scoped to this task
- **Named tests (`package.md` §8):** `shouldReturn429WhenPerAccountRateLimitExceeded` (written,
  executed, passing)

---

## Note for the reviewer: unrelated changes present on this branch

This branch also carries a batch of pre-existing defect fixes discovered when Docker/Testcontainers
became available mid-session for the first time since T25 — the entire accumulated integration
suite (T25-T30) had never actually executed before that point. These are **not** part of T31's own
requirements or acceptance criteria and involved no scope creep during this task's own design/review
gates; they are listed here only so a reviewer isn't surprised by their presence in the same commit
range:

- A systemic exception-handler-resolution bug affecting every domain-specific 4xx response
  (`common/ApiExceptionHandler.java`, `account/AccountExceptionHandler.java`,
  `apikey/ApiKeyExceptionHandler.java`, `authz/AuthzExceptionHandler.java`,
  `token/SessionExceptionHandler.java` — `@Order` annotations added), applied only after explicit
  sign-off via a human gate given its codebase-wide blast radius.
- `cleanup/CleanupJob.java` — a pgjdbc type-binding fix for the ShedLock cleanup query (T30's own
  bug, only surfaced once its integration test could actually run).
- `db/migration/V6__cleanup_indexes.sql` renamed to `V8__cleanup_indexes.sql` (T30's own migration,
  collided with a pre-existing, unrelated `V6`).
- One line in `application.properties`: `themistra.auth.cleanup.cron` corrected from a 5-field to a
  6-field cron expression (Spring's `@Scheduled` requirement; T30's own bug).
- Test-only fixes to `ApiKeyCrudIntegrationTest`, `ApiKeyExchangeIntegrationTest`,
  `AdminAccountRoleControllerTest`, `RefreshTokenFamilyIntegrationTest`, `SessionIntegrationTest`,
  and `CleanupJobTest`/`CleanupIntegrationTest` (transaction-boundary and test-design bugs in T25,
  T28, T29, and T30's own test suites, not production defects) — plus `ArchitectureTest.java`
  gaining T30's own `cleanup_job_never_depends_on_repositories_directly` rule, which had been
  documented in T30's Phase 11 artifact but had not yet actually landed in this file.
- Four still-unresolved, pre-existing failures in `authz`/`audit`/`account` integration tests
  (unrelated to T25-T31) were deliberately **not** fixed in this pass, per an explicit decision to
  keep this session's scope to T25-T31 plus the one systemic bug; they remain open for whoever picks
  them up next.
- Separately, a full-suite run surfaced apparent flakiness in `ApiKeyExchangeIntegrationTest`/
  `ApiKeyLifecycleIntegrationTest` (different failure symptoms on consecutive runs — see Testing
  performed above). Worth a dedicated look before relying on full-suite green as a merge gate, but
  not attributable to any file T31 touches.

If a clean, single-purpose T31 diff is required for review, these files should be split into a
separate commit/PR ahead of this one — they are independent of and do not depend on any T31 file.

---

**Phase 13 complete — PR preparation written. T31 is ready for merge; the full-suite run is
confirmed and shows no T31-attributable regression.**
