<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T35 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T35 — ArchUnit / module-boundary tests |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the committed tests in `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` and the Phase 10 test manifest. Findings are recommendations only, formatted as **Gap · Why it matters · Suggested test**.

---

## Gap 1 — Manual negative-proofs are not preserved as automated regression tests

**Why it matters.** The six negative-proof runs documented in Phase 10 (access-vs-dependency bug, entity fail-fast, controller fail-fast, common-service allowance, etc.) are the strongest evidence that the rules behave correctly. They currently exist only as manual, reverted scratch-class exercises. A future refactor of `FEATURE_MODULES`, `isInCommonModule`, or either `ArchCondition` could reintroduce a defect that no committed test would catch, because the current canaries only exercise the *happy path* of the existing codebase.

**Suggested test.** Add a small, self-contained `@Disabled` canary test (or a documented scratch-class procedure in a comment) for each non-happy-path behavior:
- One that temporarily introduces a scratch `@Entity` outside `FEATURE_MODULES` and asserts the entity canary fails.
- One that temporarily introduces a scratch `@RestController` outside `FEATURE_MODULES` and asserts the controller canary fails.
- One that temporarily introduces a scratch `@Service` in `common` and asserts the controller canary passes.
Each should include a comment explaining how to enable it for a one-off regression check. This preserves the negative-proof discipline without leaving violating code in `main`.

---

## Gap 2 — No regression guard for the original wildcard bug

**Why it matters.** The task's primary fix is that `resideOutsideOfPackage("com.themistra.auth.account")` (without `..`) incorrectly flagged `account.dto` and `account.event`. The new rule is generalized and no longer uses that predicate, so the passing canary does not specifically prove the original bug is fixed — it only proves the new rule works on current code.

**Suggested test.** Add a plain JUnit test that explicitly names the live positive cases as a regression guard:

```java
@Test
void accountDtoAndEventSubpackagesMayDependOnAccountEntity() {
    // Regression guard for T35's original wildcard bug: these two account-module subpackages
    // legitimately reference the Account entity and must not be flagged as cross-module imports.
    classes()
        .that().haveFullyQualifiedName("com.themistra.auth.account.dto.AccountResponse")
        .or().haveFullyQualifiedName("com.themistra.auth.account.event.UserLifecycleEventPayload")
        .should().dependOnClassesThat().haveFullyQualifiedName("com.themistra.auth.account.Account")
        .check(analyzedClasses());
}
```

This makes the exemption explicit and protects against a future refactor that accidentally narrows the entity rule back to a strict package predicate.

---

## Gap 3 — Allowlisted controller→service exceptions are not asserted to still exist

**Why it matters.** The two allowlisted exceptions (`AccountController → SessionService`, `AdminAccountController → LockoutService`) are the only reason the controller rule tolerates cross-module service dependencies. If those real dependencies are removed during a future refactor, the allowlist becomes dead configuration and the rule's exception path is no longer exercised by any live code. A later change that breaks the exception logic would then be discovered only when someone adds a *new* exception, not proactively.

**Suggested test.** Add a plain JUnit test that asserts the allowlisted dependencies are still present in the bytecode:

```java
@Test
void allowlistedControllerServiceDependenciesStillExist() {
    assertThat(hasDependency(AccountController.class, SessionService.class)).isTrue();
    assertThat(hasDependency(AdminAccountController.class, LockoutService.class)).isTrue();
}

private boolean hasDependency(Class<?> origin, Class<?> target) {
    JavaClass originClass = analyzedClasses().get(origin);
    return originClass.getDirectDependenciesFromSelf().stream()
            .anyMatch(d -> d.getTargetClass().getFullName().equals(target.getName()));
}
```

This ensures the exceptions remain meaningful regression cases.

---

## Gap 4 — Common-service allowance branch has no live coverage

**Why it matters.** `dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException()` explicitly allows controllers to depend on `@Service` classes in `common`. Today no controller does so, so this branch is not exercised by the passing canary. A regression that accidentally removes the `isInCommonModule(targetClass)` check would not fail any test until a controller legitimately depends on a common service.

**Suggested test.** Either:
- (Preferred) Add a temporary scratch `@Service` in `com.themistra.auth.common` and a temporary scratch `@RestController` that depends on it, run the canary to confirm it passes, then revert — and document this in the Phase 10 manifest as a committed negative-proof procedure.
- Or add a focused test of the helper invariants: `assertThat(isInCommonModule(analyzedClasses().get(PublicEndpoints.class))).isTrue()` and `assertThat(isInCommonModule(analyzedClasses().get(AccountController.class))).isFalse()`. This is weaker but at least prevents the helper from being silently deleted or inverted.

---

## Gap 5 — `analyzedClasses` cache is not thread-safe

**Why it matters.** The lazy static `analyzedClasses` field is initialized without synchronization. The implementation comment correctly notes that JUnit Jupiter runs tests sequentially in this project today, but if parallel execution is ever enabled (e.g., via `junit.jupiter.execution.parallel.enabled`), two canaries could race through the `if (analyzedClasses == null)` check and trigger a double import. This is a latent flakiness hazard.

**Suggested test.** This is not easily reproducible as a deterministic unit test. Instead, either:
- Guard the lazy init with a `synchronized` block or initialize `analyzedClasses` eagerly in a `@BeforeAll` method, or
- Add a code comment warning that parallel execution must not be enabled for this class without synchronization.

---

## Gap 6 — Spec traceability: named test is still mapped to L10 in `package.md`

**Why it matters.** `spec/auth-service/package.md` §8 maps `shouldPreventCrossModuleEntityImports` to **L10**, but `design.md` §4a defines L10 as the MFA enforcement role rule and L12 as the module-boundaries rule. The implementation and tests correctly enforce L12, so the test is verified against the wrong LOCKED-decision label in the spec. This does not affect correctness, but it breaks traceability and acceptance-criteria mapping.

**Suggested test/action.** Do not modify `spec/`. Log an open question for the spec author to update `package.md` §8 so `shouldPreventCrossModuleEntityImports` maps to L12. Until then, add a comment in `ArchitectureTest.java` above the rule noting that the test implements L12, not L10.

---

## Summary

The committed tests cover the happy path and the canary pattern correctly closes the Maven/Surefire gap for both new rules. The main weakness is that the strongest evidence of correctness — the negative-proof runs and the specific allowlisted exceptions — is not automated or preserved in the repo. Converting the most important of those into regression guards (Gaps 1–3) would make the test suite materially more robust against future refactor regressions.

(End of Phase 11 test review.)
