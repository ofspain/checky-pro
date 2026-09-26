STATUS: FROZEN

# notification · T01 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 9 findings independently verified against real source (sibling poms, a live `dependency:tree` run)
before disposition.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Missing `spring-boot-starter-test`/`spring-security-test`/`spring-boot-testcontainers` | **ACCEPTED** | Confirmed both sibling poms declare all three in `test` scope; the TIB's dependency list omitted them. Added explicitly. |
| 2 | "resource-server" is ambiguous (one artifact vs. two) | **ACCEPTED** | Confirmed directly in `services/auth/pom.xml`: both `spring-boot-starter-oauth2-resource-server` and `spring-security-oauth2-resource-server` are declared. Both now named explicitly. |
| 3 | Bare `@SpringBootApplication` may lack a `main` method, breaking repackage | **ACCEPTED** | The TIB's "no extra annotations" wording was meant to exclude premature annotations, not the `main` method — but the ambiguity is real and this is exactly the failure class crypto-service's own T01 hit. Made explicit: `public static void main(String[] args) { SpringApplication.run(...); }` is required. |
| 4 | `sesv2` may need an explicit HTTP-client artifact | **PARTIALLY ACCEPTED, verified rather than assumed either way** | Ran `mvn -pl services/crypto dependency:tree` directly: `kms` (identical BOM version 2.50.2) already resolves a working HTTP client transitively (`netty-nio-client` + Apache HttpClient5, both `runtime` scope) with zero explicit declaration — this is BOM-level, not per-artifact, behavior, so `sesv2` very likely behaves identically. Not adding a speculative explicit HTTP-client dependency. Adopting Kimi's own alternative (b) instead: Phase 6 will add a minimal test that actually constructs a `SesV2Client` bean, so if this assumption is wrong for `sesv2` specifically, it surfaces in this task, not task 12. |
| 5 | `<finalName>` unspecified | **ACCEPTED** | `<finalName>notification-service</finalName>` required explicitly, matching sibling convention. |
| 6 | Regression-guard scope under-specified | **ACCEPTED** | Exact positive assertions (`@SpringBootApplication`, `SpringApplication.run`, package `com.themistra.notification`) and exact negative assertions (`@ConfigurationPropertiesScan`, `@EnableScheduling`, `@EnableSchedulerLock` absent) now listed explicitly. |
| 7 | AWS BOM `dependencyManagement` import not listed as a required file element | **ACCEPTED** | Added explicitly to the Files-to-Create checklist, plus a cross-service version-alignment regression assertion (mirrors `T01SkeletonRegressionTest`'s existing `sharedDependencyVersionsStayAlignedWithAuthService`-style check). |
| 8 | `flyway-maven-plugin` version/credentials implied | **ACCEPTED** | Explicit: version `11.7.2`, same local-only credentials as auth/crypto, `<schemas>notifications</schemas>` as the one difference. |
| 9 | No owner/trigger condition for the `contracts/events/payments/` gap | **ACCEPTED** | Added the suggested tracker note to Open Questions, without expanding T01's own scope. |

## Task

Unchanged from Phase 2, with all 9 dispositions above folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- Explicit dependency list: `spring-boot-starter-web`, `-validation`,
  `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server`,
  `spring-boot-starter-data-jpa`, `flyway-core` + `flyway-database-postgresql`, `postgresql` (runtime),
  `spring-kafka`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus` (runtime),
  `software.amazon.awssdk:sesv2`, `spring-boot-starter-test` + `spring-security-test` +
  `spring-boot-testcontainers` + `testcontainers:postgresql` + `testcontainers:kafka` +
  `testcontainers:junit-jupiter` (all `test` scope), `archunit-junit5` (test), `awaitility` (test).
- `dependencyManagement` importing `software.amazon.awssdk:bom:2.50.2` (`type=pom`, `scope=import`) —
  same version as both sibling services.
- `<finalName>notification-service</finalName>`.
- `flyway-maven-plugin:11.7.2`, no `<executions>` binding, `jdbc:postgresql://localhost:5432/checky`,
  `checky`/`checky-local-only`, `<schemas>notifications</schemas>`.
- `NotificationServiceApplication` with an explicit `main` method calling `SpringApplication.run(...)`.
- A minimal test constructing a `SesV2Client` bean (Finding #4's resolution) — closes whether an
  explicit HTTP-client artifact is actually needed, empirically, in this task rather than assumed.

**Out:** Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 2's AC1–AC5, with AC2 now unambiguous (exact artifact list above, not "the shared
subset") and AC4 covering the `main`-method gap explicitly.

## Required Tests

The regression-guard class (`T01SkeletonRegressionTest`, mirroring crypto-service's naming), now with
the exact assertion list from Finding #6, plus the `SesV2Client`-construction check from Finding #4.

## Open Questions

No blockers. Tracker note (Finding #9): Task 6/7/15 cannot be considered complete until
`contracts/events/payments/` exists and Task 15's contract tests pass against it — visible to whoever
picks up those tasks, not expanding this task's own scope.
