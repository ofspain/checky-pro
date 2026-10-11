# payment · T01 · Phase 5 — Implementation Plan

Traces to the frozen brief (`04-frozen-task-brief.md`) only. No file or decision beyond it.

## Files to create

1. `services/payment/pom.xml`
2. `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
3. `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`

## Files to modify

1. `pom.xml` (repo root) — one new `<module>services/payment</module>` line, appended after
   `<module>services/notification</module>`, inside the existing `<modules>` block; no other change
   to that file.

## Public methods (signatures)

- `com.themistra.payment.PaymentServiceApplication`:
  `public static void main(String[] args)` — calls `SpringApplication.run(PaymentServiceApplication.class, args)`.
  No other public (or any) method. Class itself annotated only `@SpringBootApplication`.

## Private methods

None (no domain logic in this task; the test class's own helper methods are test-support code, not
private production methods — listed under Tests below).

## Entities used

None.

## Repositories used

None.

## Services used

None.

## Unit / integration tests required

- `com.themistra.payment.T01SkeletonRegressionTest` — plain JUnit, no Spring context, no Docker
  (mirrors `crypto`/`notification`'s own T01 test: a text-scan + one real-client-construction check,
  not a structural/ArchUnit analysis, since there is no production code yet for ArchUnit to analyze).
  Test methods (AC-traced):
  1. `rootPomRegistersPaymentServiceAfterTheThreeExistingServicesWithOrderingComment` — AC1.
  2. `paymentPomDeclaresTheExactFoundationDependencySet` — AC2 (artifactId, relativePath, BOM
     version, and every required dependency coordinate present).
  3. `paymentPomExcludesKmsChainClientsAndDeferredDependencies` — AC3 (negative proof: `kms`,
     `org.web3j`, `io.github.tronprotocol`, `oauth2-authorization-server`,
     `net.javacrumbs.shedlock`, `jackson-dataformat-yaml`, `com.bucket4j` all absent).
  4. `applicationClassIsBareWithStandardMainMethod` — AC4 (source-text check: exactly one
     class-level annotation, `@SpringBootApplication`; exact `SpringApplication.run(...)` line
     present; no other method).
  5. `buildPluginsAndFinalNameAreRegistered` — AC5 (`spring-boot-maven-plugin` present;
     `flyway-maven-plugin` present, version `11.7.2`, `<schemas>payments</schemas>`, no
     `<executions>` binding; `<finalName>payment-service</finalName>`).
  6. `sharedDependencyVersionsStayAlignedWithAuthService` — version-alignment: `testcontainers.version`,
     `archunit-junit5`, `awaitility`, AWS SDK `bom`, and `flyway-maven-plugin` all equal
     `services/auth/pom.xml`'s own values (Phase 3 finding 9).
  7. `s3ClientCanActuallyBeConstructed` — real `S3Client.builder()...build()` with dummy static
     credentials, no network call (Phase 3 finding 8, mirrors notification's
     `sesV2ClientCanActuallyBeConstructed`).
  8. `noSecretOrCredentialStringOutsideTheDocumentedFlywayLocalDevCarveOut` — negative proof L13:
     scans both poms for anything password/secret-shaped outside the one named, commented
     `flyway-maven-plugin` block.

No integration test (Testcontainers/Docker) is required by this task — nothing runtime-behavioral
exists yet to exercise against a real Postgres/Kafka.

## Execution order

1. **Root `pom.xml`** — add the `<module>` line first, so `mvn -pl services/payment ...` resolves
   as part of the reactor for every subsequent step.
2. **`services/payment/pom.xml`** — create with the full dependency set, build plugins, and
   `dependencyManagement` from the frozen brief's AC2/AC5, in one pass (no incremental partial pom).
3. **`PaymentServiceApplication.java`** — add immediately after the pom, since `mvn validate`/
   `compile` needs it to succeed at all (frozen brief's own stated build-integrity constraint).
4. **Compile check** — `mvn -pl services/payment compile` (no Docker needed) before writing tests,
   to catch a pom/dependency-resolution mistake before the test class references anything.
5. **`T01SkeletonRegressionTest.java`** — written last, against the now-real files, in the order
   listed above (AC1 → AC2 → AC3 → AC4 → AC5 → version-alignment → S3Client → L13 negative-proof).
6. **Final check** — `mvn -pl services/payment test` (Surefire only; no Testcontainers dependency
   is exercised by this task's own test class, so no Docker requirement here, consistent with the
   frozen brief).
