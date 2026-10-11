# payment · T03 · Phase 7 — Self Review

Reviewed `application.properties`, the five `@ConfigurationProperties` records,
`PublicEndpoints`, `ResourceServerConfig`, `PaymentServiceApplication`'s change, and all five test
files against the frozen brief (Phase 4) and `agents.md`, across every listed category.

**Two additional checks run beyond what Phase 6 already did:**
1. Grepped `application.properties` for the word `password` outside the one real property
   line — exactly 1 match (the real line itself). No comment-ambiguity risk here (unlike the
   three prior incidents this session in T01/T02's own files).
2. Grepped every test file for a direct reference to `PublicEndpoints.PATTERNS` or
   `PublicEndpoints.class` — **zero matches.**

## Findings

### 1. `PublicEndpoints.PATTERNS`'s exact content is never directly asserted

- **Issue:** AC3 requires `PublicEndpoints.PATTERNS` to be exactly the three named paths.
  `ResourceServerConfigTest` only proves this *behaviorally* (the health path is reachable, one
  arbitrary non-public path requires auth) — nothing introspects the array itself.
- **Severity:** Low — a future edit that widens a pattern (e.g. `/actuator/**` in place of
  `/actuator/health/**`) would still pass every current test (the health path would still be
  reachable, the one tested non-public path would still be protected) while silently exposing
  every other actuator sub-path.
- **Recommendation:** Add a direct assertion on `PublicEndpoints.PATTERNS` itself
  (`containsExactly(...)`), not deferred to Phase 9 given it's cheap and this task's own test
  suite is still open — applying it now.

### 2. `InvoiceProperties.amountToleranceBaseUnits` is a `long`, not a wider type

- **Issue:** Token base-unit amounts elsewhere in this schema are `NUMERIC(78, 0)` (L6) — far
  wider than a 64-bit `long`'s own range. This config field is currently always `0` (Q1's own
  resolution: exact match only), so no real value anywhere near `Long.MAX_VALUE` is possible
  today.
- **Severity:** Low — not exploitable now; flagged for the record since a future revisit of Q1
  (a real, non-zero tolerance band) could silently overflow for a sufficiently large configured
  value, with no validation catching it.
- **Recommendation:** No change now — the frozen brief's own AC1 scoped this field to "positive
  timeouts/TTL, non-blank URLs," not a wider numeric type, and widening it today would be
  speculative given the value's own fixed-at-zero reality. Documented as a known, accepted
  narrowness for whoever revisits Q1 later, not silently carried forward unremarked.

## Review by category (no further findings)

- **Correctness/boundary conditions:** `@NotBlank` and `@Positive`/`@PositiveOrZero` match each
  field's own real semantics; confirmed via Phase 6's own real `ApplicationContextRunner` test
  runs (not just read from the source).
- **Thread-safety:** All new beans (`SecurityFilterChain`, the two RFC 9457 handler lambdas) are
  stateless singletons — no mutable state introduced.
- **Transaction boundaries / module boundaries:** Not applicable (no entity/transactional code);
  `common`/`common.config` package placement matches every sibling's own convention.
- **Enumeration-safety/secret-handling (L13):** No secret beyond the one documented datasource
  placeholder; the `receipt.*` deviation (non-blank local default replacing design.md's own
  empty-fallback verbatim text) is disclosed in both the property file's own comment and Phase 6's
  implementation notes, not silently diverged.
- **Readability/complexity:** Every new file matches its sibling template's shape and comment
  density; no novel abstraction introduced beyond what the frozen brief authorized.

## Result

**One finding applied directly (finding 1), one documented without a code change (finding 2).**

Applied finding 1: added `PublicEndpointsTest` (14 methods), mirroring `notification-service`'s
own identical test file exactly — a direct `containsExactlyInAnyOrder` assertion on
`PublicEndpoints.PATTERNS`, parameterized checks that every declared public path is unblocked
and every sensitive actuator path is not, and a source-text sweep proving exactly one
`.permitAll()` call exists in `ResourceServerConfig`, scoped to `PublicEndpoints.PATTERNS`.
Mutation-verified: added a 4th pattern (`/actuator/env`) to `PublicEndpoints.PATTERNS` → 2
failures (confirmed real). Reverted; `git status` confirms clean. Final suite: **61/61 passing.**
