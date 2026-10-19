# payment · T27 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN.** Approved by the human on 2026-10-19. Downstream phases may not renegotiate
it.

Consumes:
- `artifacts/02-task-implementation-brief.md` (TIB)
- `artifacts/03-design-challenge.md` (Kimi)

Every finding was checked against source before disposition.

## Phase 3 dispositions

| # | Finding | Verified | Disposition |
|---|---|---|---|
| F1 | The D3 Kafka would advertise `localhost`, so the app can't connect, yet health would still be `UP`. | True. `apache/kafka`'s default advertised listener is `localhost:9092`. Spring Boot has no Kafka health indicator, so health would hide the problem. | **ACCEPTED.** See D3 below: Kafka gets network-scoped listeners, and the run is not green until the app's log shows its `chain.tx.*` consumer group **assigned partitions**. A healthy `/actuator/health` alone is not enough. |
| F2 | D3 doesn't exercise S3 or the Crypto client. | True. | **ACCEPTED (documented).** D3 proves a read-only boot plus DB and Kafka wiring. S3 and Crypto runtime wiring are T26's proof. |
| F3 | `mvn -o flyway:migrate` assumes a primed repo. | Checked: `flyway-maven-plugin` 11.7.2 is in `~/.m2`. | **ACCEPTED (documented).** D3 runs after AC1, and works offline. |
| F4 | Nothing guards the Dockerfile's content after T27. | True. Kimi suggested an *uncommitted* script, which would protect nothing in future. | **ACCEPTED, changed (human).** D4 is replaced by a committed source-text test, `paymentDockerfileHonoursBuildContextAndSecurityRules`, in `T01SkeletonRegressionTest`, following that file's existing pom source-text pattern. |
| F5 | The sibling Dockerfiles broke because payment joined the root pom. | Partly true. Notification's broke because of payment; Phase 0 reproduced it. Crypto's and auth's were already broken earlier, for the same class of reason. | **LOGGED as follow-up FU-T27-1 (human).** See below. No sibling file is touched. |
| F6 | `jwk-set-uri` is hard-coded to localhost. | True, at `application.properties:48`. The **same is true in crypto (`:46`) and notification (`:51`)**. Spring relaxed binding lets a deployment override it with `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI`. | **ACCEPTED: logged as a platform-wide follow-up, FU-T27-2.** The production property is not changed. D3 sets that env var, to prove the override works. |
| F7 | No build cache mounts. | Info. | **ACCEPTED (documented).** Out of scope. |

**Phase 2 OQ1 (siblings):** logged (FU-T27-1). **OQ2 (D3):** approved, extended per F1.

## Task

Make two things true:
- `mvn -pl services/payment verify` passes from the repo root.
- `docker build -f services/payment/Dockerfile .` succeeds from the repo root.

## Decisions (frozen)

| # | Decision |
|---|---|
| D1 | `services/payment/Dockerfile` mirrors `services/notification/Dockerfile`. **Build stage:** `maven:3.9-eclipse-temurin-21`; root `pom.xml`, then the module poms, then `mvn -q -pl services/payment dependency:go-offline`, then `services/payment/src`, then `mvn -q -pl services/payment package -DskipTests`. **Runtime stage:** `gcr.io/distroless/java21-debian12:nonroot`; `COPY` the jar as `app.jar`; `USER nonroot`; `EXPOSE 8083`; `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]`. Only explicit paths are copied, with no `ENV` and no secrets. |
| D2 | All four module poms (auth, crypto, notification, payment) are copied before `go-offline`. |
| D3 | A throwaway, uncommitted smoke start. **Setup:** a private `docker network`, plus `postgres:16-alpine` and `apache/kafka:3.8.0` with `KAFKA_LISTENERS` / `KAFKA_ADVERTISED_LISTENERS` set to network-reachable `PLAINTEXT://<kafka-container>:9092` plus a KRaft controller listener (F1). Then migrate with `mvn -o flyway:migrate` and `-Dflyway.url/user/password` overrides against the throwaway DB, and `ALTER ROLE payment_app PASSWORD`. **Run:** the image under `--read-only --tmpfs /tmp`, with `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KAFKA_BOOTSTRAP_SERVERS` and `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWKSETURI` (F6) set. **Pass condition:** `/actuator/health` is `UP` **and** the log shows the consumer group's partitions assigned (F1). **Teardown:** everything is removed. Nothing touches the developer's `auth-postgres-1` or `auth-kafka-1`. |
| D4 (revised, F4) | A committed test, `T01SkeletonRegressionTest.paymentDockerfileHonoursBuildContextAndSecurityRules`, runs against the comment-stripped Dockerfile. It asserts: <br>• **Final stage.** The last `FROM` is `gcr.io/distroless/java21-debian12:nonroot`, and `USER nonroot` appears after it. <br>• **No broad copy.** There is no `COPY . ` and no `ADD`. <br>• **No `ENV`.** There is no `ENV` instruction at all (L13). <br>• **Module poms.** Every `<module>` the root `pom.xml` declares has a matching `COPY services/<m>/pom.xml` line. The list comes from the root pom, not hard-coded, so adding a fifth module fails this test until its pom is copied. That is exactly the bug class FU-T27-1 describes. <br>• **Port.** `EXPOSE 8083`. |

## Locked Decisions

- **L13.** No secret in any image layer, and no `ENV`. Enforced by D4.
- **L3.** Continuity only: the jar's dependency set is unchanged.

**Standing rule (`agents.md` Deployment):** multi-stage, distroless JRE 21, non-root,
read-only rootfs. D1 delivers the first three, and D3 proves read-only.

## Files

- **Create:** `services/payment/Dockerfile`.
- **Modify:** `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`.
  One new `@Test` (D4), plus whatever small private helper it needs.
- **NOT modify:**
  - `src/main/**` (including `application.properties`; F6 is logged only)
  - every other test
  - both `pom.xml` files
  - the other services' Dockerfiles (FU-T27-1)
  - `spec/`
  - `contracts/`

## Acceptance Criteria (final)

- **AC1 (verify).** `mvn -pl services/payment verify` from the repo root gives `BUILD SUCCESS`,
  **497/497** (496 plus D4), and the repackaged jar.
- **AC2 (build).** `docker build -f services/payment/Dockerfile -t payment-service .` from the
  repo root succeeds.
- **AC3 (image).** `docker image inspect` shows `User=nonroot`, `ExposedPorts` = `8083/tcp`, and
  an entrypoint of `java -XX:MaxRAMPercentage=75.0 -jar app.jar`. `Env` holds no credential: only
  the base image's own `PATH` and `SSL_CERT_FILE` style entries. The image has no shell:
  `docker run --entrypoint sh` fails.
- **AC4 (smoke, D3).** The image runs read-only with `/tmp` as tmpfs. `/actuator/health`
  returns `{"status":"UP"}`, and the log shows Kafka partitions assigned for the
  `payment-service` group.
- **AC5 (guard, D4).** The new test passes, and fails under a temporary mutation: removing one
  module-pom `COPY` line, or adding an `ENV`.
- **AC6.** No change outside the two files above.

## Required Tests

- `T01SkeletonRegressionTest.paymentDockerfileHonoursBuildContextAndSecurityRules` (new, D4).
- The AC1–AC4 runs, recorded as evidence.

## Follow-ups (human-owned, out of T27)

- **FU-T27-1.** `services/{auth,crypto,notification}/Dockerfile` fail `docker build`. The root
  pom declares 4 modules, and Maven validates them all, even with `-pl`. None of these
  Dockerfiles copies every module pom. Notification has been broken since payment T01 added
  `services/payment`.
  **Fix:** copy all four module poms before `dependency:go-offline` in each. Ideally, also give
  each service a D4-style guard derived from the root pom.
- **FU-T27-2.** `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` is hard-coded to
  `http://localhost:8080/oauth2/jwks` in payment, crypto and notification. It can be overridden
  through relaxed binding, as D3 proves, but it is inconsistent with `issuer-uri`'s
  `${AUTH_ISSUER_URI:…}` placeholder.
  **Fix:** a `${AUTH_JWKS_URI:…}` placeholder, platform-wide.

## Open Questions

None remaining.
