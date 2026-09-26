<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T01 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T01 — Service skeleton & POM |
| **Spec section** | Foundation |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T01 implementation. Findings only.

---

## Finding 1 · `flyway-maven-plugin` lifecycle binding is not regression-guarded

**Issue:** The regression test verifies the flyway plugin's version, URL, credentials, and schema, but does not verify that it is **not** bound to the Maven lifecycle. The brief explicitly requires "runs solely via explicit `mvn flyway:migrate`, never during `package`/`verify`/CI".

**Evidence:**
- `services/notification/pom.xml` lines 161–171: the plugin has no `<executions>` block today.
- `T01SkeletonRegressionTest.java` lines 93–100: `finalNameAndFlywayPluginMirrorTheSiblingConvention()` asserts `<artifactId>flyway-maven-plugin</artifactId>`, `<version>11.7.2</version>`, and `<schemas>notifications</schemas>`, but never asserts absence of `<executions>`.
- A future edit that adds `<executions><execution>...</execution></executions>` to the plugin would pass this regression test and only fail later in CI when `mvn verify` tries to connect to a non-existent local database.

**Recommendation:** Add a negative assertion inside `finalNameAndFlywayPluginMirrorTheSiblingConvention()` that the flyway plugin block contains no `<executions>` element. Use a scoped substring/extraction rather than a global `doesNotContain`, so the assertion remains precise to this plugin.

**Confidence:** Medium

---

## Finding 2 · `kafka` and `junit-jupiter` dependency assertions don't verify groupId

**Issue:** The dependency-presence test uses bare `<artifactId>kafka</artifactId>` and `<artifactId>junit-jupiter</artifactId>` substring checks. It does not confirm they belong to `org.testcontainers`, so a malformed pom with the wrong groupId would pass.

**Evidence:**
- `T01SkeletonRegressionTest.java` lines 76–77:
  ```java
  assertThat(pom).contains("<artifactId>kafka</artifactId>");
  assertThat(pom).contains("<artifactId>junit-jupiter</artifactId>");
  ```
- The same test already uses `hasGroupAndArtifact(pom, "org.testcontainers", "postgresql")` for the Postgres module, showing the preferred pattern exists but was not applied to `kafka` and `junit-jupiter`.

**Recommendation:** Replace the two bare artifactId checks with `hasGroupAndArtifact(pom, "org.testcontainers", "kafka")` and `hasGroupAndArtifact(pom, "org.testcontainers", "junit-jupiter")`.

**Confidence:** Medium

---

## Finding 3 · SES dependency groupId is not verified

**Issue:** The test checks that `<artifactId>sesv2</artifactId>` is present but never confirms the groupId is `software.amazon.awssdk`. A pom that declared the artifact under a different groupId would still pass the regression test.

**Evidence:**
- `T01SkeletonRegressionTest.java` line 68: `assertThat(pom).contains("<artifactId>sesv2</artifactId>");`
- The `sesV2ClientCanActuallyBeConstructed()` test indirectly proves the SDK class is on the classpath, but it does not prove the dependency came from the expected Maven coordinate; a test-scoped or transitive pull could satisfy it while the pom declaration itself was wrong.

**Recommendation:** Add `assertThat(hasGroupAndArtifact(pom, "software.amazon.awssdk", "sesv2")).isTrue();` to the dependency-presence test.

**Confidence:** Medium

---

## Finding 4 · Non-shared auth dependencies are not negatively asserted

**Issue:** The brief requires mirroring `services/auth/pom.xml`'s shared subset only. The regression test correctly excludes the OAuth2 issuer starter, but it does not guard against accidentally copying other auth-specific dependencies (`shedlock-*`, `bucket4j_jdk17-core`, `jackson-dataformat-yaml`).

**Evidence:**
- `T01SkeletonRegressionTest.java` line 81: only `doesNotContain("oauth2-authorization-server")` is checked.
- `services/auth/pom.xml` lines 110–126 and 174–179 declare auth-specific dependencies that must not appear in notification-service at this task.

**Recommendation:** Add negative assertions for `shedlock-spring`, `shedlock-provider-jdbc-template`, `bucket4j_jdk17-core`, and `jackson-dataformat-yaml` to the dependency-presence test. This makes the "shared subset only" rule enforceable by the build, not just by human code review.

**Confidence:** Low-Medium

---

## Finding 5 · `flyway-maven-plugin` version check is not scoped to the plugin

**Issue:** `finalNameAndFlywayPluginMirrorTheSiblingConvention()` checks `<version>11.7.2</version>` anywhere in the pom. If a future dependency or plugin also happens to use `11.7.2`, the test could still pass even if the flyway plugin's own version changed.

**Evidence:**
- `T01SkeletonRegressionTest.java` line 98: `assertThat(pom).contains("<version>11.7.2</version>");` is a global substring check.
- The test already locates the plugin block by checking `<artifactId>flyway-maven-plugin</artifactId>`, but it does not tie the version to that block.

**Recommendation:** Scope the version assertion to the flyway plugin block (e.g., extract the substring between `<plugin><groupId>org.flywaydb</groupId>` and the closing `</plugin>`, then assert the version inside it).

**Confidence:** Low

---

## Finding 6 · Scopes of runtime/test dependencies are not regression-guarded

**Issue:** The pom correctly declares `postgresql` as runtime scope and the Testcontainers modules as test scope, but the regression test only checks presence. A scope regression would not be caught.

**Evidence:**
- `services/notification/pom.xml` lines 75–79 and 120–134: `postgresql` is `<scope>runtime</scope>`; Testcontainers artifacts are `<scope>test</scope>`.
- `T01SkeletonRegressionTest.java` dependency-presence test only verifies strings, not scopes.

**Recommendation:** Add scope assertions for `org.postgresql:postgresql` (runtime) and `org.testcontainers:postgresql` (test). Keep the check narrow to avoid over-specifying items whose scope is less critical.

**Confidence:** Low

---

## Finding 7 · `flyway-maven-plugin` version is not included in cross-service alignment check

**Issue:** `sharedDependencyVersionsStayAlignedWithAuthAndCrypto()` aligns `testcontainers.version`, `archunit-junit5`, `awaitility`, and the AWS SDK BOM, but it does not align the `flyway-maven-plugin` version even though the brief says the plugin block mirrors auth/crypto.

**Evidence:**
- `T01SkeletonRegressionTest.java` lines 146–164: no check for `flyway-maven-plugin` version.
- `services/auth/pom.xml` and `services/crypto/pom.xml` both pin the plugin at `11.7.2`; the notification pom does too, but a future drift in auth/crypto would not be detected by notification's regression test.

**Recommendation:** Extend the version-alignment test to extract and compare the `flyway-maven-plugin` version across all three service poms, or add it to the sibling-convention test.

**Confidence:** Low

---

## Summary

The implementation matches the Phase 5/6 plan and correctly addresses the Phase 3 design-challenge findings (test dependencies, both resource-server artifacts, `main` method, `<finalName>`, AWS BOM import, Flyway plugin mirroring, and SES client constructability). The remaining findings are narrowness gaps in the regression-guard test itself: the most consequential is **Finding 1** (unbound Flyway plugin lifecycle), because it is the only one that could allow a silent CI breakage. Findings 2–7 tighten the test's precision and cross-service drift detection without expanding T01 scope.
