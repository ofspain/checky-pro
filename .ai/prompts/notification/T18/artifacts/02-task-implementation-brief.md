# notification · T18 · Phase 2 — Task Implementation Brief

## Task

Make this service's full build/image checkpoint real, not merely assumed: confirm
`mvn -pl services/notification clean verify` fresh (already continuously true across T14-T17, this
task's own job is the explicit final check), and produce a real, working
`services/notification/Dockerfile` — fixing, for this one new file, the shared Maven reactor
validation bug Phase 0 reproduced in both existing sibling Dockerfiles, per the user's own Phase 0
scoping decision (fix only this file; leave `services/auth`/`services/crypto`'s own Dockerfiles as
the already-flagged, separate follow-up they already were).

## Purpose

Closes the one standing-rule-asserted-but-never-built gap `agents.md`'s own Deployment rule has
described since this service's own Phase 1: "Multi-stage Docker → distroless JRE 21, non-root,
read-only rootfs." No service can actually be deployed to EKS without a Dockerfile that builds —
this task makes that literally true, not merely documented.

## Scope

**In:**
- `services/notification/Dockerfile` — new, two-stage (`maven:3.9-eclipse-temurin-21` build /
  `gcr.io/distroless/java21-debian12:nonroot` runtime), mirroring `services/auth`'s/
  `services/crypto`'s own structure exactly, with the one real fix: copies **every** sibling
  module's own `pom.xml` (`services/auth/pom.xml`, `services/crypto/pom.xml`,
  `services/notification/pom.xml`) into the build context before running Maven, not just its own.
- A real `docker build -f services/notification/Dockerfile -t notification-service .` run from the
  repo root, confirming AC2 is actually true, not assumed from the Dockerfile's own text looking
  plausible.
- A fresh `mvn -pl services/notification clean verify` run, confirming AC1.

**Out:**
- Any change to `services/auth/Dockerfile` or `services/crypto/Dockerfile` — per Phase 0's own
  explicit user decision. Both remain broken exactly as they were before this task started; this
  task's own new file does not fix them as a side effect, and doesn't silently imply it did.
- A root-level `.dockerignore` — neither existing Dockerfile relies on one; adding one now would be
  a build-efficiency nicety unrelated to this task's own literal AC2 ("Docker image builds"), not a
  missing requirement. Not added.
- Any `read-only rootfs` enforcement inside the Dockerfile itself — confirmed directly: neither
  `services/auth/Dockerfile` nor `services/crypto/Dockerfile` sets this (it's conventionally a
  `docker run`/Kubernetes pod-spec-level flag, not a `Dockerfile`-level one); this task mirrors that
  same, already-established division of responsibility, not inventing a new one.
- Any CI/CD pipeline wiring (GitHub Actions, AWS CDK deploy manifests) — this task is "the image
  builds," not "the image is deployed." Out of this task's own literal wording.

## Business Rules

None (confirmed at Phase 1 — no R-numbered requirement governs this task).

## Locked Decisions

- **Deployment** (`agents.md`): multi-stage Docker, distroless JRE 21 base, non-root user. This
  task's own job is to make this real for `services/notification` for the first time.

## Dependencies

None new. No Maven dependency, no new library.

## Inputs

None at runtime — this is a build-time artifact.

## Outputs

One new Dockerfile; a confirmed-passing `mvn verify`; a confirmed-successful `docker build`. No
production Java code changes.

## State Changes

None.

## Files to Create

- `services/notification/Dockerfile`

## Files to Modify

None.

## Files NOT to Modify

- `services/auth/Dockerfile`, `services/crypto/Dockerfile` — per Phase 0's own explicit scope
  decision.
- Root `pom.xml`, `services/notification/pom.xml` — no build configuration change is needed; the
  existing `<finalName>notification-service</finalName>` already produces the exact jar name this
  task's own Dockerfile references.
- Every file under `spec/`.

## Acceptance Criteria

Unchanged from Phase 1 (AC1-AC4).

## Required Tests

None in the usual sense (Phase 1's own finding) — the real verification is a direct `mvn` run (AC1)
and a direct `docker build` run (AC2), both executed, not simulated, before this task is reported
done.

## Constraints

- **No new Maven dependency, no production Java code change** — this is an infrastructure-only
  task.
- **The fix must not touch `services/auth/Dockerfile` or `services/crypto/Dockerfile`** — Phase 0's
  own explicit scope boundary.
- **The built image must run as a non-root user** (`agents.md`'s own Deployment rule) — mirrors
  `USER nonroot` from both existing precedents exactly.

## Open Questions

No blockers. Phase 1's own one open item (`.dockerignore`) is resolved above (not added, out of
scope) — not a blocker, a concrete implementation choice a senior engineer can make directly from
this brief.
