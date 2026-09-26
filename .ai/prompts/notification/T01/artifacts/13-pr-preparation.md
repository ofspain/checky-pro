# notification · T01 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation. This is the first task in
`spec/notification-service/tasks.md` — the foundation for all 20 tasks in this service's own pipeline.

## Commit title

```
Add notification-service skeleton and POM (T01)
```

## Commit message

```
Add notification-service skeleton and POM (T01)

Registers services/notification in the root reactor and gives it a
pom.xml mirroring services/auth's resource-server-only dependency
subset (web, validation, both resource-server artifacts, data-jpa,
flyway, postgres, spring-kafka, actuator, prometheus, testcontainers,
archunit, awaitility), plus Amazon SES (sesv2) for email transport -
the one genuinely open decision this task's own acceptance criteria
required resolving now (O2/Q2). SES was chosen over SendGrid/SMTP:
AWS-native, matches the platform's existing KMS usage pattern in both
sibling services, no new vendor relationship, secret type, or auth
paradigm. Verified empirically, not assumed, that no explicit
HTTP-client dependency is needed - sesv2 resolves the same
apache5-client/netty-nio-client pair transitively that kms/s3 already
do under the identical AWS SDK BOM version, proven by actually
constructing a SesV2Client in a test, not just reading a dependency
tree.

A bare NotificationServiceApplication with a real main method closes
the exact gap crypto-service's own T01 found the hard way: a
@SpringBootApplication class with no main method fails
spring-boot:repackage, not just compile.

The regression-guard test (T01SkeletonRegressionTest, mirroring
crypto-service's own naming) went through three full review rounds -
self-review, independent review, and test review - which together
found and fixed nine real precision gaps, including two genuine bugs
in the test helpers themselves: a Javadoc that named the very
annotations its own negative assertions checked for absence,
tripping itself; and a dependency-scope regex that didn't tolerate
an explicit <version> element some dependencies carry between
<artifactId> and <scope>. Both were caught only by actually running
the tests, not by inspection.

One Kimi Phase 11 suggestion (a Failsafe-based automated jar-existence
check) was declined: maven-failsafe-plugin exists nowhere else in
this repo, and introducing it unilaterally for one check would set a
new build-lifecycle precedent better made as its own explicit
decision. The manual mvn verify run at every phase already provides
the same evidence.
```

## Testing performed

- `mvn -pl services/notification -am dependency:resolve` — every declared artifact resolves cleanly.
- `mvn -pl services/notification -am test -Dtest=T01SkeletonRegressionTest` — 9/9 passing, re-run after
  every incremental change across Phases 6, 7, 9, and 11.
- `mvn -pl services/notification -am verify` (full module, run fresh at every phase from 6 through 12) —
  clean `package`/`repackage`, no failures at any point.
- `mvn -q compile` (full three-module reactor: auth + crypto + notification) — clean, confirming the
  root pom edit didn't disturb either sibling service.
- `mvn -pl services/crypto dependency:tree` (comparative check) and `mvn -pl services/notification
  dependency:tree` — used directly to settle the AWS SDK HTTP-client question empirically rather than by
  assumption, both before and after `sesv2` was added.
- Full traceability against Phase 1's AC1–AC5: `artifacts/12-specification-verification.md` — verdict
  **PASS**.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 1 ("Service skeleton & POM") — the first task in
  this service's own 20-task pipeline.
- **Requirements:** none individually numbered — a foundation/skeleton task, not a functional behavior.
- **LOCKED decisions:** L2 (consume-only, no producer/outbox dependency added), L10 (secrets discipline —
  the `validation` starter added here enables later enforcement), L11 (module boundaries — the
  `archunit-junit5` dependency added here enables later enforcement). None implemented yet; both are
  dependencies this task adds for later tasks to build on.
- **Open decisions resolved this task:** O2/Q2 (email transport) — Amazon SES, with a full rationale
  recorded in the frozen brief and this task's own artifacts.
- **Flagged, not fixed, for future tasks:**
  - `contracts/events/payments/` does not exist yet (Payment Service unbuilt) — a real, tracked
    ordering hazard for Task 6/7/15.
  - Whether to adopt `maven-failsafe-plugin` repo-wide — deliberately deferred, not decided here.
