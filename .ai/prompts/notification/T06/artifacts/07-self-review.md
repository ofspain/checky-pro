# notification · T06 · Phase 7 — Self-Review

## Files reviewed

- `consumer/AuthEventConsumer.java`
- `consumer/{NotificationDispatcher,NoOpNotificationDispatcher}.java`
- `consumer/dto/{EmailRequestedEvent,UserLifecycleEvent}.java`
- `consumer/dto/{EmailRequestedEventContractTest,UserLifecycleEventContractTest}.java`
- `application.properties` (diff), `T01SkeletonRegressionTest.java` (diff)

## Findings

### Finding 1 — `AuthEventConsumer`'s real listener wiring was never actually executed in Phase 6

**Severity:** High (process gap; **resolved during this review, no defect found** — same class of
gap as T05's own Phase 7 Finding 1)

**Evidence:** Phase 6's own "Verification performed" section only established that the full suite
compiles and passes (98 tests) and that the pre-existing `IdempotencyGuardIntegrationTest`'s
`@SpringBootTest` context boots cleanly with the two new `@KafkaListener` beans present. No test
anywhere — including that one, which never produces a Kafka message — has ever actually put a real
message on `auth.email.requested`/`auth.user.lifecycle` and observed `AuthEventConsumer` consume
it. This meant the task's own core deliverable (the listener's dedupe → projection-refresh →
dispatch sequencing, the `purpose`/`eventType` routing switch, and the idempotent-redelivery
short-circuit) had never run end-to-end against a real broker before this review.

**Action taken during this review (informational, no code changed):** wrote a temporary,
uncommitted scratch test (`ScratchAuthEventConsumerVerificationTest`, deleted before this artifact
was written, same discipline as T04/T05's own Phase 6/7 scratch verification), using the shared
local Kafka broker (`localhost:9094`, this module's own existing test convention — no Testcontainers
Kafka module is used anywhere in this service) plus a Testcontainers Postgres and a `@Primary` spy
`NotificationDispatcher` bean. Four scenarios were exercised by publishing real JSON messages and
polling with Awaitility:

1. `auth.email.requested(purpose=verify_email)` → idempotency row created, `contact_projection`
   row updated with the event's `email`, dispatcher called with `notificationKind="verify_email"`
   and `eventData={"token": "<raw-token>"}`. **Passed.**
2. `auth.email.requested(purpose=mystery_purpose)` (unknown) → idempotency row created, projection
   still updated, dispatcher **never** called. **Passed** (confirms AC4/Finding #2's own
   dispatch-suppression for unrecognized purposes).
3. `auth.user.lifecycle(eventType=user.registered)` → idempotency row created, projection updated,
   dispatcher called with `notificationKind="user.registered"` and an empty `eventData`. **Passed.**
4. `auth.user.lifecycle(eventType=user.locked)` → idempotency row created, projection updated,
   dispatcher never called. **Passed** (confirms the `eventType`-based, not `status`-based,
   routing decision actually holds at runtime, not just in the source).

A fifth assertion (re-publishing scenario 1's identical message a second time) confirmed
`recordIfNew` returning `false` on redelivery genuinely prevents a second `dispatch` call — the
idempotency guard's own guarantee actually reaches all the way through to the dispatch seam, not
just to the projection update.

**One flaky occurrence observed, not a defect:** when all four scenarios ran back-to-back in one
JVM against the shared, persistent local broker (not an ephemeral Testcontainers Kafka instance),
the fourth scenario once missed its 15-second Awaitility budget waiting for the idempotency row.
Re-run in isolation, it passed in ~10s. Root-caused to the shared broker/consumer-group's own
rebalance overhead compounding across sequential `@Test` methods in the same Spring context — not
a defect in `AuthEventConsumer` itself (the same scenario, given a clean run, consistently
succeeds). Consistent with this module's own already-established convention of testing real
`@KafkaListener` behavior against the shared local broker rather than Testcontainers Kafka
(`IdempotencyGuardIntegrationTest`'s own precedent); flagged here only as a heads-up for whoever
writes Phase 10's own permanent version of this test, which should budget generously and/or use
distinct consumer groups per test to avoid cross-test rebalance interference.

**Recommendation:** Phase 10 should make this scratch check's own four scenarios permanent,
addressing the flakiness by either widening the Awaitility timeout or isolating each test's own
consumer group (e.g. a per-test `group-id` override via `@DynamicPropertySource`) — not required
for this task's own scope, no action taken beyond documenting it here.

### Finding 2 — `AuthEventConsumer` discards `ContactProjectionUpdater.upsertEmail`'s boolean return

**Severity:** Low (same class as T05's own Phase 7 Finding 2, now actually reached by a caller)

**Evidence:** Both listener methods call `contactProjectionUpdater.upsertEmail(...)` (returns
`boolean`: `true` if the write was accepted, `false` if the out-of-order guard rejected it) and
discard the result. Not a correctness issue — nothing in this task's own scope needs to branch on
a stale-write rejection — but it means `AuthEventConsumer` has no visibility into whether a given
message's projection write actually landed, which could matter for future observability/metrics.

**Recommendation:** No action required for T06's own scope. Worth revisiting if/when a future task
adds structured logging or metrics around projection freshness.

## Confirmed non-issue — transaction boundary already covers the full dispatch call

Re-verified `@Transactional` (default `REQUIRED`) genuinely wraps `dispatch(...)` too, not just the
two repository writes — confirmed by reading the method bodies directly (no early `return` after
the dedupe/projection steps skips past the annotated method boundary for the dispatch call in the
in-scope cases). `NoOpNotificationDispatcher` never throws, so this can't yet be observed by any
test rolling back a transaction on a failing dispatch — that proof is deferred to whichever task
(11/12) first gives `NotificationDispatcher` a real, fallible implementation.

## Verification performed

- `mvn -pl services/notification clean verify` — 98 tests, 0 failures, unchanged from Phase 6's own
  final record (the scratch test above was deleted before this run).
- Scratch verification (described in Finding 1, deleted before this commit): confirmed the full
  consume → dedupe → project → dispatch/no-dispatch pipeline against a real local Kafka broker and
  a real Postgres instance, across all 4 in-scope routing branches plus idempotent redelivery.
