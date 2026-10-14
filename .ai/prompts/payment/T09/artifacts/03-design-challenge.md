# payment · T09 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T09/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md` §4c, `spec/payment-service/requirements.md` R6/R7/R9/R10/R11/R12/R20/R24, `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`.

---

## 1. The 10-edge set narrows `any -> HELD` to `FINALIZED -> HELD` and `ATTESTED -> HELD`, conflicting with `design.md`

- **Issue:** `design.md` §4c's verbatim state-graph comment lists `any -> HELD` ("attest BLOCKED, or post-attest reorg"). The brief locks exactly 10 directed edges and lists only `FINALIZED -> HELD` and `ATTESTED -> HELD`. The rationale is defensible in practice — attest is only called from `FINALIZED` (L2) and post-attest reorg only from `ATTESTED` — but the brief does not reconcile its narrower reading with the `design.md` text. If a future caller needs to move a `SEEN` or `CONFIRMING` payment to `HELD` (e.g., a compliance signal that arrives before finality), the state machine will throw.
- **Severity:** High — a direct LOCKED-decision conflict; whichever reading is chosen must be explicit and traceable.
- **Evidence:**
  - `spec/payment-service/design.md:104-117` — `// any -> HELD (attest BLOCKED, or post-attest reorg — ops-alerted)`
  - `artifacts/02-task-implementation-brief.md:60-68` — lists 10 edges, only two targeting `HELD`
  - `artifacts/02-task-implementation-brief.md:55-58` — R20 says `FINALIZED -> HELD` on `BLOCKED`; R24 says `FINALIZED -> ATTESTED`
- **Recommended brief amendment:** Either (a) add `any -> HELD` to the legal-edge set to match `design.md` verbatim, or (b) explicitly document the narrowing with a rationale ("attest is only invoked from FINALIZED; post-attest reorg only from ATTESTED; therefore all other -> HELD edges are intentionally excluded") and update AC1/AC2/tests accordingly.
- **Confidence:** High.

---

## 2. `shouldPreventCrossModuleEntityImports` does not exist in `ArchitectureTest`

- **Issue:** AC3 requires `shouldPreventCrossModuleEntityImports` to pass with the new `payment` package included. The brief says the existing `ArchitectureTest` "already iterates `com.themistra.payment..` broadly per T05's own design, so this is very likely zero or near-zero structural change." In reality, `ArchitectureTest` currently contains only the floating-point-money rule; there is no cross-module-entity-import rule at all.
- **Severity:** High — AC3 cannot be satisfied without writing the rule first.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` (entire file) — no `shouldPreventCrossModuleEntityImports` test or rule
- **Recommended brief amendment:** Change AC3 and the "Files to Modify" section to require adding a new ArchUnit rule (and test canary) that forbids classes in one feature package from importing entities from another feature package, explicitly scoped to include `com.themistra.payment.payment`.
- **Confidence:** High.

---

## 3. `@Component` for pure, dependency-free logic adds unnecessary Spring coupling

- **Issue:** The brief makes `PaymentStateMachine` a stateless Spring `@Component` to mirror `AddressValidator`. Because the class has no dependencies and no mutable state, it could be a utility class with static `transition`/`isPermitted` methods. Using `@Component` means future unit tests can still instantiate it directly, but it also implies a Spring lifecycle and invites future developers to add injected dependencies, undermining the "pure logic" intent.
- **Severity:** Low — a style/scope-consistency concern, not a defect.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:21-23, 132-134` — `@Component`, no field, no constructor parameter
  - `artifacts/02-task-implementation-brief.md:150-152` — thread-safety note relies on the class staying stateless
- **Recommended brief amendment:** Either keep `@Component` and add a comment/test guarding against future field injection, or switch to a `final` utility class with private constructor and static methods. If `@Component` is kept, add an ArchUnit/source-text guard that the class has no fields and no constructor parameters.
- **Confidence:** Low.

---

## 4. No guard against `HELD` being a source state

- **Issue:** The brief's 10-edge set does not list any transition whose source is `HELD`. The state machine should therefore throw for every `(HELD, *)` pair, including `(HELD, HELD)`. This is consistent with `HELD` being a terminal/ops-alerted state, but the brief does not explicitly state that `HELD` is terminal. A future task that needs to recover a payment from `HELD` would require a new locked decision and a state-machine change.
- **Severity:** Low — mostly a documentation gap.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:60-68` — no edge starts at `HELD`
- **Recommended brief amendment:** Explicitly state that `HELD` is terminal in this task's scope: no transitions out of `HELD` are permitted, and any future recovery path requires a new locked decision.
- **Confidence:** Medium.

---

## Positive confirmations

- The 7-state enum matches the `chk_payment_state` DB constraint exactly.
- The `transition`/`isPermitted` API split is clean: callers can query permissibility before invoking the throwing transition.
- Null handling via `Objects.requireNonNull` matches the service's established convention.
- R14 stale-event handling is correctly left to the caller.
- The exhaustive parameterized test plan for AC2 is the right shape.
