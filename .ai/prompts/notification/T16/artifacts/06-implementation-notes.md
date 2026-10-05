# notification · T16 · Phase 6 — Implementation Notes

Implemented per the Phase 5 plan, with one deviation forced by a real, empirically-confirmed
Maven Surefire bug discovered while verifying the first build of this task's own deliverable —
disclosed in full below, not silently patched around.

## Files created

- `ArchitectureTest.java` — three rules (`shouldPreventCrossModuleEntityImports` /L11/,
  `shouldEnforcePublicEndpointAllowlist` /L8/, `shouldMakeNoSynchronousCrossServiceCall` /L2/), each
  backed by a plain `@Test` canary calling `rule.check(analyzedClasses())`, plus three
  negative-proof/fail-fast tests exercising each rule's own fixture.
- `channel/RogueChannelEntityReferencer.java`, `channel/RogueHttpClientUser.java`,
  `archtestfixtures/RogueUnmappedEntity.java` — the three fixtures the plan specified, all `public`
  (corrected from an initial package-private draft once `services/crypto`'s own
  `RogueWatchEntityReferencer`/`RogueAttestReferencer` were re-read and confirmed `public` — a
  cross-package class-literal reference from `ArchitectureTest` requires it).

## Files modified

None.

## Deviation from the plan: no `@ArchTest` fields, no `@AnalyzeClasses` annotation

The Phase 5 plan called for `@ArchTest`-annotated fields (mirroring both sibling services) with
plain `@Test` canaries as a documented backup, on the understood premise that `@ArchTest` fields
alone don't execute under this repo's Surefire setup. Running the first build of this file surfaced
something worse: `mvn test -Dtest=ArchitectureTest` reported **`Tests run: 0`** - not just for the
`@ArchTest` fields, but for the whole class, including the plain `@Test` canaries meant to be the
real enforcement.

Root-caused by direct reproduction, not inference:
- Three minimal one-purpose classes, each run through a real `mvn test`: a class with
  `@AnalyzeClasses` + a plain `@Test` only (no `@ArchTest` field) → `Tests run: 1`, correct. A class
  with only an `@ArchTest` field → `Tests run: 0`. Both together in one class → `Tests run: 0` for
  the *entire* class, canary included.
- Confirmed this is not a discovery problem: feeding Surefire's own exact forked classpath (199
  jar entries, extracted from its generated args file) straight into the real
  `org.junit.platform.launcher.Launcher` API, outside Surefire entirely, discovers and executes all
  17 tests (6 plain `@Test` + 11 `@ArchTest`-engine tests, across both existing precedents and the
  repro classes) correctly, every time.
- Conclusion: Maven Surefire 3.5.3's `JUnitPlatformProvider` cannot correctly report results for a
  class containing an `@ArchTest` field - the mere presence of one zeroes out reporting for the
  whole class, silently, with no build failure.

This class now declares each rule as a plain `private static final ArchRule` - no `@ArchTest`
annotation, no `@AnalyzeClasses` on the class (confirmed inert without an `@ArchTest` field present)
- and relies exclusively on the plain `@Test` canaries, which is the only shape confirmed, by direct
empirical reproduction, to actually run. Verified: `Tests run: 6, Failures: 0` for this class alone,
and 368/0/0 for the full suite.

**This also means `services/auth`'s own `ArchitectureTest.java` and `services/crypto`'s own
`CrossModuleEntityArchitectureTest.java`/`KmsSignerArchitectureTest.java` - which all use the
`@ArchTest`-field-plus-canary shape this finding breaks - have never actually enforced their rules
in CI**, despite their own Javadocs claiming the canary is a working backup. Raised to the user
directly as a standalone finding; fixing those two services is explicitly out of this task's own
scope, by the user's own direction.

## Mapping to acceptance criteria

- **AC1/AC2/AC3**: `shouldPreventCrossModuleEntityImports` exists, passes against the real
  codebase (zero pre-existing violations), and genuinely fails against
  `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation`'s own fixture pair
  and `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule`'s own fixture.
- **AC4**: `shouldEnforcePublicEndpointAllowlist` exists and passes; no negative-proof test, matching
  both `services/auth`'s own precedent and Phase 4's own disposition (Finding #5).
- **AC5**: `shouldMakeNoSynchronousCrossServiceCall` exists, passes, and genuinely fails against
  `shouldMakeNoSynchronousCrossServiceCallActuallyFailsAgainstAGenuineViolation`'s own fixture.

## Verification

- `mvn -pl services/notification test-compile` — clean.
- `mvn -pl services/notification test -Dtest=ArchitectureTest` — `Tests run: 6, Failures: 0, Errors: 0`.
- `mvn -pl services/notification clean verify` — 368 tests, 0 failures, 0 errors (362 + 6 new).
