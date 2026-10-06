# notification · T19 · Phase 1 — Specification Extraction

## Business Rules (verbatim from `spec/notification-service/requirements.md`)

- **R11.** WHEN a delivery is attempted on any channel, THEN the system SHALL append a delivery-log
  record capturing recipient, channel, source event key, template version, outcome, and timestamp.
- **R12.** IF a channel delivery fails with a transient error, THEN the system SHALL mark the attempt
  `FAILED`, retain it in the log, and schedule a bounded retry per the backoff policy.
- **R13.** IF a delivery has failed the maximum number of attempts, THEN the system SHALL stop
  retrying, record a terminal `DEAD_LETTERED` outcome, and SHALL NOT retry indefinitely.
- **R10.** IF a recipient has opted out of a channel for a given notification category, THEN the
  system SHALL suppress delivery on that channel and record the suppression in the delivery log.

## Locked Decisions (verbatim from `design.md`)

- **L3.** Dispute-grade delivery log. Every attempt on every channel is recorded with recipient,
  channel, source event key, template version, outcome, and timestamp (`ARCHITECTURE.md` §3.5). The log
  is append-only — a retry adds a new attempt row; it never overwrites the prior attempt. This is the
  evidence for "was the merchant notified?" in later dispute resolution.
- **L9.** Each rendered message records the template version used (in the delivery log), so a later
  dispute can reconstruct exactly what the recipient was shown.

## Source of the "why"

`ARCHITECTURE.md` §3.5 (Notification Service): the `notifications` schema owns the "delivery log
(needed for 'did the merchant get notified?' disputes later)." Task 19 asks for a check, not new
behavior; the requirement is already stated, and this task proves it holds.

## Acceptance Criteria

1. **AC1** (L3, append-only). A real `UPDATE` and a real `DELETE` on an existing `delivery_log` row,
   executed as the `notification_app` role against the real database, are rejected by PostgreSQL.
2. **AC2** (L3, reconstructability). For one source event, a real retry chain (a transient failure
   followed by a later attempt, up to terminal outcome) leaves multiple rows that all share the
   same `source_event_key`, differ by `attempt`, and are returned together when queried by that key.
   Each row carries recipient, channel, outcome, and timestamp.
3. **AC3** (R11/L9, template version). Every row that rendered a message records its
   `template_version`. Disclosed scope: rows written before rendering (for example, an EMAIL row
   with "no recipient email on file", or a SUPPRESSED row) legitimately carry no template version,
   because no message was rendered; AC3 is checked against rendered rows, not all rows.
4. **AC4** (R10, suppression recorded). A suppressed channel leaves a `SUPPRESSED` row in the log
   for that source event, not silence.
5. **AC5** (no application path rewrites a row). No code path in `services/notification` updates or
   deletes an existing `delivery_log` row, verified by the absence of any mutation path in code and
   by the live proof in AC1.

## Files involved

- Existing, read only: `DeliveryLog.java`, `DeliveryLogRepository.java`, `DeliveryOrchestrator.java`
  (the only write path, line 388), `RetryScheduler.java`, `V1__notifications_baseline.sql`,
  `V2__notification_app_role_and_grants.sql` (line 40).
- New: one integration test class that exercises the real database as `notification_app`, plus any
  shared fixture it needs. Exact placement is a Phase 2 decision.

## Dependencies

None new. Testcontainers Postgres, Flyway, and the existing integration-test pattern are already
used across this module.

## Required Tests

Named coverage for AC1, AC2, AC4, and AC5 (live database and live chain). AC3 is covered by the same
live run, with the rendered-versus-unrendered scope disclosed rather than tested as an absolute.

## Open Questions

No blockers. Phase 2 must decide: how the retry chain is driven in the test (direct invocation of
`DeliveryOrchestrator` or `RetryScheduler` behavior, as T14's own tests do), and whether suppression
(AC4) gets its own scenario or reuses an existing test's setup.
