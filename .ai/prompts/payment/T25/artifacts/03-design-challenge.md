# payment · T25 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/design.md` (L3/L6/L10/L12), and the existing `ArchitectureTest` and sibling `notification-service` precedent.

---

## Finding 1 — The new rule does not protect against `ResourceServerConfig` adding an unauthorized `permitAll()`

- **Issue:** The rule excludes `ResourceServerConfig` entirely, so any `permitAll()` call inside it is allowed. A malicious or mistaken edit inside `ResourceServerConfig` that adds a second `permitAll()` for a non-public path would not be caught by the new ArchUnit rule. The existing `PublicEndpointsTest` source sweep is supposed to guard this, but it is not part of T25 and the brief explicitly says not to modify it.
- **Severity:** Medium — the L10 guarantee is split across two unrelated test mechanisms; one of them is outside this task's scope.
- **Evidence:** TIB Locked Decisions (rule shape); `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfig.java` and `PublicEndpointsTest.java`.
- **Recommended brief amendment:** Either add a second ArchUnit rule that asserts `ResourceServerConfig` contains exactly one `permitAll()` call and that it is scoped to `PublicEndpoints.PATTERNS`, or explicitly record that `PublicEndpointsTest` remains the sole guard for the allowlist contents and that the new rule only covers stray callers.

---

## Finding 2 — The rule only bans `permitAll()`, not other ways to allow anonymous access

- **Issue:** Spring Security exposes other methods that disable authentication for a path, such as `anonymous()`, `rememberMe()`, or disabling the filter chain for a matcher. While `permitAll()` is the only one used in this codebase today, the rule's name ("enforce public-endpoint allowlist") implies broader coverage than it actually provides.
- **Severity:** Low — no current code path uses these alternatives.
- **Evidence:** TIB Scope; `agents.md` L10.
- **Recommended brief amendment:** Document that the rule covers only `permitAll()` calls, because that is the only mechanism the service uses. If the team wants broader coverage, list the additional methods (e.g., `anonymous()`) explicitly.

---

## Finding 3 — The violation fixture's compile-ability is unverified

- **Issue:** The fixture must call `AuthorizedUrl.permitAll()`. `AuthorizeHttpRequestsConfigurer.AuthorizedUrl` is a Spring Security inner class with access modifiers and generic parameters that may make it awkward to reference from a test fixture. The brief describes the fixture in prose but does not show a working shape.
- **Severity:** Low — likely solvable, but worth prototyping before Phase 4.
- **Evidence:** TIB Files to Create; `AuthorizeHttpRequestsConfigurer` class structure.
- **Recommended brief amendment:** Include a concrete fixture sketch, e.g.:
  ```java
  public class RoguePermitAllUser {
      public void rogue(org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer.AuthorizedUrl authorized) {
          authorized.permitAll();
      }
  }
  ```
  Prototype this in a throwaway compile before freezing, to confirm the exact import path and accessibility.

---

## Finding 4 — The rule depends on a Spring Security internal-ish class name

- **Issue:** The rule references `AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class` and the method name `permitAll`. If Spring Security is upgraded and either the class or method is renamed, the rule and fixture will fail to compile. The version is inherited from the parent POM and is not under this module's direct control.
- **Severity:** Low — version changes are intentional and would be caught at compile time.
- **Evidence:** TIB Dependencies; parent POM.
- **Recommended brief amendment:** Add a brief note that the rule is tightly coupled to the current Spring Security API surface; a future upgrade should include a mechanical update to this rule/fixture if the API changes.

---

## Finding 5 — No rule verifies that every authenticated handler has a role check

- **Issue:** The brief explicitly excludes a `@PreAuthorize` sweep, noting that role checks are already present. However, the gap is real: if a future handler is added without `@PreAuthorize`, it would still be authenticated (via `anyRequest().authenticated()`) but would not enforce `ROLE_MERCHANT`. The task statement is about the public-endpoint allowlist, not role enforcement, so this is a disclosed scope boundary rather than a silent omission.
- **Severity:** Low — out of scope for T25, but worth flagging at the Phase 4 gate.
- **Evidence:** TIB Scope; `agents.md` L10.
- **Recommended brief amendment:** Keep the exclusion, but add it to the Open Questions as a separately-tracked hardening item (e.g., "T25 closes the public-endpoint allowlist; a future task may add an ArchUnit `@PreAuthorize` sweep for role enforcement").

---

## Finding 6 — The rule name may over-promise

- **Issue:** `shouldEnforcePublicEndpointAllowlist` sounds like it verifies the allowlist contents, but it only verifies that no class other than `ResourceServerConfig` calls `permitAll()`. The actual allowlist contents are verified elsewhere (`PublicEndpointsTest`).
- **Severity:** Low — naming clarity.
- **Evidence:** TIB Acceptance Criteria AC4.
- **Recommended brief amendment:** Consider renaming the rule to `shouldBanStrayPermitAllCalls` or adding a Javadoc comment that clarifies it is the companion guard, not the allowlist validator.

---

## Finding 7 — `FEATURE_MODULES` may need updating if a future module adds an entity

- **Issue:** The brief says not to retune the existing cross-module entity rule, but T25 is the hardening close-out. If a new feature module (e.g., `export`) later adds an `@Entity`, it will not be in `FEATURE_MODULES` and the rule will fail fast. That is the intended behavior, but the close-out phase is a natural place to verify the list is still complete against the final codebase.
- **Severity:** Low — the fail-fast is deliberate and helpful.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 124-125.
- **Recommended brief amendment:** Add an AC or check that scans `src/main/java` for every top-level package containing an `@Entity` and confirms it appears in `FEATURE_MODULES`. This is a one-time verification, not a rule change.

---

## Summary

T25 is a small, well-scoped hardening task. The most important disposition at Phase 4 is Finding 1: decide whether `ResourceServerConfig`'s own `permitAll()` calls need an ArchUnit guard in addition to `PublicEndpointsTest`. Findings 2–7 are lower-priority clarity/scope notes that should be recorded before freezing.
