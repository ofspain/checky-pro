# notification · T08 · Phase 2 — Task Implementation Brief

## Task

Add `ChannelPreference` + `ChannelPreferenceRepository` + `PreferenceResolver` — a small,
call-directly (not event-driven) module resolving whether a given recipient/category/channel
combination is enabled, honouring stored opt-outs and falling back to the documented default when
no row exists, with a hard floor that `SECURITY`+`EMAIL` is always enabled.

## Purpose

Builds the first piece of the actual delivery-decision logic — "should this notification go out on
this channel" — as an isolated, directly-callable unit `DeliveryOrchestrator` (task 11) will later
call as the first of its own four steps ("resolve prefs → render → dispatch → log").

## Scope

**In:**
- `preference/ChannelPreference.java` — `@Entity` mapping onto `channel_preferences`'s 6 columns
  (`id` generated PK, `accountUuid`, `category`, `channel`, `enabled`, `updatedAt`). Read-only in
  practice — this task never writes a row (no write API anywhere in this spec).
- `preference/ChannelPreferenceRepository.java` — a single read query:
  `findByAccountUuidAndCategoryAndChannel(UUID, String, String)` (or equivalent), returning
  `Optional<ChannelPreference>`. `extends JpaRepository` per this module's own established
  precedent (T04/T05), even though only the derived-query method is ever called.
- `preference/PreferenceResolver.java` — the public entry point: given `(accountUuid, category,
  channel)`, returns a `boolean`. Logic: `SECURITY`+`EMAIL` → always `true` (Phase 1's own resolved
  open question, option (a) — unconditional, never even queries the repository for that specific
  pair); otherwise, if a stored row exists, return its `enabled` value; otherwise, return the
  documented default (§4c's own table, copied exactly — `SECURITY`/`PAYMENT` default `true` for
  both `EMAIL`/`IN_APP`, `MARKETING` defaults `false` for both).
- `db/migration/V6__notification_app_channel_preferences_grant.sql` — `SELECT` only.

**Out:**
- Any write path for `channel_preferences` — no `PreferenceController`/API exists anywhere in this
  spec; this task does not invent one.
- `DeliveryOrchestrator` (task 11) — the only caller `PreferenceResolver` will ever have; not wired
  up here, since it doesn't exist yet.
- `category`/`channel` as typed enums vs. plain strings — the existing DB `CHECK` constraints
  already close the value space at the DB layer; Phase 5's own job to decide whether the Java side
  mirrors that with an `enum` (matching `AccountStatus`-style precedent elsewhere in this platform)
  or stays `String` (matching `notificationKind`'s own precedent in T06, a deliberately open string
  there for a different reason — not decided here).

## Business Rules

R9, R10, R18 (stated in full in Phase 1's own extraction) — this task proves the *resolution*
decision only; R10's own "record the suppression in the delivery log" half is `DeliveryOrchestrator`'s
job (task 11), not this task's.

## Locked Decisions

- **L6.** Preference resolution with a safe default — this task's own primary deliverable; the
  default table is VERBATIM, copied exactly, not re-derived.
- **L2.** Consume-only — `PreferenceResolver` only ever reads a local table.

## Dependencies

`spring-boot-starter-data-jpa` (present). No `Clock`, no Kafka, no cross-module call.

## Inputs

`channel_preferences` table (`V1__notifications_baseline.sql`, T02, already migrated, never
granted); `design.md` §4c's own default-preferences table (quoted in full in Phase 0/1).

## Outputs

`ChannelPreference.java`, `ChannelPreferenceRepository.java`, `PreferenceResolver.java`, `V6`.

## State Changes

None — this task is pure read logic; it introduces no new writer of any kind.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/preference/ChannelPreference.java`
- `.../preference/ChannelPreferenceRepository.java`
- `.../preference/PreferenceResolver.java`
- `services/notification/src/main/resources/db/migration/V6__notification_app_channel_preferences_grant.sql`

## Files to Modify

None expected. (Historical note: T04/T05/T06 each discovered a required, undisclosed-in-brief
`T01SkeletonRegressionTest.java` update once their own new production files existed — same class of
gap is expected to recur here and will be disclosed at Phase 6/9 exactly as those three tasks did,
not pre-authorized here since the exact file list can't be known until the files are written.)

## Files NOT to Modify

- `services/notification`'s own T01-T06 files (`pom.xml`, prior migrations, `ProcessedEvent*`,
  `ContactProjection*`, `AuthEventConsumer*`, `ClockConfig`, config records, `ResourceServerConfig`,
  `PublicEndpoints`).
- Every file under `spec/`.
- `services/auth`, `services/crypto`, `services/payment` — no cross-service dependency exists for
  this task (unlike T05/T06, both of which required one).

## Acceptance Criteria

1. **AC1.** `ChannelPreference` maps exactly onto the 6 existing `channel_preferences` columns.
2. **AC2.** `PreferenceResolver` returns the stored `enabled` value when a row exists for
   `(accountUuid, category, channel)` — for any `(category, channel)` pair other than
   `(SECURITY, EMAIL)`.
3. **AC3.** `PreferenceResolver` returns the documented default when no row exists, for every
   `(category, channel)` pair the default table names (`SECURITY`/`EMAIL`, `SECURITY`/`IN_APP`,
   `PAYMENT`/`EMAIL`, `PAYMENT`/`IN_APP`, `MARKETING`/`EMAIL`, `MARKETING`/`IN_APP`).
4. **AC4.** `PreferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")` returns `true`
   unconditionally — proven both when no row exists and (defensively) when a row exists with
   `enabled = false`.
5. **AC5.** `V6` grants `notification_app` `SELECT` only on `channel_preferences`; `INSERT`/
   `UPDATE`/`DELETE` all still denied.

## Required Tests

Named (`package.md` §8): `shouldResolveChannelPreferencesPerRecipient` (R9),
`shouldSuppressChannelWhenRecipientOptedOut` (R10), `shouldFallBackToDefaultPreferenceWhenNoneSet`
(R18). Plus, implied by AC4: a test proving `SECURITY`+`EMAIL` cannot be suppressed even when a
stored row explicitly says `enabled = false`.

## Constraints

- **Default table must be copied verbatim** from `design.md` §4c, not re-derived or approximated.
- **No write path introduced** — this task must not add any method, endpoint, or migration that
  writes to `channel_preferences`; that's explicitly out of this spec's own scope through T20.
- **Null handling:** `PreferenceResolver` must handle "no row" (an empty `Optional`) as a first-class
  case (the default-fallback path), not an error.

## Open Questions

No blockers. Phase 1's own open question (how "SECURITY email cannot be disabled" is enforced) is
resolved here as a concrete design choice (option (a): unconditional `true`, never querying the
repository for that specific pair) — carried forward from Phase 0/1's own recommendation, not
re-opened.
