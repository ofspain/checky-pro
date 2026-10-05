# notification · T18 · Phase 6 — Implementation Notes

Implemented exactly per the Phase 5 plan — no deviation. One new file, zero production Java code
changes, zero new dependencies.

## Files created

- `services/notification/Dockerfile` — two-stage build, `maven:3.9-eclipse-temurin-21` →
  `gcr.io/distroless/java21-debian12:nonroot`, copying every sibling module's own `pom.xml`
  (Finding #1's fix) and exposing `8082` (Finding #2's fix) instead of the sibling precedent's
  literal `8080`.

## Files modified

None.

## Deviation from the plan

None. Both of Phase 4's own real corrections (the sibling-pom copy, the port) were implemented
exactly as planned.

## Real verification performed — in this agent's own environment, not deferred

- **AC1**: `mvn -pl services/notification clean verify` — fresh, `Tests run: 369, Failures: 0,
  Errors: 0`, `BUILD SUCCESS`.
- **AC2**: `docker build -f services/notification/Dockerfile -t notification-service .` run from
  the repo root — succeeded, real exit code 0 confirmed directly (not through a `| tail` pipe,
  which silently reports the pipe's own last-command exit status rather than the build's own — a
  real pitfall hit and corrected mid-phase, disclosed below). Image tagged
  `notification-service:latest`, 152MB content size.
- **AC3**: `docker inspect notification-service --format '{{.Config.User}}'` → `nonroot`, confirmed
  directly, not assumed from the Dockerfile's own `USER nonroot` line alone. A real smoke-test run
  (`docker run -d notification-service`) confirmed the jar genuinely launches the Spring context —
  it proceeds through startup to a real, config-dependent failure (`Unable to determine Dialect
  without JDBC metadata` — no datasource configured for a standalone run with no environment
  variables), not a packaging/classpath/entrypoint defect. This is the expected, correct behavior
  for a container run without its real runtime configuration, and is itself live evidence that
  `agents.md`'s own "startup fails on missing config in non-local profiles" rule holds inside the
  built image too, not only under `mvn test`.
- **AC4**: confirmed directly — `services/auth/Dockerfile` and `services/crypto/Dockerfile` are
  untouched (`git status` shows only the one new file); both still fail `docker build` for the
  same pre-existing reason, unchanged.

## A real pitfall hit and corrected during this phase

The first `docker build` invocation was run as `docker build ... | tail -50` in the background. Its
own reported "exit code 0" was misleading — in a shell pipeline without `pipefail`, the reported
exit status is the *last* command's (`tail`'s), not `docker build`'s own, and `tail` buffers until
EOF, producing no visible progress either. Corrected by killing that process and re-running with
output redirected directly to a file (no pipe), confirming the real build's own actual exit code
and real progress. The genuinely completed build is the one this phase's own verification is based
on.

## Mapping to acceptance criteria

- **AC1**: ✅, see above.
- **AC2**: ✅, see above — the real, literal task wording ("Docker image builds"), not merely "a
  Dockerfile exists."
- **AC3**: ✅, see above — distroless base, non-root user, confirmed by direct inspection and a real
  run, not assumed.
- **AC4**: ✅ — `services/auth`/`services/crypto`'s own Dockerfiles remain untouched and still
  broken, exactly as Phase 0's own user decision specified.

## Verification

- `mvn -pl services/notification clean verify` — 369 tests, 0 failures, 0 errors.
- `docker build -f services/notification/Dockerfile -t notification-service .` — succeeded
  (confirmed via direct, unpiped exit-code check after correcting the pipe pitfall above).
- `docker inspect`/`docker run` smoke test — confirmed non-root user and a genuine Spring Boot
  startup attempt, failing only on missing runtime configuration, as expected for a standalone run.
