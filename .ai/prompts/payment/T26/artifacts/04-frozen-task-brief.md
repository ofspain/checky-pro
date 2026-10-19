# payment · T26 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**: approved by the human on 2026-10-19. Downstream phases may not renegotiate
it.

Consumes:
- `artifacts/02-task-implementation-brief.md` (TIB)
- `artifacts/03-design-challenge.md` (Kimi)

Every finding was checked against source before disposition. All paths are relative to
`services/payment/src/main/java/com/themistra/payment/`.

## Phase 3 dispositions

| # | Finding | Verified against source | Disposition |
|---|---|---|---|
| F1 | AC1 asserts only that the S3 objects exist, not COMPLIANCE or delete prevention. | **Partly wrong.** The TIB's AC1 already required "each with COMPLIANCE retention". Kimi's "plain `deleteObject` returns 403" is also wrong for a versioned (Object Lock) bucket: a plain delete only adds a delete marker and succeeds. Only a **versioned** delete is refused; the Phase 2 LocalStack probe showed `403 AccessDenied`. | **ACCEPTED, corrected.** For both objects, AC1 asserts `ObjectLockMode=COMPLIANCE` and a non-null retain-until in the future (via `getObjectRetention`). A `deleteObject` naming the object's `versionId` must fail with `403`. |
| F2 | The ledger entry's own hash is never recomputed. | True. The real `receipt/ledger/LedgerVerifier` (`@Component`, `verify()` returning `Intact` or `Broken`) recomputes the whole chain. It is a service, not an entity, so L12 allows calling it. | **ACCEPTED.** AC1 asserts that `ledgerVerifier.verify()` is a `LedgerVerificationResult.Intact`. |
| F3 | `payment.seen` and `payment.finalized` are not observed on Kafka. | True. **Kimi's key is wrong.** Both events are keyed by `paymentUuid` (`payment/PaymentService:121`, `:262`), not `invoiceUuid` for finalized. | **ACCEPTED, corrected.** AC1 asserts one record each on `payments.payment.seen` and `payments.payment.finalized`, keyed by the payment's `paymentUuid`. |
| F4 | The `/attest` digest is never proven to be the canonical digest. | True. `ReceiptDigest.digest` is the SHA-256 hex of `CanonicalJson.serialize` over 12 fields. `ReceiptStore`'s stored JSON is those same 12 fields plus `signature`, `kmsKeyId` and `signedAt`. | **ACCEPTED.** The test reads the stored S3 JSON and removes the 3 signature fields. It then recomputes `SHA-256(CanonicalJson.serialize(rest))` itself, using `common/CanonicalJson`, the shared canonicalizer, plus JDK `MessageDigest`. It asserts the result equals the `receiptDigestSha256` that WireMock received. So the digest that was signed is proven to be the digest of the stored receipt. |
| F5 | There is no end-to-end run of `SEEN → FINALIZED` (no `confirmed`). | True, but only the `SEEN` branch of `PaymentService.applyFinalized` differs, and it is unit-tested by `PaymentServiceTest.shouldTransitionToFinalizedFromSeenWhenNeverConfirmed` (`:325`). The rest of the path is identical to AC1. | **REJECTED** (human). Coverage is noted here. |
| F6 | `MOCK` versus a real port. | It is a valid fidelity note. What matters is the real filter chain, `JwtRoleAuthoritiesConverter` and `@PreAuthorize`, and MockMvc exercises all three. | **ACCEPTED (document).** MockMvc is kept, with the rationale in the class Javadoc. |
| F7 | The JSON and PDF objects are not proven distinct. | True; it is cheap to add. | **ACCEPTED.** AC1 asserts the `.json` object has `Content-Type: application/json`, and the `.pdf` object has `application/pdf` with bytes starting `%PDF`. |

**D1–D6 from the TIB are confirmed by the human.** Most notably, D1 adds WireMock.

## Task

One end-to-end integration test class. It boots the real `PaymentServiceApplication` against:
- Testcontainers Postgres
- Testcontainers Kafka (`apache/kafka:3.8.0`)
- Testcontainers LocalStack 3.8 S3, with an Object Lock bucket
- WireMock, standing in for Crypto Service and the token endpoint

It runs one forward flow and two reorg flows.

## Scope

**In:**
- `PaymentEndToEndIntegrationTest`
- Two test-scope dependencies: `org.wiremock:wiremock-standalone` (pinned) and
  `org.testcontainers:localstack` (BOM-managed)
- A nested test `@TestConfiguration` with a `@Primary S3Client` pointed at LocalStack

**Out:**
- Any production change.
- Re-testing behaviour other tasks already own: dedupe and staleness (T14), BLOCKED (T17), the
  broken-chain verifier (T20), export (T22), problem JSON (T23), contracts (T24).
- The `SEEN → FINALIZED` end-to-end path (F5).
- T27's `verify` lifecycle and Docker build.
- The cross-service watcher-unregister caveat.

## Decisions (frozen)

| # | Decision |
|---|---|
| **D1** | WireMock as the Crypto Service and token stand-in, using the shaded `wiremock-standalone` jar. Requests are verified through its journal. |
| **D2** | LocalStack 3.8 S3, with the bucket created with `objectLockEnabledForBucket(true)`. |
| **D3** | Testcontainers Kafka, not the local 9094 broker. |
| **D4 / F6** | `@AutoConfigureMockMvc` with `webEnvironment = MOCK`, sending a real `Authorization: Bearer` header. A `@MockBean JwtDecoder` returns a `Jwt` with a UUID `sub` and `roles: ["MERCHANT"]`. |
| **D5** | The reorg flow is two methods: `CONFIRMING → SEEN` (R11), and `SEEN → WATCHING` (R12, which includes the replacement watch). |
| **D6** | The T14 context shape: `@EntityScan` of the 6 feature packages; `@BeforeAll` Flyway migrate plus `ALTER ROLE payment_app`; `auto-offset-reset=earliest`; admin-JDBC reads (L12); Awaitility; a short `relay-interval-ms`. |

## Locked Decisions

L1, L2, L5, L7, L8, L9, L10, L11, L12 and L14. The TIB's §Locked Decisions says what each one
constrains; that table is unchanged.

## Files

- **Create:** `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java`.
- **Modify:**
  - `services/payment/pom.xml`: the two test-scope dependencies.
  - `T01SkeletonRegressionTest`: **only if** a dependency tripwire forbids either artifact,
    following the T24 precedent. Any such change is disclosed as a deviation.
- **NOT modify:**
  - `src/main/**`
  - every other existing test
  - `spec/`
  - `contracts/`

## Acceptance Criteria (final)

**AC1: forward flow** (R1, R2, R6–R10, R17, R19, R21–R25, R30; L1, L2, L7, L8, L9, L10).

1. **Invoice creation.** `POST /api/v1/invoices` with a Bearer token returns `201`, and:
   - the state is `OPEN`
   - WireMock received exactly one `POST /internal/v1/watches`
   - the invoice row holds the acked `watchId`
   - an `invoice.created` outbox row exists
2. **After `seen`:**
   - the payment is `SEEN`
   - WireMock received `DELETE /internal/v1/watches/{watchId}`
   - one `payments.payment.seen` record is keyed by `paymentUuid` (**F3**)
3. **After `confirmed`:** the payment is `CONFIRMING`.
4. **After `finalized`:**
   - **The attest call.** WireMock received exactly one `POST /internal/v1/attest`. Its body
     carries the payment's `chain` and `txHash`, and a 64-hex `receiptDigestSha256` equal to the
     `receipts` row's digest.
   - **Digest (F4).** The digest is independently recomputed from the stored S3 JSON, minus
     `signature`, `kmsKeyId` and `signedAt`, using `CanonicalJson` plus SHA-256. It equals the
     `/attest` digest.
   - **S3 objects (F7).** `<prefix><receiptUuid>.json` (`application/json`) and `.pdf`
     (`application/pdf`, bytes beginning `%PDF`) both exist.
   - **Object Lock (F1).** Each object has `COMPLIANCE` retention, with a retain-until in the
     future. A versioned delete fails with `403`.
   - **Ledger.**
     - Exactly one `ledger_entries` row exists for the receipt.
     - Its `prev_hash` equals the prior head, or 64 zeros on an empty ledger.
     - `ledgerVerifier.verify()` returns `Intact` (**F2**).
   - **State.** The payment is `ATTESTED` and the invoice is `PAID`.
   - **Events.**
     - One `payments.payment.finalized` record is keyed by `paymentUuid` (**F3**).
     - One `payments.receipt.issued` record is keyed by `receiptUuid`, and its `receiptUuid`,
       `paymentUuid` and `invoiceUuid` match the persisted rows.

**AC2: reorg from `CONFIRMING`** (R11, R18; L1, L11).
- The sequence is `seen` → `confirmed` → `reorged`, which leaves the payment in `SEEN`.
- The `CONFIRMING → SEEN` reversal is recorded in `verification_records`.
- That payment has no `/attest` call, no `receipts` row, no ledger row, and no S3 object.
- Before any of these negative assertions, the test proves the `reorged` event was consumed, via
  `processed_events`.

**AC3: reorg from `SEEN`** (R12, R18; L1, L11).
- The sequence is `seen` → `reorged`, which leaves the payment in `WATCHING`.
- The `SEEN → WATCHING` reversal is recorded.
- A replacement `POST /internal/v1/watches` was received, and the invoice now holds the new
  `watchId`.
- The same no-receipt assertions apply as in AC2, with the same consumed-first rule.

**AC4.** No production change.

**AC5.** Full `mvn -o test` is green, at **496/496** (493 + 3).

## Required Tests

- `shouldDriveInvoiceToAttestedReceiptEndToEnd` (AC1)
- `shouldWalkConfirmingBackToSeenOnReorgWithoutIssuingAReceipt` (AC2)
- `shouldWalkSeenBackToWatchingOnReorgWithoutIssuingAReceipt` (AC3)

## Constraints

The TIB's constraints are unchanged:
- **Isolation:** a fresh invoice and txHash per method; an `@AfterEach` table cleanup and
  WireMock reset; S3 and Kafka assertions scoped by key or UUID.
- **Async waits:** Awaitility, never `sleep`.
- **Negative assertions:** each one proves its triggering event was consumed first.
- **Credentials:** dummies only.
- **Module boundaries (L12):** no cross-module entity imports. `LedgerVerifier` and
  `CanonicalJson` are a service and a utility, not entities.
- **Event amounts:** quoted decimal strings.

## Open Questions

None remaining.
