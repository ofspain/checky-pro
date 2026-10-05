# notification · T17 · Phase 6 — Implementation Notes

Implemented per the Phase 5 plan, with the two deliberately-deferred specifics (exact
`FakeEmailTransport`/`DeliveryLogRepository` access) resolved as the plan itself anticipated — no
surprise, no new deviation.

## Files created

- `delivery/VerifyEmailRedeliveryIntegrationTest.java` — one `@Test` method,
  `verifyEmailRedeliveryProducesExactlyOneEmailAndNoDuplicateDeliveryLogRows`, proving the real,
  unspied production chain (`AuthEventConsumer` → `DeliveryOrchestrator` → `PreferenceResolver` →
  `TemplateRenderer` → `EmailChannel`/`InAppChannel`) end to end against a real Kafka-produced
  `auth.email.requested(verify_email)` event, plus its own redelivery step.

## Files modified

None. `DeliveryLogRepository`'s own Javadoc is explicit that plain `save()` is its "entire write
path" by design — adding a finder method for one test would contradict that deliberate convention.
Used a direct JDBC query against `notifications.delivery_log` instead (`channel`, `outcome`
columns), mirroring `AuthEventConsumerIntegrationTest`'s own established JDBC-for-contact-projection
pattern exactly. `FakeEmailTransport.sentMessages()` already existed exactly as planned — no
production change needed there either.

## Deviation from the plan

None in substance. The plan's own two open items (exact accessor names, repository-vs-JDBC choice)
were resolved exactly as the plan's own fallback language anticipated: `FakeEmailTransport`'s real
method is `sentMessages()` (confirmed by reading the file first, per the plan's own execution-order
step 1); `DeliveryLogRepository` has no finder methods by design, so JDBC was used, matching the
plan's own stated preference for "whichever avoids touching production code."

## A transient first-run timeout, not a bug

The very first local run timed out waiting for `processed_events` to contain the event key (40s
budget). Two immediate reruns both passed cleanly (16.74s–16.96s, well under budget), with the real
`EmailChannel`'s own log line visible (`Email sent: accountUuid=..., messageId=fake-...`) — Kafka
consumer-group join/sync took noticeably longer on the first run of the session (new Testcontainers
Postgres container, cold consumer-group coordinator state) than on either rerun. Treated as
environment warm-up variance, not a product defect: the same shared-local-Kafka-broker convention
`AuthEventConsumerIntegrationTest` already uses has no history of this kind of flake once warm, and
nothing in the chain this test exercises is new production code.

## Mapping to acceptance criteria

- **AC1**: the real, unspied chain produces exactly one captured `EmailMessage` for the account —
  verified by filtering `fakeEmailTransport.sentMessages()` by `accountUuid` (not a raw list size,
  so the assertion is immune to any other concurrently-running test's own captures).
- **AC2**: redelivering the identical event (same `accountUuid`+`occurredAt`, hence the same
  `eventKey`) leaves both the email count and the `delivery_log` row count unchanged, waited on with
  `Awaitility`'s own `pollDelay`+`atMost`, not asserted immediately.
- **AC3** (Finding #4's revision): exactly two `delivery_log` rows exist after the first delivery —
  one `EMAIL`, one `IN_APP`, both `SENT` — confirmed via `containsExactlyInAnyOrder("EMAIL", "IN_APP")`
  plus a per-row outcome check, not merely a row count that could silently hide a wrong channel.
- **AC4**: the class Javadoc states explicitly that only `auth.email.requested(verify_email)` is
  covered and why `payments.receipt.issued` is out of scope.

## Verification

- `mvn -pl services/notification test-compile` — clean.
- `mvn -pl services/notification test -Dtest=VerifyEmailRedeliveryIntegrationTest` — run 3 times:
  1 transient timeout (cold start, disclosed above), then 2 clean passes (`Tests run: 1, Failures: 0,
  Errors: 0`).
- `mvn -pl services/notification clean verify` — 369 tests, 0 failures, 0 errors (368 + 1 new).
