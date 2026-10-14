# payment · T08 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T08 — Invoice expiry sweep |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |

## Files to create

1. `services/payment/src/main/resources/db/migration/V4__payment_app_shedlock_grant.sql`
2. `services/payment/src/main/resources/db/migration/V5__payment_app_payments_table_read_grant.sql`
3. `services/payment/src/main/java/com/themistra/payment/common/ShedLockConfig.java`
4. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExpirySweepJob.java`

(Test files are Phase 10.)

## Files to modify

1. `services/payment/pom.xml`
2. `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
3. `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`
4. `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
5. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
6. `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`

## Public methods (signatures)

- `ShedLockConfig.lockProvider(DataSource): LockProvider` — `@Bean`.
- `CryptoClient.unregisterWatch(UUID watchId): void` — `DELETE {watchPath}/{watchId}`; `404`/
  `410` treated as success; any other failure → `CryptoServiceUnavailableException`.
- `Invoice.markExpired(): void` — package-private; no-op unless `state == OPEN`.
- `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment(Instant now): List<Invoice>` —
  native query.
- `InvoiceService.findExpiredCandidatesWithoutInFlightPayment(): List<Invoice>` —
  `@Transactional(readOnly = true)`.
- `InvoiceService.expireInvoice(Invoice invoice): void` — `@Transactional`; calls
  `invoice.markExpired()` then `repository.save(invoice)`.
- `InvoiceExpirySweepJob.sweep(): void` — `@Scheduled` + `@SchedulerLock`, not `@Transactional`.

## Private methods

- `InvoiceExpirySweepJob.processOne(Invoice invoice): void` — skips/logs a `null` `watchId`;
  otherwise calls `cryptoClient.unregisterWatch(invoice.getWatchId())` (outside any transaction)
  then `invoiceService.expireInvoice(invoice)`.

## Entities used

- `Invoice` (extended: `markExpired()`).

## Repositories used

- `InvoiceRepository` (extended: one new native-query method; no `findByInvoiceUuid`, per Phase
  3/4's own disposition).

## Services used

- `InvoiceExpirySweepJob` depends on: `InvoiceService`, `CryptoClient`.
- `InvoiceService` depends on (unchanged): `InvoiceRepository`, `InvoiceProperties`, `Clock`,
  `OutboxPublisher`.

## Unit/integration tests required (Phase 10, not this phase)

- `InvoiceExpirySweepJobTest` (plain JUnit/Mockito, mirrors `RetrySchedulerTest`):
  `shouldExpireInvoiceAndUnregisterWatchAfterTtl`, `shouldNotExpireInvoiceWithPaymentInFlight`
  (a `CREATED`/`WATCHING` case, not only `SEEN`/`CONFIRMING`), `shouldContinueSweepAfterOneInvoiceFails`,
  `shouldSkipCandidateWithNullWatchId`, `sweepMethodIsAnnotatedSchedulerLock` (source-text guard).
- `InvoiceServiceTest`/`InvoiceServiceReadTest` additions (or a 3rd dedicated file, decided at
  Phase 10): `shouldNotOverwriteNonOpenInvoiceState`, `findExpiredCandidatesWithoutInFlightPayment`
  delegates correctly.
- `CryptoClientTest` additions: `unregisterWatch` success/idempotent-404/failure, mirroring
  `registerWatch`'s own real-local-HTTP-server test style.

## Execution order

1. `V4__payment_app_shedlock_grant.sql`, `V5__payment_app_payments_table_read_grant.sql`
   (no code dependency).
2. `pom.xml` (ShedLock dependencies).
3. `common/ShedLockConfig.java`.
4. `PaymentServiceApplication.java` (`@EnableSchedulerLock`).
5. `common/CryptoClient.java` (`unregisterWatch`).
6. `invoice/Invoice.java` (`markExpired`).
7. `invoice/InvoiceRepository.java` (native query).
8. `invoice/InvoiceService.java` (`findExpiredCandidatesWithoutInFlightPayment`,
   `expireInvoice`).
9. `invoice/InvoiceExpirySweepJob.java`.
10. Compile-check (`mvn -pl services/payment -am compile`) before any test is written.
11. *(Phase 10)* all tests above.
