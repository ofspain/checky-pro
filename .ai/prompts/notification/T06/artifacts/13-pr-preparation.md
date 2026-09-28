# notification · T06 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T06: auth event consumer (AuthEventConsumer, dispatch seam)`

## Commit message

```
notification-service T06: auth event consumer

Add AuthEventConsumer, the first real @KafkaListener in this codebase -
two methods, one per topic (auth.email.requested, auth.user.lifecycle),
each: dedupes via IdempotencyGuard (T04) first (L1, AC2/AC7), refreshes
ContactProjection via ContactProjectionUpdater (T05, AC3), then resolves
which notification kind applies (verify_email/password_reset/
user.registered) and hands off to NotificationDispatcher, a new seam
interface (AC4/AC5). Both listeners are @Transactional REQUIRED (AC8) -
dedupe, projection-refresh, and dispatch share one atomic transaction, so
a future failing dispatch rolls back the idempotency record too.

Add NotificationDispatcher (the seam) and NoOpNotificationDispatcher (its
real, working, temporary implementation - logs accountUuid/
notificationKind/eventData's own key set only, never eventData's values,
since eventData may carry a raw verification/reset token - L4, AC6/AC9).
Deliberately omits email from the seam's own signature: a future real
implementation resolves the recipient via contact_projection, which this
consumer's own upsertEmail call (same transaction, runs first) keeps at
least as fresh as the event allows.

Add EmailRequestedEvent/UserLifecycleEvent, hand-written deserialization
records mirroring services/auth's own producer-side payload records (no
code-generation tooling exists anywhere in this repo for contracts/
events/* - a disclosed, already-established-practice deviation from
agents.md's codegen rule, not a new violation this task introduces).
Required contract tests substitute for generation, mirroring auth's own
{EmailRequestedEventPayload,UserLifecycleEventPayload}ContractTest
pattern.

Idempotency key format pinned exactly: accountUuid + ":" +
purpose/eventType + ":" + occurredAt, using the deserialized Instant's
own toString() (ISO-8601 UTC) - stable across redeliveries. Unknown
purpose values and non-registration eventType values are still
dedup-recorded and projection-refreshed but never dispatched -
eventType, not status, is the real signal for user.registered (three
different transitions - registered/reinstated/unlocked - all produce
status=ACTIVE alike).

spring.kafka.consumer.auto-offset-reset=latest: a newly deployed/
rebalanced consumer must not replay historical events - replaying old
verification/reset links would resend stale, expired links; replaying
old lifecycle events risks re-welcoming inactive accounts.

Updates T01SkeletonRegressionTest per an undisclosed-in-brief but
required deviation (same recurring gap T04/T05 each hit): 5 new
production files, 15-file authorized list becomes 20.

127 tests total (92 T01-T05 unaffected + 35 new: 6 contract, 10 unit,
6 real-Kafka integration, 1 transaction-rollback integration, 3 no-op
dispatcher log-safety, 2 config, 3 config-format, plus 4 reflection/
static-scan guards folded into the above). Two Kimi review rounds (Phase
8: 8 findings; Phase 11: 9 gaps) both fully resolved - all 17 verified
against real source before disposition, none false. Two real empirical
discoveries during this task's own pipeline: a scratch end-to-end test
(Phase 7) proved the listener wiring actually worked before any
permanent test existed for it; a transaction-rollback test (Phase 10)
proved the @Transactional atomicity claim, previously only documented,
actually holds when dispatch throws.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/consumer/AuthEventConsumer.java`
- `services/notification/src/main/java/com/themistra/notification/consumer/NotificationDispatcher.java`
- `services/notification/src/main/java/com/themistra/notification/consumer/NoOpNotificationDispatcher.java`
- `services/notification/src/main/java/com/themistra/notification/consumer/dto/{EmailRequestedEvent,UserLifecycleEvent}.java`
- `services/notification/src/test/java/com/themistra/notification/consumer/dto/{EmailRequestedEventContractTest,UserLifecycleEventContractTest}.java`
- `services/notification/src/test/java/com/themistra/notification/consumer/AuthEventConsumerTest.java`
- `services/notification/src/test/java/com/themistra/notification/consumer/AuthEventConsumerIntegrationTest.java`
- `services/notification/src/test/java/com/themistra/notification/consumer/AuthEventConsumerTransactionRollbackIntegrationTest.java`
- `services/notification/src/test/java/com/themistra/notification/consumer/NoOpNotificationDispatcherTest.java`

**Modified**
- `services/notification/src/main/resources/application.properties` (`spring.kafka.consumer.group-id`/`auto-offset-reset` block)
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (20-file authorized list, renamed method)
- `services/notification/src/test/java/com/themistra/notification/consumer/IdempotencyGuardIntegrationTest.java`
  (Phase 11 Gap #8: real `NoOpNotificationDispatcher` bean-resolution proof)
- `services/notification/src/test/java/com/themistra/notification/ApplicationPropertiesJpaConfigTest.java`
  (Phase 11 Gap #9: `group-id`/`auto-offset-reset` config assertion)

**Process artifacts**
- `.ai/prompts/notification/T06/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Delivers the first real Kafka consumer in `notification-service`: dedupe (T04) and recipient
projection (T05) are now genuinely wired to live auth events, and a dispatch seam is in place for
whichever future task (11/12) builds real email/in-app delivery. Every downstream email-delivery
task (R1, R2, R6) now has a real, tested, atomic path from "auth published an event" to "we know
what to notify and who" — only the actual send is still a no-op.

## Testing performed

- `mvn -pl services/notification clean verify` — 127 tests, 0 failures, `BUILD SUCCESS`.
- Real end-to-end verification against a live local Kafka broker and Testcontainers Postgres,
  first as an uncommitted Phase 7 scratch check, then made permanent in Phase 10/11:
  `AuthEventConsumerIntegrationTest` (6 tests: both topics, both in-scope purposes, unknown-purpose
  and non-registered-eventType non-dispatch, idempotent redelivery, malformed-message resilience).
- `AuthEventConsumerTransactionRollbackIntegrationTest` — proves a failing `dispatch` rolls back
  both the idempotency record and the projection write, the atomicity claim in
  `AuthEventConsumer`'s own Javadoc, previously undemonstrated by any test.
- One real mutation test, reverted clean (`git status -s` empty afterward): removed the
  idempotency short-circuit entirely from `onEmailRequested`, confirmed exactly the 2 tests that
  should catch it failed (no more, no fewer), then reverted and re-verified.
- One real empirical finding surfaced during Phase 12's own verification (not fixed, disclosed):
  a schema-violating payload missing a required field deserializes to `null` rather than throwing
  (Jackson's default record behavior) - confirmed via a temporary scratch test, deleted before
  commit.
- `git status -s services/auth services/crypto` — empty throughout this task's own commits; no
  sibling service touched (unlike T05, which required one).

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 6 ("Auth event consumer").
- **Requirements:** R1, R2, R6, R7, R8 (dedupe, verification/reset/welcome routing).
- **LOCKED decisions:** L1 (idempotent by event key), L2 (consume-only, no synchronous
  cross-service call), L4 (no secrets/tokens in logs). L5 (channels behind one interface) not yet
  applicable - `NotificationDispatcher` is the seam a future channel implementation sits behind.

## Known, deliberate gaps (not this task's scope)

- **Dead-letter handling**: retry is bounded and terminates (Spring Kafka's default
  `FixedBackOff(0, 9)`, proven via `malformedMessageDoesNotPermanentlyPoisonTheListener`), but a
  permanently failing message is logged and skipped, not routed to any dead-letter topic. Disclosed
  and explicitly deferred at Phase 4 (Kimi Finding #7) - not named in `tasks.md` for any task
  through T20, a genuine whole-service gap.
- **Missing-field payloads deserialize silently to `null`**, not a thrown exception - surfaced
  empirically at Phase 12, shared with `auth-service`'s own hand-written payload records, not
  specific to this task.
- **`NotificationDispatcher` lives in `consumer/`**, which a future `delivery/`-package
  implementation will depend back on - architecturally backward, but the frozen brief explicitly
  pins this package; documented in the interface's own Javadoc as a Phase 11/12 relocation
  candidate (Kimi Phase 8 Finding #7), not fixed now.
- `displayName` remains permanently unpopulated (T05's own already-disclosed risk, unchanged here).
- No real email/in-app send exists yet - `NoOpNotificationDispatcher` is deliberately temporary,
  replaced (file deleted) by whichever task (11/12) first builds real dispatch.

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 9, 8, and 9
  findings/gaps respectively — all 26 verified against actual source before disposition; all were
  real (no false findings this task, unlike an earlier task in this same pipeline where one Kimi
  finding was verified false).
- Two spec-level blockers surfaced and were resolved via explicit user decisions before Phase 4
  could freeze: (1) what "→ templates" could mean given tasks 9/11/12 don't exist yet — resolved
  by building a real seam now, not a stub; (2) `auth.user.lifecycle` carried no field distinguishing
  `user.registered` from `user.reinstated`/`user.unlocked` (all three produce `status=ACTIVE`
  alike) — resolved via a real, separately-committed, separately-tested `auth-service` change
  (commit `fbebf1d`, adding `eventType` to the payload) before this task's own design could be
  finalized.
- `AuthEventConsumer` discarding `ContactProjectionUpdater.upsertEmail`'s own `boolean` return was
  found independently twice — once by self-review (Phase 7), once by Kimi (Phase 8 Finding #8) —
  fixed once, at Phase 9.
- The transaction-rollback proof (Phase 10) and the malformed-message resilience proof (Phase 11)
  are both worth a reviewer's specific attention: they exercise real Spring Kafka error-handling
  behavior (retry, backoff, recovery) that is easy to get wrong and easy to leave undemonstrated.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T06.**
