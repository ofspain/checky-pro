# crypto · T25 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T25 — ArchUnit/module boundaries |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Scope reminder:** Challenge the Phase 2 task implementation brief before it is frozen. Findings only.

---

## Findings

### 1. The "no existing violations" assumption is false in current `main`

- **Severity:** High
- **Evidence:** `services/crypto/src/main/java/com/themistra/crypto/dev/DevWatchController.java` imports `com.themistra.crypto.watch.Watch`, which is annotated with `@Entity`. The proposed `FEATURE_MODULES` list does not include `dev`, so the new ArchUnit rule would flag this as a violation if run against current `main`. Separately, `main` does not currently compile because the `feat` stack's non-generic `com.themistra.crypto.quorum.ProviderAnswer` collides with the generic version the spec stack expects, making AC1/AC2 re-runs impossible until that is resolved.
- **Recommended brief amendment:** Add an explicit prerequisite: "Before AC3 can be evaluated, the codebase must (a) compile cleanly and (b) have the `feat`/`spec` stack collision resolved so that `dev`, `chain`, and any other non-feature packages either classify under a feature module or are removed. If the first rule run surfaces violations, record them as Open Questions for the Phase 4/9 gate rather than silently fixing them, and do not freeze the brief until their disposition is known."

### 2. The negative-proof fixture may not exercise the specific L15 violation

- **Severity:** Medium
- **Evidence:** The brief describes the fixture as "a class outside every real feature module that imports one real entity across a module boundary." If the fixture is outside every listed `FEATURE_MODULES` entry, `featureModuleOf(origin)` returns `null`. The rule will fail because of its fail-fast null check, not because a feature module imported another feature module's entity. That proves the rule can fail, but not for the exact cross-module-entity-import scenario L15 targets.
- **Recommended brief amendment:** Require the negative proof to be a class *inside* one real feature module that imports an entity from a different real feature module (e.g., a test-only class in `com.themistra.crypto.watch` importing `com.themistra.crypto.token.TokenAllowlist`). Add a second fixture if the fail-fast null path also needs independent proof. Also explicitly require a separate `@Test` that imports only the fixture + target entity into a narrow `JavaClasses` set and asserts `AssertionError`, mirroring `KmsSignerArchitectureTest.bothRulesActuallyFailAgainstAGenuineViolation`.

### 3. Ambiguity about whether `common` and other non-feature packages may import entities

- **Severity:** Medium
- **Evidence:** The brief mirrors auth's technique: it excludes `common` from `FEATURE_MODULES` and fail-fasts when `featureModuleOf(dependingClass)` is `null`. Any class in `common`, `config`, `dev`, or a future top-level package that imports a feature-module entity will be flagged. `agents.md` says only that feature modules may not import each other's entities; it does not explicitly forbid `common` from doing so.
- **Recommended brief amendment:** State explicitly whether shared-plumbing packages (`common` and any other non-feature top-level packages) are allowed to depend on feature-module entities. If yes, update the `ArchCondition` to treat dependencies from those packages as satisfied. If no, state that shared plumbing must not depend on any feature-module entity and that this is intentional.

### 4. The `FEATURE_MODULES` list does not account for the `feat` stack's `chain` package

- **Severity:** Medium
- **Evidence:** Current `main` contains `com.themistra.crypto.chain` (from `feat`) alongside `com.themistra.crypto.adapter` (from `spec`). The proposed `FEATURE_MODULES` list includes `adapter` but not `chain`. Classes in `chain` that import entities will be flagged as outside any module; conversely, an entity placed in `chain` would trigger the fail-fast null check.
- **Recommended brief amendment:** Add a note: "The `FEATURE_MODULES` list assumes the `spec` adapter stack is the chosen Crypto Service implementation. If `com.themistra.crypto.chain` or any other top-level production package remains in `main`, either add it to `FEATURE_MODULES` or remove it before AC3 is evaluated."

### 5. No defined handling for violations discovered after the brief is frozen

- **Severity:** Medium
- **Evidence:** The brief says "if the new rule surfaces a real one, that is an Open Question for Phase 4/9, not something to silently fix here." It does not say who decides, how the AC is updated, or whether the brief can be frozen while AC3 is still red.
- **Recommended brief amendment:** Add a Phase 4/9 triage rule: "If AC3 fails on first run, the failure must be triaged as one of: (a) a real violation fixed in code, (b) an incomplete `FEATURE_MODULES` list updated to include a new/relocated module, or (c) a deliberate design exception approved by the author/owner and recorded in this artifact. AC3 is not considered passing until the rule is green or a documented exception is approved."

### 6. AC2 re-run of `ResourceServerConfigIntegrationTest` is environment-dependent

- **Severity:** Low
- **Evidence:** `ResourceServerConfigIntegrationTest` is a Spring integration test. Re-running it requires a compilable module and a Spring context that can start; earlier task notes showed full-regression failures caused by Docker/Testcontainers unavailability, not by code defects. T25's own deliverable does not change that test, but requiring it to pass gates T25 on an unrelated environmental condition.
- **Recommended brief amendment:** Clarify that AC2 is a confirmation run in an environment where the service compiles and the Spring context starts. If the environment lacks Docker/Testcontainers, record the limitation and re-run AC2 when available, rather than blocking T25 on infrastructure that this task does not touch.

### 7. Relationship with existing text-scan `*ModuleBoundaryTest.java` files is unspecified

- **Severity:** Low
- **Evidence:** Six existing module-boundary tests (e.g., `WatchModuleBoundaryTest`) already scan `import` statements and effectively forbid entity imports by not allowing them in their allow-lists. The new ArchUnit rule is narrower (entity imports only) and automatic. Both will coexist after T25.
- **Recommended brief amendment:** Add a sentence: "The existing text-scan `*ModuleBoundaryTest.java` files remain in scope and continue to guard arbitrary package imports; this task adds an ArchUnit rule specifically for cross-module entity imports. Do not delete or weaken the existing tests as part of this task."

### 8. Named-test/canary naming mismatch with `package.md`

- **Severity:** Low
- **Evidence:** `package.md` §8 names the test `shouldPreventCrossModuleEntityImports`. The brief names the canary `@Test` method `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` but does not explicitly name the `ArchRule` field.
- **Recommended brief amendment:** Name the `ArchRule` field exactly `shouldPreventCrossModuleEntityImports` to match the spec's named test, and keep the canary as `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`. This preserves traceability without changing the method that gates the build.

---

## Confirmations (no challenge)

- Reusing auth's dependency-based (`getDirectDependenciesToSelf`) rather than access-based approach is the correct choice for catching declared-but-unused entity fields.
- Fail-fast on `null` (a class outside every listed module) is consistent with auth's established design and prevents silent non-enforcement.
- One-rule-per-file matches the crypto service's own T20 precedent (`KmsSignerArchitectureTest`).
- Placing the negative-proof fixture in the standalone `archtestfixtures` package keeps it outside the production scan.
