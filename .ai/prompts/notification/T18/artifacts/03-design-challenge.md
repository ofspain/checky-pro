<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T18 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T18 — Dockerfile + build/image checkpoint |
| **Spec section** | Deployment rule in `agents.md` |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` + sibling Dockerfiles + root/notification POMs |
| **Produces** | `artifacts/03-design-challenge.md` |

Phase 3 adversarial design review of the T18 brief before implementation.

---

## Finding 1 · The sibling-pom reactor bug is the only material design problem

**Challenge:** `services/auth/Dockerfile` and `services/crypto/Dockerfile` copy only their own module's `pom.xml` plus the root `pom.xml`. The root `pom.xml` declares `<modules>services/auth</modules>`, `services/crypto</modules>`, `services/notification</modules>`. When Maven constructs the reactor for `-pl services/notification`, it must be able to read every declared module's POM, even those not being built. If `services/auth/pom.xml` or `services/crypto/pom.xml` is missing from the build context, Maven fails validation before it can build notification.

**Evidence:**
- Root `pom.xml` lines 21–27 list all three service modules.
- Auth Dockerfile lines 6–7: copies only root `pom.xml` and `services/auth/pom.xml`.
- Crypto Dockerfile lines 6–7: copies only root `pom.xml` and `services/crypto/pom.xml`.

**Resolution:** The notification Dockerfile must copy every sibling module's `pom.xml` before any Maven command runs:
```dockerfile
COPY pom.xml ./
COPY services/auth/pom.xml services/auth/
COPY services/crypto/pom.xml services/crypto/
COPY services/notification/pom.xml services/notification/
```
This is the "one real fix" the brief calls out.

---

## Finding 2 · Exposed port should match this service's actual default port

**Challenge:** The sibling Dockerfiles `EXPOSE 8080`, which matches auth's explicit `server.port=8080` and crypto's default Spring Boot port. Notification's own `application.properties` sets `server.port=8082`. If the Dockerfile `EXPOSE`s 8080 while the service listens on 8082, the image metadata is misleading.

**Evidence:**
- `services/notification/src/main/resources/application.properties` line 9: `server.port=${SERVER_PORT:8082}`.
- Auth and crypto Dockerfiles both `EXPOSE 8080`.

**Resolution:** Expose `8082` in `services/notification/Dockerfile`. This is a small, service-correct deviation from the literal sibling structure. The brief's mandate to mirror structure is best read as "two-stage, distroless, non-root, same build commands," not "blindly copy every literal line including the port."

---

## Finding 3 · The build must be invoked from the repo root

**Challenge:** The Dockerfile relies on `COPY pom.xml ./` and relative `services/notification/...` paths. A `docker build` run from inside `services/notification` would fail because the root `pom.xml` and sibling poms would not be in the build context.

**Evidence:**
- Auth/crypto Dockerfile comments explicitly say "Build from the repo root."
- The notification Dockerfile should include the same comment.

**Resolution:** Add a comment at the top of the Dockerfile:
```dockerfile
# Build from the repo root (monorepo — needs the parent POM and every sibling module POM):
#   docker build -f services/notification/Dockerfile -t notification-service .
```

---

## Finding 4 · No production code or dependency changes

**Challenge:** The task only creates a Dockerfile. There is no need to modify `pom.xml`, source code, or spec files.

**Resolution:** Confirm no files other than `services/notification/Dockerfile` are created or modified. The existing `<finalName>notification-service</finalName>` already produces the jar name the Dockerfile will copy.

---

## Finding 5 · Verification cannot be performed in this environment

**Challenge:** This workspace does not have `mvn` or Docker available. The brief's AC1 and AC2 require real command execution.

**Resolution:** Document that verification (both `mvn -pl services/notification clean verify` and `docker build ...`) must be executed in an environment with Maven and Docker before the task is reported complete. The design-challenge artifact does not gate execution; it only records the intended design.

---

## Finding 6 · `read-only rootfs` stays out of the Dockerfile

**Challenge:** `agents.md` mentions "read-only rootfs" as part of the deployment rule, but neither sibling Dockerfile enforces it. It is conventionally set at pod/runtime level, not image-build level.

**Resolution:** Do not add `READONLY` or similar flags to the Dockerfile. Mirror the sibling structure: runtime hardening is left to the Kubernetes pod spec/CDK, out of this task's scope.

---

## Decisions Made

1. **New file:** `services/notification/Dockerfile`.
2. **Two-stage build:**
   - Build: `maven:3.9-eclipse-temurin-21`.
   - Runtime: `gcr.io/distroless/java21-debian12:nonroot`.
3. **Sibling pom fix:** Copy `pom.xml`, `services/auth/pom.xml`, `services/crypto/pom.xml`, and `services/notification/pom.xml` before `mvn` runs.
4. **Build commands:**
   - `mvn -q -pl services/notification dependency:go-offline`
   - `mvn -q -pl services/notification package -DskipTests`
5. **Runtime user:** `USER nonroot`.
6. **Exposed port:** `8082` (matches notification's default `server.port`).
7. **Jar copy:** `COPY --from=build /workspace/services/notification/target/notification-service.jar app.jar`.
8. **Entrypoint:** `java -XX:MaxRAMPercentage=75.0 -jar app.jar`.
9. **No changes to sibling Dockerfiles, root POM, notification POM, or spec files.**
10. **Verification:** must run `mvn -pl services/notification clean verify` and `docker build -f services/notification/Dockerfile -t notification-service .` in a properly equipped environment.
