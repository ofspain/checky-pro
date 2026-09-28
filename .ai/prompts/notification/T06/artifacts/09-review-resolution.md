# notification · T06 · Phase 9 — Review Resolution

Disposition of the 8 findings from `artifacts/08-independent-review.md`, cross-referenced with
`artifacts/07-self-review.md`'s own 2 findings (Kimi's own Findings #2/#3 name the same gap
self-review's own Finding 1 already closed empirically via a deleted scratch test; Kimi's own
Finding #8 is verbatim self-review's own Finding 2). All 8 findings independently verified against
real source before disposition (see below) — none were false.

## Finding 1 (Kimi) — no committed test locks the idempotency key format

**Disposition: DEFERRED to Phase 10.** Verified true: no `AuthEventConsumerTest` exists anywhere in
`src/test`, so nothing currently pins the exact `accountUuid + ":" + purpose/eventType + ":" +
occurredAt` key string. Same disposition as T03/T04/T05's own Phase 9 precedent — Phase 6's own
explicit rule ("tests are Phase 10's job") defers new tests; not a defect in T06's own completion
state at this point in the pipeline. Kimi's own recommended test shape (mocked collaborators,
capture the `eventKey` argument, assert the exact string) is retained verbatim as a required Phase
10 test.

## Finding 2 (Kimi) — no committed end-to-end test exercises the consumer against a real Kafka broker

**Disposition: DEFERRED to Phase 10, risk already closed once.** Verified true: the self-review's
own scratch test (`ScratchAuthEventConsumerVerificationTest`) proved this exact behavior — consume,
dedupe, project, dispatch — across 4 real scenarios, then was deleted per this pipeline's own
established scratch-test discipline. Kimi's own recommended shape (produce real messages, poll
`processed_events`/`contact_projection`, spy-assert `dispatch`, redeliver and assert no re-dispatch)
matches the deleted scratch test almost exactly and is retained verbatim as Phase 10's own required
test, including the self-review's own noted flakiness mitigation (widen the timeout or isolate
consumer groups per test).

## Finding 3 (Kimi) — no test verifies projection-update behavior on non-dispatched or duplicate events

**Disposition: ALREADY TRACKED, no new action.** Same substance as Finding 2 and self-review's own
Finding 1 — the deleted scratch test already covered exactly these two scenarios (unknown
`purpose`, non-registration `eventType`, both projection-updated-but-not-dispatched) plus the
redelivery-skips-projection-update case Kimi names here. Re-verified Phase 10's own required-test
list (per Finding 1/2's disposition above) already names all three; nothing to add beyond what
Findings 1/2 already captured.

## Finding 4 (Kimi) — no test verifies transaction rollback when `dispatch` throws

**Disposition: DEFERRED to Phase 10.** Verified true: `NoOpNotificationDispatcher` never throws, so
this property is currently unprovable by any test. Kimi's own recommended shape (inject a
`@Primary` throwing test dispatcher, assert no rows written) is retained as a required Phase 10
test — it proves the `@Transactional` boundary claim made in `AuthEventConsumer`'s own class-level
Javadoc and Finding #3 of the frozen brief, not yet exercised.

## Finding 5 (Kimi) — no test verifies `NoOpNotificationDispatcher` does not log token values

**Disposition: DEFERRED to Phase 10.** Verified true: `NoOpNotificationDispatcher.java` line 30
logs only `eventData.keySet()` (re-confirmed by direct read), so the code is correct, but nothing
asserts it. Kimi's own recommended shape (Logback `ListAppender`, assert the raw token never
appears) is retained as a required Phase 10 test, mirroring
`EmailRequestedEventContractTest.toStringExcludesTheRawToken`'s own precedent at the DTO layer.

## Finding 6 (Kimi) — contract tests do not validate schema format constraints

**Disposition: DEFERRED to Phase 10, scope narrowed.** Verified true: both contract tests check
field presence/absence only, not `format`/`enum` constraints. Introducing a full JSON Schema
validator library is out of this task's own scope (a new dependency, not named anywhere in the
frozen brief or `agents.md`) — instead, Phase 10 adds targeted assertions matching Kimi's own
fallback suggestion: `accountUuid` round-trips as a valid `UUID.toString()`, `email` values contain
`@`, `occurredAt` serializes as ISO-8601 parseable by `Instant.parse`. Low confidence finding,
narrow fix, no new dependency.

## Finding 7 (Kimi) — `NotificationDispatcher` in `consumer/` creates a forward dependency from `delivery/`

**Disposition: ACCEPTED, documented (not moved).** Verified: the frozen brief's own "Files to
Create" explicitly names `.../consumer/NotificationDispatcher.java` — moving it now would
contradict an already-frozen decision (same reasoning as T05's own Phase 9 Finding 2 disposition
for `ContactProjectionRepository`'s `JpaRepository` inheritance). Took Kimi's own offered fallback
instead: added a Javadoc paragraph to `NotificationDispatcher` documenting the architectural
concern and naming the relocation (`notification.delivery.api` or similar) as a reasonable Phase
11/12 follow-up, not required now.

## Finding 8 (Kimi) / Self-review Finding 2 — `AuthEventConsumer` discards `upsertEmail`'s boolean return

**Disposition: ACCEPTED, fixed.** Both listener methods now route the return value through a new
private `logProjectionOutcome(UUID, boolean)` helper, logging at `DEBUG` ("updated" vs.
"skipped (stale/out-of-order)") rather than silently discarding it — Kimi's own first offered
option, chosen over the second (Javadoc-only) since it's equally cheap and closes the observability
gap without waiting for a future caller. No behavior change (dispatch/no-dispatch logic and return
values are untouched); the log statement never includes `email`/`token` (L4 compliant, mirrors
`NoOpNotificationDispatcher`'s own key-set-only logging discipline).

## Verification performed

- `mvn -pl services/notification clean verify` — 98 tests, 0 failures, unchanged count (Findings
  7/8's fixes are source-only, no new/removed test). `AuthEventConsumer`'s new `logProjectionOutcome`
  call sites compile cleanly against `ContactProjectionUpdater.upsertEmail`'s existing `boolean`
  return type (no signature change needed anywhere).
- `git status -s services/auth services/crypto` — empty; no sibling service touched.
