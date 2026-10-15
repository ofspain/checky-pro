# payment · T17 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

## Business Rules

- **R17.** WHEN a payment is in `FINALIZED` and satisfies its invoice, THEN the system SHALL
  request attestation and issue exactly one receipt.
- **R18.** IF a payment is in any state other than `FINALIZED`, THEN the system SHALL NOT issue
  a receipt under any code path.
- **R19** (partial — T15/T16 already built the mechanism; this task is the first real caller).
  WHEN issuing a receipt, THEN the system SHALL compute the canonical receipt digest and obtain
  its signature by calling the Crypto Service `POST /attest` endpoint; the system SHALL NOT
  perform signing itself.
- **R20.** IF the Crypto Service attest response is `BLOCKED`, THEN the system SHALL NOT issue a
  receipt, SHALL move the payment/invoice to `HELD`, and SHALL emit no `receipt.issued` event.

## Locked Decisions

- **L2.** A receipt is issued only from `FINALIZED`, never earlier. No code path signs or
  issues a receipt from `SEEN` or `CONFIRMING`. `ATTESTED` is set only after the signature is
  obtained, the receipt is stored in S3, and the ledger entry is appended, **all durably** —
  confirms this task's own `SIGNED` path does not itself reach `ATTESTED`; that is T19's own
  job, once T18's S3 write also exists.
- **L1/L12** (context): `PaymentStateMachine` already permits `FINALIZED → HELD`; no other
  transition may be added. `ReceiptService` (new `receipt/` package) must not import `Invoice`
  or `Payment` directly (both `@Entity` classes in other feature modules) — same constraint
  already applied to `ReceiptPayload`/`ReceiptDigest` at T16.

## Files involved

**Existing, to extend:**
- `payment/PaymentRepository.java` — needs a new `FINALIZED → HELD` atomic update method.
- `invoice/InvoiceRepository.java`/`InvoiceService.java` — needs a new HELD transition,
  reachable from whichever invoice states can legitimately precede a `BLOCKED` response
  (`PAID`/`UNDERPAID`/`OVERPAID`, since the invoice outcome is already set by the time
  `onChainTxFinalized`'s own transaction commits).
- `consumer/ChainEventConsumer.java` — `onChainTxFinalized` needs a new call to trigger
  `ReceiptService`, without holding its own transaction open across the `attest()` network call
  (mirrors `onChainTxReorged`'s own `TransactionTemplate` precedent for the identical problem).
- `common/CryptoClient.java` (T15), `receipt/ReceiptDigest.java`/`ReceiptPayload.java` (T16) —
  both already built, both now get their first real caller.

**New files the spec expects:**
- `receipt/ReceiptService.java` — this task's own primary deliverable.

**Not touched, confirmed structurally impossible for this task:**
- `receipt/Receipt.java`/`ReceiptRepository.java` — the `receipts` table's own `s3_bucket`/
  `s3_key NOT NULL` columns make a `Receipt` row impossible to insert before T18's S3 write
  exists (Phase 0 finding, confirmed against the real schema, not assumed).

## Dependencies

- `CryptoClient.attest(...)` (T15) — returns `AttestationResult.Signed`/`Blocked`, throws
  `AttestationRefusedException`/`CryptoServiceUnavailableException`.
- `ReceiptDigest.digest(ReceiptPayload)` (T16).
- `PaymentState.HELD`, `PaymentStateMachine` (already permits `FINALIZED → HELD`, T09).
- `InvoiceState.HELD` (already exists, T02).
- `PaymentRepository`/`InvoiceRepository` — both need a new method (see Files involved).
- `contracts/events/payments/receipt-issued.v1.schema.json` — relevant only as future context;
  this task's own R20 text says "emit no `receipt.issued` event" for the `BLOCKED` case, and the
  `SIGNED` case doesn't reach the point of emitting it either (L2 — that's T19's own job, after
  S3 storage and the ledger append). This task therefore touches the outbox/event machinery not
  at all.

## Acceptance Criteria

- **AC1 (R17/R18/L2).** `ReceiptService`'s own entry method refuses to act on any payment not
  currently in `FINALIZED` — the guard is checked explicitly, not merely assumed from the
  caller's own context.
- **AC2 (R19).** For a `FINALIZED`, invoice-satisfying payment, `ReceiptService` builds a
  `ReceiptPayload`, computes its digest via `ReceiptDigest`, and calls `CryptoClient.attest(...)`
  with that digest plus the payment's `chain`/`txHash`.
- **AC3 (R20).** A `BLOCKED` result moves the payment to `HELD` (via the new atomic repository
  method) and the invoice to `HELD` (via the new invoice-side transition), and emits no event
  (confirmed by the absence of any outbox-related call in this task's own new code).
- **AC4 (L2, new).** A `SIGNED` result does **not** transition the payment to `ATTESTED` and
  does **not** persist a `Receipt` row (structurally impossible per Phase 0's own finding) — the
  exact representation of a successful `SIGNED` outcome from `ReceiptService`'s own method is a
  Phase 2 design decision.
- **AC5 (new, transaction safety).** The `attest()` network call never executes inside an open
  database transaction that also holds a row lock or other transactional work from
  `onChainTxFinalized`'s own existing logic.

## Tests required

The four named tests from `package.md` §8 are pre-mapped to this task:
- `shouldIssueReceiptOnlyFromFinalizedState` (R17).
- `shouldNeverIssueReceiptBeforeFinalized` (R18) — the negative case; a payment in `SEEN`/
  `CONFIRMING`/etc. must not trigger an attest call at all.
- `shouldRequestKmsSignatureViaCryptoAttestEndpoint` (R19) — the full flow, verifying
  `CryptoClient.attest` is called with the correct digest.
- `shouldNotIssueReceiptWhenAttestReturnsBlocked` (R20) — verifying the `HELD` transition on
  both payment and invoice, and that nothing resembling an event emission occurs.

Boundary tests implied, not separately named in `package.md` §8:
- A test proving the `attest()` call happens outside any transaction (or at minimum, that
  `ReceiptService`'s own entry method is not itself `@Transactional` wrapping the network call) —
  exact test shape is a Phase 2/5 design decision.
- A test for the "invoice outcome at the time of `BLOCKED`" boundary — i.e., that the `HELD`
  transition succeeds regardless of whether the invoice was `PAID`/`UNDERPAID`/`OVERPAID` at that
  moment (pending Phase 2's resolution of Open Question 1 below).

## Open Questions

Both carried forward from Phase 0, confirmed not pre-answered anywhere in `package.md` §8/§11:

1. **Does R17's "satisfies its invoice" mean exactly `PAID`, or also `UNDERPAID`/`OVERPAID`?**
   `requirements.md` R15 distinguishes these outcomes explicitly for the invoice-state purpose
   but is silent on whether a receipt should still be issued for a mismatched amount. This is a
   genuine product-policy question (does Themistra attest to an underpaid/overpaid transaction
   the same way it attests to an exact one?), not an implementation detail — flagging as a
   candidate for a genuine `AskUserQuestion` at Phase 2/4, not a default assumption.
2. **Where exactly does the call to `ReceiptService` happen, given the transaction-boundary
   constraint (AC5)?** Not a blocker to starting Phase 2 — this is squarely an implementation
   design decision within the task's own discretion (mirrors `onChainTxReorged`'s own precedent
   for the identical class of problem), to be proposed at Phase 2 and stress-tested at Phase 3.

Question 1 is flagged as a potential blocker requiring direct product input, not merely an
implementation choice — carried to Phase 2 for a concrete proposal, with the expectation that
Phase 4's human-approval gate may need to resolve it directly rather than rubber-stamp a
Phase 2 guess.
