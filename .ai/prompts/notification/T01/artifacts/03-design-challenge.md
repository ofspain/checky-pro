<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T01 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T01 — Service skeleton & POM |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the Phase 2 Task Implementation Brief. Findings only; accepted amendments are folded into the brief in Phase 4.

---

## Finding 1 · Missing standard test dependencies break `mvn verify`

**Severity:** High

**Evidence:** The TIB lists the shared dependency subset as `(web, validation, resource-server, data-jpa, flyway, postgres, spring-kafka, actuator, prometheus, testcontainers [postgresql, kafka, junit-jupiter], archunit, awaitility)`. It never mentions `spring-boot-starter-test`, `spring-security-test`, or `spring-boot-testcontainers`, yet `services/auth/pom.xml` and `services/crypto/pom.xml` all declare them in `test` scope. AC4 requires `mvn -pl services/notification -am verify` to pass through `test` and `package`. A JUnit regression test cannot compile without `spring-boot-starter-test` (JUnit Jupiter, AssertJ), and a resource-server service will later need `spring-security-test` for task 3 onward. Omitting `spring-boot-testcontainers` also breaks the established Testcontainers + Spring Boot 3.5.4 wiring pattern used by the sibling services.

**Recommended brief amendment:** Add the three test-scoped starters explicitly to the dependency set:
- `org.springframework.boot:spring-boot-starter-test`
- `org.springframework.security:spring-security-test`
- `org.springframework.boot:spring-boot-testcontainers`

And add positive assertions for all three to the regression-guard test.

---

## Finding 2 · "resource-server" is ambiguous

**Severity:** Medium

**Evidence:** The TIB says "resource-server" once in the shared subset list. Both `services/auth/pom.xml` and `services/crypto/pom.xml` declare **two** resource-server artifacts: `spring-boot-starter-oauth2-resource-server` and `spring-security-oauth2-resource-server`. A literal reading of the TIB could lead an implementer to include only the Spring Boot starter and omit the lower-level Spring Security artifact, which would produce a different classpath from the precedents and could break resource-server tests in task 3.

**Recommended brief amendment:** Replace the single word "resource-server" with the exact two artifacts copied from auth/crypto, e.g.:
- `spring-boot-starter-oauth2-resource-server`
- `spring-security-oauth2-resource-server`

Add positive assertions for both to the regression test, plus the negative assertion that `spring-boot-starter-oauth2-authorization-server` is absent.

---

## Finding 3 · Bare `@SpringBootApplication` without a `main` method may not repackage

**Severity:** High

**Evidence:** The TIB says the Application class is "bare `@SpringBootApplication`, no extra annotations" and that it exists so the module "actually packages". AC4 requires `mvn verify` to succeed through `package`/`repackage`. The Spring Boot Maven plugin needs a main class with a `public static void main(String[])` method to create an executable jar. A class that only carries `@SpringBootApplication` and no `main` method will either fail repackage or produce a jar that cannot be launched. The crypto-service T01 lesson (cited in the TIB) was about avoiding premature annotations, not about omitting the `main` method.

**Recommended brief amendment:** Explicitly require a standard `main` method:

```java
public static void main(String[] args) {
    SpringApplication.run(NotificationServiceApplication.class, args);
}
```

Add a regression-guard assertion that the source file contains `SpringApplication.run`.

---

## Finding 4 · AWS SDK v2 SES client needs an HTTP client implementation

**Severity:** Medium

**Evidence:** The TIB resolves O2/Q2 with `software.amazon.awssdk:sesv2` under the AWS SDK BOM. AWS SDK v2 service modules do not, by themselves, guarantee a usable HTTP client at runtime; an implementation such as `url-connection-client`, `apache-client`, or `netty-nio-client` must be on the classpath. The sibling services use `software.amazon.awssdk:kms` and `s3`; their working builds may rely on transitive HTTP-client resolution that is not guaranteed to be identical for `sesv2`. AC2 (`dependency:resolve`) only checks resolution, not that a `SesV2Client` can actually be constructed.

**Recommended brief amendment:** Either (a) add an explicit HTTP-client artifact under the same AWS BOM (e.g. `software.amazon.awssdk:url-connection-client`), or (b) strengthen AC2/AC4 to include a minimal context-load or bean-creation test that instantiates the SES client so the failure surfaces in this task rather than in task 12.

---

## Finding 5 · `<finalName>` is not specified

**Severity:** Low

**Evidence:** `services/auth/pom.xml` and `services/crypto/pom.xml` both set `<finalName>auth-service</finalName>` and `<finalName>crypto-service</finalName>` respectively. The TIB says to mirror auth's structure and does not list an exception for `<finalName>`. Leaving it unspecified causes the packaged artifact to default to the `<artifactId>` (`notification-service`), which is likely the intended name, but the inconsistency is an unstated assumption that could surprise downstream Docker/build scripts copied from the sibling services.

**Recommended brief amendment:** Explicitly require `<finalName>notification-service</finalName>` in the build section, matching the sibling-service convention.

---

## Finding 6 · Regression-guard scope is under-specified

**Severity:** Low

**Evidence:** The TIB states the regression test must prove: root pom registration, required dependencies present, issuer/outbox excluded, and the skeleton class exists correctly annotated. It does not say *how* to prove "correctly annotated" (source-file scan vs. reflection), nor does it enumerate the exact annotations to assert absent (`@ConfigurationPropertiesScan`, `@EnableScheduling`, `@EnableSchedulerLock`). Without that list, the test could pass while a premature annotation silently enters the skeleton.

**Recommended brief amendment:** Give the exact positive assertions (`@SpringBootApplication` present, `SpringApplication.run` present, package `com.themistra.notification`) and the exact negative assertions (`@ConfigurationPropertiesScan`, `@EnableScheduling`, `@EnableSchedulerLock` absent). Require a source-level scan, matching the style of `services/crypto/src/test/java/com/themistra/crypto/T01SkeletonRegressionTest.java`.

---

## Finding 7 · `dependencyManagement` AWS BOM import is implied but not listed as a required file element

**Severity:** Low

**Evidence:** The TIB says SES is added "using the identical `software.amazon.awssdk:bom` dependencyManagement import already present in both `services/auth/pom.xml` and `services/crypto/pom.xml`". However, the "Files to Create" / "Outputs" section does not explicitly call out a `dependencyManagement` block. Because the BOM import is the mechanism that gives `sesv2` its version and keeps it aligned with the sibling services, omitting it from the checklist is a latent drift risk.

**Recommended brief amendment:** Add to the `services/notification/pom.xml` checklist: a `dependencyManagement` section importing `software.amazon.awssdk:bom:2.50.2` with `type=pom` and `scope=import`. Add a regression test assertion that the BOM version matches `services/auth/pom.xml` (the T01SkeletonRegressionTest-style cross-service version-alignment check).

---

## Finding 8 · `flyway-maven-plugin` version and credentials are implied

**Severity:** Low

**Evidence:** The TIB says to add "the local-dev-only `flyway-maven-plugin` block, `<schemas>notifications</schemas>`". It does not explicitly state the plugin version, JDBC URL, user, or password, relying on "mirroring `services/auth/pom.xml`". Auth uses version `11.7.2`, `jdbc:postgresql://localhost:5432/checky`, `checky`/`checky-local-only`. Because the schema name is the only stated difference, an implementer might copy the whole block correctly, but the TIB should not assume this.

**Recommended brief amendment:** State explicitly that the plugin block mirrors auth except for `<schemas>notifications</schemas>`, and require version `11.7.2` plus the same local-only credentials. Add a regression assertion that the schema is `notifications` and the version matches auth.

---

## Finding 9 · No contract files exist for `payments` events

**Severity:** Medium (non-blocking, carried risk)

**Evidence:** The TIB correctly notes that `contracts/events/payments/` does not exist yet and is "not this task's problem". This is acceptable for T01, but it is an unstated ordering hazard for downstream tasks (6, 7, 15) that consume `payments.*` events. The brief's "Open Questions" section says the payments gap is "carried forward as a known, non-blocking risk for a later task", which is accurate, but it does not identify an owner or trigger condition in any later task.

**Recommended brief amendment:** Keep the statement that T01 does not create payment contracts, but add a tracker note in the Open Questions: "Task 6/7/15 cannot be considered complete until `contracts/events/payments/` exists and task 15's contract tests pass." This makes the dependency visible to the Phase 4 human reviewer without expanding T01 scope.

---

## Summary

The brief is directionally correct and consistent with `agents.md` and the existing LOCKED decisions. The highest-risk gaps are (1) omitted test dependencies and (3) the unstated `main` method, both of which will cause AC4 to fail. Findings 2, 4, and 7 are medium-risk ambiguities that could produce a pom that looks correct but diverges from the sibling-service precedent or fails at runtime. Findings 5, 6, 8, and 9 are low-risk polish/tracking items.
