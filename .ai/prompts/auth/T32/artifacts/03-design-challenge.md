<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# auth · T32 · Phase 3 — Design Challenge

Consumed `artifacts/02-task-implementation-brief.md`, `ArchitectureTest.java`,
`PublicEndpoints.java`, `SecurityChainsConfig.java`, and `PublicEndpointsTest.java`.

No conflicts with `L11`, `L12`, or `agents.md` standing rules. Findings only — no implementation.

---

## Finding 1 · The AC2 structural rule is under-specified and may be technically fragile

**Severity:** High

**Evidence:** The brief explicitly flags the rule as "Tentative" and says "the exact Spring
Security type(s) to name and whether ArchUnit 1.3.0 can express this cleanly needs verification
in Phase 5." It currently gestures at "`AuthorizedUrl` / the `authorizeHttpRequests` registry
types" without committing to a specific type.

`SecurityChainsConfig` currently uses `auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll()`
and `auth.requestMatchers(m.method(), m.pattern()).permitAll()`. In Spring Security 6 these chain
through `AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry` to
`AuthorizedUrl`. A class-dependency rule must target the right return/parameter type or it will
miss the `permitAll()` call entirely. Conversely, if it targets `HttpSecurity` itself, every
security configuration class everywhere would trip it.

**Recommended brief amendment:** Before freezing, require Phase 5 to spike the exact ArchUnit
predicate and confirm it fails when a non-`SecurityChainsConfig` class calls `.permitAll()`. Add a
fallback formulation to the brief: if dependency-based ArchUnit cannot isolate `.permitAll()` from
`.authenticated()` (see Finding 2), the rule should be reformulated as a plain JUnit reflection/
bytecode scan that asserts only `SecurityChainsConfig` contains invocations of `.permitAll()`.

---

## Finding 2 · A dependency rule on `AuthorizedUrl` would also forbid legitimate `.authenticated()` calls

**Severity:** High

**Evidence:** `AuthorizedUrl` exposes both `.permitAll()` and `.authenticated()` (and
`.hasAuthority()`, `.hasRole()`, etc.). A rule that says "only `SecurityChainsConfig` may depend on
`AuthorizedUrl`" prevents *any* other class from using the authorization DSL, not just from adding
public endpoints.

That may sound desirable, but it is broader than the task statement ("no new handler is
permitAll outside the list"). More importantly, the existing `authorizationServerChain` in
`SecurityChainsConfig` itself uses `.anyRequest().authenticated()` through a different chain path;
other future configuration classes might legitimately need `.authenticated()` without adding
public paths. The brief should be precise about whether the goal is "only SecurityChainsConfig may
configure authorization" or "only SecurityChainsConfig may call `.permitAll()`".

**Recommended brief amendment:** Change the rule objective to "only `SecurityChainsConfig` may
invoke `.permitAll()`" and choose an ArchUnit predicate or reflection-based check that inspects
method invocations, not merely class dependencies. If ArchUnit cannot express method-level
prohibition cleanly, use a custom ArchUnit condition or a plain reflection test.

---

## Finding 3 · The rule ignores other ways to expose endpoints without authentication

**Severity:** Medium

**Evidence:** Spring Security provides several public-exposure mechanisms beyond
`AuthorizedUrl.permitAll()`:

- `WebSecurityCustomizer.ignoring()` — bypasses the filter chain entirely.
- `@PermitAll` / `@PreAuthorize("permitAll()")` on controller methods.
- Method-security meta-annotations.
- A custom filter that does not enforce authentication.
- `securityMatcher(...)` combined with `anyRequest().permitAll()` (uses `AuthorizedUrl` but the
  brief's proposed rule should catch this if it works).

The task statement uses the shorthand "permitAll outside the list," which the brief interprets as
`.permitAll()` DSL calls. If the intent is the broader "no handler reachable without
authentication outside the allowlist," the brief's scope is too narrow.

**Recommended brief amendment:** Explicitly scope AC2 to "no `.permitAll()` call outside
`SecurityChainsConfig`" and add a note that broader exposure vectors (e.g., `WebSecurityCustomizer`,
method-security `@PermitAll`) are out of scope for this task. Alternatively, expand AC2 to also
assert no `@PermitAll` / `WebSecurityCustomizer.ignoring()` exists outside the intended public
paths.

---

## Finding 4 · AC1 duplicates T07's existing coverage without a clear rationale

**Severity:** Low

**Evidence:** `PublicEndpointsTest.methodScopedContainsBothPasswordResetEndpoints` already guards
L11 for the password-reset endpoints. The brief proposes a second assertion in
`ArchitectureTest` for `/api-keys/token` rather than adding it to `PublicEndpointsTest`.

This is not wrong — the task statement explicitly says "Update `ArchitectureTest`" — but the
brief should acknowledge that this splits L11 content assertions across two test classes. A future
removal of `/api-keys/token` would fail CI regardless, but maintainers now have two places to look.

**Recommended brief amendment:** Add a one-line note that the AC1 assertion lives in
`ArchitectureTest` because the task's literal instruction names that class, and that
`PublicEndpointsTest` remains the home for T07's existing endpoint-content assertions.

---

## Finding 5 · The named test name differs from the brief's working title by case only

**Severity:** Low

**Evidence:** `package.md` §8 names the test `shouldEnforcePublicEndpointAllowlist`. The brief uses
`shouldEnforcePublicEndpointAllowlist` consistently, so this is not a conflict. No action needed.

---

## Non-Issues Confirmed

- **No production files modified:** `PublicEndpoints.java`, `SecurityChainsConfig.java`, and
  `PublicEndpointsTest.java` are all correctly listed as read-only.
- **No runtime request handling:** the task is build-time static analysis only.
- **L12 respected:** the new rules live inside the existing `ArchitectureTest` class; no new
  package or cross-module dependency is introduced.
- **ArchUnit version:** 1.3.0 is already on the test classpath.
- **Negative-proof plan:** the brief correctly notes that Phase 6/7 should temporarily reintroduce
  a violation to prove each check is not vacuous.

---

## Open Questions

1. Is the AC2 rule intended to forbid only `.permitAll()` invocations, or any use of the
   `authorizeHttpRequests` DSL outside `SecurityChainsConfig`? (See Finding 2.)
2. Which Spring Security class(es) should the ArchUnit dependency rule target, and has a Phase 5
   spike confirmed the rule catches a deliberately-introduced out-of-class `.permitAll()` call
   without false positives? (See Finding 1.)

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (human approval / brief fold) on approval.
