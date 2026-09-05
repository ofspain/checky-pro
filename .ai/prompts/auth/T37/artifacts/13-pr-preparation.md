<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T37 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS** (under the frozen brief's own AC1a/AC1b/AC2 structure). Branches off
`main`; `main` stays deployable throughout.

---

## Commit title

```
auth: fix FK-violating fabricated-UUID tests, run full suite (T37)
```

## Commit message

```
auth: fix FK-violating fabricated-UUID tests, run full suite (T37)

Final-verification task: confirm mvn -pl services/auth verify passes and
the Docker image builds from the repo root. Neither was literally true at
Phase 0 - a fresh run found 702 tests, 1 failure, 8 errors, falling into
three independently-diagnosed groups. Group C (3 tests, AuditTrailIntegrationTest
+ RoleAssignmentIntegrationTest) used UUID.randomUUID() as an account
principal without ever creating the account, violating auth_audit's real FK
to accounts - fixed here using the same registerAndActivate pattern already
established in SessionIntegrationTest/CleanupIntegrationTest. Groups A
(Kafka producer->broker connectivity, already logged during T36) and B
(null-response flakiness under full-suite load, observed since T31) have
no known code-level fix or confirmed root cause respectively - both
explicitly deferred with independent corroborating evidence at this task's
own human gate, not silently tolerated.

The fix predictably unmasked a second, pre-existing problem in
AuditTrailIntegrationTest: fixing the FK violation let the test reach its
own Kafka-wait step for the first time, which now hits Group A directly -
forecast in the implementation plan before the fix was written, then
confirmed exactly as predicted.

Independent review also surfaced a real, verified production observation
kept explicitly out of scope: neither AuditService.record nor
RoleService.assignRole validates an account UUID's existence before the
write, relying entirely on the database FK - an admin submitting a typo'd
account UUID to the role-assignment endpoint currently gets an opaque 500
rather than a clean 404. Logged as a follow-up candidate, not fixed here,
since T37's job is running the suite, not adding production validation.

The Docker image build was verified directly (docker build -f
services/auth/Dockerfile -t auth-service . from the repo root) and
succeeds cleanly.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Tests only**
- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java` (modified —
  real-account fix, JSON-parsed Kafka assertion, new `auth_audit` row assertion)
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java`
  (modified — real-account fix, two of three test methods)

No production code changed. No `spec/` file touched. No migration.

## Summary

Brings `services/auth`'s full test suite from 1 failure/8 errors to 1 failure/6 errors by fixing the
one group of failures with a known, cheap, already-established root cause and fix pattern, while
explicitly — not silently — deferring the two groups that have no code-level fix or confirmed root
cause. Confirms the Docker image build succeeds from the repo root, the task statement's other
literal requirement. This task's own review process (14 findings across Phases 7/8/11) closed one
genuinely new gap (a direct `auth_audit` row assertion, not just the Kafka mirror) and surfaced one
real, verified, deliberately-unfixed production observation (missing account-existence validation)
for a future task to pick up.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, at every phase.
- `mvn -pl services/auth test -Dtest=RoleAssignmentIntegrationTest` — 3/3 pass (was 1/3 before the
  fix).
- `mvn -pl services/auth test -Dtest=AuditTrailIntegrationTest` — FK violation confirmed gone; the
  new `auth_audit` row assertion passes; Group A's already-logged Kafka timeout is the only
  remaining, unchanged, already-diagnosed failure.
- `mvn -pl services/auth verify` (full suite): 702 tests, 1 failure, 6 errors — exact 2-test
  reduction from the Phase 0 baseline, zero regressions confirmed by comparing every remaining
  failure's signature line-by-line against that baseline.
- `docker build -f services/auth/Dockerfile -t auth-service-t37-check .` (repo root) — exit 0,
  459MB image built and exported; test image removed after confirmation.

## Specification references

- **Task:** T37 — Run full test suite (`spec/auth-service/tasks.md`, task 37)
- **Requirements:** R43
- **LOCKED decisions:** L1, L12 (both honored, neither required deviation)
- **Named tests (`package.md` §8):** none scoped to this task

## Known, logged, out-of-scope follow-ups

1. **Group A — Kafka producer→broker connectivity**, blocking `EndToEndLifecycleIntegrationTest`,
   `AccountPersistenceIntegrationTest`, and (newly unmasked) `AuditTrailIntegrationTest`'s Kafka-wait
   step. No known code-level fix; first logged during T36, independently reproduced multiple times
   across this and the prior task.
2. **Group B — null-response flakiness** under full-suite load in `ApiKeyLifecycleIntegrationTest`/
   `ApiKeyExchangeIntegrationTest` (4 tests). No confirmed root cause; observed since the T31 era,
   unrelated to any code this task or T31-T36 touched.
3. **Account-existence validation gap** in `AuditService.record`/`RoleService.assignRole` — both
   rely entirely on the database FK rather than validating input, meaning a caller-supplied
   non-existent account UUID (e.g., a typo'd admin API call) currently surfaces as an opaque 500
   rather than a clean 404. Verified real via source inspection at Phase 4; explicitly not fixed
   here per human-gate decision.

---

**Phase 13 complete — PR preparation written. T37 is ready for merge.**
