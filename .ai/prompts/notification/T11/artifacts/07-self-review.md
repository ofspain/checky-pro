# notification · T11 · Phase 7 — Self-Review

## Files reviewed

- `delivery/{DeliveryLog,DeliveryLogRepository,DeliveryOrchestrator}.java`
- `channel/{NotificationChannel,NoOpEmailChannel,NoOpInAppChannel}.java`
- `preference/ContactProjectionUpdater.java`, `consumer/AuthEventConsumer.java` (both diffs)
- `T01SkeletonRegressionTest.java`, `AuthEventConsumerTest.java`, `AuthEventConsumerIntegrationTest.java`,
  `IdempotencyGuardIntegrationTest.java` (all diffs)

## Verification performed — empirical, not just inspection

Same discipline as every prior task's own Phase 7, scaled to this task's own larger surface area:
`DeliveryOrchestrator`'s real logic — the actual `NotificationDispatcher` bean the whole
application wires up — had never been executed end-to-end before this review. Wrote a temporary,
uncommitted scratch test (`ScratchDeliveryOrchestratorVerificationTest`, deleted before this
artifact was written) against a real Postgres instance and the real, fully-wired Spring context
(no spy/mock overrides at all — the actual `DeliveryOrchestrator`, `PreferenceResolver`,
`TemplateRenderer`, `ContactProjectionUpdater`, `NoOpEmailChannel`, `NoOpInAppChannel` all
participating together for the first time):

1. **The real happy path**: a real contact projection row, `dispatch("verify_email", ...)` — both
   `EMAIL` and `IN_APP` channels resolved as enabled (no stored opt-out), rendered
   (`email.verify`/`user.verify`, version 1), sent, and logged `SENT` with the correct recipient
   (`ada@example.com` for `EMAIL`, the account UUID's own string form for `IN_APP`) and the correct
   `source_event_key`. **Passed** — and this is the first time `List<NotificationChannel>`
   injection was proven to actually collect both `NoOp*Channel` beans correctly, not merely
   inspected as plausible.
2. **A real stored opt-out**: inserted a `channel_preferences` row disabling `SECURITY`/`IN_APP`
   directly, confirmed `dispatch` recorded `IN_APP` as `SUPPRESSED` (with the correct recipient,
   `null` template name/version per the frozen brief) while `EMAIL` still proceeded to `SENT`
   independently. **Passed.**
3. **A real missing contact projection**: no row at all for a fresh `accountUuid` — confirmed
   `EMAIL` recorded `FAILED` with `errorDetail = "no recipient email on file"` while `IN_APP` still
   proceeded to `SENT` (no email needed) — proving the per-channel independence the frozen brief's
   own two-level `try/catch` is designed to guarantee, not just that no exception happened to
   propagate. **Passed.**
4. **An unrecognized `notificationKind`**: confirmed literally zero `delivery_log` rows are written
   (not even a `FAILED` row) — matching AC2's own "silent no-op" framing exactly. **Passed.**
5. **The `@Transactional` join/rollback claim** (Kimi Phase 3 Finding #1's own corrected test):
   wrapped a real `dispatch` call in an external `TransactionTemplate`, marked it
   `setRollbackOnly()`, and confirmed the `delivery_log` rows `dispatch` had written were gone
   afterward — the single most consequential, least-obvious claim in this task's own frozen brief,
   now proven against a real transaction manager, not merely asserted correct by inspection.
   **Passed.**

**No defect found.** Every one of the frozen brief's own pinned outcomes and the transaction-join
claim behaved exactly as designed on the first real attempt.

## Findings

No new findings this review — the implementation matches the frozen brief exactly on every
dimension exercised, and the two properties most worth empirically confirming (channel
independence under a real per-channel failure; real transaction rollback) were now proven against
real infrastructure, not merely asserted correct by inspection.

## Confirmed non-issue — a `null` `sourceEventKey` still can't escape `dispatch`

Considered during review (not exercised by the scratch test, reasoned through by inspection): if a
future caller ever passed `eventData` without a `"sourceEventKey"` entry, `DeliveryLog`'s own
`source_event_key` column is `NOT NULL`, so the very first `save()` call would throw a constraint
violation. That exception is caught by `dispatchOneChannel`'s own inner `catch` block, which then
attempts a *second* `save()` call (recording `FAILED`) — which would throw the identical
constraint violation again, since `sourceEventKey` is still `null`. This second exception is not
caught by anything *inside* `dispatchOneChannel`, but it is still caught by `dispatch`'s own outer
`try/catch`, so `dispatch` itself still never throws (AC9 holds at the coarser, outer level even in
this edge case) — it just means a `null`-`sourceEventKey` caller loses the graceful
per-channel-`FAILED`-row behavior for *both* channels, not just one. `AuthEventConsumer` (the only
real caller today) always supplies it, so this is not currently reachable — flagged here as a
disclosed, informational limitation, not fixed (fixing it would mean nesting a third `try/catch`
purely to protect the second `save()` call, at a complexity cost not justified for an unreachable
input today).

## Verification performed

- `mvn -pl services/notification clean verify` — 196 tests, 0 failures, unchanged from Phase 6's
  own final record (the scratch test above was deleted before this run).
- Scratch verification (described above, deleted before this commit): confirmed
  `DeliveryOrchestrator`'s full behavior — the real happy path (both channels), a real suppression,
  a real missing-recipient failure with per-channel independence, the unknown-kind no-op, and real
  transaction join/rollback — against a real Postgres instance and the fully-wired real Spring
  context. Re-ran the full scratch class twice; both runs clean (5/5 each).
