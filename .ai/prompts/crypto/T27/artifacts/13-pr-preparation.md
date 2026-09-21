# crypto · T27 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Add the crypto-service Dockerfile and run the full verification suite (T27)
```

## Commit message

```
Add the crypto-service Dockerfile and run the full verification suite (T27)

Closes package.md §9's own final verification-checklist bullet, and
this service's equivalent of auth's already-completed task 37:
services/crypto/Dockerfile, a direct structural mirror of
services/auth/Dockerfile (multi-stage, maven:3.9-eclipse-temurin-21
build -> gcr.io/distroless/java21-debian12:nonroot runtime, USER
nonroot, EXPOSE 8080, no secrets), diffed byte-for-byte against the
original after substituting only the module path and jar name.

Running mvn -pl services/crypto verify in full, for the first time in
this pipeline's history (every prior task's own verification relied on
test-compile, since Docker has been unavailable throughout), surfaced
one genuine, non-Docker defect: KmsSignerArchitectureTest's own
attest-module boundary rule (deliberately scanning test sources too)
correctly flagged T26's EndToEndIntegrationTest for mocking KmsSigner
from the watch package. Resolved with a narrow, named allowlist entry,
mirroring CrossModuleEntityArchitectureTest's own established pattern
- moving the test into attest was considered and rejected, since it
would lose package-private access to Watcher, ObservationRepository,
and ScreeningResultRepository.

A branch-integrity incident occupied a large part of this task's own
Phase 9: a merge performed mid-review replaced 64 files under
services/crypto/ - including Watch, WatchService, WatchController,
WatchRepository, and application.properties - with an incompatible
parallel implementation, the same wrong-codebase pattern that has
recurred via force-pushes throughout T24-T27, this time via a clean,
non-conflicting merge. Fully investigated and reverted file-for-file
to the last known-good commit; the one genuine addition from that
merge (a maven-failsafe-plugin Docker-API-version pin, fixing a real
docker-java/Docker-Engine-29.x negotiation mismatch unrelated to this
environment's own separate daemon-absence issue) was kept.

Final verified state: 697 tests, 0 failures, 14 errors - every one of
the 14 is Testcontainers/LocalStack failing to find a Docker daemon, a
standing, disclosed environment limitation since T23, not a defect.
docker build was attempted and confirmed blocked only at the same
daemon-connection level; the Dockerfile's own correctness rests on its
exact structural match to auth's own already-working file.
```

## Testing performed

- `mvn -pl services/crypto -am verify` — run three times across this task (Phase 6, Phase 9 after the
  branch restoration, Phase 11 after the allowlist positive-proof addition). Final result: 697 tests, 0
  failures, 14 Docker-only errors.
- `mvn -pl services/crypto -am test -Dtest=KmsSignerArchitectureTest` — run repeatedly during Phase 6/7/9
  to validate each incremental change to the allowlist condition in isolation.
- `docker build -f services/crypto/Dockerfile -t crypto-service .` — attempted; confirmed blocked only at
  the daemon-connection level (`docker info` shows the same root cause), not a Dockerfile-content issue.
- `git diff` (structural, byte-for-byte after substitution) confirming the Dockerfile is an exact mirror
  of `services/auth/Dockerfile`.
- Full traceability against Phase 1's AC1-AC3 and L13: `artifacts/12-specification-verification.md` —
  verdict **PASS**.
- **Not performed: an actual `docker build`/image-start, or the Testcontainers-based integration test
  suite.** Docker has been unavailable in this development environment throughout this task's entire
  lifecycle, exactly as it has been since T23. Disclosed plainly, here and in every preceding phase's own
  artifacts, as this task's principal residual risk.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 27 ("Run full suite / Dockerfile"), `package.md` §9's
  final verification-checklist bullet.
- **Requirements:** none individually numbered — a process/verification gate, not a functional behavior
  (Phase 1).
- **LOCKED decisions:** L13 (secrets discipline), confirmed satisfied.
- **Flagged, not fixed, for a future task:**
  - An automated Dockerfile packaging-rules check (multi-stage, distroless, non-root, no secrets) — Kimi
    Phase 11 Finding #5.
  - A test/CI step linking the POM `finalName` to the Dockerfile's `COPY` path — Kimi Phase 11 Finding #7.
  - CI archiving of surefire/failsafe reports and build logs as reproducible artifacts — Kimi Phase 11
    Finding #8.
  - The recurring wrong-codebase branch-poisoning issue, flagged identically in T25's and T26's own
    Phase 12 artifacts — this task confirms it also occurs via ordinary, clean merges under a real git
    identity, not only via force-pushes. Whatever external process is producing this needs its local
    clone re-synced.
  - Re-verify AC1's Testcontainers portion and AC2 by an actual execution at the first opportunity in a
    Docker-available environment.
