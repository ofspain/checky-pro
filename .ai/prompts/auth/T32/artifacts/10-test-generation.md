<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T32 · Phase 10 — Test Generation

Test-only task convention (same as T27): the task's entire deliverable is test code, already
written and resolved across Phases 6/9 by extending `ArchitectureTest.java`. No production code
exists to test separately. This phase is purely the manifest mapping each test to what it verifies.
No new test was written in this phase.

## `ArchitectureTest.java` (extended — 3 new members)

| Test | Type | Verifies |
|---|---|---|
| `apiKeysTokenExchangeIsInThePublicAllowlist` | Plain `@Test` (JUnit Jupiter) | AC1 — `PublicEndpoints.METHOD_SCOPED` contains `POST /api-keys/token`. Fails if that entry is ever removed. |
| `shouldEnforcePublicEndpointAllowlist` (**named test, `package.md` §8**) | `@ArchTest static final ArchRule` (ArchUnit) | AC2 — no class other than `SecurityChainsConfig` calls `AuthorizedUrl.permitAll()`. Empirically proven to fire correctly on a deliberately-introduced violation and pass correctly without one (Phase 6 negative-proof). |
| `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` (renamed from `...ActuallyRunsUnderThisBuild`, Kimi Phase 11 Gap 4) | Plain `@Test` (JUnit Jupiter) | Kimi Phase 8 Finding 2's closure — directly invokes `shouldEnforcePublicEndpointAllowlist.check(...)` against a class set built by the new shared `analyzedClasses()` helper (Kimi Phase 11 Gap 1 — ties the canary's scan config to the same `ANALYZED_PACKAGE` constant `@AnalyzeClasses` uses, so they can't silently drift), so AC2 is enforced by `mvn test` today regardless of the separate, still-open Surefire/ArchUnit engine-integration gap. Re-verified via a second negative-proof run in Phase 9 (reintroduce violation → confirm real `BUILD FAILURE` → revert). Deliberately scoped to only this one rule (Kimi Phase 11 Gap 2) — documented in-code that the other 9 pre-existing rules remain un-gated until the broader Surefire issue is fixed separately. |

## Boundary/negative-proof coverage (performed, not separately shipped as tests)

- Phase 6: introduced a scratch `.permitAll()` call outside `SecurityChainsConfig`, confirmed (via
  direct JUnit Platform Launcher invocation, since Surefire alone doesn't run the ArchUnit engine)
  that `shouldEnforcePublicEndpointAllowlist` correctly fails; reverted.
- Phase 9: reintroduced the same scratch violation, confirmed the new canary test makes `mvn test`
  itself report `BUILD FAILURE` with the exact violation message; reverted.
- **Kimi Phase 11 Gap 3 closure:** AC1's negative case was performed and reverted — temporarily
  removed `/api-keys/token` from `PublicEndpoints.METHOD_SCOPED`, confirmed
  `apiKeysTokenExchangeIsInThePublicAllowlist` fails with the exact expected AssertJ diff (real
  `BUILD FAILURE`), restored the entry, confirmed clean 2/2 pass again.

## Kimi Phase 11 test review — gaps closed

Kimi's Phase 11 review (`artifacts/11-test-review.md`) found 4 gaps, all closed directly (no
dedicated resolution artifact, standing precedent for this pipeline's test-review phase):

| Gap | Disposition |
|---|---|
| Gap 1 — canary's scan config duplicates `@AnalyzeClasses` without being tied to it | Closed — extracted `ANALYZED_PACKAGE` constant (used by both the annotation and the new `analyzedClasses()` helper) and the helper itself, used by the canary. |
| Gap 2 — canary only covers T32's own rule, not the other 9 pre-existing rules | Accepted as already-decided scope (same boundary set at Phase 9 for Kimi's Finding 2) — documented explicitly in the canary's own Javadoc so it's not misread as covering the whole file. |
| Gap 3 — AC1 has no shipped/performed negative proof | Closed — performed and reverted (see above). |
| Gap 4 — canary's name is implementation-, not behavior-focused | Closed — renamed to `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild`. |

**One claim in Kimi's own review was checked and found incorrect for this environment, not
adopted:** Kimi reported `Unsupported class file major version 70` and attributed the Surefire/
ArchUnit non-execution to "ArchUnit 1.3.0's bundled ASM not supporting the Java 24 runtime." Verified
via `javap -v` against this environment's actual compiled classes: they are major version **65**
(Java 21), matching `pom.xml`'s `maven.compiler.release=21` exactly — and Phase 6's own direct
`ClassFileImporter` invocation successfully parsed these exact class files with zero ASM errors,
which rules out a bytecode-version incompatibility as the cause *in this environment*. Kimi runs in
a separate sandbox and most likely resolved a different JDK there. The higher-level symptom Kimi
observed (0 tests via Surefire) independently matches this session's own finding, but the specific
technical explanation does not apply here and was not carried into the fix or into memory as a root
cause.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ArchitectureTest'` — **2/2 pass** (the content assertion and
  the canary, both via the JUnit Jupiter engine — the canary directly exercises the named ArchUnit
  rule's own `.check()` logic; the ArchUnit engine's own separate invocation still reports 0 tests,
  the known, separately-tracked Surefire/ArchUnit gap — no longer a blind spot for this task's own
  rule specifically, since the canary test closes it).

The named `package.md` §8 test (`shouldEnforcePublicEndpointAllowlist`) is fully written, verified
correct via two independent negative-proof runs, and — as of the Phase 9 resolution — actually
enforced by this project's standard `mvn test` command.

---

**Phase 10 complete — test manifest written and updated post-Phase-11.** Proceed to Phase 12
(Specification Verification) on approval.
