# notification · T02 · Phase 10 — Test Generation

## Outcome: no new tests — coverage already complete, verified against ACs and `package.md` §8

`package.md` §8's 19 named tests (`shouldSendVerificationEmailOnAuthEmailRequestedVerify` → R1
through `shouldPreventCrossModuleEntityImports` → L11) all require feature code — a consumer,
preference resolution, template rendering, or the in-app stream — none of which exists yet. T02 is
schema-only, identical in kind to `crypto-service`'s own T02. None of the 19 apply to this task.

Phases 6 and 9 already wrote and hardened
`services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
proactively (mirroring `crypto-service`'s own T02 precedent of not waiting for a review cycle to
flag "no tests for this task's own ACs"), and Phase 9 closed every gap Kimi's Phase 8 review found.
This phase's own job is the test manifest below — no production code changed, no new test file
needed.

## Test manifest — `NotificationBaselineMigrationIntegrationTest`

| Test method | Verifies | Requirement / Locked decision |
|---|---|---|
| `allEightBaselineTablesExistAndNoOthers` | AC1 — schema completeness, no stray tables | L1, L3, L6, L7, L8, L9 (every table each decision depends on exists, and only those) |
| `v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock` (+ Phase 9 fence-marker guard) | AC1 — `V1` is an unmodified, byte-for-byte transcription of `design.md` §4c | — (VERBATIM discipline, not a numbered requirement) |
| `allMigrationsAreRecordedAsSuccessfulInFlywayHistory` | AC5 — all three migrations recorded successful | — |
| `v2RoleCreationGuardIsIdempotentUnderARealReRun` | AC4 (supports "succeeds against the real Postgres," including on rerun) | — |
| `notificationAppRoleRequiresItsProvisionedPassword` | AC2 — passwordless role creation, real-TCP auth enforced with the provisioned local password | L10 (secrets discipline: no committed password) |
| `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnDeliveryLog` | AC2 — `INSERT`/`SELECT` succeed, `UPDATE`/`DELETE` genuinely denied at the DB level on `delivery_log` | L3 (append-only, enforced at the grant layer) |
| `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope` (extended, Phase 9 Finding #2) | AC2 — `SELECT` **and** `INSERT` both denied on all 7 ungranted tables | L1, L6, L7, L8, L9 (least-privilege boundary around every table this task doesn't grant) |
| `notificationAppCannotPerformDdlInTheNotificationsSchema` | AC2's owner/grantee constraint — no `CREATE`/`DROP` capability | — (Constraints: least privilege) |
| `baselineTablesAreOwnedByTheMigrationRoleNeverByNotificationApp` | AC2's owner/grantee constraint — ownership split is real, not just grant text | — (Constraints: least privilege) |
| `launchTemplatesAreSeededWithVersionOne` (extended, Phase 9 Finding #5) | AC3 — 14 rows, 9 distinct names, version 1, exact `(name, channel)` pairs, EMAIL-has-subject/IN_APP-null-subject convention | L9 (versioned templates) |

AC4 itself (`mvn -pl services/notification flyway:migrate` succeeds against the real local Postgres)
is not a Testcontainers-automatable assertion — it was executed for real in Phase 6 and its exact
command sequence, including the Finding #1 `citext` pre-step, is now documented in
`services/notification/README.md` (Phase 9) so the same success is repeatable, not just recorded
once in implementation notes.

## What still needs no test

Everything `package.md` §8 names — no feature code exists in this task's scope. `contracts/events/`
conformance (R19-adjacent) has nothing to conform to yet, since no consumer exists (task 4 and
later).

## Verification

`mvn -pl services/notification -am verify` — 19 tests, 0 failures (9 `T01SkeletonRegressionTest` + 10
`NotificationBaselineMigrationIntegrationTest`), unchanged from Phase 9's own final record. No
production code or test file modified in this phase.
