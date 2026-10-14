# payment · T09 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T09/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `PaymentStateMachineTest.java`, `ArchitectureTest.java`.

---

## Executive summary

- `PaymentStateMachineTest` — **16/16 passing**. The test suite is strong: 10 named edge tests, one exhaustive parameterized-style test for the 39 illegal pairs with a count guard, 4 null-argument tests, and a reflection-based AC5 test that includes `@Component` presence.
- `mvn -pl services/payment test` — **205 tests run, 0 failures, 2 errors** — all errors are in the unrelated T08 `InvoiceRepositoryIntegrationTest` (missing transaction around a `@Modifying` call). T09 itself is green.
- Remaining gaps are minor structural/contract alignments.

---

## Gap 1 — No contract test aligning `PaymentState` enum names with the DB constraint

- **Why it matters:** `PaymentState` enum names are used directly in JPQL/native SQL string literals (e.g., `WHERE i.state = 'OPEN'`). If an enum name ever drifts from the `chk_payment_state` DB constraint values, queries would fail at runtime. The enum currently matches, but there is no automated guard.
- **Suggested test:** Add a test that asserts `Arrays.stream(PaymentState.values()).map(Enum::name).toSet()` equals the exact set of state names allowed by `chk_payment_state` in `V1__payments_baseline.sql` (or a hardcoded, documented set).

---

## Gap 2 — No structural test that `PaymentStateMachine` and its constructor are public

- **Why it matters:** Spring needs a public class with a public no-arg constructor to instantiate the bean. The AC5 test counts constructors and checks zero parameters, but it does not assert visibility. A future refactor could make the class or constructor package-private and break DI while the AC5 test still passes.
- **Suggested test:** Extend `shouldHaveNoInstanceFieldsAndAZeroArgConstructorAndRemainSpringManaged` to assert `Modifier.isPublic(PaymentStateMachine.class.getModifiers())` and `Modifier.isPublic(constructors[0].getModifiers())`.

---

## Gap 3 — No structural test that `IllegalPaymentStateTransitionException` is public

- **Why it matters:** The frozen brief requires the exception to be public so a future cross-package caller (task 11's `consumer` package) can catch it. There is no test guarding this visibility.
- **Suggested test:** Add a reflection test asserting `Modifier.isPublic(IllegalPaymentStateTransitionException.class.getModifiers())`.

---

## Gap 4 — No direct structural proof that production's edge set has exactly 10 entries

- **Why it matters:** The exhaustive illegal-pairs test uses an independent `LEGAL_EDGES` map and a count assertion (39 illegal pairs), which is the right end-to-end check. A separate, cheap structural test would catch a production map that accidentally contains duplicates or an unexpected eighth source state.
- **Suggested test:** Use reflection to read `PaymentStateMachine`'s private static `PERMITTED_TRANSITIONS` map and assert it has exactly 7 keys with 10 total target entries. This is a duplicate safety net, not a replacement for the exhaustive test.

---

## Gap 5 — `InvoiceRepositoryIntegrationTest` (T08) is still broken

- **Why it matters:** It is unrelated to T09, but it keeps the full build red. The two failing tests call `InvoiceRepository.updateStateIfCurrentState` outside a transaction, causing `TransactionRequiredException`.
- **Suggested test fix:** Add `@Transactional` to the test class or call the repository through a transactional helper. This is a T08 issue, not T09.

---

## Positive confirmations

- `LEGAL_EDGES` is independently restated in the test, not derived from production's private map — strong mutation resistance.
- The 39-pair count assertion prevents the exhaustive test from silently shrinking.
- `(HELD, HELD)` and every `(HELD, x≠HELD)` pair are explicitly covered because `LEGAL_EDGES` maps `HELD` to an empty set.
- The null-argument tests assert both the exception type and the parameter name in the message.
- The AC5 test closes the Phase 8 Finding 7 gap by asserting `@Component` is present.
- `ArchitectureTest` now has the cross-module entity-import rule and two working canaries.
- Mutation verification in Phase 10 shows the tests fail on real production regressions.
