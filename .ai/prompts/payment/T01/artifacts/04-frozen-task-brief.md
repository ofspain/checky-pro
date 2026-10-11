STATUS: FROZEN (approved 2026-10-11, Human Approval gate)

# payment · T01 · Frozen Task Brief

## Phase 3 disposition (all 10 findings ACCEPTED — none rejected)

1. **L13 vs. hardcoded Flyway plugin password.** Real tension, verified: every sibling pom
   (`auth`/`crypto`/`notification`) hardcodes `<password>checky-local-only</password>` in a
   `flyway-maven-plugin` block that only runs via an explicit, manual `mvn flyway:migrate` — never
   during `package`/`verify`/CI/the Docker build (each pom's own comment says so verbatim). This is
   an already-standing, cross-service pattern, not something newly introduced by this brief.
   **Resolution: documented as an explicit, scoped carve-out from L13 below, not a behavior
   change** — making payment-service follow a different rule than its own siblings would be a
   worse inconsistency than the tension itself.
2. **AWS SDK BOM version.** Pinned to `2.50.2` (matches all three siblings) — folded into AC2.
3. **"Bare" Application class.** Clarified: no domain code, no annotation beyond
   `@SpringBootApplication` — but the standard `public static void main(String[] args)` calling
   `SpringApplication.run(PaymentServiceApplication.class, args)` is required (verified against
   `services/notification`'s own T01 test, which asserts exactly this string) — folded into AC4.
4. **`<relativePath>`.** `<relativePath>../../pom.xml</relativePath>` required explicitly (verified
   present in all three sibling poms) — folded into AC2.
5. **AC4's "validate-equivalent check."** Clarified as a structural/text check (source file exists,
   contains only `@SpringBootApplication`, has a valid `main` method) — no Maven shell-out from
   JUnit, matching real sibling precedent (confirmed: none of the three siblings' own T01 tests
   invoke Maven from inside a test) — folded into Required Tests.
6. **"Mirroring services/auth" imprecision.** The verbatim task statement can't be edited (spec
   files are untouchable); the TIB's own Purpose/Scope below is tightened so the auth reference
   reads unambiguously as "the platform convention auth established," with crypto/notification as
   the structurally accurate templates.
7. **`bucket4j` omitted from AC3.** Confirmed present in `services/auth/pom.xml`
   (`com.bucket4j:bucket4j_jdk17-core`, rate limiting, auth-only) — added to AC3's prohibited list.
8. **No S3Client construction test.** Confirmed `services/notification` has the equivalent
   (`sesV2ClientCanActuallyBeConstructed`) — an analogous `S3Client` construction test is added to
   Required Tests.
9. **Flyway plugin version alignment untested.** Added to the version-alignment assertion
   (confirmed `11.7.2` identical across all three siblings).
10. **`<artifactId>` unstated.** Confirmed artifactId==finalName convention in both crypto
    (`crypto-service`/`crypto-service`) and notification (`notification-service`/
    `notification-service`) — `<artifactId>payment-service</artifactId>` added to AC2.

## Task

T01 — Service skeleton & POM.

## Purpose

Register `services/payment` as a new Maven module with exactly the dependency set this service's
foundation needs, mirroring the **resource-server-only convention** `services/crypto` and
`services/notification` already established (not `services/auth`'s own OIDC-issuer-specific
dependencies), so later tasks have a buildable module to add code into.

## Scope

**In:**
- Add `<module>services/payment</module>` to the root `pom.xml`.
- Create `services/payment/pom.xml`.
- Add a bare `PaymentServiceApplication` class (no domain code, standard `main` method only).
- Add the S3 SDK dependency (receipt store, L9) and the AWS SDK BOM to version it.
- Explicitly exclude the KMS SDK (L3).

**Out:**
- Any schema, config, entity, controller, consumer, or service class (tasks 2–29).
- ShedLock and `jackson-dataformat-yaml` dependencies (tasks 8 and 24 respectively).
- Any annotation on `PaymentServiceApplication` beyond `@SpringBootApplication`.
- Externalizing the local-dev Flyway plugin password (see disposition 1 — stays as-is, documented).

## Business Rules

None — no `requirements.md` R-number applies to pure build-scaffolding.

## Locked Decisions

- **L3** — no KMS SDK, no chain client (web3j/Tron) anywhere in the pom.
- **L9** — the S3 SDK is added for the receipt WORM store only.
- **L12** — module boundaries are package-by-feature under `com.themistra.payment`; this task only
  establishes the module.
- **L13** — no secret/credential string in either pom, **with one explicit, documented carve-out**:
  the Flyway Maven plugin's local-dev-only `<configuration>` block (never executed during
  `package`/`verify`/CI/the Docker build; only via a developer's own explicit `mvn flyway:migrate`
  against their own local Postgres) may carry the same `checky-local-only` placeholder every
  sibling pom already carries. This is not a new exception — it already exists identically in
  `auth`/`crypto`/`notification`; this brief makes it explicit rather than silently repeating it.

## Dependencies

None (no classes beyond the bare Application class exist to depend on anything).

## Inputs

- Root `pom.xml` (read, then extend).
- `services/crypto/pom.xml` and `services/notification/pom.xml` (the structurally accurate
  templates — both resource-server-only).

## Outputs

- `services/payment/pom.xml` (new).
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` (new).
- Root `pom.xml` (modified: one new `<module>` line).

## State Changes

None.

## Files to Create

- `services/payment/pom.xml`
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`

## Files to Modify

- `pom.xml` (repo root) — add the one `<module>` entry, after the three existing ones.

## Files NOT to Modify

- `services/payment/README.md`.
- `services/auth/pom.xml`, `services/crypto/pom.xml`, `services/notification/pom.xml` (read-only
  reference templates).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — root `pom.xml`'s `<modules>` contains `<module>services/payment</module>`, after the
  three existing entries, with the existing "dependency order" comment intact.
- **AC2** — `services/payment/pom.xml` declares: `<artifactId>payment-service</artifactId>`,
  `<relativePath>../../pom.xml</relativePath>` in `<parent>`, the AWS SDK BOM pinned to `2.50.2` in
  `dependencyManagement`, and exactly: `spring-boot-starter-web`, `spring-boot-starter-validation`,
  `spring-boot-starter-oauth2-resource-server`, `spring-security-oauth2-resource-server`,
  `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`, `postgresql`
  (runtime), `spring-kafka`, `spring-boot-starter-actuator`, `micrometer-registry-prometheus`
  (runtime), `software.amazon.awssdk:s3`; test-scope `spring-boot-starter-test`,
  `spring-security-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`,
  `org.testcontainers:kafka`, `org.testcontainers:junit-jupiter`, `com.tngtech.archunit:archunit-junit5`,
  `org.awaitility:awaitility`; and `testcontainers.version` pinned to `1.21.4`.
- **AC3** — `services/payment/pom.xml` contains none of: `<artifactId>kms</artifactId>`,
  `org.web3j`, `io.github.tronprotocol`, `oauth2-authorization-server`, `net.javacrumbs.shedlock`,
  `jackson-dataformat-yaml`, `com.bucket4j`.
- **AC4** — `PaymentServiceApplication` exists with `@SpringBootApplication` and nothing else, plus
  a standard `public static void main(String[] args)` calling
  `SpringApplication.run(PaymentServiceApplication.class, args)`; `mvn -pl services/payment
  validate` and `compile` succeed.
- **AC5** — `services/payment/pom.xml` registers `spring-boot-maven-plugin` (no configuration) and
  the Flyway Maven plugin (local-dev-only, no `<executions>`, version `11.7.2`,
  `<schemas>payments</schemas>`, the same admin-role JDBC URL/credentials as every sibling pom —
  see L13's documented carve-out above); `<finalName>payment-service</finalName>`.

## Required Tests

- `com.themistra.payment.T01SkeletonRegressionTest` (new): asserts AC1–AC5 by direct file read of
  both poms, plus:
  - a structural check for AC4 (source contains `@SpringBootApplication`, no other class-level
    annotation, and the exact `SpringApplication.run(PaymentServiceApplication.class, args);`
    line) — no Maven shell-out from the test.
  - version-alignment assertions against `services/auth/pom.xml`: `testcontainers.version`,
    `archunit-junit5`, `awaitility`, the AWS SDK BOM, **and `flyway-maven-plugin`** (added per
    finding 9) — narrowed from crypto's own version of this test by omitting `shedlock-spring`
    (not present in this task's pom yet).
  - a real `S3Client` construction test (dummy credentials, no network call), mirroring
    `services/notification`'s own `sesV2ClientCanActuallyBeConstructed()` precedent (finding 8) —
    proves the AWS SDK BOM + S3 dependency actually resolves a constructible client now, rather
    than deferring that discovery to task 18.
  - a negative-proof scan confirming no secret/credential string appears in either pom **outside**
    the one documented L13 carve-out (the Flyway plugin's local-dev-only block).

## Constraints

- **Module boundaries:** none yet (no feature packages exist).
- **Security:** no secret/credential in either pom outside the one documented, pre-existing L13
  carve-out.
- **Thread-safety / transactions / null handling / performance:** not applicable — no behavior in
  this task.
- **Build integrity:** `mvn -pl services/payment verify` must not fail at the `repackage` goal.

## Open Questions

None. All resolved via the Phase 3 disposition above.
