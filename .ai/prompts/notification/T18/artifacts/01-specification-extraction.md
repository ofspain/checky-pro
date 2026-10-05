# notification · T18 · Phase 1 — Specification Extraction

## Business Rules

**No R-numbered requirement governs this task's own substance — confirmed directly, not assumed.**
`requirements.md` contains no mention of "docker," "deploy," "image," or "build" anywhere in its own
text. This mirrors T16's own identical finding for an analogous infrastructure/process task with no
corresponding functional requirement.

## Locked Decisions

- **Deployment** (`agents.md`, identical across all Themistra services): "Multi-stage Docker →
  distroless JRE 21, non-root, read-only rootfs." This task's own job: produce the
  `services/notification/Dockerfile` this standing rule has described in prose since this service's
  own Phase 1, but never actually built until now — the same "asserted in prose, never built"
  pattern T16 found for its own three ArchUnit rules.
- **Scope, per Phase 0's user decision**: fix only `services/notification/Dockerfile`; the
  already-known, already-flagged shared reactor bug in `services/auth/Dockerfile`/
  `services/crypto/Dockerfile` remains untouched, a separate follow-up.

## Files involved

**Existing — read, not modified, the established precedent this task mirrors structurally (while
fixing the one defect Phase 0 found in it):**
- `services/auth/Dockerfile`, `services/crypto/Dockerfile` — the two-stage
  `maven:3.9-eclipse-temurin-21` build / `gcr.io/distroless/java21-debian12:nonroot` runtime
  pattern, `USER nonroot`, `EXPOSE 8080`, the `-XX:MaxRAMPercentage=75.0` JVM flag. Both currently
  fail `docker build` (Phase 0's own reproduced finding) — mirrored structurally, not literally,
  since literal copying would reproduce the same defect for a third sibling.
- Root `pom.xml` — the real `<modules>` list (`services/auth`, `services/crypto`,
  `services/notification`) that makes Maven's reactor validation require every sibling's `pom.xml`
  to be present in the build context, not just the module being built.
- `services/notification/pom.xml` — confirmed `<finalName>notification-service</finalName>`
  (`:167`), so the real build artifact is `target/notification-service.jar`, matching
  `auth-service.jar`/`crypto-service.jar`'s own identical naming convention.

**New — this task's own real deliverable:**
- `services/notification/Dockerfile` — copies the root `pom.xml` plus **every** sibling module's
  own `pom.xml` (not `src`) into the build context before running Maven, the one-line-per-sibling
  fix Phase 0 already diagnosed and confirmed.

## Dependencies

None new. No Maven dependency, no new library — this task is infrastructure-only.

## Acceptance Criteria

1. **AC1**. `mvn -pl services/notification clean verify` passes — confirmed continuously true
   already (Phase 0); this task re-confirms it fresh as its own explicit, final checkpoint.
2. **AC2**. `docker build -f services/notification/Dockerfile -t notification-service .`, run from
   the repo root, succeeds — the real, literal task wording ("Docker image builds from repo root"),
   not merely "a Dockerfile exists."
3. **AC3**. The built image follows the `agents.md` Deployment rule: multi-stage, distroless JRE 21
   base, runs as a non-root user.
4. **AC4** (disclosure). `services/auth/Dockerfile` and `services/crypto/Dockerfile` remain
   untouched and still fail `docker build` for the same pre-existing reason — explicitly disclosed,
   per Phase 0's own user decision, not silently left implying this task also fixed them.

## Required Tests

None in the usual sense — this is an infrastructure task, not a code task. The real verification is
AC1 (an `mvn` run) and AC2 (a real `docker build` invocation), both executable directly, not
simulated.

## Open Questions

No blockers. Phase 0 already resolved the one real decision (scope) via explicit user choice. One
concrete implementation detail remains for Phase 2: whether to also add a root-level `.dockerignore`
(neither existing Dockerfile relies on one; likely out of scope, matching precedent, but worth a
deliberate yes/no rather than silent omission).
