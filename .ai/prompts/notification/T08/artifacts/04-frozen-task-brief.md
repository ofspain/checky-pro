STATUS: FROZEN

# notification · T08 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 8 findings independently verified against source before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | `WEBHOOK`/`PUSH` defaults unspecified | High | **ACCEPTED** | `PreferenceResolver` returns `false` for any `(category, channel)` pair not among the 6 named defaults (`SECURITY`/`PAYMENT`/`MARKETING` × `EMAIL`/`IN_APP`) — covers `WEBHOOK`/`PUSH` today and any future category/channel alike, folded together with Finding #3's own identical resolution. Matches `agents.md`'s "never send on every channel, never fail" — an unrecognized pair is suppressed, not defaulted open. |
| 2 | Case-sensitivity of `category`/`channel` unspecified | Medium | **ACCEPTED** | `PreferenceResolver` normalizes both arguments to uppercase (`String.toUpperCase(Locale.ROOT)`) before querying the repository and before the default-table lookup — matches the DB `CHECK` constraint's own uppercase value space, more forgiving than requiring exact-case callers. |
| 3 | Unknown categories/channels unspecified | Medium | **ACCEPTED** | Same resolution as Finding #1 — `false` for anything outside the 6 named pairs, evaluated after uppercase normalization. |
| 4 | `ChannelPreferenceRepository extends JpaRepository` exposes write methods | Medium | **ACCEPTED (documented, not narrowed)** | Kept `extends JpaRepository`, consistent with T04's `ProcessedEventRepository` and T05's own identical Kimi finding (Phase 8 Finding #2) resolution — a prominent Javadoc warning is added instead, naming `findByAccountUuidAndCategoryAndChannel` as the only sanctioned call and explaining the inherited mutators are unused, not a valid write path. Narrowing the interface would break consistency with the module's own established precedent for no strong offsetting benefit — the `V6` grant (`SELECT` only) already makes any accidental `save`/`delete` call fail at the DB layer regardless. |
| 5 | `NotificationBaselineMigrationIntegrationTest` not named in "Files to Modify" | Medium | **ACCEPTED** | Added to Files to Modify below — Flyway-history expectation widens to `"1".."6"`; a new dedicated test proves `notification_app` can `SELECT` but not `INSERT`/`UPDATE`/`DELETE` on `channel_preferences`, mirroring T05's own identical precedent for `contact_projection`. |
| 6 | Null handling for `category`/`channel` arguments unspecified | Low | **ACCEPTED** | `PreferenceResolver.resolve` requires non-null `accountUuid`/`category`/`channel` — a caller contract (`@Nonnull`-documented), not defensive code; a `null` argument is a caller bug, not a case this method silently tolerates. |
| 7 | Enum vs. string trade-off for `category`/`channel` not discussed | Low | **ACCEPTED (informational)** | Trade-off noted for Phase 5: enums give compile-time safety on the default-table lookup but require a Java-side update whenever the DB `CHECK` constraint's own value space changes; strings match `notificationKind`'s own precedent (T06) and need no Java change to track a future DB-only category addition. No decision forced here — Phase 5's own call, informed by this note. |
| 8 | Default table quoted in lowercase prose vs. uppercase DB constants | Low | **ACCEPTED** | Every restatement of the default table in this and future artifacts uses the exact DB constant spelling (`EMAIL`, `IN_APP`, `SECURITY`, `PAYMENT`, `MARKETING`), not `design.md`'s own lowercase prose shorthand. |

## Task

Unchanged from Phase 2, with all 8 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `PreferenceResolver` returns `false` for `WEBHOOK`/`PUSH` and any other unrecognized
  `(category, channel)` pair (Findings #1/#3).
- Both `category` and `channel` arguments are uppercased before lookup (Finding #2).
- `ChannelPreferenceRepository`'s own Javadoc documents the inherited-mutators-unused warning
  (Finding #4).
- `resolve`'s own `null`-argument contract documented (Finding #6).
- A short Phase 5 design note on the enum-vs-string trade-off (Finding #7, informational only).

**Out:** Unchanged from Phase 2.

## Business Rules

Unchanged from Phase 2.

## Locked Decisions

Unchanged from Phase 2: L6, L2.

## Dependencies

Unchanged from Phase 2.

## Inputs

Unchanged from Phase 2.

## Outputs

Unchanged from Phase 2 (file names unchanged).

## Files to Create

Unchanged from Phase 2:
- `services/notification/src/main/java/com/themistra/notification/preference/ChannelPreference.java`
- `.../preference/ChannelPreferenceRepository.java`
- `.../preference/PreferenceResolver.java`
- `services/notification/src/main/resources/db/migration/V6__notification_app_channel_preferences_grant.sql`

## Files to Modify

- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (Finding #5) — Flyway-history expectation `"1".."6"`; new grant-proof test for `channel_preferences`
  (`SELECT` succeeds, `INSERT`/`UPDATE`/`DELETE` denied).
- `T01SkeletonRegressionTest.java` — not pre-authorized here (same reasoning as Phase 2: exact file
  list unknown until Phase 6 writes the files), but expected, per T04/T05/T06's own unbroken
  precedent, and will be disclosed exactly as those three tasks did if/when it recurs.

## Files NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 2's AC1-5, with AC3 now explicit about the fallback boundary, plus:
6. **AC6.** `PreferenceResolver.resolve` returns `false` for any `(category, channel)` pair not
   among the 6 named default pairs (Finding #1/#3), including `WEBHOOK` and `PUSH`.
7. **AC7.** `resolve("security", "email", ...)` (lowercase input) produces the same result as
   `resolve("SECURITY", "EMAIL", ...)` (Finding #2).

## Required Tests

Unchanged from Phase 2, plus: a test proving `WEBHOOK`/`PUSH` (and one made-up unknown pair) both
resolve `false` (Finding #1/#3); a case-insensitivity test (Finding #2); the new
`NotificationBaselineMigrationIntegrationTest` grant-proof test (Finding #5).

## Constraints

Unchanged from Phase 2, plus: `category`/`channel` normalized to uppercase before any lookup
(Finding #2); `null` `category`/`channel`/`accountUuid` is a caller contract violation, not a
handled case (Finding #6).

## Open Questions

No blockers. All 8 Phase 3 findings resolved above.
