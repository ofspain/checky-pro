<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# auth · T32 · Phase 2 — Task Implementation Brief

## Task

Update `ArchitectureTest` to (1) assert `/api-keys/token` is present in the unauthenticated public
allowlist, and (2) assert no handler is `permitAll`-reachable outside that allowlist.

## Purpose

Convert L11 ("public endpoint discipline") from a convention enforced only by code review into a
permanent, CI-enforced check — closing the exact class of defect the reference project shipped
(`PublicEndpoints.java`'s own Javadoc: a "testing only" whitelist that exposed role administration).

## Scope

**In:** a new test/rule (or pair of them) in `ArchitectureTest.java` covering both halves above.

**Out:** any change to `PublicEndpoints.java`'s actual contents (the `/api-keys/token` entry
already exists, added at T25); any change to `SecurityChainsConfig.java`'s security wiring; any
new HTTP-level/Testcontainers integration test (not required by the task's own wording or by any
named test/AC in `package.md`).

## Business Rules

No R-numbered requirement applies (process/verification task, per `package.md` §7).

## Locked Decisions

- **L11.** Public endpoint discipline — the exhaustive unauthenticated-path list, any new public
  path must be added to `PublicEndpoints.java`. This task's subject.
- **L12.** Module boundaries — enforced by `ArchitectureTest`; this task's own new rule(s) must
  themselves respect it (no new cross-module dependency introduced).

## Dependencies

`com.themistra.auth.common.PublicEndpoints` (`PATTERNS`/`METHOD_SCOPED`),
`com.themistra.auth.token.SecurityChainsConfig` (the sole `.permitAll()` caller), ArchUnit 1.3.0
(`archunit-junit5`, already a test dependency — confirmed on the classpath, no version change
needed).

## Inputs

None (compile-time/test-time static analysis + a data-content assertion; no runtime request
handling).

## Outputs

A CI-enforced test failure if either: `/api-keys/token` is removed from
`PublicEndpoints.METHOD_SCOPED`, or any class outside `SecurityChainsConfig` introduces a new
`permitAll()`-reachable path.

## State Changes

None.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — add:
  1. A plain `@Test` method (not `@ArchTest`) asserting
     `PublicEndpoints.METHOD_SCOPED.contains(new MethodScoped(HttpMethod.POST, "/api-keys/token"))`
     — satisfies the task's literal instruction to put this assertion in `ArchitectureTest`
     itself, mirroring T07's own `PublicEndpointsTest.methodScopedContainsBothPasswordResetEndpoints`
     pattern for a different entry rather than duplicating that file.
  2. A new `@ArchTest static final ArchRule shouldEnforcePublicEndpointAllowlist` (the task's own
     named test) constraining which classes may depend on the Spring Security types that expose
     `.permitAll()` (`AuthorizedUrl` / the `authorizeHttpRequests` registry types) to
     `SecurityChainsConfig` only — the same class-dependency mechanism the existing
     `only_token_module_references_public_endpoints` rule already uses for `PublicEndpoints`
     itself, applied one layer further out. **Tentative**: the exact Spring Security type(s) to
     name and whether ArchUnit 1.3.0 can express this cleanly needs verification in Phase 5 before
     implementation; flagged for Kimi's Phase 3 challenge rather than treated as settled.

## Files NOT to Modify

- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` (already correct).
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java`.
- `services/auth/src/test/java/com/themistra/auth/common/PublicEndpointsTest.java` (T07's existing
  test stays as-is, not merged or duplicated).
- Any `spec/` file.

## Acceptance Criteria

- **AC1.** A test fails if `/api-keys/token` is ever removed from `PublicEndpoints.METHOD_SCOPED`.
- **AC2.** A test fails if any class outside `SecurityChainsConfig` becomes able to declare a new
  `permitAll()`-reachable path.

## Required Tests

- `shouldEnforcePublicEndpointAllowlist` (named, `package.md` §8) — the structural (AC2) rule.
- A second, purpose-named test/method for AC1's content assertion (exact name TBD at Phase 5).
- A negative-proof check (not a separate shipped test, but a Phase 6/7 verification step): confirm
  each new check actually fails when the condition it guards against is temporarily and locally
  reintroduced, so neither is vacuously true — reverted before commit, per standard "prove the
  test can fail" practice for this kind of regression-lock test.

## Constraints

- **Performance:** none (test/build-time only, no runtime code path).
- **Security:** this IS the security control being hardened — a mis-scoped rule that's too
  permissive would silently fail to catch the exact class of regression it exists for.
- **Thread-safety:** N/A.
- **Transaction:** N/A.
- **Module boundaries (L12):** the new rule(s) live in the existing `ArchitectureTest` class, no
  new package/module introduced.
- **Null handling:** N/A.

## Open Questions

No blockers. One item explicitly deferred to Phase 3/5 rather than treated as settled here: whether
ArchUnit 1.3.0 can cleanly express "only class X may depend on Spring Security type Y" for the
specific `AuthorizedUrl`-family type(s) involved, or whether a different formulation (e.g.
constraining `.permitAll()`'s enclosing method/class via a different ArchUnit predicate) is needed.

---

**Phase 2 complete — Task Implementation Brief written.** Proceed to Phase 3 (Kimi design
challenge) on approval.
