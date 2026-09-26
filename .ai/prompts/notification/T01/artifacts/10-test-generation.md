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
| `runtimeAndTestScopesAreCorrect` | `postgresql` runtime vs. test scope | Kimi Phase 8 Finding #6 |
| `finalNameAndFlywayPluginMirrorTheSiblingConvention` | `finalName`, Flyway plugin version (scoped), schema, no lifecycle binding | Kimi Phase 3 Findings #5/#8; Kimi Phase 8 Findings #1/#5 |
| `applicationClassIsBareWithOnlyTheMainMethod` | Bare skeleton class, real `main` method, no premature annotations | AC3/AC4; Kimi Phase 3 Findings #3/#6 |
| `sesV2ClientCanActuallyBeConstructed` | Real SDK client construction (HTTP-client-on-classpath proof) | Kimi Phase 3 Finding #4 |
| `sharedDependencyVersionsStayAlignedWithAuthAndCrypto` | Cross-service version drift (Testcontainers, ArchUnit, Awaitility, AWS BOM, Flyway plugin) | Kimi Phase 3 Finding #7; Kimi Phase 8 Finding #7 |

## What stands in for "AC4" here

`mvn -pl services/notification -am verify`, run fresh at Phases 6, 7, and 9: stable result — 7 tests, 0
failures, clean `package`/`repackage`. A separate `mvn -q compile` across the full three-module reactor
confirmed the root pom edit didn't disturb `services/auth` or `services/crypto`.

## Phase 11 (Kimi Test Review) preview

Every test this task touches lives in this one file, already through two rounds of tightening (Phase 7
self-review, Phase 9 Kimi-driven). If Kimi's Phase 11 pass finds anything, it will necessarily be about
this same file's remaining precision, not a dedicated new suite.
