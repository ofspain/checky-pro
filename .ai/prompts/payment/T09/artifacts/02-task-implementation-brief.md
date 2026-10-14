# payment · T09 · Phase 2 — Task Implementation Brief

## Task

Implement `PaymentStateMachine` as pure logic enforcing exactly the transitions in `design.md`
§4c/L1, including the two reorg reversals. Unit-test every permitted transition and assert
illegal transitions throw (not silently no-op).

## Purpose

A single, centralized, Spring-free source of truth for the payment verification state graph
(L1) — every future caller (tasks 11, 12, 17, 19) validates and performs a state change through
this one class, so "no other transition may be added" (L1) is enforced in exactly one place
rather than duplicated or drifting across the consumer, reorg-handling, receipt-issuance, and
ledger-append code paths that will each need to drive a transition later.

## Scope

**In:**
- `PaymentState` enum (7 values, matching the real `chk_payment_state` DB constraint exactly).
- `PaymentStateMachine`: a stateless `@Component` (mirrors `AddressValidator`'s own identical
  precedent — stateless, no injected dependency, still a proper Spring bean for eventual
  injection at a future call site) exposing a throwing `transition(current, target)` and a
  non-throwing `isPermitted(current, target)` query.
- `IllegalPaymentStateTransitionException` (new, public — must be reachable from a future
  caller in a different package, e.g. task 11's `consumer` package, so cannot be package-private
  the way `InvoiceNotFoundException` is).
- Extending the existing `ArchitectureTest` (T05) to cover the new `payment` package for
  `shouldPreventCrossModuleEntityImports` (L1/L12), rather than writing a second, parallel
  ArchUnit test class.

**Out:**
- `Payment` entity, `PaymentRepository`, `PaymentService` — `design.md` §6's own package map
  lists them in the same `payment/` package, but task 9's own text names none of them; read as
  task 11's own scope (the first real caller that needs to persist a `Payment` row). Not built.
- `ChainEventConsumer`, Kafka listener infrastructure, `processed_events` dedupe — task 11.
- `VerificationRecord` — task 10.
- Mapping a specific `chain.tx.*` event type (or the watch-ack, or an attest outcome) to a target
  `PaymentState` — that mapping is the future caller's own job; this class only validates/performs
  a `(current, target)` pair it is handed.
- Detecting whether an incoming event is stale/out-of-order (R14) — a caller-side (task 11)
  concern; see Constraints.

## Business Rules

- R6 — `CREATED → WATCHING` on watch-ack.
- R7 — `WATCHING → SEEN` on `chain.tx.seen`.
- R9 — `SEEN → CONFIRMING` on `chain.tx.confirmed`.
- R10 — `CONFIRMING → FINALIZED` **and** `SEEN → FINALIZED` on `chain.tx.finalized` (finalization
  can legitimately arrive without an intervening `confirmed`).
- R11 — `CONFIRMING → SEEN` reversal on `chain.tx.reorged`.
- R12 — `SEEN → WATCHING` reversal on `chain.tx.reorged`.
- R14 — stale/out-of-order events are a caller-side concern (Constraints), not this class's own
  throw/no-throw contract.
- R20 — `FINALIZED → HELD` on a `BLOCKED` attest outcome (the transition itself is in scope; the
  attest call that decides to invoke it is task 17's).
- R24 — `FINALIZED → ATTESTED` once the receipt is signed/stored/ledger-appended (the transition
  itself is in scope; the receipt pipeline that invokes it is tasks 17/19's).

## Locked Decisions

- **L1** — fixed state graph, exactly 10 permitted directed edges (resolved reading, see
  Constraints): `CREATED→WATCHING`, `WATCHING→SEEN`, `SEEN→CONFIRMING`, `CONFIRMING→FINALIZED`,
  `SEEN→FINALIZED`, `CONFIRMING→SEEN`, `SEEN→WATCHING`, `FINALIZED→ATTESTED`, `FINALIZED→HELD`,
  `ATTESTED→HELD`. No other ordered pair — including every self-pair — is permitted.
- **L2** — `ATTESTED` reachable only via `FINALIZED→ATTESTED`; no other edge targets `ATTESTED`.
- **L11** — `ATTESTED→HELD` is the sole post-attestation path (a reorg after attestation);
  ops-alerting itself is a future caller's concern, not this class's.
- **L12** — `payment` package imports no entity from any other feature package; `PaymentState`/
  `PaymentStateMachine` have zero imports from `invoice`/`common`/any future package.

## Dependencies

None beyond the JDK and this task's own new `PaymentState` enum. No Spring dependency beyond the
`@Component` stereotype annotation itself (mirrors `AddressValidator`). No persistence, no other
`payment-service` class.

## Inputs

`PaymentState current`, `PaymentState target` (both methods). No other input.

## Outputs

- `transition(current, target)` returns `PaymentState target` unchanged, as a convenience echo
  for a fluent call-site (`payment.state = stateMachine.transition(payment.state, target)`), or
  throws `IllegalPaymentStateTransitionException`.
- `isPermitted(current, target)` returns `boolean`, never throws for a non-null, non-permitted
  pair (only for `null` arguments, per Constraints).

## State Changes

None — this class is pure logic; it does not read or write any entity, row, or field. (The
*caller*, in a future task, is responsible for actually setting a `Payment` row's `state` column
to the value this class validated/echoed back.)

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/payment/PaymentState.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java`
- `services/payment/src/main/java/com/themistra/payment/payment/IllegalPaymentStateTransitionException.java`
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentStateMachineTest.java`

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/ArchitectureTest.java` — extend the
  existing no-cross-module-entity-import rule's scope to include the new `payment` package (it
  already iterates `com.themistra.payment..` broadly per T05's own design, so this is very
  likely zero or near-zero structural change — confirm exact diff in Phase 5).

## Files NOT to Modify

- `V1__payments_baseline.sql` (immutable; `chk_payment_state` already lists all 7 `PaymentState`
  values — nothing to add).
- Any file under `spec/`.
- `invoice` package — this task touches no invoice code.

## Acceptance Criteria

- **AC1 (L1)** — all 10 listed edges succeed via `transition(...)` and return the target state,
  and all report `true` via `isPermitted(...)`.
- **AC2 (L1, this task's own named behavior)** — every other ordered pair (39 of the 49 total:
  7 self-pairs plus 32 other cross-state pairs — corrects Phase 1's own miscount of "32 illegal,"
  which omitted the 7 self-pairs) throws `IllegalPaymentStateTransitionException` via
  `transition(...)` and reports `false` via `isPermitted(...)`. Never a silent no-op, never a
  null/unchanged return.
- **AC3 (L12)** — `shouldPreventCrossModuleEntityImports` passes with the new `payment` package
  included in its scope.
- **AC4 (defensive)** — `transition(null, x)`, `transition(x, null)`, `isPermitted(null, x)`,
  `isPermitted(x, null)` all throw `NullPointerException`, never a silent `false`/incorrect
  exception type.
- **AC5 (implied by "pure logic")** — `PaymentStateMachine` has no Spring dependency beyond its
  own `@Component` annotation, no field, no constructor parameter — confirmed by direct code
  read at Phase 7/12, not a dedicated runtime test (mirrors `AddressValidator`'s own identical,
  untested-by-a-dedicated-test "stateless" property).

## Required Tests

- `shouldPreventCrossModuleEntityImports` (named, `package.md` §8).
- One test per AC1 edge (10 tests, named per Phase 1's own list — e.g.
  `shouldTransitionCreatedToWatchingOnWatchAck` … `shouldTransitionAttestedToHeldOnPostAttestReorg`).
- One exhaustive test proving every one of the 39 illegal pairs throws (a single parameterized
  test iterating `EnumSet`/all pairs minus the legal-edge set, not 39 separate methods) — AC2.
- `isPermitted` mirrored coverage for both the legal and illegal sets — can reuse the same
  parameterized source as the `transition(...)` tests rather than duplicating the pair
  enumeration.
- Null-argument tests for both methods (4 tests, or one parameterized test) — AC4.

## Constraints

- **Thread-safety**: must be safe for concurrent use by multiple consumer threads later (task
  11) — trivially true for a stateless class with no mutable field, but confirm no field is ever
  added without re-examining this.
- **Module boundaries (L12)**: zero imports from any other feature package; the exception class
  lives in `payment`, not `common`, since it is specific to this state graph (mirrors
  `InvoiceNotFoundException`'s own precedent of living in its own feature package, not `common`)
  — but unlike `InvoiceNotFoundException`, it must be **public**, not package-private, since a
  future caller in a different package (task 11's `consumer`) needs to catch or reference it.
- **Null handling**: every public method validates both arguments via `Objects.requireNonNull`
  before any other logic, mirroring `InvoiceService`'s own established convention throughout
  this service.
- **R14 is explicitly out of this class's own contract**: a stale/out-of-order event (e.g. a
  `chain.tx.seen` arriving for an already-`FINALIZED` payment) is request for an illegal pair
  from this class's own point of view and **will throw**. The future caller (task 11) is
  responsible for either (a) calling `isPermitted(...)` first and treating `false` as "ignore,
  record as stale" per R14, rather than ever calling the throwing `transition(...)` for an event
  it hasn't first checked, or (b) catching `IllegalPaymentStateTransitionException` and treating
  it the same way. This class does not — and must not — guess which stale events are "expected"
  versus a genuine caller bug; that judgment, and R14's own "record that it was ignored" duty,
  belong entirely to task 11/10.
- **The L1-vs-§4c edge-count resolution is locked in by this brief**, not left open further:
  the 10-edge set above is what Phase 5/6 implement. Phase 3 (Kimi) is explicitly invited to
  challenge this reading before it freezes at Phase 4.

## Open Questions

No blockers. The two real open items from Phase 1 (the Payment-entity scope boundary, and the
L1-vs-§4c edge count) are resolved above as this brief's own concrete proposal — both are
flagged for genuine Phase 3 (Kimi) challenge and a real Phase 4 Human Approval decision, not
rubber-stamped, since both carry real consequences for tasks 11/12/17/19.
