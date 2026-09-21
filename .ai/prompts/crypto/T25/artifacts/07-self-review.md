# crypto · T25 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`CrossModuleEntityArchitectureTest.java`,
`RogueWatchEntityReferencer.java`) against the frozen brief (`artifacts/04-frozen-task-brief.md`) and
`agents.md`. Findings only — no fixes applied here (Phase 9), per this phase's own rule. Two findings
identified.

## 1. `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` has no "still exists in code" regression guard

- **Issue:** Auth's own `ArchitectureTest.java` — this task's own explicit precedent — has a dedicated
  test for its structurally identical allowlist (`ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES`):
  `allowlistedControllerServiceDependenciesStillExistInCode`, documented there as closing "Kimi Phase 11
  Gap 3" — if a future refactor ever removed one of the two allowlisted dependencies, the allowlist
  entry would become silent dead configuration (the rule would simply never have anything to apply it
  to, not fail), and nothing would notice. My own new `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` has the
  identical shape and the identical exposure, but no equivalent guard test.
- **Severity:** Medium (not a live defect — both allowlisted couplings genuinely exist right now,
  confirmed at Phase 6 — but a real, demonstrated-in-precedent gap: if `AttestationService` stopped
  depending on `ChainCursor`, or `Watcher` stopped depending on `QuorumDecision`, the now-stale allowlist
  entry would silently persist forever, undetected).
- **Evidence:** `common/CrossModuleEntityArchitectureTest.java:76-78` (the allowlist); contrast with
  `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java:356-374`
  (`allowlistedControllerServiceDependenciesStillExistInCode` and its `hasDirectDependency` helper).
- **Recommendation:** Add an equivalent test asserting both allowlisted dependencies still exist in the
  real, current codebase (e.g., via `analyzedClasses.get(AttestationService.class)
  .getDirectDependenciesFromSelf()` filtered to `ChainCursor`, and the `Watcher`/`QuorumDecision`
  equivalent via its fully-qualified string name since `Watcher` can't be referenced by class literal
  here either).

## 2. Inline fully-qualified class reference instead of an import

- **Issue:** `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` references
  `com.themistra.crypto.token.TokenAllowlist.class` inline, while every other cross-module class this
  file uses (`AttestationService`, `ChainCursor`, `QuorumDecision`) is imported normally at the top.
- **Severity:** Low (cosmetic/style inconsistency only — compiles and behaves identically either way).
- **Evidence:** `common/CrossModuleEntityArchitectureTest.java:144` vs. lines 3-6 (the file's own import
  block).
- **Recommendation:** Add `import com.themistra.crypto.token.TokenAllowlist;` and use the bare class
  name, matching the file's own established style.

---

No correctness, thread-safety, or scope-boundary defects were found. Specifically checked and found
clean:
- **Frozen-brief conformance:** only the two authorized new files were created; `KmsSignerArchitectureTest`,
  `ResourceServerConfigIntegrationTest`, every existing `*ModuleBoundaryTest.java`, and every
  `@Entity`/feature-module production class remain byte-for-byte unmodified.
- **Allowlist narrowness:** verified the allowlist is keyed by exact `(originClass, entityClass)` pairs,
  not by module or package — a hypothetical *new* class in `attest` depending on `ChainCursor` would
  still correctly fail the rule; only the two specific, named, already-existing dependencies are exempt.
- **`common` exemption claim (Frozen Brief Finding #3):** re-verified the entity condition gives `common`
  no special-case anywhere in the code — confirmed by re-reading `onlyBeAccessedFromTheSameFeatureModule()`
  in full; the Javadoc's claim matches the actual implementation exactly.
- **Fixture placement (Finding #2's own fix):** `RogueWatchEntityReferencer` is confirmed to live inside
  `com.themistra.crypto.watch` (not the standalone `archtestfixtures` package), and the negative-proof
  test's narrow `JavaClasses` set correctly exercises the real `entityModule != dependingModule` path
  (confirmed by the passing test itself, not just by inspection).
