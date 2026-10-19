# payment · T26 · Phase 10 — Test Manifest

Consumes:
- `artifacts/04-frozen-task-brief.md` (FROZEN)
- `artifacts/09-review-resolution.md`

T26 is a test-only task, so its deliverable is the test class. That class was written in
Phase 6 and tightened in Phase 9 (human-approved). This phase adds no further test: every
acceptance criterion and implied boundary below is already covered. `package.md` §8 names no
test for task 26. No production code was touched.

**Class:** `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java`.
It is an integration test under `agents.md:57-58`:
- **Containers:** Testcontainers Postgres and Kafka, plus LocalStack S3.
- **Crypto Service:** WireMock, per `package.md:74`.
- **Clock:** none fixed. It is a real-time end-to-end test, the same as T14's container test.
  The fixed-`Clock` rule applies to unit tests.

## Manifest

### `shouldDriveInvoiceToAttestedReceiptEndToEnd` (AC1)

| Step | Verifies | Requirement / decision |
|---|---|---|
| `POST /api/v1/invoices` with a Bearer token returns `201` `OPEN`, with the pay-to address and the JWT merchant | Creation through the real filter chain, the role converter and `@PreAuthorize` | R1, L10 |
| One watch POST is received: Bearer header; body has `invoiceUuid`, `chain`, `address`, `tokenContractAddress`, and `expectedAmount` as the string `"1000"` | The outbound watch contract (`design.md` §4c), service-to-service auth, decimal string | R1, R6, L6, L10 |
| The invoice holds the acked `watchId` | The watch-ack (`CREATED→WATCHING` has no row; Phase 1 OQ5) | R6 |
| An `invoice.created` outbox row exists, and the record reaches `payments.invoice.created` | Outbox in the same transaction, and the relay to Kafka | R2, R30, L8 |
| `seen`: the payment is `SEEN`; `DELETE /watches/{W1}`; `payments.payment.seen` keyed by `paymentUuid` | First-seen effects | R7, R7a, R8, L14 |
| `confirmed`: the payment is `CONFIRMING` | — | R9 |
| `finalized`: the payment is `ATTESTED`, the invoice `PAID` | The finality and receipt path | R10, R17, R24, L1, L2 |
| One `/attest` call: Bearer header, `chain`, `txHash`, a 64-hex digest equal to `receipts.digest_sha256` | Signing via Crypto Service only | R19, L3 |
| The digest is recomputed independently from the stored S3 JSON, minus the signature fields | The canonical receipt digest | R19, O4 |
| `.json` and `.pdf` exist with the right content types; the PDF starts with `%PDF`; both carry `payment-uuid` and `receipt-uuid` metadata | Receipt storage in both formats | R23 |
| COMPLIANCE retention with a future retain-until on the exact version; a versioned delete returns `403` | WORM | R23, L9 |
| The only ledger `prev_hash` is genesis; 1 entry for the receipt; `LedgerVerifier.verify()` returns `Intact` | Hash-chain append and integrity | R21, L7 |
| `payments.payment.finalized` (key `paymentUuid`; `chain`, `txHash`, amount as a string) | The finalized event | R10, R30, L6 |
| `payments.receipt.issued` (key `receiptUuid`; matching uuids; `receiptUri` = the stored object; `kmsKeyId`; amount as a string) | The receipt event after attestation | R25, R30, L6, L8 |

### `shouldWalkConfirmingBackToSeenOnReorgWithoutIssuingAReceipt` (AC2)

| Step | Verifies | Requirement / decision |
|---|---|---|
| seen → confirmed → reorged; the reorg key is awaited in `processed_events` first | Non-vacuous negatives | L5 (T14 lesson) |
| The state is `SEEN`, with a `CONFIRMING→SEEN` verification record | The reversal and its record | R11, L1, L11 |
| 0 `/attest`, 0 `receipts`, 0 ledger rows, no S3 object with this `payment-uuid` | No receipt before finality | R18, L2, L11 |

### `shouldWalkSeenBackToWatchingOnReorgWithoutIssuingAReceipt` (AC3)

| Step | Verifies | Requirement / decision |
|---|---|---|
| seen → reorged, consumed first | — | L5 |
| The state is `WATCHING`, with a `SEEN→WATCHING` record | The reversal and its record | R12, L1, L11 |
| 1 `DELETE` of W1, 0 of W2; 2 watch POSTs with a Bearer header; the invoice holds W2 | The watch lifecycle across a reorg | R7a, R12, L10, L14 |
| The same no-receipt assertions as AC2 | No receipt before finality | R18, L2, L11 |

## AC4 and AC5

- **AC4:** no `src/main` change. The diff covers the test class and `pom.xml` test scope only.
- **AC5:** the full suite passes **496/496**: 493 plus 3.

## State-transition coverage

| Edge | Covered by |
|---|---|
| `WATCHING→SEEN` | AC1, AC2, AC3 |
| `SEEN→CONFIRMING` | AC1, AC2 |
| `CONFIRMING→FINALIZED` | AC1 |
| `FINALIZED→ATTESTED` | AC1 |
| `CONFIRMING→SEEN` | AC2 |
| `SEEN→WATCHING` | AC3 |

**Not run end-to-end here, and why:**
- `SEEN→FINALIZED`: rejected at Phase 4 (F5). It is unit-covered by
  `PaymentServiceTest.shouldTransitionToFinalizedFromSeenWhenNeverConfirmed`.
- `FINALIZED→HELD` and `ATTESTED→HELD`: owned by T17 and T14, and out of scope.

## Mutation evidence

Every assertion group was seen to fail under a targeted temporary edit:
- **Phase 6:** the digest recomputation, the replacement watch, and the no-receipt guard.
- **Phase 9:** the bearer header, the metadata key, and the "W2 not deleted" check.
