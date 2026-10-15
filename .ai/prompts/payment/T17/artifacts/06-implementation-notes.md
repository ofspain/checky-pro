# payment · T17 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

**New files:**
- `payment/PaymentReceiptView.java`, `invoice/InvoiceReceiptView.java` — cross-module
  projections (Phase 3 Finding 1), each with a package-private static `from(entity)` factory.
- `receipt/ReceiptIssueResult.java` — sealed interface (Phase 3 Finding 5): `Signed`/`Blocked`/
  `NotEligible`/`Failed`.
- `receipt/ReceiptService.java` — the task's own primary deliverable:
  `issueIfEligible(Long paymentId, String triggerEventKey)`.

**Modified:**
- `payment/PaymentRepository.java` — new `markHeldIfFinalized(Long id, Instant now)`, a literal
  `'FINALIZED'`/`'HELD'` atomic update (no `expectedCurrentState` parameter needed — this
  task's own resolved scope means `FINALIZED` is the only state it's ever called against,
  re-checked fresh immediately before the call).
- `payment/PaymentService.java` — new `findReceiptPrimitives`/`markHeldIfFinalized` (the latter
  records a `VerificationRecord` on success, Phase 3 Finding 4).
- `invoice/InvoiceService.java` — new `findReceiptPrimitives`/`markHeldIfPaid` (the latter
  delegates to the already-generic `InvoiceRepository.updateStateIfCurrentState` — **no new
  `InvoiceRepository` method needed**, a deviation from the Phase 5 plan's own sketch, explained
  below).
- `consumer/ChainEventConsumer.java` — new `ReceiptService` constructor parameter/field;
  `onChainTxFinalized` restructured per the frozen brief (no longer `@Transactional` at the
  method level; the existing dedupe/resolve/apply/outcome-update sequence now runs inside an
  explicit `TransactionTemplate.execute(...)` block, unchanged in its own internal behavior;
  `receiptService.issueIfEligible(...)` is called after that commits, only when
  `amountDiscrepancy == null`).

**Mechanically fixed (not new test content, kept the module buildable):**
- `test/.../consumer/ChainEventConsumerTest.java` — added the new `ReceiptService` mock and
  constructor argument the signature change requires. No new test methods added here — that is
  Phase 10's own job.

## Deviations forced by reality

**`InvoiceRepository` gained no new method, contrary to the Phase 5 plan's own sketch.** The
plan proposed a new `markHeldIfPaid` JPQL query on `InvoiceRepository` directly. Re-reading
`InvoiceRepository.updateStateIfCurrentState`'s own existing signature (already fully generic:
`(id, expectedCurrentState, newState, now)`) during implementation showed this was unnecessary —
`InvoiceService.markHeldIfPaid` simply calls `repository.updateStateIfCurrentState(invoiceId,
InvoiceState.PAID, InvoiceState.HELD, now)` directly. This matches Kimi's own Phase 3 Finding 9
text exactly ("can delegate to `updateStateIfCurrentState`... Wrap them in... `InvoiceService
.markHeldIfPaid(...)`") — the named-method requirement was about the **service** layer keeping
the receipt-blocked business meaning visible at the call site, not about adding a redundant
repository query. `PaymentRepository` still needed its own new method, since no existing
single-state-conditional-update method there was reusable (every existing `PaymentRepository`
method is already bespoke to its own specific transition).

**`PaymentReceiptView`/`InvoiceReceiptView` are populated via `repository.findById(...).map(View
::from)`, not a JPQL constructor-expression query, contrary to the Phase 5 plan's own first
sketch.** Re-checking `InvoiceWatchView`'s own real, established precedent
(`InvoiceService.findByIdAsWatchView`/`findByWatchId`) during implementation showed the actual
convention is the inherited `JpaRepository.findById(...)` plus a package-private static
`from(entity)` factory — simpler than a hand-written JPQL constructor expression, and the one
this codebase has actually used twice already. Followed that instead.

## Mapping to the frozen brief / acceptance criteria

- **AC1 (R17/R18/L2)** — `issueIfEligible`'s own first two guards (`payment.state !=
  FINALIZED`, `invoice.state != PAID`) both return `NotEligible` before any attest call.
- **AC2 (R19)** — `buildPayload` + `ReceiptDigest.digest` + `cryptoClient.attest(digest, chain,
  txHash)`.
- **AC3 (R20)** — `handleBlocked`'s own `TransactionTemplate` block calls both `HELD` methods.
- **AC4 (L2)** — the `Signed` branch returns immediately; no repository call, no entity touched.
- **AC5 (transaction safety)** — `onChainTxFinalized` is no longer `@Transactional`; the
  `attest()` call happens strictly after the `TransactionTemplate.execute(...)` block returns.
- **AC6 (amended, 409-vs-BLOCKED)** — `AttestationRefusedException` and
  `CryptoServiceUnavailableException` are each caught in their own `catch` clause, both
  returning `Failed` with no `HELD` call at all; only `AttestationResult.Blocked` reaches
  `handleBlocked`.
- **AC7 (split-result atomicity)** — `handleBlocked`'s own `paymentHeld != invoiceHeld` check
  throws `IllegalStateException` inside the `TransactionTemplate` block (rolling back any
  partial update), caught outside it, converted to `Failed`.
- **AC8 (audit trail)** — `PaymentService.markHeldIfFinalized` calls
  `verificationRecordService.record(...)` on success.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean.
- `mvn -o -pl services/payment -am test-compile`: one expected break
  (`ChainEventConsumerTest`'s own direct constructor call), fixed mechanically (see above), then
  clean.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **348/348 passing**, unchanged
  from the T16 baseline — no regression.
- `mvn -o -pl services/payment -am test -Dtest=ChainEventConsumerTest,ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
  **32/32 passing** — including the real Postgres+Kafka integration test (T14) that exercises
  the full `seen → confirmed → finalized` sequence end-to-end. Its own log output confirms the
  restructured `onChainTxFinalized` ran against a real Spring context without any wiring error,
  and that `ReceiptService`'s own `CryptoServiceUnavailableException` handling correctly
  swallowed the inevitable failure of a real `attest()` call against a crypto-service that isn't
  running in this environment — a live, real-world confirmation of the disclosed-degradation
  design (AC6), not just a mocked one.
