STATUS: FROZEN

# notification · T17 · Phase 4 — Frozen Task Brief

## Process note: Phase 2 (Task Implementation Brief) was skipped this time

Kimi's Phase 3 commit (`03-design-challenge.md`) was produced directly from Phase 1's extraction —
no separate `02-task-implementation-brief.md` exists for this task. Its own content effectively
combines what Phase 2 and Phase 3 would normally produce separately (concrete file/package/naming
decisions alongside the adversarial challenge). Rather than manufacture a redundant Phase 2 document
that would only restate Phase 3's own already-recorded decisions, this frozen brief proceeds
directly from Phase 1 + Phase 3, disclosing the skip here rather than silently absorbing it.

## Phase 3 findings — dispositions

All 9 findings verified directly against actual source before disposition — every factual claim
(repository visibility, the `NotificationMapping` record's real two-template shape, the existing
`scheduler-interval-seconds`/`clearFakeEmailTransport`/Awaitility patterns already in production
test code) was independently re-confirmed via direct `grep`/file reads, not taken on word. All 9 are
**ACCEPTED**; none rejected.

| # | Finding | Severity | Disposition | Resolution |
|---|---|---|---|---|
| 1 | The new test must not repeat `AuthEventConsumerIntegrationTest`'s spy-dispatch pattern | — | **ACCEPTED** | Confirmed directly: that class's `SpyDispatcherConfig` (`:54-64`) replaces the real `NotificationDispatcher` (`DeliveryOrchestrator`) bean — exactly the gap Phase 1 already identified. The new test imports no spy configuration. |
| 2 | Test package choice is constrained by package-private repositories | — | **ACCEPTED** | Verified directly: both `ProcessedEventRepository` and `DeliveryLogRepository` are package-private interfaces. Test placed in `com.themistra.notification.delivery` — `DeliveryLogRepository` injectable directly; `processed_events` queried via JDBC, mirroring `AuthEventConsumerIntegrationTest`'s own identical JDBC-for-contact-projection pattern. |
| 3 | The test must use its own Kafka consumer group ID | Low | **ACCEPTED** | `verify-email-redelivery-it`, distinct from `AuthEventConsumerIntegrationTest`'s own `auth-event-consumer-it` and `AuthEventConsumerTransactionRollbackIntegrationTest`'s own group. |
| 4 | `verify_email` produces both an EMAIL and an IN_APP delivery row | Medium | **ACCEPTED — corrects Phase 1's own under-specified AC3** | Verified directly: `DeliveryOrchestrator.java:98`, `NotificationMapping("email.verify", "user.verify", "SECURITY")` — a real two-template mapping. AC3 (below) is revised from "a `delivery_log` row" (singular, as Phase 1 wrote it) to "two rows, one per channel, both `SENT`." |
| 5 | The shared `FakeEmailTransport` singleton must be cleared per test | — | **ACCEPTED** | Mirrors `DeliveryOrchestratorIntegrationTest`'s own real `@BeforeEach clearFakeEmailTransport()` (`:192-193`), confirmed by direct read. |
| 6 | Redelivery timing must be waited, not assumed | — | **ACCEPTED** | Mirrors `AuthEventConsumerIntegrationTest`'s own real `await().pollDelay(...).atMost(...)` pattern (`:141`), confirmed by direct read. |
| 7 | The background retry scheduler should be pushed far out | Low | **ACCEPTED** | Mirrors `DeliveryOrchestratorIntegrationTest`'s own real `scheduler-interval-seconds` → `999999` override (`:130`), confirmed by direct read. |
| 8 | No new dependencies, no production code changes | — | **ACCEPTED** | Confirmed — `testcontainers`/`spring-kafka`/`awaitility` already present since T01/T06/T11; no production file is touched by this task's own scope. |
| 9 | The `payments.receipt.issued` scenario is explicitly out of scope | — | **ACCEPTED** | Matches Phase 0's own user decision exactly; documented in the new test's own Javadoc and restated at Phase 12. |

## Task

Unchanged from Phase 1, scoped per Phase 0's user decision: build one new, real end-to-end
integration test proving `auth.email.requested(verify_email)` → exactly one captured email +
real `delivery_log` rows, through the real, unspied production wiring, with redelivery producing
no additional rows. The `payments.receipt.issued` scenario remains explicitly deferred.

## Scope

Unchanged from Phase 1, with Finding #4's two-row correction folded into AC3.

## Business Rules

R1, R7, R8 (unchanged from Phase 1).

## Locked Decisions

L1, L3 (unchanged from Phase 1).

## Dependencies

None new (Finding #8).

## Files to Create

- `src/test/java/com/themistra/notification/delivery/VerifyEmailRedeliveryIntegrationTest.java`
  (Finding #2's package choice; Decision #1's exact name).

## Files to Modify

None (Finding #8).

## Files NOT to Modify

`AuthEventConsumer`, `DeliveryOrchestrator`, `FakeEmailTransport`, `IdempotencyGuard`, any
repository, any template row, any configuration file.

## Acceptance Criteria

1. **AC1** (R1/L1). A real `auth.email.requested` event with purpose `verify_email`, produced to
   the real Kafka broker under this test's own group ID (`verify-email-redelivery-it`), results in
   exactly one captured `EmailMessage` in `FakeEmailTransport`, through the real, unspied
   production wiring.
2. **AC2** (R7/R8). Redelivering the identical event (same key) produces no second captured email
   and no additional `delivery_log` rows — proven against real state, waited on with `Awaitility`,
   not asserted immediately.
3. **AC3** (L3, **revised by Finding #4**). Two real `delivery_log` rows exist for the account after
   the first delivery — one `EMAIL`/`SENT`, one `IN_APP`/`SENT` — both unchanged in count after
   redelivery.
4. **AC4** (Finding #9 / disclosure). The test's own Javadoc states explicitly that only
   `auth.email.requested(verify_email)` is covered; `payments.receipt.issued` is out of scope per
   Phase 0's own user decision.

## Required Tests

One new class, `VerifyEmailRedeliveryIntegrationTest`, proving AC1-AC3 together in one real
Kafka-to-capture run plus its own redelivery step. No change to any existing test.

## Constraints

- No spy/test `NotificationDispatcher` configuration (Finding #1).
- `FakeEmailTransport` cleared via `@BeforeEach` (Finding #5).
- `themistra.notification.retry.scheduler-interval-seconds` set to `999999` via
  `@DynamicPropertySource` (Finding #7).
- Own, unique Kafka consumer group ID (Finding #3).

## Open Questions

No blockers. All 9 Phase 3 findings resolved above, every one ACCEPTED. Finding #4 revised AC3's own
wording to match a real production behavior Phase 1 had under-specified — caught before any code
was written, not after.
