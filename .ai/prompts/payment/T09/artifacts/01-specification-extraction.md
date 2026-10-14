# payment · T09 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T09 — Payment state machine |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R6** — the Crypto Service's watch-registration ack moves tracking from `CREATED` to `WATCHING`.
- **R7** — a `chain.tx.seen` event for a watched address moves the payment to `SEEN`.
- **R9** — a `chain.tx.confirmed` event for a `SEEN` payment moves it to `CONFIRMING` (confirmation-count recording itself is a `Payment` entity field, out of this pure-logic class's own scope).
- **R10** — a `chain.tx.finalized` event for a `CONFIRMING` **or** `SEEN` payment moves it to `FINALIZED` (finalization can legitimately arrive without an intervening `confirmed` event).
- **R11** — a `chain.tx.reorged` event for a `CONFIRMING` payment walks it back to `SEEN`.
- **R12** — a `chain.tx.reorged` event for a `SEEN` payment walks it back to `WATCHING`.
- **R14** — a stale/out-of-order event must be ignored without changing state, not raise an error that aborts processing. **Read as a caller-side (task 11) concern, not this class's own contract** — see Open Questions.
- **R20** — a `BLOCKED` attest outcome (task 17's own scope) moves the payment to `HELD` instead of issuing a receipt. The *transition itself* (`FINALIZED → HELD`) is this class's concern even though *calling* it is a later task's.
- **R24** (context only, not this task's own scope to drive) — `FINALIZED → ATTESTED` happens once the receipt is signed, stored, and ledger-appended (tasks 17/19). The *edge* belongs in the same fixed graph this class enforces, per `design.md` §4c, even though no caller exists yet.

## Locked Decisions

- **L1** — the governing decision. Fixed states: `CREATED, WATCHING, SEEN, CONFIRMING, FINALIZED, ATTESTED, HELD` (7, matching the real `chk_payment_state` DB constraint exactly, confirmed by direct read). "No other transition may be added" beyond the full set `design.md` §4c actually specifies (see the Phase 0 tension already flagged — carried forward, not re-litigated here; resolution proposed in Phase 2, confirmed in Phase 3/4).
- **L2** (context) — a receipt, and therefore `ATTESTED`, is reachable only via `FINALIZED`. No other state may reach `ATTESTED`.
- **L11** — reorg is a first-class transition. A reorg **after** `ATTESTED` is out-of-policy-scope and must move the payment to `HELD` (ops-alerted) rather than rewrite an attested record — this is the second, narrower source for a `→ HELD` edge, alongside R20's `FINALIZED → HELD`. **Neither R20 nor L11 supports a `→ HELD` edge from any other state** (`CREATED`/`WATCHING`/`SEEN`/`CONFIRMING`) — the §4c comment's own literal `any -> HELD` wording is read as imprecise shorthand for these two specific edges, not literally every state, since implementing a true "any → HELD" would itself violate L1's own "no other transition may be added" for the other five states with no requirement backing them.
- **L12** — module boundaries; `PaymentStateMachine` (and `PaymentState`) must import no entity from any other feature package, and `payment` itself must not be imported into by an entity it doesn't own.

## Files involved

**New (this task's own scope — see Open Questions for the Payment entity/repository/service boundary):**
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentState.java` (enum)
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java` (pure logic)
- Test files mirroring both, under `src/test/java/com/themistra/payment/payment/`.

**Existing, to read/extend:**
- `services/payment/src/main/java/com/themistra/payment/ArchitectureTest.java` (T05) — likely needs extending to cover the new `payment` package for `shouldPreventCrossModuleEntityImports`, or this task adds its own dedicated rule; Phase 2 to decide which.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceState.java` — the one sibling enum precedent to mirror stylistically.
- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` — `chk_payment_state` is the authoritative 7-value state list; `PaymentState` must match it exactly (already confirmed in Phase 0).

**Not involved (confirmed by direct read, not assumed):**
- No `Payment` JPA entity, `PaymentRepository`, or `PaymentService` exists yet, and task 9's own text names none of them — read as task 11's own scope (the first caller that needs to persist a `Payment` row). Building them here would be scope creep; **not built this task**.
- No `ChainEventConsumer`/Kafka listener infrastructure — task 11's own scope.
- No `VerificationRecord` — task 10's own scope (records every transition/reversal/ignored-event, but that's a persistence concern layered on top of this task's own pure logic, not this task's own job to write).

## Dependencies

- None beyond the JDK and JUnit/AssertJ for tests — "pure logic" means no Spring, no persistence, no other `payment-service` class. `PaymentState` is this class's only real dependency.
- Cross-reference only (not a code dependency): the real `payments.payments.state` `CHECK` constraint (`V1__payments_baseline.sql`), which this enum must continue to match exactly so a future `Payment` entity (task 11) can map to it with `@Enumerated(STRING)` without drift.

## Acceptance Criteria

- **AC1 (L1)** — every one of the 10 permitted edges derived above succeeds and returns the target state: `CREATED→WATCHING` (R6), `WATCHING→SEEN` (R7), `SEEN→CONFIRMING` (R9), `CONFIRMING→FINALIZED` (R10), `SEEN→FINALIZED` (R10), `CONFIRMING→SEEN` (R11, reversal), `SEEN→WATCHING` (R12, reversal), `FINALIZED→ATTESTED` (R24/L2), `FINALIZED→HELD` (R20), `ATTESTED→HELD` (L11).
- **AC2 (L1, task's own named behavior)** — every `(current, target)` pair **not** in AC1's own set throws, never silently no-ops or returns an unchanged/null value. This includes same-state "transitions" (e.g. `SEEN→SEEN`) and every reversal/skip not explicitly listed (e.g. `WATCHING→CONFIRMING`, `CREATED→SEEN`, `ATTESTED→FINALIZED`).
- **AC3 (L12)** — `PaymentState`/`PaymentStateMachine` import no entity class from any other feature package (`invoice`, or any future `verification`/`consumer`/`receipt` package) — proven by ArchUnit, named test `shouldPreventCrossModuleEntityImports`.
- **AC4 (implied by "pure logic")** — the class has no Spring annotation, no field beyond what's needed for the fixed edge set (if any), and no constructor parameter — confirmed by direct code read; no dedicated test needed beyond compiling without a Spring context, mirroring how `LockoutStateMachine` (auth-service) needs no such test either.
- **AC5 (defensive, mirrors every other task's own null-handling convention in this service)** — `transition(null, target)` / `transition(current, null)` throws `NullPointerException`, not a silent/incorrect `IllegalPaymentStateTransitionException`.

## Tests required

- `shouldPreventCrossModuleEntityImports` (`package.md` §8, named, L1/L12) — ArchUnit.
- One test per AC1 edge (10 tests) — e.g. `shouldTransitionCreatedToWatchingOnWatchAck`, `shouldTransitionWatchingToSeenOnChainTxSeen`, `shouldTransitionSeenToConfirmingOnChainTxConfirmed`, `shouldTransitionConfirmingToFinalizedOnChainTxFinalized`, `shouldTransitionSeenDirectlyToFinalizedWhenConfirmedNeverArrived`, `shouldTransitionConfirmingBackToSeenOnReorg`, `shouldTransitionSeenBackToWatchingOnReorg`, `shouldTransitionFinalizedToAttestedOnReceiptIssuance`, `shouldTransitionFinalizedToHeldOnBlockedAttest`, `shouldTransitionAttestedToHeldOnPostAttestReorg`.
- A boundary test asserting illegal transitions throw — implied, not individually named in `package.md`. Given 7 states, there are 42 ordered pairs total minus the 10 legal ones = 32 illegal pairs; a single parameterized/exhaustive test (not 32 separate methods) is the natural shape — Phase 2 to decide the exact mechanism (e.g. iterate `EnumSet` minus the legal-edge set).
- Null-argument tests for `AC5`, mirroring `InvoiceServiceTest`'s own `shouldThrowNullPointerExceptionForNull*` convention.

## Open Questions

- **Scope boundary (not a package.md §11 item — a direct-reading finding):** does task 9 include the `Payment` entity/repository/service `design.md` §6's own package map lists alongside `PaymentStateMachine`? Tentative reading: no — task 9's own text names only `PaymentStateMachine`; those three are task 11's own scope (the first real caller that needs persistence). **To confirm in Phase 2/3, not silently assumed either way.**
- **The L1-vs-§4c transition-count tension** (carried forward from Phase 0): resolved reading is the full 10-edge set above (§4c + its own cross-references, including the two separate, narrowly-justified `→ HELD` edges rather than a literal "any state"), not L1's/`agents.md`'s own narrower six-state-line-plus-two-reversals narrative. **This is a real architectural call with consequences for tasks 11/12/17/19 — propose in Phase 2, invite a genuine Kimi challenge at Phase 3, and treat Phase 4 as a real Human Approval decision, not a rubber stamp.**
- **R14's "stale event → ignore silently" vs. this task's own "illegal transition → throw":** tentative reading (carried from Phase 0) is that these are different layers — `PaymentStateMachine` always throws on any non-edge pair (this task's own explicit contract); task 11's `ChainEventConsumer` is responsible for detecting staleness *before* calling it (or catching the exception), never relying on exception-driven control flow for an expected, routine case. Proposing a non-throwing `isPermitted(current, target)` query method alongside the throwing `transition(...)` method in Phase 2, so task 11 has a safe pre-check rather than needing try/catch for routine at-least-once-delivery staleness.
- **Interface shape**: `transition(PaymentState current, PaymentState target)` (state-pair keyed) vs. an event-type-keyed method. Tentative: state-pair keyed, keeping this class ignorant of Kafka/event DTOs entirely (cleaner module boundary, reusable from any future caller including task 17/19's own non-Kafka-driven transitions). Propose in Phase 2.
