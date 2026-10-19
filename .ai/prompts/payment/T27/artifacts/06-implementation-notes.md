# payment · T27 · Phase 6 — Implementation Notes

Consumes:
- `artifacts/05-implementation-plan.md`
- `artifacts/04-frozen-task-brief.md` (FROZEN)

## What changed

| File | Change | Plan / brief |
|---|---|---|
| `services/payment/Dockerfile` | **Created.** Two-stage build. **Stage 1** (`maven:3.9-eclipse-temurin-21`): copies the root pom and all four module poms, runs `go-offline`, copies `services/payment/src`, then runs `package -DskipTests`. **Stage 2** (`gcr.io/distroless/java21-debian12:nonroot`): the jar becomes `app.jar`, with `USER nonroot`, `EXPOSE 8083` and the sibling entrypoint. The header comment gives the repo-root build command and explains why every module pom is copied. | D1, D2 |
| `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` | **Modified.** Adds the constant `DOCKERFILE = Path.of("Dockerfile")`, the new test `paymentDockerfileHonoursBuildContextAndSecurityRules()`, and the private helper `stripDockerfileComments(String)`. The test checks: at least 2 `FROM` lines; the last `FROM` is the distroless nonroot image, with `USER nonroot` after it; no `COPY . ` and no `ADD`; no `ENV`; each `<module>` in the comment-stripped root pom has its `COPY <m>/pom.xml <m>/` line, and the module list is asserted non-empty; `EXPOSE 8083`. The class goes from 8 tests to 9. | D4 (revised, F4) |

No other file changed (AC6). `git status` shows only the two files above, plus this phase's
artifacts and the user's own untracked `prompt` file, which T27 did not touch.

## Evidence per acceptance criterion

| AC | Command | Result |
|---|---|---|
| **AC5** (guard) | `mvn -o -q test -Dtest=T01SkeletonRegressionTest` | 9/9 pass. |
| | Mutation 1: delete `COPY services/crypto/pom.xml services/crypto/` | FAIL: "services/crypto/pom.xml must be in the build context". |
| | Mutation 2: add `ENV FOO=bar` | FAIL on the L13 no-`ENV` assertion. |
| | Restore | `cmp` against the saved copy matches. |
| **AC1** (verify) | `mvn -o -pl services/payment verify`, from the repo root | `BUILD SUCCESS`, Tests run: **497**, Failures 0, Errors 0, Skipped 0. Repackaged `services/payment/target/payment-service.jar` produced. |
| **AC2** (build) | `docker build -f services/payment/Dockerfile -t payment-service .`, from the repo root | Exit 0. Image size 157 MB. |
| **AC3** (image) | `docker image inspect payment-service` | `User=nonroot`. `ExposedPorts={"8083/tcp":{}}`. `Entrypoint=["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]`. `Env` holds only `PATH`, `SSL_CERT_FILE`, `LANG` and `JAVA_VERSION=21.0.10`, with no credentials. |
| | `docker run --rm --entrypoint sh payment-service` | Fails: `exec: "sh": executable file not found in $PATH`. There is no shell. |
| **AC4** (smoke, D3) | See below. | `/actuator/health` returns `{"status":"UP","groups":["liveness","readiness"]}` after about 7 s. All four `chain.tx.*` listeners log `payment-service: partitions assigned`. |

### AC4 run (D3), verbatim setup

1. **Network.** `docker network create t27-net`.
2. **Postgres.** `postgres:16-alpine` as `t27-pg`, with `POSTGRES_DB=checky`,
   `POSTGRES_USER=checky` and `POSTGRES_PASSWORD=t27`. Host port 55432, so the developer's
   5432 is untouched.
3. **Kafka.** `apache/kafka:3.8.0` as `t27-kafka`, in KRaft mode, configured as follows:
   - `KAFKA_PROCESS_ROLES=broker,controller`
   - `KAFKA_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093`
   - `KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://t27-kafka:9092` (F1)
   - `KAFKA_CONTROLLER_QUORUM_VOTERS=1@t27-kafka:9093`
   - replication factors of 1
4. **Migration.** `mvn -o -q -pl services/payment flyway:migrate`, with
   `-Dflyway.url=jdbc:postgresql://localhost:55432/checky -Dflyway.user=checky
   -Dflyway.password=t27`. Exit 0.
5. **App role.** `ALTER ROLE payment_app PASSWORD '…'`.
6. **Topics.** Created `chain.tx.{seen,confirmed,finalized,reorged}`, each with 1 partition.
   The listeners need existing topics to receive assignments.
7. **App container.** Started as follows:

   ```
   docker run -d --name t27-app --read-only --tmpfs /tmp --network t27-net -p 58083:8083 \
     -e DB_URL=jdbc:postgresql://t27-pg:5432/checky -e DB_USERNAME=payment_app -e DB_PASSWORD=… \
     -e KAFKA_BOOTSTRAP_SERVERS=t27-kafka:9092 \
     -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI=http://t27-auth.invalid/oauth2/jwks \
     payment-service
   ```

**Observed:**
- `docker inspect` shows `running=true`, `readonly=true` and `user=nonroot`.
- `Started PaymentServiceApplication in 5.044 seconds`, and `Tomcat started on port 8083`.
- Partitions were assigned for `chain.tx.confirmed-0`, `chain.tx.seen-0`,
  `chain.tx.finalized-0` and `chain.tx.reorged-0`, all in group `payment-service`.
- Grepping the log for `ERROR`/`WARN` (excluding the transient `NOT_COORDINATOR` lines)
  returns nothing.
- The JWKS override (F6) did not block start-up. The JWKS is fetched lazily, as D3 predicted.

**Teardown:** removed `t27-app`, `t27-pg`, `t27-kafka`, the `t27-net` network and the
`payment-service` image. Re-checked afterwards: no `t27*` containers or networks remain.
`auth-postgres-1` and `auth-kafka-1` are still `Up 41 hours`, untouched.

## Deviations

None from the frozen brief or the plan. Three details are worth noting:

- **Explicit topic creation (D3 step 6).** This is not listed in the plan. It is a harness
  detail of the throwaway broker: without it, the pass condition (partitions assigned) cannot
  be observed. Nothing committed depends on it.
- **Transient `NOT_COORDINATOR` / `RebalanceInProgressException` INFO lines.** These appeared
  for about 300 ms at the first group join, while the fresh broker created `__consumer_offsets`.
  They resolved without intervention. This is expected on a brand-new broker, not a defect.
- **Shell tooling.** macOS has no `timeout`, so commands ran without it. The long
  verify-plus-build run went to the background and finished with exit 0. This affects nothing
  in the repo.

## Follow-ups (unchanged from frozen brief)

- **FU-T27-1.** The sibling Dockerfiles for auth, crypto and notification fail `docker build`.
  Notification's failure was re-confirmed at Phase 6 with "Child module
  /workspace/services/payment … does not exist", and the probe image was removed.
- **FU-T27-2.** `jwk-set-uri` is hard-coded to localhost in payment, crypto and notification.
  The relaxed-binding override works, as AC4 shows.

## Open Questions

None.
