# payment · T25 · Phase 6 — Implementation Notes

Consumes:
- `artifacts/04-frozen-task-brief.md` (FROZEN)
- `artifacts/05-implementation-plan.md`

This task is test-only by nature (frozen brief §Scope). Its code is test code. **No production
file changed.**

## What changed

| File | Change | Plan / AC |
|---|---|---|
| `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RoguePermitAllUser.java` (new) | Unannotated class. `openUp(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry)` calls `requestMatchers("/rogue/**").permitAll()`. Uses the exact shape prototyped at Phase 4 (F3). | Plan §create · AC4 |
| `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` | **Imports:** `RoguePermitAllUser`, `ResourceServerConfig`, `AuthorizeHttpRequestsConfigurer`. | Plan §modify |
| | **New rule** `shouldEnforcePublicEndpointAllowlist`: `noClasses().that().resideInAPackage(ANALYZED_PACKAGE + "..").and().doNotBelongToAnyOf(ResourceServerConfig.class).should().callMethod(AuthorizedUrl.class, "permitAll").because("L10: …")`. It is the frozen shape exactly. | AC4 |
| | **Rule Javadoc** covers four points: the stray-caller-only scope plus the `PublicEndpointsTest` guarantee split (F6/F1); `permitAll()`-only coverage (F2); and the Spring Security API coupling (F4). | AC5 |
| | **New canary** `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`, run against the real code. | AC4 |
| | **New canary** `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`. It asserts an `AssertionError` containing `RoguePermitAllUser` and `permitAll`. | AC4 |

The three existing rules, `FEATURE_MODULES` and the four existing fixtures are untouched; the
diff contains insertions only. This covers AC1, AC2 and AC3.

## Verification

- **`mvn -o test -Dtest=ArchitectureTest`: 11 run, 0 failures.** Both new canaries appear by name
  in the surefire XML, which shows they really execute: there is no `@ArchTest` and no
  `@AnalyzeClasses` (AC5).
- **Mutation check: temporary edit, restored from a scratchpad copy, `git diff` re-checked.**
  - I removed `.and().doNotBelongToAnyOf(ResourceServerConfig.class)`.
  - The real-code canary then **failed**, naming
    `ResourceServerConfig.securityFilterChain(...) calls method AuthorizedUrl.permitAll() in (ResourceServerConfig.java:57)`.
  - This proves the rule sees the production call. The call sits inside a lambda, which compiles
    to a synthetic method in `ResourceServerConfig`. The exclusion is what lets it pass, rather
    than the rule being vacuous.
- **Full `mvn -o test`: 492/492, BUILD SUCCESS.** The baseline was 490, plus the 2 new canaries
  (AC5).

## Deviations

1. **The plan's per-class count was wrong. This is a documentation error only, not a code
   deviation.**
   - Phase 5 said "13 tests: 11 existing plus 2". I had derived 11 from `grep -c "@Test"`, which
     also counted Javadoc mentions of `@Test`.
   - The real numbers are 9 existing plus 2 new, so 11.
   - The full-suite expectation, 492, was correct and is met.

There is no other deviation. No file outside the frozen Files section was touched.
