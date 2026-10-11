# payment · T01 · Phase 3 — Design Challenge Findings

Artifact: `.ai/prompts/payment/T01/artifacts/03-design-challenge.md`  
Consumes: `.ai/prompts/payment/T01/artifacts/02-task-implementation-brief.md`  
Authoritative standing rules: `spec/payment-service/agents.md`

Scoped to T01 only. Findings are brief-challenge items only; no redesign or implementation is proposed.

---

### 1. L13 and AC5 directly conflict over the Flyway plugin password

- **Issue:** AC5 instructs the payment POM to use "the same admin-role JDBC URL/credentials as every sibling pom", and every sibling (`services/auth/pom.xml`, `services/crypto/pom.xml`, `services/notification/pom.xml`) hard-codes `<password>checky-local-only</password>` in the `flyway-maven-plugin` block. L13 (a LOCKED decision) states "no secret/credential string in either pom".
- **Severity:** High — a LOCKED decision would be violated by following the acceptance criterion verbatim.
- **Evidence:** `spec/payment-service/design.md` L13; `02-task-implementation-brief.md` AC5; `services/auth/pom.xml` lines 196–200; `services/crypto/pom.xml` lines 157–160; `services/notification/pom.xml` lines 186–189.
- **Recommended brief amendment:** Reconcile L13 and AC5. Prefer keeping L13 authoritative: remove the password from the POM and require it to be supplied via a Maven property/settings profile or local environment, e.g. `${flyway.password}`. If the local-dev-only placeholder is intentionally not treated as a secret, add an explicit L13 exception for that single local-only Flyway password so the brief does not contradict itself.

---

### 2. AWS SDK BOM version is not pinned or aligned

- **Issue:** AC2 says to add "the AWS SDK BOM in `dependencyManagement`" but never states the version. All sibling services that consume AWS SDK artifacts (`auth`, `crypto`, `notification`) import `software.amazon.awssdk:bom` at `2.50.2`. The required regression test only aligns `testcontainers.version`, `archunit-junit5`, and `awaitility` with `services/auth/pom.xml`, so the BOM version could drift silently.
- **Severity:** Medium — version drift could break the S3 client later or produce different transitive HTTP-client artifacts across services.
- **Evidence:** `services/auth/pom.xml` lines 30–36; `services/crypto/pom.xml` lines 40–46; `services/notification/pom.xml` lines 30–36; `02-task-implementation-brief.md` AC2 and Required Tests.
- **Recommended brief amendment:** In AC2, pin the AWS SDK BOM version to `2.50.2` (matching siblings). In Required Tests, add an assertion that the payment POM's `software.amazon.awssdk:bom` version equals the version declared in `services/auth/pom.xml`/`services/crypto/pom.xml`/`services/notification/pom.xml`.

---

### 3. "Bare" `PaymentServiceApplication` is ambiguous about the `main` method

- **Issue:** The brief says "Add a bare `PaymentServiceApplication` class (no behavior, no extra annotations)". A class with only `@SpringBootApplication` and no `main` method will still fail `spring-boot:repackage` for want of a main class. AC4/AC5 require `compile` and `repackage` to succeed, but the wording could be read as forbidding even the standard `SpringApplication.run` entry point.
- **Severity:** Medium — likely to cause a build failure if interpreted literally.
- **Evidence:** `02-task-implementation-brief.md` Scope / AC4 / AC5; `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` explicitly checks for `SpringApplication.run(NotificationServiceApplication.class, args);` because that is the known failure mode.
- **Recommended brief amendment:** Clarify that "bare" means no domain code and no annotations beyond `@SpringBootApplication`, but the class must still contain the standard `public static void main(String[] args)` method calling `SpringApplication.run(PaymentServiceApplication.class, args);`.

---

### 4. Parent POM `<relativePath>` is not specified

- **Issue:** `services/payment/pom.xml` must declare its parent. Maven's default `<relativePath>` is `../pom.xml`; for `services/payment` that resolves to `services/pom.xml`, which does not exist. The sibling service POMs all set `<relativePath>../../pom.xml</relativePath>`. The brief never mentions this.
- **Severity:** Medium — `mvn -pl services/payment validate` from the repo root may work via reactor resolution, but a direct `mvn -f services/payment/pom.xml validate` would fail.
- **Evidence:** `services/auth/pom.xml` line 11; `services/crypto/pom.xml` line 11; `services/notification/pom.xml` line 11; `02-task-implementation-brief.md` AC2/AC4.
- **Recommended brief amendment:** Add to AC2 or Outputs that `services/payment/pom.xml` declares `<relativePath>../../pom.xml</relativePath>` in its parent block.

---

### 5. AC4's "`mvn validate`-equivalent check" is undefined

- **Issue:** Required Tests says the regression test asserts AC4 via "a successful `mvn -pl services/payment validate`-equivalent check". It is unclear whether the test should shell out to Maven, rely on the fact that `mvn test` already compiled the code, or simply inspect the `PaymentServiceApplication` source.
- **Severity:** Medium — ambiguity leads to inconsistent test implementations between services and may make the test slow or fragile.
- **Evidence:** `02-task-implementation-brief.md` Required Tests; sibling T01 tests do not invoke Maven from JUnit.
- **Recommended brief amendment:** Define the "equivalent check" explicitly, e.g. "verify that `PaymentServiceApplication.java` exists in `com.themistra.payment`, carries only `@SpringBootApplication`, and contains a valid `main` method; the test itself runs under the normal Maven lifecycle so compilation is already proven."

---

### 6. "Mirroring `services/auth`" is an imprecise template instruction

- **Issue:** The task statement says to "Create `pom.xml` mirroring `services/auth`", but `services/auth/pom.xml` contains several auth-only dependencies (`oauth2-authorization-server`, `kms`, `shedlock-spring`, `bucket4j_jdk17-core`). The brief correctly excludes some of them, but the headline wording invites copy-paste errors.
- **Severity:** Low/Medium — the explicit exclusion list mitigates it, but the instruction is still misleading.
- **Evidence:** `02-task-implementation-brief.md` Task statement and Scope/Out; `services/auth/pom.xml` lines 54, 93–96, 111–119, 123–126.
- **Recommended brief amendment:** Change the template reference from `services/auth/pom.xml` to "the resource-server-only subset represented by `services/crypto/pom.xml` and `services/notification/pom.xml`", while keeping the explicit exclusions.

---

### 7. AC3's prohibited-dependency list omits `bucket4j`

- **Issue:** AC3 lists artifacts that must not appear (`kms`, `org.web3j`, `io.github.tronprotocol`, `oauth2-authorization-server`, `net.javacrumbs.shedlock`, `jackson-dataformat-yaml`). It does not list `com.bucket4j:bucket4j_jdk17-core`, which is auth-specific rate-limiting and is present in the `services/auth` template the brief references.
- **Severity:** Low — not in scope, but the exclusion list should be complete given the "mirror auth" wording.
- **Evidence:** `services/auth/pom.xml` lines 123–126; `02-task-implementation-brief.md` AC3.
- **Recommended brief amendment:** Add `com.bucket4j` to the AC3 prohibited dependencies list.

---

### 8. No empirical check that the S3 SDK resolves a constructible client

- **Issue:** Notification's T01 includes a real `SesV2Client` construction test to prove the SES BOM + dependency resolves a working HTTP client. Payment's T01 adds `software.amazon.awssdk:s3` but the required tests do not include an equivalent `S3Client` construction check. A broken or incomplete AWS SDK BOM configuration would only surface in T18 (S3 WORM store).
- **Severity:** Low — defers failure discovery to a later task.
- **Evidence:** `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` `sesV2ClientCanActuallyBeConstructed()`; `02-task-implementation-brief.md` AC2/Required Tests.
- **Recommended brief amendment:** Add an optional acceptance criterion or required regression test that constructs an `S3Client` with dummy credentials (no network call) to prove the dependency resolves.

---

### 9. Flyway Maven plugin version alignment is not verified

- **Issue:** AC5 says the Flyway Maven plugin block "mirrors ... every sibling pom" (version `11.7.2`). The Required Tests section only verifies `testcontainers.version`, `archunit-junit5`, and `awaitility`. A future edit could change the plugin version without failing the regression test.
- **Severity:** Low — drift would be caught by later schema tasks, but T01 is supposed to freeze the skeleton.
- **Evidence:** `services/auth/pom.xml` lines 192–194; `services/crypto/pom.xml` lines 153–155; `services/notification/pom.xml` lines 182–184; `02-task-implementation-brief.md` AC5/Required Tests.
- **Recommended brief amendment:** Extend the version-alignment assertion in Required Tests to include the `flyway-maven-plugin` version, matching `services/auth/pom.xml`.

---

### 10. `<artifactId>` and `<finalName>` relationship is unstated

- **Issue:** AC5 specifies `<finalName>payment-service</finalName>`, but the brief never states that the module's `<artifactId>` should also be `payment-service`. Sibling services use a consistent `*-service` artifactId and finalName.
- **Severity:** Low — Maven requires an artifactId anyway; the omission is mostly a documentation gap.
- **Evidence:** `services/crypto/pom.xml` line 14 and 116; `services/notification/pom.xml` line 14 and 172; `02-task-implementation-brief.md` AC5.
- **Recommended brief amendment:** Add to Outputs or AC2 that the POM declares `<artifactId>payment-service</artifactId>`.
