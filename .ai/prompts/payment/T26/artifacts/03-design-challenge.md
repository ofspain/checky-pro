# payment · T26 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/requirements.md`, `spec/payment-service/design.md`, and the real service wiring.

---

## Finding 1 — AC1 does not assert S3 Object Lock retention mode or deletion prevention

- **Issue:** The brief states that R23/L9 require JSON and PDF objects in the Object Lock bucket in COMPLIANCE mode, and the LocalStack prototype confirmed retention works. However, AC1 only asserts that the objects exist. A regression that wrote the objects without COMPLIANCE retention (e.g., a misconfigured `ReceiptStore` or test S3 client) would pass AC1.
- **Severity:** Medium — L9 is a load-bearing security property of the receipt store.
- **Evidence:** TIB AC1 step 4; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 96-112.
- **Recommended brief amendment:** Add to AC1: "Each S3 object has `ObjectLockMode=COMPLIANCE` and a non-null `RetainUntilDate`; an attempted `deleteObject` on either object returns 403 AccessDenied."

---

## Finding 2 — AC1 does not verify the ledger entry's own hash self-consistency

- **Issue:** AC1 checks that exactly one `ledger_entries` row exists and that its `prev_hash` is the genesis/prior-head value, but it does not check that the row's `entry_hash` is the SHA-256 of its canonical payload concatenated with `prev_hash`. A corrupt `entry_hash` would not be detected by the end-to-end test.
- **Severity:** Medium — R21/R22 are central to the ledger's tamper-evidence claim.
- **Evidence:** TIB AC1 step 4; `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerService.java`; `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerVerifier.java`.
- **Recommended brief amendment:** Add an assertion that recomputing `SHA-256(canonicalPayload + prevHash)` equals the stored `entry_hash` (the same recomputation `LedgerVerifier` uses).

---

## Finding 3 — AC1 does not assert the `payment.seen` and `payment.finalized` Kafka events

- **Issue:** The forward flow is required to emit `payment.seen` (R8) and `payment.finalized` (R10). AC1 only observes `receipt.issued` on Kafka. Missing these events would break downstream consumers and would not fail the test.
- **Severity:** Medium — R8/R10 are explicit requirements, and the end-to-end test is the natural place to prove they are published through the outbox/relay.
- **Evidence:** TIB Business Rules (R8, R10); `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java` lines 121 and 262.
- **Recommended brief amendment:** Add Kafka assertions for at least one record on `payments.payment.seen` and one on `payments.payment.finalized`, keyed by `paymentUuid`/`invoiceUuid` respectively.

---

## Finding 4 — AC1 does not verify the digest sent to `/attest` is the canonical receipt digest

- **Issue:** AC1 asserts that the `/attest` request carries a 64-hex `receiptDigestSha256` and that the `receipts` row's digest equals it. It does not assert that the digest is the correct canonical digest of the receipt payload. A bug in `ReceiptDigest` or `ReceiptService` that produced a consistent but wrong digest would pass.
- **Severity:** Medium — R19 says the signature is obtained for the canonical digest.
- **Evidence:** TIB AC1 step 4; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptDigest.java`; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptService.java` line 131.
- **Recommended brief amendment:** Have the test independently compute the expected digest using `ReceiptDigest.digest(...)` (or by re-running the canonical JSON serialization) and assert the WireMock `/attest` request body contains that exact value.

---

## Finding 5 — The forward flow only covers `SEEN → CONFIRMING → FINALIZED`, not direct `SEEN → FINALIZED`

- **Issue:** R10 explicitly allows a `chain.tx.finalized` event to arrive without a prior `chain.tx.confirmed` (`SEEN -> FINALIZED`). The brief's forward flow always inserts `confirmed`. The reorg tests also rely on `confirmed`. The direct transition is therefore untested end-to-end.
- **Severity:** Low — the transition is unit-tested in T09/T11, but end-to-end wiring is the point of T26.
- **Evidence:** `spec/payment-service/design.md` §4c state machine; TIB AC1.
- **Recommended brief amendment:** Either add a fourth test method `shouldDriveInvoiceToAttestedReceiptWithoutConfirmedEvent` or explicitly note in Open Questions that this edge is covered by unit tests and deferred here.

---

## Finding 6 — The test uses `webEnvironment = MOCK` rather than a real port

- **Issue:** `@AutoConfigureMockMvc` with `MOCK` exercises the filter chain and dispatcher but not the full embedded servlet container/network stack. For a task framed as "end-to-end integration," a `RANDOM_PORT` environment with `TestRestTemplate` would be more faithful.
- **Severity:** Low — MockMvc is sufficient for the requirements listed, and the spec/auth precedents use it.
- **Evidence:** TIB Locked Decision D4.
- **Recommended brief amendment:** Keep MockMvc but document the rationale (the filter chain and `@PreAuthorize` wiring are what matter; HTTP transport is owned by Spring Boot and tested elsewhere) or switch to `RANDOM_PORT` if the human prefers a stricter end-to-end definition.

---

## Finding 7 — No assertion that the receipt PDF and JSON objects are distinct

- **Issue:** `ReceiptStore` writes both `{prefix}.json` and `{prefix}.pdf`. AC1 checks both exist, but does not check they are different objects (one could overwrite the other if the keys accidentally collided). The store's logic prevents this, but the test should prove it.
- **Severity:** Low — a key-collision bug would be obvious, but the test should close the loop.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java` lines 96-112.
- **Recommended brief amendment:** Assert that the two S3 objects have different ETags/lengths (PDF is expected to be larger than JSON).

---

## Summary

T26 is a well-scoped capstone integration test. The highest-priority amendments before freezing are Finding 1 (S3 COMPLIANCE assertion) and Finding 2 (ledger hash self-consistency), because they protect load-bearing security properties that AC1 currently only weakly checks. Findings 3–7 are smaller completeness and fidelity gaps that should be dispositioned at the Phase 4 gate.
