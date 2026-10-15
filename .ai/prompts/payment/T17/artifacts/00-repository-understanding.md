# payment · T17 · Phase 0 — Repository Understanding

Artifact: `.ai/prompts/payment/T17/artifacts/00-repository-understanding.md`

## 1. Architecture summary

`payment-service` (`services/payment`, package `com.themistra.payment`). T15 built
`CryptoClient.attest(...)`; T16 built `CanonicalJson`/`ReceiptDigest` (`receipt/` package, one
file so far: `ReceiptPayload`, `ReceiptDigest`). Nothing yet calls either — T17 is the first real
caller of both. The `receipts` table (T02's own baseline schema) exists but has never been
written to. `ChainEventConsumer.onChainTxFinalized` (T13) marks the payment `FINALIZED` and the
invoice's own `PAID`/`UNDERPAID`/`OVERPAID` outcome, then returns — it does **not** call anything
receipt-related today; there is no existing hook point.

## 2. Existing code this task touches

**Already exists, read/extend:**
- `receipt/ReceiptDigest.java`, `receipt/ReceiptPayload.java` (T16) — the digest mechanism this
  task will finally call.
- `common/CryptoClient.java` (T15) — `attest(...)`, returning `AttestationResult.Signed`/
  `Blocked`, throwing `AttestationRefusedException`/`CryptoServiceUnavailableException`.
- `payment/PaymentState.java` — already has `HELD`, and `PaymentStateMachine` already permits
  exactly `FINALIZED → HELD` (R20) and `ATTESTED → HELD` (L11) — confirmed via direct read,
  decided at T09's own Phase 4 gate, specifically anticipating this task's own need.
- `payment/PaymentRepository.java` — has no `FINALIZED → HELD` atomic update method yet
  (confirmed by listing every `@Modifying` method in the file) — net new for this task.
- `invoice/InvoiceState.java` — already has `HELD`.
- `invoice/InvoiceService.java`/`InvoiceRepository.java` — `updateStateIfCurrentState` exists
  (T08, generic, takes exactly one expected-current-state) but no method or call site moves an
  invoice to `HELD` yet — net new.
- `consumer/ChainEventConsumer.java` — `onChainTxFinalized` (T13) is the only plausible existing
  trigger point for "a payment reached `FINALIZED`," but currently does nothing receipt-related.

**Does not exist yet (net new):**
- `receipt/ReceiptService.java` — this task's own primary deliverable.
- Any wiring that actually calls `ReceiptService` when a payment reaches `FINALIZED`.
- Any `PaymentRepository`/`InvoiceRepository` method transitioning to `HELD`.
- `receipt/Receipt.java`/`ReceiptRepository.java` — **confirmed NOT part of this task's own
  scope per its own text** ("Implement `ReceiptService`: guard... call `/attest`... honour a
  `BLOCKED` outcome"), yet the `receipts` table's own schema (`design.md`, T02) has
  `s3_bucket VARCHAR(128) NOT NULL` and `s3_key VARCHAR(256) NOT NULL` — meaning a `Receipt` row
  **cannot be inserted at all** until the S3 write (T18) has produced a real key. L2's own text
  confirms this is deliberate, not an oversight: "`ATTESTED` is set only after the signature is
  obtained, the receipt is stored in S3, and the ledger entry is appended, **all durably**." T17
  only covers obtaining the signature — the first of those three durable steps.

## 3. Established patterns to follow

- **Never call an outbound HTTP client from inside an open transaction.** Already established
  for `CryptoClient.registerWatch` (`InvoiceController` calls it before opening
  `InvoiceService.create`'s own transaction) and for `CryptoClient.unregisterWatch` inside
  `onChainTxReorged` (T12 uses a narrow `TransactionTemplate` split specifically so the HTTP call
  itself is outside any transaction). `CryptoClient.attest` is the same category of call —
  `ReceiptService` cannot be invoked from inside `onChainTxFinalized`'s own `@Transactional`
  method without repeating this exact, already-twice-solved problem.
- **Atomic, conditional `UPDATE ... WHERE state = :expected`, never load-mutate-save.** Every
  state transition in this codebase (`Payment`, `Invoice`) follows this pattern since T08's own
  corrected lesson. A new `HELD` transition for each entity should follow it too.
- **`InvoiceService.markOutcomeIfOpenOrExpired`'s own two-attempt shape** (T13: try `OPEN`, then
  `EXPIRED`) is the direct precedent for "more than one plausible current state can legitimately
  precede this transition" — relevant here since the invoice could legitimately be `PAID`,
  `UNDERPAID`, or `OVERPAID` by the time a `BLOCKED` attest response arrives (the invoice outcome
  is set synchronously, in the same `onChainTxFinalized` transaction, before any attest call
  could even begin).

## 4. Testing conventions

- Unit, mocked collaborators, for `ReceiptService`'s own business logic (mirrors
  `PaymentServiceTest`/`InvoiceServiceTest`'s own established shape — no Spring context).
- No Testcontainers needed for this task's own new code specifically (no new entity, no
  migration) — though the existing `SchemaBaselineMigrationIntegrationTest` may need revisiting
  if this task adds any new grant (unlikely, since no new table is written to).

## 5. Known gaps / unknowns

**Genuine, real open question for Phase 1/2 — not resolved by anything in the spec package
itself:** R17 says "a payment is in `FINALIZED` **and satisfies its invoice**." R15 explicitly
distinguishes `PAID` (exact match) from `UNDERPAID`/`OVERPAID` and says the invoice is never
marked `PAID` on an underpayment — but says nothing about whether a receipt should still be
issued for an `UNDERPAID`/`OVERPAID` outcome. I do not know whether "satisfies its invoice"
means exactly `PAID`, or any of `PAID`/`UNDERPAID`/`OVERPAID` (i.e., any outcome where a
real payment was matched and finalized, regardless of amount correctness).

**Genuine open question, directly consequential for this task's own design:** nothing in this
codebase currently calls anything when a payment reaches `FINALIZED` beyond the existing
invoice-outcome update. Wiring `ReceiptService` to actually run is therefore part of this task's
own scope (the task statement doesn't explicitly say so, but "Implement `ReceiptService`" with no
caller would leave the task's own named tests — `shouldIssueReceiptOnlyFromFinalizedState` etc.
— exercising a class nothing in production ever calls, which doesn't fit this task's own stated
purpose). The real design question is **where** that call happens without reintroducing the
"HTTP call inside an open transaction" anti-pattern `onChainTxReorged` already solved once with
`TransactionTemplate`.

**Known, not a gap — confirmed above, not assumed:** T17 cannot persist a `Receipt` row at all
(the schema's own `NOT NULL` S3 columns make this structurally impossible before T18 exists).
This task's own `SIGNED` path must therefore do something short of full persistence — the exact
shape (return a result to an in-memory caller? hold state some other way?) is a Phase 2 design
decision, not fixed by the spec.
