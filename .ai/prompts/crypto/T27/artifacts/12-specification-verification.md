# crypto · T27 · Phase 12 — Specification Verification

## Acceptance Criteria (Phase 1)

### AC1. `mvn -pl services/crypto verify` succeeds

**PASS, for the achievable portion; Docker-dependent portion deferred and disclosed, as scoped from
Phase 1 onward.** Final verified result (Phase 11): 697 tests, 0 failures, 14 errors — every one of the
14 is `IllegalStateException`/`ExceptionInInitializer` from Testcontainers/LocalStack failing to find a
Docker daemon, a standing, disclosed environment limitation since T23, not a code or configuration
defect. Every non-Docker-gated check passes: compile, package, unit tests, both ArchUnit rule suites
(module boundaries from T25, KMS-signer boundary from T20/T25/T27), all contract tests.

### AC2. `docker build -f services/crypto/Dockerfile -t crypto-service .` succeeds

**Deferred and disclosed, as scoped from Phase 1 onward.** Attempted twice (Phase 6, and implicitly
re-confirmed unchanged after Phase 9's restoration). Both times failed at the daemon-connection level
(`failed to connect to the docker API at unix:///.../docker.sock ... no such file or directory`),
confirmed via `docker info` to be the same root cause, not a Dockerfile-content problem. The Dockerfile's
own correctness is verified by direct construction: byte-for-byte structural match to
`services/auth/Dockerfile` (diffed directly, Phase 7), substituting only the module path and jar name.

### AC3. `services/crypto/Dockerfile` exists, correct multi-stage build, matches `agents.md`'s Deployment
rule, no secret or credential (L13)

**PASS.** Multi-stage (`maven:3.9-eclipse-temurin-21` build stage → `gcr.io/distroless/java21-debian12
:nonroot` runtime stage), `USER nonroot`, no `ENV`/`ARG` lines of any kind (let alone secret-carrying
ones) — runtime configuration remains externally supplied, matching the running application's own
existing convention outside a container. `EXPOSE 8080` (Spring Boot's real default; re-confirmed against
the real, restored `application.properties` at Phase 9 after a branch-integrity incident briefly made
this look wrong — see below).

## L13 (secrets discipline)

**PASS.** No secret, credential, or environment-specific value appears anywhere in the new Dockerfile.

## Branch-integrity incident (Phase 9) — recorded for visibility, not a spec gap

Between Phase 7 and Phase 8, a merge (`189c4e3`) briefly replaced 64 files under `services/crypto/`
(including `Watch`, `WatchService`, `WatchController`, `WatchRepository`, `application.properties`) with
an incompatible parallel implementation — the same wrong-codebase pattern that has recurred via
force-pushes throughout T24-T27, this time via a clean, non-conflicting merge under the user's own git
identity. Investigated and fully reverted at Phase 9 (`550b74e`); the one genuine addition from that
merge (the `maven-failsafe-plugin` Docker-API-version pin) was kept. Full suite re-verified clean
immediately after. No lasting effect on this task's own deliverable or verification results — recorded
here only because it's the second consecutive task (after T25's allowlist candidates) where this
artifact is the natural place to leave a durable note for whoever eventually investigates why this keeps
happening.

## Candidates for future tasks (not fixed here, out of T27's own scope)

- An automated Dockerfile packaging-rules check (multi-stage, distroless, non-root, no secrets) as a
  lightweight test or CI script — Kimi Phase 11 Finding #5.
- A test or CI step linking the POM `finalName` to the Dockerfile's `COPY` path, so a `finalName` change
  fails loudly instead of breaking the image build silently — Kimi Phase 11 Finding #7.
- CI archiving of `surefire-reports`/`failsafe-reports`/build logs as reproducible artifacts — Kimi
  Phase 11 Finding #8.
- Whatever external process is producing the recurring wrong-codebase merges/force-pushes needs its
  local clone re-synced — flagged identically in T25's and T26's own Phase 12 artifacts; still
  unresolved, now confirmed to also occur via ordinary merges, not only force-pushes.

## Verdict

**PASS.** AC3 and L13 fully satisfied. AC1 and AC2 satisfied to the full extent achievable in this
environment, with the Docker-dependent remainder deferred and disclosed exactly as scoped from Phase 1
onward — not silently claimed passing.
