# crypto · T27 · Phase 5 — Implementation Plan

## Files to create

- `services/crypto/Dockerfile`

## Files to modify

None.

## Exact file content (pinned, adapted from `services/auth/Dockerfile` with only the module path,
jar name, and comment updated — port confirmed unchanged at 8080 per Phase 4):

```dockerfile
# Build from the repo root (monorepo — needs the parent POM):
#   docker build -f services/crypto/Dockerfile -t crypto-service .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY services/crypto/pom.xml services/crypto/
RUN mvn -q -pl services/crypto dependency:go-offline
COPY services/crypto/src services/crypto/src
RUN mvn -q -pl services/crypto package -DskipTests

FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/services/crypto/target/crypto-service.jar app.jar
USER nonroot
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

No `ENV` lines carrying secrets or environment-specific values (L13) — matches auth's own file exactly
in this respect; runtime configuration (`DB_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `AUTH_ISSUER_URI`, and
this service's own KMS/screening/chain-provider config) remains externally supplied at container
run-time, never baked into the image.

## Public methods (signatures)

N/A — this task authors no Java code.

## Private methods

N/A.

## Entities / Repositories / Services used

N/A.

## Unit/integration tests required

None (Phase 1/2, unchanged) — this task's own "test" is the verification commands themselves.

## Execution order

1. Create `services/crypto/Dockerfile` with the exact content above.
2. Run `mvn -pl services/crypto -am verify` in this environment and record the real result — per
   Phase 4's own confirmed finding, this triggers `test` + `package` only (no failsafe/integration-test
   phase exists for this module), so this single command *is* the full, real scope of AC1 achievable
   here, not a partial substitute for a larger command this environment can't run.
3. Attempt `docker build -f services/crypto/Dockerfile -t crypto-service .` from the repo root; since
   Docker remains unavailable in this environment (confirmed at every phase since T23), this step is
   expected to fail at the daemon-connection level, not at any Dockerfile-content level — record the
   exact failure mode to confirm it's an environment limitation, not a Dockerfile defect.
4. Review the Dockerfile's own correctness by direct construction/comparison against auth's already-
   proven-working file — the only verification method available for AC2/AC3 given step 3's outcome.
5. Write Phase 6's implementation notes documenting exactly what ran, what passed, what was blocked and
   why, per Finding #5's now-explicit scoping (build-verification only, no runtime smoke test).
