# payment · T26 · Phase 6 — Implementation Notes

Consumes:
- `artifacts/04-frozen-task-brief.md` (FROZEN)
- `artifacts/05-implementation-plan.md`

The task is test-only by nature, so its code is test code. **No production file changed.**

## What changed

| File | Change | Plan / AC |
|---|---|---|
| `services/payment/pom.xml` | Two test-scope dependencies, with one T26 comment: `org.wiremock:wiremock-standalone:3.9.1` (fetched once online at Phase 5; resolves under `mvn -o` from then on) and `org.testcontainers:localstack` (BOM-managed, 1.21.4). | Plan §modify, D1, D2 |
| `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java` (new) | **Infrastructure:** Testcontainers Postgres (`postgres:16-alpine`), Kafka (`apache/kafka:3.8.0`) and LocalStack 3.8 S3, plus a static `WireMockServer` (dynamic port). The context shape copies T14 (D6). It adds a nested `@TestConfiguration` with a `@Primary S3Client localStackS3ClientBean` and a `@MockBean JwtDecoder` that returns a `MERCHANT` `Jwt`. **Startup (`@BeforeAll`):** creates the 8 topics with `AdminClient`, and creates the Object Lock bucket. | Plan §create, D1–D6 |
| (same file) | **Three tests:** `shouldDriveInvoiceToAttestedReceiptEndToEnd`, `shouldWalkConfirmingBackToSeenOnReorgWithoutIssuingAReceipt`, `shouldWalkSeenBackToWatchingOnReorgWithoutIssuingAReceipt`. | AC1, AC2, AC3 |

`T01SkeletonRegressionTest` was **not** touched. Its dependency tripwires forbid neither
artifact, as checked at Phase 5.

## AC mapping (what each assertion proves)

**AC1, forward flow:**
- **Invoice creation:**
  - The request is a MockMvc `POST` with a Bearer header, through the real filter chain and
    `@PreAuthorize`. It returns `201` with `OPEN`, the pay-to address and the JWT's merchant
    (R1, L10).
  - WireMock receives exactly 1 `POST /watches`, and the invoice stores the acked `watchId`
    (R6).
  - An `invoice.created` outbox row exists (R2).
- **Seen:**
  - The payment becomes `SEEN` (R7).
  - WireMock receives `DELETE /watches/{W1}` (R7a/L14).
  - `payments.payment.seen` arrives on Kafka, keyed by `paymentUuid` (R8, F3).
- **Confirmed:** the payment becomes `CONFIRMING` (R9).
- **Finalized:** the payment becomes `ATTESTED` and the invoice `PAID` (R10, R17, R24).
- **Attest call:** exactly 1 `/attest` call, carrying this payment's `chain` and `txHash` and a
  64-hex digest. The digest equals `receipts.digest_sha256` (R19).
- **Digest recomputation (F4):** the stored S3 JSON, minus `signature`/`kmsKeyId`/`signedAt`,
  is run through `CanonicalJson` and SHA-256. The result equals the `/attest` digest.
- **S3 objects:** `.json` is `application/json`, and `.pdf` is `application/pdf` with bytes
  beginning `%PDF` (F7).
- **WORM (R23/L9, F1):** each object has `COMPLIANCE` retention with a future retain-until, and a
  versioned delete throws `S3Exception` with `403`.
- **Ledger (R21/L7, F2):** the only `prev_hash` is the genesis value (64 zeros), there is 1 entry
  for the receipt, and `LedgerVerifier.verify()` returns `Intact`.
- **Outbound events (R10, R25, R30, F3):**
  - `payments.payment.finalized` arrives, keyed by `paymentUuid`.
  - `payments.receipt.issued` arrives, keyed by `receiptUuid`; its `receiptUuid`,
    `paymentUuid` and `invoiceUuid` match the persisted rows.

**AC2, reorg from `CONFIRMING`:**
- The flow is seen, then confirmed, then reorged. The test waits for the reorg key in
  `processed_events` first, so the negative assertions can't pass vacuously.
- The payment ends in `SEEN`, with a `CONFIRMING→SEEN` verification record (R11).
- **No receipt was issued (R18/L2/L11):**
  - 0 `/attest` calls
  - 0 `receipts` rows
  - 0 ledger rows
  - no S3 object whose `payment-uuid` metadata matches this payment

**AC3, reorg from `SEEN`:**
- The flow is seen, then reorged, with the same consumed-first wait as AC2.
- The payment ends in `WATCHING`, with a `SEEN→WATCHING` record (R12).
- There are 2 `POST /watches` in total, and the invoice now holds the replacement `watchId` (W2,
  through the WireMock scenario).
- The same no-receipt assertions as AC2 hold.

## Verification

- **This class alone:** 3/3 passed, in 28 s.
- **Mutation checks.** Each edit was temporary, and the file was restored from a scratchpad
  copy (`cmp` confirmed it).
  - **Run 1** made two edits:
    - stopped stripping `signedAt` before recomputing the digest
    - expected the first `watchId` instead of the replacement one in AC3

    Result: AC1 and AC3 failed, and AC2 still passed.
  - **Run 2** called `assertNoReceiptIssued` at the end of AC1, on a payment that does have a
    receipt.

    Result: it failed with `Expected exactly 0 requests matching … /internal/v1/attest … but
    received 1`. So the no-receipt check isn't vacuous.
- **Full `mvn -o test`:** 496/496, BUILD SUCCESS. That is 493 plus the 3 new tests (AC5).

## Deviations

1. **Shutdown log noise.** This is disclosed, but it is not a failure.
   - After the class finishes, the log shows `OutboxRelay` errors:
     `JDBCConnectionException … An I/O error occurred while sending to the backend`.
   - Cause: Testcontainers stops the Postgres container at the end of the class, while the
     cached Spring context's `@Scheduled` relay is still polling until JVM shutdown.
   - It affects no assertion, and every test result is green. It is the same lifecycle T14's
     container-backed test has. Phase 7 records it as a finding.
2. **Plan wording versus implementation, for the AC2/AC3 S3 check.**
   - The plan said "`listObjectsV2` under the prefix returning 0".
   - That can't work, because WORM objects written by earlier tests can't be cleaned up.
   - The implementation lists the prefix and asserts that no object's `payment-uuid` metadata
     equals this payment's UUID. That keeps the same intent, scoped correctly.

There are no other deviations, and no file outside the frozen Files section was touched.
