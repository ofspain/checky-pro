# payment · T01 · Phase 1 — Specification Extraction

## Business Rules

None. T01 is pure build-scaffolding (module registration + `pom.xml`); it introduces no runtime
behavior, so no `requirements.md` R-number applies. This matches `auth-service` T01's and
`crypto-service` T01's own identical, already-established precedent.

## Locked Decisions

- **L3** — this service never touches a blockchain or KMS. The KMS SDK is explicitly NOT added
  (task statement's own parenthetical); no `web3j`/Tron SDK is ever added either (not named in the
  task, confirming no accidental addition). An ArchUnit import-ban enforcing this is a later task
  (25), but this task's own negative proof (no KMS dependency in the pom) is checkable now.
- **L9** — S3 Object Lock WORM. This task adds the S3 SDK dependency *for the receipt store only*
  (task statement's own words) — the actual WORM-write code is task 18; this task only makes the
  dependency available.
- **L12** — module boundaries, package-by-feature under `com.themistra.payment`. No feature
  packages exist yet; this task's own root-`<modules>` registration is the structural precondition
  for every later module to exist inside `services/payment`.
- **L13** — secrets discipline. No secret, credential, or identifier is committed anywhere in this
  task's two files (`pom.xml`, root `pom.xml`'s `<modules>` edit) — trivially satisfied, worth a
  named test anyway per the repo's own "regression guard against a future violation" convention.

## Files involved

**Existing, to extend:**
- `pom.xml` (repo root) — add `<module>services/payment</module>` to `<modules>`.

**New:**
- `services/payment/pom.xml` — mirrors `services/crypto/pom.xml` and `services/notification/pom.xml`
  (both resource-server-only, the structurally accurate templates — Phase 0 finding), **not**
  `services/auth/pom.xml` literally (an OIDC issuer; its SAS-starter and KMS-envelope dependencies
  do not apply here). The task statement's own phrase "mirroring services/auth" is read as "mirror
  the platform convention auth established," which crypto/notification already correctly adapted
  for a resource-server-only shape — not as "copy auth's issuer-specific dependencies."
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java` — bare
  `@SpringBootApplication`, no other annotations yet (no `@ConfigurationPropertiesScan`/
  `@EnableScheduling`/`@EnableSchedulerLock` — those are added only in the task that introduces the
  thing each one enables, per `crypto-service` T01's own named lesson 6). **Required in this same
  task, not deferred:** `crypto-service` T01 hit a real, documented failure — a pom with
  `spring-boot-maven-plugin` but zero `@SpringBootApplication` classes fails `mvn verify`'s own
  `repackage` goal. Adding the bare class now avoids re-discovering that exact failure.

## Dependencies

No Java-level dependencies yet (no classes beyond the bare Application class exist). Pom-level
dependencies this task adds (per the task statement's own enumerated, deliberately narrow list —
confirmed in Phase 0 as the authoritative scope, not auth/crypto/notification's fuller actual pom):

- `spring-boot-starter-web`, `spring-boot-starter-validation`
- `spring-boot-starter-oauth2-resource-server`, `spring-security-oauth2-resource-server`
  ("resource-server")
- `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-database-postgresql`,
  `postgresql` (runtime) ("data-jpa, flyway, postgres")
- `spring-kafka` ("spring-kafka")
- `spring-boot-starter-actuator`, `micrometer-registry-prometheus` (runtime)
  ("actuator, prometheus")
- `software.amazon.awssdk:s3` (task statement's own explicit addition, L9) — needs the
  `software.amazon.awssdk:bom` import in `dependencyManagement` for version management, mirroring
  every sibling service's identical pattern.
- Test scope: `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`,
  `org.testcontainers:postgresql`, `org.testcontainers:kafka`, `org.testcontainers:junit-jupiter`
  ("testcontainers"), `com.tngtech.archunit:archunit-junit5` ("archunit"),
  `org.awaitility:awaitility` ("awaitility")
- `testcontainers.version` property pinned to `1.21.4` (the documented Docker-API-handshake fix,
  identical reason in every sibling's own pom comment — not service-specific).

**Deliberately NOT added this task** (deferred to the task that actually needs it, per the Phase 0
finding): ShedLock (`net.javacrumbs.shedlock:*` — task 8, invoice expiry sweep) and
`jackson-dataformat-yaml` (task 24, OpenAPI contract test). The KMS SDK is never added (L3).

Config keys: none (task 3). Contracts: the header's listed contracts
(`contracts/api/crypto-internal.yaml`, `contracts/api/payments.yaml`,
`contracts/events/payments/*`) describe this *service's* eventual surface, not anything this
specific task reads or writes — no contract file is touched by T01.

## Acceptance Criteria

- **AC1** (task statement, part 1): `services/payment` is registered in the root `<modules>`,
  after the three existing services, with the same "dependency order" comment already present —
  payment-service depends on nothing else in this repo (no `libs/java/*` module exists yet it would
  need to precede), so placement is additive-at-the-end, not a reordering.
- **AC2** (task statement, part 2): `services/payment/pom.xml` exists with exactly the dependency
  set above — present, versioned/managed where the siblings version/manage theirs, and absent where
  this task deliberately excludes something (ShedLock, jackson-yaml, KMS, web3j/Tron,
  oauth2-authorization-server).
- **AC3** (task statement's own explicit KMS exclusion, L3): no KMS artifact
  (`<artifactId>kms</artifactId>`) appears anywhere in `services/payment/pom.xml`.
- **AC4** (precedent, not stated verbatim but required to avoid a known failure): a bare
  `PaymentServiceApplication` class exists so `mvn -pl services/payment verify` can reach the
  `repackage` goal at all.
- **AC5** (build plugins, implied by "flyway" in the task's own list plus every sibling's identical
  convention): `spring-boot-maven-plugin` registered (no `<configuration>` needed); the Flyway
  Maven plugin registered local-dev-only (no `<executions>` binding), `<schemas>payments</schemas>`,
  same admin-role JDBC URL/credentials every sibling's pom already uses verbatim.

## Tests required

No pre-mapped named test exists in `package.md` §8 for T01 (that list is organized by requirement
ID, and T01 has none — same as every sibling service's own T01). Per the repo's own established
`T01SkeletonRegressionTest` convention (confirmed present, and later extended by T29, in both
`services/crypto` and `services/notification`), this task's own boundary tests are:

- `com.themistra.payment.T01SkeletonRegressionTest` (new file), asserting:
  - the root pom contains `<module>services/payment</module>` positioned after the three existing
    `<module>` entries, and the "dependency order" comment survives (AC1).
  - the module pom contains each of the dependencies listed above and the `testcontainers.version`
    property (AC2).
  - the module pom does **not** contain `<artifactId>kms</artifactId>`, `org.web3j`,
    `io.github.tronprotocol`, `oauth2-authorization-server`, `net.javacrumbs.shedlock`, or
    `jackson-dataformat-yaml` (AC2/AC3 negative proofs — the last two are a scope-discipline guard,
    not an L-decision, since nothing requires their *absence* forever, only their absence *now*).
  - `mvn -pl services/payment validate` (or an equivalent lightweight check reachable without
    Docker) succeeds, and the bare `PaymentServiceApplication` class exists (AC4).
  - shared dependency *versions* that this pom does carry (`testcontainers.version`,
    `archunit-junit5`, `awaitility`, the AWS SDK BOM) match `services/auth/pom.xml`'s own values —
    mirrors crypto's own `sharedDependencyVersionsStayAlignedWithAuthService`, narrowed to drop the
    `shedlock-spring` comparison (not present in this task's own pom yet, unlike crypto's).
  - no secret/credential string appears in either pom (L13) — a narrow negative-proof, not a
    general secrets-scanner (gitleaks already covers that in CI).

## Open Questions

None that block this task. Phase 0's one flagged item (whether to add the bare Application class)
is resolved above by direct citation to `crypto-service` T01's own documented precedent — not a
genuine blocker, just something the task statement's own text left implicit.
