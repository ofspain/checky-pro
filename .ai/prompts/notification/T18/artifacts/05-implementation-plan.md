# notification · T18 · Phase 5 — Implementation Plan

One file. Every line traces to either an existing sibling precedent (structure) or a Phase 3/4
finding (the two real corrections: the sibling-pom fix, the port).

## File to create

### `services/notification/Dockerfile`

```dockerfile
# Build from the repo root (monorepo — needs the parent POM and every sibling module POM):
#   docker build -f services/notification/Dockerfile -t notification-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/auth/pom.xml services/auth/
COPY services/crypto/pom.xml services/crypto/
COPY services/notification/pom.xml services/notification/
RUN mvn -q -pl services/notification dependency:go-offline
COPY services/notification/src services/notification/src
RUN mvn -q -pl services/notification package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/notification/target/notification-service.jar app.jar
USER nonroot
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

Structurally identical to `services/auth/Dockerfile`/`services/crypto/Dockerfile` (two-stage,
`maven:3.9-eclipse-temurin-21` build, `gcr.io/distroless/java21-debian12:nonroot` runtime, `USER
nonroot`, the same `-XX:MaxRAMPercentage=75.0` entrypoint flag) with exactly two deliberate
departures, both already dispositioned at Phase 4:
- **Finding #1's fix**: `COPY`s all three modules' own `pom.xml` (not just notification's own)
  before any `mvn` command runs, so Maven's reactor validation for the root `pom.xml`'s own 3-module
  `<modules>` list succeeds.
- **Finding #2's fix**: `EXPOSE 8082`, matching `application.properties`'s own real
  `server.port=${SERVER_PORT:8082}` default, not `8080`.

## Files to modify

None.

## Execution order

1. Write `services/notification/Dockerfile` exactly as above.
2. Run `mvn -pl services/notification clean verify` fresh, confirming AC1 (expected: already true,
   continuously, since T17 — this is the final, explicit checkpoint, not a new achievement).
3. Run `docker build -f services/notification/Dockerfile -t notification-service .` from the repo
   root, confirming AC2 — the real, literal task requirement, executed directly in this agent's own
   environment (both `mvn` and `docker` confirmed available at Phase 0), not deferred or assumed.
4. If the build succeeds, confirm the image's own base layer and user via `docker inspect` or
   equivalent, closing AC3 with direct evidence, not an assumption that copying the right Dockerfile
   lines was sufficient.
