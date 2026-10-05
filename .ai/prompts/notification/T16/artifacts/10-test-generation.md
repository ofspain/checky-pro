# notification · T16 · Phase 10 — Test Generation

T16's own tests are its production deliverable (an ArchUnit/testing-only task, per Phase 5's own
note) — all 6 were written in Phase 6 and one was strengthened in Phase 9. This phase's job was an
audit for genuine coverage gaps in the checking mechanism itself, mirroring T15's own Phase 10
audit style. One real gap found and closed (an assertion weak in the same way Phase 9 already fixed
elsewhere in this file); one real gap found and deliberately left open, disclosed below, not fixed.

## Gap found and closed in this phase

- **`shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`'s own assertion didn't name the specific
  offending class** — the same weakness Phase 9 (Finding 3) already fixed for the L2 negative-proof
  test, left unnoticed in the L11 fail-fast test at the time. Verified directly: the production
  message (`onlyBeAccessedFromTheSameFeatureModule`) is `entityClass.getName() + " is annotated
  @Entity but does not reside in any module listed in FEATURE_MODULES..."` — the fully-qualified
  class name is a real, present prefix the test never checked for. Closed by adding
  `.hasMessageContaining("RogueUnmappedEntity")` alongside the existing generic-phrase assertion, so
  an unrelated `AssertionError` that happens to contain the same generic phrase (however unlikely)
  can no longer silently pass this test for the wrong reason.

## Gap found and deliberately left open

- **`featureModuleOf`'s own `startsWith(modulePackage + ".")` branch (the sub-package case) has zero
  test coverage, direct or indirect.** Confirmed directly: all 7 real `@Entity` classes in this
  service live directly in their own top-level module package — none in a sub-package — so every
  existing test (the real canary, both negative-proof fixtures) only ever exercises the `equals`
  half of `featureModuleOf`'s own match condition, never the `startsWith` half. Checked whether this
  is unique to this task: it is not — `services/auth`'s own identical helper (line-for-line the same
  `equals(...) || startsWith(... + ".")` idiom) and `services/crypto`'s own entity set have the exact
  same characteristic; no entity in either sibling service lives in a sub-package either, and neither
  service's own architecture test exercises that branch. **Left open, not fixed**: the idiom is a
  simple, standard, already-proven-elsewhere pattern (not novel logic unique to this file), and
  closing it here would require a throwaway fixture class with no real-world counterpart anywhere in
  this repository today, for a branch no entity has ever needed. Consistent with this pipeline's own
  precedent (T15 Finding 1) for not fixing a currently-unreachable gap speculatively.

## Test manifest

| Test method | Verifies | AC / Requirement |
|---|---|---|
| `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` | L11 passes on the real, current codebase | AC1, AC3 |
| `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` | L8 passes on the real, current codebase | AC4 |
| `shouldMakeNoSynchronousCrossServiceCallIsCheckedDuringStandardBuild` | L2 passes on the real, current codebase | AC5 |
| `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` | L11 genuinely fails against a real in-module violation, naming both the violating class and the entity it wrongly depends on | AC2 |
| `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` | L11's own fail-fast path fires for an `@Entity` outside every `FEATURE_MODULES` entry, naming the specific offending class | AC2 |
| `shouldMakeNoSynchronousCrossServiceCallActuallyFailsAgainstAGenuineViolation` | L2 genuinely fails against a real HTTP-client dependency, naming both the violating class and the dependency it uses | AC5 |

## Verification

`mvn -pl services/notification test -Dtest=ArchitectureTest` — 6/6, 0 failures. `mvn -pl
services/notification clean verify` — 368 tests, 0 failures, 0 errors (unchanged count — this
phase only strengthened an existing assertion, added no new test method). No production code was
modified in this phase.
