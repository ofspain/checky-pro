# payment · T08 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T08 — Invoice expiry sweep |
| **Consumes** | `artifacts/00-repository-understanding.md` |

## Business Rules

- **R5 (revised)** — when an invoice's expiry time elapses with no `payments` row in state
  `SEEN`/`CONFIRMING` (the only two non-terminal states that are ever actually persisted — see
  Phase 0's own confirmed reading), the sweep transitions `OPEN → EXPIRED` and unregisters its
  watch. An invoice with a payment already in flight is left `OPEN` past its nominal expiry so
  the payment can still reach `FINALIZED`/attest normally; its watch is **not** unregistered in
  that case.

## Locked Decisions

- **L3** — this service never touches a blockchain or signing key (unaffected by this task;
  `unregisterWatch` is still a Crypto Service REST call, not direct chain access).
- **L8** — no outbox event is named by R5 for the `EXPIRED` transition (unlike `invoice.created`/
  `payment.seen`/etc.) — confirmed by re-checking `EventTopics`'s own 4-entry map (T04): no
  `invoice.expired` topic exists. Reading: this task does **not** emit an outbox event. Flagged
  for Phase 3 challenge — a real, consequential scope boundary, not assumed without evidence.

## Files involved

**New:**
- `common/ShedLockConfig.java` — mirrors `crypto-service`'s own one-bean template.
- A ShedLock grant migration (`V4__payment_app_shedlock_grant.sql`), mirroring
  `notification-service`'s own empirically-confirmed `SELECT, INSERT, UPDATE` grant.
- A `payments`-table read-only grant migration (same or a separate `V5`, decided at Phase 2) —
  needed for the native in-flight-payment check query.
- `invoice/InvoiceExpirySweepJob.java` — the ShedLock-guarded `@Scheduled` job.

**Existing, extended:**
- `pom.xml` — add `shedlock-spring`/`shedlock-provider-jdbc-template` (version matching
  `crypto-service`'s/`notification-service`'s own pinned version).
- `PaymentServiceApplication.java` — `@EnableSchedulerLock`.
- `CryptoClient.java` — new `unregisterWatch(UUID watchId)` method.
- `Invoice.java` — a narrow `markExpired()` mutator (mirrors `OutboxEvent.markPublished`).
- `InvoiceRepository.java` — a native query for "OPEN, past-expiry, no in-flight payment"
  candidates.
- `InvoiceService.java` — the actual expire-one-invoice transactional method.

**Not to modify:** `V1`/`V2`/`V3` migrations, anything under `spec/`.

## Dependencies

- `InvoiceExpirySweepProperties` (T03, already stubbed — `cron`).
- `CryptoClient` (T06, extended this task).
- The real `payments` table (T02, read-only from this task's own new query — no write).

## Acceptance Criteria

- **AC1 (R5)** — an `OPEN` invoice past `expires_at` with no `payments` row in
  `SEEN`/`CONFIRMING` → `EXPIRED`, watch unregistered.
- **AC2 (R5)** — an `OPEN` invoice past `expires_at` WITH a `payments` row in
  `SEEN`/`CONFIRMING` → left `OPEN`, watch left registered.
- **AC3** — the sweep runs under a ShedLock (`@SchedulerLock`), so only one replica's sweep runs
  at a time.
- **AC4** — one invoice's own expiry/unregister failure does not abort the sweep for the rest of
  the batch (mirrors `RetryScheduler`'s own per-row failure isolation).
- **AC5** — `unregisterWatch` runs outside any open database transaction (mirrors T06's own
  transaction-boundary discipline for `registerWatch`).

## Tests required

- `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (R5) — named, `package.md` §8.
- `shouldNotExpireInvoiceWithPaymentInFlight` (R5, revised) — named.
- Implied: ShedLock-guarded (the job method itself only needs proving it's annotated correctly,
  per `createMethodIsAnnotatedTransactional`'s own T05 source-text-guard precedent — no real
  lock-acquisition test, matching `RetryScheduler`'s own established testing convention); one
  failing invoice does not block the rest of the batch; `unregisterWatch`'s own new `CryptoClient`
  method (request shape, error handling) gets its own test, mirroring `registerWatch`'s own T06
  test suite.

## Open Questions

1. **(Phase 0 item 1, now resolved by direct evidence above)** The native-query design and the
   "no outbox event" reading are both carried forward as **disclosed, evidence-based
   interpretations**, explicitly flagged for Phase 3 challenge — not blockers, but the two most
   consequential calls in this task.
