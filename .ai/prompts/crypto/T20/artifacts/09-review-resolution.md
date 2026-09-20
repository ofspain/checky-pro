# crypto · T20 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 8 (Kimi) independent review. Phase 8's own verdict confirmed
the Phase 7 self-review's critical `@Autowired` finding was fixed and regression-tested, and found no
new logic bugs, security defects, or LOCKED-decision deviations. Four new findings required a fresh
decision.

| # | Finding | Disposition | Change made |
|---|---|---|---|
| 1 | `onlyKmsSignerMayUseTheKmsSigningSdk`'s exception is name-only, with no package constraint — a hypothetical `com.themistra.crypto.watch.KmsSignerWatcher` could dodge it | **ACCEPTED** | Tightened to `.that().haveSimpleNameNotStartingWith("KmsSigner").or().resideOutsideOfPackage("com.themistra.crypto.attest..")` — only a class named `KmsSigner*` **and** residing inside `attest` is exempted. Verified the `.or(DescribedPredicate)` combinator exists directly against the ArchUnit 1.3.0 jar's own `GivenClassesConjunction` bytecode before using it. |
| 2 | Structural source-scan tests read files via a CWD-relative `Path.of("src/main/java/...")`, fragile if run from a different working directory | **REJECTED** | This is the exact same pattern already used by every module-boundary/source-scan test across T16-T19 (`WatchModuleBoundaryTest`, `ReorgModuleBoundaryTest`, `ScreeningModuleBoundaryTest`, `ChainBaselineMigrationIntegrationTest`, etc.). Fixing it only in this task's new files would be inconsistent, unrequested scope creep against an established, already-accepted codebase-wide convention that works correctly under the standard `mvn test` invocation every developer and CI actually uses. |
| 3 | `@AnalyzeClasses`'s scanned package and the canary's `ClassFileImporter` package are two independent string literals that could silently drift apart | **ACCEPTED** | Extracted `static final String ANALYZED_PACKAGE = "com.themistra.crypto"`, referenced by both `@AnalyzeClasses(packages = ANALYZED_PACKAGE)` and `new ClassFileImporter().importPackages(ANALYZED_PACKAGE)` — mirrors auth's own `ArchitectureTest.ANALYZED_PACKAGE` lesson (that service's own Kimi Phase 11 Gap 1). |
| 4 | `KmsSigner` never closes its `KmsClient` on context shutdown, unlike `MfaSeedEncryption` (the precedent it's explicitly modeled on) | **ACCEPTED** | `KmsSigner implements DisposableBean`; `destroy()` calls `kmsClient.close()`. |

## Files changed in this phase

- `attest/KmsSignerArchitectureTest.java` — Finding #1's tightened rule; Finding #3's shared
  `ANALYZED_PACKAGE` constant; Javadoc updated to describe both.
- `attest/KmsSigner.java` — Finding #4's `DisposableBean`/`destroy()` addition.

No public API, class name, or method signature changed beyond `KmsSigner` gaining `implements
DisposableBean` and its `destroy()` method. No refactoring beyond what each accepted finding required.

## Verification

`mvn -pl services/crypto test -Dtest=KmsSignerTest,KmsSignerArchitectureTest,KmsSignerLocalStackIntegrationTest,KmsSignerSpringWiringTest`
— 11/11 pass. Full module regression (`mvn -pl services/crypto -am test`): 651 tests, same 6
pre-existing, disclosed, unrelated failing tests; zero regressions.
