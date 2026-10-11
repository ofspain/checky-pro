# payment · T01 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch (not a per-task `main`-based PR), mirroring
`auth-service`/`crypto-service`/`notification-service`'s own identical convention for this
pipeline. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T01: service skeleton & Maven POM`

## Commit message

```
payment-service T01: service skeleton & Maven POM

Registers services/payment in the root Maven reactor and gives it a
pom.xml mirroring the resource-server-only convention crypto-service and
notification-service already established (not auth-service's own
OIDC-issuer-specific dependencies): web, validation, resource-server,
data-jpa, flyway, postgres, spring-kafka, actuator, prometheus,
testcontainers, archunit, awaitility, plus the S3 SDK for the receipt
WORM store only (L9). The KMS SDK is deliberately absent (L3), as are
ShedLock and jackson-dataformat-yaml, both deferred to the tasks that
actually need them (8 and 24).

Adds a bare PaymentServiceApplication (@SpringBootApplication + the
standard main entry point only) and T01SkeletonRegressionTest (8 plain-
JUnit guard tests covering AC1-AC5 plus version alignment with
services/auth/pom.xml, a real S3Client construction, and an L13
negative-proof) so this task's own acceptance criteria fail CI if
silently reverted, rather than relying on a later re-read of the files.

Ran this task through all 14 phases of the spec-driven pipeline,
including two independent-review passes and one test-review pass
(the last produced directly this round, held to the same adversarial
standard). Every finding across all three was verified against real
source before being accepted or rejected - three were accepted and
fixed in the implementation (Phase 3: a version-pin gap, a relativePath
omission, an artifactId/finalName gap, among others), three were
rejected as premature (Phase 8, all hinging on test-writing being
Phase 10's job, not Phase 6's), and three were accepted and fixed in
the test suite itself (Phase 11: a Testcontainers-postgres false-
negative, a comment-ambiguity risk already proven real once in this
same file, and a missing duplicate-dependency guard). Every applied
fix was mutation-tested: the real defect introduced, confirmed caught,
reverted, confirmed clean again.

Spec: spec/payment-service/tasks.md task 1. Locked decisions: L3, L9,
L12, L13 (L13 carries one explicit, human-approved carve-out for the
pre-existing, cross-service local-dev Flyway plugin password pattern -
documented, not silently repeated).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

(The task-template's own generated trailer text names a different, stale model identity; the
trailer above is this session's real, current convention and is what was actually used on every
commit for this task.)

## Files changed

**Created**
- `services/payment/pom.xml`
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`

**Modified**
- `pom.xml` (root — one `<module>` line)

**Process artifacts (not application code)**
- `.ai/prompts/payment/T01/artifacts/00-*.md` through `12-*.md` — the full 14-phase pipeline
  record for this task (this file is `13-*.md`).

## Summary

Pure build-scaffolding: no requirement, entity, repository, service, or controller code exists yet
(all deferred to tasks 2–29, per this spec's own ordering — schema, config, consumers/state
machine, then attestation/receipts/ledger, then exports/contracts/hardening). The module now
compiles, registers correctly in the full 5-module reactor, and carries exactly the dependency set
the frozen brief named — no more, no less. Two real bugs were found and fixed in the task's own
test suite along the way (Phase 10: pom comments defeating their own negative-proof checks; Phase
11: a false-negative risk, a latent comment-ambiguity risk, and a missing duplicate guard) — each
found by actually running the tests and deliberately breaking things, not assumed correct from
reading the code alone.

## Testing performed

- `mvn -pl services/payment -am compile` and a forced `clean compile` — confirmed a real (not
  cached) single-source-file compile.
- `mvn -pl services/payment validate` / `test` — clean at every phase checkpoint.
- Full 5-module reactor `mvn validate` — confirmed registering `services/payment` didn't disturb
  `auth`/`crypto`/`notification`.
- `mvn -pl services/payment dependency:tree` (filtered to `software.amazon.awssdk`) — confirmed the
  S3 SDK resolves cleanly at `2.50.2` via the BOM, no version conflict.
- Final `T01SkeletonRegressionTest` run: **8/8 passing.**
- Five separate mutation tests across Phases 10 and 11 (BOM version bump, a `//` comment
  containing the scanned text, a duplicated dependency block, the Testcontainers-postgres removal,
  plus the original two pom-comment false-negatives) — each confirmed to actually fail the
  relevant test, then reverted and confirmed clean.
- A bonus full-reactor `mvn test` surfaced 7 pre-existing `auth-service` test failures, confirmed
  real (reproduced in isolation) and confirmed unrelated to this task (the only change outside
  `services/payment` was the one root-pom `<module>` line). Logged as a tracked follow-up in
  memory, not fixed here — out of this task's own scope, and the user explicitly chose to continue
  rather than pivot to it.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 1 — "Service skeleton & POM."
- **Requirement IDs:** none (pure scaffolding; no `requirements.md` R-number applies, consistent
  with every sibling service's own T01).
- **LOCKED decisions:** L3 (no KMS/chain client), L9 (S3 for receipt store only), L12 (module
  boundaries), L13 (secrets, with the one documented Flyway local-dev carve-out approved at the
  Phase 4 Human Approval gate).

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T01.**
