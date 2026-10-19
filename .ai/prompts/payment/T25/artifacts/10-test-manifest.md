# payment · T25 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md` (FROZEN)
- `artifacts/09-review-resolution.md`

T25 is a test-only task: its deliverable *is* tests. The tests were written in Phase 6 (the rule
plus its 2 canaries) and Phase 9 (the human-approved load-bearing test). This phase adds no
further test, because every acceptance criterion and named test below is already covered. No
production code is touched.

All tests are in `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`.
They are plain JUnit 5 with ArchUnit 1.3.0, and need no Spring context, `Clock` or
Testcontainers: this is static bytecode analysis, so the `agents.md:57` ArchUnit layer applies.

## Manifest

| Test | Verifies | Kind | Origin |
|---|---|---|---|
| `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` | **AC1 / L12**: the real code has no cross-module `@Entity` dependency. Satisfies the **named test** `shouldPreventCrossModuleEntityImports` (`package.md` §8) via the rule field plus this canary, as T09 Phase 12 accepted. | Real-code | T09 |
| `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` | **AC1**: the rule fails on `RogueInvoiceEntityReferencer` → `Invoice`. | Violation | T09 |
| `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` | **AC1** boundary: an `@Entity` outside `FEATURE_MODULES` fails loudly (this is the per-build guarantee that rejected Phase 3 F7). | Violation | T09 |
| `shouldRejectFloatingPointMoneyTypesAtArchUnit` | **AC2 / L6**: the **named test** (`package.md` §8). Real code has no `float`/`double`/`Float`/`Double`, including array forms. | Real-code | T05 |
| `shouldRejectFloatingPointMoneyTypesAtArchUnitActuallyFailsAgainstAGenuineViolation` | **AC2** boundaries: a field, an array field, a return type, a parameter and a constructor parameter each fail. | Violation | T05 |
| `shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` | **AC3 / L3**: no dependency on web3j, tron or KMS. | Real-code | T15 |
| `shouldBanKmsAndChainClientImportsActuallyFailsAgainstAGenuineViolation` | **AC3**: fails on `RogueKmsAndChainClientUser`. | Violation | T15 |
| `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` | **AC4 / L10**: no class other than `ResourceServerConfig` calls `AuthorizedUrl.permitAll()`. | Real-code | **T25** Phase 6 |
| `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation` | **AC4**: fails with a message naming `RoguePermitAllUser.openUp`. | Violation | **T25** Phase 6, tightened in Phase 9 |
| `shouldEnforcePublicEndpointAllowlistExclusionIsLoadBearing` | **AC4** non-vacuity: the analyzed classes contain `ResourceServerConfig`'s real `permitAll()` call, so the exclusion is what lets the real-code canary pass. | Real-code | **T25** Phase 9 (Kimi F5) |
| `shouldNotInlineProblemTypeUrisIsCheckedDuringStandardBuild`, `…ActuallyFailsAgainstAGenuineViolation` | Continuity only (T23/R29). Not a T25 AC; unchanged. | Both | T23 |

**Related guards outside this class (unchanged; the F1 guarantee split):**
`common/PublicEndpointsTest` covers four things.
- `patternsListExactlyTheThreeDeclaredPaths`: the allowlist contents.
- `resourceServerConfigContainsNoPermitAllOutsidePublicEndpoints`: the single scoped
  `permitAll()` call.
- The behavioural checks for public paths: they are not blocked.
- The behavioural checks for sensitive paths: they return 401.

## AC5 (enforcement)

- The class has no `@ArchTest` field and no `@AnalyzeClasses`.
- All 12 tests appear by name in `target/surefire-reports/TEST-com.themistra.payment.ArchitectureTest.xml`.
  That shows they really run under `mvn test`, despite the Surefire 3.5.3 `@ArchTest` bug.
- The full suite is green: **493/493** (490 baseline plus 3 T25 tests).

## Coverage of state transitions and boundaries

- **State transitions:** none apply, because T25 touches no state machine.
- **Boundaries:** the boundary cases for each rule are covered by its violation fixtures (the
  rows above).
- **Mutation evidence:** every T25 assertion has been seen to fail under a targeted edit (Phase 6
  and Phase 9).
