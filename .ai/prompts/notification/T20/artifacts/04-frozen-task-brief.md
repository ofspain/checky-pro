# notification · T20 · Phase 4 — Frozen Task Brief

Consumes:
- `artifacts/02-task-implementation-brief.md`
- `artifacts/03-design-challenge.md` (Kimi, commit `68cc991f`)

This artifact contains no code; it is documentation only. Once frozen, this brief is the only specification Phases 5–13 use.

## Human decisions (2026-10-19)

| # | Decision |
|---|---|
| D1 (OQ1, Kimi F1/F3) | **The spec edit is approved, and the spec is bumped with disclosed partials.** T20 may edit `spec/notification-service/`. `tasks.md` task 20 assigns the status edit to this task, mirroring payment T29. **Version** is `0.2`. **Status** is `READY FOR IMPL`, followed by a disclosure naming the partial §9 items and their owners (FU-T07-1/2/4, payment FU-T29-1). |
| D2 (OQ2, Kimi F8) | **Dated annotations only.** `design.md` gets dated notes: the L2 interpretation (T07 D2), a §4c recipient note **below** the verbatim block, and O1, O2, O4 and O5 marked resolved. `requirements.md` gets dated partial notes on R3–R5. No original sentence is rewritten, and §4c's verbatim content stays byte-identical (pinned by a test). |
| D3 (OQ3, OQ4, OQ5) | **The R15 test and the suppression E2E are added; L11 is cited as it is.** New `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` (R15). A new payment-event E2E: PAYMENT email opt-out → `SUPPRESSED`, with in-app still delivered (T17's buildable half). L11 is cited as the rule field `shouldPreventCrossModuleEntityImports` plus its canary (the Surefire workaround). |
| D4 (Kimi F2, F4–F7) | **F2, F4 and F6 accepted; F5 and F7 rejected.** See below. |

**Accepted:**
- **F2.** The guards parse the header table (the text before `## 0.`), never fixed line numbers.
- **F4.** The §9 partial notes say what the two drifted named tests actually prove. `shouldNotifyBothPartiesOnInvoiceCreated` proves merchant-only delivery. `shouldNotifyWithReceiptLinkOnReceiptIssued` proves `receiptId` is present and no link is rendered.
- **F6.** The §11 resolutions cite real classes. Kimi's wrong names are corrected: `InappStreamController` (not `InAppNotificationStream`), `EmailProperties` / `SesEmailTransport` (not `EmailTransportProperties`), and `RetryScheduler` (not `DeliveryRetryScheduler`).

**Rejected:**
- **F5.** Its premise is wrong: the test doesn't exist at all, rather than covering too little. It is superseded by D3.
- **F7.** `tasks.md` has no status or checkbox field for any task, and payment T29 didn't add one.

## Task

Close out the notification spec:
1. Record each §11 question's resolution against the shipped code.
2. Tick each §9 item with evidence, or mark it partial.
3. Add the two decided tests.
4. Annotate the deviations in `design.md` and `requirements.md`.
5. Bump `package.md` to `0.2` / `READY FOR IMPL`, with disclosure.

## Scope

**In:**

1. **`package.md` header.** Version `0.2`. Status `READY FOR IMPL`, with the disclosure (D1). No other header row changes.
2. **`package.md` §11.** A dated **Resolved (2026-10-19)** note is appended to each of Q1–Q7, citing the classes (F6).
   - Q4 and Q7 state their partial or deviating parts.
   - The original question text stays.
3. **`package.md` §9.** Each item is either `[x]` with test citations, or `[ ] **Partial —**` with a reason that includes F4's drift wording. Expected partials:
   - item 1: R3–R5 are partial (merchant only, no link)
   - item 2: the L2 interpretation
   - item 3: the §4c recipients

   The final set is decided in Phase 6 by evidence.
4. **`design.md`.** Dated annotations:
   - after L2, the T07 D2 interpretation
   - below the §4c verbatim block, a recipient note: merchant only; payer deferred to FU-T07-1; no receipt link, FU-T07-2; `account.suspended` unmapped
   - O1, O2, O4 and O5 marked resolved (O3 already is)
5. **`requirements.md`.** Dated partial notes after R3, R4 and R5.
6. **Tests:**
   - `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs`: a real rendered password-reset body and a captured log line; the raw token appears only inside the intended one-time link and never in a log line.
   - a payment-event suppression E2E
   - in `T01SkeletonRegressionTest`, guards for:
     - the header bump (header table only)
     - every test cited in §9 exists
     - the partial items stay unticked
     - §4c is byte-identical, through a SHA-256 of the block taken before the edit

**Out:**
- production code
- other specs, `contracts/` and other services
- the follow-ups themselves
- `tasks.md`

## Business Rules

- **T20-1:** the questions are closed (with disclosed partials, D1).
- **T20-2:** tests pass.
- **T20-3:** the bump.

## Locked Decisions

- §9 item 2's "no silent deviation".
- §4c VERBATIM: byte-identical, with notes below it only.
- `agents.md` is referenced, not restated.

## Acceptance Criteria

| AC | Criterion |
|---|---|
| AC1 | The header table holds exactly one Version row (`0.2`) and one Status row starting with `READY FOR IMPL`, carrying the disclosure. |
| AC2 | Q1–Q7 each have a dated resolution with real class citations. Q4 and Q7 state what is partial. |
| AC3 | Every §9 item is ticked with existing tests, or marked `[ ] **Partial —**`. No false tick. |
| AC4 | `design.md` / `requirements.md` gain only dated annotations, and §4c is byte-identical. |
| AC5 | Both new tests pass. The R15 test fails if the token leaks into a log line or appears outside the link. The E2E test fails if PAYMENT email isn't suppressed or in-app isn't delivered. |
| AC6 | The guard tests pass, and the full suite is green. |
| AC7 | No change outside `spec/notification-service/` and `services/notification/src/test/`. |

## Required Tests

- `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs`
- the payment-event suppression E2E (name decided in Phase 5)
- `notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo`
- `everyTestNamedInTheVerificationChecklistExists`
- `verificationChecklistKeepsItsPartialItemsUnticked`
- `verbatimSection4cIsUnchanged`

## Constraints

- **Honesty.** No tick without evidence.
- **Scope.** No production code. No `@ArchTest` (the Surefire bug).
- **Isolation.** The new integration test uses its own Kafka container if it needs Kafka (the T07 pattern).

## Open Questions

None.
