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

## Addendum (post Phase 11) — 5 gaps raised, 1 closed, 4 already-correct dispositions

Kimi's Phase 11 review raised 5 gaps. Every factual claim was checked directly against source
before disposition — all held up accurately, no citation errors.

- **Gap 1** (Kimi's own sandbox lacks Maven, so it could not itself confirm the "369 tests, 0
  failures" claim) — **re-confirmed, not a real gap**: `mvn -pl services/notification clean verify`
  was actually run, with real tool output, at every phase since Phase 6. Re-run once more, fresh,
  immediately upon receiving this finding: `Tests run: 369, Failures: 0, Errors: 0` (full
  aggregate), `Tests run: 1, Failures: 0` (this test alone, 8.9s) — no remaining uncertainty.
- **Gap 2** (captured email content and the `inapp_notifications` row are intentionally not
  asserted) — **re-confirmed as the same, already-disclosed scope choice** this phase's own main
  text and Phase 7's self-review already cover; Kimi's own assessment agrees with the disposition
  already given (accept, no fix — that coverage belongs to `TemplateRendererIntegrationTest`/
  `InAppChannelIntegrationTest`).
- **Gap 3** (the captured email's own `to()` field was never directly asserted) — **ACCEPTED and
  fixed**: verified `DeliveryOrchestratorIntegrationTest.dispatchCapturesARealSentEmailWithCorrectFields`
  (`:399`) already does this for its own non-Kafka scenario; added the same `to()` assertion here,
  against the real event's own email address (`e2e@example.com`). Re-verified: still passes.
- **Gap 4** (no test for a *different* key producing a new, independent delivery) — **ACCEPTED as
  correctly assessed, no change**: the first-delivery half of this same test already is that proof
  (a never-before-seen key producing a real delivery); a dedicated second-account scenario would
  re-prove the same mechanism, not close a real gap. Matches R7/R8's own scope, which this task's
  own AC1/AC2 already cover.
- **Gap 5** (shared local Kafka broker state can affect repeated local runs) — **already addressed
  in Phase 9's own resolution** (Finding 5), with the exact root cause confirmed (a Jackson
  `JsonParseException` from a historical malformed-message backlog). Kimi's own assessment matches
  that disposition exactly.

**Verification:** `mvn -pl services/notification clean verify` — 369 tests, 0 failures, 0 errors
(unchanged count from before this addendum — only the EMAIL row's captured `to()` field gained a
new assertion within the existing test method).
