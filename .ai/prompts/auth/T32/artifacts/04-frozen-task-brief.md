<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# auth · T32 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md` (Kimi,
5 findings). All verified against actual source (Spring Security 7.1.0's resolved
`AuthorizeHttpRequestsConfigurer$AuthorizedUrl` bytecode via `javap`, and ArchUnit 1.3.0's own
`ClassesShould`/`ArchConditions` API via `javap`) before disposition. femi decided the two findings
with genuine trade-off/technical weight via human gate; the remaining three are mechanical
amendments folded in directly.

## Findings disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | AC2's originally-proposed "dependency rule on `AuthorizedUrl`" is under-specified/fragile | High | **Resolved, femi's gate decision.** Verified `AuthorizedUrl` exposes `permitAll()`, `authenticated()`, `hasRole()`, etc. — confirmed ArchUnit 1.3.0 has `ClassesShould.callMethod(Class, String, Class...)` / `ArchConditions.callMethodWhere(...)`, a method-call-level (not class-dependency-level) predicate. Rule reformulated to target the exact method call. |
| 2 | A dependency rule on `AuthorizedUrl` would also forbid legitimate `.authenticated()` calls | High | **Resolved, femi's gate decision (same fix as #1).** `callMethod(AuthorizedUrl.class, "permitAll")` targets only the `permitAll()` invocation itself, not the class dependency edge — `.authenticated()`/`.hasRole()` calls elsewhere (none exist today, but the rule wouldn't touch them if they did) are unaffected. |
| 3 | The rule only covers `.permitAll()`, not `WebSecurityCustomizer.ignoring()`/`@PermitAll`/other exposure vectors | Medium | **Resolved, femi's gate decision.** Scope stays narrow: `.permitAll()` only, matching the task statement's literal wording. Named explicitly as out of scope below, not silently ignored. Neither mechanism is used anywhere in this codebase today (confirmed via `grep`). |
| 4 | AC1's `/api-keys/token` assertion in `ArchitectureTest` duplicates T07's `PublicEndpointsTest` pattern without stating why | Low | **Accepted, folded in.** One-line rationale added to Scope below. |
| 5 | Named test name matches `package.md` §8 exactly | Low | **Confirmed, no action.** Not a conflict. |

## Task

Update `ArchitectureTest` to (1) assert `/api-keys/token` is present in the public allowlist, and
(2) assert no class outside `SecurityChainsConfig` calls `.permitAll()`.

## Purpose

Unchanged from Phase 2: make L11 CI-enforced rather than convention-only.

## Scope

**In:** two additions to `ArchitectureTest.java` — a content-presence check (AC1) and a
method-call-site ArchUnit rule (AC2, the task's own named test).

**Out:** `WebSecurityCustomizer.ignoring()`, `@PermitAll`/method-security annotations, or any other
public-exposure mechanism — none exist in this codebase today; explicitly out of scope per the
task's literal "permitAll outside the list" wording, not silently narrowed. A future task
introducing any of these would need its own dedicated ArchUnit rule. AC1's assertion lives in
`ArchitectureTest` (not merged into `PublicEndpointsTest`) specifically because the task statement
names that class; `PublicEndpointsTest` remains the home for T07's own password-reset assertions,
unchanged.

## Business Rules

No R-numbered requirement applies (process/verification task).

## Locked Decisions

- **L11.** Public endpoint discipline — this task's subject, now CI-enforced for both halves
  (content + no-stray-permitAll).
- **L12.** Module boundaries — respected; no new package/module, no new cross-module dependency.

## Dependencies

`com.themistra.auth.common.PublicEndpoints`, `com.themistra.auth.token.SecurityChainsConfig`,
ArchUnit 1.3.0 (`archunit-junit5`, already resolved), Spring Security 7.1.0's
`org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer$AuthorizedUrl`
(the `permitAll()` method's declaring class, verified via `javap` against the actually-resolved
jar — not assumed from an older Spring Security version's package layout).

## Inputs

None (static analysis only).

## Outputs

A CI-enforced test failure if: `/api-keys/token` is removed from
`PublicEndpoints.METHOD_SCOPED`, or any class other than `SecurityChainsConfig` calls
`AuthorizedUrl.permitAll()`.

## State Changes

None.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`:
  1. A plain `@Test` method asserting `PublicEndpoints.METHOD_SCOPED` contains
     `new MethodScoped(HttpMethod.POST, "/api-keys/token")` (AC1).
  2. `@ArchTest static final ArchRule shouldEnforcePublicEndpointAllowlist` (the named test, AC2):
     `noClasses().that().areNotAssignableFrom(SecurityChainsConfig.class).should().callMethod(
     AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")` (exact predicate form to be
     finalized at Phase 5/6 against ArchUnit's actual fluent-API signature — the mechanism, not the
     precise builder-chain syntax, is what's frozen here).

## Files NOT to Modify

- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java`.
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java`.
- `services/auth/src/test/java/com/themistra/auth/common/PublicEndpointsTest.java`.
- Any `spec/` file.

## Acceptance Criteria

- **AC1.** A test fails if `/api-keys/token` is ever removed from `PublicEndpoints.METHOD_SCOPED`.
- **AC2.** A test fails if any class other than `SecurityChainsConfig` calls
  `AuthorizedUrl.permitAll()`. Explicitly scoped to this one method — `WebSecurityCustomizer`/
  `@PermitAll`/other exposure vectors are out of scope (Finding 3 disposition).

## Required Tests

- `shouldEnforcePublicEndpointAllowlist` (named, `package.md` §8) — AC2.
- A second test/method for AC1 (exact name decided at Phase 5).
- Negative-proof step at Phase 6/7: temporarily reintroduce a violation locally (add a throwaway
  `.permitAll()` call in another class, or remove the `/api-keys/token` entry) to confirm each
  check actually fails, then revert before commit — neither check may be vacuously true.

## Constraints

- **Performance:** none (build/test-time only).
- **Security:** this rule IS the control being hardened.
- **Thread-safety / Transaction:** N/A.
- **Module boundaries (L12):** respected, no new package.
- **Null handling:** N/A.

## Open Questions

No blockers. Both Phase 3 open questions resolved above (Finding 1/2's disposition answers both:
the rule targets `.permitAll()` specifically via `callMethod`, verified against actual ArchUnit
1.3.0 API and the actually-resolved Spring Security 7.1.0 class).

---

**Phase 4 complete — brief FROZEN.** Proceed to Phase 5 (Implementation Plan).
