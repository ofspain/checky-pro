# payment · T17 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable. The attribution trailer
below uses this session's own current identity (Claude Sonnet 5), not the generic placeholder
text baked into this prompt template's own header.

## Commit title

`payment T17: ReceiptService - attest orchestration and HELD transition (R17-R20)`

## Commit message

```
payment T17: ReceiptService - attest orchestration and HELD transition (R17-R20)

Adds ReceiptService.issueIfEligible(paymentId, triggerEventKey) - the first real caller of
T15's CryptoClient.attest and T16's ReceiptDigest, and the first real exercise of
PaymentState.HELD, an edge PaymentStateMachine has permitted since T09 but nothing had ever
triggered. Guards that the payment is FINALIZED and its invoice is exactly PAID - a real
product-policy decision made directly by the user: a receipt is Themistra's attestation that
the payment correctly satisfied the invoice, so an UNDERPAID/OVERPAID outcome (already recorded
separately per R15) never requests one at all.

Re-reads both the payment and invoice fresh via two new cross-module projections,
PaymentReceiptView and InvoiceReceiptView (mirroring InvoiceWatchView's own T11 precedent -
neither imports the real entity across its module boundary), rather than trusting the
triggering transaction's own already-stale outcome. The attest() call itself runs strictly
outside any transaction, mirroring onChainTxReorged's own established TransactionTemplate split
(T12) for the identical "an outbound HTTP call must never hold a DB transaction open" problem -
ChainEventConsumer.onChainTxFinalized is restructured the same way, its own existing dedupe/
resolve/apply/outcome-update sequence unchanged internally, just now explicitly scoped.

A BLOCKED attest response moves both the payment and invoice to HELD as an atomic pair, via two
new named repository/service methods - a genuine race (a concurrent reorg moving the payment out
of FINALIZED between the eligibility check and this transaction) could otherwise leave exactly
one of the two updated; a split result throws and rolls both back rather than ever being
observed. A 409 (AttestationRefusedException - the signing gate not satisfied) is explicitly
kept distinct from a 200 BLOCKED response: only the latter triggers HELD. A SIGNED result
persists nothing here and does not reach ATTESTED - no Receipt row can exist yet (receipts.
s3_bucket/s3_key are NOT NULL, T18 hasn't written one) - and is carried forward via a new sealed
ReceiptIssueResult rather than discarded.

Two real bugs were found and fixed during review, both verified against source before
accepting: a BLOCKED outcome with a null reason (a response the real wire contract explicitly
allows) crashed the HELD transition's own audit-record map construction; and the HELD
transition's own exception handling only guarded the one exception type it threw itself, not a
genuine database failure from either of its two collaborators - both closed.

Disclosed, not solved here: neither a failed attest() call nor the narrower crash-before-
issueIfEligible-ever-runs window has any retry mechanism - the same structural gap
onChainTxReorged's own replacement-watch registration already discloses for the identical
reason (dedupe() already committed in the first transaction, so Kafka redelivery cannot retry
this step either). No task in the current breakdown (15-21) adds one.

ReceiptServiceTest: 16/16 passing. Full suite: 374/374 passing (348 pre-task baseline + 26 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` (new).
- `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptIssueResult.java` (new).
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentReceiptView.java` (new).
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceReceiptView.java` (new).
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentRepository.java` — new
  `markHeldIfFinalized`.
- `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` — new
  `findReceiptPrimitives`/`markHeldIfFinalized`.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java` — new
  `findReceiptPrimitives`/`markHeldIfPaid`.
- `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java` — new
  `ReceiptService` dependency; `onChainTxFinalized` restructured (no longer `@Transactional` at
  the method level); class-level Javadoc corrected.

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/receipt/ReceiptServiceTest.java` (new)
  — 16 tests.
- `services/payment/src/test/java/com/themistra/payment/payment/PaymentServiceTest.java` — 5
  new tests.
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java` — 5
  new tests.
- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerTest.java`
  — constructor call updated for the new dependency; 3 existing methods strengthened with
  `ReceiptService`-wiring assertions.

No migration file — this task adds no entity, no persistence beyond the existing `payments`/
`invoices` state columns.

## Summary

T17 is where the attestation flow's pieces built since T15 first actually run together. The
hardest design question — surfaced by Kimi's own Phase 3 review, not anticipated in the
original brief — was keeping the two `HELD` updates atomic as a pair despite each being its own
independent, atomic, conditional repository call; the fix (throw-and-roll-back on a split
result) follows directly from the already-established "no entity load, no merge" discipline
this codebase has used since T08, just applied across two aggregates instead of one. Two real
review rounds each found one genuine bug (a null-handling crash, an incomplete exception catch)
that a less adversarial process would likely have shipped.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `mvn -o -pl services/payment -am test-compile`:
  clean throughout every phase.
- `mvn -o -pl services/payment -am test -Dtest=ReceiptServiceTest`: **16/16 passing**.
- `mvn -o -pl services/payment -am test -Dtest=PaymentServiceTest,InvoiceServiceTest`: **66/66
  passing**.
- `mvn -o -pl services/payment -am test -Dtest=ChainEventConsumerTest`: **27/27 passing**.
- `mvn -o -pl services/payment -am test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
  **5/5 passing** — the real Postgres+Kafka integration test (T14), re-confirmed stable after
  every phase of this task, including a live demonstration that `ReceiptService`'s own
  `CryptoServiceUnavailableException` handling genuinely swallows a real `attest()` failure
  against a crypto-service that isn't running in this environment.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **374/374 passing** (348 pre-task baseline + 26 new).

## Specification references

- **Task:** T17 — Receipt issuance, FINALIZED only (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R17 (resolved scope: exactly `PAID`), R18, R19 (partial — the full
  mechanism spans T15/T16/this task), R20.
- **LOCKED decisions:** L2 (receipt only from `FINALIZED`, `ATTESTED` reached only after S3 +
  ledger, both still unbuilt); L1/L12 (continuity — `PaymentStateMachine` unchanged, no
  cross-module entity import).
- **Product decision (not a numbered requirement, decided directly by the user via
  `AskUserQuestion`):** a receipt is only ever requested for an exactly-`PAID` invoice outcome.
