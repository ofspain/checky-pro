# crypto · T27 · Phase 2 — Task Implementation Brief

## Task

Author `services/crypto/Dockerfile`, mirroring `services/auth/Dockerfile`'s exact structure, and run
(or, where blocked, honestly attempt and disclose) `mvn -pl services/crypto verify` and a `docker
build` from the repo root.

## Purpose

Closes `package.md` §9's own final verification-checklist bullet and this service's equivalent of
auth's own already-completed task 37. Purely packaging/verification work — no domain code changes.

## Scope

**In:**
- `services/crypto/Dockerfile` — a direct structural mirror of `services/auth/Dockerfile`:
  - Build stage: `FROM maven:3.9-eclipse-temurin-21 AS build`, copy the parent `pom.xml` and
    `services/crypto/pom.xml` first (layer-cache-friendly), `mvn -q -pl services/crypto
    dependency:go-offline`, then copy `services/crypto/src` and `mvn -q -pl services/crypto package
    -DskipTests`.
  - Runtime stage: `FROM gcr.io/distroless/java21-debian12:nonroot`, copy
    `/workspace/services/crypto/target/crypto-service.jar` (confirmed real `finalName`, Phase 2) to
    `/app/app.jar`, `USER nonroot`, `EXPOSE 8080` (Spring Boot's default, no override found in
    `application.properties`), `ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]`.
  - A leading comment documenting the exact `docker build -f services/crypto/Dockerfile -t
    crypto-service .` invocation, matching auth's own file's own documentation convention.
- Attempt `mvn -pl services/crypto verify` in this environment; disclose the actual result honestly,
  split between the achievable (compile/package/unit-test, per Phase 0's own finding that no failsafe
  plugin changes what `verify` does beyond `test` + `package`) and the Docker-blocked (Testcontainers
  integration tests).
- Attempt `docker build` in this environment; disclose that it cannot run here (no Docker daemon) once
  the Dockerfile itself is authored and reviewed for correctness by direct construction.

**Out:**
- Any change to application code, migrations, or existing tests.
- Any change to `services/auth/Dockerfile` or any other service's own build tooling.
- Provisioning, fixing, or working around this environment's Docker unavailability — out of this
  task's own power; disclosed, not solved.
- Any secret, credential, or environment-specific value in the new Dockerfile (L13) — runtime
  configuration remains externally supplied, exactly as it already is for the running application
  outside a container.

## Business Rules / Locked Decisions

L13 (secrets discipline) constrains the new Dockerfile's own content; no `R`-number targets this task
directly (Phase 1).

## Dependencies

None new.

## Files to Create

- `services/crypto/Dockerfile`

## Files to Modify

None.

## Files NOT to Modify

- `services/auth/Dockerfile` (cited as precedent, not touched).
- Every existing `services/crypto` source, test, and migration file.
- Any file under `spec/`.

## Acceptance Criteria

Unchanged from Phase 1's AC1-AC3.

## Required Tests

None — this task authors no test (Phase 1).

## Constraints

- **Multi-stage, distroless, non-root, no secrets** — `agents.md`'s own Deployment rule and L13,
  satisfied by mirroring auth's own already-correct structure exactly.
- **Docker unavailability remains unresolved** in this environment; AC1's Testcontainers portion and
  all of AC2 will be disclosed as attempted-but-blocked, not silently claimed passing — the same
  posture established and carried through T23, T26, and now T27.

## Open Questions

No blockers. Adopting Phase 0/1's working assumption (mirror auth's Dockerfile) as final for
implementation.
