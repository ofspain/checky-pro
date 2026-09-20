<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T32 · Phase 8 — Independent Code Review

Consumed `artifacts/06-implementation-notes.md`, `artifacts/07-self-review.md`, and the updated
`ArchitectureTest.java`. No conflicts with `L11`, `L12`, or `agents.md` standing rules.

---

## Finding 1 — AC2 is only half-enforced: the rule does not require `SecurityChainsConfig`'s `.permitAll()` paths to come from `PublicEndpoints`

**Evidence:** `ArchitectureTest.java:115-121` uses `noClasses().that().doNotBelongToAnyOf(SecurityChainsConfig.class).should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")`. This proves only that `SecurityChainsConfig` may call `.permitAll()`, not *what paths* it calls `.permitAll()` on.

A future change inside `SecurityChainsConfig` such as
`auth.requestMatchers("/api/admin/health").permitAll()` would pass this rule even though the path
is not in `PublicEndpoints`. The task statement says "no new handler is permitAll outside the
list" — the current implementation stops at "who may call permitAll" and does not verify "the
paths are in the list."

**Recommendation:** Add a second check (ArchUnit custom condition or plain reflection) that
inspects `SecurityChainsConfig` and asserts every `.permitAll()` call is chained from
`requestMatchers(PublicEndpoints.PATTERNS)` or `requestMatchers(HttpMethod, PublicEndpoints.METHOD_SCOPED[i].pattern())`.
Alternatively, add an integration/HTTP test that scrapes the application context's
`SecurityFilterChain`(s) and enumerates all `permitAll` request matchers, asserting each maps to a
`PublicEndpoints` entry. At minimum, document this as a known residual gap if it is intentionally
out of scope.

**Confidence:** High.

---

## Finding 2 — The ArchUnit rules do not execute under Maven Surefire, so AC2 is not actually CI-enforced today

**Evidence:** `artifacts/06-implementation-notes.md` §3 reports `mvn test -Dtest='ArchitectureTest'`
shows `Tests run: 1` (the JUnit Jupiter engine) and `Tests run: 0` for the ArchUnit engine. The
negative-proof step confirms a deliberately-introduced stray `.permitAll()` call did **not** fail
the Maven build. The rule was only verified via a custom JUnit Platform Launcher invocation, not
via the project's standard build command.

**Recommendation:** Treat this as a release-blocker for the CI value proposition of T32. A
"CI-enforced check" that does not run in CI is a false sense of security. File a follow-up task
(or demand one before this task is considered complete) to fix the Surefire/ArchUnit engine
integration and add a smoke test that asserts `mvn test` actually executes the ArchUnit rules
(e.g., assert the pre-existing `repositories_are_never_public` rule fires, or add a deliberately
failing probe rule that is removed once execution is proven). Per the implementation notes, a
pre-existing rule bug (`only_the_account_module_may_touch_the_Account_entity`) is also hidden by
this same non-execution, confirming the blast radius.

**Confidence:** High.

---

## Finding 3 — Other public-exposure vectors are not covered

**Evidence:** The rule targets only `AuthorizedUrl.permitAll()`. Spring Security also exposes
endpoints without authentication via:

- `WebSecurityCustomizer.ignoring()` — bypasses the filter chain entirely.
- `@PermitAll` or `@PreAuthorize("permitAll()")` annotations on controller methods.
- A `SecurityFilterChain` that uses `anyRequest().permitAll()`.

None of these are checked. The task statement uses "permitAll" colloquially, but a public-endpoint
sweep whose intent is "no handler reachable without authentication outside the list" should
consider whether these vectors are in scope.

**Recommendation:** Either broaden the rule set to cover `WebSecurityCustomizer` and method-level
`@PermitAll` annotations, or explicitly document in the brief and the rule's `.because(...)` text
that the scope is intentionally limited to `.permitAll()` DSL calls inside Spring Security's
`authorizeHttpRequests` configuration.

**Confidence:** Medium.

---

## Finding 4 — The rule is brittle against legitimate refactoring of security configuration

**Evidence:** `doNotBelongToAnyOf(SecurityChainsConfig.class)` permits `.permitAll()` calls only
inside `SecurityChainsConfig` itself. If a future refactor extracts permit-list logic into a
helper class (e.g., `PublicSecurityRules` in the `token` module), the rule would fail even though
the helper is still module-local and still consumed only by `SecurityChainsConfig`.

**Recommendation:** Consider whether the rule should permit nested classes of
`SecurityChainsConfig` (already supported by ArchUnit's `belongToAnyOf` semantics, per the
self-review) and any class that is itself referenced only from `SecurityChainsConfig` — or accept
this brittleness and document that security wiring must remain in `SecurityChainsConfig`.

**Confidence:** Low.

---

## Non-Issues Confirmed

- **AC1 content assertion:** `apiKeysTokenExchangeIsInThePublicAllowlist` correctly uses
  `MethodScoped` record equality and matches T07's established pattern.
- **Module-boundary imports:** `ArchitectureTest.java`'s new imports of `PublicEndpoints` and
  `SecurityChainsConfig` do not trip existing rules because
  `@AnalyzeClasses(... importOptions = ImportOption.DoNotIncludeTests.class)` excludes test classes
  from the scan population.
- **Named-test naming:** `shouldEnforcePublicEndpointAllowlist` is intentionally camelCase to match
  `package.md` §8; this is justified.
- **No production code changed:** only `ArchitectureTest.java` is modified, consistent with the
  brief's scope.

---

## Open Questions

1. Is the residual gap in Finding 1 acceptable, or should T32 be amended to also verify that all
   `.permitAll()` paths are drawn from `PublicEndpoints`?
2. Will the Surefire/ArchUnit non-execution issue be fixed before T32 is merged, or will it be
   tracked as a separate follow-up? (Finding 2 argues it should block the task's CI-enforcement
   claim.)

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (human disposition) on approval.
