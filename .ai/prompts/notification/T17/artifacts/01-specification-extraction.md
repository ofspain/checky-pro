# notification · T17 · Phase 1 — Specification Extraction

Scope per Phase 0's own user decision: the `auth.email.requested(verify)` redelivery/idempotency
scenario only. The `payments.receipt.issued` scenario is deferred, disclosed, identical in kind to
T07's own already-accepted blocker.

## Business Rules

- **R1.** WHEN an `auth.email.requested` event with purpose `verify_email` is consumed, THEN the
  system SHALL send the recipient an email-verification message containing the verification link.
  This task's own job: prove this happens through the *real*, fully-wired pipeline — not a stand-in.
- **R7.** WHEN a consumed event carrying a stable event key is processed, THEN the system SHALL
  record that key so the same event is processed at most once.
- **R8.** IF the same event is redelivered (at-least-once Kafka semantics or a consumer-group
  rebalance), THEN the system SHALL NOT produce a second delivery for it — this task's own literal
  wording ("redeliver the same event → no second email").

## Locked Decisions

- **L1.** Idempotent by event key — a `processed_events` table written in the same transaction as
  the delivery-log append (R7, R8); Kafka is at-least-once, not optional.
- **L3.** Dispute-grade delivery log — every attempt recorded, append-only. This task's own
  end-to-end run is itself a live proof that a real `delivery_log` row is produced by the real
  chain, not only by direct, unit-scoped calls to `DeliveryOrchestrator`.

## Real gap this task closes — confirmed by direct inspection, not assumed

Every individual piece of the chain this task needs is already real-Postgres/real-collaborator
tested — but **no existing test proves the full chain glued together end-to-end**:

- `AuthEventConsumerIntegrationTest` (T06) proves a real Kafka-produced `auth.email.requested`
  event is consumed, deduped, projected, and dispatched — through a real local Kafka broker and
  real Postgres — **but it replaces the real `NotificationDispatcher` bean
  (`DeliveryOrchestrator implements NotificationDispatcher`) with a `SpyDispatcherConfig`**
  (`AuthEventConsumerIntegrationTest.java:54-64`), so nothing downstream of the dispatch call
  (preference resolution, template rendering, the real `EmailChannel`/`FakeEmailTransport`) is
  exercised by that test at all. Its own redelivery assertion (`:140-146`) only checks a spy's own
  call count, not a captured email.
- `DeliveryOrchestratorIntegrationTest` (T11) proves `DeliveryOrchestrator` → `PreferenceResolver`
  → `TemplateRenderer` → `EmailChannel` → `FakeEmailTransport` end-to-end with real Postgres — **but
  it calls `orchestrator.dispatch(...)` directly** (confirmed: no Kafka container, no
  `KafkaTemplate` anywhere in that file's own imports) — the real Kafka-consumption half is never
  exercised.

**This task's own real deliverable**: one new integration test gluing both already-proven halves
together for the first time — a real Kafka-produced `auth.email.requested(verify_email)` event,
consumed through the real (unspied) production wiring, landing as exactly one captured email in
`FakeEmailTransport`, with redelivery producing no second one.

## Files involved

**Existing — read, not modified, the real chain this task proves end-to-end:**
- `consumer/AuthEventConsumer.java`, `consumer/IdempotencyGuard.java`/`ProcessedEvent.java` (T04/T06)
- `delivery/DeliveryOrchestrator.java` (T11, the real `NotificationDispatcher` in production)
- `preference/PreferenceResolver.java` (T08), `template/TemplateRenderer.java` (T09)
- `channel/EmailChannel.java`, `channel/FakeEmailTransport.java` (T12) — the capturing transport
  task 17's own wording names explicitly
- `AuthEventConsumerIntegrationTest.java` (T06) and `DeliveryOrchestratorIntegrationTest.java` (T11)
  — read in full to confirm the gap above; neither is modified, both remain as the dedicated,
  narrower proofs they already are

**New — this task's own real deliverable:**
- One new end-to-end integration test (exact name/location is Phase 2's own decision), real
  Testcontainers Postgres + the shared local Kafka broker convention (mirrors
  `AuthEventConsumerIntegrationTest`'s own established pattern — no Testcontainers Kafka module is
  used anywhere in this service) + the real, unspied `FakeEmailTransport`.

## Dependencies

None new. Every dependency this task needs (`testcontainers`, `spring-kafka`, `awaitility`) is
already present since T01/T06/T11.

## Acceptance Criteria

1. **AC1** (R1/R7/L1). A real `auth.email.requested` event with purpose `verify_email`, produced to
   the real Kafka broker, results in exactly one captured email in `FakeEmailTransport`, through the
   real, unspied production wiring (`AuthEventConsumer` → `DeliveryOrchestrator` →
   `PreferenceResolver` → `TemplateRenderer` → `EmailChannel`).
2. **AC2** (R8). Redelivering the identical event (same key) produces no second captured email —
   proven against the real `FakeEmailTransport` capture list, not a dispatch-call spy.
3. **AC3** (L3). A real `delivery_log` row exists for the delivery, with outcome `SENT`, produced by
   the real chain — not asserted only indirectly via the email capture.
4. **AC4** (disclosure). The `payments.receipt.issued` scenario is explicitly, visibly absent —
   matching this task's own Phase 0 scoping decision, not silently dropped.

## Required Tests

One new end-to-end integration test proving AC1-AC3 together, in one real Kafka-to-capture run, plus
its own redelivery step. No change to any existing test — `AuthEventConsumerIntegrationTest` and
`DeliveryOrchestratorIntegrationTest` remain exactly as they are, each still the right place for
their own narrower, already-proven claims.

## Open Questions

No blockers remaining — Phase 0 already resolved the one real blocker (the payment scenario) via
explicit user decision. One concrete implementation choice remains for Phase 2: the exact class
name/package for the new test (a senior-engineer-level naming choice, not a blocker to extraction).
