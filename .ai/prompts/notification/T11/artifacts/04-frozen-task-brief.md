STATUS: FROZEN

# notification · T11 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 10 findings independently verified against source/design before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Transaction boundary for `dispatch` unspecified | High | **ACCEPTED (test corrected)** | `DeliveryOrchestrator.dispatch` is `@Transactional` (default `REQUIRED`) — joins `AuthEventConsumer`'s own already-open transaction when called from there. Kimi's own suggested test ("a failure during the second channel rolls back the first channel's row") assumes a scenario AC9 already forecloses — every per-channel failure is caught and converted to a `FAILED` row, never re-thrown, so no exception can ever reach `dispatch`'s own boundary from a channel/render failure. The real, achievable test instead: (a) reflection-proof `dispatch` is `@Transactional`; (b) an integration test proving that if the *external* caller's own transaction later rolls back (for an unrelated reason), the `delivery_log` rows `dispatch` wrote also roll back — mirroring `IdempotencyGuardIntegrationTest`'s own transaction-join precedent exactly. |
| 2 | `IN_APP`'s own `delivery_log.recipient` value unspecified | Medium | **ACCEPTED** | `EMAIL`: the resolved email address. `IN_APP`: the `accountUuid`'s own string form (there is no email recipient for this channel). |
| 3 | `findEmail` returning empty unspecified | Medium | **ACCEPTED** | `EMAIL` channel: a missing projection is a `FAILED` outcome with `errorDetail` explaining no recipient address on file — the channel is never called. `IN_APP`: proceeds normally (no email needed). |
| 4 | Failure-to-outcome mapping unspecified | Medium | **ACCEPTED** | Pinned decision table: channel enabled + `send` succeeds → `SENT`; channel disabled by preference → `SUPPRESSED`; render failure, missing-recipient (`EMAIL` only), missing channel bean, or `send` failure → `FAILED` with a redacted `errorDetail`; unknown `notificationKind` → no row at all (a true no-op, not even attempted per-channel — matches AC2's own "silent no-op" framing, not a failure of anything this task's own scope owns). |
| 5 | `SecretSafeLogging` usage location unspecified | Medium | **ACCEPTED** | `redact()` is applied to every `errorDetail` value before it is persisted to `delivery_log` and before any log statement that includes it. `eventData` is never logged as a whole anywhere in `DeliveryOrchestrator`. |
| 6 | `NotificationChannel.send`'s `email` parameter name is `EMAIL`-specific | Low | **ACCEPTED** | Renamed to `recipient`, documented as holding the email address for `EMAIL` and the account UUID's own string form for `IN_APP` — matches Finding #2's own resolution exactly. |
| 7 | Null template name per channel in the mapping table unspecified | Low | **ACCEPTED (defensive, not currently reachable)** | If a mapping's own template name for a given channel is `null`, that channel is skipped entirely for that `notificationKind` (no row at all for that channel) — defensive; every one of the 7 real mapping entries has both channel names populated today (V3's own seed always provides both), so this path is not currently reachable, only future-proofing. |
| 8 | `DeliveryLog`'s own construction pattern unspecified | Low | **ACCEPTED (clarified)** | A single public constructor taking every field except `id` (generated) and including an explicit `Instant createdAt` parameter — the caller (`DeliveryOrchestrator`) passes `clock.instant()`, matching T04's own `Clock`-injection precedent exactly. No factory method, no setters. |
| 9 | Exception vs. `Throwable` catch boundary unspecified | Low | **ACCEPTED** | Catches `Exception`, not `Throwable` — `Error` subclasses (e.g. `OutOfMemoryError`) are allowed to propagate, matching Kimi's own reasoning that swallowing genuine JVM-level errors would be worse than letting them surface. |
| 10 | Grant-migration note confirmed correct | Low | **No action** | Kimi's own concession — nothing to change. |

## Task

Unchanged from Phase 2, with all 10 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- `dispatch` is `@Transactional` (Finding #1).
- `NotificationChannel.send`'s second parameter is named `recipient`, not `email` (Finding #6).
- The pinned outcome decision table (Finding #4) governs every code path.
- `IN_APP`'s own `delivery_log.recipient` is the account UUID's own string form (Finding #2).
- A missing contact projection is `FAILED` for `EMAIL`, ignored for `IN_APP` (Finding #3).
- Every `errorDetail` is redacted before being logged or persisted (Finding #5).
- A `null` per-channel template name in the mapping table skips that channel entirely (Finding #7,
  defensive only).
- `dispatch` catches `Exception`, not `Throwable` (Finding #9).

**Out:** Unchanged from Phase 2.

## Business Rules

Unchanged from Phase 2.

## Locked Decisions

Unchanged from Phase 2: L3, L4, L5, L6, L9.

## Dependencies

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 1/2's own AC1-9, with AC7/AC9 now explicit per the pinned decision table and
catch boundary, plus:
10. **AC10.** `dispatch` is `@Transactional`; a `delivery_log` row written during `dispatch` rolls
    back if the external caller's own transaction later rolls back for an unrelated reason.

## Files to Create / Modify / Delete

Unchanged from Phase 2.

## Required Tests

Unchanged from Phase 2/1, plus: the corrected Finding #1 test pair (reflection-based
`@Transactional` proof + external-rollback integration test), the pinned outcome-decision-table
coverage (Finding #4 — one test per row), the `IN_APP` recipient-value test (Finding #2), the
missing-projection tests for both channels (Finding #3), and an `errorDetail`-redaction test
(Finding #5).

## Constraints

Unchanged from Phase 2, plus: `dispatch` catches `Exception`, never `Throwable` (Finding #9).

## Open Questions

No blockers. All 10 Phase 3 findings resolved above.
