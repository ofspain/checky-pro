STATUS: FROZEN (approved 2026-10-14, Human Approval gate)

# payment · T08 · Frozen Task Brief

## Phase 3 disposition (6 findings ACCEPTED, 1 REJECTED/documented)

1. **Native query only excluded `SEEN`/`CONFIRMING`, not the full `CREATED`/`WATCHING`/`SEEN`/
   `CONFIRMING` set `design.md` O2 names.** ACCEPTED — widened to all 4 states. Re-verified
   directly: `payments.tx_hash` is `NOT NULL` with `UNIQUE (chain, tx_hash)`, so a real row
   literally cannot exist in `CREATED`/`WATCHING` today (no transaction hash exists yet at that
   point) — the original 2-state reasoning was empirically correct, not wrong — but checking all
   4 costs nothing and matches the task's own literal text and `design.md` O2 exactly, so it is
   widened regardless, as defense-in-depth against any future architecture change.
2. **No state guard on `markExpired()`.** ACCEPTED — the mutator becomes a no-op unless the
   current state is `OPEN` (closes a narrow concurrency window: a `chain.tx.seen` event or a
   concurrent sweep run moving the invoice between the candidate query and the update).
3. **`unregisterWatch` idempotency on `404`/`410` not specified.** ACCEPTED — treated as success
   (already-unregistered), so a retried sweep after a prior successful unregister (but failed DB
   commit) doesn't get stuck.
4. **No outbox event for `EXPIRED`.** REJECTED as an action for this task — building it would
   require touching T04's own frozen `EventTopics` and authoring a new
   `contracts/events/payments/` schema file, a real scope expansion beyond R5's own text.
   Documented explicitly below with product rationale, as a backlog item.
5. **`@Transactional(readOnly = true)` on the candidate-finder.** ACCEPTED.
6. **Redundant `findByInvoiceUuid` / native-query return-type ambiguity.** ACCEPTED, resolved by
   dropping `findByInvoiceUuid` entirely — the native query returns `List<Invoice>` directly;
   `expireInvoice` takes the already-fetched (now-detached) `Invoice` and calls
   `repository.save(invoice)` after `markExpired()`, a merge — mirrors `RetryScheduler`'s own
   proven detached-entity-save pattern exactly, and is cleaner than the original two-method
   design.
7. **`unregisterWatch(null)` risk.** ACCEPTED — the sweep skips (and logs) any candidate with a
   `null` `watchId`.

### Finding 4's own disclosed, accepted gap (not a defect)

No `invoice.expired` outbox event exists. `EventTopics` (T04) is frozen and lists only
`invoice`/`payment-seen`/`payment-finalized`/`receipt` — no 5th entry, and no
`contracts/events/payments/` schema exists for it. R5's own text names only the state transition
and watch unregistration, not an event emission. Building one now would be a real, cross-task
scope expansion (touching a different task's frozen file, authoring a new contract). Tracked as
a disclosed backlog item: a future task should add `invoice.expired` if a downstream consumer
(merchant notification, UI refresh) is ever needed — not required for R5's own correctness.

## Task

T08 — Invoice expiry sweep.

## Purpose

A ShedLock-guarded scheduled job that expires `OPEN` invoices past their TTL — but only when no
payment is already in flight — and unregisters the Crypto Service watch for the ones it expires.

## Scope

**In:**
- `pom.xml` — `shedlock-spring`/`shedlock-provider-jdbc-template`.
- `common/ShedLockConfig.java`.
- `PaymentServiceApplication` — `@EnableSchedulerLock`.
- `V4__payment_app_shedlock_grant.sql` — `GRANT SELECT, INSERT, UPDATE ON payments.shedlock`.
- `V5__payment_app_payments_table_read_grant.sql` — `GRANT SELECT ON payments.payments`.
- `common/CryptoClient.unregisterWatch(UUID watchId)` — treats `404`/`410` as success.
- `invoice/Invoice.markExpired()` — no-op unless current state is `OPEN`.
- `invoice/InvoiceRepository.findExpiredCandidatesWithoutInFlightPayment(Instant now)` — native
  query, excludes any invoice with a `payments` row in `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING`.
  **No `findByInvoiceUuid` method** (Phase 3 finding 6 — dropped).
- `invoice/InvoiceService.findExpiredCandidatesWithoutInFlightPayment()` (read-only, delegates),
  `expireInvoice(Invoice invoice)` (`@Transactional`, takes the detached entity directly,
  mutates + saves/merges).
- `invoice/InvoiceExpirySweepJob` — `@Scheduled` + `@SchedulerLock`, skips/logs a `null`
  `watchId` candidate, per-row failure isolation (mirrors `RetryScheduler`).

**Out:**
- Any `Payment` JPA entity/repository (task 11's own scope).
- An `invoice.expired` outbox event (disclosed backlog item, not this task).
- Receipts, state-machine/consumer tasks (9-14).

## Business Rules

- **R5 (revised)** — `OPEN` + past `expires_at` + no `payments` row in `CREATED`/`WATCHING`/
  `SEEN`/`CONFIRMING` → `EXPIRED` + watch unregistered. A payment already in flight (any of
  those 4 states) → left `OPEN`, watch left registered.

## Locked Decisions

- **L3** — `unregisterWatch` is a Crypto Service REST call only.
- **L8** — no outbox event for this transition (disclosed, accepted gap above).

## Dependencies

- `InvoiceExpirySweepProperties` (T03).
- `CryptoClient` (T06, extended).

## Inputs

- `notification-service`'s own `RetryScheduler`/`V11__notification_app_shedlock_grant.sql`.
- `crypto-service`'s own `ShedLockConfig`.
- `contracts/api/crypto-internal.yaml` — `DELETE /internal/v1/watches/{watchId}`.

## Outputs

- New: `common/ShedLockConfig.java`, `invoice/InvoiceExpirySweepJob.java`,
  `V4__payment_app_shedlock_grant.sql`, `V5__payment_app_payments_table_read_grant.sql`.
- Modified: `pom.xml`, `PaymentServiceApplication.java`, `common/CryptoClient.java`,
  `invoice/Invoice.java`, `invoice/InvoiceRepository.java`, `invoice/InvoiceService.java`.

## State Changes

- `Invoice.state`: `OPEN` → `EXPIRED` (sweep-driven), guarded to a no-op if not currently `OPEN`.

## Files to Create

- `services/payment/src/main/java/com/themistra/payment/common/ShedLockConfig.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExpirySweepJob.java`
- `services/payment/src/main/resources/db/migration/V4__payment_app_shedlock_grant.sql`
- `services/payment/src/main/resources/db/migration/V5__payment_app_payments_table_read_grant.sql`
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceExpirySweepJobTest.java`
- `CryptoClientTest.java` gains `unregisterWatch` tests (same file, not a new one).

## Files to Modify

- `services/payment/pom.xml`
- `services/payment/src/main/java/com/themistra/payment/PaymentServiceApplication.java`
- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceRepository.java`
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java`

## Files NOT to Modify

- `V1`/`V2`/`V3` migrations (immutable). `events/EventTopics.java` (T04, frozen — Finding 4
  disposition). Anything under `spec/`.

## Acceptance Criteria

- **AC1 (R5)** — eligible invoice → `EXPIRED`, watch unregistered.
- **AC2 (R5)** — invoice with a payment in ANY of `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING` →
  stays `OPEN`, watch stays registered.
- **AC3** — `@SchedulerLock` present on the sweep method.
- **AC4** — one invoice's own failure doesn't abort the batch.
- **AC5** — `unregisterWatch` runs outside any open transaction.
- **AC6 (Phase 3 finding 2)** — `markExpired()` is a no-op on a non-`OPEN` invoice.
- **AC7 (Phase 3 finding 3)** — `unregisterWatch` treats `404`/`410` as success.
- **AC8 (Phase 3 finding 7)** — a `null`-`watchId` candidate is skipped and logged, not passed
  to `unregisterWatch`.

## Required Tests

- `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (R5, named).
- `shouldNotExpireInvoiceWithPaymentInFlight` (R5, named) — covers at least one `CREATED`/
  `WATCHING` case, not only `SEEN`/`CONFIRMING` (Phase 3 finding 1).
- `shouldContinueSweepAfterOneInvoiceFails` (AC4).
- `sweepMethodIsAnnotatedSchedulerLock` (AC3, source-text guard).
- `shouldNotOverwriteNonOpenInvoiceState` (AC6).
- `CryptoClient.unregisterWatch` tests: success (`204`), already-unregistered (`404`/`410`
  treated as success), genuine failure mapping.
- `shouldSkipCandidateWithNullWatchId` (AC8).

## Constraints

- **Transaction:** `sweep()` not `@Transactional`; `unregisterWatch` runs before
  `expireInvoice` (`@Transactional`), never inside it.
- **Concurrency:** `@SchedulerLock(name = "invoice-expiry-sweep", lockAtMostFor = "4m",
  lockAtLeastFor = "10s")`.
- **Null handling:** new methods null-guard their own parameters.

## Open Questions

None. All resolved above.
