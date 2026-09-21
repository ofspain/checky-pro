# crypto · T25 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T25 — ArchUnit/module boundaries |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Scope reminder:** Review the T25 implementation (Phase 6) and self-review (Phase 7) only. The deliverables are `CrossModuleEntityArchitectureTest.java` and `RogueWatchEntityReferencer.java`; `KmsSignerArchitectureTest` and `ResourceServerConfigIntegrationTest` were confirmed unmodified.

---

## Summary

The T25 implementation correctly follows the frozen brief and addresses all four acceptance criteria. The self-review identified two real gaps: a missing regression guard for the allowlist and a cosmetic inline class reference. This independent review agrees with both and adds two additional low-severity observations about the allowlist's relationship to L15 and the shape of the negative proof.

No correctness, security, or build-breaking defects were found.

---

## Findings

### 1. No regression guard for stale allowlist entries

- **Issue:** `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` can silently become dead configuration. If a future refactor removes `AttestationService`'s use of `ChainCursor` or `Watcher`'s use of `QuorumDecision`, the corresponding allowlist entry would remain forever without causing any test to fail.
- **Evidence:** `common/CrossModuleEntityArchitectureTest.java:76-78` defines the allowlist, but there is no equivalent of auth's `allowlistedControllerServiceDependenciesStillExistInCode` test. Auth's own `ArchitectureTest.java:356-374` demonstrates the same guard.
- **Recommendation:** Add a canary test such as:
  ```java
  @Test
  void allowlistedCrossModuleEntityDependenciesStillExistInCode() {
      assertThat(hasDirectDependency(AttestationService.class, ChainCursor.class))
              .as("AttestationService should still depend on ChainCursor; if not, remove the stale allowlist entry")
              .isTrue();
      JavaClass watcherClass = analyzedClasses.get("com.themistra.crypto.watch.Watcher");
      assertThat(watcherClass.getDirectDependenciesFromSelf().stream()
              .anyMatch(d -> d.getTargetClass().getFullName().equals(QuorumDecision.class.getName())))
              .as("Watcher should still depend on QuorumDecision; if not, remove the stale allowlist entry")
              .isTrue();
  }

  private static boolean hasDirectDependency(Class<?> origin, Class<?> target) {
      return analyzedClasses.get(origin).getDirectDependenciesFromSelf().stream()
              .anyMatch(d -> d.getTargetClass().getFullName().equals(target.getName()));
  }
  ```
- **Confidence:** Medium

### 2. `Watcher` allowlist entry is a plain string literal

- **Issue:** Because `Watcher` is package-private, its allowlist entry is spelled as `"com.themistra.crypto.watch.Watcher->" + QuorumDecision.class.getName()`. A rename of `Watcher` will not break compilation of this test file, so the allowlist entry would silently stop matching and the rule would begin failing for the renamed class.
- **Evidence:** `common/CrossModuleEntityArchitectureTest.java:78`.
- **Recommendation:** The guard test recommended in Finding 1 also mitigates this: if `Watcher` is renamed, the `analyzedClasses.get("com.themistra.crypto.watch.Watcher")` lookup will throw, and the test will fail loudly. No production change is needed.
- **Confidence:** Medium

### 3. Inline fully-qualified `TokenAllowlist` reference

- **Issue:** `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` references `com.themistra.crypto.token.TokenAllowlist.class` inline, while other classes used by the test are imported normally at the top of the file.
- **Evidence:** `common/CrossModuleEntityArchitectureTest.java:144` vs. lines 3-6.
- **Recommendation:** Add `import com.themistra.crypto.token.TokenAllowlist;` and use the bare class name in the negative-proof test, matching the file's own style.
- **Confidence:** Low

### 4. Allowlisted dependencies are a literal exception to L15 and are not tracked as actionable debt

- **Issue:** L15 states: "No feature module imports another feature module's entity." The two entries in `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` are exactly that — cross-module entity imports. The brief and Javadoc correctly treat them as pre-existing, out-of-scope technical debt, but there is no TODO, ticket reference, or Open Question directing future work to remove them.
- **Evidence:** `common/CrossModuleEntityArchitectureTest.java:66-78` documents the couplings as "candidates for a future decoupling task" but provides no traceable next step.
- **Recommendation:** Add a concise TODO/FIXME comment linking to a planned decoupling task (e.g., `// TODO(CRYPTO-TECH-DEBT-1): pass a DTO instead of ChainCursor/QuorumDecision entities`), or record an Open Question in this task's artifacts so L15 can eventually be enforced without exceptions.
- **Confidence:** Low

### 5. Negative-proof only exercises a field-dependency shape

- **Issue:** `RogueWatchEntityReferencer` uses a single, never-read field of type `TokenAllowlist`. The rule is dependency-based and will catch this, but the test does not also prove the rule catches the same entity used as a method parameter, return type, or annotation value.
- **Evidence:** `watch/RogueWatchEntityReferencer.java:18`.
- **Recommendation:** Strengthen the fixture by adding a method that takes/returns `TokenAllowlist`, or add a second negative-proof fixture covering a different dependency shape. This is optional; the current fixture is sufficient for the primary L15 path.
- **Confidence:** Low

---

## Confirmations

- **Scope respected:** Only the two authorized files were created; no production code, `KmsSignerArchitectureTest`, `ResourceServerConfigIntegrationTest`, existing `*ModuleBoundaryTest.java`, or `spec/` files were modified.
- **Rule technique:** Dependency-based (`getDirectDependenciesToSelf`) checking is the correct choice and matches auth's established, documented rationale.
- **Fail-fast behavior:** A class outside every listed `FEATURE_MODULES` entry will fail loudly rather than be silently skipped.
- **`common` exemption:** The implementation correctly gives `common` no special case; an entity import from `common` would be flagged because `featureModuleOf` returns `null`.
- **Negative-proof placement:** `RogueWatchEntityReferencer` is correctly placed inside `com.themistra.crypto.watch` so the test exercises the real `entityModule != dependingModule` path, not the fail-fast null path.
- **Canary pattern:** The plain `@Test` canary correctly gates the build because this repo's Surefire setup does not execute `@ArchTest` fields.

---

## Open Questions

- None specific to T25. The class-collision between the `feat` and `spec` `ProviderAnswer` types in current `main` is a project-level integration blocker, not a defect in this task's implementation.
