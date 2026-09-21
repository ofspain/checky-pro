# crypto · T27 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T27 — Run full suite / Dockerfile |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` + `services/crypto/Dockerfile` + `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java` |
| **Produces** | `artifacts/08-independent-review.md` |

---

## Findings

### 1. Dockerfile exposes port 8080, but the service defaults to 8082

- **Issue:** The Dockerfile mirrors `services/auth/Dockerfile` exactly and exposes 8080. `services/crypto/src/main/resources/application.properties` sets `server.port=${SERVER_PORT:8082}`. If the container is run without overriding `SERVER_PORT`, the application listens on 8082 while the image metadata advertises 8080.
- **Evidence:** `services/crypto/Dockerfile:16` (`EXPOSE 8080`) vs. `services/crypto/src/main/resources/application.properties:9` (`server.port=${SERVER_PORT:8082}`).
- **Recommendation:** Change `EXPOSE 8080` to `EXPOSE 8082` to match the service default, or add `ENV SERVER_PORT=8080` to the runtime stage and keep `EXPOSE 8080`. Update the leading build comment if the internal port changes.
- **Confidence:** High

### 2. Dockerfile still compiles test sources during the production image build

- **Issue:** `RUN mvn -q -pl services/crypto package -DskipTests` skips test execution but still compiles test sources. For a production image this is unnecessary build work and exposes the build to test-only fixtures.
- **Evidence:** `services/crypto/Dockerfile:10`.
- **Recommendation:** Use `mvn -q -pl services/crypto package -Dmaven.test.skip=true` for the image-build stage so neither test compilation nor test execution occurs. Keep the `mvn test` / `mvn verify` runs outside the Dockerfile for CI.
- **Confidence:** Low

### 3. KMS-SDK rule allows any `KmsSigner*` class inside `attest` to use the SDK

- **Issue:** `onlyKmsSignerMayUseTheKmsSigningSdk` permits any class whose simple name starts with `KmsSigner` and resides inside `com.themistra.crypto.attest..` to depend on the KMS SDK. This is broader than "only `KmsSigner` itself may call `kms:Sign`." A future `com.themistra.crypto.attest.KmsSignerHelper` could bypass `KmsSigner` and call the SDK directly while satisfying the rule.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java:123-134`.
- **Recommendation:** Either restrict the exception to an exact allowlist (`KmsSigner`, `KmsSignerTest`, `KmsSignerLocalStackIntegrationTest`) or document in the brief/artifact that the rule intentionally permits any `KmsSigner*` class in `attest` as a trade-off for maintainability.
- **Confidence:** Low

### 4. Negative-proof assertions do not verify the failure message

- **Issue:** `bothRulesActuallyFailAgainstAGenuineViolation` checks only that an `AssertionError` is thrown. It does not verify that the error message names the violating class or the forbidden dependency, so a failure from an unrelated assertion could satisfy the test.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java:167-172`.
- **Recommendation:** Add message assertions, e.g. `.hasMessageContaining("RogueAttestReferencer")` and `.hasMessageContaining("KmsSigner")` / `.hasMessageContaining("KmsClient")`.
- **Confidence:** Low

### 5. No smoke test that the built image starts or listens on the exposed port

- **Issue:** The Dockerfile builds the jar, but T27 includes no check that the image can start. The service requires external runtime config (DB, Kafka, JWT issuer, chain providers) and defaults to port 8082, so a naive `docker run` would not demonstrate health even if the image is structurally correct.
- **Evidence:** `services/crypto/Dockerfile` has no `HEALTHCHECK` or startup verification; `services/crypto/src/main/resources/application.properties` requires externalized config.
- **Recommendation:** Document in the brief that image-build verification is the scope of T27; runtime smoke testing is intentionally deferred to deployment config provisioning. If feasible in a future task, add a CI step that runs the image with test env vars and checks `/actuator/health`.
- **Confidence:** Low

### 6. Allowlist regression guard assumes `EndToEndIntegrationTest` remains in `com.themistra.crypto.watch`

- **Issue:** `allowlistedKmsSignerCrossModuleReferenceStillExistsInCode()` hard-codes the package `"com.themistra.crypto.watch"` and the class name string. If the test is moved or renamed, this guard will throw `IllegalArgumentException` rather than failing with a clear "stale allowlist" message.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java:182-184`.
- **Recommendation:** Catch the missing-class case and fail with an assertion message that explicitly says the allowlist entry is stale, e.g.:
  ```java
  assertThat(watchPackage.contains("com.themistra.crypto.watch.EndToEndIntegrationTest"))
          .as("EndToEndIntegrationTest no longer exists; remove its stale allowlist entry")
          .isTrue();
  ```
- **Confidence:** Low

### 7. The ArchUnit scan covers tests, which is correct but undocumented in the verification report

- **Issue:** `KmsSignerArchitectureTest` intentionally scans both main and test sources (no `ImportOption.DoNotIncludeTests`). This catches rogue test references but also means legitimate test-only KMS SDK usage inside attest must be named `KmsSigner*`. This design decision is buried in the Javadoc and not restated in the Phase 10 traceability matrix.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/attest/KmsSignerArchitectureTest.java:136-139`.
- **Recommendation:** Add a one-line note to `artifacts/10-test-generation.md` confirming that the KMS SDK rule scans test sources and that the `KmsSigner*` naming exception applies to tests as well as production code.
- **Confidence:** Low

---

## Open Questions

- **Port choice:** Should the Dockerfile expose 8082 (service default) or force 8080 via `ENV SERVER_PORT=8080` for consistency with auth? Either is acceptable, but the current mismatch is not.
- **Exact vs. prefix KMS SDK exception:** Is the intentional widening to any `KmsSigner*` class in `attest` an acceptable risk, or should it be tightened to a closed allowlist?
