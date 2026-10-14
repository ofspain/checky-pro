# payment · T09 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T09 — Payment state machine |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 | All 10 permitted edges succeed and return the target | `PaymentStateMachine.PERMITTED_TRANSITIONS` (static `EnumMap`) | 10 named tests (`shouldTransitionCreatedToWatchingOnWatchAck` … `shouldTransitionAttestedToHeldOnPostAttestReorg`), each mutation-verified in Phase 10 |
| AC2 | Every other ordered pair (39/49) throws, never no-ops; `HELD` terminal | Same map — `HELD → ∅` | `shouldThrowForEveryPairNotInThePermittedEdgeSet` (exhaustive, count-guarded at 39), mutation-verified against both a dropped edge and an added illegal edge |
| AC3 | `shouldPreventCrossModuleEntityImports` (L1/L12) | New ArchUnit rule in `ArchitectureTest` (`FEATURE_MODULES`, dependency-based condition) | 3 tests: real-code canary, genuine-violation canary (`RogueInvoiceEntityReferencer`), fail-fast canary (`RogueUnmappedEntity`) — all passing |
| AC4 | Null arguments throw `NullPointerException` | `Objects.requireNonNull` in `isPermitted`, inherited by `transition` | 4 named tests, mutation-verified (both `requireNonNull` calls removed together, confirmed all 4 catch it) |
| AC5 | Stateless, zero-arg, Spring-managed, public | `PaymentStateMachine` — one static field, implicit no-arg public constructor, `@Component` | `shouldHaveNoInstanceFieldsAndAZeroArgConstructorAndRemainSpringManaged`, mutation-verified against a dropped `@Component` and a package-private class |
| — (Phase 9 Finding 7) | `IllegalPaymentStateTransitionException` must be public | `public class IllegalPaymentStateTransitionException` | `illegalPaymentStateTransitionExceptionShouldBePublic`, mutation-verified |
| — (Phase 11 Gap 1) | `PaymentState` must never drift from `chk_payment_state` | N/A (a guard, not a feature) | `shouldMatchChkPaymentStateConstraintExactly`, mutation-verified against a mutated constraint |

## Required Tests (frozen brief)

All named/implied tests present: `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`
(the literal method name; the frozen brief cites the package.md §8 name
`shouldPreventCrossModuleEntityImports`, which this canary method directly checks), the 10 edge
tests, the one exhaustive illegal-pair test, 4 null tests, the AC5 reflection test (plus its
Phase 11 extensions), the two ArchUnit genuine-violation/fail-fast canaries. 18 tests in
`PaymentStateMachineTest`, 5 in `ArchitectureTest` (2 pre-existing + 3 new) — 21 net-new tests
across this task's own two files, all passing.

## Scope discipline

`git diff --stat 0f13e643..HEAD -- services/payment` (T08's own final commit to this task's own
HEAD) touches exactly 7 files — all named in the frozen brief's own Files to Create/Modify list,
with zero deviation: `PaymentState.java`, `PaymentStateMachine.java`,
`IllegalPaymentStateTransitionException.java`, `PaymentStateMachineTest.java`,
`RogueInvoiceEntityReferencer.java`, `RogueUnmappedEntity.java` (all created, as planned), and
`ArchitectureTest.java` (modified, as planned). No migration touched. Nothing under `spec/`. No
`Payment` entity/repository/service built (confirmed out of scope at Phase 1/2/4, held).

## Locked decisions

- **L1** — the 10-edge graph, finalized at Phase 4 via a genuine Human Approval decision (narrow
  `HELD` reading). Held — confirmed by direct trace of `PERMITTED_TRANSITIONS` against the
  frozen brief's own list, edge-for-edge.
- **L2** — `ATTESTED` reachable only via `FINALIZED → ATTESTED`. Held — no other edge targets
  `ATTESTED` in the map.
- **L11** — `ATTESTED → HELD` is the sole post-attestation path. Held.
- **L12** — module boundaries. Held — confirmed both by direct import-list reads (Phase 7) and
  by the new, working ArchUnit rule itself (Phase 6/8/11).

## Open items carried forward (not blockers for this task)

- **`InvoiceRepositoryIntegrationTest` (T08)** — confirmed broken via Kimi's own Docker-enabled
  Phase 8 run this task (`TransactionRequiredException`, a test-file bug calling a `@Modifying`
  repository method outside a transaction). Disclosed at Phase 9, re-acknowledged at Phase 11,
  logged in memory. Out of T09's own scope; needs a dedicated fix (add `@Transactional` to the
  test class/methods) before the full `mvn -pl services/payment test` run is green again.
- **`InvoiceState` has no equivalent `chk_invoice_state`-drift guard** (Phase 11 Gap 1's own
  disclosed asymmetry) — a worthwhile, separately-scoped future consistency task, not built here.
- **`Payment`/`PaymentRepository`/`PaymentService`** — `design.md` §6's own package map lists them
  alongside this task's own files; confirmed at Phase 1/2/4 to be task 11's own scope, not built
  here. Task 11 is the natural next task that will need to resolve the interface-shape question
  this task's own `PaymentStateMachine` leaves open for its future caller (state-pair keyed, not
  event-type keyed — a deliberate choice, not an oversight).
- **Two Testcontainers-backed classes** (`SchemaBaselineMigrationIntegrationTest`,
  `InvoiceRepositoryIntegrationTest`) remain unrunnable locally this session — Docker
  unavailable — same disclosed, carried-forward gap as every prior task.

## Test suite status

183/183 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest'`, up from 165
at the start of this task). `InvoiceRepositoryIntegrationTest` itself was run this task only via
Kimi's own Docker-enabled Phase 8 environment, which confirmed its 2 real failures (T08's own
bug, not this task's).

## Verdict rationale

All 5 acceptance criteria (plus 2 review-driven additions) are implemented and test-covered.
Two full Kimi review passes (Phase 3 design challenge, Phase 8 independent review, Phase 11 test
review) together surfaced one genuine architectural decision (resolved via a real
`AskUserQuestion`, not a default), one real planning error caught and fixed during
implementation itself (the missing ArchUnit rule, and the `events`-module gap found by running
it against real code), and a handful of real test-coverage refinements — one of which (Gap 4)
was genuinely rejected with a concrete technical rationale rather than rubber-stamped. All 4
LOCKED decisions in scope (L1, L2, L11, L12) held. No file outside this task's own disclosed
scope touched. The carried-forward items above are accepted, named limitations/follow-ups, not
defects of this task's own output. **PASS.** Proceeding to Phase 13.
