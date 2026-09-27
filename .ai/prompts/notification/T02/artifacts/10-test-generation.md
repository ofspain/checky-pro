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

## Addendum (post Phase 11) — all 5 gaps accepted and added

Kimi's Phase 11 review raised 5 gaps against `NotificationBaselineMigrationIntegrationTest`. All 5
verified against the actual test/source files before acting, all genuine, all added (10 tests → 13):

- **Gap #1** (no automated guard for AC4's real Maven-plugin migration path) — added
  `flywayPluginConnectionDetailsMatchTheDocumentedLocalDevSetup`, a plain-text `pom.xml` scan
  asserting `<url>`, `<user>`, `<password>`, `<schemas>` on the `flyway-maven-plugin` block. T01's own
  `finalNameAndFlywayPluginMirrorTheSiblingConvention` already guards `<schemas>` and the
  no-`<executions>` rule but not connection details — not touched (T02's scope excludes modifying
  `T01SkeletonRegressionTest.java`), so this is T02's own copy of that same guard style, scoped to
  what this task's AC4 needs.
- **Gap #2** (V2 idempotency test only proves Flyway's bookkeeping, never re-executes V2's own SQL) —
  added `v2sOwnSqlIsIdempotentWhenReExecutedDirectlyNotJustSkippedByFlyway`: reads `V2`'s file content
  and executes it directly against the admin connection a second time, then re-proves the grant is
  still exactly as scoped.
- **Gap #3** (UPDATE/DELETE on the 7 ungranted tables were never tested) — extended
  `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope` with per-table UPDATE/DELETE denial
  checks. Kimi's own suggested snippet used a `WHERE name = 'no-such-row'`-style predicate; a real
  mutation test (temporarily adding `GRANT UPDATE ON notifications.templates TO notification_app` to
  V2, rerunning just this test) proved that shape doesn't work — Postgres denies the WHERE-column
  read (needing `SELECT`) before ever reaching the UPDATE check, so the assertion would still pass
  even with UPDATE mistakenly granted, silently failing to catch exactly the regression it exists
  for. Fixed by dropping the `WHERE` clause entirely (blind, table-wide UPDATE/DELETE, isolating the
  UPDATE/DELETE privilege check with nothing else to confound it); re-ran the same mutation — test
  now genuinely fails with no exception thrown; reverted V2, full suite green again.
- **Gap #4** (V2's secrets-discipline invariants — no committed password, no hardcoded database name,
  dynamic `current_database()` — asserted only in prose) — added
  `v2ContainsNoCommittedPasswordOrHardcodedDatabaseName`: strips `--` comment lines first (the header
  comment legitimately documents the local-dev `ALTER ROLE ... PASSWORD` step as prose), then asserts
  the remaining executable SQL contains no `PASSWORD`, no hardcoded `checky`, and does contain
  `current_database()`.
- **Gap #5** (the `citext`-into-`notifications` pre-step wasn't itself regression-guarded) — added an
  assertion immediately after the `@BeforeAll` pre-step querying `pg_extension`/`pg_namespace`,
  confirming `citext` actually landed in `notifications`, with a message explaining why (`V1`'s own
  `SET search_path` requirement).

**Verification:** `mvn -pl services/notification -am verify` — 22 tests, 0 failures (9
`T01SkeletonRegressionTest` + 13 `NotificationBaselineMigrationIntegrationTest`). One real mutation
test performed and reverted clean (`git status -s` on `V2__notification_app_role_and_grants.sql`
empty after revert), catching a genuine gap in Gap #3's own first-draft fix before it was ever
committed.
