# crypto · T25 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes, tests were written alongside the
one new production-adjacent file this task authors (T25 is test-only — its entire deliverable is an
ArchUnit test and its negative-proof fixture). Strengthened at Phase 9 per 3 of Kimi's/self-review's
accepted findings. No production code exists for this task. This artifact is the traceability manifest.

## Test files (this task's own contribution)

| File | Tests | Purpose |
|---|---|---|
| `common/CrossModuleEntityArchitectureTest.java` | 3 | The named test `shouldPreventCrossModuleEntityImports` (L15) plus its negative-proof and allowlist-regression-guard tests. |
| `watch/RogueWatchEntityReferencer.java` (fixture, no `@Test` methods) | 0 | A deliberate, real L15 violation used only by the negative-proof test above. |

**Total: 3 new test methods**, all passing. **Confirmed unmodified:** `attest/KmsSignerArchitectureTest.java`
(2 tests, R22/L11) and `common/ResourceServerConfigIntegrationTest.java` (11 tests, R27).

## Traceability matrix

| Test | AC | What it proves |
|---|---|---|
| `KmsSignerArchitectureTest` (both tests, re-run unmodified) | AC1 (R22/L11) | The KMS-signer package ban remains intact and untouched by this task — confirmed passing alongside the new file, ruling out any accidental `@AnalyzeClasses`/static-state interference. |
| `ResourceServerConfigIntegrationTest` (all 11, re-run unmodified) | AC2 (R27) | The internal-scope requirement on watch/attest endpoints remains intact and untouched by this task. |
| `CrossModuleEntityArchitectureTest.shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (the named test) | AC3 (L15) | Every `@Entity` class across all 11 feature modules is accessed only from its own module — with the 2 pre-existing, explicitly named exceptions (`AttestationService`→`ChainCursor`, `Watcher`→`QuorumDecision`) discovered on this rule's real first run and resolved via a narrow allowlist per explicit user direction (Phase 6/9). |
| `CrossModuleEntityArchitectureTest.shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` | AC4 (negative-proof) | `RogueWatchEntityReferencer` (a class genuinely inside `watch` referencing `token.TokenAllowlist`) causes the rule to throw `AssertionError` when checked against a narrow, two-class `JavaClasses` set — proving the rule exercises the real `entityModule != dependingModule` violation path, not merely the fail-fast null path (the mistake caught and corrected at Phase 3). |
| `CrossModuleEntityArchitectureTest.allowlistedCrossModuleEntityDependenciesStillExistInCode` (Phase 9 addition) | Regression-lock for the allowlist itself | Both allowlisted dependencies are asserted to still exist in the real codebase — if a future refactor removes either, this test fails loudly rather than leaving silent dead configuration; also closes the `Watcher` string-literal rename-safety gap (a rename makes the `analyzedClasses.get(...)` lookup throw). |

## Verification run

`mvn -pl services/crypto test -Dtest=CrossModuleEntityArchitectureTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
— 16/16 pass (3 + 2 + 11).

Full module regression (`mvn -pl services/crypto -am test`): not re-run this phase — the last full
regression attempt in this session (T24 Phase 6) showed 0 real failures and 13 Docker-unavailability
errors, unrelated to any task's own tests. `CrossModuleEntityArchitectureTest` and
`KmsSignerArchitectureTest` are both pure static-analysis (no Spring context, no Testcontainers);
`ResourceServerConfigIntegrationTest` is a `@WebMvcTest`-style slice (no Docker dependency either,
confirmed by its own 11/11 pass in this environment moments ago).

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved (at the time this section was
first written — see the Phase 11 additions below).

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into this artifact and the test suite. Kimi raised 5 strengthening
recommendations (no correctness defects). 2 accepted, 2 rejected as exercising no new code path beyond
what an existing test already covers, 1 acknowledged-not-implemented (a repeat of Phase 8's identical
Finding #5, dispositioned identically for the same reason).

| Recommendation | Disposition | Resolution |
|---|---|---|
| 1. No negative-proof for the fail-fast null-module path | **ACCEPTED** | Added `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`, using a new fixture `archtestfixtures.RogueUnmappedEntity` (a real `@Entity`, placed outside `com.themistra.crypto` entirely, T20's own established location for exactly this purpose) — proves the documented "fail loudly, don't silently skip" behavior, a genuinely different code branch than the existing negative-proof exercises. |
| 2. No negative-proof for `common` importing a feature-module entity | **REJECTED — no new code path** | Verified directly against the `ArchCondition`'s own logic: `sameModule = entityModule.equals(dependingModule)` treats a `common`-package importer (`dependingModule == null`) identically to any other non-matching module (`dependingModule` = a different string) — both simply make the equality `false`, the same branch the existing `RogueWatchEntityReferencer` test already exercises. A dedicated `common`-specific fixture would prove the same boolean outcome with different input data, not new logic. |
| 3. Negative-proof only exercises a field-dependency shape | **ACKNOWLEDGED, not implemented** | Identical to Phase 8 Finding #5, dispositioned identically at Phase 9: `ArchCondition` never inspects dependency *shape*, only origin/target module identity — the real Phase 6 first-run violations already empirically proved multiple shapes (field, method parameter, return type, method call) are caught by the same underlying ArchUnit API, before this task's own tests even existed. |
| 4. Negative-proof doesn't assert the failure message (could pass for the wrong reason) | **ACCEPTED** | Strengthened `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` with `.hasMessageContaining("RogueWatchEntityReferencer")` and `.hasMessageContaining("TokenAllowlist")`, verified passing against the real thrown message. |
| 5. No test that a *new*, non-allowlisted cross-module entity import is still rejected | **REJECTED — already proven** | The existing `RogueWatchEntityReferencer`→`TokenAllowlist` pair is itself not in `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` — the existing negative-proof test already demonstrates the allowlist is narrow (specific pairs, not a blanket per-module exemption). Kimi's suggested substitution (`screening.ScreeningResult` instead of `token.TokenAllowlist`) is the same property with different data, not new coverage. |

**Verification run (Phase 11):**
`mvn -pl services/crypto test -Dtest=CrossModuleEntityArchitectureTest,KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest`
— 17/17 pass (4 in `CrossModuleEntityArchitectureTest`, up from 3; 2 in `KmsSignerArchitectureTest`; 11
in `ResourceServerConfigIntegrationTest`, both cited unmodified).
