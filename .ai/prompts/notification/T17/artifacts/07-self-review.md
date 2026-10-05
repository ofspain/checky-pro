# notification · T17 · Phase 7 — Self Review

Self-review of the Phase 6 implementation against the frozen brief and `agents.md`. One finding was
cheap, clear, and fixed directly during this review rather than deferred to Phase 9; one scope
choice is disclosed, not a defect; one run-time observation was investigated and did not reproduce.

## Finding 1 · Inconsistent diagnostic messages across the test's own assertions — fixed

**Severity:** Low

**Evidence:** The original Phase 6 version gave `.as(...)` descriptions to the delivery-log-row
assertion and both redelivery assertions, but not to the first `processedEventExists` or
first-delivery email-count assertions.

**Issue:** Experienced directly, not hypothetically: the very first local run of this test timed
out waiting on `processedEventExists` (cold Testcontainers/consumer-group start, see Phase 6's own
disclosure), and the resulting failure message was a generic `Expecting value to be true but was
false within 40 seconds` — no indication of *what* was being waited for. A future genuine failure
on either of these two assertions would read the same way, costing a reader time working out which
of the test's own multiple `await()` blocks actually failed.

**Fix applied:** Added `.as("the produced event must be recorded in processed_events")` and
`.as("verify_email must result in exactly one captured email")` to the two assertions that lacked
one, matching the style already used by the other two. Re-verified: still passes
(`Tests run: 1, Failures: 0, Errors: 0`).

## Observation · A one-time, non-reproducing Kafka listener exception during repeated manual reruns

**Not treated as a finding** — investigated, not fixed, because it never reproduced again and never
caused a test failure. While re-verifying Finding 1's fix, one run's log showed a Spring Kafka
listener-invocation exception trace followed by several successful `EmailChannel` sends for
*different* account UUIDs than the current run's own — consistent with the consumer catching up on
previously-unprocessed messages left on the shared local Kafka broker from an earlier manual rerun
in this same session (this service's own established convention is a shared local broker, not
Testcontainers Kafka, so state persists across repeated local invocations within one session).
Four subsequent clean reruns (three dedicated to checking this specifically) showed no recurrence.
This is consistent with — not contradicting — `AuthEventConsumerIntegrationTest`'s own already-proven
`malformedMessageDoesNotPermanentlyPoisonTheListener` behavior: the consumer is already known to
recover from a processing exception and continue. Not a defect in this task's own new code; an
artifact of repeated interactive local reruns against persistent shared broker state, not something
a real CI run (one broker lifetime per build) would encounter the same way.

## Scope choice, disclosed · No assertion on captured-email content or on an `inapp_notifications` row

**Not a gap** — deliberate, matching this task's own bounded purpose. This test's job (Phase 1) is
proving the *wiring glue* between two already-separately-proven halves, not re-proving template
rendering content (`TemplateRendererIntegrationTest`'s own job) or `InAppChannel`'s own persistence
correctness (`InAppChannelIntegrationTest`'s own job, T13). Asserting only presence/count/outcome at
the `delivery_log`/`FakeEmailTransport` boundary is the right level for what this task was scoped to
prove.

## Open Questions

No blockers. The one real, fixable finding was fixed inline; the other two items are disclosed,
not defects.
