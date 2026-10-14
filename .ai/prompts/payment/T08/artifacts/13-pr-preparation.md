# payment · T08 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring every prior task's own identical
convention. This artifact is the consolidated summary of that already-committed history, not a
pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T08: invoice expiry sweep (ShedLock)`

## Commit message

```
payment-service T08: invoice expiry sweep (ShedLock)

Adds InvoiceExpirySweepJob, a ShedLock-guarded @Scheduled sweep that
transitions eligible OPEN invoices to EXPIRED and unregisters their Crypto
Service watch (R5). Eligibility excludes any invoice with a payments row
in any of the full non-terminal set - CREATED/WATCHING/SEEN/CONFIRMING,
not only SEEN/CONFIRMING - per design.md O2 and the task's own text
(Phase 3/4 widened this from an earlier, narrower reading).

State transitions use a single atomic, conditional
UPDATE invoices SET state = :new WHERE state = :expected (Invoice-
Repository.updateStateIfCurrentState), not a load-mutate-save of a
detached entity: Kimi's Phase 8 independent review found the original
Phase 6 design (mirroring notification-service's own RetryScheduler
pattern) created a real lost-update race, because the detached Invoice's
own state field is always OPEN (the candidate query's own filter) and a
merge-save could silently overwrite a real concurrent state change back
to EXPIRED. The atomic update closes this, and the no-longer-needed
Invoice.markExpired() mutator was removed.

A second, more significant defect was self-identified while resolving
Kimi's Phase 11 test-review findings: V3 (T06) granted only INSERT,
SELECT on payments.invoices, never UPDATE - meaning the atomic update
above would fail with a real permission-denied error against a live
database. Fixed with V6__payment_app_invoice_update_grant.sql.

CryptoClient gained unregisterWatch (DELETE /internal/v1/watches/{id}),
treating 404/410 as success for retry-safety. A disclosed, accepted
residual race remains: unregisterWatch runs against the batch-stale
candidate state before the atomic update is attempted, so a payment
arriving in that narrow window loses its watch even though the DB row
is correctly left alone - logged at WARN, not silently accepted, closing
it fully is a future hardening task if the window is ever shown to
matter in practice.

Two full Kimi review passes (Phase 8, Phase 11) together found the two
defects above and a comprehensive set of real coverage gaps, all fixed.
InvoiceRepositoryIntegrationTest (new, Testcontainers-backed) proves both
the candidate query and the atomic update against a real payment_app
connection - not run locally this session (Docker unavailable), same
disclosed gap as T06/T07/T08's own earlier equivalents.

Spec: spec/payment-service/tasks.md task 8. Requirements: R5. Locked
decisions: L3, L8.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/payment/src/main/java/com/themistra/payment/common/ShedLockConfig.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExpirySweepJob.java`
- `services/payment/src/main/resources/db/migration/V4__payment_app_shedlock_grant.sql`
- `services/payment/src/main/resources/db/migration/V5__payment_app_payments_table_read_grant.sql`
- `services/payment/src/main/resources/db/migration/V6__payment_app_invoice_update_grant.sql` (disclosed addition beyond the frozen brief — Phase 11 self-identified grant bug)
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceExpirySweepJobTest.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceRepositoryIntegrationTest.java` (disclosed addition beyond the frozen brief — Phase 11 Gap 1/2 resolution)

**Modified**
- `services/payment/pom.xml`
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java` (disclosed addition — V4/V5/V6 grant proofs)
- `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` (established necessary-consequence-update pattern — ShedLock dependency/annotation)
- `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceServiceTest.java`

**Process artifacts (not application code)**
- `.ai/prompts/payment/T08/artifacts/00-*.md` through `12-*.md`.

## Summary

Invoices that are never paid now expire automatically: a ShedLock-guarded scheduled sweep moves
eligible `OPEN` invoices to `EXPIRED` and tears down their Crypto Service watch, skipping any
invoice with a payment genuinely in flight. The task surfaced two real defects beyond its own
initial design — a lost-update race (Kimi Phase 8) and a missing database grant that would have
made the race's own fix non-functional in production (self-identified, Phase 11) — both fixed
before merge, not shipped silently.

## Testing performed

- `mvn -pl services/payment -am compile` / `test-compile` — clean throughout.
- 39 new tests added this task (17 in `InvoiceServiceTest`, 7 in `InvoiceExpirySweepJobTest`, 5 in
  `CryptoClientTest`, 2 new privilege tests in `SchemaBaselineMigrationIntegrationTest`, plus the
  3-test `InvoiceRepositoryIntegrationTest`, not yet run).
- **162/162 non-Docker-dependent tests passing**, up from 141 before this task.
- Mutation tests across Phases 10/11/12 on: `expireInvoice`'s return-value semantics, the
  per-invoice `try/catch` isolation, the null-`watchId` guard, the native query's 4-state
  exclusion, the `404`/`410`-as-success handling, the `@Transactional`/`@Scheduled` annotations,
  the `i.updatedAt = :now` clause, the `unregisterWatch` URI, the residual-race `WARN` log
  message, and the `unregisterWatch`-before-`expireInvoice` ordering — each caught its introduced
  defect, each reverted clean (via `Edit`, never `git checkout --`, for anything with uncommitted
  changes in that file).
- `SchemaBaselineMigrationIntegrationTest` and `InvoiceRepositoryIntegrationTest` (both
  Testcontainers-backed) were not run locally this session — Docker unavailable throughout.
  Disclosed, carried-forward gap; expected to be empirically confirmed next time Kimi's own
  Docker-enabled environment runs the suite.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 8 — "Invoice expiry sweep."
- **Requirement IDs:** R5.
- **LOCKED decisions:** L3, L8.

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T08.**
