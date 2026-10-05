# notification · T17 · Phase 10 — Test Generation

`VerifyEmailRedeliveryIntegrationTest`'s own single test method was written at Phase 6 and
strengthened twice already (Phase 7's diagnostic-message fix, Phase 9's `sourceEventKey`/EMAIL-
recipient correlation checks). This phase's own job was an audit for one more genuine gap in the
same class of checking the prior phases just closed — not a brand-new scenario.

## Gap found and closed in this phase

- **The IN_APP row's own recipient was never asserted, only the EMAIL row's.** Phase 9 closed this
  for `EMAIL` (Kimi's own Finding 3) but left `IN_APP` unchecked — an asymmetry within the very same
  assertion block. Verified directly: `DeliveryOrchestrator.dispatchOneChannel` (`:195`) sets
  `String recipient = "EMAIL".equals(channel) ? email : accountUuid.toString();` — the real,
  present production value for the `IN_APP` row's own `recipient` column is the account's own UUID
  string. Closed by adding the symmetric assertion: the `IN_APP` row's `recipient` must equal
  `accountUuid.toString()`.

## No other gap found

Checked specifically for the kind of gap T15/T16's own Phase 10 audits looked for — an unexercised
branch in the checking mechanism itself, not the production code. There isn't a comparable "checking
mechanism" here beyond the two JDBC helper methods (`processedEventExists`, `deliveryLogRowsFor`),
both simple, direct `SELECT`s with no branching logic of their own to leave untested. The one real
asymmetry (IN_APP recipient) is closed above; the task's own deliberately bounded scope (no
email-content assertion, no `inapp_notifications` row assertion — Phase 7's own disclosure) stands
unchanged, for the same reasons already given there.

## Verification

- `mvn -pl services/notification test -Dtest=VerifyEmailRedeliveryIntegrationTest` — 1/1, 0
  failures (run multiple times for stability; the shared-broker backlog exception from Phase 9's
  own confirmed root cause reappeared once more, harmlessly, exactly as expected).
- `mvn -pl services/notification clean verify` — 369 tests, 0 failures, 0 errors (unchanged count —
  this phase only strengthened an existing assertion, added no new test method). No production code
  was modified in this phase.
