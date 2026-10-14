# payment · T09 · Phase 4 — Frozen Task Brief (Human Approval gate)

Consumes `artifacts/02-task-implementation-brief.md` and `artifacts/03-design-challenge.md`
(Kimi 2.7). All 4 findings dispositioned below; Finding 1 was a genuine architectural decision
put to the user directly (not rubber-stamped) — **decided: keep the narrow `HELD`-edge reading.**
Findings 2-4 are self-evident corrections/additions, applied directly.

## Finding 1 — `any -> HELD` vs. the narrow two-edge reading

**DECIDED (user, direct question, not a default-accept):** keep the narrow reading — only
`FINALIZED → HELD` (R20) and `ATTESTED → HELD` (L11) are permitted. No requirement anywhere in
`requirements.md` backs a `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING → HELD` edge; adding one now
would be speculative surface area with no caller in any current task's own scope and no test
able to justify why it's safe. If a future task needs a pre-finality compliance hold, it
introduces a new LOCKED decision and its own requirement at that time — not assumed here.
`design.md` §4c's own `any -> HELD` comment is treated as imprecise shorthand for these two
specific, requirement-backed edges, not a literal instruction; this reading is now locked in by
this brief, reconciled explicitly rather than left implicit.

## Finding 2 — `shouldPreventCrossModuleEntityImports` does not exist yet

**ACCEPTED and FIXED (brief amended).** The Phase 2 brief's claim that `ArchitectureTest` "already
iterates `com.themistra.payment..` broadly... very likely zero or near-zero structural change" was
wrong — confirmed by direct re-read: the file contains only the floating-point-money rule. This
task must author the cross-module-entity-import rule from scratch, mirroring `crypto-service`'s
own `CrossModuleEntityArchitectureTest` (itself mirroring `auth-service`'s identical rule)
adapted to `payment-service`'s own established style: **no `@ArchTest` field / `@AnalyzeClasses`
annotation** (this service's own `ArchitectureTest` deliberately avoids them — see its own class
Javadoc and [[surefire-archtest-field-bug]] — relying solely on plain `@Test` canaries that call
`.check(...)` directly). "Files to Modify" is corrected to reflect a real, non-trivial addition,
not a near-zero one.

## Finding 3 — `@Component` on a zero-dependency pure-logic class

**ACCEPTED, narrow fix.** Keep `@Component` (matches `AddressValidator`'s own identical,
established precedent: "stateless `@Component` - no persistence, no injected dependency, but
still a proper Spring bean for eventual injection at a future call site") — switching to a static
utility class would be a style change with no established precedent anywhere in this codebase,
and every other "pure logic" class in this service/sibling services is an instantiated bean, not
a static-method utility. Kimi's own underlying concern (nothing currently guards against a future
field/constructor-parameter creeping in and undermining "pure logic") is real and cheap to close:
add a structural test (reflection-based, not source-text) asserting `PaymentStateMachine` has zero
declared fields and its no-arg constructor takes zero parameters.

## Finding 4 — `HELD` as an implicit terminal state

**ACCEPTED and FIXED.** State explicitly: `HELD` is terminal within this task's own scope — no
edge in the 10-edge set (now locked by Finding 1's decision) has `HELD` as its source. The
exhaustive illegal-pairs test must explicitly exercise every `(HELD, x)` pair including
`(HELD, HELD)`, not just omit `HELD` as a source by the absence of a matching edge. Documented
here as a locked scope boundary: any future recovery path out of `HELD` requires a new LOCKED
decision, not a change to this task's own output.

---

## Frozen Brief

## Task

Implement `PaymentStateMachine` as pure logic enforcing exactly the transitions in `design.md`
§4c/L1, including the two reorg reversals. Unit-test every permitted transition and assert
illegal transitions throw (not silently no-op).

## Purpose

A single, centralized, Spring-free source of truth for the payment verification state graph
(L1) — every future caller (tasks 11, 12, 17, 19) validates and performs a state change through
this one class, so "no other transition may be added" is enforced in exactly one place.

## Scope

**In:**
- `PaymentState` enum (7 values, matching `chk_payment_state` exactly).
- `PaymentStateMachine`: stateless `@Component`, throwing `transition(current, target)` plus
  non-throwing `isPermitted(current, target)`.
- `IllegalPaymentStateTransitionException` (new, public).
- A **new** `shouldPreventCrossModuleEntityImports` ArchUnit rule + canaries in `ArchitectureTest`
  (Finding 2 — not an extension of an existing rule; none exists), scoped to cover `invoice` and
  `payment` as the service's two feature modules.
- New test fixtures: `RogueInvoiceEntityReferencer` (test-source, inside the `payment` package,
  depends on the real `Invoice` entity — proves the entity-module-mismatch violation path) and
  `RogueUnmappedEntity` (test-source, inside the existing `archtestfixtures` package, a bare
  `@Entity` outside every known feature module — proves the fail-fast path), mirroring
  `crypto-service`'s own identical two-fixture pattern.
- A structural test proving `PaymentStateMachine` has zero fields and a zero-parameter
  constructor (Finding 3).

**Out:**
- `Payment` entity, `PaymentRepository`, `PaymentService` — task 11's own scope.
- `ChainEventConsumer`, Kafka listener infrastructure, `processed_events` dedupe — task 11.
- `VerificationRecord` — task 10.
- Mapping a specific `chain.tx.*` event type (or watch-ack, or attest outcome) to a target
  `PaymentState` — the future caller's own job.
- Detecting whether an incoming event is stale/out-of-order (R14) — a caller-side (task 11)
  concern.
- Any `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING → HELD` edge (Finding 1, decided — out of scope,
  not merely deferred).

## Business Rules

- R6 — `CREATED → WATCHING` on watch-ack.
- R7 — `WATCHING → SEEN` on `chain.tx.seen`.
- R9 — `SEEN → CONFIRMING` on `chain.tx.confirmed`.
- R10 — `CONFIRMING → FINALIZED` **and** `SEEN → FINALIZED` on `chain.tx.finalized`.
- R11 — `CONFIRMING → SEEN` reversal on `chain.tx.reorged`.
- R12 — `SEEN → WATCHING` reversal on `chain.tx.reorged`.
- R14 — stale/out-of-order events are a caller-side concern, not this class's contract.
- R20 — `FINALIZED → HELD` on a `BLOCKED` attest outcome.
- R24 — `FINALIZED → ATTESTED` once the receipt pipeline completes.

## Locked Decisions

- **L1** — fixed state graph, exactly 10 permitted directed edges (final, Finding 1 decided):
  `CREATED→WATCHING`, `WATCHING→SEEN`, `SEEN→CONFIRMING`, `CONFIRMING→FINALIZED`,
  `SEEN→FINALIZED`, `CONFIRMING→SEEN`, `SEEN→WATCHING`, `FINALIZED→ATTESTED`, `FINALIZED→HELD`,
  `ATTESTED→HELD`. No other ordered pair — including every self-pair, and including every
  `HELD→x` pair (`HELD` is terminal, Finding 4) — is permitted.
- **L2** — `ATTESTED` reachable only via `FINALIZED→ATTESTED`.
- **L11** — `ATTESTED→HELD` is the sole post-attestation path.
- **L12** — `payment` package imports no entity from any other feature package.

## Dependencies

None beyond the JDK and this task's own new `PaymentState` enum.

## Inputs

`PaymentState current`, `PaymentState target` (both methods).

## Outputs

- `transition(current, target)` returns `target` unchanged, or throws
  `IllegalPaymentStateTransitionException`.
- `isPermitted(current, target)` returns `boolean`.

## State Changes

None — pure logic; no entity, row, or field read/written.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/payment/PaymentState.java`
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentStateMachine.java`
- `services/payment/src/main/java/com/themistra/payment/payment/IllegalPaymentStateTransitionException.java`
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentStateMachineTest.java`
- `services/payment/src/test/java/com/themistra/payment/payment/RogueInvoiceEntityReferencer.java` (test fixture, Finding 2)
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueUnmappedEntity.java` (test fixture, Finding 2)

## Files to Modify

- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — add the new
  `shouldPreventCrossModuleEntityImports` rule (`ArchCondition`-based, mirroring the existing
  `shouldRejectFloatingPointMoneyTypes` rule's own no-`@ArchTest` style) plus its three canaries
  (passes cleanly, genuine violation fails, fail-fast on an unmapped entity) — a real, non-trivial
  addition (corrects Finding 2's "near-zero change" error).

## Files NOT to Modify

- `V1__payments_baseline.sql` (immutable).
- Any file under `spec/`.
- `invoice` package (read-only reference to `Invoice` for the new test fixture only).

## Acceptance Criteria

- **AC1 (L1)** — all 10 listed edges succeed via `transition(...)` (returning the target state)
  and report `true` via `isPermitted(...)`.
- **AC2 (L1)** — every other ordered pair (39 of 49 total: 7 self-pairs including `(HELD,HELD)`,
  plus 32 other cross-state pairs including every `(HELD, x≠HELD)` pair) throws
  `IllegalPaymentStateTransitionException` via `transition(...)` and reports `false` via
  `isPermitted(...)`.
- **AC3 (L12, Finding 2)** — `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`
  passes on real code; a genuine-violation canary (`RogueInvoiceEntityReferencer` depending on
  `Invoice`) fails it; a fail-fast canary (`RogueUnmappedEntity`) fails it with the
  "does not reside in any module" message.
- **AC4 (defensive)** — `transition(null, x)`, `transition(x, null)`, `isPermitted(null, x)`,
  `isPermitted(x, null)` all throw `NullPointerException`.
- **AC5 (Finding 3)** — `PaymentStateMachine` has zero declared fields and a zero-parameter
  constructor, proven by a reflection-based test, not just a code read.

## Required Tests

- `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (named in `package.md` §8
  as `shouldPreventCrossModuleEntityImports`; this exact method name mirrors the established
  canary-naming convention from `crypto-service`'s own identical test).
- One test per AC1 edge (10 tests).
- One exhaustive parameterized test proving all 39 illegal pairs throw (AC2), explicitly
  including `(HELD, HELD)` and every `(HELD, x≠HELD)` pair.
- `isPermitted` coverage mirroring the same pair enumeration (legal → `true`, illegal → `false`).
- Null-argument tests (4 cases) for both methods.
- `shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation`,
  `shouldFailFastWhenAnEntityIsOutsideEveryFeatureModule` (ArchUnit genuine-violation canaries,
  mirroring `crypto-service`'s own two-canary pattern).
- A reflection-based test proving `PaymentStateMachine` has no fields and a zero-arg constructor
  (AC5).

## Constraints

- **Thread-safety**: trivially satisfied by a stateless class with no mutable field; AC5's own
  test is what keeps this true over time.
- **Module boundaries (L12)**: zero imports from any other feature package in production code;
  `IllegalPaymentStateTransitionException` lives in `payment`, is **public** (a future
  cross-package caller must reach it).
- **Null handling**: every public method validates both arguments via `Objects.requireNonNull`
  first.
- **R14 is explicitly out of this class's own contract** — see Scope/Out above; the future
  caller (task 11) uses `isPermitted(...)` to pre-check staleness rather than relying on
  exception-driven control flow.
- **`HELD` is terminal** within this task's own output (Finding 4) — any future recovery path
  requires a new LOCKED decision.

## Open Questions

No blockers. Both real open items (the `HELD`-edge scope and the `Payment`-entity scope
boundary) are resolved above; Finding 1 was a genuine Human Approval decision, not a default.
