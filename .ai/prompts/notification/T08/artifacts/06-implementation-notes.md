# notification · T08 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). `src/main` files created plus the frozen-brief-authorized
`NotificationBaselineMigrationIntegrationTest` update — no new test file authored (Phase 10 scope,
per this phase's own rule, mirroring T05's own precedent exactly), plus one justified,
undisclosed-in-brief `T01SkeletonRegressionTest.java` update (same recurring gap T04/T05/T06 each
hit in turn).

## Files created

- `preference/ChannelPreference.java` — `@Entity` mapping onto all 6 `channel_preferences` columns;
  read-only (no `create(...)`/setters beyond JPA's own protected no-arg constructor) — nothing in
  this codebase constructs or persists an instance, matching the "no write API anywhere in this
  spec" finding from Phase 0/1.
- `preference/ChannelPreferenceRepository.java` — package-private, `extends JpaRepository` per
  T04/T05's own established precedent (Finding #4, documented not narrowed); one derived query,
  `findByAccountUuidAndCategoryAndChannel`.
- `preference/PreferenceResolver.java` — `@Service`, `resolve(UUID, String, String)`: null-checks
  all three arguments (Finding #6), uppercases `category`/`channel` (Finding #2), returns `true`
  unconditionally for `SECURITY`+`EMAIL` without querying the repository, otherwise queries and
  falls back to a 6-entry `Map<String, Boolean>` default table (exact DB constant spelling, Finding
  #8), returning `false` for any pair outside that table (`WEBHOOK`/`PUSH`/anything unrecognized —
  Findings #1/#3).
- `db/migration/V6__notification_app_channel_preferences_grant.sql` — `GRANT SELECT` only.

## Files modified

- `NotificationBaselineMigrationIntegrationTest.java` (Finding #5, frozen-brief-authorized) —
  `channel_preferences` moved from `UNGRANTED_TABLES` to a dedicated
  `notificationAppCanSelectButNotInsertUpdateOrDeleteOnChannelPreferences` test (admin inserts the
  fixture row, `notification_app` proves `SELECT` succeeds and `INSERT`/`UPDATE`/`DELETE` are all
  denied — the first table in this module where the grant itself is `SELECT`-only, so unlike
  `processed_events`/`contact_projection` the fixture row can't be inserted by `notification_app`
  itself); its own 3 now-dead cases removed from the three ungranted-side fixture switches;
  Flyway-history expectation widened to `"1".."6"`.
- `T01SkeletonRegressionTest.java` — `noExtraProductionClassesExistBeyondT06sOwnAuthorizedSet`
  renamed to `...T08sOwnAuthorizedSet`; 20-file list widened to 23 (`ChannelPreference`/
  `ChannelPreferenceRepository`/`PreferenceResolver`, sorted correctly within `preference/`).

## Verification performed

- `mvn -pl services/notification clean verify` — 128 tests, 0 failures, clean `package`/`repackage`
  (up from 127; +1 = the new `channel_preferences` grant-proof test; `PreferenceResolver`'s own
  behavioral tests are Phase 10's job per this phase's own "no tests" rule, matching T05's precedent
  of no new test file in Phase 6 when nothing structural needs proving before Phase 10).
- `V6` migrated successfully in every Testcontainers run
  (`Successfully applied 6 migrations ... now at version v6`).
- Manually traced `PreferenceResolver.resolve` against all 8 frozen-brief findings by inspection:
  confirmed the `SECURITY`+`EMAIL` branch returns before any repository call (Finding #1's own
  "never even queries" requirement), confirmed the default map's 6 keys match §4c's own table
  exactly (spot-checked against `design.md` directly), confirmed uppercasing happens before both
  the hardcoded-pair check and the repository call.

## Acceptance criteria mapping

- **AC1** — `ChannelPreference` maps exactly onto the 6 existing columns. ✅ (validated against the
  real schema by Hibernate itself in every Testcontainers `@SpringBootTest` boot.)
- **AC2/AC3/AC6** — stored-row and default-fallback logic implemented as designed; real proof
  (stored-row precedence, all 6 defaults, `WEBHOOK`/`PUSH`/unknown-pair `false`) is Phase 10's own
  job per this phase's "no tests" rule — not yet automated, but the logic itself is in place and
  was exercised implicitly (no error) by every Testcontainers boot in this suite.
- **AC4** — `SECURITY`+`EMAIL` special-cased before any repository call — confirmed by direct
  source inspection (`resolve`'s own early-return, `PreferenceResolver.java`).
- **AC5** — `V6` grants `notification_app` `SELECT` only; `INSERT`/`UPDATE`/`DELETE` all denied —
  proven by the new dedicated test.
- **AC7** — case-insensitivity via `toUpperCase(Locale.ROOT)` before every lookup — confirmed by
  direct source inspection; real behavioral proof is Phase 10's own job.
