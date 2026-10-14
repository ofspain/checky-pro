# payment · T09 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T09: payment state machine (PaymentStateMachine)`

## Commit message

```
payment-service T09: payment state machine (PaymentStateMachine)

Adds PaymentState (7-value enum, matches chk_payment_state exactly) and
PaymentStateMachine: a stateless @Component, pure logic, enforcing the
single centralized state graph L1 requires - transition(current, target)
throws IllegalPaymentStateTransitionException for any pair outside the
10 permitted edges; isPermitted(current, target) is a non-throwing query
so a future caller (task 11's consumer) can pre-check R14 staleness
rather than relying on exception-driven control flow for a routine,
expected case.

Resolved a real conflict between L1's own prose ("no other transition
may be added") and design.md's own verbatim §4c comment ("any -> HELD")
via a genuine Human Approval decision (Phase 4, AskUserQuestion, not a
default): HELD is reachable only from FINALIZED (R20, a BLOCKED attest)
and from ATTESTED (L11, a post-attest reorg) - no requirement backs any
other state reaching HELD, so HELD is terminal within this task's own
output. A future pre-finality compliance-hold path would need its own
new LOCKED decision.

Kimi's Phase 3 design challenge also found that ArchitectureTest had no
cross-module-entity-import rule at all (the Phase 2 brief wrongly assumed
a near-zero extension of an existing rule) - authored one from scratch,
mirroring crypto-service's own CrossModuleEntityArchitectureTest, with
two new fixtures for the genuine-violation and fail-fast canaries.
Running the new rule against real code surfaced a real gap the plan
missed: T04's own OutboxEvent entity needed its own module entry.

Two Kimi review passes (Phase 8, Phase 11) found zero production defects
and a handful of real test-coverage refinements - one (a redundant
reflection-based structural count-check, duplicating what the existing
exhaustive 49-pair behavioral test already fully covers) was genuinely
rejected with a concrete technical rationale, not rubber-stamped.

Also surfaced, disclosed, and deliberately NOT fixed here (out of this
task's own scope): InvoiceRepositoryIntegrationTest (T08) is confirmed
broken via Kimi's own Docker-enabled run - a test-file bug calling a
@Modifying repository method outside a transaction, logged as a tracked
follow-up.

Spec: spec/payment-service/tasks.md task 9. Requirements: R6, R7, R9,
R10, R11, R12, R14, R20, R24. Locked decisions: L1, L2, L11, L12.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentState.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java`
- `services/payment/src/main/java/com/themistra/payment/payment/IllegalPaymentStateTransitionException.java`
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentStateMachineTest.java`
- `services/payment/src/test/java/com/themistra/payment/payment/RogueInvoiceEntityReferencer.java`
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueUnmappedEntity.java`

**Modified**
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`

**Process artifacts (not application code)**
- `.ai/prompts/payment/T09/artifacts/00-*.md` through `12-*.md`.

## Summary

The payment verification state graph (L1) now has a single, pure-logic, centrally-enforced
source of truth that every future task (11, 12, 17, 19) will use rather than each independently
re-implementing "which transitions are legal." A real architectural ambiguity in the spec itself
(the `HELD`-edge scope) was resolved by a genuine product decision rather than assumed, and a
real planning gap (a missing ArchUnit rule the Phase 2 brief wrongly assumed already existed) was
caught and fixed before it could ship unenforced.

## Testing performed

- `mvn -pl services/payment -am compile` / `test-compile` — clean throughout.
- 21 new tests added this task (18 in `PaymentStateMachineTest`, 3 in `ArchitectureTest`).
- **183/183 non-Docker-dependent tests passing**, up from 165 before this task.
- Mutation tests across Phases 10/11 on: every one of the 10 legal edges (via two representative
  mutations — a dropped edge and an added illegal edge), null handling (both `requireNonNull`
  calls removed together), the `@Component` stereotype, both new public-visibility guards, and
  the `chk_payment_state`-drift guard — each caught its introduced defect, each reverted clean
  (via `Edit`, never `git checkout --`, since several of this task's own files had uncommitted
  changes at mutation time).

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 9 — "Payment state machine."
- **Requirement IDs:** R6, R7, R9, R10, R11, R12, R14, R20, R24.
- **LOCKED decisions:** L1, L2, L11, L12.

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T09.**
