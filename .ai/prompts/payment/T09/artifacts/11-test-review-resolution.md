# payment · T09 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). 3 of 5 findings fixed, 1 rejected with direct
rationale (first rejection in this task), 1 acknowledged as already-logged and out of scope.

## Gap 1 — No guard against `PaymentState` drifting from `chk_payment_state`

**ACCEPTED and FIXED.** Added `shouldMatchChkPaymentStateConstraintExactly` — reads the real
`V1__payments_baseline.sql`, regex-extracts the `chk_payment_state` constraint's own value list,
and asserts it's an exact set-match against `PaymentState.values()`. Mutation-verified: changed
`'HELD'` to `'HELDXX'` in the SQL file, confirmed red, reverted via `Edit`. `InvoiceState` has no
equivalent guard — a worthwhile, separately-scoped future consistency task, not retroactively
added here.

## Gap 2 — No visibility guard on `PaymentStateMachine`/its constructor

**ACCEPTED and FIXED.** Extended the existing AC5 test with
`Modifier.isPublic(PaymentStateMachine.class.getModifiers())` and the same for the zero-arg
constructor. Mutation-verified: made the class package-private, confirmed red, reverted.

## Gap 3 — No visibility guard on `IllegalPaymentStateTransitionException`

**ACCEPTED and FIXED.** Added `illegalPaymentStateTransitionExceptionShouldBePublic`.
Mutation-verified: made the class package-private, confirmed red, reverted.

## Gap 4 — No reflection-based structural proof of `PERMITTED_TRANSITIONS`'s own cardinality

**REJECTED, with direct rationale — the first rejection in this task.** The suggested test would
read the private `PERMITTED_TRANSITIONS` field via reflection and assert "7 keys, 10 total target
entries." This adds no real detection power beyond what
`shouldThrowForEveryPairNotInThePermittedEdgeSet` already provides: that test is already
exhaustive over the complete domain (all 49 `PaymentState × PaymentState` pairs, a closed 7-value
enum), so it already fully characterizes the function `PERMITTED_TRANSITIONS` represents. A
"duplicate" target entry is structurally impossible (`EnumSet`), and an "unexpected eighth source
state" is unreachable via any public API (the enum itself is closed to 7 values) — there is no
real production mistake this reflection check would catch that the exhaustive behavioral test
would not. Adding it would instead introduce a brittle coupling to a private implementation
detail (field name/type) that could break on a behavior-preserving internal refactor (e.g.
switching the internal representation to a `Set<record Edge(...)>`) with zero actual regression —
the opposite of what a good regression test should do. Not added.

## Gap 5 — `InvoiceRepositoryIntegrationTest` (T08) still broken

**ACKNOWLEDGED, no new action.** Already disclosed at Phase 9 and logged in memory
(`payment-service-spec-progress.md`) as a tracked follow-up; Kimi's own re-confirmation here adds
no new information. Remains out of T09's own scope per the same established precedent.

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='PaymentStateMachineTest'` — 18/18 (up from 16).
- Mutation-verified all 3 fixed gaps (table below), each via `Edit`, confirmed red, reverted.
- Full non-Docker suite: **183/183 passing** (up from 181).

| Mutation | Test that caught it |
|---|---|
| `'HELD'` → `'HELDXX'` in `V1`'s own `chk_payment_state` | `shouldMatchChkPaymentStateConstraintExactly` |
| `IllegalPaymentStateTransitionException` made package-private | `illegalPaymentStateTransitionExceptionShouldBePublic` |
| `PaymentStateMachine` made package-private | `shouldHaveNoInstanceFieldsAndAZeroArgConstructorAndRemainSpringManaged` |

## Net result

3 real gaps fixed, 1 genuinely rejected (not rubber-stamped) with a concrete technical rationale,
1 re-acknowledged as already out of scope. Proceeding to Phase 12.
