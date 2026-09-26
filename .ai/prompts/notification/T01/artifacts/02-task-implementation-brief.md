# notification · T01 · Phase 2 — Task Implementation Brief

## Task

Add `services/notification` to the root reactor and author its `pom.xml`, mirroring
`services/auth/pom.xml`'s resource-server-only dependency subset plus Amazon SES as the email-transport
client (O2/Q2, resolved below). Add a bare `NotificationServiceApplication` class so the module actually
packages.

## Purpose

Establishes the buildable skeleton every later notification-service task builds on — the same role
crypto-service's own T01 played for that module.

## Scope

**In:**
- Root `pom.xml` — append `<module>services/notification</module>`.
- `services/notification/pom.xml` — new file, mirroring `services/auth/pom.xml`'s structure exactly for
  the shared subset (web, validation, resource-server, data-jpa, flyway, postgres, spring-kafka,
  actuator, prometheus, testcontainers [postgresql, kafka, junit-jupiter], archunit, awaitility), plus:
  - **Amazon SES via AWS SDK v2** (O2/Q2, resolved): `software.amazon.awssdk:sesv2`, using the identical
    `software.amazon.awssdk:bom` dependencyManagement import already present in both
    `services/auth/pom.xml` and `services/crypto/pom.xml` (version `2.50.2`, kept aligned per
    `T01SkeletonRegressionTest`-style cross-service version-alignment precedent). Rationale: the
    platform is AWS-native throughout (KMS in both sibling services, MSK for Kafka, presumably EKS/S3
    elsewhere); SES avoids a third-party vendor relationship, a new secret type, and a new
    authentication paradigm outside the IAM-based discipline L10 already establishes. SendGrid and raw
    SMTP relay were considered and rejected: SendGrid adds an unnecessary third-party dependency this
    AWS-native platform doesn't need elsewhere; a generic SMTP relay is redundant with SES's own SMTP
    interface if that were ever preferred later, and buys nothing over the native SDK today.
  - The local-dev-only `flyway-maven-plugin` block, `<schemas>notifications</schemas>`.
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
  — bare `@SpringBootApplication`, no extra annotations (no `@ConfigurationPropertiesScan`,
  `@EnableScheduling`, `@EnableSchedulerLock` — none of those things exist yet; add each in the task
  that actually introduces it, per crypto-service's own T01 lesson).
- A narrow regression-guard test class (mirroring `T01SkeletonRegressionTest`'s established style)
  proving: root pom registers the module, the new pom declares the required dependencies and excludes
  the issuer starter and any outbox/producer library, and the skeleton class exists correctly annotated.

**Out:**
- Any `@ConfigurationProperties` class, security config, schema migration, consumer, or any other
  production class beyond the bare Application class — all later tasks' own scope.
- `contracts/events/payments/` — doesn't exist yet (Phase 0 finding); not this task's problem.
- Any file under `spec/`.

## Business Rules

None individually targeted (Phase 1, unchanged) — governed by `tasks.md` task 1's own text.

## Locked Decisions

- L2 — consume-only, no producer/outbox dependency added.
- L10 — secrets discipline; this task adds the `validation` starter enabling it, implements nothing yet.
- L11 — module boundaries; this task adds the `archunit-junit5` dependency enabling it, implements
  nothing yet (no packages exist to bound).

## Dependencies

None new beyond the pom itself. No existing classes/entities/contracts consumed by a bare skeleton.

## Inputs

`services/auth/pom.xml` (precedent), root `pom.xml` (current module list), `design.md` §4c (config keys,
for context only — not implemented this task).

## Outputs

`services/notification/pom.xml`, root `pom.xml` (modified), `NotificationServiceApplication.java`, one
new regression-guard test class.

## State Changes

None to application state — no database, no runtime behavior yet.

## Files to Create

- `services/notification/pom.xml`
- `services/notification/src/main/java/com/themistra/notification/NotificationServiceApplication.java`
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (name deliberately mirrors crypto-service's own `T01SkeletonRegressionTest` for cross-service
  consistency and discoverability).

## Files to Modify

- Root `pom.xml` — `<modules>` block only.

## Files NOT to Modify

- `services/auth/pom.xml`, `services/crypto/pom.xml` (cited as precedent, not touched).
- Every file under `spec/`.
- Every existing file in `services/auth`, `services/crypto`.

## Acceptance Criteria

1. **AC1.** Root `pom.xml` lists `services/notification` in `<modules>`, appended after the existing two
   entries (no reordering needed — no `libs/java/*` module exists yet for either sibling service).
2. **AC2.** `services/notification/pom.xml` exists with exactly the dependency set specified above —
   verified by a fresh `mvn -pl services/notification -am dependency:resolve` (or equivalent), not
   assumed from the file's own text alone.
3. **AC3.** `NotificationServiceApplication` exists, bare, correctly annotated.
4. **AC4.** `mvn -pl services/notification -am verify` succeeds through `package`/`repackage`, not just
   `compile`/`test-compile` — the exact gap crypto-service's own T01 found the hard way.
5. **AC5.** The new regression-guard test class passes, and (per its own negative-proof convention
   established in T27/T28/T29) is verified to actually fail when the property it guards is mutated,
   not just to pass on already-correct content.

## Required Tests

The one regression-guard test class described above — no functional test exists yet (no functional code
exists yet).

## Constraints

- **No performance/thread-safety/transaction concerns** — a pom and a bare Application class have none.
- **Module boundaries**: N/A yet (no packages beyond the bare Application class exist to bound).
- **Null handling**: N/A.
- **Security**: no secret, credential, or environment-specific value in any new file (L10) — SES access
  is IAM-role-based at runtime (matching KMS's own pattern in the sibling services), not an API-key
  secret committed anywhere.

## Open Questions

No blockers. O2/Q2 (email-transport vendor) is resolved above as a working decision — subject to Phase
3/4 challenge like any other design choice in this pipeline, not left ambiguous. The
`contracts/events/payments/` gap (Phase 0/1) is carried forward as a known, non-blocking risk for a
later task, not this one.
