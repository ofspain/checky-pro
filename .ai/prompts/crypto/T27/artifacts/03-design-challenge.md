# crypto · T27 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T27 — Run full suite / Dockerfile |
| **Spec section** | Final verification |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

---

## Findings

### 1. The Dockerfile is instructed to expose the wrong default port

- **Issue:** The TIB states `EXPOSE 8080` because "Spring Boot's default, no override found in `application.properties`." In fact, `services/crypto/src/main/resources/application.properties` sets `server.port=${SERVER_PORT:8082}`. A container built with `EXPOSE 8080` will listen on 8082 by default, so a naive `-p 8080:8080` mapping will not reach the application.
- **Severity:** Medium
- **Evidence:** `services/crypto/src/main/resources/application.properties:9` (`server.port=${SERVER_PORT:8082}`) vs. `services/auth/Dockerfile:16` (`EXPOSE 8080`) and the TIB's instruction to mirror it.
- **Recommended brief amendment:** Either expose 8082 in `services/crypto/Dockerfile` to match the service's own default, or set `ENV SERVER_PORT=8080` in the Dockerfile and keep `EXPOSE 8080`. Document the chosen internal port in the leading comment.

### 2. `mvn verify` does run integration tests, contrary to the brief's parenthetical

- **Issue:** The TIB says "no failsafe plugin changes what `verify` does beyond `test` + `package`." The crypto POM explicitly configures `maven-failsafe-plugin` with `integration-test` and `verify` goals, so `mvn -pl services/crypto verify` will execute `*IT` classes (including the Testcontainers-based `EndToEndIntegrationTest`).
- **Severity:** Medium
- **Evidence:** `services/crypto/pom.xml:118-141` (failsafe executions and `api.version` system property).
- **Recommended brief amendment:** Correct the statement. Distinguish `mvn -pl services/crypto test` (unit + ArchUnit, no Docker) from `mvn -pl services/crypto verify` (adds failsafe integration tests). Report both results separately when Docker is unavailable.

### 3. Build success depends on a compile-clean codebase that does not currently exist

- **Issue:** The TIB assumes the Dockerfile can simply run `mvn -q -pl services/crypto package -DskipTests` and succeed. The current branch does not compile: missing external dependencies (Tron trident, AWS KMS SDK, Kafka clients, ShedLock, S3) and internal API mismatches (`ProviderAnswer` generic vs. non-generic, `Watch` entity API vs. `Watcher`) block both `compile` and `test-compile`.
- **Severity:** High
- **Evidence:** `./mvnw -pl services/crypto -am compile test-compile -DskipTests` fails with multiple compilation errors; representative examples include `package org.tron.trident.core does not exist`, `package software.amazon.awssdk.services.kms does not exist`, and `type com.themistra.crypto.quorum.ProviderAnswer does not take parameters`.
- **Recommended brief amendment:** Add a prerequisite that T27 can only succeed after prior tasks leave `services/crypto` compiling cleanly. In the verification section, disclose the actual compile errors and whether they are environment-specific (missing downloads) or code-specific (API drift).

### 4. Local `mvn` binary is missing; only `./mvnw` is available

- **Issue:** The TIB repeatedly references `mvn -pl services/crypto ...`. In this environment, `mvn` is not on the PATH; only the wrapper `./mvnw` exists.
- **Severity:** Low
- **Evidence:** `mvn -pl services/crypto -am compile test-compile -DskipTests -q` returns `mvn: command not found`; `./mvnw` exists at the repo root.
- **Recommended brief amendment:** For local verification attempts, use `./mvnw`. Keep `mvn` for the Dockerfile build stage because the `maven:3.9-eclipse-temurin-21` image provides it.

### 5. No smoke test that the runtime image actually starts

- **Issue:** The Dockerfile builds the jar, but the brief does not include any check that the image can start. The crypto service refuses to start without runtime config (DB URL, Kafka bootstrap, JWT issuer, chain providers), so a simple `docker run` would fail even if the image is correct.
- **Severity:** Low
- **Evidence:** `services/crypto/src/main/resources/application.properties` requires `DB_URL`, `spring.kafka.bootstrap-servers`, `AUTH_ISSUER_URI`, and uncommented chain provider blocks.
- **Recommended brief amendment:** State explicitly that T27 verifies the *build* of the Docker image, not runtime startup, because the required external dependencies and secrets are not available in this environment. Runtime smoke testing belongs to deployment/infra tasks.

### 6. `dependency:go-offline` may not fully prime the build if dependencies are unresolved

- **Issue:** The Dockerfile runs `mvn -q -pl services/crypto dependency:go-offline` after copying the two POMs. If the compile errors in Finding #3 are caused by genuinely missing artifacts (e.g., Tron trident not resolvable), this step will fail in the Docker build too, before any source is copied.
- **Severity:** Low/Medium
- **Evidence:** Same compile failures as Finding #3; some are unresolved packages rather than source errors.
- **Recommended brief amendment:** Add that `dependency:go-offline` success is itself a verification step; if it fails, the root cause is a dependency resolution problem that must be resolved before the image can build.

---

## Open Questions

1. **Port choice:** Should the crypto Dockerfile expose 8082 (matching the service default) or force 8080 via `ENV SERVER_PORT=8080` for consistency with auth?
2. **Compile errors:** Are the current compilation failures due to incomplete dependency downloads in this offline environment, or due to unresolved code/API drift from merged branches? This determines whether T27 is blocked by infrastructure or by prior tasks.
3. **Scope of "full suite":** The task statement says `mvn -pl services/crypto verify` must pass, but the environment cannot run Docker-dependent failsafe tests. Should the brief propose `-DskipITs` for the local attempt, or only report the failure honestly?
