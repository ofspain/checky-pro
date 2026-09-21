# crypto · T25 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T25 — ArchUnit/module boundaries |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Claude Sonnet |
| **Consumes** | All prior T25 artifacts + `spec/crypto-service/{requirements.md,design.md,package.md,tasks.md,agents.md}` |
| **Produces** | `artifacts/12-specification-verification.md` |

---

## Traceability Matrix

| Requirement / LOCKED decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **L15 — No cross-module entity imports** | Yes — new rule | `common/CrossModuleEntityArchitectureTest.java:98-126` | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (`:132-135`), `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` (`:146-154`), `allowlistedCrossModuleEntityDependenciesStillExistInCode` (`:167-182`) | No new code missing for the rule itself. | **Minor deviation:** two pre-existing cross-module entity couplings (`AttestationService`→`ChainCursor`, `Watcher`→`QuorumDecision`) are explicitly allowlisted rather than removed. They are documented in `CrossModuleEntityArchitectureTest.java:68-81` and in the Phase 6/8/9 artifacts as future-decoupling debt. The rule still rejects every other cross-module entity import. |
| **L11 / R22 — `kms:Sign` reachable only from attest path** | Yes — authored in earlier task, left untouched by T25 | `attest/KmsSignerArchitectureTest.java` | `shouldOnlyAllowAttestPathToInvokeKmsSign`; `bothRulesActuallyFailAgainstAGenuineViolation` | T25 does not need to add or change this rule. | None introduced by T25. |
| **R27 — Internal scope required on watch/attest endpoints** | Yes — authored in earlier task, left untouched by T25 | `common/ResourceServerConfigIntegrationTest.java` | `shouldRequireInternalScopeForWatchAndAttestEndpoints` | T25 does not need to add or change this test. | None introduced by T25. |
| **Task 25 statement** | Yes | Same evidence as above | 16/16 targeted tests pass (3 new + 2 existing ArchUnit + 11 existing resource-server integration) per Phase 10. | Full `mvn -pl services/crypto verify` is blocked by an unrelated `ProviderAnswer` class collision in current `main`, not by T25. | None beyond the documented L15 allowlist. |

---

## Principal Engineer Review

### 1. Is the task fully complete?

**Yes.** T25's deliverables are:
- A new ArchUnit rule enforcing L15 (`CrossModuleEntityArchitectureTest`).
- A real negative-proof fixture placed inside a feature module (`RogueWatchEntityReferencer`).
- A regression guard that locks the two allowlisted cross-module couplings.
- Confirmation that `KmsSignerArchitectureTest` (R22/L11) and `ResourceServerConfigIntegrationTest` (R27) still pass and were not modified.

### 2. Does it satisfy every acceptance criterion?

The task statement requires enforcing three boundaries. Each has a corresponding, passing named test:
- `shouldPreventCrossModuleEntityImports` → L15
- `shouldOnlyAllowAttestPathToInvokeKmsSign` → R22/L11
- `shouldRequireInternalScopeForWatchAndAttestEndpoints` → R27

The new L15 test suite includes the named canary, a genuine-failure negative proof, and the allowlist-regression guard, satisfying the criterion. The other two tests were already green and remain green.

### 3. Does it violate any LOCKED decision?

- **L11/R22:** No violation.
- **R27:** No violation.
- **L15:** The literal text forbids any feature module from importing another feature module's entity. The implementation contains two explicit, named exceptions. They are not silently allowed; they are allowlisted, documented, and guarded by a regression test. This is a **recorded, accepted deviation** rather than a silent violation, but it does mean L15 is not enforced without exceptions until the future decoupling task removes these couplings.

### 4. Remaining risks

- **Main-branch build health:** `mvn -pl services/crypto verify` currently fails on `main` due to a `ProviderAnswer` class collision between the `feat` and `spec` stacks, unrelated to T25. The T25 tests themselves pass in isolation, but the full module build cannot be verified until that collision is resolved.
- **Allowlist string-literal fragility:** The `Watcher` allowlist entry uses a fully-qualified string name. A rename would stop matching silently, but the regression guard closes this gap by failing when the name no longer resolves.
- **Future decoupling debt:** The two allowlisted couplings need a follow-up task to pass DTOs/derived values instead of entities, so L15 can be enforced without exceptions.
- **Negative-proof coverage gaps:** The test review (Phase 11) noted optional additional negative proofs (fail-fast null-module path, `common`-package import, non-field dependency shapes). These are not required to satisfy the spec but would harden the test suite.

---

## Verdict

**PASS** — T25 delivers the new L15 ArchUnit rule with a genuine negative-proof and an allowlist-regression guard, while leaving the previously implemented R22/L11 and R27 tests intact and passing; the only deviation is the explicitly allowlisted, documented, pre-existing L15 exceptions that are tracked as future decoupling debt.
