# payment · T26 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`

T26 is a *verification* task. Every requirement below was already implemented and unit- or
slice-tested by T05–T19. T26 must prove that they compose, end-to-end, against real
infrastructure. It re-implements none of them.

## Business Rules

**Forward ("happy") flow:**

| ID | Rule (one sentence) |
|---|---|
| R1 | `POST /api/v1/invoices` with a valid `MERCHANT` JWT creates an `OPEN` invoice, registers a watch with Crypto Service, and returns `invoiceUuid` and the pay-to address. |
| R2 | The invoice row and its `invoice.created` outbox event are written in one transaction. |
| R6 | When Crypto Service acknowledges the watch, payment tracking moves `CREATED → WATCHING`. In this codebase that is *virtual*: there is no payment row before `SEEN` (`PaymentService:107`, a T10 decision). The observable is the invoice holding the acked `watchId`. |
| R7 | `chain.tx.seen` for a watched address records a payment in `SEEN`, linked to the invoice. |
| R7a | On first `SEEN`, the invoice's watch is unregistered in the same flow (`DELETE /internal/v1/watches/{watchId}`). |
| R8 | First `SEEN` emits `payment.seen` in the same transaction. |
| R9 | `chain.tx.confirmed` moves a `SEEN` payment to `CONFIRMING` and records the confirmation count. |
| R10 | `chain.tx.finalized` moves `CONFIRMING` or `SEEN` to `FINALIZED` and emits `payment.finalized` in the same transaction. |
| R17 | A `FINALIZED` payment that satisfies its invoice gets exactly one receipt. |
| R19 | The receipt digest is signed only via Crypto Service `POST /attest`; there is no local signing. |
| R23 | The signed receipt is stored as JSON and PDF in the S3 Object Lock bucket, in compliance mode, with no overwrite. |
| R21 | One ledger entry is appended, with `prev_hash` equal to the head's `entry_hash` (the genesis value is 64 zeros). |
| R24 | After it is signed, stored and appended, the payment becomes `ATTESTED`. |
| R25 | `ATTESTED` emits `receipt.issued` in the same transaction as the ledger append. |
| R30 | Each event routes to its `payments.*` topic. `receipt.issued` goes to `payments.receipt.issued`. |

**Reorg flow:**

| ID | Rule |
|---|---|
| R11 | A reorg of a `CONFIRMING` payment walks it back to `SEEN` and records the reversal. |
| R12 | A reorg of a `SEEN` payment walks it back to `WATCHING` and records the reversal. |
| R18 | No receipt is issued from any non-`FINALIZED` state, by any code path. |

**Cross-cutting continuity** (observed, not re-tested in isolation):
- R13: dedupe
- R29, R31: not exercised

## Locked Decisions

| ID | Constraint on T26 |
|---|---|
| **L1** | The states are exactly `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`. The only reversals are `CONFIRMING → SEEN` and `SEEN → WATCHING`. The test asserts these exact names. |
| **L2** | A receipt is issued only from `FINALIZED`. `ATTESTED` is set only after signature, S3 storage and ledger append, all durably. The forward flow must observe all three before `ATTESTED`. |
| **L3** | No chain client or KMS. Crypto Service is faked over HTTP, so no SDK stand-in is involved. |
| **L5** | Consumers dedupe on `chain:txhash:eventtype`. The event fixtures must use real deterministic keys. |
| **L7** | The ledger is append-only. The test reads `ledger_entries` and never mutates it. |
| **L8** | Every publish goes through the outbox. `receipt.issued` reaches Kafka only via `OutboxRelay`. |
| **L9** | Receipts are WORM: compliance-mode Object Lock, and the key embeds `receiptUuid`. |
| **L10** | `POST /api/v1/invoices` requires a `MERCHANT` JWT. The test must authenticate; it must not bypass security. |
| **L11** | A reorg before finality never needs a receipt revoked, because none exists. That is the reorg flow's "no receipt" assertion. |
| **L12** | Test code may not import one module's entity from another module's package. The precedent verifies with raw JDBC. |
| **L14** | One transaction per invoice. The watch is unregistered on first `SEEN` (R7a). |

## Files involved

**Existing, read-only.** Everything below is exercised, not modified:
- `invoice/InvoiceController`, `InvoiceService`
- `common/CryptoClient`, `CryptoServiceTokenProvider`, `ResourceServerConfig`
- `consumer/ChainEventConsumer`
- `payment/PaymentService`, `PaymentStateMachine`
- `receipt/ReceiptService`, `ReceiptStore`, `ReceiptStoreConfig`
- `receipt/ledger/LedgerService`
- `events/OutboxPublisher`, `OutboxRelay`, `EventTopics`
- `db/migration/V1–V9`
- `application.properties`

**Precedents to read:**
- `consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest` (T14): the full-context
  shape, `@EntityScan`, Flyway plus `payment_app`, raw JDBC, Awaitility
- `crypto/.../watch/EndToEndIntegrationTest`: Testcontainers Kafka in an end-to-end test
- `crypto/.../ObservationSnapshotStoreLocalStackIntegrationTest`: S3 against LocalStack
- `common/CryptoClientTest` and `CryptoServiceTokenProviderTest`: the Crypto and token HTTP
  shapes

**New files the spec expects:** one end-to-end integration test class under `services/payment/src/test/java/…`.
The spec doesn't name it, so the name and package are a Phase 2 decision. Any test-scope
dependencies go in `services/payment/pom.xml`.

**No production file and no `spec/` file is touched.**

## Dependencies

- **Infrastructure named by the task:**
  - Testcontainers Postgres (present)
  - Testcontainers Kafka (`org.testcontainers:kafka`, present at `pom.xml:80`, unused so far)
  - a **WireMock** Crypto Service (`tasks.md:47`, `package.md:74`, `:138`). WireMock is
    **absent** from the repo and from `~/.m2`.
- **Infrastructure the task implies:** an S3-compatible store, for "receipt in S3". There is no
  payment precedent. Crypto uses `org.testcontainers:localstack` with `localstack/localstack:3.8`
  (1.21.4 is in `~/.m2`; the image is local).
- **Crypto Service contract** (`design.md` §4c `:181-189`; `contracts/api/crypto-internal.yaml`):
  - `POST /internal/v1/watches` returns `200 {watchId, status:"REGISTERED"}`
  - `DELETE /internal/v1/watches/{watchId}` returns `204`
  - `POST /internal/v1/attest` with `{receiptDigestSha256, chain, txHash}` returns
    `200 {signature, kmsKeyId, signedAt, outcome:"SIGNED"}`
- **Token endpoint:** `themistra.payment.crypto.auth.token-uri`. It must return a
  `client_credentials` token (`access_token`, `expires_in`).
- **Config keys to override in the test:**
  - `spring.datasource.*`
  - `spring.kafka.bootstrap-servers`
  - `themistra.payment.crypto.base-url`
  - `themistra.payment.crypto.auth.token-uri`
  - `themistra.payment.receipt.bucket`, `region`
  - `spring.kafka.consumer.auto-offset-reset=earliest` (T14 lesson)
  - `themistra.payment.outbox.relay-interval-ms`, optionally lowered
- **Beans:** the `S3Client` bean (`ReceiptStoreConfig`) has no endpoint override, so the test
  must supply one pointed at the stand-in. `JwtDecoder` has no reachable JWKS; T14 used a
  `@MockBean`.
- **Contracts:** `contracts/events/payments/receipt-issued.v1.schema.json`, for the
  `receipt.issued` payload that arrives on Kafka. Its conformance is already covered by T24's
  `PaymentEventPayloadContractTest`.
- **Tables read by raw JDBC:**
  - `invoices`
  - `payments`
  - `verification_records`
  - `receipts`
  - `ledger_entries`
  - `processed_events`
  - `outbox`

## Acceptance Criteria

**AC1: forward flow, end-to-end (R1, R2, R6, R7, R7a, R8–R10, R17, R19, R21, R23–R25, R30; L1,
L2, L8, L9, L10).** Starting from an authenticated `POST /api/v1/invoices`:
1. **Creation:**
   - a `201` with an `OPEN` invoice
   - the stand-in received a `POST /watches`, and the invoice stores the acked `watchId`
2. **`chain.tx.seen`:**
   - the payment is `SEEN`
   - `DELETE /watches/{watchId}` was received
3. **`chain.tx.confirmed`:** the payment is `CONFIRMING`.
4. **`chain.tx.finalized`:**
   - the stand-in received `POST /attest` carrying the receipt digest, `chain` and `txHash`
   - both `<prefix><receiptUuid>.json` and `.pdf` exist in the bucket
   - one `receipts` row exists
   - one `ledger_entries` row exists, with the genesis or head `prev_hash`
   - the payment is `ATTESTED`, and the invoice is `PAID`
   - a `receipt.issued` message arrives on `payments.receipt.issued`, keyed by `receiptUuid`,
     whose `receiptUuid`, `paymentUuid` and `invoiceUuid` match the persisted rows

**AC2: reorg flow, no receipt (R11, R12, R18; L1, L11).** A separate invoice is driven to `SEEN`,
then (Phase 2 decides the exact sequence) reorged. In the end:
- the payment has walked backward (`CONFIRMING → SEEN` and/or `SEEN → WATCHING`)
- each reversal is recorded in `verification_records`
- **no** `/attest` call was made for that payment
- **no** `receipts` row, `ledger_entries` row, S3 object or `receipt.issued` message exists for it

**AC3: no production change.** Only test code and test-scope `pom.xml` entries change.
`main` remains untouched.

**AC4: suite green.** The full `mvn -o test` passes, at 493 plus the new tests.

## Tests required

- **Named tests:** none is pre-mapped to task 26 in `package.md` §8, so the new names are a
  Phase 2 decision. The two flows imply two test methods: one forward end-to-end flow, and one
  reorg-without-receipt flow.
- **Boundary tests implied by the task text:**
  - the S3 objects carry COMPLIANCE Object Lock (L9, as far as the stand-in supports it)
  - the `receipt.issued` Kafka key is `receiptUuid` (the G2-documented key)
  - the reorg flow makes no `/attest` call (R18 and L2 together)
- **Not required:**
  - re-testing dedupe, staleness, BLOCKED or the ledger verifier. T14, T17 and T20 own those.

## Open Questions

None blocks the task. Five need a Phase 2 or Phase 4 disposition.

`package.md` §11 was re-checked: Q1–Q8 are all closed or deferred, and none is scoped to T26.

1. **WireMock vs the repo's JDK `HttpServer` precedent.**
   - The spec names WireMock literally (`tasks.md:47`, `package.md:74`), but no service uses it.
   - It is not in `~/.m2`, while Maven Central is reachable.
   - Following the spec would add one test-scope dependency.
2. **S3 stand-in, and whether Object Lock is really enforced.**
   - Is LocalStack (crypto's precedent, plus one test-scope dependency) the right choice?
   - Does LocalStack 3.8 accept compliance-mode puts on an object-lock bucket? This must be
     prototyped before freezing.
3. **Kafka.** The task says Testcontainers Kafka, while payment's T14 precedent uses the local
   broker on 9094. Recommendation: Testcontainers, matching the task text and the siblings'
   end-to-end tests.
4. **Reorg sequence.** "Between seen and finalized" could mean either of these:
   - seen → confirmed → reorged, giving `SEEN`
   - seen → reorged, giving `WATCHING`, which also triggers a replacement `POST /watches`

   The spec's "walks the state backward" fits both.
5. **R6 observability.** There is no `CREATED` or `WATCHING` payment row before `SEEN`, by an
   earlier design decision. AC1 can therefore only assert the acked `watchId` on the invoice.
   This is disclosed, not a gap.
