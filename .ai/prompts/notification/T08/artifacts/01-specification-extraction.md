# notification · T08 · Phase 1 — Specification Extraction

## Business Rules

- **R9.** WHEN determining how to reach a recipient for a given event type, THEN the system SHALL
  resolve that recipient's channel preferences and deliver only on the enabled channels.
- **R10.** IF a recipient has opted out of a channel for a given notification category, THEN the
  system SHALL suppress delivery on that channel and record the suppression in the delivery log
  (the *recording* half is `DeliveryOrchestrator`'s own job, task 11 — this task only needs to
  return a resolvable, correct enabled/disabled signal for the orchestrator to act on and log).
- **R18.** IF a recipient has no stored preference for an event category, THEN the system SHALL
  apply the documented default preference (per §4c) rather than failing or sending on all channels.

## Locked Decisions

- **L6.** Preference resolution with a safe default — "Delivery honours the recipient's per-category
  channel preferences and opt-outs; absent a preference, a documented default applies — never 'send
  on every channel' and never 'fail'." This is the task's own primary locked decision; the default
  table itself (§4c, quoted in Phase 0) is VERBATIM and must be copied exactly.
- **L2.** Consume-only / no synchronous cross-service call — `PreferenceResolver` only ever reads
  `channel_preferences`, a local table; never calls another service.

## Files involved

**Already exists, read-only precedent/dependency:**
- `channel_preferences` table (`V1__notifications_baseline.sql`, T02) — migrated since T02, never
  granted to `notification_app` (Phase 0's own finding).
- `preference/{ContactProjection,ContactProjectionRepository,ContactProjectionUpdater}.java` (T05)
  — structural precedent for a small `@Service` wrapping a package-private repository in the same
  `preference/` package this task also adds to.
- `common/ClockConfig.java` (T04) — not obviously needed here (preference resolution has no
  timestamp concern the way idempotency/upsert-ordering do), but available if a future need arises.

**New, this task's own deliverable (per `design.md` §6):**
- `preference/ChannelPreference.java` — entity mapping onto `channel_preferences`'s existing 5
  columns (`id`, `account_uuid`, `category`, `channel`, `enabled`, `updated_at` — Phase 0 undercounted
  at 4; re-verified against the real DDL: it's `id`, `account_uuid`, `category`, `channel`,
  `enabled`, `updated_at`, 6 columns).
- `preference/ChannelPreferenceRepository.java` — read path only; no write API exists anywhere in
  this spec (Phase 0's own finding), so this is very likely a plain `findBy` query, not an upsert
  like `ContactProjectionRepository`.
- `preference/PreferenceResolver.java` — the public entry point `DeliveryOrchestrator` (task 11)
  will call once it exists.
- A grant migration, `V6__notification_app_channel_preferences_grant.sql` — `SELECT` only (no
  `INSERT`/`UPDATE`/`DELETE`, since nothing in this codebase ever writes to this table).

## Dependencies

`spring-boot-starter-data-jpa` (present), `notification_app`'s DB role + the new `V6` grant. No
`Clock`, no Kafka — this task is pure read-and-resolve logic, callable directly with plain
arguments (`accountUuid`, `category`, `channel`), not event-driven.

## Acceptance Criteria

1. **AC1.** `ChannelPreference` maps exactly onto the 6 existing `channel_preferences` columns.
2. **AC2.** `PreferenceResolver` returns the *stored* `enabled` value when a row exists for the
   given `(accountUuid, category, channel)`.
3. **AC3.** `PreferenceResolver` returns the documented default (§4c's own table) when no row
   exists — never throws, never defaults to "send everywhere."
4. **AC4.** `SECURITY` category + `EMAIL` channel resolves to enabled regardless of any stored row
   — the "email cannot be disabled" floor (exact enforcement mechanism is Phase 1's own open
   question below, resolved at Phase 2).
5. **AC5.** `V6` grants `notification_app` `SELECT` only on `channel_preferences`; `INSERT`/
   `UPDATE`/`DELETE` all still denied.

## Tests required

Named (`package.md` §8): `shouldResolveChannelPreferencesPerRecipient` (R9),
`shouldSuppressChannelWhenRecipientOptedOut` (R10), `shouldFallBackToDefaultPreferenceWhenNoneSet`
(R18). Plus, not separately named but implied by AC4: a test proving `SECURITY`+`EMAIL` cannot be
suppressed even when a stored row says otherwise.

## Open Questions

1. **Not a blocker, but needs a concrete decision before Phase 2 freezes.** How is "SECURITY email
   cannot be disabled" (§4c's own parenthetical) actually enforced, given no write API exists
   anywhere in this spec to ever create a `SECURITY`+`EMAIL`+`enabled=false` row in the first
   place? Two options, both behaviorally identical today, differently defensive in code:
   - **(a)** `PreferenceResolver` special-cases `SECURITY`+`EMAIL` and returns `true`
     unconditionally, never even querying the repository for that specific pair. Matches L6's "never
     fail" framing most directly; needs no assumption about how such a row could exist.
   - **(b)** `PreferenceResolver` queries as normal, but the floor is enforced by simply never
     providing a way to write `enabled=false` for that pair — meaning it's only a floor by omission,
     not an active guarantee the resolver itself enforces.
   Phase 0 already recommended (a); carried forward here as the Phase 2 default unless the design
   challenge (Phase 3, Kimi) surfaces a reason to prefer (b).
