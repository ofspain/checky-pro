# notification · T18 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T18 — Run full suite; Dockerfile + build/image checkpoint |
| **Consumes** | All T18 artifacts, Phases 0–11 (including the Phase 9 and Phase 11 addenda) |
| **Produces** | `artifacts/12-specification-verification.md` |

## Traceability matrix

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **Task 18 AC1** — `mvn -pl services/notification clean verify` passes | Yes | Fresh run, Phase 11: exit 0, 369 tests, 0 failures, 0 errors | The 369-test suite itself | No | No |
| **Task 18 AC2** — Docker image builds from repo root | Yes | `services/notification/Dockerfile`; `docker build -f services/notification/Dockerfile -t notification-service .` exit 0 (Phases 6 and 9) | Real `docker build` run, not a simulated check | No | No |
| **agents.md Deployment rule** — multi-stage, distroless JRE 21, non-root | Yes | Dockerfile lines 4–16: `maven:3.9-eclipse-temurin-21` build stage, `gcr.io/distroless/java21-debian12:nonroot` runtime, `USER nonroot`. `docker inspect` → `User=nonroot`; `docker export` → only `app/app.jar` under `/app` (Phase 11) | Verified by direct inspection, not a permanent automated test (out of brief scope) | No | No |
| **AC3** (frozen brief) — image follows the Deployment rule | Yes | Same evidence as the row above | As above | No | No |
| **AC4** (frozen brief) — `services/auth/Dockerfile` and `services/crypto/Dockerfile` untouched | Yes | `git status` shows only the new notification Dockerfile and artifacts; both sibling Dockerfiles unchanged | N/A | No | No — disclosed; both still fail `docker build` for the pre-existing reactor reason, as the Phase 0 user decision specified |
| **Frozen-brief Files to Create** — `services/notification/Dockerfile` | Yes | Created, Phase 6 | N/A | No | No |
| **Frozen-brief Constraint** — `EXPOSE` matches notification's real port, not auth's literal `8080` | Yes | `EXPOSE 8082`; `application.properties` `server.port=${SERVER_PORT:8082}` | N/A | No | Corrected from the Phase 2 brief's own implicit assumption at Phase 4, Finding 2 |
| **Frozen-brief Constraint** — no production Java or Maven build-config change | Yes | Confirmed clean: the only files changed are the Dockerfile and T18 artifacts | N/A | No | No |

## Answers

**(1) Is the task fully complete?** Yes, for the scope chosen at Phase 0. The Dockerfile exists and
builds, the image meets the Deployment rule, and the full suite is green. The review rounds (Kimi
Phases 8 and 11, plus the self-review) raised no defects. Their concerns were either addressed or
deliberately deferred.

**(2) Does it satisfy every acceptance criterion?** Yes. AC1 and AC2 are backed by real executions,
not by the Dockerfile merely looking correct. The run that first failed (Phase 9) was environmental:
Docker and the shared Kafka broker had stopped. Once restored, both commands passed.

**(3) Does it violate any LOCKED decision?** No. The Deployment rule is satisfied as written. Its
"read-only rootfs" element is correctly absent from the Dockerfile, as decided at Phases 2–4: it is a
runtime/pod-spec concern.

**(4) Remaining risks?**
- **Sibling-pom drift**: adding a fourth module to the root `pom.xml` will break `docker build` again
  until the Dockerfile is updated. Deferred and tracked alongside the auth/crypto Dockerfile fix.
- **auth and crypto Dockerfiles still fail `docker build`**: the pre-existing reactor bug, now
  reproduced fresh. Out of this task's scope by explicit user decision; still the top repo-level
  follow-up.
- **Environment dependence**: `mvn verify` depends on Docker (Testcontainers) and on the shared local
  Kafka broker from `services/auth/compose.local.yaml`. Both were found stopped during Phase 9 and
  restarted. CI must provide them.
- **Image runtime config**: the image fails fast without runtime configuration (datasource, Kafka
  bootstrap, and so on). This is correct behavior under `agents.md`, but it means smoke-testing the
  image needs the real environment variables.

## Verdict

**PASS** — T18 fully satisfies its own task wording (`mvn verify` passes; Docker image builds from
repo root), the Deployment rule in `agents.md`, and every acceptance criterion and frozen-brief
constraint. The remaining risks are deliberate, disclosed follow-ups, not defects in this task's work.
