# notification · T02 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `V1` byte-for-byte identical to `design.md` §4c | Yes | `V1__notifications_baseline.sql` (96 lines, verbatim) | `v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock` (fence pinned to header comment, Phase 8/11 hardening) | No | No |
| **AC2** — `notification_app` passwordless, `INSERT`/`SELECT`-only on `delivery_log`, no access elsewhere | Yes | `V2__notification_app_role_and_grants.sql:24-40` | `notificationAppRoleRequiresItsProvisionedPassword`, `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnDeliveryLog`, `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope` (SELECT+INSERT+UPDATE+DELETE denial on all 7 ungranted tables, Phase 9/11), `notificationAppCannotPerformDdlInTheNotificationsSchema`, `baselineTablesAreOwnedByTheMigrationRoleNeverByNotificationApp`, `v2sOwnSqlIsIdempotentWhenReExecutedDirectlyNotJustSkippedByFlyway` (Phase 11) | No | No |
| **AC3** — 14 launch-template rows, `version = 1` | Yes | `V3__seed_launch_templates.sql:15-57` | `launchTemplatesAreSeededWithVersionOne` (row count, 9 distinct names, exact 14 pairs, EMAIL/IN_APP subject convention, Phase 9) | No | No — content is disclosed-provisional pending O6 (task 9), not a defect |
| **AC4** — real `mvn -pl services/notification flyway:migrate` succeeds against local Postgres | Yes | Executed in Phase 6 against the real shared local Postgres; `citext` relocation approved and applied; documented and made repeatable in `services/notification/README.md` (Phase 9) | `flywayPluginConnectionDetailsMatchTheDocumentedLocalDevSetup` (Phase 11, guards the plugin config the real command depends on) — the real migration itself is not container-automatable and is not re-run by this suite | No | No |
| **AC5** — `flyway_schema_history` records all 3 migrations successful | Yes | Flyway-managed table, populated by the 3 migrations above | `allMigrationsAreRecordedAsSuccessfulInFlywayHistory` | No | No |
| L1 (idempotent by event key) | Schema only | `V1__notifications_baseline.sql:39-43` (`processed_events`) | `allEightBaselineTablesExistAndNoOthers` (existence only) | Behavior (task 4+) | No — schema-level scope is this task's own literal boundary |
| L3 (dispute-grade, append-only delivery log) | Schema + DB-enforced | `V1:46-62` (shape), `V2:40` (grant) | `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnDeliveryLog` (UPDATE/DELETE genuinely denied at DB level) | No | No |
| L6 (safe-default preference resolution) | Schema only | `V1:15-25` (`channel_preferences`, `uq_pref`, `chk_pref_category`, `chk_pref_channel`) | `allEightBaselineTablesExistAndNoOthers` (existence only) | Resolution logic (task 7+) | No |
| L7 (bounded retry, then dead-letter) | Schema only | `V1:79-88` (`delivery_retry`), `V1:58-59` (`DEAD_LETTERED` outcome) | Existence only | Scheduling logic (task 14) | No |
| L8 (zero trust on in-app surface) | Schema (storage) only | `V1:65-76` (`inapp_notifications`) | Existence only | JWT validation, stream/read API (task 13) | No |
| L9 (versioned templates) | Yes, schema-enforced | `V1:27-36` (`uq_template UNIQUE(name, channel, version)`) | `launchTemplatesAreSeededWithVersionOne` | No | No |
| R7/R8 (dedupe by event key, no double-send) | Not yet | — | — | Consumer (task 4+) | No — correctly out of this task's scope |
| R9/R10/R18 (preference resolution, opt-out, safe default) | Not yet | — | — | `PreferenceResolver` (task 7+) | No |
| R11 (delivery attempts recorded) | Schema only | `V1:46-62` | Existence only | Write path (task 6+) | No |
| R12/R13 (retry, dead-letter) | Not yet | — | — | `RetryScheduler` (task 14) | No |
| R14 (template rendering) | Not yet | — | — | `TemplateRenderer` (task 9) | No |
| R16/R17 (in-app stream, unread read API) | Schema (storage) only | `V1:65-76` | Existence only | `InappStreamController`/`InappReadController` (task 13) | No |
| `package.md` §8 named tests (19 total, R1-R19/L11) | Not applicable | — | — | All require feature code that doesn't exist at T02's scope | No — verified in Phase 10, none map to this task |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T02's own literal scope (`tasks.md` task 2: `V1` +
seed + real migration + `delivery_log` grant). All 5 files delivered (`V1`, `V2`, `V3`, the
integration test, and the README documentation added in Phase 9). No file outside this task's own
`Files to Create`/`Files to Modify` list was touched, and `T01SkeletonRegressionTest.java`,
`pom.xml`, `NotificationServiceApplication.java` remain byte-for-byte unchanged from T01 (re-verified
via `git status -s` before every commit this task made).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC5 all hold, each with direct
evidence and an automated test (AC4's own automated coverage is necessarily partial: the real Maven
invocation itself isn't container-repeatable, but it was executed for real in Phase 6, its result
recorded, and the connection details it depends on are now regression-guarded).

**(3) Does it violate any LOCKED decision?** No. L1/L3/L6/L7/L8/L9 are all schema-level only in this
task, matching Phase 1's own extraction — none required behavioral implementation here, and none of
their schema-level halves were compromised (L3's append-only intent is the one LOCKED decision this
task actually enforces at the DB-permission layer, and it is directly tested).

**(4) Remaining risks?**
- AC4's real-environment success depends on a manual, undocumented-until-now `citext` bootstrap step
  on any *new* Postgres instance (fresh CI runner, new teammate's machine, a future RDS instance) —
  mitigated by Phase 9's README documentation, but not automated; a fresh-environment run has not
  been tested end-to-end since the fix (only the already-provisioned shared local Postgres was
  verified).
- V3's template content (subject/body copy, `{{variable}}` placeholder syntax) is explicitly
  provisional pending O6 (task 9's template-engine choice) — flagged consistently since Phase 1,
  not a new risk.
- `shedlock`'s dependency gap (table exists, no ShedLock Maven dependency/`@EnableSchedulerLock` yet)
  is intentional and deferred to task 14, per the Phase 4 disposition of Finding #7 — carried forward
  correctly, not a regression.

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20
(the service's own final task), matching auth-service's T40 and crypto-service's T29 precedent. Not
touched here.

---

**PASS** — all 5 acceptance criteria satisfied with direct evidence and automated coverage, no LOCKED
decision violated, task boundary held throughout (no files touched beyond this task's own scope).
