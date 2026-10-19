# payment · T27 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

There is no schema, DAO, service or API layer. Both files below trace to the frozen brief's Files
section.

## Files to create

`services/payment/Dockerfile`, following D1 and D2. Its top comment gives the repo-root build
command, and says why all four module poms are copied (FU-T27-1).

**Build stage** (`maven:3.9-eclipse-temurin-21 AS build`, `WORKDIR /workspace`):
1. `COPY pom.xml ./`
2. `COPY services/auth/pom.xml services/auth/`
3. `COPY services/crypto/pom.xml services/crypto/`
4. `COPY services/notification/pom.xml services/notification/`
5. `COPY services/payment/pom.xml services/payment/`
6. `RUN mvn -q -pl services/payment dependency:go-offline`
7. `COPY services/payment/src services/payment/src`
8. `RUN mvn -q -pl services/payment package -DskipTests`

**Runtime stage** (`gcr.io/distroless/java21-debian12:nonroot`, `WORKDIR /app`):
1. `COPY --from=build /workspace/services/payment/target/payment-service.jar app.jar`
2. `USER nonroot`
3. `EXPOSE 8083`
4. `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]`

## Files to modify

`services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`:
- **New constant:** `private static final Path DOCKERFILE = Path.of("Dockerfile");`. Like the
  existing `MODULE_POM`, it is relative to the module directory, which is Surefire's working
  directory.
- **New `@Test`:** `void paymentDockerfileHonoursBuildContextAndSecurityRules()`. It works on the
  Dockerfile with its `#` comment lines stripped. It checks:
  - **Final stage:** the last `FROM` line equals `FROM gcr.io/distroless/java21-debian12:nonroot`,
    and `USER nonroot` appears after it.
  - **No broad copy:** no line matches `^COPY\s+\.\s` or `^ADD\s`.
  - **No `ENV`:** no line matches `^ENV\s` (L13).
  - **Module poms:** for each `<module>(.+?)</module>` in the comment-stripped root pom, the
    Dockerfile has `COPY <m>/pom.xml <m>/`. The module list is also asserted to be non-empty, so
    a regex mistake can't make the loop pass vacuously.
  - **Port:** a line equals `EXPOSE 8083`.
- **New private helper:** `stripDockerfileComments(String)`, which drops lines whose trimmed
  form starts with `#`.

## Methods, entities, repositories, services

- **Public:** none.
- **Private:** the helper above.
- **Entities, repositories and services:** none.

## Tests required

- The D4 test above.
- **AC5 mutations** (temporary, then restored):
  - remove the `crypto` pom `COPY` line; the test must fail
  - add `ENV FOO=bar`; the test must fail

## Execution order

1. Write the Dockerfile.
2. Add the T01 test, then run `mvn -o -q test -Dtest=T01SkeletonRegressionTest`.
3. Run the AC5 mutations, then restore.
4. **AC1:** `mvn -o -pl services/payment verify` from the repo root. Expect 497/497 and the jar.
5. **AC2:** `docker build -f services/payment/Dockerfile -t payment-service .`
6. **AC3:** `docker image inspect payment-service`, then `docker run --rm --entrypoint sh
   payment-service`, which must fail.
7. **AC4:** the D3 smoke start, as below.
   - **Infrastructure:** create a network `t27-net`; run `postgres:16-alpine`
     (`POSTGRES_DB=checky`, `POSTGRES_USER=checky`, `POSTGRES_PASSWORD=t27`, published on a
     free host port for Flyway); run `apache/kafka:3.8.0` in KRaft mode with network listeners.
   - **Migration:** `mvn -o -pl services/payment flyway:migrate`, with
     `-Dflyway.url=jdbc:postgresql://localhost:<port>/checky -Dflyway.user=checky
     -Dflyway.password=t27`. Then `ALTER ROLE payment_app PASSWORD`.
   - **Start:** run the image with `--read-only --tmpfs /tmp`, `-p <free>:8083`, and these env
     vars:
     - `DB_URL=jdbc:postgresql://t27-pg:5432/checky`
     - `DB_USERNAME=payment_app`
     - `DB_PASSWORD=…`
     - `KAFKA_BOOTSTRAP_SERVERS=t27-kafka:9092`
     - `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI=http://t27-auth.invalid/oauth2/jwks`
   - **Check:** poll `/actuator/health` until `UP`, then grep the logs for
     `partitions assigned`.
   - **Teardown:** remove the containers, the network and the image.
