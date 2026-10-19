# payment · T26 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

The task is test-only: there is no schema, DAO, service or API layer. Every file below traces to
the frozen brief's Files section.

## Files to create

### `services/payment/src/test/java/com/themistra/payment/PaymentEndToEndIntegrationTest.java`

**Class annotations:**
- `@Testcontainers`
- `@SpringBootTest(webEnvironment = MOCK, properties = …)` with:
  - `spring.kafka.consumer.group-id=payment-e2e-it`
  - `spring.kafka.consumer.auto-offset-reset=earliest`
  - `spring.kafka.consumer.properties.metadata.max.age.ms=1000`
  - `themistra.payment.outbox.relay-interval-ms=200`
- `@AutoConfigureMockMvc`
- `@EntityScan` of the 6 feature packages (D6)

**Static infrastructure.** Each piece is started before `@DynamicPropertySource` resolves.
- `@Container PostgreSQLContainer` (`postgres:16-alpine`).
- `@Container KafkaContainer` (`org.testcontainers.kafka.KafkaContainer`, `apache/kafka:3.8.0`).
- `@Container LocalStackContainer` (`localstack/localstack:3.8`, S3).
- `WireMockServer` (`dynamicPort()`). It is started in a `static {}` initialiser, because its
  port is needed by `@DynamicPropertySource`. It is stopped in `@AfterAll`.

**`@DynamicPropertySource`** sets:
- the datasource URL
- `payment_app` and its password
- `spring.kafka.bootstrap-servers`
- `themistra.payment.crypto.base-url` = WireMock base URL
- `themistra.payment.crypto.auth.token-uri` = WireMock `/oauth2/token`
- `themistra.payment.receipt.bucket` = `e2e-receipts`
- `themistra.payment.receipt.region` = the LocalStack region

**`@BeforeAll` (static):**
1. Run the Flyway migration, then `ALTER ROLE payment_app PASSWORD`. This follows T14.
2. Use `AdminClient` to create the 8 topics:
   - `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized`, `chain.tx.reorged`
   - `payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized`,
     `payments.receipt.issued`

   They have to exist before the listener and the assertion consumer subscribe, which avoids
   metadata-refresh stalls on a fresh broker.
3. Create the S3 bucket `e2e-receipts` with `objectLockEnabledForBucket(true)`, using a static
   LocalStack `S3Client`.

**Nested `@TestConfiguration`.** It declares a `@Bean @Primary S3Client localStackS3Client()`:
LocalStack endpoint, static credentials, `forcePathStyle(true)`. It is named differently from
production's `s3Client` bean so there is no bean-override clash. `ReceiptStore` receives it by
type.

**Injected beans:**
- `MockMvc`
- `KafkaTemplate<String,String>`
- `LedgerVerifier`
- `@MockBean JwtDecoder` (D4). It returns `Jwt.withTokenValue("e2e-merchant-token")` with
  `sub` = merchant UUID and `roles` = `["MERCHANT"]`.

**`@BeforeEach`:**
- Stub the JWT decoder.
- Stub WireMock:
  - `POST /oauth2/token` → `{"access_token":"e2e-crypto-token","expires_in":3600}`
  - `POST /internal/v1/watches` is a two-state **scenario**:
    - the first call returns `{watchId: W1, status: REGISTERED}`
    - every later call returns `{watchId: W2, status: REGISTERED}` (needed for AC3's
      replacement watch)
    - W1 and W2 are fresh UUIDs per test
  - `DELETE /internal/v1/watches/.*` → `204`
  - `POST /internal/v1/attest` → `{signature, kmsKeyId, signedAt, outcome:"SIGNED"}`
- Start one assertion `KafkaConsumer<String,String>`:
  - unique group, `earliest`
  - subscribed to the 3 `payments.*` topics the ACs assert
  - polled in Awaitility into an accumulating list

**`@AfterEach`:**
- Close the consumer.
- `WIREMOCK.resetAll()`.
- Admin-JDBC `DELETE`, in FK order:
  `ledger_entries`, `receipts`, `verification_records`, `processed_events`, `outbox`,
  `payments`, `invoices`.

  The admin role owns these tables, so the app role's INSERT/SELECT-only grants don't apply.

**Private helpers:**
- `createInvoice()`: MockMvc `POST /api/v1/invoices` with a Bearer header and a JSON body:
  - `chain` `ETHEREUM`
  - `tokenContractAddress` `0x111`
  - `tokenDecimals` 6
  - `amount` `1000`
  - `payToAddress` `0xdef`

  It asserts `201` and `state` `OPEN`, and returns the `invoiceUuid`.
- `sendAndWait(topic, key, json)`: the T14 pattern.
- `seenJson`, `confirmedJson`, `finalizedJson`, `reorgedJson`: the T14 fixtures, with quoted
  amounts and `chain:txhash:eventtype` keys.
- JDBC readers:
  - `paymentState`
  - `paymentUuid`
  - `invoiceState`
  - `invoiceWatchId`
  - `outboxCount(eventType, aggregateId)`
  - `processedEventExists`
  - `verificationRecordExists(txHash, from, to)`
  - `receiptRow(paymentId)`, giving uuid, digest and `s3_key`
  - `ledgerRows(receiptId)`, giving `prev_hash`
  - `countForPayment(table)`
- `recordsOn(topic, key)`: filters the accumulated Kafka records.
- `sha256Hex(String)`: JDK `MessageDigest` plus `HexFormat`.

### Tests

**`shouldDriveInvoiceToAttestedReceiptEndToEnd` (AC1)**, in order:
1. Create the invoice. Verify one watch POST, the invoice's `watch_id` = W1, and one
   `invoice.created` outbox row.
2. Send `seen`. Await `SEEN`. Verify `DELETE /watches/W1`. Await one `payments.payment.seen`
   record keyed by `paymentUuid`.
3. Send `confirmed`. Await `CONFIRMING`.
4. Send `finalized`, then:
   - Await `ATTESTED`.
   - **Attest call:** verify exactly one `/attest` POST, and read its body (`chain`, `txHash`,
     64-hex digest).
   - **Receipt row:** its digest equals the `/attest` digest.
   - **S3 objects:**
     - `headObject` on `.json` gives content type `application/json`; `.pdf` gives
       `application/pdf`, and its bytes start with `%PDF`.
     - `getObjectRetention` on each gives `COMPLIANCE`, with retain-until after now.
     - `deleteObject(versionId)` on each throws `S3Exception` with status 403.
   - **Digest (F4):** parse the `.json` object, drop `signature`, `kmsKeyId` and `signedAt`,
     then `sha256Hex(CanonicalJson.serialize(rest))` must equal the `/attest` digest.
   - **Ledger:** exactly one row for the receipt, with `prev_hash` = 64 zeros (the ledger is
     empty at that point because of `@AfterEach`), and `ledgerVerifier.verify()` is `Intact`.
   - **Invoice:** `PAID`.
   - **Kafka:** await one `payments.payment.finalized` record keyed by `paymentUuid`, and one
     `payments.receipt.issued` record keyed by `receiptUuid` whose JSON `receiptUuid`,
     `paymentUuid` and `invoiceUuid` match.

**`shouldWalkConfirmingBackToSeenOnReorgWithoutIssuingAReceipt` (AC2):**
- Create, then send `seen` (await `SEEN`), then `confirmed` (await `CONFIRMING`), then `reorged`.
- Await the `reorged` key in `processed_events`, then:
  - the state is `SEEN`
  - a `CONFIRMING → SEEN` verification record exists
  - `/attest` POST count is 0
  - `receipts`, ledger and S3 rows for this payment number 0; S3 is checked as `listObjectsV2`
    under the prefix returning 0

**`shouldWalkSeenBackToWatchingOnReorgWithoutIssuingAReceipt` (AC3):**
- Create, send `seen` (await `SEEN`), then send `reorged` and await it in `processed_events`.
- Then:
  - the state is `WATCHING`
  - a `SEEN → WATCHING` record exists
  - await 2 watch POSTs in total, and the invoice's `watch_id` = W2
  - the same no-receipt assertions as AC2

## Files to modify

`services/payment/pom.xml`. Add two test-scope dependencies, each with a T26 comment:
- `org.wiremock:wiremock-standalone:3.9.1`, the version fetched online once at Phase 5
- `org.testcontainers:localstack`, BOM-managed

**T01 tripwire:** checked. `paymentPomExcludesKmsChainClientsAndDeferredDependencies` forbids
neither artifact, so `T01SkeletonRegressionTest` is **not** modified.

## Public / private methods, entities, repositories, services

- **Public:** none. The tests are package-private.
- **Private:** the helpers above.
- **Entities:** none imported (L12).
- **Repositories:** none.
- **Services used:** `LedgerVerifier` (a `@Component`) and `CanonicalJson` (a `common` utility).

## Execution order

1. Make the `pom.xml` additions. Run `mvn -o -q test-compile` to confirm both resolve offline.
2. Write the test-class skeleton: containers, properties, `@BeforeAll`, the `@TestConfiguration`,
   stubs, cleanup.
3. Write AC1, then run it alone.
4. Write AC2 and AC3, then run the class.
5. Mutation-check one negative assertion and one positive one, temporarily, and restore.
6. Run the full `mvn -o test`. The expected result is 496/496.
