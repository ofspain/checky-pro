# payment · T08 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T08 — Invoice expiry sweep |
| **Consumes** | `artifacts/05-implementation-plan.md` |

## Files created

- `V4__payment_app_shedlock_grant.sql` — `GRANT SELECT, INSERT, UPDATE ON payments.shedlock`,
  mirrors `notification-service`'s own empirically-confirmed grant exactly.
- `V5__payment_app_payments_table_read_grant.sql` — `GRANT SELECT ON payments.payments`.
- `common/ShedLockConfig.java` — one `LockProvider` bean, mirrors `crypto-service`'s own
  identical template.
- `invoice/InvoiceExpirySweepJob.java` — `@Scheduled` + `@SchedulerLock`, per-invoice failure
  isolation, mirrors `notification-service`'s own `RetryScheduler` shape exactly.

## Files modified

- `pom.xml` — `shedlock-spring`/`shedlock-provider-jdbc-template` (version 7.9.0, matching
  `notification-service`'s own pinned version).
- `PaymentServiceApplication.java` — `@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")`.
- `common/CryptoClient.java` — new `unregisterWatch(UUID watchId)`; `404`/`410` treated as
  success (Phase 3 finding 3).
- `invoice/Invoice.java` — new package-private `markExpired()`, a no-op unless current state is
  `OPEN` (Phase 3 finding 2).
- `invoice/InvoiceRepository.java` — new native-query method
  `findExpiredCandidatesWithoutInFlightPayment(Instant now)`, excluding the full
  `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING` non-terminal set (Phase 3/4 finding 1). No
  `findByInvoiceUuid` method (Phase 3/4 finding 6 — dropped by design).
- `invoice/InvoiceService.java` — new `findExpiredCandidatesWithoutInFlightPayment()`
  (`@Transactional(readOnly = true)`) and `expireInvoice(Invoice invoice)` (`@Transactional`,
  takes the detached entity directly, mirrors `RetryScheduler`'s own detached-entity-save
  pattern).

## Existing tests updated (necessary-consequence updates, not new test authorship)

- `T01SkeletonRegressionTest.java` — the pom's own negative ShedLock check flipped to a positive
  one (T08 is the task that introduces it); the application-class annotation count moved from 3
  to 4 (`@EnableSchedulerLock`).

## Not written this phase (deliberately deferred to Phase 10)

- `InvoiceExpirySweepJobTest.java`, `CryptoClient.unregisterWatch`'s own tests (added to the
  existing `CryptoClientTest.java`), and the `markExpired`/race-guard test.

## Verification performed this phase

- `mvn -pl services/payment -am compile` — clean.
- `mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` —
  **141/141 passing, unchanged count** from before this task (the 2 necessary-consequence test
  updates kept the suite green at the same count, no new tests added yet).

## Traceability to frozen brief

- AC1/AC2 (R5) — implemented; not yet unit-tested (Phase 10).
- AC3 — `@SchedulerLock` present on `sweep()`.
- AC4 — per-invoice try/catch in `sweep()`.
- AC5 — `unregisterWatch` called before `expireInvoice` (`@Transactional`), never inside it.
- AC6 — `markExpired()` no-op guard implemented.
- AC7 — `unregisterWatch`'s own `404`/`410` handling implemented.
- AC8 — `processOne`'s own `null`-`watchId` skip-and-log implemented.

No code deviates from the frozen brief.
