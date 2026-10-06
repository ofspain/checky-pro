# notification · T19 · Phase 2 — Task Implementation Brief

## Task

Prove the dispute-grade property of the delivery log against the real database and the real delivery
chain: rows are append-only at the database level, a retry chain for one source event is fully
reconstructable by its source event key, and suppression leaves a record. One new integration test
class, no production change.

## Purpose

"Was the merchant notified?" is answered only from `delivery_log`. If a row could be rewritten, or
a retry chain could not be recovered by its key, the evidence would not hold in a dispute. This
task turns the static picture from Phase 0 into a live, executed guarantee.

## Scope

**In:**
- One new integration test class in `com.themistra.notification.delivery`, using Testcontainers
  Postgres, Flyway, and the same datasource and password setup as `DeliveryOrchestratorIntegrationTest`.
- Proving AC1 (rejected UPDATE and DELETE as `notification_app`), AC2 (a transient-then-success retry
  chain sharing one `source_event_key`, each row with recipient, channel, outcome, timestamp), AC3
  (template version present on rendered rows), AC4 (a SUPPRESSED row for an opted-out channel), and
  AC5 (no application mutation path, backed by AC1).

**Out:**
- Any production code change. Phase 0 found no mutation path to fix, so none is needed.
- Re-testing the retry state machine, backoff, or dead-lettering. Those belong to T14 and already
  have tests. This task needs one successful retry chain as evidence, not an exhaustive retry suite.
- Rows with no template version (AC3 is scoped to rendered rows, per Phase 1).

## Business Rules / Locked Decisions

R10, R11, R12, R13 and L3, L9 (verbatim in Phase 1).

## Dependencies

None new. Testcontainers Postgres, Flyway, AssertJ, Awaitility. No new Maven dependency.

## Inputs / Outputs

Test-time only. Output is a passing test class; the output is a verifiable fact, not a new artifact.

## State Changes

None in production. The test writes `delivery_log` rows in its own Testcontainers database.

## Files to Create

- `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryLogDisputeIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

Everything under `src/main`, every migration, every existing test, `spec/`, the auth/crypto Dockerfiles.

## How the retry chain is driven (grounded in existing code)

- A transient failure is injected the same way `DeliveryOrchestratorIntegrationTest` already does:
  a `ControllableEmailTransport` bean whose `failNextAttempts(n)` makes the next `n` sends throw
  `EmailDeliveryException`. The real `dispatch(...)` path classifies that as `TRANSIENT_FAILURE`
  (`DeliveryOrchestrator.java:226`) and schedules a retry.
- The retry is then replayed by calling `DeliveryOrchestrator.replay(...)` directly. It is
  package-private, and the test lives in the same package, so this is legal. Deliberately NOT
  driven through `RetryScheduler.sweep()`: T14 documented that `sweep()` is ShedLock-guarded and
  can be silently skipped inside the lock's minimum-hold window, which would make the test flaky.
  The background scheduler is already pushed to `999999` by the same property override the sibling
  test uses.

## Acceptance Criteria

Unchanged from Phase 1, AC1–AC5, with AC4 proven by a fresh SUPPRESSED row inside this class rather
than only by reference to `DeliveryOrchestratorIntegrationTest.shouldSuppressChannelWhenRecipientOptedOut`,
so the dispute-log claim does not depend on another class's setup.

## Required Tests

One class, `DeliveryLogDisputeIntegrationTest`, with one test method per AC: append-only UPDATE
rejected, append-only DELETE rejected, retry chain reconstructable by key, rendered rows carry a
template version, suppression recorded. Each test asserts real database state, not a mock.

## Constraints

- Direct SQL for the UPDATE/DELETE proof must run as `notification_app` (the role with the real
  grant), not the Testcontainers superuser, or the proof proves nothing.
- Assertions on the rejected statement must check the PostgreSQL permission error (SQLState
  `42501`), not just that some exception was thrown.
- No scheduler-driven timing in any assertion (see replay rationale above).

## Open Questions

No blockers. Phase 2 decisions are fixed above: `replay(...)` rather than `sweep()`, the existing
transient-failure hook, and a fresh SUPPRESSED row inside this class.
