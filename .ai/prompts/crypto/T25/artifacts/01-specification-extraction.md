# crypto · T25 · Phase 1 — Specification Extraction

## Business Rules

- **R22.** The system SHALL invoke `kms:Sign` on the attestation key only from the attest path; no
  other module or endpoint SHALL be able to reach the signer.
- **R27.** WHEN the internal watch or attest endpoints are called, THEN the system SHALL require a
  valid service-to-service JWT bearing the `internal.crypto:write` scope and SHALL reject
  unauthenticated or under-scoped callers.

## Locked Decisions

- **L11.** KMS-only signing, single path. `kms:Sign` on the attestation key is reachable only from the
  attest module — enforced by ArchUnit (package ban) and by IAM. Already fully implemented
  (`attest/KmsSignerArchitectureTest.java`, T20) — this task's role is to confirm, not rebuild.
- **L15.** Module boundaries. Package-by-feature under `com.themistra.crypto`; no feature module
  imports another feature module's entity. Shared plumbing lives in `common`. Enforced by ArchUnit,
  mirroring the auth service. **This is the one clause with no existing implementation.**

## Files involved

**Existing, already satisfying their named test (from Phase 0 — read-only, cited not rebuilt):**
- `attest/KmsSignerArchitectureTest.java` — R22/L11's exact named test
  (`shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild`), two `ArchRule`s, a
  negative-proof fixture (`archtestfixtures.RogueAttestReferencer`). Confirmed still passing at T24
  (2/2, unmodified) as recently as this task's immediate predecessor.
- `common/ResourceServerConfigIntegrationTest.java` — R27's exact named test
  (`shouldRequireInternalScopeForWatchAndAttestEndpoints`, 3 parameterized variants ×3 routes),
  built at T03.

**Precedent, to mirror the shape/technique of (not to modify):**
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — the direct precedent L15's
  own text names ("mirroring the auth service"). `shouldPreventCrossModuleEntityImports` (lines
  104-141) is the technique: a `FEATURE_MODULES` list, `featureModuleOf(JavaClass)`, an
  `ArchCondition` over `entityClass.getDirectDependenciesToSelf()` (dependency-based, catches an
  unused-field coupling an access-based check would miss), plus a plain-`@Test` canary.

**New, this task's own deliverable (exact file name/placement a Phase 2 design decision):**
- A crypto-service equivalent of the entity-import rule, covering all 9 `@Entity` classes
  (`attest/Attestation`, `events/OutboxEvent`, `observation/Observation`, `provider/ProviderHealth`,
  `quorum/QuorumDecision`, `screening/ScreeningResult`, `token/TokenAllowlist`, `watch/ChainCursor`,
  `watch/Watch`) across all 11 feature-module packages (everything except `common`).

## Dependencies

None new. `com.tngtech.archunit` is already a test-scope dependency (used by every existing
`*ModuleBoundaryTest`/`KmsSignerArchitectureTest`). `jakarta.persistence.Entity` is already a compile
dependency (JPA, via `spring-boot-starter-data-jpa`).

## Acceptance Criteria

1. **AC1 (R22/L11, named test).** `shouldOnlyAllowAttestPathToInvokeKmsSign`-family assertions
   (`KmsSignerArchitectureTest`, unmodified) still pass, confirming the KMS-signer package ban remains
   intact and untouched by this task.
2. **AC2 (R27, named test).** `shouldRequireInternalScopeForWatchAndAttestEndpoints`-family assertions
   (`ResourceServerConfigIntegrationTest`, unmodified) still pass, confirming the internal-scope
   requirement remains intact and untouched by this task.
3. **AC3 (L15, named test, the actual new work).** `shouldPreventCrossModuleEntityImports` passes on
   the real, current codebase: no feature module's main source imports another feature module's
   `@Entity` class, verified for all 9 entities across all 11 feature-module packages, with a genuine
   negative-proof (a fixture or mutation proving the rule can actually fail).

## Tests required

- **Named tests (`package.md` §8), all three explicitly scoped to this task per the header:**
  `shouldOnlyAllowAttestPathToInvokeKmsSign` (confirm unmodified, AC1), 
  `shouldRequireInternalScopeForWatchAndAttestEndpoints` (confirm unmodified, AC2),
  `shouldPreventCrossModuleEntityImports` (build new, AC3).
- A negative-proof fixture or equivalent for the new rule, mirroring
  `archtestfixtures.RogueAttestReferencer`'s established pattern (T20) — a rule that only ever passes
  on clean code, with no fixture ever exercising its failure path, is not fully trusted per this
  codebase's own established discipline.

## Open Questions

No blockers. One genuine design question for Phase 2, not a blocker:
- **Whether the new rule lives in its own new file (e.g. `common/CrossModuleEntityArchitectureTest.java`)
  or is added to an existing, more general architecture-test home** — no such general home currently
  exists in crypto-service (unlike auth's single `ArchitectureTest.java` covering many rules); creating
  one now, scoped to only this task's one new rule, versus starting a broader consolidation is a Phase 2
  proportionality call.
