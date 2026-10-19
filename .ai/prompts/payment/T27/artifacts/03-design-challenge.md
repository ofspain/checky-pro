# payment · T27 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `services/payment/src/main/resources/application.properties`, the sibling `services/notification/Dockerfile`, and the service's runtime wiring.

---

## Finding 1 — D3's Kafka container may not be reachable from the payment container without explicit listener configuration

- **Issue:** The smoke start spins up an `apache/kafka:3.8.0` container and expects the payment app to consume/produce. The app's `spring.kafka.bootstrap-servers` must point at that container's hostname inside the private Docker network. Kafka's default advertised listener is `localhost:9092`; unless the container is started with `KAFKA_ADVERTISED_LISTENERS` including a `PLAINTEXT://kafka:9092` entry (and a matching `KAFKA_LISTENERS`), the payment container cannot resolve or connect. The brief does not list this configuration.
- **Severity:** Medium — the app may still report `UP` because `/actuator/health` does not depend on Kafka, but the Outbox relay will log connection errors and the D3 run will not actually prove Kafka wiring.
- **Evidence:** TIB D3; `services/payment/src/main/resources/application.properties` line 35.
- **Recommended brief amendment:** Add the exact Kafka container env vars to D3, e.g.:
  ```
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092
  ```
  and run the payment container with `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` on the same network.

---

## Finding 2 — D3 only checks `/actuator/health`, so it does not prove S3 or Crypto client wiring

- **Issue:** The smoke start is intended to prove the read-only rootfs half of the Deployment rule, but the health endpoint alone does not exercise `ReceiptStoreConfig`'s `S3Client`, the `CryptoClient`, or the outbound token call. A runtime misconfiguration (e.g., a missing AWS region or an unreachable `CRYPTO_SERVICE_BASE_URL`) would not cause the D3 health check to fail.
- **Severity:** Low — the build and unit/integration tests already cover those beans independently; the smoke start is not meant to replace them.
- **Evidence:** TIB AC4; `services/payment/src/main/resources/application.properties` lines 63, 91-94; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStoreConfig.java`.
- **Recommended brief amendment:** Either keep D3 as a shallow read-only/smoke test and explicitly state that S3/crypto runtime connectivity is verified by the integration tests (T26), or extend D3 with one actuator-secured readiness probe that forces a lazy bean (e.g., a lightweight `CryptoClient` ping) if the human wants more coverage.

---

## Finding 3 — D3 uses `mvn -o flyway:migrate`, which assumes the local Maven repo is already primed

- **Issue:** The Flyway plugin and its dependencies must be present in the local repository for offline mode to succeed. AC1 `verify` will download them, but if D3 is run on a clean machine or after `mvn clean`, the offline migration step will fail.
- **Severity:** Low — the intended order is AC1 first, then D3, and the notes can record that assumption.
- **Evidence:** TIB D3, Dependencies.
- **Recommended brief amendment:** Either drop the `-o` flag for the Flyway step (network is already required for the build), or explicitly document that D3 must run after a successful AC1 `verify` so the local repo is populated.

---

## Finding 4 — No committed guardrail verifies the Dockerfile stays compliant with the security rules

- **Issue:** D4 rejects a committed automated test of the Dockerfile. That means a future edit could reintroduce `COPY . .`, a secret `ENV`, a non-distroless final stage, or a root user, and the only defense is the manual AC2/AC3 run at implementation time.
- **Severity:** Low — the rules are clear, and a manual check is acceptable for a small service, but it is a recurring toil/risk.
- **Evidence:** TIB D4, Constraints, AC3.
- **Recommended brief amendment:** Keep D4, but add a lightweight CI-friendly script (uncommitted, like D3) that parses the Dockerfile with `grep`/`sed` and fails on `COPY .`, `USER root`, non-distroless `FROM` in the final stage, or `ENV.*SECRET`. This does not replace the build, but it catches obvious regressions without running Docker.

---

## Finding 5 — The broken sibling Dockerfiles are a direct consequence of adding the payment module to the root POM

- **Issue:** Auth, crypto, and notification Dockerfiles now fail because their `COPY` lists do not include `services/payment/pom.xml`, but the root POM lists payment as a module. The brief correctly scopes them out, but this is not just a pre-existing sibling problem — it was introduced by this service's own module creation.
- **Severity:** Low — out of scope per TIB, but the follow-up should be tracked explicitly.
- **Evidence:** TIB Open Questions; `services/notification/Dockerfile` lines 7-9; root `pom.xml` modules.
- **Recommended brief amendment:** In Open Questions, record the exact root-cause sentence and a suggested follow-up ticket: "Update auth/crypto/notification Dockerfiles to COPY services/payment/pom.xml before `dependency:go-offline`, because the root reactor now validates it."

---

## Finding 6 — The hardcoded `jwk-set-uri` default points to localhost, which is unreachable inside the container

- **Issue:** `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` is hardcoded to `http://localhost:8080/oauth2/jwks` (no env-var override). The brief states the JWKS fetch is lazy and only happens on the first token, so health will be `UP`. However, any accidental eager initialization or a future health detail that checks the decoder would crash the container in a real network.
- **Severity:** Low — current Spring Security behavior is lazy, and the smoke start does not authenticate.
- **Evidence:** `services/payment/src/main/resources/application.properties` line 48.
- **Recommended brief amendment:** Note in D3 that health is reached without auth, and add an Open Question to make `jwk-set-uri` overridable by env var in a future task, consistent with `issuer-uri`.

---

## Finding 7 — The Dockerfile may leave build-time `.m2` cache layers with no direct benefit

- **Issue:** `dependency:go-offline` populates the image's local Maven repository, but that repository is discarded with the build stage. It does not speed up subsequent image builds because each build stage starts fresh. This is the standard pattern and not wrong, but it does mean the build downloads all dependencies every time unless a layer-caching volume is used.
- **Severity:** Info.
- **Evidence:** TIB D1; `services/notification/Dockerfile`.
- **Recommended brief amendment:** None required, but document that `docker build` with `--build-arg` or BuildKit cache mounts is out of scope for T27.

---

## Summary

T27 is a straightforward build-gate task. The most important disposition at Phase 4 is Finding 1: decide whether to add the Kafka listener configuration to D3 or accept that D3 is a shallow health/read-only smoke test. Findings 2–7 are scope, risk, and documentation notes that should be recorded before freezing.
