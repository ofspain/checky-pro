<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T32 · Phase 7 — Self Review

Reviewed the one-file diff (`ArchitectureTest.java`) against the frozen brief, `agents.md`, and the
dimensions this phase names. Money types, transaction boundaries, thread-safety, idempotency, and
enumeration-safety/secret-handling don't apply to a static-analysis/content-assertion test file —
noted as N/A rather than silently skipped.

## Finding 1 — Naming convention inconsistency (deliberate, not a defect)

**Severity:** Low

**Evidence:** `ArchitectureTest.java:115` — `shouldEnforcePublicEndpointAllowlist` uses camelCase,
while every other `@ArchTest` field in this file uses snake_case
(`only_the_account_module_may_touch_the_Account_entity`,
`only_token_module_references_public_endpoints`, etc.).

**Recommendation:** No change. This is intentional, not an oversight — the field name must match
`package.md` §8's exact named-test string (`shouldEnforcePublicEndpointAllowlist`) for the pipeline's
own traceability convention, which takes precedence over this file's internal snake_case habit.
Documented here so a future reader doesn't "fix" it into snake_case and silently break the
named-test/spec linkage.

## Correctness — verified, not merely inspected

- `shouldEnforcePublicEndpointAllowlist`'s predicate (`doNotBelongToAnyOf(SecurityChainsConfig.class)`
  targeting `callMethod(AuthorizedUrl.class, "permitAll")`) was empirically proven in both
  directions during Phase 6 (fires on a deliberately-introduced violation outside
  `SecurityChainsConfig`; passes with the two legitimate lambda-bodied calls already inside it) —
  not just reasoned about from the API surface.
- Confirmed `@AnalyzeClasses(... importOptions = ImportOption.DoNotIncludeTests.class)` excludes
  `ArchitectureTest.java` itself (and every other test class) from the classes checked *against*
  the rules — so this file's own new imports of `SecurityChainsConfig`/`PublicEndpoints` do not
  themselves trip `only_token_module_references_public_endpoints`, the same way
  `PublicEndpointsTest.java`'s existing import of `PublicEndpoints` never has. No module-boundary
  regression introduced.
- `apiKeysTokenExchangeIsInThePublicAllowlist` relies on `PublicEndpoints.MethodScoped`'s
  record-generated `equals`/`hashCode` for the `.contains(...)` structural match — verified passing,
  not assumed.

## Boundary conditions considered, none found lacking

- A hypothetical future nested/helper class inside `SecurityChainsConfig` would still be treated as
  "belonging to" it by ArchUnit's `belongToAnyOf` semantics (groups nested classes with their
  top-level enclosing class) — correct behavior for this rule's intent, not a loophole.
- The rule's target (`AuthorizeHttpRequestsConfigurer.AuthorizedUrl`, a Spring Security class)
  lives outside the `@AnalyzeClasses(packages = "com.themistra.auth")` scan scope; confirmed this
  is fine — ArchUnit resolves call targets from the full classpath regardless of the scan package,
  it's only the *source* classes being checked that are scoped. Already proven working via the
  Phase 6 negative-proof run.

## Out of scope, already flagged elsewhere (not re-litigated here)

The Phase 6 finding that ArchUnit's engine doesn't execute under `mvn test`/Surefire in this
environment, and the pre-existing `only_the_account_module_may_touch_the_Account_entity` rule bug
it exposed, are both already logged in `artifacts/06-implementation-notes.md` and memory. Neither
is a defect in this task's own diff — restating them here would be noise, not a new finding.

## No findings on

Null-safety (no nullable fields introduced), thread-safety (build-time static analysis only, no
shared mutable state), transaction boundaries (N/A), module boundaries (checked above, clean),
readability (straightforward, matches file's existing shape aside from Finding 1's justified
naming exception), complexity (two short, single-expression members, no branching).

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi independent review) on approval.
