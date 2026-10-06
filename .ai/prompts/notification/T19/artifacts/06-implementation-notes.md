# notification · T19 · Phase 6 — Implementation Notes

Implemented per the Phase 4 frozen brief and Phase 5 plan. One new test class, no production change.

## Files created

- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryLogDisputeGradeIntegrationTest.java`
  — four `@Test` methods, one per acceptance-criteria group.

## Files modified

None.

## Implementation facts confirmed while writing

- `ContactProjectionUpdater.upsertEmail(UUID, String, Instant)` is the public writer the chain test
  uses to put a real recipient on file. Without it the EMAIL channel fails with "no recipient email
  on file", which would not exercise the rendered path.
- `processOne` replays a real `delivery_retry` row and deletes it on a terminal outcome, so every
  assertion reads `delivery_log` only.
- The shared Kafka broker is not needed by this class (it dispatches directly). It is needed by the
  full suite, so it was started for the full run.

## Deliberate failures: each assertion shown live

Per Phase 5's execution order, each method was broken on one expectation, run in isolation, shown to
fail for the stated reason, and then reverted:

| Method | Broken expectation | Result |
|---|---|---|
| `deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole` | SQLState `42501` → `00000` | Failed at line 214, the SQLState assertion |
| `retryChainAppendsMultipleRowsForTheSameSourceEventKey` | outcomes `FAILED, SENT` → `FAILED, FAILED` | Failed at line 262 |
| `suppressedChannelLeavesASuppressedDeliveryLogRow` | outcome `SUPPRESSED` → `SENT` | Failed at line 286 |
| `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` | planted a temporary offender in `src/main` (deleted afterwards) | Failed listing `TmpAc5MutationProbe.java` as the offender |

All four were reverted, and the reverted values were checked with `grep` before the full run.

## Verification

- `mvn -pl services/notification test -Dtest=DeliveryLogDisputeGradeIntegrationTest` — 4/4 pass
  (10.5s), before and after the deliberate-failure cycle.
- `mvn -pl services/notification clean verify` — 373 tests, 0 failures, 0 errors, exit 0 (369 prior
  plus the 4 new).

## Mapping to acceptance criteria

- **AC1**: `deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole` runs real UPDATE and DELETE as
  `notification_app` and asserts PostgreSQL SQLState `42501`. The row is unchanged afterwards.
- **AC2**: `retryChainAppendsMultipleRowsForTheSameSourceEventKey` produces a `FAILED` and a `SENT`
  EMAIL row for one source key, with distinct attempt numbers, the same recipient, and timestamps.
- **AC3**: the same test asserts `template_version` is non-null on those rendered rows. Non-rendered
  rows are out of this assertion's scope, as disclosed in Phase 1.
- **AC4**: `suppressedChannelLeavesASuppressedDeliveryLogRow` asserts a `SUPPRESSED` EMAIL row, an
  independent IN_APP row, and no email actually sent.
- **AC5**: `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` scans `src/main/java` for raw
  `UPDATE`/`DELETE` against `delivery_log` and for delete calls on `deliveryLogRepository`. It runs
  clean on the real code and fails on a planted violation.
