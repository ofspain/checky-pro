# payment · T26 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi).

**Human sign-off: 2026-10-19.** Every gap was checked against source. Kimi's field names were
correct this time: `InvoiceCreatedPayload`, `PaymentSeenPayload`, `PaymentFinalizedPayload` and
`ReceiptIssuedPayload` were all read before acceptance.

All changes are in `PaymentEndToEndIntegrationTest`. No production code changed, and no test
method was added; the changes extend the existing three methods.

| Gap | Decision | Exact change |
|---|---|---|
| **1**: no bearer check on `DELETE /watches` | **ACCEPTED** | The DELETE verifications in AC1 and AC3 add `.withHeader("Authorization", equalTo(CRYPTO_BEARER))`. Every Crypto Service call is now proven authenticated: token, watch POST, DELETE and attest. |
| **2**: `payment.seen` and `invoice.created` bodies were shallow | **ACCEPTED** | **`invoice.created`:** `invoiceUuid`, `merchantAccountUuid`, `amount` (a string equal to `"1000"`), `payToAddress`, `tokenDecimals` = 6, `reference` = `"e2e"`. **`payment.seen`:** `paymentUuid`, `invoiceUuid`, `chain`, `txHash`, `tokenContractAddress`. |
| **3**: `receipt.issued` body was partial | **ACCEPTED** | Adds `merchantAccountUuid`, `tokenContractAddress`, and a non-null `occurredAt`. |
| **4**: `payment.finalized` values | **ACCEPTED** | `amount` = `"1000"`, `fromAddress` = `"0xfrom"`, `toAddress` = pay-to, `confirmations` = 12, and a non-null `occurredAt`. |
| **5**: outbox rows never checked as published | **ACCEPTED** | New helper `outboxPublished(eventType, aggregateId)`. AC1 waits until all four rows have a non-null `published_at`, each found by its real aggregate id (G2 keys). |
| **6**: no idempotency replay on the real receipt path | **ACCEPTED** (AC1 extended) | After `ATTESTED`, AC1 re-sends the same `chain.tx.finalized`. It then sends a **sentinel** finalized event on the same single-partition topic, with an unknown txHash and a fresh key, and waits for the sentinel in `processed_events`. That proves the replay was consumed first, because the replay's own key was already present and can't show it. Afterwards it checks: 1 `/attest` call, 1 `receipts` row, 1 ledger entry, still `ATTESTED`, and exactly 2 S3 objects (one JSON, one PDF) for this payment (new helper `s3ObjectsForPayment`). |
| **7**: AC2 doesn't rule out extra watch calls | **ACCEPTED** | AC2 verifies 1 watch POST in total (no replacement), 1 DELETE in total, and that DELETE targeting W1. |
| **8**: the PDF check was only the `%PDF` header | **ACCEPTED** (PDFBox) | `Loader.loadPDF(pdfBytes)` parses the stored object, and the test asserts 1 page. This is stronger than Kimi's `%%EOF` suggestion. PDFBox is already a main dependency. |
| **9**: watch `expiresAt` not checked | **ACCEPTED** | `Instant.parse(expiresAt)` must be after a timestamp captured just before invoice creation. |

## Verification

- **`PaymentEndToEndIntegrationTest`:** 3/3 passed, in 27 s.
- **Mutation check (temporary, restored with `cmp`).** I inverted `outboxPublished` to
  `published_at IS NULL`. AC1 then failed, on the Awaitility timeout. Gap 1's header checks are
  covered by Phase 9 Run A's bearer mutation, which used the same `CRYPTO_BEARER` constant.
- **Gap 6 disclosure.** The replay assertion holds because of two independent production guards:
  dedupe on `processed_events`, and `applyFinalized` ignoring a payment that is no longer
  `SEEN`/`CONFIRMING`. Making the test fail would need *both* broken in production code, which
  is out of scope here. The assertion is a regression guard on the composed behaviour. Its
  non-vacuity comes from the sentinel ordering: the replay is consumed before the checks run.
- **Full `mvn -o test`:** 496/496, BUILD SUCCESS.
