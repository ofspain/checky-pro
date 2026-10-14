# payment · T09 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T09/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, current implementation.

---

## Executive summary

- T09 production code is complete and matches the frozen brief: the 10-edge graph is correctly encoded, `HELD` is terminal, null handling is present, the exception is public, and the new cross-module entity-import ArchUnit rule plus canaries are in place.
- `mvn -pl services/payment test` currently fails with **2 errors in `InvoiceRepositoryIntegrationTest` (T08)**, not in T09 code. That test calls a `@Modifying` repository method outside a transaction.
- `PaymentStateMachineTest.java` does not exist yet (Phase 10's scope), so AC1–AC5 are not yet under automated test.

---

## 1. `PaymentStateMachine` correctly implements the locked 10-edge graph

- **Issue:** Not a finding — positive confirmation. The `PERMITTED_TRANSITIONS` map contains exactly 10 edges: `CREATED→WATCHING`, `WATCHING→SEEN`, `SEEN→{CONFIRMING,FINALIZED,WATCHING}`, `CONFIRMING→{FINALIZED,SEEN}`, `FINALIZED→{ATTESTED,HELD}`, `ATTESTED→HELD`, `HELD→∅`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java:38-49`
- **Confidence:** High.

---

## 2. `transition`/`isPermitted` share the same lookup, preventing drift

- **Issue:** Not a finding — positive confirmation. `transition` delegates to `isPermitted`, so the legal/illegal views cannot diverge.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java:58-74`
- **Confidence:** High.

---

## 3. Null handling is correct and consistent

- **Issue:** Not a finding — positive confirmation. `isPermitted` uses `Objects.requireNonNull` on both arguments; `transition` routes through `isPermitted` before throwing its own exception.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java:58-74`
- **Confidence:** High.

---

## 4. `IllegalPaymentStateTransitionException` is public and carries context

- **Issue:** Not a finding — positive confirmation. The exception is `public`, stores `current`/`target`, and exposes getters. It imports nothing outside `java.lang`/`payment`.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/IllegalPaymentStateTransitionException.java`
- **Confidence:** High.

---

## 5. Cross-module entity-import ArchUnit rule is correctly implemented

- **Issue:** Not a finding — positive confirmation. `ArchitectureTest` now defines `FEATURE_MODULES = [invoice, payment, events]`, a dependency-based rule that only `@Entity` classes may not be accessed from a different feature module, and two canaries (genuine cross-module violation + unmapped entity fail-fast). The production scan uses `DoNotIncludeTests`, so test fixtures are excluded.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java:99-209`
  - `services/payment/src/test/java/com/themistra/payment/payment/RogueInvoiceEntityReferencer.java`
  - `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueUnmappedEntity.java`
- **Confidence:** High.

---

## 6. `PaymentStateMachineTest.java` is missing

- **Issue:** The production implementation exists but has no unit tests. AC1 (10 legal edges), AC2 (39 illegal pairs), AC4 (null arguments), and AC5 (zero fields/constructor) are all supposed to be proven by `PaymentStateMachineTest`, which is not present.
- **Severity:** Medium — expected at this phase, but it is the only unverified T09 surface.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/payment/PaymentStateMachineTest.java` does not exist
  - `artifacts/04-frozen-task-brief.md:180-194` lists required tests
- **Recommendation:** Create `PaymentStateMachineTest.java` in Phase 10 with the tests listed in the frozen brief.
- **Confidence:** High.

---

## 7. AC5 test will not prove `@Component` is present

- **Issue:** The required AC5 test checks zero declared fields and a zero-parameter constructor, but it does not verify the `@Component` stereotype is still on the class. A future refactor could drop `@Component` and the AC5 test would still pass, even though callers expecting dependency injection would break.
- **Severity:** Low — a structural-guard gap, not a runtime defect.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java:32` (`@Component`)
  - `artifacts/04-frozen-task-brief.md:177-178` (AC5 wording)
- **Recommendation:** Add a reflection assertion in `PaymentStateMachineTest` that `PaymentStateMachine.class.getAnnotation(Component.class)` is non-null (or that the class is annotated with a Spring stereotype).
- **Confidence:** High.

---

## 8. Build context — unrelated T08 integration test is failing

- **Issue:** `InvoiceRepositoryIntegrationTest` (added after T08's Phase 11 review) fails with `TransactionRequiredException` in two tests because it calls `InvoiceRepository.updateStateIfCurrentState` directly without an active transaction. This is a test bug, not a production defect (`InvoiceService.expireInvoice` is `@Transactional`). It is unrelated to T09 but makes the current full build red.
- **Severity:** Medium — CI is red; fix belongs to T08's test suite.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceRepositoryIntegrationTest.java:167-193`
  - `services/payment/target/surefire-reports/com.themistra.payment.invoice.InvoiceRepositoryIntegrationTest.txt`
- **Recommendation:** Add `@Transactional` to `InvoiceRepositoryIntegrationTest` (or to each test method), or call the repository through a transactional test helper. Verify the V6 UPDATE grant assertion still passes.
- **Confidence:** High.

---

## Positive confirmations

- The `HELD`-terminal decision is correctly encoded: `PERMITTED_TRANSITIONS.put(PaymentState.HELD, EnumSet.noneOf(...))` makes every `(HELD, x)` pair illegal.
- `PaymentState` enum matches the `chk_payment_state` constraint values exactly.
- `PaymentStateMachine` has zero instance fields and no constructor parameters, satisfying the stateless requirement.
- `RogueInvoiceEntityReferencer` is deliberately placed in test source under `payment/` and uses a declared-but-never-read field of type `Invoice` — the right shape for dependency-based ArchUnit checking.
- `FEATURE_MODULES` includes `events` to avoid tripping the fail-fast path against the already-shipped `OutboxEvent` entity, mirroring the sibling-service pattern.

---

## Recommended follow-up order

1. Create `PaymentStateMachineTest.java` in Phase 10.
2. Add the `@Component` reflection assertion to the AC5 test.
3. Fix the unrelated `InvoiceRepositoryIntegrationTest` transaction issue so the full build is green.
