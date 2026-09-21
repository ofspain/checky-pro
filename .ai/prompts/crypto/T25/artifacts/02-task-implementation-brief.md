# crypto · T25 · Phase 2 — Task Implementation Brief

## Task

Confirm the two already-implemented ArchUnit/security guarantees (R22/L11 KMS-signer package ban, R27
internal-scope requirement) remain intact, and build the one genuinely missing guarantee: a real
ArchUnit rule enforcing L15's "no feature module imports another feature module's entity," mirroring
auth-service's own `ArchitectureTest.shouldPreventCrossModuleEntityImports` technique.

## Purpose

Closes the last of three module-boundary/security guarantees this spec package requires. Two of the
three (R22/L11, R27) were already built in earlier tasks (T20, T03) for reasons specific to those
tasks' own scope, not because this task's own concern was anticipated — this task's job for those two is
verification, not construction. The third (L15) has never been built: the 6 existing per-module
"boundary" tests are plain text-based source scans, explicitly noted in their own code as a stand-in
("T09 Phase 11 deferred introducing ArchUnit itself to a future dedicated task") — this is that task,
scoped specifically to L15's own narrower concern (entity imports), not a general migration of those 6
tests to ArchUnit.

## Scope

**In:**
- A new test file, `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`,
  built using real ArchUnit (`com.tngtech.archunit`, already a test-scope dependency), mirroring
  `attest/KmsSignerArchitectureTest.java`'s established one-rule-per-file, `ArchRule` +
  plain-`@Test`-canary convention (since `@ArchTest` fields do not execute under this repo's Surefire
  setup, confirmed at T20) and auth's `ArchitectureTest.shouldPreventCrossModuleEntityImports`'s exact
  technique:
  - A `FEATURE_MODULES` list naming all 11 feature packages (`adapter`, `attest`, `events`, `finality`,
    `observation`, `provider`, `quorum`, `reorg`, `screening`, `token`, `watch`) — `common` excluded
    (L15's own shared-plumbing exception).
  - `featureModuleOf(JavaClass)` — returns the owning module or `null`.
  - An `ArchCondition` over `classes().that().areAnnotatedWith(Entity.class)`, checking
    `getDirectDependenciesToSelf()` (dependency-based, not access-based — auth's own file documents an
    access-based first attempt missing a declared-but-unused field of the forbidden type).
  - Fail-fast on `null` (a class not in any listed module) — auth's own established, deliberate choice,
    not a silent skip.
  - The named test as a plain `@Test` canary: `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`.
  - A negative-proof fixture (new class in the existing standalone `archtestfixtures` top-level package,
    T20's precedent) — a class outside every real feature module that imports one real entity across a
    module boundary, proving the rule can genuinely fail.
- Confirm (re-run unmodified, no code change): `attest/KmsSignerArchitectureTest.java` (R22/L11) and
  `common/ResourceServerConfigIntegrationTest.java` (R27) both still pass.

**Out:**
- Any change to `attest/KmsSignerArchitectureTest.java` or `common/ResourceServerConfigIntegrationTest.java`.
- Migrating the 6 existing text-scan `*ModuleBoundaryTest.java` files to ArchUnit, or touching them at
  all — a different, broader concern (arbitrary forbidden-package imports) than L15's specific,
  narrower entity-import ban; out of this task's literal scope.
- Any production code change — confirmed at Phase 0/1 that no current entity import violates L15 (to be
  verified, not assumed, once the rule exists).
- Consolidating `KmsSignerArchitectureTest`/`ResourceServerConfigIntegrationTest`/the new file into one
  mega `ArchitectureTest.java` (auth's own single-file convention) — this codebase's own established
  precedent (T20) is one rule per file; no reason found to deviate for this task.

## Business Rules

- **R22.** `kms:Sign` reachable only from the attest path — confirm only, unmodified.
- **R27.** Internal watch/attest endpoints require `internal.crypto:write` — confirm only, unmodified.

## Locked Decisions

- **L11.** KMS-only signing, single path, ArchUnit-enforced — already implemented; confirmed unmodified.
- **L15.** No feature module imports another feature module's entity, ArchUnit-enforced, mirroring
  auth — the new deliverable.

## Dependencies

None new. `com.tngtech.archunit` and `jakarta.persistence.Entity` are both already present.

## Inputs

None (no HTTP/CLI entry point — pure static-analysis test).

## Outputs

None (test-only; no production behavior changes).

## State Changes

None.

## Files to Create

- `services/crypto/src/test/java/com/themistra/crypto/common/CrossModuleEntityArchitectureTest.java`
- `services/crypto/src/test/java/archtestfixtures/RogueCrossModuleEntityReferencer.java` (negative-proof
  fixture, exact name a Phase 5 detail)

## Files to Modify

None.

## Files NOT to Modify

- `attest/KmsSignerArchitectureTest.java`, `common/ResourceServerConfigIntegrationTest.java` — cited/
  re-run as proof, not touched.
- Every existing `*ModuleBoundaryTest.java` (Finality, Provider, Reorg, Screening, Token, Watch) — a
  different, broader, pre-existing concern, out of this task's scope.
- Every `@Entity` class and every feature module's production code — Phase 0/1 found no anticipated
  violation; if the new rule surfaces a real one, that is an Open Question for Phase 4/9, not something
  to silently fix here.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R22/L11, confirm).** `KmsSignerArchitectureTest` — 2/2 tests pass, unmodified.
- **AC2 (R27, confirm).** `ResourceServerConfigIntegrationTest` — all parameterized
  `shouldRequireInternalScopeForWatchAndAttestEndpoints_*` variants pass, unmodified.
- **AC3 (L15, named test, new).** `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`
  passes against the real, current codebase — no feature module's main source imports another feature
  module's `@Entity` class, verified for all 9 entities across all 11 feature-module packages.
- **AC4 (negative-proof).** The new negative-proof fixture, when included in the scanned package,
  causes the rule to genuinely fail — proving AC3 isn't vacuously true.

## Required Tests

- **Named test (`package.md` §8):** `shouldPreventCrossModuleEntityImports` → L15/AC3.
- The negative-proof fixture's own failing-case demonstration (AC4) — proven during implementation
  (Phase 6), not left as an assumption.

## Constraints

- **Thread-safety/transaction:** None applicable — pure static-analysis test, no Spring context, no
  database.
- **Module boundaries:** The new test lives in `common` (cross-cutting concern, matches L15's own
  "shared plumbing lives in common" framing and mirrors where a service-wide architecture check
  belongs); the negative-proof fixture lives in the existing standalone `archtestfixtures` top-level
  package (T20 precedent), never inside `com.themistra.crypto` itself, so it can never pollute the real
  production scan.
- **Null handling:** N/A — no new production code.
- **Security:** None beyond what AC1/AC2 re-confirm.
- **Fail-fast, not silent-skip:** A class outside every listed `FEATURE_MODULES` entry must fail the
  rule loudly (mirrors auth's own established choice), not be silently unenforced.

## Open Questions

No blockers.
