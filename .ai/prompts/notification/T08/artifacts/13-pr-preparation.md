# notification · T08 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T08: preference resolver (ChannelPreference, PreferenceResolver)`

## Commit message

```
notification-service T08: preference resolver

Add ChannelPreference (maps onto T02's already-migrated channel_preferences
table, read-only - no write API exists anywhere in this spec through T20)
and ChannelPreferenceRepository, with one derived query,
findByAccountUuidAndCategoryAndChannel. Add PreferenceResolver: given
(accountUuid, category, channel), returns whether that channel is
enabled for that recipient/category. Logic order: SECURITY+EMAIL is a
hard floor (returns true unconditionally, never queries the repository -
the strongest form of "email cannot be disabled", design.md §4c's own
parenthetical); category/channel are uppercased before every lookup
(matches the DB's own CHECK-constrained value space); a stored row takes
precedence when one exists; otherwise the documented default (§4c's own
VERBATIM table) applies; any (category, channel) pair outside the 6
named defaults - including the DB-permitted but undefaulted WEBHOOK/PUSH
channels - resolves false, never "send everywhere."

Add V6__notification_app_channel_preferences_grant.sql - SELECT only,
the first grant in this module that isn't INSERT-capable, since nothing
in this codebase ever writes to this table.

ChannelPreferenceRepository keeps extends JpaRepository (T04/T05's own
established precedent) despite exposing unused inherited mutators -
documented via Javadoc, not narrowed, mirroring T05's own identical
Kimi finding; the SELECT-only grant is the real defense-in-depth layer.

Updates NotificationBaselineMigrationIntegrationTest per the frozen
brief's own explicit authorization: channel_preferences moves from
UNGRANTED_TABLES to a dedicated grant-proof test (the first in this
module where the fixture row must be admin-inserted, since notification_app
itself can't INSERT); Flyway-history expectation widens to "1".."6".
Updates T01SkeletonRegressionTest per an undisclosed-in-brief but
required deviation (same recurring gap T04/T05/T06 each hit): 3 new
production files, 20-file authorized list becomes 23. Adds a
PreferenceResolver bean-resolution proof to IdempotencyGuardIntegrationTest
(the one test class whose Spring context never overrides a seam bean).

148 tests total (128 T01-T06 unaffected + 20 new: 8 unit, 8 real-Postgres
integration, plus 4 folded into the count above from Phase 11's own
addendum). Two Kimi review rounds (Phase 8: 8 findings; Phase 11: 7
gaps) both fully resolved - all 15 verified against real source before
disposition, none false. Two properties independently empirically
verified via scratch tests (both since made permanent): the hard floor
holds against a real adversarial stored row, not merely the "no row"
case; case normalization actually reaches a real stored uppercase row
from a lowercase query argument. One real mutation test performed and
reverted clean: removed the SECURITY+EMAIL early return entirely,
confirmed exactly 2 tests (1 unit, 1 integration) fail - no more, no
fewer.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/preference/ChannelPreference.java`
- `services/notification/src/main/java/com/themistra/notification/preference/ChannelPreferenceRepository.java`
- `services/notification/src/main/java/com/themistra/notification/preference/PreferenceResolver.java`
- `services/notification/src/main/resources/db/migration/V6__notification_app_channel_preferences_grant.sql`
- `services/notification/src/test/java/com/themistra/notification/preference/PreferenceResolverTest.java`
- `services/notification/src/test/java/com/themistra/notification/preference/PreferenceResolverIntegrationTest.java`

**Modified**
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (23-file authorized list, renamed method)
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (`channel_preferences` grant-proof coverage; Flyway-history widened to `"1".."6"`)
- `services/notification/src/test/java/com/themistra/notification/consumer/IdempotencyGuardIntegrationTest.java`
  (`PreferenceResolver` bean-resolution proof)

**Process artifacts**
- `.ai/prompts/notification/T08/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Delivers the first piece of the actual delivery-decision logic: whether a given recipient should
be notified on a given channel for a given category, honouring stored opt-outs, a documented
default, and a hard floor that security email can never be silenced. `DeliveryOrchestrator` (task
11) will be this component's own first real caller — none exists yet, by this task's own explicit
scope boundary.

## Testing performed

- `mvn -pl services/notification clean verify` — 148 tests, 0 failures, `BUILD SUCCESS`.
- Real end-to-end verification against a live Testcontainers Postgres, first as an uncommitted
  Phase 7 scratch check, then made permanent in Phase 10/11: `PreferenceResolverIntegrationTest`
  (8 tests) proves stored-row precedence, default fallback, the hard floor against a real
  adversarial stored row, unsupported-channel/unknown-category fallback, a stored row winning on
  an unsupported channel, case normalization against a real stored row, and the entity's own column
  mapping directly.
- One real mutation test, reverted clean (`git status -s` empty afterward): removed the
  `SECURITY`+`EMAIL` early return entirely from `PreferenceResolver.resolve`, confirmed exactly the
  2 tests that should catch it (1 unit, 1 integration) failed — no more, no fewer — then reverted
  and re-verified.
- `git status -s services/auth services/crypto services/payment` — empty throughout this task's own
  commits; no sibling service touched (unlike T05/T06, both of which required one).

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 8 ("Preference resolver").
- **Requirements:** R9, R10, R18.
- **LOCKED decisions:** L6 (preference resolution with a safe default), L2 (consume-only, no
  synchronous cross-service call).

## Known, deliberate gaps (not this task's scope)

- **No real caller exists yet** — `DeliveryOrchestrator` (task 11) is the only intended caller;
  `PreferenceResolver`'s correctness through a real request path is unverified by construction,
  since no such path exists in this codebase yet (same "seam built ahead of its caller" shape as
  T06's own `NotificationDispatcher`).
- **`category`/`channel` remain plain `String`, not typed enums** (Kimi Phase 3 Finding #7,
  explicitly left to Phase 5 as an informational trade-off, not forced) — a caller typo would
  silently resolve via the `false` unknown-pair fallback rather than fail loudly. Acceptable under
  L6 ("never fail"), worth revisiting once task 11's own calling convention is known.
- **Whitespace-padded input is not trimmed** (Kimi Phase 11 Gap #4, documented not fixed) — same
  category of risk as the enum question above. No real caller exists yet to produce this bug.
- `WEBHOOK`/`PUSH` channels have no documented default in `design.md` §4c — `PreferenceResolver`'s
  own `false`-for-unsupported-channel fallback is forward-compatible (a stored row still wins), so
  no rework is anticipated; only the `DEFAULTS` map would need new entries if/when `design.md` is
  updated.
- `ChannelPreferenceRepository` still technically exposes inherited `JpaRepository` mutators
  (`save`/`delete`) that nothing in this codebase calls — documented with a prominent Javadoc
  warning, not structurally prevented (same precedent as T05's `ContactProjectionRepository`); the
  `V6` `SELECT`-only grant is the real defense-in-depth layer.

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 8, 8, and 7
  findings/gaps respectively — all 23 verified against actual source before disposition; all were
  real (no false findings this task).
- `ChannelPreferenceRepository`'s `extends JpaRepository` write-surface concern was raised
  independently by Kimi at both Phase 3 (Finding #4) and Phase 8 (Finding #8) — same disposition
  both times (documented via Javadoc, not narrowed), consistent with T05's own identical precedent
  for `ContactProjectionRepository`.
- The two empirically-verified properties (Phase 7's own scratch test, later made permanent) are
  worth a reviewer's specific attention: they prove the task's own most adversarial claims — the
  hard floor surviving a real stored opt-out row, and case normalization surviving a real
  uppercase-vs-lowercase round trip — not just the "happy path, no row exists" cases that are easy
  to get right by construction.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T08.**
