# payment · T27 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Make two things true:
- `mvn -pl services/payment verify` passes from the repo root.
- `docker build -f services/payment/Dockerfile .` succeeds from the repo root.

That means authoring payment's Dockerfile, which does not exist yet.

## Purpose

This is the last build gate before T28 (the threat model) and T29 (the spec bump). It proves:
- the whole suite is green through the real Maven lifecycle
- the service ships as an image that follows the `agents.md` Deployment rule: multi-stage,
  distroless JRE 21, non-root, read-only rootfs, and no secrets baked in (L13)

## Scope

**In:**
- **New `services/payment/Dockerfile`.**
- **AC1 and AC2 runs**, as gate evidence.
- **An uncommitted, throwaway smoke start (D3).** The image runs under `--read-only --tmpfs /tmp`
  on a private Docker network, against throwaway Postgres and Kafka containers, until
  `/actuator/health` returns `UP`. It proves the read-only rootfs half of the Deployment rule,
  which a build alone cannot.

**Out:**
- **Production Java code, `application.properties`, and both `pom.xml` files.**
- **`spec/`.**
- **The broken Dockerfiles of the other services** (auth, crypto, and now notification).
  They are logged as a follow-up, not fixed. See Open Questions.
- **A repo-root `.dockerignore`.**
- **Kubernetes or CDK manifests:** the `securityContext`, replicas. These belong to the infra
  workstream.
- **CI wiring.**

## Business Rules

None scoped (Phase 1). The `verify` run is the regression evidence for R1–R31, with R26
deferred.

## Locked Decisions

- **L13.** No secret in the image. There is no `ENV` holding a credential, and no copied
  config file. Runtime values arrive as environment variables.
- **L3 (continuity).** The dependency set is unchanged.

**Resolved directly** (low-risk choices that mirror siblings; flagged for the Phase 4 gate):

| # | Decision | Basis |
|---|---|---|
| D1 | **Mirror `services/notification/Dockerfile` exactly**, changing only the module name, the jar name (`payment-service.jar`) and `EXPOSE 8083`. The stages are `maven:3.9-eclipse-temurin-21`, then `dependency:go-offline`, then `package -DskipTests`, then `gcr.io/distroless/java21-debian12:nonroot`, `USER nonroot`, `ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]`. | This is the newest sibling, and the only one built to survive reactor validation. |
| D2 | **Copy all four module poms** (auth, crypto, notification, payment) before `go-offline`. | Maven validates every `<module>` the root declares, even with `-pl` (Phase 0 §5.1, which reproduced the failure). |
| D3 | **Throwaway smoke start**, uncommitted and fully containerised, so the user's local stack is never touched. The steps: 1. Create a temporary network. 2. Start `postgres:16-alpine` and `apache/kafka:3.8.0`. 3. Run Flyway against the throwaway DB (`mvn -o flyway:migrate` with `-Dflyway.url/user/password` overrides), then `ALTER ROLE payment_app PASSWORD`. 4. Run the image with `--read-only --tmpfs /tmp` and env vars pointing at those containers. 5. Poll `/actuator/health` until it returns `UP`. 6. Tear everything down. | The Deployment rule says "read-only rootfs", which a build cannot prove. Start-up needs no auth or crypto service: the JWKS is only fetched lazily on the first token. |
| D4 | **No committed automated test of the Dockerfile's content.** | The build itself is the test. Siblings have none. A source-text tripwire would add maintenance without coverage the build doesn't already give. |

## Dependencies

- Docker, plus network access to Docker Hub, `gcr.io` and Maven Central (all verified at
  Phase 0).
- The root `pom.xml` and the four module poms.
- `services/payment/src`.
- The base images listed in D1.
- For D3: the local `postgres:16-alpine` and `apache/kafka:3.8.0` images, plus the module's
  Flyway plugin.

## Inputs

The repo-root build context.

## Outputs

- `services/payment/target/payment-service.jar`.
- A local image `payment-service` (tag illustrative).
- Gate evidence: the command results, `docker image inspect` showing user `nonroot`, the
  entrypoint and the exposed port, and the D3 health result.

## State Changes

None committed beyond the Dockerfile. The D3 containers, network and image are removed
afterwards.

## Files to Create

- `services/payment/Dockerfile`.

## Files to Modify

- None.

## Files NOT to Modify

- `src/main/**`
- `src/test/**`
- `services/payment/pom.xml`
- the root `pom.xml`
- the other services' Dockerfiles (the follow-up)
- `spec/`
- `contracts/`

## Acceptance Criteria

- **AC1.** `mvn -pl services/payment verify` from the repo root reports `BUILD SUCCESS` with
  496/496 and the repackaged jar.
- **AC2.** `docker build -f services/payment/Dockerfile -t payment-service .` from the repo root
  succeeds.
- **AC3.** `docker image inspect` shows all of the following:
  - user `nonroot`
  - the distroless base (no shell, so a `docker run --entrypoint sh` attempt fails)
  - the entrypoint `java … -jar app.jar`
  - `ExposedPorts` `8083/tcp`
  - no `Env` entry carrying a secret (L13)
- **AC4.** Under D3, the container runs with `--read-only --tmpfs /tmp` and reaches
  `/actuator/health` = `UP`.
- **AC5.** No change outside `services/payment/Dockerfile`, and the suite is still green.

## Required Tests

- No named tests.
- The evidence is the AC1–AC4 runs, recorded verbatim in the Phase 6 notes and Phase 10
  manifest.

## Constraints

- **Security:**
  - non-root
  - distroless (no shell or package manager)
  - no secrets in layers
  - `COPY` only explicit paths, never `COPY . .`, so no stray repo files enter the image
- **Reproducibility:** base images are pinned to the sibling tags.
- **Isolation:** D3 must not touch the developer's running `auth-postgres-1` or `auth-kafka-1`.
  It uses its own network, its own containers and its own ports.

## Open Questions

No blockers. Two items need the human's disposition at Phase 4.

1. **Sibling Docker builds.** Auth, crypto, and now notification fail `docker build`. The cause
   is the same: a module pom missing from the build context. Notification has been broken since
   payment T01 added the module.
   - **Recommendation:** log the breakage as a follow-up and leave the three files alone, as the
     scope guardrail requires.
   - **Alternative:** if the human approves, pull a fix into T27. It would be three to four
     `COPY` lines per file, with each image build-verified.
2. **D3 smoke start.** Recommended. The human may instead reduce T27 to build-only (AC1–AC3),
   deferring the read-only proof to the infra/EKS work.
