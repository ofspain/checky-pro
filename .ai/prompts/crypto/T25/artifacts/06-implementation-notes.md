# crypto · T25 · Phase 6 — Implementation Notes

## What changed

**Created:**
- `services/crypto/src/test/java/com/themistra/crypto/watch/RogueWatchEntityReferencer.java` — the
  negative-proof fixture (Frozen Brief Finding #2): a class inside `watch` with a single, never-read
  field of type `token.TokenAllowlist`, a real entity in a different real feature module.
- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`
  — the named test `shouldPreventCrossModuleEntityImports` (L15), mirroring auth's own
  `ArchitectureTest` technique: `FEATURE_MODULES` (this branch's actual 11 packages),
  `featureModuleOf(JavaClass)`, an `ArchCondition` over `getDirectDependenciesToSelf()`, fail-fast on
  an unmapped module, plus the plain-`@Test` canary
  (`shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`) and the negative-proof test
  (`shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation`).

**Confirmed, unmodified:** `attest/KmsSignerArchitectureTest.java` (AC1) and
`common/ResourceServerConfigIntegrationTest.java` (AC2) — both re-run and still passing (2/2, 11/11).

## Deviations from the plan, forced by reality

**One significant, real deviation — a genuine first-run rule failure, handled per the frozen brief's
own triage rule, not silently worked around:**

Running the new canary for the first time against the real, current codebase (per the frozen brief's
explicit Constraints/triage instruction) surfaced 9 real violation lines, collapsing to exactly 2
distinct, pre-existing cross-module entity couplings that predate this task:

1. `attest.AttestationService.distinctFromAddresses(List<ChainCursor>)` — the `attest` module using
   `watch`'s `ChainCursor` entity directly (as a method parameter type and via `.fromAddress()` calls).
2. `watch.Watcher`'s `evaluateFact`/`handleSeenIfAgreed`/`handleConfirmedIfAgreed`/`pollFinalityFor` —
   the `watch` module using `quorum`'s `QuorumDecision` entity directly (as a return type, a parameter
   type, and via `.outcome()` calls).

Both are natural, functioning couplings built in earlier tasks (roughly T17-T21), not defects, and
production code changes are explicitly out of this task's own scope. Per the frozen brief's triage rule
(Finding #5) and the user's own explicit direction, both are resolved as **documented, named
exceptions** — a new `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` set (mirroring auth's own
`ALLOWED_CROSS_MODULE_CONTROLLER_SERVICE_DEPENDENCIES` pattern exactly), keyed by
`originClass.getName() + "->" + entityClass.getName()`. The rule now passes while still catching any
*new* cross-module entity coupling that isn't one of these two named, approved exceptions — the correct
middle ground between leaving the rule permanently red (no enforcement value) and silently expanding
this task's scope into an unrelated production refactor.

One implementation-only wrinkle: `Watcher` is package-private in `com.themistra.crypto.watch` and
cannot be referenced via a class literal (`Watcher.class`) from this `common`-package test. Its
allowlist entry is spelled out as a plain string literal (`"com.themistra.crypto.watch.Watcher->" +
QuorumDecision.class.getName()`) instead, sacrificing that one entry's rename-safety for the other
three components' (`AttestationService`, `ChainCursor`, `QuorumDecision`, all public).

No other deviation — the file/field/method structure otherwise matched Phase 5's plan exactly.

## Mapping to acceptance criteria

- **AC1 (R22/L11, confirm).** `KmsSignerArchitectureTest` — 2/2 pass, unmodified.
- **AC2 (R27, confirm).** `ResourceServerConfigIntegrationTest` — 11/11 pass, unmodified.
- **AC3 (L15, named test).** `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` passes
  against the real codebase, with the 2 pre-existing couplings explicitly, narrowly allowlisted (not
  silently ignored — any other cross-module entity import still fails the rule).
- **AC4 (negative-proof).** `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation`
  proves the rule genuinely throws `AssertionError` against `RogueWatchEntityReferencer` + `TokenAllowlist`
  — a real, feature-module-internal violation, not the fail-fast null path (Finding #2's own correction).

## Verification

`mvn -pl services/crypto test-compile` — clean.
`mvn -pl services/crypto test -Dtest=KmsSignerArchitectureTest,ResourceServerConfigIntegrationTest,CrossModuleEntityArchitectureTest`
— 15/15 pass (2 + 11 + 2).
