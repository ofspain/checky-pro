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

## Addendum (post Phase 11) — 6 gaps raised, all verified, no code change needed

Kimi's Phase 11 review raised 6 gaps. Every factual claim was checked directly against source
before disposition (not accepted on word) — all 6 held up accurately this time, no citation errors.
No production or test code changed in this addendum.

- **Gap 1** (Kimi's own sandbox lacks Maven, so it could not itself confirm the "368 tests, 0
  failures" claim) — **re-confirmed, not a real gap**: this claim was never merely asserted - `mvn
  -pl services/notification clean verify` was actually run, with real tool output, at Phase 6 (368),
  Phase 9 (368, after the `RestTemplate` assertion fix), and Phase 10 (368, after the
  `RogueUnmappedEntity` assertion fix). Re-run once more, fresh, immediately upon receiving this
  finding: `Tests run: 368, Failures: 0, Errors: 0` (full aggregate), `Tests run: 6, Failures: 0`
  (`ArchitectureTest` alone) — no remaining uncertainty.
- **Gap 2** (`featureModuleOf`'s `startsWith` branch untested) — **re-confirmed as the same,
  already-disclosed gap this phase's own main text already covers**; Kimi's own assessment agrees
  with the disposition already given (accept, no fix).
- **Gap 3** (no negative proof for the sibling-service-package half of L2) — **verified directly**:
  `services/notification/pom.xml` has no dependency on `services/auth`, `services/crypto`, or
  `services/payment` — confirmed by direct `grep`, no such artifact coordinate exists anywhere in
  the file. No compiling fixture is possible; already disclosed in the frozen brief (Finding #4). No
  change.
- **Gap 4** (no negative-proof test for L8) — **verified directly**: re-read `services/auth`'s own
  `ArchitectureTest.java` around its identical `shouldEnforcePublicEndpointAllowlist` rule in full;
  confirmed it has only the canary (`shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`),
  no negative-proof test anywhere in that file. Matches both this task's own frozen-brief precedent
  (Finding #5) and Kimi's own claim exactly. No change.
- **Gap 5** (only one of four banned HTTP-client packages is exercised) — **acknowledged, no test
  added**, for the exact reason Kimi's own text gives: the rule is a package-pattern match, so
  proving the mechanism works for one package proves it for all four; adding three more fixtures
  for the same mechanism would be gold-plating.
- **Gap 6** (independent verification of the Surefire `@ArchTest` bug claim) — **already addressed
  in Phase 9's own resolution** (Finding 4): the claim was independently, empirically verified
  before Kimi's own review (three isolated repro classes, plus Surefire's own exact forked
  classpath fed directly into the real JUnit Platform `Launcher` API outside Surefire — see Phase 6
  notes and the `surefire-archtest-field-bug` memory). Kimi's own sandbox lacking `mvn` is a
  limitation of that sandbox, not evidence the claim itself was unverified. The repo-wide
  issue/ADR suggestion remains deferred, per the user's own explicit instruction to scope this
  task's fix to notification only.

**Verification:** `mvn -pl services/notification clean verify` — 368 tests, 0 failures, 0 errors
(unchanged from Phase 10 — this addendum made no code changes).
