# payment · T08 · Phase 3 — Design Challenge

Artifact: `.ai/prompts/payment/T08/artifacts/03-design-challenge.md`  
Consumed: `artifacts/02-task-implementation-brief.md`, `spec/payment-service/design.md`, `spec/payment-service/tasks.md`, `spec/payment-service/agents.md`, existing T06/T07 code.

---

## 1. "In-flight" payment states in the native query conflict with the task statement and `design.md`

- **Issue:** The brief's R5 wording says the sweep expires invoices with "no `payments` row in `SEEN`/`CONFIRMING`". The task statement says "no payment row in a non-terminal state", and `design.md` O2 explicitly lists the non-terminal payment states as `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING`. A payment row can exist in `CREATED` or `WATCHING` before any `chain.tx.seen` event arrives; expiring the invoice and unregistering the watch in that window would strand a payment that is already in flight.
- **Severity:** High — direct conflict with the task statement and with a resolved open decision.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:45-47` — "no `payments` row in `SEEN`/`CONFIRMING`"
  - `spec/payment-service/tasks.md` (task 8) — "no payment row in a non-terminal state"
  - `spec/payment-service/design.md:49-52` — post-expiry finalization decision lists `CREATED`/`WATCHING`/`SEEN`/`CONFIRMING` as non-terminal states that block expiry
- **Recommended brief amendment:** Change the native query to exclude invoices with any payment in state `CREATED`, `WATCHING`, `SEEN`, or `CONFIRMING`, matching `design.md` O2. Update AC2 and the named test `shouldNotExpireInvoiceWithPaymentInFlight` to cover at least one `CREATED` or `WATCHING` row.

---

## 2. No state guard on `Invoice.markExpired()` creates a race-to-overwrite risk

- **Issue:** The sweep selects candidates, then later loads each invoice by UUID and calls `markExpired()`. Between the candidate query and the update, a `chain.tx.seen` event could move the invoice to `PAID`/`UNDERPAID`/`OVERPAID`, or a concurrent sweep/run could already have moved it to `EXPIRED`. If `markExpired()` blindly sets `state = EXPIRED`, it can overwrite a terminal payment state or cause an unnecessary/conflicting update.
- **Severity:** Medium — real concurrency window, though narrow.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:26-27, 32-34` — `markExpired` described as a narrow mutator; no guard specified
  - `services/payment/src/main/java/com/themistra/payment/invoice/Invoice.java:105` — state is currently mutable without transition guard
- **Recommended brief amendment:** Specify that `markExpired()` is a no-op (or returns without mutating) when the current state is not `OPEN`. Add a test verifying that a non-OPEN invoice passed to `expireInvoice` remains unchanged.

---

## 3. `CryptoClient.unregisterWatch` idempotency is required for retries but not specified

- **Issue:** The brief says `unregisterWatch` should have a test for "idempotent-already-unregistered acceptance", but it does not specify the production behavior when the Crypto Service returns `404`/`410` (watch already gone). Because `expireInvoice` runs in its own transaction *after* `unregisterWatch`, a DB failure after a successful unregister leaves the invoice `OPEN`. The next sweep will call `unregisterWatch` again. If the client treats `404` as a failure, that invoice will be skipped forever.
- **Severity:** Medium — retry safety gap.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:25, 34-35, 119-122` — unregister before transaction, per-row failure isolation
  - `contracts/api/crypto-internal.yaml` (`spec/payment-service/design.md:184`) — `DELETE /internal/v1/watches/{watchId} -> 204`; no error shape is documented
- **Recommended brief amendment:** Explicitly state that `CryptoClient.unregisterWatch` treats HTTP `404`/`410` as success (idempotent already-unregistered) and proceeds without throwing. Add a production test and a test verifying the retry path succeeds after a previous unregister.

---

## 4. No outbox event for the `EXPIRED` transition

- **Issue:** The brief carries forward a Phase 1 finding that no outbox event is published for `OPEN -> EXPIRED`. `agents.md` L8 says "Outbox for every publish" and lists `invoice.created`, `payment.seen`, `payment.finalized`, and `receipt.issued`; it does not explicitly exclude `invoice.expired`. A merchant or downstream service that needs to react to expiry (e.g., UI update, notification, refund flow) will have no event to consume.
- **Severity:** Low/Medium — the brief flags it as a known limitation, but it is a standing-rule tension worth logging.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:39-40, 53` — "no outbox event for this transition"
  - `spec/payment-service/design.md:12` — L8: "Outbox for every publish"
- **Recommended brief amendment:** Either (a) add an `invoice.expired` outbox event and a schema under `contracts/events/payments/`, or (b) explicitly document the accepted gap with the product rationale and a backlog item.

---

## 5. `InvoiceService.findExpiredCandidatesWithoutInFlightPayment()` transaction semantics are unspecified

- **Issue:** The brief says the method is read-only and delegates to the repository, but it does not say whether it should be `@Transactional(readOnly = true)`. Without an explicit read-only transaction, the method relies on the Open-EntityManager-in-View filter or a non-transactional `EntityManager`, which is inconsistent with the read-path methods added in T07.
- **Severity:** Low.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:31-32` — read-only delegation, no annotation specified
  - `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceService.java:77-81` — T07 read methods use `@Transactional(readOnly = true)`
- **Recommended brief amendment:** Add `@Transactional(readOnly = true)` to `findExpiredCandidatesWithoutInFlightPayment` in the brief and require a source-text guard or test for it.

---

## 6. Native query return type / loading strategy is ambiguous

- **Issue:** The brief lists both `findExpiredCandidatesWithoutInFlightPayment(Instant now)` and `findByInvoiceUuid(UUID)` as new repository methods. If the native query returns `List<Invoice>`, `findByInvoiceUuid` is unnecessary and the method name should reflect it returns full entities. If it returns `List<UUID>`, the job will issue an extra `findByInvoiceUuid` per candidate (N+1).
- **Severity:** Low — implementation detail, but worth pinning down.
- **Evidence:**
  - `artifacts/02-task-implementation-brief.md:28-31`
- **Recommended brief amendment:** State the native query's return type. Prefer `List<Invoice>` mapped by the native query (or `List<UUID>` with a clear N+1 rationale) and drop the unused repository method.

---

## 7. Sweep job may call `unregisterWatch` with a `null` watchId

- **Issue:** The job selects `OPEN` invoices. In the current implementation every created invoice has a `watchId`, but the schema allows `watch_id` to be nullable and the brief does not explicitly guard against calling `unregisterWatch(null)`. A null watchId would produce an invalid DELETE URI.
- **Severity:** Low — defensive hardening.
- **Evidence:**
  - `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql:19` — `watch_id UUID` (nullable)
  - `artifacts/02-task-implementation-brief.md:25` — `unregisterWatch(UUID watchId)`
- **Recommended brief amendment:** Specify that the sweep skips any candidate with a `null` `watchId` (and logs it), or that `markExpired()`/`expireInvoice()` asserts non-null watchId before unregistering.

---

## Positive confirmations

- `@EnableScheduling` is already present in `PaymentServiceApplication`; adding `@EnableSchedulerLock` is the only remaining annotation change.
- Transaction ordering is correct: `sweep()` is not transactional, `unregisterWatch` runs before `expireInvoice(@Transactional)`, satisfying AC5.
- Failure isolation per row mirrors `RetryScheduler` and aligns with AC4.
- ShedLock `lockAtMostFor = "4m"` under the 5-minute cron interval is a sound self-healing choice.
- Granting only `SELECT` on `payments` (not `INSERT`/`UPDATE`) keeps the task's read-only promise.
