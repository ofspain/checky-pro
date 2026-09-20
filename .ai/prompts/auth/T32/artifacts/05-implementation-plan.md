<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T32 · Phase 5 — Implementation Plan

No production code, no new files — this task is entirely test-side, extending one existing file.
Both ArchUnit constructs verified against the actually-resolved jars (ArchUnit 1.3.0,
Spring Security 7.1.0) before committing to this plan.

## Files to create

None.

## Files to modify

- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — add one plain `@Test`
  method and one `@ArchTest` rule field (below). New imports needed: `HttpMethod`
  (`org.springframework.http.HttpMethod`), `PublicEndpoints`
  (`com.themistra.auth.common.PublicEndpoints`), `AuthorizeHttpRequestsConfigurer`
  (`org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer`),
  `SecurityChainsConfig` (`com.themistra.auth.token.SecurityChainsConfig`), plus
  `org.junit.jupiter.api.Test` and `static org.assertj.core.api.Assertions.assertThat` (this file
  currently has neither, since every existing member is an `@ArchTest` field).

## Public methods (signatures)

None (test-only; no production API surface changes).

## Private methods

None planned — both additions are self-contained (a single assertion statement; a single
`ArchRule` expression), matching every existing `@ArchTest` field's own shape in this file (no
rule in `ArchitectureTest.java` currently factors out a private helper).

## New test members (exact plan)

1. **`apiKeysTokenExchangeIsInThePublicAllowlist`** (plain `@Test`, AC1):
   ```
   @Test
   void apiKeysTokenExchangeIsInThePublicAllowlist() {
       assertThat(PublicEndpoints.METHOD_SCOPED)
               .contains(new PublicEndpoints.MethodScoped(HttpMethod.POST, "/api-keys/token"));
   }
   ```
   Mirrors `PublicEndpointsTest.methodScopedContainsBothPasswordResetEndpoints`'s exact shape
   (T07 precedent), placed here instead because the task statement names `ArchitectureTest`
   specifically (Phase 4 Scope).

2. **`shouldEnforcePublicEndpointAllowlist`** (`@ArchTest static final ArchRule`, AC2, the named
   test):
   ```
   @ArchTest
   static final ArchRule shouldEnforcePublicEndpointAllowlist = noClasses()
           .that().doNotBelongToAnyOf(SecurityChainsConfig.class)
           .should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")
           .because("L11: SecurityChainsConfig is the only class that may declare an "
                   + "unauthenticated path, and it must do so only via PublicEndpoints — a stray "
                   + "permitAll() anywhere else would silently create an undocumented public "
                   + "endpoint (gap-analysis §2's 'testing only' whitelist lesson)");
   ```
   `doNotBelongToAnyOf(Class<?>...)` and `callMethod(Class<?>, String, Class<?>...)` both confirmed
   present on ArchUnit 1.3.0's `ClassesThat`/`ClassesShould` via `javap` in Phase 3/4.
   `AuthorizeHttpRequestsConfigurer.AuthorizedUrl` confirmed `public`, `permitAll()` confirmed
   no-arg, both via `javap` against the resolved Spring Security 7.1.0 jar.

## Entities used

None.

## Repositories used

None.

## Services used

None.

## Unit / integration tests required

- The two new members above (unit-level, no Spring context — `ArchitectureTest` already runs via
  ArchUnit's own `@AnalyzeClasses`/JUnit 5 integration, not `@SpringBootTest`).
- **Negative-proof step (performed, not shipped):** before finalizing, temporarily add a throwaway
  `.permitAll()` call in an unrelated class (e.g. a scratch method in a test-only file, or
  reusing `SecurityBeansConfig` briefly) and confirm `shouldEnforcePublicEndpointAllowlist` fails;
  separately, temporarily comment out the `/api-keys/token` entry in `PublicEndpoints` and confirm
  `apiKeysTokenExchangeIsInThePublicAllowlist` fails. Revert both before running the real suite.
  This directly satisfies the frozen brief's Required Tests item and Kimi's own "Non-Issues
  Confirmed" callout that this step was correctly planned.
- Full existing `ArchitectureTest` suite re-run afterward to confirm the other 7 pre-existing rules
  are unaffected (no shared state, but confirming no accidental interaction, e.g. the new rule's
  `noClasses().that().doNotBelongToAnyOf(...)` scope not accidentally overlapping an existing rule's
  target set).

## Execution order

1. Add the two new imports-requiring members to `ArchitectureTest.java`.
2. Run the negative-proof step for `shouldEnforcePublicEndpointAllowlist` (introduce a violation,
   confirm failure, revert).
3. Run the negative-proof step for `apiKeysTokenExchangeIsInThePublicAllowlist` (comment out the
   entry, confirm failure, revert).
4. Run the full `ArchitectureTest` class clean and confirm all members pass (the 7 pre-existing
   rules + the 2 new ones).
5. Full `services/auth` compile (`mvn clean test-compile`) to confirm no import/compile regressions
   elsewhere (this file is imported by nothing else, but confirms the new imports themselves
   resolve correctly against the actual classpath, not just the `javap`-inspected jars).

No schema/migration/dao/service/api layers exist for this task — execution order is
test-file-only, front-loaded by "prove it can fail" before "prove it currently passes," per the
frozen brief's own required negative-proof step.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
