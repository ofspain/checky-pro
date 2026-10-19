# payment · T27 · Phase 0 — Repository Understanding

No design here: this is grounding only. I read everything below directly from source, or ran
it, on 2026-10-19 at branch `spec/service-specs-and-ai-framework` after T26 (`e1e183d8`).

## 1. Architecture summary

The architecture is unchanged since T26 Phase 0: package-by-feature `com.themistra.payment`,
JPA plus Flyway for the `payments` schema, outbox events to Kafka, a single JWT resource
server, and a `CryptoClient` over HTTP.

**Build structure (relevant to T27):**
- The root `pom.xml` (`com.themistra:checky-pro:0.0.1-SNAPSHOT`) has `spring-boot-starter-parent`
  3.5.4 as its parent. It lists 4 modules in order: `services/auth`, `services/crypto`,
  `services/notification`, `services/payment`.
- `services/payment/pom.xml` names the root as its parent (`<relativePath>../../pom.xml</relativePath>`).
  It sets `<finalName>payment-service</finalName>` and uses `spring-boot-maven-plugin`, which
  repackages to `target/payment-service.jar`.
- It also has a `flyway-maven-plugin` with **no executions bound**, so it never runs during
  `package` or `verify`.
- `server.port` defaults to `${SERVER_PORT:8083}`.
- There is no `.dockerignore` at the repo root.

## 2. Existing code this task touches

| Item | State |
|---|---|
| `mvn -pl services/payment verify` | **Already passes.** I ran it at Phase 0 from the repo root, offline: `Tests run: 496, Failures: 0, Errors: 0`. It built `services/payment/target/payment-service.jar` (about 105 MB, repackaged), giving `BUILD SUCCESS`. Docker and Testcontainers are required, and both are available. |
| `services/payment/Dockerfile` | **Does not exist.** Neither does any other payment Docker artifact. |
| Sibling Dockerfiles | `services/{auth,crypto,notification}/Dockerfile` all use one shape: a `maven:3.9-eclipse-temurin-21` build stage, which copies the root `pom.xml` plus module poms, runs `mvn -q -pl <svc> dependency:go-offline`, copies `src`, then runs `mvn -q -pl <svc> package -DskipTests`. The second stage is `gcr.io/distroless/java21-debian12:nonroot`, with `COPY` of the jar, `USER nonroot`, `EXPOSE`, and `ENTRYPOINT java -XX:MaxRAMPercentage=75.0 -jar app.jar`. Each is built from the repo root: `docker build -f services/<svc>/Dockerfile .`. |

No production Java code, no table and no config is touched. The deliverable is a Dockerfile plus
a verified build.

## 3. Established patterns to follow

- **Dockerfile:** the notification shape is the newest, and it is the only one that copies
  every *sibling* module `pom.xml`. That is the documented fix for the reactor-validation
  failure:
  > Maven validates every `<module>` the root declares, even with `-pl`. So the build context
  > must contain every module's `pom.xml`.
- **Standing rule (`agents.md`, Deployment section):**
  - a multi-stage Docker build to a distroless JRE 21
  - non-root, read-only rootfs
  - EKS with at least 2 replicas
  - scheduled jobs guarded by ShedLock
  - infrastructure in AWS CDK
- **Secrets (L13):** no secret goes into the image. Configuration comes from environment
  variables, through flat `application.properties` placeholders.

## 4. Testing conventions

The suite is unchanged: unit, then ArchUnit and contract, then Testcontainers integration.
`verify` runs Surefire only. The pom binds no Failsafe or other `verify`-phase plugin, so
`verify` amounts to `test` plus `package` (repackage).

## 5. Known gaps / unknowns

1. **The notification Docker build is now broken by payment's existence. I verified this at
   Phase 0.**
   - Running `docker build -f services/notification/Dockerfile .` from the repo root fails at
     the `dependency:go-offline` step: `Child module /workspace/services/payment of
     /workspace/pom.xml does not exist`.
   - Cause: payment T01 added `<module>services/payment</module>` to the root pom. Notification's
     Dockerfile copies auth, crypto and notification poms, but not payment's.
   - So **3 of 4 Dockerfiles** now fail. Crypto and auth were already logged as broken, in
     memory and in crypto T29.
   - This is outside T27's literal scope, which is payment's image only. Whether T27 also
     repairs the siblings, or logs them, is a **human decision for Phase 4**.
2. **The payment Dockerfile must copy all four module poms.** With today's root pom, a
   payment-only context fails the same way. That is the root cause of the three sibling
   failures.
   - **I do not know** whether the human prefers to keep copying every sibling pom, which is
     the notification precedent and fragile whenever a module is added, or a structural fix. A
     structural fix would touch other services and the root pom, and is out of scope.
3. **"Read-only rootfs" (`agents.md`)** is a runtime and deployment setting (the Kubernetes
   `securityContext`), not a Dockerfile one.
   - A distroless JRE app needs a writable `/tmp` for Tomcat's work directory and the JVM. Spring
     Boot and Tomcat write temp files there, and PDFBox may use `java.io.tmpdir`.
   - **I do not know** whether T27 should prove the image runs with `--read-only --tmpfs /tmp`.
     The task says only "Docker image builds".
4. **"Image builds" versus "image starts".**
   - The task says *builds*. A smoke start would need Postgres, Kafka, a JWKS and the required
     environment.
   - Like the siblings, the image can't serve `/actuator/health` without those dependencies.
   - **I do not know** whether a start check is wanted.
5. **`dependency:go-offline` with `-pl services/payment`** downloads plugin and dependency
   artifacts inside the build. Docker Hub and Maven Central are reachable from this machine:
   the notification probe pulled its base image and reached the Maven step.
6. **There is no `.dockerignore`.**
   - The context is the whole repo, including `frontend/`, `target/` folders, PDFs and scratch
     files. That makes builds slower, and stray files could be `COPY`'d.
   - The Dockerfiles copy only explicit paths, so nothing unintended lands in the image.
   - Adding a `.dockerignore` would be a repo-root file, which is cross-cutting and a Phase 4
     decision.
7. **`EXPOSE` port: 8083**, from `server.port` in `application.properties`.
