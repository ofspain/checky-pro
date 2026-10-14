# payment · T08 · Phase 8 — Independent Code Review

Artifact: `.ai/prompts/payment/T08/artifacts/08-independent-review.md`  
Consumed: `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md`, current implementation, and existing tests.

---

## Executive summary

- `mvn -pl services/payment test`: **159 tests run, 3 failures** — all in the pre-existing `SchemaBaselineMigrationIntegrationTest` (T02), which has not been updated for the new V4/V5 migrations.
- The production-code design has one serious correctness issue: `InvoiceService.expireInvoice(Invoice)` merges a detached invoice loaded by the candidate query, so a concurrent `chain.tx.*` event that moves the invoice out of `OPEN` can be silently overwritten back to `EXPIRED`.
- Otherwise the implementation follows the frozen brief: ShedLock wiring, grants, native-query in-flight check, `404`/`410` idempotency, null-watchId skip, and transaction ordering are all correct.

---

## 1. `expireInvoice` merges a detached invoice, creating a lost-update race

- **Issue:** `InvoiceExpirySweepJob.sweep()` loads candidates in a read-only transaction, closes it, calls `cryptoClient.unregisterWatch` (network), and then calls `invoiceService.expireInvoice(invoice)`. That method calls `markExpired()` on the **detached** invoice and then `repository.save(invoice)`, which is a JPA merge. Hibernate loads the current DB row by ID and copies the detached object's fields onto it. If a `chain.tx.seen` event moved the invoice to `PAID` (or any other state) between the candidate query and the save, the detached invoice still has `state = OPEN`, so the merge copies `OPEN` back into the persistence context and then `markExpired()` sets it to `EXPIRED`. The payment's state change is lost.
- **Severity:** High — a paid invoice could be incorrectly expired.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExpirySweepJob.java:40-59` (load → detach → unregister → expire)
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:105-110` (`expireInvoice` saves the detached invoice)
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:116-121` (`markExpired` checks the object's own state, not the DB state)
- **Recommendation:** Change `expireInvoice` to take `UUID invoiceUuid` (or the surrogate `Long id`) and re-load the invoice inside its own transaction, then check state. Even better, perform a conditional native/stateless update such as `UPDATE invoices SET state='EXPIRED', updated_at=... WHERE id=:id AND state='OPEN'`, returning whether a row was updated. Add a test that simulates this exact concurrent-state-change race.
- **Confidence:** High.

---

## 2. `SchemaBaselineMigrationIntegrationTest` is stale again

- **Issue:** T08 added `V4__payment_app_shedlock_grant.sql` and `V5__payment_app_payments_table_read_grant.sql`, but the T02 integration test still expects exactly versions `1`, `2`, `3` and still lists `payments`/`shedlock` as tables `payment_app` must not access. The build therefore fails with three assertion errors.
- **Severity:** High — CI is red.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java:146` expects `["1", "2", "3"]`
  - `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java:272` / `:234` treat `payments` and `shedlock` as ungranted
  - `services/payment/src/main/resources/db/migration/V4__payment_app_shedlock_grant.sql`
  - `services/payment/src/main/resources/db/migration/V5__payment_app_payments_table_read_grant.sql`
- **Recommendation:** Update the T02 test to expect versions `1-5` and to assert the exact, scoped privileges on `shedlock` (SELECT/INSERT/UPDATE) and `payments` (SELECT only). Keep the negative assertions for tables that truly remain ungranted.
- **Confidence:** High.

---

## 3. `expireInvoice` saves even when `markExpired` is a no-op

- **Issue:** When the invoice state is no longer `OPEN` when `expireInvoice` runs, `markExpired()` returns without mutating anything, but `expireInvoice` still calls `repository.save(invoice)`. The merge/flush can still trigger an `UPDATE` of `updated_at` (via `@PreUpdate`) and potentially overwrite a concurrently changed `updated_at`.
- **Severity:** Medium — related to finding 1 but can occur even without a state change.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:106-109`
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:133-136` (`@PreUpdate` sets `updatedAt = Instant.now()`)
- **Recommendation:** Make `markExpired()` return a `boolean` (true if changed) and only call `repository.save(invoice)` when it returns true. Combined with finding 1's recommendation, the safest fix is a conditional update query that naturally returns the number of rows changed.
- **Confidence:** High.

---

## 4. Native query not empirically verified against a real database

- **Issue:** `InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment` is a native query returning `List<Invoice>` via `SELECT i.*`. The self-review already flags this as a carried-forward process gap because Docker is unavailable in this environment. The risk is a subtle column-name or type-mapping mismatch that only a real Postgres run would catch.
- **Severity:** Medium — process gap, not a known defect.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java:23-32`
  - `artifacts/07-self-review.md:11-23`
- **Recommendation:** Add a Testcontainers-backed integration test (or, at minimum, a `@DataJpaTest` against an embedded DB if schema compatibility is close enough) that exercises the query with seed invoices and in-flight payment rows. The `SchemaBaselineMigrationIntegrationTest` infrastructure already spins up Postgres.
- **Confidence:** Medium.

---

## 5. Phase 10 test files are missing

- **Issue:** The frozen brief lists `InvoiceExpirySweepJobTest.java` and additional `CryptoClient.unregisterWatch` tests as Phase 10 outputs. Neither exists yet, so AC1–AC8 and the unregister contract are not under automated test.
- **Severity:** Medium — expected at this phase, but worth tracking.
- **Evidence:**
  - `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceExpirySweepJobTest.java` — does not exist
  - `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java` — contains only `registerWatch` tests
  - `artifacts/04-frozen-task-brief.md:146-156` lists required tests
- **Recommendation:** Create the test files in Phase 10. Prioritize a race-regression test for finding 1 and a test proving `unregisterWatch` treats `404`/`410` as success.
- **Confidence:** High.

---

## 6. Race window between in-flight check and expiration

- **Issue:** The native query proves there is no in-flight payment at the moment the candidate list is built. A payment row in `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING` can be inserted after that moment but before `expireInvoice` commits. The current `markExpired` guard only checks the detached invoice's stale `OPEN` state, so it does not prevent expiring an invoice that just received an in-flight payment.
- **Severity:** Medium — same root cause as finding 1; fixing finding 1 with a conditional `WHERE state='OPEN'` update also closes this.
- **Evidence:**
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java:23-32` (check at query time)
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:105-110` (no re-check)
- **Recommendation:** Use the conditional update `WHERE id = :id AND state = 'OPEN'` inside `expireInvoice`, or re-check for in-flight payments inside the same transaction before updating.
- **Confidence:** High.

---

## Positive confirmations

- **ShedLock wiring:** `ShedLockConfig` provides a `JdbcTemplateLockProvider`, `@EnableSchedulerLock` is present, and the method-level `lockAtMostFor = "4m"` is under the 5-minute cron interval.
- **Grants are minimal and correct:** V4 gives `SELECT/INSERT/UPDATE` on `shedlock`; V5 gives only `SELECT` on `payments`.
- **Transaction ordering:** `sweep()` is not transactional; `unregisterWatch` runs strictly before `expireInvoice(@Transactional)`, satisfying AC5.
- **Idempotent unregister:** `CryptoClient.unregisterWatch` catches `NotFound`/`Gone` and treats them as success, so a retry after a prior successful unregister does not get stuck.
- **Null `watchId` defense:** `InvoiceExpirySweepJob.processOne` skips and logs candidates with a null `watchId`.
- **Native query scope:** The in-flight check correctly excludes all four non-terminal payment states (`CREATED`, `WATCHING`, `SEEN`, `CONFIRMING`).
- **Locked decision honored:** No `invoice.expired` outbox event is emitted.

---

## Recommended follow-up order

1. Fix `expireInvoice` to avoid the detached-entity merge (use a fresh load or conditional update) — this is the only correctness blocker.
2. Update `SchemaBaselineMigrationIntegrationTest` for V4/V5.
3. Create `InvoiceExpirySweepJobTest` and `CryptoClient.unregisterWatch` tests in Phase 10, including a regression test for finding 1.
4. Add a real-Postgres integration test for the native candidate query.
