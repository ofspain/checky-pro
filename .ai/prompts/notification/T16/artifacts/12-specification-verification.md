# notification · T16 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T16 — ArchUnit module-boundary tests |
| **Consumes** | All prior T16 artifacts (Phases 0–11, including the Phase 11 addendum) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **L11** — no feature module may import an entity class from another feature module | Yes | `ArchitectureTest.java:85-111` (`shouldPreventCrossModuleEntityImports`, the literal `package.md` §8 name) | Canary (`:150-152`) + 2 negative proofs (`:171-180` genuine violation, `:186-194` fail-fast path) | No | No |
| **L8** — only `ResourceServerConfig` may declare an unauthenticated path, only via `PublicEndpoints` | Yes | `ArchitectureTest.java:117-121` (`shouldEnforcePublicEndpointAllowlist`) | Canary only (`:155-157`) | No negative proof | No — matches `services/auth`'s own identical precedent exactly, re-verified this phase |
| **L2** — consume-only, no synchronous cross-service call anywhere on the delivery path | Yes | `ArchitectureTest.java:130-137` (`shouldMakeNoSynchronousCrossServiceCall`) | Canary (`:159-161`) + 1 negative proof for the HTTP-client half (`:200-208`) | Sibling-service-package half has no compiling fixture | No — structural limitation, disclosed since Phase 2, re-verified this phase (`pom.xml` has no auth/crypto/payment dependency) |
| **AC1/AC2/AC3** — L11 named test exists, backed by canary, resolves all 7 real entities, both negative paths proven | Yes | `FEATURE_MODULES` (`:66-67`) covers all 6 modules containing the 7 real `@Entity` classes (confirmed Phase 4, re-confirmed by the canary's own clean pass every verification run) | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`, `...ActuallyFailsAgainstAGenuineViolation`, `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` | No | No |
| **AC4** — L8 named test exists, mirrors `services/auth` | Yes | `ArchitectureTest.java:117-121`, `:155-157` | `shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` | No negative proof, by precedent | No |
| **AC5** — L2 named test exists, whole-service scope, HTTP-client negative proof | Yes | `ArchitectureTest.java:130-137`, `:200-208` | `shouldMakeNoSynchronousCrossServiceCallIsCheckedDuringStandardBuild`, `...ActuallyFailsAgainstAGenuineViolation` | No | No |
| **Frozen-brief Constraint** — `@AnalyzeClasses` and every canary share one eagerly-initialized `JavaClasses`/`ImportOption` | Yes, in substance | `ArchitectureTest.java:141-147` (`analyzedClasses`/`analyzedClasses()`) | Implicit — every canary and negative proof calls `analyzedClasses()` or its own narrow importer | No | **Yes, disclosed**: the constraint's literal text names `@AnalyzeClasses`, which Phase 6 removed entirely (Surefire bug, see below); substance (one shared instance) still holds, text is stale — flagged at Phase 7/8/9, read as superseded by this class's own Javadoc |
| **Frozen-brief Files to Create** — `RogueHttpClientUser.java` at `archtestfixtures/` | No, moved | `channel/RogueHttpClientUser.java` | — | — | **Yes, disclosed**: Phase 5 moved it so it would be a valid subject for L2's rule at all (`resideInAPackage` is inclusion-shaped); flagged at Phase 7/8/9 as a FROZEN path amended without a renewed approval gate — correct on the merits, still an open process question for the user |

## Answers

**(1) Is the task fully complete?** Yes, for everything within this task's own disclosed scope.
Every file the frozen brief's "Files to Create" list named exists (one at a corrected path,
disclosed above and at every phase since Phase 5). "Files to Modify" was correctly empty. The task
went through adversarial review (Kimi Phases 3, 8, 11) plus this session's own self-review (Phase
7), with every finding fixed, correctly disposed with a stated reason, or explicitly documented as
already-correct/already-disclosed. One real, severe, previously-unknown problem was found and
fixed during this task's own Phase 6 — not left for a future task to discover, and not silently
patched over either: the mere presence of an `@ArchTest` field causes Maven Surefire 3.5.3 to
report zero tests for the *entire* class it's in, which this class avoids entirely (plain
`private static final ArchRule` fields, no `@ArchTest`, no `@AnalyzeClasses`), but which means
`services/auth`'s and `services/crypto`'s own existing architecture tests are themselves currently
silently non-enforced — raised directly to the user, fix explicitly scoped to this task only, per
the user's own instruction.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC5, see matrix above. Every
canary passes against the real, current codebase (confirmed fresh at every phase, most recently
Phase 11: `Tests run: 6, Failures: 0`), and every negative-proof/fail-fast test genuinely throws
against its own fixture, each asserting both the violating class's own name and the specific thing
it violated (DeliveryLog, RestTemplate, or the fail-fast phrase plus RogueUnmappedEntity's own name)
— strengthened twice, at Phases 9 and 10, after the original Phase 6 versions asserted only the
violating class name.

**(3) Does it violate any LOCKED decision?** No. L2, L8, L11 all hold, per the matrix. No production
code was touched by this task (test-source only), so no other LOCKED decision in this service's
`agents.md` is at risk.

**(4) Remaining risks?**
- **The L2 rule's sibling-service-package half has no negative-proof test** — structural, not an
  oversight: no compiling fixture is possible without a real dependency on auth/crypto/payment,
  which would itself violate the very rule being tested. Disclosed since Phase 2, re-verified this
  phase directly against `pom.xml`. Resolves automatically if this service ever grows a legitimate
  reason to depend on another service's source (at which point the dependency itself, not a test
  gap, would be the real problem).
- **`featureModuleOf`'s own `startsWith` (sub-package) branch has zero test coverage** — disclosed
  at Phase 10, re-confirmed at Phase 11: no entity in this service, or in `services/auth`/
  `services/crypto`'s identical helper, lives in a sub-package today. Would surface the moment any
  future entity is added to a sub-package, since the real canary re-runs on every build.
- **`services/auth`'s and `services/crypto`'s own existing architecture tests are silently
  non-enforced** by the same Surefire/`@ArchTest` mechanism this task discovered and avoided —
  the single largest remaining risk this task surfaced, deliberately left unfixed outside this
  task's own scope, per the user's explicit instruction. Recorded in memory
  (`surefire-archtest-field-bug`) so it isn't lost between sessions.
- **A FROZEN file path (Phase 4) was amended one phase later (Phase 5) without a renewed approval
  gate** — correct on the merits (confirmed independently by both self-review and Kimi), but still
  an open process question for the user about this pipeline's own freeze discipline, not yet
  answered.

## Verdict

**PASS** — T16 fully satisfies L2, L8, L11 and every acceptance criterion (AC1–AC5) it touches. All
three named rules exist, are enforced by real, verified-executing `@Test` canaries (not merely
`@ArchTest` fields, which this task discovered do not execute at all under this repository's
Surefire configuration — nor, it turns out, does anything else in the same class once one is
present), and every negative-proof/fail-fast path that can structurally exist is proven. The full
suite is green at 368 tests, 0 failures, 0 errors. Two disclosed, non-blocking process questions
(the FROZEN-path amendment, and whether to retrofit auth/crypto) remain open for the user, not for
this task to decide unilaterally.
