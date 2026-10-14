# payment · T09 · Phase 9 — Review Resolution

Human Approval gate decision not required — none of Kimi's Phase 8 findings is a design
trade-off; each has an obvious disposition (5 positive confirmations, 2 test-plan refinements
expected at this phase, 1 out-of-scope finding to disclose and defer). Recorded here for the
audit trail.

## Findings 1-5 — positive confirmations

**ACKNOWLEDGED, no action.** Kimi independently confirmed the 10-edge graph, the shared
`transition`/`isPermitted` lookup, null handling, the public exception, and the new ArchUnit rule
all match the frozen brief exactly.

## Finding 6 — `PaymentStateMachineTest.java` missing

**ACKNOWLEDGED, no action needed — expected at this phase.** Kimi's own severity call ("Medium —
expected at this phase") is correct: Phase 8 (independent code review) runs before Phase 10 (test
generation) by design. The frozen brief's own Required Tests list is what Phase 10 will produce.

## Finding 7 — AC5's own test won't prove `@Component` survives a future refactor

**ACCEPTED.** A real, cheap gap: zero-fields/zero-constructor-params alone doesn't guard against
someone later dropping the `@Component` stereotype itself (breaking future DI at a call site
tasks 11/12/17/19 would add). Folded into Phase 10's own test plan: the AC5 reflection test will
also assert `PaymentStateMachine.class.isAnnotationPresent(Component.class)`.

## Finding 8 — `InvoiceRepositoryIntegrationTest` (T08) fails with `TransactionRequiredException`

**ACKNOWLEDGED as valid and high-value — explicitly NOT fixed in this task.** This is squarely a
T08 test-file bug (`InvoiceRepositoryIntegrationTest.updateStateIfCurrentStateActuallyUpdates...`/
`...ReturnsZeroAndLeavesRowUnchangedWhenNoLongerOpen` call the `@Modifying` repository method
`updateStateIfCurrentState` directly, outside any transaction — `@Modifying` methods require an
active transaction to execute at all), not a T09 production defect, and not something any T09
acceptance criterion depends on. Mirrors this session's own established precedent (the
`AuthorizationDeniedException` bug found during T06, logged as a flagged follow-up for a
*different* file rather than retroactively "fixed" inside the task that happened to find it) —
guardrails are explicit: "Work ONLY on T09... No scope beyond this task." Genuinely valuable,
though: this is the **first real empirical confirmation**, via Kimi's own Docker-enabled run, that
`InvoiceRepositoryIntegrationTest` (written in T08 Phase 11, never runnable locally this session)
has a real bug — previously only a disclosed "could not verify, Docker unavailable" gap, now a
confirmed defect. **Flagged to the user directly** (not buried) and recorded in memory as a
tracked follow-up; not actioned here.

## Verification performed this phase

- No code change this phase (all 5 confirmations needed none; findings 6/7 are Phase 10 test-plan
  items; finding 8 is out of scope).
- Full non-Docker suite unchanged from Phase 7: 165/165.

## Net result

0 code defects in T09's own scope. 1 test-plan refinement folded into Phase 10 (the `@Component`
assertion). 1 real, out-of-scope defect in a different task's file disclosed to the user and
logged as a follow-up, not fixed here. Proceeding to Phase 10.
