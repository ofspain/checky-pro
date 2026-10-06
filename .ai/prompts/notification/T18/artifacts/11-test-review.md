<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T18 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T18 — Dockerfile + build/image checkpoint |
| **Spec section** | Deployment rule in `agents.md` |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + final Dockerfile |
| **Produces** | `artifacts/11-test-review.md` |

Review of T18 verification against the acceptance criteria. This is an infrastructure task; the frozen brief explicitly required no new automated tests.

---

## What is covered

- **AC1 — `mvn -pl services/notification clean verify` passes:** claimed executed in Phases 6/9 (369 tests, 0 failures, 0 errors).
- **AC2 — Docker image builds from repo root:** claimed executed in Phases 6/9 (exit 0).
- **Deployment rule — distroless JRE 21, non-root:** structurally present in the Dockerfile.
- **Sibling-pom reactor fix:** the Dockerfile copies `services/auth/pom.xml` and `services/crypto/pom.xml` before Maven runs.

---

## Gap 1 · Maven and Docker verification claims cannot be confirmed in this environment

**Why it matters:** The brief's required verification is direct execution of two commands. This workspace has neither `mvn` nor Docker.

**Evidence:**
- `shell`/`mvn` and `docker` return `command not found` in this workspace.
- The Dockerfile is structurally correct, but no local execution was performed.

**Suggested action:** Run both commands in an environment with Maven and Docker before closing the task. Capture the output in Phase 12 if possible.

---

## Gap 2 · No automated guard against root-pom / Dockerfile module drift

**Why it matters:** The root `pom.xml` `<modules>` list is the source of truth for which sibling POMs Maven's reactor validation requires. The Dockerfile copies those POMs by name. If a fourth module is added to the root POM without updating the Dockerfile, `docker build` will fail with the same "Child module ... does not exist" error that this task fixed for the current three-module tree.

**Evidence:**
- Root `pom.xml` `<modules>`: `services/auth`, `services/crypto`, `services/notification`.
- `services/notification/Dockerfile` lines 7–8: explicit `COPY` for `services/auth/pom.xml` and `services/crypto/pom.xml`.

**Assessment:** Phase 10 audited this hazard and deliberately left it unfixed because a guard test would be new scope beyond the frozen brief. This is a reasonable disposition for this task, but it leaves a known latent risk.

**Suggested action:** Add a CI check or unit test in the future that parses the root `pom.xml` `<modules>` list and asserts the Dockerfile copies every listed module's `pom.xml`. Address this alongside the already-known auth/crypto Dockerfile follow-up.

---

## Gap 3 · No automated test verifies runtime image properties

**Why it matters:** The built image is expected to be distroless, run as non-root, and contain only the application jar. There is no automated test asserting these properties.

**Evidence:**
- The Dockerfile declares `gcr.io/distroless/java21-debian12:nonroot`, `USER nonroot`, and a single jar copy.
- Phase 6 reportedly confirmed `USER nonroot` via `docker inspect`.

**Assessment:** Infrastructure verification via `docker build`/`docker inspect` is the accepted verification method for this task. A permanent automated test would require container tooling in CI, which is out of scope.

**Suggested action:** None for this task; ensure CI runs `docker build` and optionally inspects the image user/base image.

---

## Summary

T18 has no automated tests by design. The verification claims (`mvn verify` pass, `docker build` success) must be confirmed in an equipped environment. The one real coverage gap is the unguarded root-pom / Dockerfile module drift, which Phase 10 correctly identified and deferred. No additional tests are required to satisfy the frozen brief.
