STATUS: FROZEN

# crypto · T25 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

6 of 8 findings accepted; 2 (Findings #1, #4) verified not applicable to this branch's actual state
before disposition — their cited evidence (a `dev` controller, a `chain` package) exists only on
`main`'s separate, poisoned history (the `feat` stack, unrelated to this task), not on
`spec/service-specs-and-ai-framework`, confirmed directly: this branch has exactly the 12 packages
identified at Phase 0, compiles cleanly, and both `KmsSignerArchitectureTest`/
`ResourceServerConfigIntegrationTest` pass (13/13) as of this freeze.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | "No existing violations" assumption false in current `main` (`dev/DevWatchController` imports `Watch`; `main` doesn't compile) (HIGH) | **NOT APPLICABLE (verified)** | `dev/` does not exist on this branch; this branch compiles cleanly and AC1/AC2 both pass 13/13 as of this freeze. The underlying discipline Kimi's recommendation points at — don't declare AC3 done without a real first run — is retained anyway (see Finding #5's resolution). |
| 2 | Negative-proof fixture placed outside every `FEATURE_MODULES` entry only proves the fail-fast null path, not the actual cross-module-entity violation | **ACCEPTED** | The fixture is now specified as a test-only class **inside** a real feature module (`watch`) importing a **different** real feature module's real entity (`token.TokenAllowlist`) — this exercises the actual `entityModule != dependingModule` violation path, not the null/fail-fast path. |
| 3 | Ambiguity about whether `common` may import feature-module entities | **ACCEPTED (docs only)** | Explicitly stated: `common` receives no exemption — mirroring auth's own unmodified `ArchitectureTest` behavior exactly (`featureModuleOf` returns `null` for any `common`-package class, and the entity condition's `entityModule.equals(dependingModule)` check is `false` for a `null` `dependingModule`, so a `common`-package class importing any feature-module entity is already, correctly, a violation with zero special-casing needed). No `ArchCondition` change from the original plan. |
| 4 | `FEATURE_MODULES` doesn't account for the `feat` stack's `chain` package | **NOT APPLICABLE (verified)** | `chain/` does not exist on this branch (confirmed via direct `find`). The `FEATURE_MODULES` list correctly reflects this branch's actual 11 feature-module packages. |
| 5 | No defined handling for a violation discovered after the brief is frozen | **ACCEPTED** | Added explicit triage rule (below, under Constraints) for a first-run AC3 failure. |
| 6 | AC2 re-run is environment-dependent (Spring context/Docker) | **ACCEPTED** | AC2's scope clarified: a confirmation run in an environment where the service compiles and the Spring context starts (already true of this environment, confirmed 13/13 at this freeze) — not a new blocker if a future environment lacks that. |
| 7 | Relationship with the 6 existing text-scan `*ModuleBoundaryTest.java` files unspecified | **ACCEPTED** | Explicitly stated in Scope/Out below: they remain untouched and continue to guard their own, broader, pre-existing concern. |
| 8 | `ArchRule` field name not pinned to match `package.md`'s named test | **ACCEPTED** | Field named exactly `shouldPreventCrossModuleEntityImports`; canary named `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (unchanged from Phase 2). |

## Task

Confirm R22/L11 and R27 remain intact (unmodified re-run), and build one new file,
`common/CrossModuleEntityArchitectureTest.java`, enforcing L15 — no feature module imports another
feature module's `@Entity` class — mirroring auth-service's own `ArchitectureTest` technique exactly.

## Purpose

Unchanged from Phase 2: closes the last of three module-boundary/security guarantees this spec package
requires. R22/L11 and R27 are verification-only (already built at T20/T03); L15 is the one genuinely new
deliverable, closing a gap T09 itself explicitly deferred ("introducing ArchUnit itself to a future
dedicated task").

## Scope

**In:**
- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`:
  - `FEATURE_MODULES` = `{adapter, attest, events, finality, observation, provider, quorum, reorg,
    screening, token, watch}` — this branch's actual 11 feature packages (verified at Phase 0/3, not
    `main`'s separate `feat`-stack packages).
  - `featureModuleOf(JavaClass)` — returns the owning module or `null`; `common` is deliberately
    excluded and receives no special exemption in the entity rule itself (Finding #3).
  - `ArchRule shouldPreventCrossModuleEntityImports` (exact name, Finding #8) — a `classes().that()
    .areAnnotatedWith(Entity.class).should(...)` rule using an `ArchCondition` over
    `getDirectDependenciesToSelf()` (dependency-based, catches an unused-field coupling), fail-fast
    (reports a violation, does not silently skip) for any class in a `null`/unmapped module.
  - Plain-`@Test` canary `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`, since
    `@ArchTest` fields don't execute under this repo's Surefire setup (T20 precedent).
  - A negative-proof fixture (Finding #2): a test-only class **inside** `watch` importing
    `token.TokenAllowlist` (a real entity in a different real feature module), plus a `@Test` proving
    the rule genuinely fails against it when scoped narrowly, mirroring
    `KmsSignerArchitectureTest.bothRulesActuallyFailAgainstAGenuineViolation`'s exact pattern.
- Confirm (re-run unmodified): `attest/KmsSignerArchitectureTest.java` (R22/L11),
  `common/ResourceServerConfigIntegrationTest.java` (R27) — both already pass 13/13 combined as of this
  freeze.

**Out:**
- Any change to `attest/KmsSignerArchitectureTest.java` or `common/ResourceServerConfigIntegrationTest.java`.
- The 6 existing text-scan `*ModuleBoundaryTest.java` files (Finding #7) — remain untouched, continue
  to guard their own broader, pre-existing, different concern (arbitrary forbidden-package imports).
- Any production code change — Phase 0/3 confirmed no current entity import violates L15 on this
  branch's actual codebase.
- Consolidating existing architecture/security tests into one mega-file.

## Business Rules

- **R22.** `kms:Sign` reachable only from the attest path — confirm only, unmodified.
- **R27.** Internal watch/attest endpoints require `internal.crypto:write` — confirm only, unmodified.

## Locked Decisions

- **L11.** KMS-only signing, single path, ArchUnit-enforced — already implemented; confirmed unmodified.
- **L15.** No feature module imports another feature module's entity, ArchUnit-enforced, mirroring
  auth — the new deliverable; `common` receives no exemption (Finding #3).

## Dependencies

None new. `com.tngtech.archunit` and `jakarta.persistence.Entity` are both already present.

## Inputs / Outputs / State Changes

Unchanged from Phase 2: no inputs, no state changes, test-only output.

## Files to Create

- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`
- `services/crypto/src/test/java/archtestfixtures/RogueWatchEntityReferencer.java` (negative-proof
  fixture — placed *inside* `com.themistra.crypto.watch` per Finding #2's resolution; the exact package
  is a Phase 5 detail, but it must be inside a real `FEATURE_MODULES` entry, not the standalone
  `archtestfixtures` package used by T20's own KMS fixture, since this fixture's whole point is to be
  seen as belonging to `watch` by `featureModuleOf`)

## Files to Modify

None.

## Files NOT to Modify

- `attest/KmsSignerArchitectureTest.java`, `common/ResourceServerConfigIntegrationTest.java`.
- Every existing `*ModuleBoundaryTest.java` (Finding #7).
- Every `@Entity` class and every feature module's production code.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R22/L11, confirm).** `KmsSignerArchitectureTest` — 2/2 pass, unmodified.
- **AC2 (R27, confirm, environment-scoped per Finding #6).** `ResourceServerConfigIntegrationTest` — all
  parameterized variants pass, unmodified, in an environment where the service compiles and the Spring
  context starts (already true here: 11/11 confirmed at this freeze).
- **AC3 (L15, named test, new).** `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`
  passes against the real, current codebase (11 feature modules, 9 entities) with zero violations.
- **AC4 (negative-proof, corrected per Finding #2).** The fixture, placed inside `watch` and importing
  `token.TokenAllowlist`, causes the rule to genuinely fail when scoped to just the fixture and its
  target entity — proving AC3 tests the actual cross-module-entity scenario, not just the fail-fast
  null path.

## Required Tests

- **Named test (`package.md` §8):** `shouldPreventCrossModuleEntityImports` → L15/AC3.
- The negative-proof fixture's own failing-case demonstration (AC4).

## Constraints

Unchanged from Phase 2, plus (Finding #5) an explicit first-run triage rule: if AC3 fails on its first
real run against the actual codebase, the failure must be triaged as one of (a) a real violation, fixed
in code (would itself become a new Open Question requiring sign-off, since "no production code change"
is this task's own scope boundary), (b) an incomplete `FEATURE_MODULES` list, corrected to include a
new/relocated module, or (c) a deliberate, documented exception recorded in this artifact. AC3 is not
considered passing until the rule is green or a documented exception is explicitly approved — Phase 0/3
found no evidence of (a) or (b) being needed on this branch's actual codebase, so a clean first run is
expected, but this must still be verified during Phase 6, not assumed.

## Open Questions

No blockers.
