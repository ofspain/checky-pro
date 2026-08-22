<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T35 · Phase 10 — Test Generation

Test-only task convention (same as T27/T32/T33): the task's entire deliverable is test code,
already written and resolved across Phases 6 and 9 by extending `ArchitectureTest.java`. No
production code exists to test separately. This phase is purely the manifest.

## `ArchitectureTest.java` (extended — 2 new `@ArchTest` rules + 2 new canaries + 3 shared helpers)

| Test | Type | Verifies |
|---|---|---|
| `shouldPreventCrossModuleEntityImports` (**named test, `package.md` §8**) | `@ArchTest` (ArchUnit) | AC1 — no `@Entity` class is depended on (field/parameter/etc., dependency-based) by a class outside its own feature module; generalized from the original Account-only rule (Phase 4 D1b) and further hardened to fail-fast on an entity outside every known module (Phase 9). |
| `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` | Plain `@Test` (JUnit Jupiter) | AC3 — makes the named rule actually gate `mvn test`, per T32's established canary pattern (ArchUnit's own engine doesn't execute under this project's Surefire setup). |
| `controllersDependOnlyOnTheirOwnModuleServices` | `@ArchTest` (ArchUnit) | AC2 — no `@RestController` depends on another module's `@Service`, except the two named, allowlisted exceptions and any `common`-housed service; fails fast on a controller outside every known module (Phase 9). |
| `controllersDependOnlyOnTheirOwnModuleServicesIsCheckedDuringStandardBuild` | Plain `@Test` (JUnit Jupiter) | AC3 — same canary pattern for the second rule. |

**Shared helpers** (not directly tested, exercised by the four members above): `FEATURE_MODULES`,
`COMMON_PACKAGE`, `ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES` (now `Class.getName()`-keyed,
Phase 9), `featureModuleOf(JavaClass)`, `isInCommonModule(JavaClass)`, the two `ArchCondition`
implementations, and the lazily-cached `analyzedClasses()` (Phase 9, shared across all three
canaries in this file now, not just this task's two).

## Boundary/negative-proof coverage (performed across Phases 6 and 9, not separately shipped as tests)

- **Phase 6, D1b, initial (caught a real implementation bug)**: a scratch class with unused
  `Account`/`VerificationToken`-typed fields incorrectly *passed* an access-based first
  implementation — exposed that `getAccessesToSelf()` misses declared-but-unused fields. Fixed by
  switching to `getDirectDependenciesToSelf()`; re-run correctly failed on both entities.
- **Phase 6, D1b, regression**: reverted, confirmed `account.dto`/`account.event` remain exempt
  under the corrected mechanism.
- **Phase 6, D2, forward**: a scratch `LockoutService` field on `AdminRoleController` (not
  allowlisted) correctly failed; reverted, confirmed both real allowlisted exceptions still pass.
- **Phase 9, entity fail-fast**: a scratch `@Entity` class placed in `common` correctly failed
  `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` with a clear "outside every
  listed module" message; reverted.
- **Phase 9, controller fail-fast**: a scratch `@RestController` placed in `common` correctly
  failed the second canary the same way; reverted.
- **Phase 9, common-services-allowed**: a scratch `@Service` in `common`, depended on by
  `AdminRoleController`, correctly *passed* (not flagged) — proving the `common` exception works
  in the direction that matters (a legitimate dependency isn't wrongly blocked), not just that
  violations are caught; reverted.

Six total negative-proof runs across this task, the same discipline as every prior task's contract/
rule work this session, and the one that directly caught a real, ship-blocking implementation bug
before it ever reached review.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ArchitectureTest'` — **4/4 pass** (the two content/canary
  tests from T32 plus this task's two new canaries, all via the JUnit Jupiter engine; the ArchUnit
  engine's own separate invocation still reports 0 tests, the known, separately-tracked
  Surefire/ArchUnit gap — no longer a blind spot for either of this task's own rules specifically,
  since both canaries close it exactly as T32's did for its own rule).

The named `package.md` §8 test (`shouldPreventCrossModuleEntityImports`) is fully written, verified
correct via six independent negative-proof runs (including catching and fixing a real defect), and
— as of Phase 9's canary — actually enforced by this project's standard `mvn test` command.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi test review) on approval.
