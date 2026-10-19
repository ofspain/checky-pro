# payment · T26 · Phase 2 — Task Implementation Brief

Consumes: `artifacts/01-specification-extraction.md`

## Task

Write one end-to-end integration test class. It boots the real `PaymentServiceApplication`
against:
- Testcontainers Postgres
- Testcontainers Kafka
- Testcontainers LocalStack S3
- a WireMock stand-in for Crypto Service and the token endpoint

It drives two flows:
1. **Forward:** create invoice → watch-ack → `chain.tx.seen` → `chain.tx.confirmed` →
   `chain.tx.finalized` → `/attest` (SIGNED) → receipt in S3 → ledger appended → `ATTESTED` →
   `receipt.issued`.
2. **Reorg:** a reorg between seen and finalized walks the state backward and issues no receipt.

## Purpose

Every step was built and tested in isolation in T05–T19. None of them has yet been proven to
compose through the real wiring: HTTP security, the Kafka listeners, the transaction boundaries,
`OutboxRelay`, and S3. T26 is the final-verification proof that R1→R25 hold as one flow, and that
L2 (receipts only from `FINALIZED`) and L11 (reorg before finality issues no receipt) hold under
real infrastructure.

## Scope

**In:**
- A new test class.
- Three test-scope dependencies:
  - WireMock
  - `org.testcontainers:localstack`
  - nothing for Kafka, whose dependency is already present
- A test-only `@Primary S3Client` pointed at LocalStack.

**Out:**
- **Any production change.**
- Changes to existing tests.
- **Re-testing behaviour already covered elsewhere.** This covers dedupe and staleness (T14),
  BLOCKED/HELD (T17), the ledger verifier (T20), export (T22), problem JSON (T23) and contracts
  (T24).
- **The cross-service watcher-unregister caveat.** It is a crypto-service gap, logged earlier,
  and is not reproducible against a stand-in.
- **The Dockerfile and `verify` lifecycle**, which belong to T27.

## Business Rules

- **R1:** an authenticated `POST /api/v1/invoices` creates an `OPEN` invoice and registers a
  watch.
- **R2:** the invoice and its `invoice.created` outbox row are written in one transaction.
- **R6:** watch-ack. Observable as the acked `watchId` persisted on the invoice; no payment row
  exists before `SEEN`.
- **R7 / R7a / R8:** `seen` creates a `SEEN` payment, unregisters the watch, and emits
  `payment.seen`.
- **R9:** `confirmed` gives `CONFIRMING`, with the confirmation count recorded.
- **R10:** `finalized` gives `FINALIZED`, plus `payment.finalized`.
- **R17 / R19:** exactly one receipt; the signature comes only via `POST /attest`.
- **R23:** JSON and PDF land in the Object Lock bucket in compliance mode.
- **R21:** one ledger entry, with `prev_hash` equal to the head's (or the genesis) hash.
- **R24 / R25:** `ATTESTED`, and `receipt.issued` emitted in the same transaction as the ledger
  append.
- **R30:** `receipt.issued` arrives on `payments.receipt.issued`.
- **R11 / R12:** a reorg walks `CONFIRMING → SEEN` and `SEEN → WATCHING`, each recorded.
- **R18:** no receipt from any non-`FINALIZED` state.

## Locked Decisions

**Constraints on this task:**
- **L1 / L2 / L11:** the exact state names; a receipt only after `FINALIZED`; no receipt on a
  reorg before finality.
- **L5:** the fixtures use real `chain:txhash:eventtype` keys.
- **L7:** the test only reads `ledger_entries`.
- **L8:** `receipt.issued` is observed on Kafka, so it really travels through the outbox and
  relay.
- **L9:** COMPLIANCE Object Lock.
- **L10:** the invoice is created through the real security chain.
- **L12:** no cross-module entity imports in the test, so verification uses raw admin-JDBC
  (the T14 precedent).
- **L14:** the watch is unregistered on first `SEEN`.

**Resolved directly** (low-risk test-infrastructure choices; all are flagged for the Phase 4
gate):

| # | Decision | Basis |
|---|---|---|
| D1 | **WireMock** (`org.wiremock:wiremock-standalone`, test scope, the shaded jar) in place of a JDK `HttpServer`. | The spec names WireMock literally (`tasks.md:47`, `package.md:74, :138`). The shaded artifact avoids Jetty clashing with Spring Boot's own managed versions. One stub server serves the token endpoint and the three Crypto routes. Request verification (`verify(postRequestedFor…)`) gives the "/attest was called" and "no /attest was called" assertions directly. |
| D2 | **LocalStack 3.8 S3** (`org.testcontainers:localstack`, BOM-managed), with the bucket created with Object Lock enabled. | This is crypto's precedent. **It was prototyped at Phase 2:** LocalStack 3.8 accepted a COMPLIANCE put, returned `Mode=COMPLIANCE` with the retain-until date via `?retention`, and refused a versioned DELETE with `403 AccessDenied`. So L9 is provable, not just plausible. |
| D3 | **Testcontainers Kafka** (`org.testcontainers.kafka.KafkaContainer`, `apache/kafka:3.8.0`), not the local 9094 broker. | It matches the task's text ("Testcontainers … Kafka") and the auth and crypto end-to-end precedents. The image is already local, and the class is self-contained. |
| D4 | **Invoice creation through MockMvc** (`@AutoConfigureMockMvc`, `webEnvironment = MOCK`), sending `Authorization: Bearer …`. A `@MockBean JwtDecoder` returns a `Jwt` with a UUID `sub` and `roles: ["MERCHANT"]`. | This exercises the real filter chain, `JwtRoleAuthoritiesConverter` and `@PreAuthorize`. Only JWKS signature validation is stubbed, and `ResourceServerConfigTest` owns that. It is the T14 `@MockBean JwtDecoder` precedent. |
| D5 | **The reorg flow is two test methods:** (a) seen → confirmed → reorged, giving `SEEN` (R11); (b) seen → reorged, giving `WATCHING` (R12), which also asserts the replacement `POST /watches` and the new `watchId` on the invoice. | Dedupe on `chain:txhash:reorged` makes a second reorg of one tx unreachable (T14's documented limitation). Two methods are the only way to cover both reversal edges, and "walks the state backward" fits both. |
| D6 | **Context shape:** copied from T14. That means the `@EntityScan` of the 6 feature packages (mandatory: `RogueUnmappedEntity`), `@BeforeAll` Flyway migrate plus `ALTER ROLE payment_app`, a `payment_app` datasource, `auto-offset-reset=earliest`, admin-JDBC reads, and Awaitility. A short `relay-interval-ms` (e.g. 200) keeps waits short. | The T14 precedent, with its lessons already learnt. |

## Dependencies

- **Production beans, exercised unmodified:**
  - `InvoiceController`, `InvoiceService`
  - `CryptoClient`, `CryptoServiceTokenProvider`
  - `ChainEventConsumer`
  - `PaymentService`
  - `ReceiptService`, `ReceiptStore`
  - `LedgerService`
  - `OutboxPublisher`, `OutboxRelay`, `EventTopics`
- **Test overrides:**
  - a `@Primary S3Client` built in a nested `@TestConfiguration`, against the LocalStack
    endpoint with `forcePathStyle`
  - `@MockBean JwtDecoder`
- **Config overrides (`@DynamicPropertySource`):**
  - the datasource
  - `spring.kafka.bootstrap-servers`
  - `themistra.payment.crypto.base-url` and `.auth.token-uri`, pointed at WireMock
  - `themistra.payment.receipt.bucket` and `.region`
  - the consumer group id
  - `auto-offset-reset=earliest`
  - `themistra.payment.outbox.relay-interval-ms`
- **Contracts the stand-in must honour** (`design.md` §4c; `contracts/api/crypto-internal.yaml`):
  - `POST /internal/v1/watches` returns `200 {watchId, status:"REGISTERED"}`
  - `DELETE /internal/v1/watches/{id}` returns `204`
  - `POST /internal/v1/attest` returns `200 {signature, kmsKeyId, signedAt, outcome:"SIGNED"}`
  - the token endpoint returns `200 {access_token, expires_in}`

## Inputs

The inputs are an HTTP `POST /api/v1/invoices` request body, plus four `chain.tx.*` JSON events
published to Kafka. Their amounts are quoted decimal strings and their idempotency keys are
deterministic.

## Outputs

Pass or fail. The observable state comes from five sources:
- Postgres rows (via JDBC)
- S3 objects and their retention
- WireMock's request journal
- `payments.receipt.issued` Kafka records
- the HTTP `201` body

## State Changes

These happen only inside throwaway containers. Nothing persists, and no production state is
touched.

## Files to Create

- `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java`.
  It sits in the root package because it spans every module, and verifies with JDBC (L12).

## Files to Modify

- `services/payment/pom.xml`: add the test-scope `org.wiremock:wiremock-standalone` (pinned
  version) and `org.testcontainers:localstack` (BOM-managed).
- `T01SkeletonRegressionTest`: **only if** one of its dependency tripwires forbids either
  artifact. This is the T24 precedent; otherwise the file is not touched.

## Files NOT to Modify

- Everything under `src/main`
- Every existing test, apart from the conditional T01 tripwire above
- `spec/`
- `contracts/`

## Acceptance Criteria

**AC1 (forward flow: R1, R2, R6–R10, R17, R19, R21, R23–R25, R30; L1, L2, L8, L9, L10).**
Starting from `POST /api/v1/invoices`, the test observes:
1. **Creation:**
   - a `201` response with state `OPEN`
   - WireMock received a `POST /watches`
   - the invoice persists the acked `watchId`
   - an `invoice.created` outbox row exists
2. **After `seen`:**
   - the payment is `SEEN`
   - `DELETE /watches/{watchId}` was received
3. **After `confirmed`:** the payment is `CONFIRMING`.
4. **After `finalized`:**
   - exactly one `POST /attest` was received, carrying the payment's `chain` and `txHash` and a
     64-hex `receiptDigestSha256`
   - the `receipts` row's digest equals that `receiptDigestSha256`
   - `<prefix><receiptUuid>.json` and `.pdf` exist in S3, each with COMPLIANCE retention
   - exactly one `ledger_entries` row exists, whose `prev_hash` is the genesis value (64 zeros)
     or the prior head
   - the payment is `ATTESTED` and the invoice is `PAID`
   - one `payments.receipt.issued` record arrives, keyed by `receiptUuid`, with matching
     `receiptUuid`, `paymentUuid` and `invoiceUuid`

**AC2 (reorg from CONFIRMING: R11, R18; L1, L11).** Seen → confirmed → reorged gives `SEEN`,
with a `CONFIRMING → SEEN` verification record. For that payment there is no `/attest` call, no
`receipts` row, no ledger row and no S3 object.

**AC3 (reorg from SEEN: R12, R18; L1, L11).**
- Seen → reorged gives `WATCHING`, with a `SEEN → WATCHING` verification record.
- A replacement `POST /watches` was received, and the invoice now holds the new `watchId`.
- The same no-receipt assertions as AC2 apply.

**AC4.** No production change.

**AC5.** The full `mvn -o test` is green: 493 plus 3.

## Required Tests

There are no pre-named tests (`package.md` §8 maps none to task 26). The new tests are:
- `shouldDriveInvoiceToAttestedReceiptEndToEnd` (AC1)
- `shouldWalkConfirmingBackToSeenOnReorgWithoutIssuingAReceipt` (AC2)
- `shouldWalkSeenBackToWatchingOnReorgWithoutIssuingAReceipt` (AC3)

## Constraints

- **Isolation:**
  - each method uses a fresh invoice and txHash
  - an `@AfterEach` clears the tables (T14 pattern) and resets WireMock
  - S3 assertions are scoped by `receiptUuid`
  - Kafka assertions filter by key or `receiptUuid`
- **Timing:** Awaitility everywhere async, with no `Thread.sleep`. Every negative ("no receipt")
  assertion first proves the triggering event was consumed, through `processed_events` (the
  T14 Phase 8 lesson), so it can't pass vacuously.
- **Security:** no real credentials; the LocalStack and WireMock secrets are dummies. Invoice
  creation never bypasses the filter chain.
- **Module boundaries (L12):** no entity imports; JDBC only.
- **Money (L6):** event amounts are JSON strings, and `BigDecimal` is used only where needed.
- **Transactions:** observed, not asserted directly. R25's same-transaction property is already
  unit-proven (T19). Here, a `receipt.issued` record together with the `ATTESTED` state is the
  composed evidence.
- **Null handling:** not applicable.

## Open Questions

No blockers. D1–D6 are low-risk test-infrastructure choices, resolved directly and flagged for
the Phase 4 gate. The one that most deserves human confirmation is **D1**, which adds
WireMock: it is a new dependency, though the spec names it. It must be fetched online once,
because this session's builds run `mvn -o`.
