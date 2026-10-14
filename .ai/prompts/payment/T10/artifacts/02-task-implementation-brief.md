# payment · T10 · Phase 2 — Task Implementation Brief

## Task

Add `VerificationRecord` (append-only) and record every transition, reversal, and ignored stale
event with the trigger event key (R11, R12, R14).

## Purpose

The durable, append-only audit trail for every payment-state-movement decision this service ever
makes — including the two that are easiest to get wrong (a reorg reversal, and a deliberately
ignored stale event) — so an auditor or a future debugging session can reconstruct exactly why a
payment is in its current state, without relying on log retention.

## Scope

**In:**
- `VerificationRecord` entity, mapping the real `verification_records` table exactly (`payment_id`
  as a plain `Long` — no `Payment` entity exists yet, task 11's own scope).
- `VerificationRecordRepository` (plain `JpaRepository`, no custom query needed this task).
- `VerificationRecordService`: `record(...)` (covers both plain forward transitions and
  reversals — mechanically identical, a state pair plus a trigger key) and `recordIgnored(...)`
  (a dedicated method for R14's own case, so "this was a no-op" is explicit at the call site
  rather than relying on callers to know the `fromState == toState` convention).
- Updating `ArchitectureTest`'s own `FEATURE_MODULES` list to include `verification` (closes the
  same category of gap T09 itself found empirically for `events` — called out proactively this
  time).

**Out:**
- `Payment`/`PaymentRepository`/`PaymentService` — task 11.
- `ChainEventConsumer`, `processed_events`, staleness *detection* — task 11.
- Any real call site that invokes `record(...)`/`recordIgnored(...)` from a live event — tasks 11
  (forward transitions, ignored events) and 12 (reversals). This task only builds what records
  the outcome; it is never itself the decision-maker about what happened.
- Any validation of the `(fromState, toState)` pair against `PaymentStateMachine`'s own legal-edge
  set — `VerificationRecordService` trusts its caller completely; it is a recorder, not a second
  enforcement point for L1.

## Business Rules

- R11 — recording half only: a `CONFIRMING → SEEN` reversal, with its trigger event key, is
  recorded.
- R12 — recording half only: a `SEEN → WATCHING` reversal, with its trigger event key, is
  recorded.
- R14 — recording half only: an ignored stale/out-of-order event, with its trigger event key, is
  recorded, without any state actually changing (`fromState == toState == currentState`).

## Locked Decisions

- **L1** — `fromState`/`toState` are `PaymentState` (T09), `@Enumerated(STRING)`.
- **L5** — `triggerEventKey` carries the deterministic `chain:txhash:eventtype` key; this task
  does not itself dedupe (that's `processed_events`, task 11).
- **L7** — append-only, mirroring `ledger_entries`'s own pattern: `payment_app` already has
  `INSERT`+`SELECT` only on `verification_records` (T02, no new migration); nothing in this
  task's own code ever calls a delete/update operation.
- **L11** — a reorg reversal is recorded as a normal row, not a special error case.
- **L12** — the new `verification` package imports no entity from any other feature package
  (`PaymentState` is an enum, permitted).

## Dependencies

- `PaymentState` (T09).
- `Clock` (existing `ClockConfig`, T04) — `occurredAt` set explicitly, mirroring `Invoice`'s own
  established pattern (a `@PrePersist` `Instant.now()` fallback mirrors `OutboxEvent`/`Invoice`'s
  own identical last-resort precedent, for the same reason: entities cannot have an injected
  `Clock`).
- Hibernate 6's native `@JdbcTypeCode(SqlTypes.JSON)` (`org.hibernate.annotations`/
  `org.hibernate.type.SqlTypes`) for the `detail` column, mapped as `Map<String, Object>` — no new
  dependency (Spring Boot 3.5.4 already manages a Hibernate 6.x version with this built in).

## Inputs

`record(Long paymentId, PaymentState fromState, PaymentState toState, String triggerEventKey,
Integer confirmations, Map<String, Object> detail)`; `recordIgnored(Long paymentId, PaymentState
currentState, String triggerEventKey, Map<String, Object> detail)`.

## Outputs

Both methods return the persisted `VerificationRecord` (mirrors `InvoiceService.create`'s own
"return what was saved" convention).

## State Changes

A new `verification_records` row per call. Never an update or delete of an existing row.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecord.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordRepository.java`
- `services/payment/src/main/java/com/themistra/payment/verification/VerificationRecordService.java`
- `services/payment/src/test/java/com/themistra/payment/verification/VerificationRecordServiceTest.java`

## Files to Modify

- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — add
  `"verification"` to `FEATURE_MODULES`.

## Files NOT to Modify

- `V1__payments_baseline.sql` / `V2__payment_app_role_and_grants.sql` (immutable; the existing
  grant already covers this task's own needs — no new migration).
- Any file under `spec/`.
- `invoice`/`payment`/`events` packages (read-only reference to `PaymentState` only).

## Acceptance Criteria

- **AC1 (R11, named)** — `record(paymentId, CONFIRMING, SEEN, triggerEventKey, null, null)`
  persists a row with those exact field values and a non-null `occurredAt`.
- **AC2 (R12, named)** — `record(paymentId, SEEN, WATCHING, triggerEventKey, null, null)`
  persists the equivalent row.
- **AC3 (R14, named)** — `recordIgnored(paymentId, currentState, triggerEventKey, detail)`
  persists a row with `fromState == toState == currentState` and the given `detail`.
- **AC4 (general "every transition")** — `record(...)` with an arbitrary legal-looking forward
  pair (e.g. `CREATED → WATCHING`) persists correctly too — this method makes no distinction
  between "forward" and "reversal," by design (Scope/Out).
- **AC5 (L7, corrected from Phase 1's own looser wording)** — no code in this task's own
  production classes calls `delete`/`deleteById`/`deleteAll` on `VerificationRecordRepository`
  (a structural "the repository interface declares no delete method" claim would be false —
  `JpaRepository` always inherits one; the real guarantee is behavioral, proven by a source-text
  check over this task's own two new production files).
- **AC6 (L12)** — `verification` is in `ArchitectureTest.FEATURE_MODULES`; all 3 existing
  cross-module-entity-import canaries still pass.
- **AC7 (defensive)** — `record(...)`/`recordIgnored(...)` throw `NullPointerException` for a
  null `paymentId` or a null `toState`/`currentState` (the two columns the real schema marks
  `NOT NULL`); `fromState`, `triggerEventKey`, `confirmations`, and `detail` are legitimately
  nullable per the real schema and must NOT be null-checked.

## Required Tests

- `shouldWalkBackwardConfirmingToSeenOnReorg` (named, R11).
- `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named, R12).
- `shouldIgnoreOutOfOrderOrStaleChainEvents` (named, R14).
- One forward-transition test (AC4, not individually named).
- A source-text test proving no `delete*` call exists in `VerificationRecordService.java` (AC5).
- `ArchitectureTest`'s own 3 existing canaries, re-run after `FEATURE_MODULES` gains
  `"verification"` (AC6) — no new canary needed; `VerificationRecord` itself only needs to be
  correctly module-mapped, not independently proven to violate anything.
- Null-argument tests for the two `NOT NULL` parameters on each method (AC7) — `fromState`/
  `triggerEventKey`/`confirmations`/`detail` get no null test (they're legitimately nullable).

## Constraints

- **Transactions**: `record`/`recordIgnored` are `@Transactional` (Spring's default `REQUIRED`
  propagation) — standalone-testable now, and correctly joins a wider caller transaction once
  task 11/12 wire this in as one step of a larger unit of work (L5's own "inside the same
  transaction that applies the effect").
- **Module boundaries (L12)**: `verification` package imports only `PaymentState` across feature
  boundaries, never an entity.
- **Null handling (AC7)**: only `paymentId` and `toState`/`currentState` are validated —
  `fromState`, `triggerEventKey`, `confirmations`, `detail` are legitimately nullable per the real
  schema; validating them as non-null would be a real, self-inflicted bug (rejecting valid input
  the schema explicitly allows).
- **No second enforcement of L1**: this service never calls or references
  `PaymentStateMachine`/`IllegalPaymentStateTransitionException` — it has no opinion on whether a
  given `(fromState, toState)` pair is a *legal* transition, only that it happened (per its
  caller) and must be recorded.
- **Append-only (L7)**: no `update`/`delete` call anywhere in this task's own production code.

## Open Questions

No blockers. The two real open items from Phase 0/1 (the entity/repository/service scope
boundary, and the "ignored" row shape) are resolved above as this brief's own concrete proposal.
Neither carries the same architectural weight T09's own `HELD`-edge question did (nothing else in
this codebase yet depends on parsing these rows programmatically), so Phase 3 is invited to
challenge but this is not flagged for a mandatory Phase 4 `AskUserQuestion` the way T09's was.
