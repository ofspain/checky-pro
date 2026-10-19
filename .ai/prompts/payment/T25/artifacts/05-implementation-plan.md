# payment · T25 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

This task is test-only, so there is no schema, DAO, service or API layer. Every file below traces
to the frozen brief's Files section.

## Files to create

- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RoguePermitAllUser.java`
  - It is a plain public class, with no annotations, and not a bean.
  - It has one package-private method:
    `void openUp(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth)`,
    whose body is `auth.requestMatchers("/rogue/**").permitAll();`.
  - The shape was prototype-compiled at Phase 4 (F3). Its call target is
    `AuthorizeHttpRequestsConfigurer$AuthorizedUrl.permitAll`.
  - Its Javadoc follows the sibling fixtures' style: the purpose is to prove that the L10 rule
    can fail.

## Files to modify

- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
  - **Imports to add:**
    - `com.themistra.payment.archtestfixtures.RoguePermitAllUser`
    - `com.themistra.payment.common.ResourceServerConfig`
    - `org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer`
  - **New field**, placed after `shouldNotInlineProblemTypeUris` and before `analyzedClasses()`:
    `private static final ArchRule shouldEnforcePublicEndpointAllowlist`. Its shape is exactly
    the frozen rule, with `.because("L10: ...")`. Its Javadoc covers:
    - F6: it is the stray-caller guard, and `PublicEndpointsTest` owns the contents and
      in-config checks
    - F2: it covers `permitAll()` only
    - F4: it is coupled to the Spring Security API
  - **New tests:**
    - `@Test void shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild()` calls
      `rule.check(analyzedClasses())`. It goes next to the other `…IsCheckedDuringStandardBuild`
      canaries.
    - `@Test void shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation()`
      builds `new ClassFileImporter().importClasses(RoguePermitAllUser.class)` and asserts
      `assertThatThrownBy(...).isInstanceOf(AssertionError.class).hasMessageContaining("RoguePermitAllUser")`.
      It goes at the end of the class, after the other violation canaries.

## Public methods

None. The fixture's method is package-private, and the tests are package-private JUnit methods.

## Private methods

None new. The tests reuse the existing `analyzedClasses()`.

## Entities, repositories, services

None.

## Tests required

- **The 2 new canaries above (AC4).**
- **The existing canaries, unchanged:**
  - `shouldRejectFloatingPointMoneyTypesAtArchUnit` (+ violation canary): AC2
  - `shouldPreventCrossModuleEntityImports…` (×3): AC1
  - `shouldBanKmsAndChainClientImports…` (×2): AC3
  - `shouldNotInlineProblemTypeUris…` (×2): continuity
- **Full suite: expected 492/492 (AC5).**

## Execution order

1. Create the fixture, so that it compiles first.
2. Add the imports, the rule and its Javadoc to `ArchitectureTest`.
3. Add the two canaries.
4. Run `mvn -o test -Dtest=ArchitectureTest`. Expect 13 tests: 11 existing plus 2.
5. Mutation check (temporary, reverted):
   - Remove `doNotBelongToAnyOf(ResourceServerConfig.class)`, so that the real-code canary
     fails naming `ResourceServerConfig`.
   - Then restore it.
6. Run the full `mvn -o test`. Expect 492/492.
