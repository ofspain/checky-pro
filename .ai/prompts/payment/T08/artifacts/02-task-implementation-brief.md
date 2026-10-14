# payment · T08 · Phase 2 — Task Implementation Brief

## Task

T08 — Invoice expiry sweep.

## Purpose

A ShedLock-guarded scheduled job that expires `OPEN` invoices past their TTL — but only when no
payment is already in flight — and unregisters the Crypto Service watch for the ones it expires.
This service's first scheduled, multi-replica-coordinated job.

## Scope

**In:**
- `pom.xml` — `shedlock-spring`/`shedlock-provider-jdbc-template` (version matching
  `crypto-service`'s/`notification-service`'s own pinned version).
- `common/ShedLockConfig.java` — one `LockProvider` bean.
- `PaymentServiceApplication` — `@EnableSchedulerLock`.
- `V4__payment_app_shedlock_grant.sql` — `GRANT SELECT, INSERT, UPDATE ON payments.shedlock`
  (mirrors `notification-service`'s own empirically-confirmed grant exactly).
- `V5__payment_app_payments_table_read_grant.sql` — `GRANT SELECT ON payments.payments` (needed
  for the new native in-flight-payment-check query; no write access — this task never inserts
  into `payments`).
- `common/CryptoClient.unregisterWatch(UUID watchId)` — `DELETE /internal/v1/watches/{watchId}`.
- `invoice/Invoice.markExpired()` — narrow, package-private mutator (mirrors
  `OutboxEvent.markPublished`).
- `invoice/InvoiceRepository` — `findExpiredCandidatesWithoutInFlightPayment(Instant now)` (native
  query) and `findByInvoiceUuid(UUID)` (system-level lookup, not merchant-scoped — this job acts
  across all merchants).
- `invoice/InvoiceService` — `findExpiredCandidatesWithoutInFlightPayment()` (read-only,
  delegates), `expireInvoice(UUID invoiceUuid)` (`@Transactional`, mutates + saves).
- `invoice/InvoiceExpirySweepJob` — `@Scheduled` + `@SchedulerLock`, per-row failure isolation
  (mirrors `RetryScheduler` exactly).

**Out:**
- Any `Payment` JPA entity/repository (task 11's own scope — this task reads the real
  `payments` table via a native query only).
- An outbox event for the `EXPIRED` transition (no topic exists for it — Phase 1 finding,
  disclosed, flagged for Phase 3 challenge).
- Receipts, state-machine/consumer tasks (9-14).

## Business Rules

- **R5 (revised)** — `OPEN` + past `expires_at` + no `payments` row in `SEEN`/`CONFIRMING` →
  `EXPIRED` + watch unregistered. A payment already in flight → left `OPEN`, watch left
  registered.

## Locked Decisions

- **L3** — `unregisterWatch` is a Crypto Service REST call, not direct chain access.
- **L8** — no outbox event for this transition (disclosed reading, Phase 1).

## Dependencies

- `InvoiceExpirySweepProperties` (T03) — `cron`.
- `CryptoClient` (T06, extended).

## Inputs

- `notification-service`'s own `RetryScheduler`/`V11__notification_app_shedlock_grant.sql` —
  exact templates.
- `crypto-service`'s own `ShedLockConfig` — exact template.
- `contracts/api/crypto-internal.yaml` — `DELETE /internal/v1/watches/{watchId}` shape.

## Outputs

- New: `common/ShedLockConfig.java`, `invoice/InvoiceExpirySweepJob.java`,
  `V4__payment_app_shedlock_grant.sql`, `V5__payment_app_payments_table_read_grant.sql`.
- Modified: `pom.xml`, `PaymentServiceApplication.java`, `common/CryptoClient.java`,
  `invoice/Invoice.java`, `invoice/InvoiceRepository.java`, `invoice/InvoiceService.java`.

## State Changes

- `Invoice.state`: `OPEN` → `EXPIRED` (sweep-driven, system-initiated, not caller-initiated).

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/common/ShedLockConfig.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExpirySweepJob.java`
- `services/payment/src/main/resources/db/migration/V4__payment_app_shedlock_grant.sql`
- `services/payment/src/main/resources/db/migration/V5__payment_app_payments_table_read_grant.sql`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceExpirySweepJobTest.java`
- `services/payment/src/test/java/com/themistra/payment/common/CryptoClientUnregisterWatchTest.java` (or extend `CryptoClientTest.java` — Phase 5 decides)

## Files to Modify

- `services/payment/pom.xml`
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`

## Files NOT to Modify

- `V1`/`V2`/`V3` migrations (immutable). Anything under `spec/`.

## Acceptance Criteria

- **AC1 (R5)** — eligible invoice → `EXPIRED`, watch unregistered.
- **AC2 (R5)** — invoice with an in-flight payment → stays `OPEN`, watch stays registered.
- **AC3** — `@SchedulerLock` present on the sweep method.
- **AC4** — one invoice's own failure doesn't abort the batch.
- **AC5** — `unregisterWatch` runs outside any open transaction.

## Required Tests

- `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (R5, named).
- `shouldNotExpireInvoiceWithPaymentInFlight` (R5, named).
- `shouldContinueSweepAfterOneInvoiceFails` (AC4).
- `sweepMethodIsAnnotatedSchedulerLock` (AC3, source-text guard, mirrors
  `createMethodIsAnnotatedTransactional`'s own T05 precedent).
- `CryptoClient.unregisterWatch`'s own tests (real local HTTP server, mirrors `registerWatch`'s
  own T06 suite): success (204), failure mapping, idempotent-already-unregistered acceptance.

## Constraints

- **Transaction:** `InvoiceExpirySweepJob.sweep()` itself is NOT `@Transactional` (one
  transaction per row, mirrors `RetryScheduler`); `unregisterWatch` runs before
  `InvoiceService.expireInvoice` (`@Transactional`), never inside it.
- **Concurrency:** `@SchedulerLock(name = "invoice-expiry-sweep", lockAtMostFor = "4m",
  lockAtLeastFor = "10s")` — `lockAtMostFor` kept just under the 5-minute cron interval so a
  stuck lock self-heals before the next scheduled fire; `lockAtLeastFor` matches
  `RetryScheduler`'s own identical minimum.
- **Null handling:** new repository/service methods null-guard their own parameters, matching
  established convention.

## Open Questions

No blockers. Phase 1's two disclosed, evidence-based readings (native-query design, no outbox
event) are carried forward for Phase 3 challenge.
