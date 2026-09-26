# notification · T01 · Phase 10 — Test Generation

## Scope

None authored fresh in this phase. Like every T01-equivalent in this pipeline so far, this task's own
regression-guard tests were added directly as implementation (Phase 6) and review-resolution (Phase 7,
Phase 9) work, not deferred to this phase.

## What test code this task actually touched, and where

`services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` — the
one test file this task authors, built up across three phases:

- **Phase 6:** all 6 original methods (root pom registration, dependency presence + issuer exclusion,
  finalName/Flyway plugin, bare Application class, real `SesV2Client` construction, cross-service
  version alignment).
- **Phase 7 (self-review):** tightened the `postgresql` artifactId check to a groupId+artifactId pair
  (a plain `.contains()` couldn't tell the runtime driver and the Testcontainers module apart).
- **Phase 9 (Kimi Phase 8 review resolution):** tightened `sesv2`/`kafka`/`junit-jupiter` checks to
  groupId+artifactId pairs; added the Flyway plugin's own lifecycle-binding guard (`<executions>`
  absence) and scoped its version check to the plugin's own extracted block; added negative assertions
  against auth-specific dependencies (ShedLock, bucket4j, jackson-yaml) this task's scope excludes;
  added a dependency-scope check (`runtimeAndTestScopesAreCorrect`); extended the cross-service
  version-alignment check to the Flyway plugin version.

## Traceability

| Test method | Guards | Closes |
|---|---|---|
| `rootPomRegistersNotificationServiceAfterAuthAndCrypto` | Root `pom.xml` module registration/order | AC1 |
| `notificationPomDeclaresTheRequiredDependenciesAndExcludesTheIssuerStarter` | Every required dependency present (by groupId+artifactId where ambiguous), issuer starter and auth-specific dependencies absent | AC2; Kimi Phase 8 Findings #2/#3/#4 |
| `runtimeAndTestScopesAreCorrect` | `postgresql`/`micrometer-registry-prometheus` runtime scope, all 8 test-only dependencies' test scope | Kimi Phase 8 Finding #6; Kimi Phase 11 Gaps #2/#3 |
| `noExtraProductionClassesExistBeyondTheBareApplicationClass` | No premature production class beyond the skeleton | Kimi Phase 11 Gap 5 |
| `awsSdkBomIsImportedNotMerelyDeclaredAtTheRightVersion` | AWS SDK BOM is a real `dependencyManagement` import (type=pom, scope=import), not just a version match | Kimi Phase 11 Gap 6 |
| `finalNameAndFlywayPluginMirrorTheSiblingConvention` | `finalName`, Flyway plugin version (scoped), schema, no lifecycle binding | Kimi Phase 3 Findings #5/#8; Kimi Phase 8 Findings #1/#5 |
| `applicationClassIsBareWithOnlyTheMainMethod` | Bare skeleton class, real `main` method, no premature annotations | AC3/AC4; Kimi Phase 3 Findings #3/#6 |
| `sesV2ClientCanActuallyBeConstructed` | Real SDK client construction (HTTP-client-on-classpath proof) | Kimi Phase 3 Finding #4 |
| `sharedDependencyVersionsStayAlignedWithAuthAndCrypto` | Cross-service version drift (Testcontainers, ArchUnit, Awaitility, AWS BOM, Flyway plugin) | Kimi Phase 3 Finding #7; Kimi Phase 8 Finding #7 |

## What stands in for "AC4" here

`mvn -pl services/notification -am verify`, run fresh at Phases 6, 7, and 9: stable result — 7 tests, 0
failures, clean `package`/`repackage`. A separate `mvn -q compile` across the full three-module reactor
confirmed the root pom edit didn't disturb `services/auth` or `services/crypto`.

## Phase 11 (Kimi Test Review) additions

Kimi's Phase 11 pass (`artifacts/11-test-review.md`) raised 6 gaps, all about this same file, as
predicted above.

| # | Gap | Disposition |
|---|---|---|
| 1 | Root pom ordering only checked the last pair (crypto→notification), not the full chain | **Accepted.** `rootPomRegistersNotificationServiceAfterAuthAndCrypto` now chains auth→crypto→notification. |
| 2 | `micrometer-registry-prometheus` runtime scope unguarded | **Accepted.** Added to `runtimeAndTestScopesAreCorrect`. |
| 3 | Test-scoped dependencies' scopes not verified | **Accepted.** Added a `Coordinate` list of all 8 test-only dependencies, asserted in a loop. Fixing this exposed a real regex bug: `archunit-junit5`/`awaitility` carry an explicit `<version>` between `<artifactId>` and `<scope>` (they're not managed by the parent BOM), which the original `dependencyScope` pattern didn't tolerate — fixed with an optional non-capturing group. |
| 4 | AC4 (`mvn verify` through package/repackage) has no automated regression guard; suggested a Failsafe-based jar-existence test | **Rejected.** `maven-failsafe-plugin` doesn't exist anywhere else in this repo (confirmed across auth/crypto's own poms throughout this whole session) — introducing it here, for one jar-existence check, would set a new, inconsistent build-lifecycle precedent unilaterally. The manual `mvn verify` run at every phase (recorded in this task's own implementation notes) already provides the evidence AC4 needs; a real Failsafe adoption deserves its own explicit decision, not a Phase 11 fold-in. |
| 5 | No guard against extra production classes beyond the bare Application class | **Accepted.** Added `noExtraProductionClassesExistBeyondTheBareApplicationClass`, walking `src/main/java/com/themistra/notification` and asserting exactly one file. |
| 6 | AWS SDK BOM import mechanism (type=pom, scope=import) not verified, only its version | **Accepted.** Added `awsSdkBomIsImportedNotMerelyDeclaredAtTheRightVersion`, extracting the `dependencyManagement` block and asserting all five required elements. |

Full suite re-verified after all fixes: 9 tests (7 + 2 new methods), 0 failures, clean `package`/
`repackage`.
