# notification · T17 · Phase 9 — Review Resolution

**Human Approval gate.** Resolution log for the Phase 7 self-review (1 finding, already fixed
inline) and Phase 8 independent review (6 findings — 1 concurs with self-review, 5 new). Every
factual claim was re-verified directly against actual source before disposition, not accepted on
word.

## Finding 1 (Kimi concurs with self-review) · Diagnostic messages now consistent

**ACCEPTED, already fixed at Phase 7.** No further action.

## Finding 2 (Kimi, new) · `delivery_log.source_event_key` propagation was not asserted

**ACCEPTED and fixed.** Verified directly: `AuthEventConsumer.java:93` places the real idempotency
key into `eventData` under the literal key `"sourceEventKey"`
(`Map.of("token", event.token(), "sourceEventKey", eventKey)`), and `DeliveryOrchestrator` threads
it through every `save(...)` call into `DeliveryLog.sourceEventKey` — a real, present correlation
field, not asserted before.

**Change:** `DeliveryLogRow` gained a `sourceEventKey` field; the first-delivery assertion now
checks both rows' `sourceEventKey` equals the test's own computed `eventKey`, strengthening AC3
exactly as Kimi proposed — a real dispute-grade-log (L3) correlation guarantee, not merely that a
row exists.

## Finding 3 (Kimi, new) · The `delivery_log` row was not linked to the captured email's recipient

**ACCEPTED and fixed**, applied together with Finding 2 since both are the same class of
cheap, low-risk, clearly-correct strengthening — not left as documentation-only, consistent with
this pipeline's own precedent (T15) for findings exactly this shape.

**Change:** `DeliveryLogRow` also gained a `recipient` field; the EMAIL row's own `recipient` is now
asserted to equal `"e2e@example.com"`, the real address from the produced event — ruling out a
regression where an email is captured but the log records a wrong or null recipient.
`DeliveryOrchestratorIntegrationTest` was confirmed (`:253`, `:263`) to already assert recipient
equality for its own scenarios — this test now carries the same guarantee for the real,
Kafka-triggered path.

## Finding 4 (Kimi, new) · No assertion that the second Kafka message is actually consumed

**ACCEPTED as correctly assessed, no change.** Matches Kimi's own conclusion: the intended proof
("redelivery produces no second email/log row") is fully satisfied by the unchanged counts; proving
the second record was *seen and deduped* specifically (vs. e.g. an offset skip) is a stronger claim
than AC2 asks for, and `AuthEventConsumerIntegrationTest`'s own redelivery assertion has the
identical shape. Not added.

## Finding 5 (Kimi, new) · Shared local Kafka broker state can leak between manual reruns — now fully explained

**ACCEPTED, and the root cause is now confirmed, not merely plausible.** While applying Findings
2/3's fix, the exact same exception class recurred with its full stack trace visible this time:
`com.fasterxml.jackson.core.JsonParseException: Unexpected character ('n' ...)` thrown from
`AuthEventConsumer.onEmailRequested` — a malformed-JSON message. This is conclusively a backlog
message from `AuthEventConsumerTest`'s/`AuthEventConsumerIntegrationTest`'s own deliberate,
already-tested poison-message scenario (`malformedMessageDoesNotPermanentlyPoisonTheListener`),
sitting on the shared, persistent local `auth.email.requested` topic from an earlier test run in
this or a prior session, consumed by this test's own brand-new consumer group reading from the
earliest available offset. The consumer recovered and continued processing correctly (confirmed by
the subsequent real `EmailChannel` sends and this test's own passing result) — exactly the already-proven
resilience Kimi's own assessment cited. No code change; this fully explains, rather than merely
excuses, Phase 7's own "Observation."

## Finding 6 (Kimi, new) · JDBC was used for `delivery_log` rather than `DeliveryLogRepository`

**ACCEPTED as accurate, no change.** Confirmed: Phase 3's own "Decisions Made" list (Decision #5)
did propose repository injection for `delivery_log`; Phase 6 chose JDBC for both tables instead,
already disclosed and justified in Phase 6's own implementation notes (`DeliveryLogRepository`'s own
Javadoc states plain `save()` is its entire write path by design). Kimi's own assessment ("valid,
arguably simpler... acceptable") matches that disposition; no further documentation update needed
beyond what Phase 6 already recorded.

## Summary

Two real strengthenings applied (Findings 2/3), both verified against actual source first and both
passing after the change (re-run 3 additional times for stability, all clean). One finding fully
resolved a previously-open observation with its now-confirmed root cause (Finding 5) rather than
leaving it as a disclosed-but-unexplained anomaly. Three findings required no change, each matching
Kimi's own correct self-assessment. Full suite re-verified: `mvn -pl services/notification clean
verify` — 369 tests, 0 failures, 0 errors (unchanged count — this phase only strengthened existing
assertions, added no new test method).
