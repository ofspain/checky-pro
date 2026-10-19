# payment · T27 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

**No R-requirement is scoped to T27.** `requirements.md` contains no build or deployment
requirement. T27 is a gate task with two halves.

**Half 1: "every requirement still holds."**
- `mvn -pl services/payment verify` re-runs the whole suite, which maps every R1–R31 to its
  named test (`package.md` §8).
- R26 is deferred.
- T27 re-implements none of them. Their only role here is that a green `verify` is the evidence
  for all of them.

**Half 2: a deployable artifact.** This is driven by standing rules, not requirements.

## Locked Decisions

| ID | Constraint on T27 |
|---|---|
| **L13** (`design.md` §4a) | **Secrets discipline.** No secret, credential, DB password, or bucket or KMS identifier may be baked into the image. Values arrive at runtime as environment variables, injected by External Secrets Operator. The image must contain no config file beyond the jar's own `application.properties`, whose secret values are already `${ENV:local-only-default}` placeholders. |
| **L3** | Continuity only. The image must not add a chain client or KMS client. The jar's dependency set is unchanged, and ArchUnit already gates it. |

**Standing rules (`agents.md`):**
- **Deployment:** "Multi-stage Docker → distroless JRE 21, non-root, read-only rootfs. EKS ≥ 2
  replicas; multi-replica scheduled jobs are ShedLock-guarded. Infra is AWS CDK."
- **Testing:** the full pyramid is run by `verify`.

## Files involved

**Existing, read-only:**
- the root `pom.xml` (module list)
- `services/payment/pom.xml` (`finalName`, the repackage plugin)
- `services/payment/src/main/resources/application.properties` (port `8083`; every secret is an
  `${ENV}` placeholder)
- the sibling precedents `services/notification/Dockerfile` (the closest),
  `services/crypto/Dockerfile` and `services/auth/Dockerfile`

**New, expected by the task:** `services/payment/Dockerfile`. "Docker image builds from repo
root" implies that a payment Dockerfile exists and builds from the repo-root context, like the
siblings.

**No `spec/` change.** No production Java change.

## Dependencies

- **Base images:** `maven:3.9-eclipse-temurin-21` for the build stage and
  `gcr.io/distroless/java21-debian12:nonroot` for runtime. Both are the sibling precedent, and
  Phase 0 confirmed they pull.
- **Build context needs:**
  - the root `pom.xml`
  - **all four** module poms, so that reactor validation passes (Phase 0 §5.1–5.2)
  - `services/payment/src`
- **Runtime environment the image reads** (all already placeholders):
  - `SERVER_PORT`
  - `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`
  - `KAFKA_BOOTSTRAP_SERVERS`
  - `AUTH_ISSUER_URI`
  - `CRYPTO_SERVICE_BASE_URL`, `CRYPTO_AUTH_*`, `PAYMENT_SERVICE_CLIENT_SECRET`
  - `RECEIPT_BUCKET*`, `KMS_PUBLIC_KEYS_URL`
  - `OUTBOX_RELAY_INTERVAL_MS`

## Acceptance Criteria

- **AC1 (verify).** `mvn -pl services/payment verify`, run from the repo root, passes. That
  means the full suite (496/496 at Phase 0) and the repackaged
  `services/payment/target/payment-service.jar`.
- **AC2 (image builds).** `docker build -f services/payment/Dockerfile .`, run from the repo
  root, succeeds.
- **AC3 (Deployment standing rule).** The image is:
  - multi-stage
  - a distroless JRE 21 runtime
  - running as a non-root user
  - built with no secrets baked in (L13)

  Read-only-rootfs compatibility is an open question; see OQ2.
- **AC4 (no regression).** No production code or `spec/` change. The suite stays green.

## Tests required

- **Named tests:** none. `package.md` §8 maps none to task 27.
- **Implied:** the AC1 and AC2 command runs themselves, which are the gate evidence. An image
  inspection (`docker image inspect`) confirms the user, entrypoint and port for AC3.
- Phase 2 decides whether a scripted or unit check of the Dockerfile's content is warranted.
  The precedent is `T01SkeletonRegressionTest`'s source-text checks.

## Open Questions

None blocks payment's own image. Four need a Phase 2 or Phase 4 disposition.

`package.md` §11 has Q1–Q8 all closed, and none is scoped to T27.

1. **Sibling Docker builds are broken (Phase 0 §5.1).**
   - Notification's Dockerfile now fails, because payment was added to the root pom. Auth's and
     crypto's were already failing, as logged in crypto T29.
   - Fixing them means editing three other services' Dockerfiles, which is outside task 27's
     "payment" scope.
   - Options: (a) log them and leave them; (b) add the missing sibling `COPY` lines to all three
     as a cross-service fix, with the human's approval.
2. **Read-only rootfs.** It is a deployment-time setting.
   - Should T27 prove the image *starts* under `--read-only --tmpfs /tmp`? A start needs
     Postgres, Kafka and the JWKS.
   - Or is that left to the CDK/EKS deployment work (the infra pivot noted in memory)?
3. **Build-only or smoke-start.** The task says "builds". A smoke start, for example
   `java -jar` reaching Spring context refresh, would need the full infrastructure.
4. **`.dockerignore`.** It is absent repo-wide. Adding one is a repo-root, cross-cutting change.
