# crypto · T27 · Phase 7 — Self-Review

## Files reviewed

- `services/crypto/Dockerfile`
- `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java`

## Findings and dispositions

### 1. `Dockerfile` — verified an exact structural mirror of `services/auth/Dockerfile`

Diffed both files after substituting `auth`→`crypto` in the auth original: zero differences. No
finding.

### 2. `dependOnKmsSignerUnlessAllowlisted` reported one aggregate event per class, losing per-call-site
   diagnostic detail — **fixed**

The first working version of the new `ArchCondition` added a single event per violating class
(`javaClass.getName() + " depends on ..."`), discarding the exact field/method/line detail the
original `.dependOnClassesThat().haveFullyQualifiedName(...)` rule produced (visible in Phase 6's own
first `mvn verify` run: 5 separate lines, one per field/method reference in `EndToEndIntegrationTest`).
Rewritten to iterate `getDirectDependenciesFromSelf()` and emit one event per matching dependency edge,
mirroring `CrossModuleEntityArchitectureTest.onlyBeAccessedFromTheSameFeatureModule`'s own established
per-edge pattern exactly. Re-ran `KmsSignerArchitectureTest` standalone after the change — all 3 tests
(including the negative-proof and regression-guard tests) still pass.

### 3. `noClasses()`'s negation semantics — already caught and fixed in Phase 6, re-verified here

Re-checked the reasoning documented in the class's own Javadoc against the actual extracted ArchUnit
1.3.0 source (`ArchRuleDefinition.Creator.noClasses()` wraps every condition with `negateCondition()`).
Confirmed correct, no further finding — flagging here only because self-review is the checkpoint where
a wrong assumption from implementation would otherwise go uncaught before Phase 8.

### 4. Unused imports / dead code — none found

Full `mvn -pl services/crypto -am test-compile` after all Phase 6/7 edits produces zero warnings. Every
new import (`JavaClass`, `ArchCondition`, `ConditionEvents`, `SimpleConditionEvent`, `Set`,
`assertThat`) is used.

### 5. Allowlist set shape — consistent with precedent, no finding

`ALLOWED_CROSS_MODULE_KMS_SIGNER_REFERENCES` is keyed by fully-qualified class name (a `Set<String>`),
matching `CrossModuleEntityArchitectureTest`'s own `ALLOWED_CROSS_MODULE_ENTITY_DEPENDENCIES` key
format for its one package-private entry (`Watcher`). `EndToEndIntegrationTest` is public, so a class
literal (`EndToEndIntegrationTest.class.getName()`) was considered instead of a string literal for
rename-safety - rejected: `attest`'s `KmsSignerArchitectureTest` importing a class from `watch`'s test
sources purely for this literal would itself be an unnecessary, real compile-time coupling between two
otherwise-independent feature modules' test trees, for zero behavioral gain given the regression-guard
test (`allowlistedKmsSignerCrossModuleReferenceStillExistsInCode`) already fails loudly on a stale
entry.

## Verification performed

- `mvn -pl services/crypto -am test -Dtest=KmsSignerArchitectureTest` — 3/3 passing, before and after
  the Finding #2 fix.
- `mvn -pl services/crypto -am test-compile` — clean, zero warnings, full module.
- `mvn -pl services/crypto -am verify` (re-run after Finding #2's fix, to confirm no regression against
  the full suite) — 696 tests, 0 failures, the same 14 already-disclosed Docker-only errors.
