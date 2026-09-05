<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). -->

# auth · T35 · Phase 5 — Implementation Plan

No code, but this task's two rules need a genuinely new ArchUnit technique (a custom
`ArchCondition`, not the simple fluent-predicate style every existing rule in this file uses),
since both D1b and D2 need to compare each violation's *own* module against a computed reference,
not a single fixed package. Verified: `jakarta.persistence.Entity` is the correct annotation
(`Account.java`'s own import); `@RestControllerAdvice` is meta-annotated `@AliasFor(annotation =
ControllerAdvice.class)`, never `@RestController` — confirmed via bytecode — so
`areAnnotatedWith(RestController.class)` naturally excludes every `@RestControllerAdvice` class
with no extra exclusion logic needed (Finding 8 is satisfied for free by the chosen detector).

## Files to modify

- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`.

## New private helpers (shared by both rules)

- `private static final List<String> FEATURE_MODULES` — the 10 named packages (D2's frozen list).
- `private static String featureModuleOf(JavaClass javaClass)` → returns the feature-module name
  (e.g. `"account"`) if `javaClass`'s package is that module or a subpackage of it; `null`
  otherwise (e.g. for `common` or unrecognized top-level code).

## `shouldPreventCrossModuleEntityImports` (D1b, replaces `only_the_account_module_may_touch_the_Account_entity`)

```java
@ArchTest
static final ArchRule shouldPreventCrossModuleEntityImports = classes()
        .that().areAnnotatedWith(Entity.class)
        .should(onlyBeAccessedFromTheSameFeatureModule())
        .because("L12: no feature module may import an entity class from another feature "
                + "module — generalized (Phase 4 D1b) from the original Account-only rule, "
                + "which had a package-wildcard bug incorrectly flagging account.dto/account.event");
```

- **Private helper**: `private static ArchCondition<JavaClass> onlyBeAccessedFromTheSameFeatureModule()`
  — for each `@Entity` class found, iterates `entityClass.getAccessesToSelf()`; for each access,
  computes `featureModuleOf(access.getOriginOwner())` and compares to `featureModuleOf(entityClass)`;
  adds a `SimpleConditionEvent` satisfied iff the modules match (or the entity's own module is
  unrecognized — e.g. not actually inside a listed feature module — in which case nothing is
  enforced, matching the existing rules' own conservative style of only asserting what's actually
  known).
- New imports: `jakarta.persistence.Entity`, `com.tngtech.archunit.lang.ArchCondition`,
  `com.tngtech.archunit.lang.ConditionEvents`, `com.tngtech.archunit.lang.SimpleConditionEvent`.
- **Canary**: `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild()`, identical
  shape to T32's own canary — `shouldPreventCrossModuleEntityImports.check(analyzedClasses())`.

## New controller→service rule (D2)

```java
@ArchTest
static final ArchRule controllersDependOnlyOnTheirOwnModuleServices = classes()
        .that().areAnnotatedWith(RestController.class)
        .should(dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException())
        .because("L12: a controller reaching into another module's service layer without a "
                + "defined boundary is the same class of coupling L12 forbids at the entity "
                + "level — two pre-existing, deliberate exceptions are explicitly allowlisted "
                + "(Phase 4 D2), not silently broken by this rule");
```

- **Private constant**: `private static final Set<String> ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES`
  — exactly 2 entries, `"...AccountController->...SessionService"` and
  `"...AdminAccountController->...LockoutService"` (fully-qualified names, `->`-joined).
- **Private helper**: `private static ArchCondition<JavaClass> dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException()`
  — for each `@RestController` class, iterates `controllerClass.getDirectDependenciesFromSelf()`,
  filters to targets annotated `@Service`; satisfied iff same feature module OR the
  `controllerClass.getName() + "->" + targetClass.getName()` pair is in the allowlist.
- New import: `org.springframework.stereotype.Service`.
- **Canary**: `controllersDependOnlyOnTheirOwnModuleServicesIsCheckedDuringStandardBuild()`.

## Entities used / Repositories used / Services used

None — this task reflects on entity/controller/service *classes*, it doesn't instantiate or call
any of them.

## Unit / integration tests required

None beyond the two canaries above (no Spring context, no Testcontainers — matches every existing
rule/canary in this file).

## Negative-proof plan (per this pipeline's own established practice, not new brief text — Kimi
Finding 6's disposition)

1. **D1b, forward**: temporarily add a scratch class outside `account` importing `Account`
   directly → confirm `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` fails →
   revert.
2. **D1b, a second entity**: temporarily add a scratch class outside `account` importing
   `VerificationToken` directly → confirm the same canary fails → revert. (Proves the
   generalization actually covers more than `Account`, not just the one entity every prior version
   of this rule ever checked.)
3. **D1b, the original bug**: confirm `account.dto`/`account.event` classes (e.g.
   `AccountResponse`) do NOT trip the rule — this should already pass given D1's wildcard fix, but
   re-confirm now that the rule's mechanism changed from a simple predicate to a custom condition.
4. **D2, forward**: temporarily add a scratch service dependency from a controller to another
   module's service NOT on the allowlist → confirm the D2 canary fails → revert.
5. **D2, exceptions**: confirm `AccountController`/`AdminAccountController`'s real, existing
   dependencies on `SessionService`/`LockoutService` do NOT trip the rule (they're real code,
   already present — this is confirmed by the rule simply passing on the unmodified codebase, not
   a separate scratch step).
6. **D2, controller advice**: confirm no `@RestControllerAdvice` class (e.g.
   `AccountExceptionHandler`) is ever inspected by the rule at all — confirmed structurally (the
   detector predicate excludes them by construction, per the bytecode check above), not just by
   the rule happening to pass.

## Execution order

1. Add `FEATURE_MODULES`/`featureModuleOf` helpers.
2. Implement and negative-proof `shouldPreventCrossModuleEntityImports` (steps 1-3 above) —
   replaces the old rule entirely, not added alongside it.
3. Implement and negative-proof the D2 rule (steps 4-6 above).
4. Full `ArchitectureTest` class run (all 11 `@ArchTest` rules + all `@Test` methods) to confirm no
   regression to the 9 pre-existing rules or T32's own canary.
5. Full `services/auth` compile to confirm no import/compile regressions elsewhere.

---

**Phase 5 complete — implementation plan written.** Proceed to Phase 6 (Implementation) on approval.
