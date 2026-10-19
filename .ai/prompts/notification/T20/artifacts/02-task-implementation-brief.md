# notification · T20 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`. This artifact contains no code; it is documentation only.

## Task

Bump `spec/notification-service/package.md` to `READY FOR IMPL` / `0.2`, after recording each §11 question's resolution against the shipped code and ticking (or marking partial) each §9 item with evidence. **The baseline below assumes OQ1(a), bump with disclosed partials, pending Phase 4.**

## Purpose

- **Unblock the spec.** It has been `DRAFT` since authoring, although all 19 implementation tasks are done. The status misleads readers, and the frontend and payment specs reference it.
- **Record the open questions.** §11 still lists Q1–Q7 as open, even though the code has settled each one.

## Scope

**In (baseline):**
1. **Header.** Version `0.2`. Status `READY FOR IMPL`, with a short note of the disclosed gaps, in the payment T29 form.
2. **§11.** A dated **Resolved** note for each of Q1–Q7. Each cites its implementing task and code. Q4 and Q7 state their partial or deviating parts:
   - **Q4:** the base URL is decided; the link paths are provisional; there is no receipt link (FU-T07-2).
   - **Q7:** merchant only (T07 D1); the routing projection (D2); `account.suspended` is unmapped.
3. **§9.** Each item is ticked with test citations, or left `[ ] **Partial —**` with a reason. Expected partials:
   - item 1 (R5 partial; R15's name, OQ3)
   - item 2 (the L2 interpretation)
   - item 3 (the §4c recipients)
4. **Guard tests in `T01SkeletonRegressionTest`:**
   - the header bump, read from the header table only
   - every cited test exists
   - the partial items stay unticked

**Conditional on Phase 4:**

| Item | Question |
|---|---|
| Annotations in `design.md` (L2, §4c, O1/O2/O4/O5) and `requirements.md` (R3–R5) | OQ2 |
| The R15 named test | OQ3 |
| The T17 payment-suppression E2E test | OQ4 |

**Out:**
- `spec/` packages other than notification
- production code
- the follow-ups FU-T07-1/2/4 and payment FU-T29-1

## Business Rules

T20-1 to T20-3 (Phase 1).

## Locked Decisions

- **No silent deviation (§9 item 2).** Every deviation is written down where a reader of `package.md` sees it.
- **§4c is VERBATIM.** It is never paraphrased in place.

## Dependencies

- **Evidence:** T01–T19 and T07 artifacts and code, and the current suite (413/413).
- **Precedent:** payment T29's `package.md` edit and its guard tests.

## Inputs and Outputs

- **Inputs:** the current `package.md` and the code.
- **Outputs:**
  - an edited `package.md` (plus `design.md` / `requirements.md` if OQ2 is (b) or (c))
  - new guard tests

## State Changes

None at runtime.

## Files to Modify

| File | Change |
|---|---|
| `spec/notification-service/package.md` | Header, §9, §11 |
| `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java` | Guard tests |

## Files NOT to Modify

- `services/notification/src/main/**`
- other services and specs
- `contracts/`
- the untracked `prompt` file

## Acceptance Criteria

AC1–AC6 (Phase 1).

## Required Tests

- `notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo`
- `everyTestNamedInTheVerificationChecklistExists`
- `verificationChecklistKeepsItsPartialItemsUnticked`
- the full suite green

## Constraints

- **Honesty.** No tick without evidence. Wherever a status claim is partial, the partial wording travels with it.
- **Scope.** Spec edits stay within what Phase 4 authorizes.

## Open Questions

OQ1–OQ5 from Phase 1. Recommendations:

| # | Recommendation |
|---|---|
| OQ1 | (a) Bump with disclosed partials. This matches the payment T29 precedent, and the remaining gaps are owned by tracked follow-ups. |
| OQ2 | (b) Dated annotations, never rewriting original sentences. §4c stays VERBATIM, with a note below it. |
| OQ3 | Add the literal named test as an aggregate R15 check over a rendered body and a log line. Small, and it closes §9 item 1's naming gap. |
| OQ4 | Build the PAYMENT email opt-out → `SUPPRESSED` E2E test (test-only); it is buildable today. |
| OQ5 | Cite as is, with the canary noted. |
