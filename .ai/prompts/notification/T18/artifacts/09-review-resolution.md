# notification · T18 · Phase 9 — Review Resolution

**Human Approval gate.** Resolution log for the Phase 7 self-review (2 findings) and Phase 8
independent review (4 findings). Every factual claim was checked directly against source or a real
run before disposition.

## Finding 1 (self-review + Kimi concur) · The sibling-pom list is hand-maintained

**ACCEPTED, no code change.** Both reviews agree this is a real deferred hazard: a fourth module
added to the root `pom.xml` would reintroduce the same reactor error. Tracked as a follow-up alongside
the already-known auth/crypto Dockerfile fix, since both share one root cause. Kimi's own note that a
flat wildcard copy would break the directory layout (Finding 4) is correct — any future generalization
must preserve structure, which I confirmed is the only reason the explicit list was chosen.

## Finding 2 (self-review + Kimi concur) · Only a `latest` tag is produced

**ACCEPTED, no change.** Matches the sibling precedent; release tagging is CI/CD scope.

## Finding 3 (Kimi, new) · `mvn verify` and `docker build` could not be confirmed in Kimi's environment

**ACCEPTED as a correct gap for Kimi, and re-verified here with real runs.** Kimi's sandbox has no
Maven or Docker, so it correctly declined to claim AC1/AC2. I ran both directly this phase.

The first re-run failed in a way worth recording honestly: 11 Testcontainers-based tests errored
because the Docker daemon was not running (`failed to connect to the docker API`). I started Docker
Desktop and re-ran. The image then built (exit 0, `USER nonroot`, `EXPOSE 8082`), but 8 Kafka-backed
consumer tests still errored with `KafkaException: Send failed`. Cause: the shared local Kafka broker
(`auth-kafka-1`, defined in `services/auth/compose.local.yaml`) had been stopped for 26 hours. I
started only that service with `docker compose -f compose.local.yaml up -d kafka`, not the rest of
the stack, and re-ran.

**Final result, real run:** `mvn -pl services/notification clean verify` → exit 0, 369 tests,
0 failures, 0 errors. `docker build -f services/notification/Dockerfile -t notification-service .` →
exit 0.

This is an environment-state finding, not a code defect: the same test suite passed earlier in this
session, and the failures tracked the stopped Docker daemon and stopped broker exactly. Worth noting
for future sessions that the shared broker and Docker are not guaranteed to be up.

## Summary

No code change required by any review finding. Both verification commands pass in this environment
after restoring its services. Kimi's own correct assessment (AC1/AC2 unverifiable in its sandbox) is
now closed by direct evidence.
