# payment · T26 · Phase 0 — Repository Understanding

No design here: this is grounding only. Everything below was read directly from source on
2026-10-19, at branch `spec/service-specs-and-ai-framework` after T25 (commit `f9c7ddcc`), with
the suite at 493/493.

## 1. Architecture summary

- **Modules:** package-by-feature under `com.themistra.payment`:
  - `common`: security, errors, `CryptoClient`, `CryptoServiceTokenProvider`, `CanonicalJson`,
    config properties
  - `invoice`
  - `payment`: `PaymentService`, `PaymentStateMachine`
  - `verification`
  - `receipt`: `ReceiptService`, `ReceiptStore`, `ReceiptStoreConfig`, plus the `receipt.ledger`
    sub-package with `LedgerService` and `LedgerVerifier`
  - `consumer`: `ChainEventConsumer`, `ProcessedEvent`
  - `events`: outbox, relay, `EventTopics`
  - `export`

  L12 means no feature module imports another feature module's entity. Cross-module reads go
  through `*View` records.
- **Persistence:** JPA with `ddl-auto=validate`, and Flyway V1–V9 for the `payments` schema.
  - Flyway is disabled at runtime (`spring.flyway.enabled=false`); migrations run via the Maven
    plugin or, in tests, via `Flyway.configure()…migrate()`.
  - The app connects as `payment_app` with least-privilege grants: `ledger_entries`,
    `verification_records`, `receipts` and `processed_events` are INSERT/SELECT only (V2, V8, V9).
- **Events:**
  - **Inbound:** `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized` and
    `chain.tx.reorged`, handled by `@KafkaListener`s in `ChainEventConsumer`. Each one dedupes
    on `idempotencyKey` through `processed_events`.
  - **Outbound:** `OutboxPublisher` writes rows inside the business transaction. `OutboxRelay`
    is `@Scheduled` every `relay-interval-ms` (default 2000) and sends them with
    `kafkaTemplate.send(topic, aggregateId, payload)` to the topics in `EventTopics`:
    - `payments.invoice.created`
    - `payments.payment.seen`
    - `payments.payment.finalized`
    - `payments.receipt.issued`
- **Security:**
  - One resource-server chain (`ResourceServerConfig`). Every handler carries
    `@PreAuthorize("hasRole('MERCHANT')")`, and the merchant comes from the JWT `sub`.
  - Outbound calls to Crypto Service carry a `client_credentials` bearer token from
    `CryptoServiceTokenProvider`. It POSTs to `themistra.payment.crypto.auth.token-uri` and
    caches the token.

## 2. Existing code this task touches

This task has no production code of its own. **The whole flow already exists, and T26 only
exercises it end-to-end.**

| Flow step (task 26 text) | Existing code |
|---|---|
| create invoice | `InvoiceController.create` (`POST /api/v1/invoices`). It calls `cryptoClient.registerWatch(...)` *first*, then `invoiceService.create(..., watchResponse.watchId())`. |
| watch-ack | The `POST {watch-path}` response `{"watchId", "status":"REGISTERED"}`. `CryptoClient.registerWatch` throws unless `status` is `REGISTERED`. |
| `chain.tx.seen` | `ChainEventConsumer.onChainTxSeen`: dedupe, `findByWatchId`, then `paymentService.applySeen`, then `cryptoClient.unregisterWatch(watchId)` (`DELETE {watch-path}/{watchId}`), all inside one transaction. |
| `chain.tx.confirmed` | `onChainTxConfirmed`, then `paymentService.applyConfirmed`: `SEEN → CONFIRMING`. |
| `chain.tx.finalized` | `onChainTxFinalized`. Transaction 1 runs dedupe, `applyFinalized` (to `FINALIZED`, with the `payment.finalized` outbox row) and `invoiceService.markOutcomeIfOpenOrExpired` (to `PAID`). Then, outside that transaction, it calls `receiptService.issueIfEligible(paymentId, idempotencyKey)`. |
| `/attest` (SIGNED) | `ReceiptService.issueIfEligible` re-reads the payment (it must be `FINALIZED`) and the invoice (it must be `PAID`), computes `ReceiptDigest`, and calls `cryptoClient.attest(digest, chain, txHash)` (`POST {attest-path}`). |
| receipt in S3 | `ReceiptStore.store` puts `<prefix><receiptUuid>.json` and `.pdf` with `ObjectLockMode.COMPLIANCE` plus retain-until. The `S3Client` bean comes from `ReceiptStoreConfig`: region only, **no endpoint override**. |
| ledger appended | `LedgerService.append(receiptId, payload)`: `prev_hash` is the head's `entry_hash`, or 64 zeros for genesis. |
| `ATTESTED` | `paymentService.markAttestedIfFinalized`. It shares one `TransactionTemplate` with the receipts insert, the ledger append and the outbox publish. |
| `receipt.issued` | `outboxPublisher.publish("receipt", receiptUuid, "receipt.issued", ReceiptIssuedPayload)`, then `OutboxRelay` publishes to `payments.receipt.issued`, keyed by `receiptUuid`. |
| reorg flow | `onChainTxReorged`, then `paymentService.applyReorged`. `CONFIRMING → SEEN` comes first; if not applicable, `SEEN → WATCHING`. Only on `WATCHING` does it call `registerReplacementWatch`, which issues another `POST {watch-path}` and `invoiceService.updateWatchIdIfOpen`. A reorg of an already `FINALIZED` payment is ignored and recorded (T14). Allowed reverse edges in `PaymentStateMachine`: `CONFIRMING → SEEN` and `SEEN → WATCHING`. |

**Tables involved:**
- `invoices`
- `payments`
- `verification_records`
- `receipts`
- `ledger_entries`
- `processed_events`
- `outbox`

`ledger_anchors` stays empty, because T21 is deferred.

**New in this task:** only test code, plus test-scope dependencies (see §5).

## 3. Established patterns to follow

- **Full-context integration test:** `consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest` (T14).
  - Shape: `@SpringBootTest(webEnvironment = NONE)`, a static `PostgreSQLContainer`
    (`postgres:16-alpine`), and `@BeforeAll` Flyway migrate plus `ALTER ROLE payment_app
    PASSWORD …`.
  - `@DynamicPropertySource` connects as `payment_app`.
  - **`@EntityScan` limited to the 6 feature packages.** It is mandatory: otherwise
    `archtestfixtures.RogueUnmappedEntity` breaks Hibernate startup.
  - `@MockBean JwtDecoder` and `@MockBean CryptoClient`. Verification uses raw admin-JDBC,
    because entities aren't reachable across modules (L12).
  - The test uses Awaitility, `sendAndWait` on the `KafkaTemplate` future, an `@AfterEach`
    DELETE of all tables, and JSON event builders whose amounts are quoted strings.
- **Kafka in that precedent is the already-running local broker** (`localhost:9094`, from
  `services/auth/compose.local.yaml`), with a fail-fast reachability check. It is **not** a
  Testcontainers image, by T14's approved Phase 4 decision.
  - Sibling end-to-end tests *do* use Testcontainers Kafka:
    - `crypto/.../watch/EndToEndIntegrationTest`: `confluentinc/cp-kafka:7.6.0`, `KAFKA::getBootstrapServers`
    - `auth/.../EndToEndLifecycleIntegrationTest`: `apache/kafka:3.8.0`
  - `org.testcontainers:kafka` is already a test dependency of payment (`pom.xml:80`), but no
    payment test uses it.
- **S3 against LocalStack:** `crypto/.../ObservationSnapshotStoreLocalStackIntegrationTest`
  uses `LocalStackContainer("localstack/localstack:3.8").withServices(S3)`, a static-credential
  `S3Client`, `forcePathStyle(true)` and `createBucket`. Crypto's `pom.xml:103` has
  `org.testcontainers:localstack` (test scope). Payment does **not** have it yet.
- **HTTP stand-in for Crypto Service:** payment's `CryptoClientTest` uses a JDK
  `com.sun.net.httpserver.HttpServer` on 127.0.0.1, mirroring auth's `BreachCheckClientTest`.
  - It does not use `MockRestServiceServer`, because `CryptoClient`'s constructor calls
    `.requestFactory(...)`, which defeats it.
  - **No service in the repo uses WireMock.**
- **Outbox, idempotency and transactions:** this is existing behaviour that T26 must observe,
  not change. The rules are publish-in-transaction, at-least-once relay, and dedupe on
  `idempotencyKey`.
- **Configuration:** flat `application.properties`. Crypto client properties:
  - `themistra.payment.crypto.base-url`
  - `attest-path=/internal/v1/attest`
  - `watch-path=/internal/v1/watches`
  - `auth.token-uri`

  Receipt properties: `themistra.payment.receipt.bucket`, `prefix` and `region`.

## 4. Testing conventions

- **The pyramid** (`agents.md:57-58`; `package.md:74`):
  - unit tests: plain JUnit with a fixed `Clock`
  - then ArchUnit and contract tests
  - then integration tests: Testcontainers Postgres and Kafka, **plus "a WireMock stand-in for
    the Crypto Service"** (`package.md:74`, `:138`)
- The Surefire 3.5.3 `@ArchTest` bug doesn't affect this task, because there are no ArchUnit
  fields.
- Docker and Testcontainers are required. They are available on this machine:
  - images present: `postgres:16-alpine`, `localstack/localstack:3.8`, `apache/kafka:3.8.0`,
    `confluentinc/cp-kafka:7.6.0`
  - the local `auth-kafka-1` broker is up on 9094

## 5. Known gaps / unknowns

1. **WireMock is not a dependency anywhere, and it is not in the local `~/.m2`.** The task text
   and `package.md:74` name WireMock. The repo's precedent for faking Crypto Service HTTP is the
   JDK `HttpServer`. Maven Central is reachable (HTTP 200), so adding WireMock is possible, but
   this session's builds run `mvn -o`.
   - **I do not know** whether the human wants a new dependency or the existing `HttpServer`
     precedent. This is a Phase 1/2/4 decision.
2. **S3 has no stand-in in the payment module.**
   - The `S3Client` bean has no endpoint override. The test would need either a `@Primary`
     test bean pointing at LocalStack (crypto's precedent, which adds the
     `org.testcontainers:localstack` dependency; 1.21.4 is in `~/.m2`) or some other double.
   - **I do not know** whether LocalStack 3.8 honours `ObjectLockMode.COMPLIANCE` on
     `putObject`: the bucket must be created with Object Lock enabled. If it rejects the
     request, "receipt in S3" can't be proven against LocalStack unchanged.
3. **Testcontainers Kafka or the local broker?**
   - The task says "Testcontainers Postgres + Kafka". The payment precedent (T14) uses the
     shared local broker, while the siblings' end-to-end tests use a Kafka container.
   - **I do not know** which the human prefers here.
4. **Token endpoint:** `CryptoServiceTokenProvider` POSTs to `auth.token-uri`. The stand-in must
   also serve a `client_credentials` token response, or the provider must be mocked. Payment's
   `CryptoServiceTokenProviderTest` defines the expected response shape (`access_token`,
   `expires_in`).
5. **Invoice creation entry point:** either the real HTTP route (`POST /api/v1/invoices`, which
   needs a JWT with role `MERCHANT` and a UUID `sub`) or `InvoiceService.create` directly (T14's
   shape, which skips the real watch-ack). "Create invoice → watch-ack" suggests the controller
   path, through MockMvc or a real port.
6. **Asserting `receipt.issued`:** it reaches Kafka only via the scheduled `OutboxRelay`. The
   assertion could read the `outbox` row (published flag) or consume `payments.receipt.issued`
   from the broker. Which one counts as "receipt.issued" is a Phase 1 question.
7. **Reorg flow, "walks the state backward with no receipt":**
   - From `CONFIRMING`, one reorg gives `SEEN`. A reorg from `SEEN` gives `WATCHING` and
     triggers a **replacement `registerWatch`** POST, so the stand-in must answer it.
   - "Between seen and finalized" may mean seen, then confirmed, then reorged (giving `SEEN`),
     or seen then reorged (giving `WATCHING`). **I do not know** which is intended; both are
     backward walks.
8. **Known cross-service caveat (memory, logged earlier):** crypto-service's `unregisterWatch`
   stops all tracking of an already-matched tx. Payment calls `unregisterWatch` on first SEEN,
   so in production `confirmed`, `finalized` and `reorged` may never arrive. T26 fakes Crypto
   Service, so the test won't reproduce this. It is out of scope, and noted so that a green T26
   isn't read as proof of the real cross-service flow.
