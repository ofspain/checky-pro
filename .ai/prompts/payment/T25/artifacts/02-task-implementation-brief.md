# payment · T25 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Make sure every ArchUnit guard T25 names is in place and gated by `mvn test`:
- L12: no cross-module entity imports
- L6: no floating-point money types
- L3: the KMS and chain-client ban
- L10: the public-endpoint allowlist

Add the one guard that is missing, the L10 ArchUnit `permitAll` ban, together with its canaries.

## Purpose

Three of the four rules were built incrementally in T05, T09 and T15, as each module appeared.
T25 is the hardening close-out. It confirms those three still hold against the finished
codebase, and it closes the L10 gap. Today a `permitAll()` call in any class *other than*
`ResourceServerConfig` would escape detection: `PublicEndpointsTest`'s source-text sweep reads
only that one file. Auth and notification both already ban this with ArchUnit.

## Scope

**In:**
- A new `shouldEnforcePublicEndpointAllowlist` rule in `ArchitectureTest`, with a real-code
  canary and a violation canary.
- One new violation fixture.
- Re-running every existing rule and canary unchanged.

**Out:**
- **Any production change.**
- **Changes to the three existing rules.** Phase 0/1 found them complete against L3, L6 and L12
  as written. Re-tuning them would be unrelated refactoring.
- **`agents.md:32`, `api → application → domain` layering.** It would mean restructuring every
  module's packages. It is not in the task statement, and it is disclosed as a platform-wide
  gap (Open Questions).
- **A `@PreAuthorize`-on-every-handler sweep.**
  - "The public-endpoint allowlist" means the set of *unauthenticated* paths. Authentication of
    everything else is already guaranteed by `anyRequest().authenticated()`.
  - Role checks are a separate guarantee, already present on every handler today.
  - Adding the sweep would go beyond the task statement. It is flagged for the Phase 4 gate,
    not silently dropped.
- Any change to `PublicEndpoints`, `PublicEndpointsTest` or `ResourceServerConfig`.

## Business Rules

- None scoped. Continuity only: R28 and R4 (Phase 1).

## Locked Decisions

- **L3, L6, L10, L12** (`design.md` §4a), plus L1 for continuity. The table in Phase 1 shows
  what each constrains.
- **Resolved directly** (low-risk structural choices, mirroring siblings):
  - **Rule shape.** Mirror `notification`'s `shouldEnforcePublicEndpointAllowlist`
    (`ArchitectureTest:117-121`) exactly:
    `noClasses().that().resideInAPackage("com.themistra.payment..").and().doNotBelongToAnyOf(ResourceServerConfig.class).should().callMethod(AuthorizeHttpRequestsConfigurer.AuthorizedUrl.class, "permitAll")`.
    The added `resideInAPackage` clause is this file's own convention, which every other
    `noClasses()` rule here uses (`:190`, `:207`).
  - **Enforcement.** A private static final rule plus a plain `@Test` canary, with no
    `@ArchTest` (the `ArchitectureTest:27-37` Surefire workaround).
  - **Violation canary.** Unlike notification, this rule gets one, for consistency with every
    other rule in this file. The fixture lives under `archtestfixtures`, like the other four.
  - **Named tests.** `shouldPreventCrossModuleEntityImports` stays satisfied by the rule field
    plus its `…IsCheckedDuringStandardBuild` canary. T09's Phase 12 accepted that mapping, and
    it is not renamed now.

## Dependencies

All already on the test classpath: ArchUnit 1.3.0, and Spring Security's
`AuthorizeHttpRequestsConfigurer`. No new dependency.

## Inputs

None at runtime. The input is the compiled `com.themistra.payment` main classes, plus the
fixture for the violation canary.

## Outputs

Pass or fail of `mvn test`.

## State Changes

None.

## Files to Create

- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RoguePermitAllUser.java`
  - It is a test-only class that genuinely calls `AuthorizedUrl.permitAll()`, for example via an
    `AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry`
    parameter.
  - It is never a Spring bean, and never sits on a component-scanned path that a slice test
    would load.

## Files to Modify

- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`: add the rule,
  two `@Test` canaries, and the imports they need.

## Files NOT to Modify

- Any file under `src/main`. In particular `ResourceServerConfig`, `PublicEndpoints`,
  `ApiExceptionHandler` and the controllers.
- `PublicEndpointsTest`.
- The existing rules, `FEATURE_MODULES`, and the four existing fixtures.
- `spec/`.

## Acceptance Criteria

- **AC1 (L12).** `shouldPreventCrossModuleEntityImports` and its 3 canaries pass unchanged.
- **AC2 (L6).** `shouldRejectFloatingPointMoneyTypesAtArchUnit` and its violation canary pass
  unchanged.
- **AC3 (L3).** `shouldBanKmsAndChainClientImports` and its 2 canaries pass unchanged.
- **AC4 (L10).** The new `shouldEnforcePublicEndpointAllowlist` passes on the real code, where
  the only caller is `ResourceServerConfig`. It fails with an `AssertionError` naming
  `RoguePermitAllUser` when run against that fixture.
- **AC5.** All T25 rules are gated by plain `@Test` methods that run under `mvn test`. No
  `@ArchTest` field is introduced. The full suite stays green, at 490 plus the new canaries.

## Required Tests

- **Named:** `shouldPreventCrossModuleEntityImports` (via its canary) and
  `shouldRejectFloatingPointMoneyTypesAtArchUnit`. Both exist already.
- **New:**
  - `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`
  - `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`
- **Continuity:** every existing `ArchitectureTest` canary.

## Constraints

- Thread-safety, transactions, performance and null-handling don't apply: this is a static
  analysis test.
- **Security:** the new rule strengthens L10. It must not weaken or alter the existing
  allowlist or `PublicEndpointsTest`.
- **Module boundaries:** the fixture lives under `archtestfixtures`, outside every feature
  module. The real-code canary imports with `DoNotIncludeTests`, so the fixture can never trip
  it.
- **Surefire:** no `@ArchTest` and no `@AnalyzeClasses`. Introducing either would silently
  disable the entire class.

## Open Questions

No blockers. Two items need the human's disposition at Phase 4. Neither blocks implementation.

1. **The `agents.md:32` layering rule is unenforced and unenforceable without restructuring.**
   - It is unenforced in every service.
   - Recommendation: log it as a platform-wide gap and keep it out of T25.
2. **A `@PreAuthorize` handler sweep, which is excluded above.**
   - Recommendation: exclude it, because it goes beyond "the public-endpoint allowlist".
   - The human may pull it in, as one more rule and fixture following the same pattern.
