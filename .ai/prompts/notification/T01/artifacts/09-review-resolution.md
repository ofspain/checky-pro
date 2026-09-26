# notification · T01 · Phase 9 — Review Resolution (Human Approval Gate)

## Kimi Phase 8 findings — dispositions

All 7 findings independently verified against real source before disposition. All accepted — every one
is a precision/coverage gap in the regression-guard test itself, not a design or judgment call with a
real alternative.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Flyway plugin lifecycle binding (`<executions>` absence) not guarded | **ACCEPTED** — highest-value finding, the only one that could allow a silent CI breakage. | Added a `pluginBlock` extraction helper; the plugin test now asserts the extracted block contains no `<executions>`. |
| 2 | `kafka`/`junit-jupiter` checks don't verify groupId | **ACCEPTED** | Switched to `hasGroupAndArtifact(pom, "org.testcontainers", ...)` for both, matching the pattern already used for `postgresql`. |
| 3 | `sesv2` groupId not verified | **ACCEPTED** | Switched to `hasGroupAndArtifact(pom, "software.amazon.awssdk", "sesv2")`. |
| 4 | Auth-specific dependencies not negatively asserted | **ACCEPTED** | Added `doesNotContain` for `shedlock-spring`, `shedlock-provider-jdbc-template`, `bucket4j`, `jackson-dataformat-yaml`. |
| 5 | Flyway plugin version check is a global substring, not scoped to the plugin | **ACCEPTED** | Reused the new `pluginBlock` extraction; version is now asserted within that extracted block only. |
| 6 | Dependency scopes not regression-guarded | **ACCEPTED** | Added `runtimeAndTestScopesAreCorrect`, checking `org.postgresql:postgresql` is `runtime` and `org.testcontainers:postgresql` is `test` — narrowly scoped to the two the brief calls out explicitly, not every dependency. |
| 7 | Flyway plugin version excluded from the cross-service alignment check | **ACCEPTED** | Extended `sharedDependencyVersionsStayAlignedWithAuthAndCrypto` to also compare `flyway-maven-plugin`'s version across all three service poms. |

## Verification performed

- `mvn -pl services/notification -am test -Dtest=T01SkeletonRegressionTest` — 7/7 passing (6 existing +
  1 new method), after all 7 fixes.
- `mvn -pl services/notification -am verify` — clean, same shape as Phase 7's own final record, one
  additional passing test.
