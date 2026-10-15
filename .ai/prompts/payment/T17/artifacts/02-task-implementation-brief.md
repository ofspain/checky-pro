# payment · T17 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Implement `ReceiptService`: guard that the payment is `FINALIZED` and its invoice is `PAID`
(R17/R18/L2, scope decided directly by the user — see below), call `/attest` via
`CryptoClient`/`ReceiptDigest` (R19), and honour a `BLOCKED` outcome by moving the payment and
invoice to `HELD` and emitting nothing (R20). Wire `ChainEventConsumer.onChainTxFinalized` to
call it, without holding a database transaction open across the `attest()` network call.

## Purpose

This is the point where a finalized, correctly-paid invoice actually gets Themistra's signed
attestation process started. T15 built the HTTP mechanism; T16 built the digest; neither has a
real caller yet. This task is also the first to actually exercise `PaymentState.HELD` — a
transition `PaymentStateMachine` has permitted since T09 but nothing has ever triggered.

## Scope

**In:** `ReceiptService`'s own guard/orchestration logic; new `PaymentRepository`/
`InvoiceRepository` methods for the `HELD` transition; the `ChainEventConsumer` wiring change
(splitting `onChainTxFinalized`'s existing `@Transactional` body from the new post-commit step).

**Out:** Persisting a `Receipt` row (structurally impossible — T18's S3 write doesn't exist yet,
and `receipts.s3_bucket`/`s3_key` are `NOT NULL`), the `ATTESTED` transition (T19's own job,
after S3 storage and the ledger append both also exist), any `receipt.issued` event (same — not
reached from either this task's `SIGNED` or `BLOCKED` path), any receipt-retry/scheduled job for
a failed `attest()` call (see Constraints — a disclosed, out-of-scope degradation, not solved
here).

## Business Rules

- **R17/R18 (resolved scope, decided directly by the user via `AskUserQuestion`):** a receipt is
  requested only when the payment is `FINALIZED` **and** its invoice is exactly `PAID` (exact
  amount match). `UNDERPAID`/`OVERPAID` payments are finalized on-chain and keep their own
  discrepancy record (R15), but are never attested — `ReceiptService` takes no action for them
  at all (no `HELD`, no attest call, nothing). This is a real product-policy decision, not an
  implementation default — "a receipt is Themistra's attestation that the payment correctly
  satisfied the invoice."
- **R19** (partial, T15/T16 already built the mechanism).
- **R20.**

## Locked Decisions

- **L2**: confirmed — this task's own `SIGNED` path does not transition to `ATTESTED` and does
  not persist a `Receipt` row; it is the first of three durable steps design.md itself names.
- **L1** (context): `FINALIZED → HELD` is the only new edge this task exercises;
  `PaymentStateMachine` already permits it (T09) — no change to that class needed.
- **L12**: `ReceiptService` lives in `receipt/`, imports neither `Invoice` nor `Payment`
  directly — takes/returns primitive-typed data, mirroring `ReceiptPayload`'s own T16 precedent.

## Dependencies

- `CryptoClient.attest(...)` (T15), `ReceiptDigest.digest(...)`/`ReceiptPayload` (T16).
- `PaymentRepository` — new method, e.g. `markHeldIfFinalized(Long id, Instant now)`.
- `InvoiceRepository` — new method(s) to reach `HELD` from whichever state the invoice is
  actually in at that moment: by construction, once `ReceiptService` only proceeds when the
  invoice is `PAID` (the scope decision above), the **only** state `HELD` needs to be reachable
  from here is `PAID` itself — no multi-attempt shape needed (unlike `markOutcomeIfOpenOrExpired`'s
  own two-attempt `OPEN`-then-`EXPIRED` precedent, which existed because multiple prior states
  were plausible; here exactly one is, by this task's own gating logic).
- `TransactionTemplate` (`PlatformTransactionManager`) — already a `ChainEventConsumer` field
  (T12), reused here for the same reason.
- `PaymentRepository.findCurrentState`-style scalar read (T11's own established "read the real
  current value via a projection, not a stale loaded entity" pattern) — needed because the
  post-commit step reads state that was written in a transaction that has already committed,
  via a fresh, separate connection/transaction.

## Inputs

`ReceiptService`'s own entry method takes primitive-typed data identifying the payment/invoice
(e.g. `Long paymentId` or an equivalent small record) — never a `Payment`/`Invoice` entity
reference (L12).

## Outputs

**Design decision (resolving Phase 1 Open Question 2 — the transaction-boundary problem):**
mirror `onChainTxReorged`'s own already-established, already-reviewed shape exactly:

1. `onChainTxFinalized` stops being `@Transactional` at the method level.
2. The existing dedupe + `applyFinalized` + `markOutcomeIfOpenOrExpired` logic moves inside a
   `transactionTemplate.execute(...)` block, unchanged in its own internal behavior, returning
   enough data for the next step (at minimum: the payment's own id, and whether the invoice
   outcome just set was `PAID`).
3. **After** that transaction commits, and only if the outcome was `PAID`, call
   `receiptService.issueIfEligible(paymentId)` as a separate, non-transactional step — which
   itself calls `ReceiptDigest`/`CryptoClient.attest()` with no transaction open, then — only on
   `BLOCKED` — opens its own new, narrow transaction (a second `transactionTemplate.execute(...)`
   call, scoped to just the two `HELD` updates) to persist the consequence.
- A `SIGNED` result: no DB write at all in this task's own scope (per L2/Out-of-scope above) —
  `ReceiptService`'s own method can simply return without persisting anything; a future task
  (T18/T19) is the one that will eventually do something with the signed result. Exact return
  type (void? a sealed result the caller logs?) is an implementation-phase choice.
- A transport/refused failure (`CryptoServiceUnavailableException`/`AttestationRefusedException`):
  logged and swallowed, **not retried from here** — mirrors `registerReplacementWatch`'s own
  already-accepted degradation for the identical class of problem (Phase 9 Finding 5, T12):
  `dedupe` already consumed this event's idempotency key in the committed transaction, so Kafka
  redelivery cannot retry this step either. **Disclosed, not solved here**: unlike a watch
  registration failure (which "rides" the invoice's own natural expiry), a `FINALIZED` payment
  that fails to attest has no analogous natural recovery path — it would simply never reach
  `ATTESTED` until some future retry mechanism exists. No task in `tasks.md` 15–21 currently adds
  one. Logged as a known, disclosed gap — out of this task's own scope to invent a retry job that
  isn't specified.

## State Changes

- `Payment.state`: `FINALIZED → HELD` (new, on `BLOCKED` only).
- `Invoice.state`: `PAID → HELD` (new, on `BLOCKED` only).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java`.

## Files to Modify

- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` — new
  `FINALIZED → HELD` atomic update method.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java` — new
  `PAID → HELD` atomic update method.
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java` —
  `onChainTxFinalized`'s own restructuring (Outputs, above).

## Files NOT to Modify

`PaymentStateMachine` (already permits the needed edge, T09), `ReceiptDigest`/`ReceiptPayload`/
`CryptoClient` (T15/T16, used as-is), anything under `spec/`, anything outside
`services/payment`.

## Acceptance Criteria

- AC1 (R17/R18/L2): `ReceiptService` only acts when the payment is `FINALIZED` and the invoice
  is `PAID` — any other combination is a no-op.
- AC2 (R19): a `PAID`/`FINALIZED` payment triggers a real `CryptoClient.attest(...)` call with
  the correct digest.
- AC3 (R20): `BLOCKED` → both payment and invoice reach `HELD`; no event is emitted.
- AC4 (L2): `SIGNED` → no state change, no `Receipt` row, no event.
- AC5 (new, transaction safety): the `attest()` call never executes while
  `onChainTxFinalized`'s own dedupe+outcome-update transaction is still open.
- AC6 (new, disclosed degradation): a transport/refused failure is logged and swallowed, not
  retried from this code path.

## Required Tests

Following `PaymentServiceTest`/`InvoiceServiceTest`'s own established unit, mocked-collaborator
shape (no Spring context):
1. `shouldIssueReceiptOnlyFromFinalizedState` (named) — a `FINALIZED`+`PAID` payment triggers
   `attest`.
2. `shouldNeverIssueReceiptBeforeFinalized` (named) — `SEEN`/`CONFIRMING`/etc. never calls
   `attest` at all.
3. A test proving an `UNDERPAID`/`OVERPAID` invoice outcome also never calls `attest` (the
   resolved scope decision — not literally named in `package.md` §8, but the direct consequence
   of this task's own resolved R17 scope).
4. `shouldRequestKmsSignatureViaCryptoAttestEndpoint` (named) — the digest/call shape.
5. `shouldNotIssueReceiptWhenAttestReturnsBlocked` (named) — both `HELD` transitions, no event.
6. A test proving `SIGNED` causes no state change (AC4).
7. A test proving a `CryptoServiceUnavailableException`/`AttestationRefusedException` is caught
   and does not propagate out of `ReceiptService`'s own entry method (AC6).
8. A `ChainEventConsumer`-level test (or an inspection-only test, given the restructuring)
   proving the existing dedupe/outcome-update behavior is unchanged, and that `ReceiptService`
   is only invoked after that transaction's own logic completes.

## Constraints

- **Transaction:** `attest()` must never run inside an open transaction (AC5) — the one hard
  constraint this entire task's design revolves around.
- **Thread-safety:** `ReceiptService` has no mutable state beyond its injected collaborators,
  consistent with every other service class in this codebase.
- **Module boundaries:** no `Invoice`/`Payment` entity import from `receipt/` (L12).
- **Null handling:** none of `ReceiptService`'s own inputs are expected to be null from a
  well-formed caller (`ChainEventConsumer`), mirroring the established "trust the caller" stance.
- **Disclosed, not solved:** no retry mechanism exists for a failed `attest()` call (Outputs,
  above) — logged as a known gap, not invented here.

## Open Questions

No blockers. Open Question 1 (receipt scope) resolved directly by the user via
`AskUserQuestion` — **PAID only**. Open Question 2 (transaction boundary) resolved above,
mirroring `onChainTxReorged`'s own already-reviewed precedent exactly, for Phase 3 to
stress-test.
