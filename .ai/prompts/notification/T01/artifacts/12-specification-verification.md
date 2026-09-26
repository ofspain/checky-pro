# notification · T01 · Phase 12 — Specification Verification

## Acceptance Criteria (Phase 1)

### AC1. Root `pom.xml`'s `<modules>` block includes `services/notification`, appended in order

**PASS.** Confirmed by `rootPomRegistersNotificationServiceAfterAuthAndCrypto`, chaining the full
auth→crypto→notification order (strengthened at Phase 11 from a single pairwise check).

### AC2. `services/notification/pom.xml` exists with exactly the specified dependency set

**PASS.** Every dependency named in the Phase 4 frozen brief is present, verified by groupId+artifactId
pair wherever a bare artifactId string would be ambiguous (`postgresql`, `sesv2`, `kafka`,
`junit-jupiter`). The issuer starter and every auth-specific dependency (ShedLock, bucket4j,
jackson-dataformat-yaml) are confirmed absent. Runtime and test scopes are individually verified for
every dependency the brief distinguishes by scope. The AWS SDK BOM is confirmed to be a real
`dependencyManagement` import (not merely a version-matched string) — O2/Q2 resolved to Amazon SES via
`sesv2`, verified via a real `SesV2Client` construction (no explicit HTTP-client dependency needed,
confirmed empirically against the identical pattern already proven for `kms`/`s3` in the sibling
services).

### AC3. `NotificationServiceApplication` exists, bare, correctly annotated

**PASS.** Confirmed present, `@SpringBootApplication`, correct package, and free of any premature
annotation (`@ConfigurationPropertiesScan`, `@EnableScheduling`, `@EnableSchedulerLock`) — verified on
the code after the class-level Javadoc specifically, since the Javadoc's own explanatory prose names
those same three annotations (a real self-inflicted test bug found and fixed during Phase 6).

### AC4. `mvn -pl services/notification -am verify` succeeds through `package`/`repackage`

**PASS.** Verified fresh at Phases 6, 7, 9, 11, and again this phase: clean `package` and
`spring-boot:repackage`, exactly the failure mode crypto-service's own T01 hit and this task
deliberately guarded against from the start. A Failsafe-based automated jar-existence check was
proposed (Kimi Phase 11 Gap 4) and rejected — it would introduce build tooling this repo uses nowhere
else, for a check the manual per-phase verification already provides.

### AC5. The regression-guard test class passes, proven against real mutation where practical

**PASS.** 9 test methods, all passing, built up across three review rounds (self-review, Kimi
independent review, Kimi test review) that together found and fixed 9 real precision gaps — including
two genuine regex bugs in the test helpers themselves (the Javadoc self-reference in Phase 6, the
missing-`<version>`-element case in Phase 11), both caught only by actually running the tests, not by
inspection.

## Verdict

**PASS.** All five Phase 1 acceptance criteria satisfied, each verified with fresh evidence this phase,
not carried over from memory. `services/notification` now exists as a real, buildable Maven module —
the foundation every later notification-service task builds on.

## Candidates for future tasks (not fixed here, out of T01's own scope)

- **`contracts/events/payments/` does not exist yet** (Phase 0/1/4 finding, carried forward per Kimi
  Phase 3 Finding #9's own tracker note): Task 6/7/15 cannot be considered complete until it exists and
  Task 15's contract tests pass against it.
- Whether to adopt `maven-failsafe-plugin` repo-wide for post-package integration checks (Kimi Phase 11
  Gap 4) — deliberately not decided unilaterally in this task; a candidate for its own explicit decision
  if a real need for it emerges in a later task.
