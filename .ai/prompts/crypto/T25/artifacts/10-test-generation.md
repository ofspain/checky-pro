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
first written — see the Phase 11 additions below, once Kimi's test review lands).
