# payment · T26 · Phase 9 — Review Resolution

Consumes:
- `artifacts/07-self-review.md`
- `artifacts/08-independent-review.md` (Kimi)

**Human sign-off: 2026-10-19.** Every comment was checked against source before disposition.
Kimi's field names were wrong in three places and were corrected before the human decided.

All changes are in `PaymentEndToEndIntegrationTest`. **No production code changed.**

## Resolution log

| Comment | Decision | Verification against source | Exact change |
|---|---|---|---|
| **Kimi F1**: the DELETE is not checked against the original watch | **ACCEPTED (AC3 only)** | **Partly wrong.** AC1 already used `urlEqualTo(WATCHES_PATH + "/" + firstWatchId)`. AC3 had no DELETE check at all, so the gap there is real. | AC3 verifies 1 `DELETE /watches/{W1}` and 0 `DELETE /watches/{W2}`, so the replacement watch stays live. |
| **Kimi F2**: the `POST /watches` body is not checked | **ACCEPTED, corrected** | **Kimi's fields were wrong.** The body is `RegisterWatchRequest(invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt)`; it has no `reference` and no `txHash`. | AC1 reads the single logged watch request and checks four things. `invoiceUuid` equals the 201's value. `chain` and `tokenContractAddress` are as sent. `address` is the pay-to address. `expectedAmount` is the *string* `"1000"` (L6). |
| **Kimi F3**: `invoice.created` is never seen on Kafka | **ACCEPTED** | True. | The assertion consumer also subscribes to `payments.invoice.created`. AC1 awaits a record keyed by `invoiceUuid`. All four outbox events are now proven on the broker. |
| **Kimi F4**: the retention read doesn't name the version | **ACCEPTED** | True. The read targeted the latest version, while the delete attempt named a specific `versionId`. | `getObjectRetention(...versionId(versionId))`. The `versionId` non-blank check moved before the read. |
| **Kimi F5**: the `receipt.issued` / `payment.finalized` payload checks are shallow | **ACCEPTED, corrected** | **Kimi's fields were wrong.** `ReceiptIssuedPayload` has no `s3Key`, `digestSha256` or `signedAt`. Its fields are `receiptUri`, `kmsKeyId` and `amount`. | **`receipt.issued`:** `receiptUri` = `s3://e2e-receipts/<jsonKey>`, which is the actual stored object; `kmsKeyId` = `e2e-kms-key`; `amount` is the string `"1000"`. **`payment.finalized`:** `chain`, `txHash`, and `amount` as a string. |
| **Kimi F6**: the S3 no-receipt check depends on unproven `payment-uuid` metadata | **ACCEPTED** | True. `ReceiptStore.buildPutRequest` sets `receipt-uuid`, `invoice-uuid` and `payment-uuid`, but nothing tested that. If the metadata were missing, `assertNoReceiptIssued`'s S3 loop would pass blind. | AC1 asserts that both stored objects carry `payment-uuid` and `receipt-uuid` metadata matching the persisted rows. |
| **Kimi F7** (= self-review F2): `hasSize(1)` is stricter than at-least-once delivery | **ACCEPTED: relax** | True. `OutboxRelay` legitimately resends after a send timeout (`OutboxRelay:76-90`). | `awaitOutboundRecord` now waits for at least one record. Any records for the key must have identical payloads. "Exactly one receipt" (R17) stays pinned by the single `receipts` row, the single `/attest` call and the single ledger entry. |
| **Kimi F8** (= self-review F1): the outbound bearer token is not checked | **ACCEPTED** | True. | **AC1:** the watch call and the attest call each carry `Authorization: Bearer e2e-crypto-token`. **AC3:** both watch POSTs, the original and the replacement, are verified `.withHeader(...)`. |
| **Self-review F3**: shutdown log noise | No change (info) | — | None |
| **Self-review F4**: genesis `prev_hash` depends on cleanup | No change (info) | `LedgerVerifier.verify()` is the order-independent check either way. | None |

## Verification

- **`PaymentEndToEndIntegrationTest`:** 3/3 passed, in 27 s.
- **Mutation checks.** The edits were temporary and restored from a scratchpad copy; `cmp`
  confirmed the restore.
  - **Run A:** an expected bearer token of `"Bearer wrong-token"`. AC1 and AC3 failed, which
    proves F8. AC2 still passed, because it makes no header check.
  - **Run B:** two edits at once. Metadata key `payment-id` failed AC1 (F6), and expecting 1
    DELETE of W2 failed AC3 (F1). AC2 still passed.
- **Full `mvn -o test`:** 496/496, BUILD SUCCESS. No test count changed; every change tightened
  an existing test.
