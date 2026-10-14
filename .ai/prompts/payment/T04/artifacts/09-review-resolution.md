# payment · T04 · Phase 9 — Review Resolution

Human Approval gate. Decision made by the human (approved 2026-10-14); this artifact records it.

## Finding 1 — "Required `EventTopicsTest` is missing"

**REJECTED as a Phase 9 action — same disposition as `payment` T02 Phase 9 finding 1.** The
observation itself is factually correct (the file does not exist yet), but it is the deliberate,
correct consequence of this task having followed the pipeline's own phase separation:
`06-implementation-notes.md` explicitly states `EventTopicsTest.java` was deferred to Phase 10,
and `07-self-review.md` reviews only the Phase 6 diff, which correctly excludes it. No change
made in this phase; Phase 10 writes it.

## Finding 2 — "No source-text guard for the T04 `application.properties` addition"

**ACCEPTED as a correct, genuinely new gap** — `ApplicationPropertiesContentTest.java` is
confirmed (by direct read) to be entirely T03-scoped; it does not assert
`themistra.payment.outbox.relay-interval-ms` is present. **No test code is written in this
phase** — adding the assertion is test authorship, which belongs in Phase 10 by the same rule as
Finding 1. Logged here so Phase 10 adds one assertion to `ApplicationPropertiesContentTest`
(or an equivalent T04-scoped guard) alongside writing `EventTopicsTest`.

## Finding 3 — "`OutboxPublisher` has no transaction-active guard"

**ACCEPTED as a correct technical observation; NO code change made.** Re-confirms
`07-self-review.md` finding 1. Verified again that `crypto-service`'s own `OutboxPublisher` has
the identical no-guard shape — this is an established, consistent precedent across the codebase,
not a defect introduced by this task. Decided not to diverge from that precedent for
payment-service alone. If ever fixed, it is a cross-service hardening task, not R30-scoped.

## Finding 4 — "Unroutable outbox events will be re-logged on every poll forever"

**ACCEPTED as written; no action required.** `OutboxRelay.relayOne`'s own comment (lines 66-70)
already documents exactly this behavior and why no quarantine state exists. Kimi's own
confidence on this finding was Low and its own recommendation was "acceptable for now, document
it" — already done.

## Finding 5 — "`OutboxEvent` maps the real schema exactly"

**ACCEPTED as written — positive confirmation, no action required.**

## Finding 6 — "Producer config and scheduling infrastructure are in place"

**ACCEPTED as written — positive confirmation, no action required.**

## Net result

**Nothing was accepted that required a code or test change in this phase.** Findings 1 and 2
both route into Phase 10 (test generation); findings 3 and 4 are disclosed, accepted,
no-action observations. No file was modified in this phase. Proceeding to Phase 10, where
`EventTopicsTest.java` is written (R30, the named test
`shouldRouteEachPublishedEventToItsPaymentsTopic` plus the negative unmapped-type case) and
`ApplicationPropertiesContentTest` gets its one new T04 assertion.
