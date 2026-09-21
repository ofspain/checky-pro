# crypto · T25 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T25 — ArchUnit/module boundaries |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Scope reminder:** Review the T25 tests only. The deliverables are `common/CrossModuleEntityArchitectureTest.java` and `watch/RogueWatchEntityReferencer.java`; `KmsSignerArchitectureTest` and `ResourceServerConfigIntegrationTest` were confirmed unmodified.

---

## Summary

The Phase 10 test set directly verifies L15 via the named canary, proves the rule can actually fail via a well-placed negative-proof fixture, and locks the two allowlisted exceptions with a regression guard. The earlier Phase 8 findings (allowlist guard, inline class reference) have been resolved.

The remaining gaps are **additional negative-proof coverage** and **stronger assertions**; they are not correctness defects. The tests as written will pass and do verify the specification.

---

## Recommendations

### 1. No negative-proof for the fail-fast null-module path

- **Gap:** The `ArchCondition` fail-fasts if `featureModuleOf(entityClass)` returns `null` (an `@Entity` class outside every `FEATURE_MODULES` entry). The canary only proves the happy path; it does not prove this fail-fast fires.
- **Why it matters:** A bug in `FEATURE_MODULES` list maintenance or in `featureModuleOf` could silently stop enforcing the rule for an entity. A dedicated negative-proof would lock the documented "fail-fast, not silent-skip" behavior.
- **Suggested test:** `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` — create a fixture `@Entity` in a package outside every listed module (e.g., a top-level `archtestfixtures` package), import only the fixture into a narrow `JavaClasses` set, run the rule, and assert an `AssertionError` whose message contains the fail-fast text ("does not reside in any module listed in FEATURE_MODULES").

### 2. No negative-proof for `common` importing a feature-module entity

- **Gap:** The Javadoc explicitly states that `common` receives no exemption and that a `common`-package class importing any feature-module entity is a violation. There is no test proving this.
- **Why it matters:** Future maintainers may assume `common` is allowed to use entities (because it is shared plumbing). A test would make the documented behavior CI-enforceable.
- **Suggested test:** `shouldRejectCommonPackageImportingAFeatureModuleEntity` — create a fixture in `com.themistra.crypto.common` (test source) with a field of type `token.TokenAllowlist`, import only the fixture + `TokenAllowlist` into a narrow `JavaClasses` set, and assert the rule fails with `AssertionError`.

### 3. Negative-proof only exercises a field-dependency shape

- **Gap:** `RogueWatchEntityReferencer` uses a single, never-read field of type `TokenAllowlist`. The rule is dependency-based and should catch parameters, return types, and annotations too, but only the field shape is proven.
- **Why it matters:** A regression that accidentally narrowed the rule to field-only inspection would not be caught. Proving non-field dependency shapes strengthens the guarantee that the rule truly inspects all direct dependencies.
- **Suggested test:** Add a method to `RogueWatchEntityReferencer` (or a second fixture) that accepts `TokenAllowlist` as a parameter and/or returns it, then run the negative-proof test against that fixture and assert the rule still fails.

### 4. Negative-proof does not assert the failure message

- **Gap:** `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` only checks `isInstanceOf(AssertionError.class)`. If the rule threw `AssertionError` for an unrelated reason (e.g., a malformed `JavaClasses` set), the test would still pass.
- **Why it matters:** The test should prove the *specific* cross-module violation path, not just that some assertion failed somewhere.
- **Suggested test:** Strengthen the existing assertion to verify the message mentions the expected violation, e.g.:
  ```java
  assertThatThrownBy(() -> shouldPreventCrossModuleEntityImports.check(violatingClasses))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining("RogueWatchEntityReferencer")
          .hasMessageContaining("TokenAllowlist");
  ```

### 5. No test that a new cross-module entity import outside the allowlist is rejected

- **Gap:** The canary proves the current codebase is clean, and the negative-proof uses the fixture. Neither proves that a *new*, non-allowlisted production-style class importing an entity would fail.
- **Why it matters:** This would demonstrate that the allowlist is narrow (per-origin + per-entity) and not a blanket exemption for entire modules.
- **Suggested test:** `shouldRejectNewCrossModuleEntityImportOutsideAllowlist` — create a second fixture inside `com.themistra.crypto.watch` that imports a different real entity (e.g., `screening.ScreeningResult`, which is not in `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES`), import only the fixture + entity into a narrow `JavaClasses` set, and assert the rule fails.

---

## Confirmations

- The named test `shouldPreventCrossModuleEntityImports` is present as an `ArchRule` and is exercised by a plain `@Test` canary that gates the build.
- AC1 and AC2 are satisfied by re-running `KmsSignerArchitectureTest` and `ResourceServerConfigIntegrationTest` unmodified.
- AC3 is satisfied by the canary against the real codebase, with the two pre-existing couplings explicitly allowlisted.
- AC4 is satisfied by the negative-proof fixture placed inside a real feature module (`watch`) so the real `entityModule != dependingModule` path is exercised.
- The Phase 8/9 allowlist-regression guard correctly closes the stale-allowlist and `Watcher` rename-safety gaps.
- No duplicate tests or production code changes were introduced.
