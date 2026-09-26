<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T01 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T01 — Service skeleton & POM |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T01 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · Root pom module ordering is only partially verified

**Why it matters:** `rootPomRegistersNotificationServiceAfterAuthAndCrypto()` asserts `services/crypto` appears before `services/notification`, but it does not assert `services/auth` appears before `services/crypto` or, transitively, before `services/notification`. A future edit could reorder the root pom to `services/crypto`, `services/auth`, `services/notification` and still pass this test, violating the brief's AC1 intent that notification is appended after the existing two entries in their original dependency order.

**Suggested test:** Replace the single pairwise ordering assertion with a chained check:

```java
int authIdx = rootPom.indexOf("<module>services/auth</module>");
int cryptoIdx = rootPom.indexOf("<module>services/crypto</module>");
int notificationIdx = rootPom.indexOf("<module>services/notification</module>");
assertThat(authIdx).isLessThan(cryptoIdx);
assertThat(cryptoIdx).isLessThan(notificationIdx);
```

---

## Gap 2 · `micrometer-registry-prometheus` runtime scope is unguarded

**Why it matters:** The brief lists "actuator, prometheus" as required dependencies, mirroring `services/auth/pom.xml` and `services/crypto/pom.xml`, where `micrometer-registry-prometheus` is declared with `<scope>runtime</scope>`. A future regression could move it to compile scope, bloating the production classpath with a metrics registry that should only be activated at runtime. The current dependency-presence test only checks the artifactId string.

**Suggested test:** Extend `runtimeAndTestScopesAreCorrect()` to assert:

```java
assertThat(dependencyScope(pom, "io.micrometer", "micrometer-registry-prometheus")).isEqualTo("runtime");
```

---

## Gap 3 · Test-scoped dependencies' scopes are not verified

**Why it matters:** `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, all `org.testcontainers:*` modules, `archunit-junit5`, and `awaitility` are test-only dependencies. The current tests check their presence but not their scope. A scope regression to `compile` would leak test infrastructure into the production artifact without failing any existing test.

**Suggested test:** Expand `runtimeAndTestScopesAreCorrect()` (or add `testScopesAreCorrect()`) to assert each of the following is `test` scope:
- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.security:spring-security-test`
- `org.springframework.boot:spring-boot-testcontainers`
- `org.testcontainers:postgresql`
- `org.testcontainers:kafka`
- `org.testcontainers:junit-jupiter`
- `com.tngtech.archunit:archunit-junit5`
- `org.awaitility:awaitility`

---

## Gap 4 · AC4 (`mvn verify` through package/repackage) has no automated regression guard

**Why it matters:** AC4 requires `mvn -pl services/notification -am verify` to succeed through `package`/`repackage`. Today this is verified only by manual/CI execution (Phase 6/7/9). A future pom edit — e.g., misplacing the `spring-boot-maven-plugin`, adding a second main class, or breaking the parent inheritance — could cause `verify` to fail while all unit tests still pass. The existing `applicationClassIsBareWithOnlyTheMainMethod` checks that a `main` method exists, but it does not prove the executable jar is actually produced.

**Suggested test:** Add a Failsafe integration test (runs after `package`) that asserts `target/notification-service.jar` exists and is a non-empty file. This is the only test that can directly guard AC4.

---

## Gap 5 · No guard against extra production classes beyond the bare Application class

**Why it matters:** The brief's "Out" scope states "Any `@ConfigurationProperties` class, security config, schema migration, consumer, or any other production class beyond the bare Application class" are excluded from T01. The current test only reads `NotificationServiceApplication.java`; it does not verify that no other `.java` file exists under `src/main/java/com/themistra/notification`. A premature config class or entity added in T01 would not fail any test.

**Suggested test:** Add a test that walks `src/main/java/com/themistra/notification`, counts `.java` files, and asserts the set contains exactly `NotificationServiceApplication.java`.

---

## Gap 6 · AWS SDK BOM import mechanism is not verified

**Why it matters:** The brief requires SES to be added "using the identical `software.amazon.awssdk:bom` dependencyManagement import". `sharedDependencyVersionsStayAlignedWithAuthAndCrypto()` checks the BOM version string, but it does not verify that the BOM is declared inside `<dependencyManagement>` with `<type>pom</type>` and `<scope>import</scope>`. A pom that declared the BOM as a regular dependency with that version would pass the version-alignment test while violating the intended import mechanism.

**Suggested test:** Assert the notification pom contains a `dependencyManagement` block whose content includes:

```xml
<groupId>software.amazon.awssdk</groupId>
<artifactId>bom</artifactId>
<version>2.50.2</version>
<type>pom</type>
<scope>import</scope>
```

A scoped extraction of the `dependencyManagement` block (similar to the existing `pluginBlock()` helper) would make this robust.

---

## Summary

The T01 regression-guard suite is now strong on dependency presence/absence, sibling-service version alignment, SES client constructability, Flyway lifecycle binding, and the bare Application class. The remaining gaps are precision and scope-creep guards: fully chaining the root-pom module order, verifying runtime/test scopes, adding a packaging existence test for AC4, guarding against extra production classes, and verifying the AWS BOM is imported rather than merely present at the right version. None of these gaps indicate a current implementation defect — the pom and Application class are correct today — but closing them would make the regression suite complete against the acceptance criteria.
