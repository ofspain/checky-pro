# payment · T26 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the final `PaymentEndToEndIntegrationTest` against the frozen acceptance criteria and event payloads.

---

## Gap 1 — `DELETE /watches` bearer token is not asserted

- **Why it matters:** The outbound service-to-service auth guard (L10) applies to every Crypto Service call, including watch unregistering. A regression that dropped the auth header from `CryptoClient.deleteWatch` would not fail AC1 or AC3.
- **Suggested test:** Add `.withHeader("Authorization", equalTo(CRYPTO_BEARER))` to the `deleteRequestedFor` verifications at lines 383 and 509.

---

## Gap 2 — `payment.seen` and `invoice.created` event body assertions are shallow

- **Why it matters:** These events are part of the public outbox contract (R8, R2, R30). The test currently proves only that a record with the right key reaches Kafka. A payload that dropped `chain`, `txHash`, or `invoiceUuid` would pass.
- **Suggested test:** In AC1, after `awaitOutboundRecord("payments.payment.seen", ...)`, parse the value and assert `paymentUuid`, `invoiceUuid`, `chain`, `txHash`, and `tokenContractAddress`. Do the same for `payments.invoice.created`, asserting `merchantAccountUuid`, `amount`, `payToAddress`, `tokenDecimals`, and `reference`.

---

## Gap 3 — `receipt.issued` event body is not fully asserted

- **Why it matters:** `ReceiptIssuedPayload` contains `merchantAccountUuid`, `tokenContractAddress`, and `occurredAt` in addition to the fields already checked. Downstream consumers may depend on them.
- **Suggested test:** Add assertions for `merchantAccountUuid`, `tokenContractAddress`, and `occurredAt` in the existing `receipt.issued` body check.

---

## Gap 4 — `payment.finalized` event body value assertions are incomplete

- **Why it matters:** The test checks `chain`, `txHash`, and that `amount` is textual, but does not assert the actual values of `amount`, `fromAddress`, `toAddress`, `confirmations`, or `occurredAt`. A wrong `amount` or `toAddress` would pass.
- **Suggested test:** Assert `amount` equals `"1000"`, `fromAddress` equals the finalized event's `fromAddress`, `toAddress` equals `PAY_TO_ADDRESS`, `confirmations` equals `12`, and `occurredAt` is present.

---

## Gap 5 — No test verifies outbox rows are marked published

- **Why it matters:** The outbox pattern relies on the relay updating rows from unpublished to published. The test asserts Kafka records and outbox counts, but never checks that the rows' `published_at` timestamps are non-null. A silently failing mark-published update could leave the table growing while Kafka appears healthy.
- **Suggested test:** After awaiting each outbound Kafka record, query `payments.outbox` for the matching `event_type` + `aggregate_id` and assert `published_at IS NOT NULL`.

---

## Gap 6 — No idempotency replay test

- **Why it matters:** `processed_events` and the `chain:txhash:eventtype` key (L5) exist precisely to tolerate duplicate event delivery. The test sends each chain event once. A second delivery of `chain.tx.finalized` should not produce a second `/attest` call, receipt, ledger row, or S3 object.
- **Suggested test:** Add a new test or extend AC1: after the flow completes, re-send the original `chain.tx.finalized` event and assert that `/attest` count stays at one and no additional receipt/ledger/S3 artifacts appear.

---

## Gap 7 — AC2 does not guard against an unwanted replacement watch or second deletion

- **Why it matters:** A reorg from `CONFIRMING` back to `SEEN` should leave the payment `SEEN` without registering a replacement watch (that's only for `SEEN→WATCHING`). The current AC2 verifies no receipt, but does not verify that no second `POST /watches` and no second `DELETE /watches` occurred.
- **Suggested test:** Add to AC2:
  - `WIREMOCK.verify(1, postRequestedFor(urlEqualTo(WATCHES_PATH)))` (still only the original registration)
  - `WIREMOCK.verify(1, deleteRequestedFor(urlEqualTo(WATCHES_PATH + "/" + firstWatchId)))` (no re-delete)

---

## Gap 8 — PDF object is only checked for the `%PDF` magic bytes

- **Why it matters:** A truncated or corrupted PDF could still start with `%PDF`. The test asserts content type and magic bytes, but not that the object is a complete PDF.
- **Suggested test:** Assert the PDF byte array ends with `%%EOF` and has a minimum reasonable length (e.g., > 100 bytes), or parse it with a PDF box sanity check.

---

## Gap 9 — `RegisterWatchRequest` `expiresAt` is not asserted

- **Why it matters:** The outbound watch contract includes `expiresAt` (`design.md` §4c). The test currently checks `invoiceUuid`, `chain`, `address`, `tokenContractAddress`, and `expectedAmount`, but skips `expiresAt`.
- **Suggested test:** Assert that `watchBody.get("expiresAt").asText()` is a valid ISO-8601 instant and is after the invoice creation time.

---

## Summary

T26's test suite is strong: it covers the forward and both reorg flows, verifies WORM, ledger integrity, digest correctness, and service-to-service auth. The highest-value additions are Gap 1 (auth on deletes), Gap 5 (outbox published state), and Gap 6 (idempotency replay). Gaps 2–4, 7–9 are payload-completeness notes that would make the suite even more regression-resistant without adding much cost.
