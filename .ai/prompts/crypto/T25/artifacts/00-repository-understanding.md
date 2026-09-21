# crypto · T25 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` (`services/crypto`, `com.themistra.crypto`) is package-by-feature under 12 top-level
packages: `adapter`, `attest`, `common`, `events`, `finality`, `observation`, `provider`, `quorum`,
`reorg`, `screening`, `token`, `watch`. `common` is the deliberate shared-plumbing exception (L15's own
text); the other 11 are feature modules. One Postgres schema (`chain`), Flyway migrations, Kafka via a
transactional outbox, ArchUnit-enforced boundaries (already partially built, per-module). Security is a
pure resource-server setup (T03): internal endpoints require a service-to-service JWT with
`internal.crypto:write`; the well-known verification-keys endpoint is the sole public exception
(`PublicEndpoints`).

## 2. Existing code this task touches

**Already fully implemented, pre-dating this task — the key finding of this phase:**

- **R22/L11 (KMS-signer package ban).** `attest/KmsSignerArchitectureTest.java` (built T20, tightened at
  its own Phase 9) already contains the exact rule this task needs, via two `ArchRule`s plus a
  plain-`@Test` canary already named **exactly** `shouldOnlyAllowAttestPathToInvokeKmsSignIsCheckedDuringStandardBuild`
  — one class outside `attest` may not touch `KmsSigner`; no class anywhere in `com.themistra.crypto`
  except one named `KmsSigner*` residing in `attest` may depend on the KMS SDK at all. A
  negative-proof fixture (`archtestfixtures.RogueAttestReferencer`, standalone top-level package)
  already proves both rules can genuinely fail, not just pass on clean code.
- **R27 (internal-scope requirement).** `common/ResourceServerConfigIntegrationTest.java` (built T03)
  already contains the exact named test `package.md` §8 requires, split into three parameterized
  variants: `shouldRequireInternalScopeForWatchAndAttestEndpoints_rejectsUnauthenticated`,
  `_rejectsUnderScopedToken`, `_acceptsCorrectScope` — each parameterized over the watch and attest
  internal routes (`InternalRequest`), confirmed via the actual surefire report from a prior run (9
  executions: 3 assertions × 3 parameterized routes).

**Genuinely new — the one real deliverable of this task:**

- **L15 (no cross-module entity imports).** No equivalent test exists anywhere in `services/crypto`.
  The 9 `@Entity` classes found (`attest/Attestation`, `events/OutboxEvent`, `observation/Observation`,
  `provider/ProviderHealth`, `quorum/QuorumDecision`, `screening/ScreeningResult`,
  `token/TokenAllowlist`, `watch/ChainCursor`, `watch/Watch`) currently have no ArchUnit rule
  preventing another feature module from importing one directly. The 6 existing per-module boundary
  tests (`FinalityModuleBoundaryTest`, `ProviderModuleBoundaryTest`, `ReorgModuleBoundaryTest`,
  `ScreeningModuleBoundaryTest`, `TokenModuleBoundaryTest`, `WatchModuleBoundaryTest`) each hand-roll a
  broader "no import of forbidden sibling packages at all" check specific to that one module — none of
  them is the generic, entity-scoped `shouldPreventCrossModuleEntityImports` rule L15's own wording and
  `package.md` §8's named test call for, and 5 of the 11 feature modules (`adapter`, `attest`, `events`,
  `observation`, `quorum`) have no boundary test at all today.
- **Direct precedent to mirror exactly:** `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java`
  — L15's own text says "mirroring the auth service." Its `shouldPreventCrossModuleEntityImports` rule
  (lines 104-141) is the exact technique: a `FEATURE_MODULES` list, a `featureModuleOf(JavaClass)`
  helper, an `ArchCondition` checking `entityClass.getDirectDependenciesToSelf()` (dependency-based, not
  access-based — deliberately catches a declared-but-unused field of the entity's type too, per that
  file's own documented negative-proof), plus a plain-`@Test` canary
  (`shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`) that directly invokes
  `.check(analyzedClasses())`, since `@ArchTest` fields do not execute under this repo's Surefire setup
  (already independently confirmed for crypto-service at T20).

## 3. Established patterns to follow

- **`@ArchTest` fields don't execute under this repo's Surefire config** (confirmed at T20 for
  crypto-service, and explicitly documented again in auth's own `ArchitectureTest.java`) — every real
  rule needs a companion plain-JUnit `@Test` canary that calls `.check(analyzedClasses())` directly.
- **Dependency-based checking (`getDirectDependenciesToSelf`), not access-based (`getAccessesToSelf`)**
  — auth's own file documents catching a declared-but-unused field of the forbidden type that an
  access-based check let through in its own negative-proof history.
- **Fail-fast on an unmapped module** — auth's `featureModuleOf` returns `null` for a class outside
  every listed module, and both its rules explicitly flag that as a violation rather than silently
  skipping enforcement for it (Phase 9 self-correction in that file's own history).
- **A real negative-proof fixture** proves a rule can actually fail, not just pass on clean code —
  crypto-service already has one precedent (`archtestfixtures.RogueAttestReferencer`, T20) deliberately
  placed in a standalone top-level package outside `com.themistra.crypto` so it never pollutes the real
  production canary's own package-wide scan.

## 4. Testing conventions

Unit (plain JUnit) → ArchUnit + contract → integration (Testcontainers). ArchUnit rules in this
codebase always pair an `@ArchTest`-annotated `ArchRule` field (for documentation/tooling value, IDE
plugin support) with a plain `@Test` canary that actually gates `mvn test`. No Spring context, no
Testcontainers needed for any of T25's three rules — all operate on compiled bytecode via
`ClassFileImporter`.

## 5. Known gaps / unknowns

- **Whether T25 should consolidate the KMS-signer and internal-scope proofs into one new unified
  `ArchitectureTest.java`-style file (mirroring auth's single-file convention), or leave them exactly
  where they are** (`attest/KmsSignerArchitectureTest.java`, `common/ResourceServerConfigIntegrationTest.java`)
  **and add only the new cross-module-entity rule as a new, separate file** — a Phase 2 design decision.
  Both already satisfy their respective named tests' literal requirements; moving them risks unrelated
  churn the frozen brief's own "no unrelated refactoring" guardrail would forbid.
- **Whether any of the 9 existing `@Entity` classes already have a real cross-module dependency that
  would fail a newly-built `shouldPreventCrossModuleEntityImports` rule on first run** — I do not know
  yet; this needs direct verification once the rule exists (Phase 5/6), not assumed clean.
- **Whether `watch`'s two entities (`Watch`, `ChainCursor`) being in the same module changes anything**
  — I do not believe so (both belong to the same feature module, so intra-module use is unaffected by
  L15), but this should be confirmed once the rule is built.
