# payment · T09 · Phase 10 — Test Generation

Created `PaymentStateMachineTest.java` (16 tests) per the frozen brief, folding in Phase 8
(Kimi) Finding 7.

## Tests added

- **10 named tests for AC1** (the 10 permitted edges), each a direct, literal
  `transition`/`isPermitted` assertion — matching the frozen brief's own Required Tests list
  verbatim.
- **1 exhaustive test for AC2** (`shouldThrowForEveryPairNotInThePermittedEdgeSet`) — iterates
  all 49 ordered `PaymentState × PaymentState` pairs via an independently-restated,
  test-owned `LEGAL_EDGES` map (not a reference to production's own private
  `PERMITTED_TRANSITIONS` field — two independent expressions of the same documented fact catch
  more than one proof plus a derived check), asserts the 39 non-edges each throw
  `IllegalPaymentStateTransitionException` carrying the correct `current`/`target` and report
  `false` via `isPermitted`. Explicitly covers `(HELD, HELD)` and every `(HELD, x≠HELD)` pair
  (Finding 4) since `LEGAL_EDGES` maps `HELD` to an empty set. A final count assertion
  (`illegalPairsChecked == 39`) guards against the loop itself silently checking fewer pairs than
  intended.
- **4 null-argument tests for AC4** (`transition`/`isPermitted` × `current`/`target`).
- **1 reflection-based test for AC5**, corrected from the frozen brief's own literal "zero
  declared fields" wording: reads as "zero **instance** fields" (already Phase 7's own
  self-review reading) — `PERMITTED_TRANSITIONS` is a `private static final` immutable lookup
  table, not per-instance state, so its existence doesn't violate "stateless." Also folds in
  Phase 8 (Kimi) Finding 7: asserts `@Component` is still present, so a future refactor can't
  silently drop Spring-bean eligibility while this test keeps passing.

## Mutation verification performed this phase

Each mutation introduced via `Edit` (never `git checkout --`), confirmed red, reverted via `Edit`:

| Mutation | Test(s) that caught it |
|---|---|
| Removed `SEEN → FINALIZED` from production | `shouldTransitionSeenDirectlyToFinalizedWhenConfirmedNeverArrived` + the exhaustive test |
| Added an illegal `HELD → CREATED` edge to production | `shouldThrowForEveryPairNotInThePermittedEdgeSet` |
| Removed `@Component` from `PaymentStateMachine` | `shouldHaveNoInstanceFieldsAndAZeroArgConstructorAndRemainSpringManaged` |
| Removed both `Objects.requireNonNull` calls from `isPermitted` | all 4 null-argument tests |

## Verification performed this phase

- `mvn -pl services/payment -am test-compile` — clean.
- `mvn -pl services/payment -am test -Dtest='PaymentStateMachineTest'` — 16/16.
- Full non-Docker suite: `mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest,!InvoiceRepositoryIntegrationTest'` —
  **181/181 passing** (165 carried forward + 16 new).

## Net result

All of the frozen brief's own Required Tests are present and mutation-verified, plus Phase 8
Finding 7's own refinement. `InvoiceRepositoryIntegrationTest`'s pre-existing, out-of-scope
failure (T08, disclosed at Phase 9) is unaffected by this task and remains excluded from the
suite run above, same as every prior phase this task. Proceeding to Phase 11 — per the Kimi
handoff/wait discipline, this is Kimi 2.7's job; stopping here to hand off.
