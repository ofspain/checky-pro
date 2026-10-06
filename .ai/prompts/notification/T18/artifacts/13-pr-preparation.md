# notification · T18 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T18: Dockerfile and build/image checkpoint`

## Commit message

```
notification-service T18: Dockerfile and build/image checkpoint

Adds services/notification/Dockerfile, the first deployable image for this
service. Two-stage build (maven:3.9-eclipse-temurin-21 to
gcr.io/distroless/java21-debian12:nonroot), non-root, EXPOSE 8082 (the real
server.port default, not the sibling precedent's literal 8080).

The Dockerfile copies every sibling module's pom.xml before running Maven.
This fixes a shared reactor-validation bug: the root pom declares all three
service modules, and Maven requires each declared module's pom to be present
even when only one is built. Both existing auth and crypto Dockerfiles copy
only their own pom and still fail docker build for this reason. Those two
files are left untouched here, per the Phase 0 scope decision; the bug is
recorded as the top repo-level follow-up.

Verified in this environment, not assumed:
- mvn -pl services/notification clean verify: 369 tests, 0 failures, 0 errors
- docker build -f services/notification/Dockerfile -t notification-service .: exit 0
- docker inspect: User=nonroot; docker export: only app/app.jar under /app

Phase 9 had a real environmental failure, not a code one: the Docker daemon and
the shared local Kafka broker (services/auth/compose.local.yaml) were stopped.
Both were restored, and the suite then passed.

Known follow-ups, deliberately out of scope:
- The Dockerfile's hand-listed sibling poms will break docker build again if a
  fourth module is added to the root pom. Needs a guard or a structure-
  preserving copy.
- auth and crypto Dockerfiles still fail docker build (shared reactor bug).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/Dockerfile`

**Modified**

None.

**Deleted**

None.

**Process artifacts**
- `.ai/prompts/notification/T18/artifacts/00-13-*.md`: the full 14-phase pipeline record. This
  file completes it.

## Summary

Adds the first deployable image for `notification-service`, closing the Deployment rule `agents.md`
has described since Phase 1. The image is a two-stage, distroless, non-root build on Java 21, exposing
the service's real port 8082. Making it build required fixing a shared Maven reactor-validation bug
that both existing sibling Dockerfiles still have. That fix is applied only to this new file, per the
Phase 0 scope decision. The sibling Dockerfiles stay as they were.

## Testing performed

- `mvn -pl services/notification clean verify`: 369 tests, 0 failures, 0 errors, exit 0. Run fresh in
  Phases 6, 9, and 11.
- `docker build -f services/notification/Dockerfile -t notification-service .` from the repo root:
  exit 0, run in Phases 6 and 9.
- `docker inspect` shows `User=nonroot` and exposed port `8082/tcp`. `docker export` shows only
  `app/app.jar` under `/app`.
- A smoke-test run launched the jar, which reached Spring Boot startup and failed only on missing
  runtime configuration, as expected for a standalone run. This is evidence that `agents.md`'s
  fail-fast config rule holds in the image too.
- `git diff --stat ccf1299^..HEAD -- services/auth services/crypto services/payment spec/`: empty.
  No sibling service or spec file changed.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 18 ("Run full suite").
- **Standing rule:** `agents.md` Deployment ("multi-stage Docker, distroless JRE 21, non-root,
  read-only rootfs").
- **Requirements:** none (no R-number governs this task; confirmed at Phase 1).

## Known, deliberate gaps (not this task's scope)

- **Sibling-pom drift**: a fourth module added to the root `pom.xml` breaks `docker build` until the
  Dockerfile is updated. Deferred to a deliberate design choice, tracked with the auth/crypto follow-up.
- **auth and crypto Dockerfiles still fail `docker build`**: the same reactor bug. Explicitly left
  for a separate task by user decision.
- **Read-only rootfs** is not set in the Dockerfile. It is a runtime/pod-spec concern, consistent with
  both sibling precedents.

## Reviewer notes

- **Kimi's Phase 8 review (4 findings) and Phase 11 review (3 gaps)** raised no code defects. Its one
  material concern, that `mvn` and Docker could not be verified in its sandbox, was closed by real
  runs in this environment. Its suggestion for a module-drift guard is the same one already recorded
  as a deferred follow-up.
- **This task's own Phase 4 correction** set `EXPOSE 8082` instead of the sibling precedent's literal
  `8080`, which was an unchecked assumption in the Phase 2 brief. Caught before implementation.
- **An environmental verification failure occurred and was resolved, not hidden**: Docker and the
  shared Kafka broker had stopped, causing 19 test errors across two runs. Both were restored, and the
  suite passed with no code change.

---

**Phase 13 complete. PR description drafted. All phases 0-12 closed for notification-service T18.**
