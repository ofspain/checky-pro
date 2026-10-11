# payment · T01 · Task Implementation Brief (TIB)

## Task

T01 — Service skeleton & POM.

## Purpose

Register `services/payment` as a new Maven module and give it a `pom.xml` carrying exactly the
dependency set this service's own foundation needs, so later tasks (schema, config, consumers,
attestation, receipts) have a buildable module to add code into.

## Scope

**In:**
- Add `<module>services/payment</module>` to the root `pom.xml`.
- Create `services/payment/pom.xml`.
- Add a bare `PaymentServiceApplication` class (no behavior, no extra annotations).
- Add the S3 SDK dependency (receipt store, L9) and the AWS SDK BOM needed to version it.
- Explicitly exclude the KMS SDK (L3).

**Out:**
- Any schema, config, entity, controller, consumer, or service class (tasks 2–29).
- ShedLock and `jackson-dataformat-yaml` dependencies (tasks 8 and 24 respectively).
- Any annotation on `PaymentServiceApplication` beyond `@SpringBootApplication`.

## Business Rules

None — this task introduces no runtime behavior; no `requirements.md` R-number applies.

## Locked Decisions

- **L3** — no KMS SDK, no chain client (web3j/Tron) anywhere in the pom.
- **L9** — the S3 SDK is added for the receipt WORM store only; no other purpose.
- **L12** — module boundaries are package-by-feature under `com.themistra.payment`; this task only
  establishes the module itself, no feature packages yet.
- **L13** — no secret/credential string in either pom.

## Dependencies

None (no classes beyond the bare Application class exist to depend on anything).

## Inputs

- Root `pom.xml` (read, then extend).
- `services/crypto/pom.xml` and `services/notification/pom.xml` (read as the structurally accurate
  templates — both resource-server-only, unlike `services/auth/pom.xml`, which is an OIDC issuer).

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

- `services/payment/README.md` (already accurate, out of this task's scope).
- `services/auth/pom.xml`, `services/crypto/pom.xml`, `services/notification/pom.xml` (read-only
  reference templates).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — root `pom.xml`'s `<modules>` contains `<module>services/payment</module>`, after the
  three existing entries, with the existing "dependency order" comment intact.
- **AC2** — `services/payment/pom.xml` declares exactly: `spring-boot-starter-web`,
  `spring-boot-starter-validation`, `spring-boot-starter-oauth2-resource-server`,
  `spring-security-oauth2-resource-server`, `spring-boot-starter-data-jpa`, `flyway-core`,
  `flyway-database-postgresql`, `postgresql` (runtime), `spring-kafka`,
  `spring-boot-starter-actuator`, `micrometer-registry-prometheus` (runtime),
  `software.amazon.awssdk:s3`, plus the AWS SDK BOM in `dependencyManagement`, plus test-scope
  `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`,
  `org.testcontainers:postgresql`, `org.testcontainers:kafka`, `org.testcontainers:junit-jupiter`,
  `com.tngtech.archunit:archunit-junit5`, `org.awaitility:awaitility`; and the
  `testcontainers.version` property pinned to `1.21.4`.
- **AC3** — `services/payment/pom.xml` contains none of: `<artifactId>kms</artifactId>`,
  `org.web3j`, `io.github.tronprotocol`, `oauth2-authorization-server`, `net.javacrumbs.shedlock`,
  `jackson-dataformat-yaml`.
- **AC4** — `PaymentServiceApplication` exists with a bare `@SpringBootApplication` annotation and
  nothing else; `mvn -pl services/payment validate` and `compile` succeed.
- **AC5** — `services/payment/pom.xml` registers `spring-boot-maven-plugin` (no configuration) and
  the Flyway Maven plugin (local-dev-only, no `<executions>`, `<schemas>payments</schemas>`, same
  admin-role JDBC URL/credentials as every sibling pom); `<finalName>payment-service</finalName>`.

## Required Tests

- `com.themistra.payment.T01SkeletonRegressionTest` (new): asserts AC1–AC5 by direct file read of
  the two poms and (for AC4) a successful `mvn -pl services/payment validate`-equivalent check —
  mirrors `services/crypto`'s and `services/notification`'s own identically-named, identically-
  purposed T01 test class. Also asserts `testcontainers.version`, `archunit-junit5`, and
  `awaitility` versions match `services/auth/pom.xml`'s own values (shared-version alignment,
  narrowed from crypto's own version of this test by dropping the `shedlock-spring` comparison,
  since this task's pom does not carry that dependency yet).

## Constraints

- **Module boundaries:** none yet (no feature packages exist).
- **Security:** no secret/credential in either pom (L13).
- **Thread-safety / transactions:** not applicable — no behavior in this task.
- **Null handling:** not applicable.
- **Performance:** not applicable.
- **Build integrity:** `mvn -pl services/payment verify` must not fail at the `repackage` goal for
  want of an `@SpringBootApplication` class (the one documented failure mode this brief exists to
  pre-empt, per `crypto-service` T01's own precedent).

## Open Questions

No blockers.
