<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T35 · Phase 6 — Implementation Notes

## What changed

One file: `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`. Replaced
`only_the_account_module_may_touch_the_Account_entity` with the generalized, named
`shouldPreventCrossModuleEntityImports` (D1b); added `controllersDependOnlyOnTheirOwnModuleServices`
(D2); added `FEATURE_MODULES`/`featureModuleOf`/`ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES`
shared helpers; added two canary tests matching T32's established pattern. No production code
touched — confirmed via `git status`.

## Mapping to acceptance criteria

- **AC1** ← `shouldPreventCrossModuleEntityImports`, negative-proofed for both `Account` and
  `VerificationToken`, confirmed `account.dto`/`account.event` remain exempt.
- **AC2** ← `controllersDependOnlyOnTheirOwnModuleServices`, negative-proofed for a new
  un-allowlisted dependency, confirmed both real named exceptions still pass, confirmed
  `@RestControllerAdvice` classes are structurally excluded.
- **AC3** ← both rules have their own canary test, matching T32's pattern exactly.

## A real implementation bug found and fixed during this phase's own negative-proof

Phase 5 planned `onlyBeAccessedFromTheSameFeatureModule()` using `entityClass.getAccessesToSelf()`
(ArchUnit's *access*-tracking API — actual bytecode-level get/put/invoke instructions). The first
negative-proof run (a scratch class in `common` with an `Account`-typed field, never read or
written) **passed when it should have failed** — `getAccessesToSelf()` only sees real field
reads/writes/method calls, not a mere field-of-that-type declaration with no such access. Fixed by
switching to `entityClass.getDirectDependenciesToSelf()` (ArchUnit's *dependency*-tracking API,
covering field types, parameter types, etc. — the same category of API the original,
pre-T35 rule and this task's own D2 rule already correctly used via `dependOnClassesThat()`/
`getDirectDependenciesFromSelf()`). Re-ran the identical negative-proof after the fix: both
`Account` and `VerificationToken` violations were caught correctly. This is exactly the class of
defect this pipeline's own negative-proof discipline exists to catch — the rule looked correct on
inspection (compiled clean, matched the Phase 5 plan) and would have shipped silently broken
without actually running a real violation through it.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ArchitectureTest'` — **4/4 pass** (2 pre-existing plain
  `@Test` methods from T32 + 2 new canaries, all via the JUnit Jupiter engine — the ArchUnit
  engine's own separate invocation still reports 0 tests, the same known, separately-tracked
  Surefire/ArchUnit gap noted since T32, not a regression introduced here).
- **Four negative-proof runs, each confirmed to fail for the right reason and reverted:**
  1. D1b, first attempt (access-based): scratch class with unused `Account`/`VerificationToken`
     fields — incorrectly passed, exposing the implementation bug above.
  2. D1b, corrected (dependency-based): same scratch class — correctly failed, naming both
     violations by field and type.
  3. D1b regression check: reverted, confirmed clean 4/4 pass (proves `account.dto`/`account.event`
     remain exempt under the new mechanism, not just the old one).
  4. D2: a scratch `LockoutService` field added to `AdminRoleController` (in `authz`, not on the
     allowlist) — correctly failed, naming the exact field and type. Reverted, confirmed clean 4/4
     pass (proves both real, allowlisted exceptions — `AccountController`→`SessionService`,
     `AdminAccountController`→`LockoutService` — still pass).
- `git status` after every revert confirmed no leftover scratch file; final diff is exactly the one
  authorized file.

---

**Phase 6 complete — implementation notes written.** Proceed to Phase 7 (Self Review) on approval.
