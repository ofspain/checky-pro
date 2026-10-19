# payment · T26 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief (`artifacts/04-frozen-task-brief.md`), `spec/payment-service/agents.md`, and the implemented `PaymentEndToEndIntegrationTest`.

---

## Finding 1 — `DELETE /watches` is not verified to target the original watch

- **Issue:** The forward-flow assertion only checks that one `DELETE` request was sent to `/internal/v1/watches/.*`. It does not verify that the path ends with `firstWatchId`. In the reorg AC3 test, the original watch must be deleted before the replacement watch is registered; without this check, a bug that deleted the replacement watch (or the wrong ID) would not fail the test.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java` lines 368 and 475.
- **Recommendation:** Change the verification to `deleteRequestedFor(urlEqualTo(WATCHES_PATH + "/" + firstWatchId))` in AC1 and to the same path in AC3's first-watch deletion assertion.
- **Confidence:** Medium — the current assertion is weaker than the requirement.

---

## Finding 2 — `POST /watches` request body is not asserted

- **Issue:** The tests verify the count and response parsing of `/watches` calls, but never inspect the request body. A regression in `CryptoClient` or the event-to-request mapping (e.g., wrong `chain`, `tokenContractAddress`, or `txHash`) would still pass because WireMock matches only on URL.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 359, 475.
- **Recommendation:** Add a body matcher to the first `/watches` stub/verification asserting the JSON contains the expected `chain`, `tokenContractAddress`, `payToAddress`, and a `reference` matching the invoice.
- **Confidence:** Medium — T26 is the right place to prove the outbound contract.

---

## Finding 3 — `invoice.created` Kafka event is never asserted on the broker

- **Issue:** AC1 checks the `outbox` row for `invoice.created`, but it does not prove that the outbox relay actually published that event to Kafka. The relay is central to the architecture (R2/R30), and a failure in the relay or topic configuration would only be caught for `payment.seen`, `payment.finalized`, and `receipt.issued`.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 361, 369, 428-430; `spec/payment-service/requirements.md` R2, R30.
- **Recommendation:** Add `awaitOutboundRecord("payments.invoice.created", invoiceUuid.toString())` to AC1, mirroring the pattern used for the other outbound events.
- **Confidence:** Medium — the test claims to prove end-to-end Kafka wiring but skips one of the four domain events.

---

## Finding 4 — `assertWormCompliance` reads retention without specifying the version ID

- **Issue:** `s3.getObjectRetention(...)` is called with only `bucket` and `key`, so it returns the retention of the current (latest) version. The same assertion then checks `versionId` is non-blank and tries to delete that exact version. If a later version were ever created, the retention read and the delete attempt would target different versions.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 541-549.
- **Recommendation:** Use `GetObjectRetentionRequest` with `.versionId(versionId)` so the retention check and the deletion resistance check target the same object version.
- **Confidence:** Low — the test writes exactly one version per key, but making it explicit removes a subtle coupling.

---

## Finding 5 — `receipt.issued` and `payment.finalized` body assertions are shallow

- **Issue:** `awaitOutboundRecord` for these topics asserts only the record key and a couple of fields (`receiptUuid`, `paymentUuid`, `invoiceUuid`). It does not assert the S3 key, digest, or signature fields that downstream consumers rely on. A malformed payload that omitted `s3Key` or `digestSha256` would pass.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 428-434.
- **Recommendation:** Extend the JSON assertions to include `s3Key`, `digestSha256`, and `signedAt` for `receipt.issued`, and `chain`/`txHash` for `payment.finalized`.
- **Confidence:** Low — the critical fields are already validated elsewhere, but the Kafka contract deserves the same coverage.

---

## Finding 6 — The S3 "no receipt object" check depends on an invisible production metadata convention

- **Issue:** `assertNoReceiptIssued` iterates S3 objects and checks `metadata.get("payment-uuid")`. This only works because `ReceiptStore.buildPutRequest` happens to set that metadata. There is no assertion in the test that proves the production code sets it, and no comment explaining the dependency. If `ReceiptStore` dropped that metadata, the reorg tests would still pass even though receipt objects existed.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 561-566; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 168-171.
- **Recommendation:** Either add a forward-flow assertion that the stored JSON object's metadata contains `payment-uuid` (so the convention is tested), or add a comment in `assertNoReceiptIssued` referencing `ReceiptStore`'s metadata keys.
- **Confidence:** Low — the convention is currently correct, but the coupling is not obvious.

---

## Finding 7 — `hasSize(1)` on outbound records is stricter than the at-least-once delivery guarantee

- **Issue:** This aligns with Phase 7 self-review Finding 2. `OutboxRelay` guarantees at-least-once delivery. Under slow CI, a transient send timeout can produce a duplicate Kafka record even though the database state is correct. `awaitOutboundRecord` then fails with `hasSize(1)`, making the test potentially flaky while the duplicate itself is valid L8 behavior.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 526-532; `services/payment/src/main/java/com/themistra/payment/events/OutboxRelay.java`.
- **Recommendation:** Relax the Kafka assertion to `isNotEmpty()` and keep the "exactly one" business invariant pinned at the database layer (`receipts` row count, ledger count, single `/attest` call).
- **Confidence:** Low — the happy path is deterministic today, but the strictness is at odds with the documented delivery guarantee.

---

## Finding 8 — No assertion that the outbound bearer token was sent

- **Issue:** This aligns with Phase 7 self-review Finding 1. The WireMock verifications for `/watches` and `/attest` do not require the `Authorization: Bearer e2e-crypto-token` header. A regression that disabled `CryptoClient`'s token interceptor would not fail the test.
- **Evidence:** `PaymentEndToEndIntegrationTest.java` lines 359, 368, 381.
- **Recommendation:** Add `.withHeader("Authorization", equalTo("Bearer e2e-crypto-token"))` to the relevant `postRequestedFor` verifications.
- **Confidence:** Low — `CryptoClientTest` covers the interceptor, but T26 should prove it is wired end-to-end.

---

## Summary

The end-to-end test is comprehensive and the three acceptance criteria are correctly mapped. The most valuable Phase 9 touches are Finding 1 (assert the watch deletion path), Finding 2 (assert the `/watches` request body), and Finding 3 (assert `invoice.created` on Kafka). Findings 4–8 are smaller completeness, coupling, or flakiness notes. No production code changes are needed.
