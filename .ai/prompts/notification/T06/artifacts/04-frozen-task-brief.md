STATUS: FROZEN

# notification · T06 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 9 findings independently verified against source before disposition.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | Idempotency key format deferred to Phase 5 | High | **ACCEPTED** | Pinned now: `auth.email.requested` → `accountUuid + ":" + purpose + ":" + occurredAt`; `auth.user.lifecycle` → `accountUuid + ":" + eventType + ":" + occurredAt`. `occurredAt` is the deserialized `Instant`'s own `toString()` (ISO-8601 UTC, e.g. `2026-01-01T00:00:00Z`) — stable and byte-identical across redeliveries of the same payload. |
| 2 | Unknown `purpose` values on `auth.email.requested` unhandled | Medium | **ACCEPTED** | Unknown purposes are dedup-recorded (via `IdempotencyGuard`, so a redelivery is still recognized) but **not dispatched** — `NotificationDispatcher.dispatch` is only called for the two known purposes, matching how non-`user.registered` lifecycle events are already handled. `ContactProjectionUpdater.upsertEmail` still runs regardless (the projection refresh isn't purpose-specific). |
| 3 | Transaction boundary for the listener methods unspecified | Medium | **ACCEPTED** | Both `@KafkaListener` methods are `@Transactional` (default `REQUIRED`) — dedupe, projection-refresh, and dispatch run in one atomic transaction. If `dispatch` ever throws (once a real implementation lands in task 11/12), the whole transaction rolls back, including the idempotency record — so a failed dispatch is correctly retried on redelivery, not silently marked processed. |
| 4 | No-op dispatcher risks logging the raw token | Medium | **ACCEPTED** | `NoOpNotificationDispatcher` (renamed, Finding #9) logs only `accountUuid`, `notificationKind`, and `eventData.keySet()` — never `eventData`'s own values. A test asserts the literal token value never appears in captured log output. |
| 5 | `auto-offset-reset` value unchosen | Medium | **ACCEPTED** | `latest` — a newly deployed/rebalanced consumer group does not replay historical events. Rationale documented: replaying old verification/reset links would resend stale, likely-expired links; replaying old lifecycle events risks re-welcoming inactive accounts. Idempotency (`IdempotencyGuard`) makes replay *safe*, but `latest` avoids the *business* cost of mass-replay by default. |
| 6 | Hand-written DTOs conflict with `agents.md`'s "generated from contracts" rule | Medium | **ACCEPTED (disclosed deviation)** | Verified: no code-generation tooling exists anywhere in this repo for `contracts/events/*` — `auth-service`'s own producer-side payload records (`EmailRequestedEventPayload`, `UserLifecycleEventPayload`) are themselves hand-written, not generated, despite the same `agents.md`-class rule applying there too. Setting up real schema-to-Java codegen is out of this task's own scope. Resolution: hand-written DTOs are kept, explicitly disclosed as a deviation matching the repo's own already-established real practice (not a new violation this task introduces), and a contract test is now **required** (not optional) for each DTO, mirroring `auth-service`'s own `{EmailRequestedEventPayload,UserLifecycleEventPayload}ContractTest` pattern exactly — structural validation against the real schema file stands in for generation. |
| 7 | No error-handling strategy for deserialization/processing failures | Medium | **ACCEPTED** | Exceptions propagate — Spring Kafka's own default consumer error handling applies (retries with backoff, per-container default). Documented rationale: a malformed message should already have failed a contract test (Finding #6's own new test) before ever reaching a real environment; a dedicated dead-letter topic/handler is a genuine future gap, not named anywhere in `tasks.md`, flagged for later consideration but not this task's own scope to build. |
| 8 | Dispatcher doesn't carry `email`; future recipient lookup reads from the projection, not the event | Low | **ACCEPTED** | Documentation-only: `NotificationDispatcher`'s own Javadoc now states explicitly that the seam intentionally omits `email` — a future implementation resolves the recipient via `contact_projection`, and this consumer's own `upsertEmail` call (which runs before `dispatch`) ensures that projection is at least as fresh as this event allows. |
| 9 | `AuthEventConsumerNoOpDispatcher` name implies auth-specific scope | Low | **ACCEPTED** | Renamed to `NoOpNotificationDispatcher` — a generic, temporary implementation of the generic seam, not coupled to the auth-event consumer path specifically. |

## Task

Unchanged from Phase 2, with all 9 dispositions folded in.

## Scope

**In (unchanged from Phase 2, plus):**
- Idempotency key format pinned exactly (Finding #1).
- Unknown `purpose` values: dedup-recorded, projection-refreshed, **not dispatched** (Finding #2).
- Both `@KafkaListener` methods are `@Transactional` (Finding #3).
- `NoOpNotificationDispatcher` (renamed, Finding #9) logs only `accountUuid`/`notificationKind`/
  `eventData.keySet()` (Finding #4).
- `spring.kafka.consumer.auto-offset-reset=latest`, with its own rationale comment (Finding #5).
- A contract test per DTO (`EmailRequestedEventContractTest`, `UserLifecycleEventContractTest`)
  required, mirroring `auth-service`'s own precedent; hand-written-DTO deviation from `agents.md`'s
  own codegen rule explicitly disclosed in each DTO's own Javadoc (Finding #6).
- Deserialization/processing exceptions propagate; documented rationale (Finding #7).
- `NotificationDispatcher`'s own Javadoc documents why `email` is intentionally omitted (Finding #8).

**Out:** Unchanged from Phase 2.

## Business Rules

Unchanged from Phase 2.

## Locked Decisions

Unchanged from Phase 2: L1, L2, L5. Plus **L4** (no secrets/tokens in logs) — directly relevant now
via Finding #4's own resolution.

## Dependencies

Unchanged from Phase 2.

## Inputs

Unchanged from Phase 2, plus: `EmailRequestedEventPayloadContractTest.java`/
`UserLifecycleEventPayloadContractTest.java` (`services/auth`) as the direct structural precedent
for this task's own two new contract tests.

## Outputs

Unchanged from Phase 2 (file names updated per Finding #9's rename).

## State Changes

Unchanged from Phase 2.

## Files to Create

- `services/notification/src/main/java/com/themistra/notification/consumer/AuthEventConsumer.java`
- `.../consumer/NotificationDispatcher.java`
- `.../consumer/NoOpNotificationDispatcher.java` (renamed from `AuthEventConsumerNoOpDispatcher`)
- `.../consumer/dto/EmailRequestedEvent.java`
- `.../consumer/dto/UserLifecycleEvent.java`
- `services/notification/src/test/java/com/themistra/notification/consumer/dto/EmailRequestedEventContractTest.java`
- `.../consumer/dto/UserLifecycleEventContractTest.java`

(The two contract test files are named here, in Files to Create, even though this phase's own
guardrails otherwise treat tests as Phase 10's scope — mirroring T02's own precedent of naming
schema/contract-shape tests explicitly in the frozen brief when they encode a structural guarantee
the implementation phase itself must satisfy, not merely a later-phase nicety.)

## Files to Modify

- `services/notification/src/main/resources/application.properties` — Kafka consumer keys,
  including the now-pinned `auto-offset-reset=latest`.

## Files NOT to Modify

Unchanged from Phase 2.

## Acceptance Criteria

Unchanged from Phase 2's AC1-AC6, with AC4 now explicit that unknown `purpose` values are not
dispatched (Finding #2), plus:
7. **AC7.** The idempotency key format is exactly as pinned in Finding #1's own resolution.
8. **AC8.** Both `@KafkaListener` methods are `@Transactional`.
9. **AC9.** `NoOpNotificationDispatcher`'s own log output never contains a raw token value.

## Required Tests

Unchanged from Phase 2, plus: `EmailRequestedEventContractTest`/`UserLifecycleEventContractTest`
(Finding #6), a token-not-logged assertion (Finding #4), and an unknown-purpose-not-dispatched test
(Finding #2).

## Constraints

Unchanged from Phase 2, with the idempotency-key-format constraint now an Acceptance Criterion
(AC7) rather than an open Phase-5 decision.

## Open Questions

No blockers. All 9 Phase 3 findings resolved above.
