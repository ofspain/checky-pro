# crypto · T25 · Phase 5 — Implementation Plan

**Correction to Phase 4's own Files-to-Create listing:** the frozen brief's file path line
(`services/crypto/src/test/java/archtestfixtures/RogueWatchEntityReferencer.java`) contradicts its own
parenthetical, which correctly says the fixture must live *inside* `com.themistra.crypto.watch`, not
the standalone `archtestfixtures` package. This plan uses the parenthetical's correct intent — the path
line was a copy-paste error carried over from T20's own `RogueAttestReferencer` path. Pinned here:
`services/crypto/src/test/java/com/themistra/crypto/watch/RogueWatchEntityReferencer.java`.

## Files to create

- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/watch/RogueWatchEntityReferencer.java`

## Files to modify

None.

## Public methods (signatures)

None — both new classes are package-private test classes with no public API, matching
`KmsSignerArchitectureTest`'s and every other ArchUnit test's own convention in this codebase.

## Private methods

`CrossModuleEntityArchitectureTest`:
```java
private static String featureModuleOf(JavaClass javaClass) { ... }  // returns owning module or null
```
Mirrors auth's `ArchitectureTest.featureModuleOf` exactly, adapted to this branch's own 11-entry
`FEATURE_MODULES` list (Phase 4: `adapter, attest, events, finality, observation, provider, quorum,
reorg, screening, token, watch` — verified against this branch's actual package layout, not `main`'s).

`onlyBeAccessedFromTheSameFeatureModule()` — a private static factory returning an
`ArchCondition<JavaClass>`, structurally identical to auth's own (dependency-based via
`getDirectDependenciesToSelf()`, fail-fast on a `null` module for either the entity or the depending
class).

`RogueWatchEntityReferencer` has no methods of its own beyond a single field of type
`com.themistra.crypto.token.TokenAllowlist` — the field's mere declaration is enough to register as a
dependency via `getDirectDependenciesToSelf()` (mirrors auth's own documented rationale: "a
declared-but-unused field of the entity's type is already a real cross-module coupling," and this is
exactly the scenario the dependency-based, not access-based, check exists to catch).

## Entities used

`com.themistra.crypto.token.TokenAllowlist` (`@Entity`, confirmed at `token/TokenAllowlist.java:40`) —
the negative-proof fixture's target. All 9 real `@Entity` classes (`attest/Attestation`,
`events/OutboxEvent`, `observation/Observation`, `provider/ProviderHealth`, `quorum/QuorumDecision`,
`screening/ScreeningResult`, `token/TokenAllowlist`, `watch/ChainCursor`, `watch/Watch`) are exercised
by the real production canary's package-wide scan, not individually referenced in code.

## Repositories used

None.

## Services used

None.

## Unit/integration tests required

In `CrossModuleEntityArchitectureTest`:

1. **`shouldPreventCrossModuleEntityImports`** — the `@ArchTest`-annotated `ArchRule` field (exact name
   per Frozen Brief Finding #8), documentation/tooling value only (does not execute under this repo's
   Surefire setup, per the established, twice-independently-confirmed finding).
2. **`shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`** (the actual named test,
   plain `@Test`) — invokes `shouldPreventCrossModuleEntityImports.check(analyzedClasses)` where
   `analyzedClasses` is scanned with `ImportOption.DoNotIncludeTests` (unlike
   `KmsSignerArchitectureTest`'s deliberate inclusion of tests — here tests **must** be excluded from
   the main scan, since the negative-proof fixture deliberately lives inside a real feature-module test
   package and would otherwise trip the production canary itself).
3. **`ruleActuallyFailsAgainstAGenuineViolation`** (AC4, the negative-proof, mirroring
   `KmsSignerArchitectureTest.bothRulesActuallyFailAgainstAGenuineViolation`'s exact pattern) — builds a
   narrow `JavaClasses` set via `new ClassFileImporter().importClasses(RogueWatchEntityReferencer.class,
   TokenAllowlist.class)` and asserts `assertThatThrownBy(() ->
   shouldPreventCrossModuleEntityImports.check(violatingClasses)).isInstanceOf(AssertionError.class)`.

No integration test needed — pure static-analysis, no Spring context, no database, matching
`KmsSignerArchitectureTest`'s own scope exactly.

## Execution order

1. Create `RogueWatchEntityReferencer` first (inside `watch`, a single field of type
   `com.themistra.crypto.token.TokenAllowlist`) — compiling it is itself a first proof that the class
   correctly creates a real bytecode-level dependency.
2. Create `CrossModuleEntityArchitectureTest` with `FEATURE_MODULES`, `featureModuleOf`, and the
   `ArchCondition`/`ArchRule` — run the real canary
   (`shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`) immediately, with
   `ImportOption.DoNotIncludeTests`, against the real codebase. Per Frozen Brief's Constraints section
   (Finding #5's triage rule): if this fails, STOP and triage per the three documented options before
   writing anything further — do not proceed past a real, unexplained red result.
3. Once step 2 is confirmed green, add the negative-proof test
   (`ruleActuallyFailsAgainstAGenuineViolation`) and confirm it correctly throws `AssertionError`
   against the narrow, fixture-including `JavaClasses` set.
4. Re-run `KmsSignerArchitectureTest` and `ResourceServerConfigIntegrationTest` unmodified (AC1/AC2) to
   confirm this task's new file introduces no interference (e.g., no accidental shared static state,
   no `@AnalyzeClasses` package-scope collision).
5. Run the full `common` package's test suite plus `watch`'s, then the full module regression, to
   confirm zero regressions elsewhere.
