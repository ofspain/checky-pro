# payment · T08 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T08 — Invoice expiry sweep |
| **Consumes** | `artifacts/06-implementation-notes.md` |

Findings only — no fixes applied here (Phase 9).

## Finding 1 — Native query not empirically verified against a real database

- **Severity:** Medium (carried-forward process gap, not a known defect).
- **Evidence:** `findExpiredCandidatesWithoutInFlightPayment`'s own `@Query(nativeQuery = true)`
  relies on Hibernate's standard native-query-to-entity mapping (`SELECT i.*` returning every
  real column, matching `Invoice`'s own `@Column` annotations exactly, same discipline as
  `OutboxEvent`'s own T04 verification). Docker is unavailable in this environment, so this
  could not be run against a real Postgres this session — same carried-forward gap as the
  already-disclosed T04/T05/T06 DB-mapping verification items.
- **Recommendation:** No fix requested now; Phase 10's own test suite should include at least
  one assertion that would catch a gross SQL error (e.g., a Testcontainers-backed test if one is
  judged worth the added infrastructure, or — more consistent with this task's own established
  unit-test-only scope — accept this as a disclosed gap until Docker is available).

## Retry-safety end-to-end trace (self-verification, not a finding)

Traced the full failure/retry sequence by hand: (1) `unregisterWatch` fails → exception
propagates to `sweep()`'s own catch, logged, invoice stays `OPEN`, watch stays registered, retried
next sweep — correct. (2) `unregisterWatch` succeeds but `expireInvoice` then fails (e.g. a DB
error) → watch is now unregistered but the invoice stays `OPEN`; the next sweep calls
`unregisterWatch` again on an already-gone watch, which Phase 3 finding 3's own `404`/`410` fix
treats as success, so the retry correctly proceeds straight to `expireInvoice` without getting
permanently stuck. Both failure directions self-heal on the next scheduled run.

## Review checklist

- **Correctness:** `markExpired()`'s guard (`state != OPEN` → no-op) correctly prevents
  overwriting a terminal/concurrently-changed state, traced directly against the entity's own
  field.
- **Module boundaries:** no feature-module boundary crossed; the native query reads
  `payments.payments` directly (no entity class for it, by design — task 11's own scope).
- **Thread-safety:** `InvoiceExpirySweepJob` holds no mutable state; `@SchedulerLock` guarantees
  single-execution across replicas, not addressed by application-level synchronization.
- **Transaction boundary:** re-confirmed `unregisterWatch` runs before `expireInvoice`
  (`@Transactional`), never inside it — same discipline as T06's `registerWatch`.
- **Null handling:** `expireInvoice` null-guards its own parameter; `processOne`'s own
  `null`-`watchId` skip is a deliberate, disclosed defensive path (Phase 3 finding 7), confirmed
  not currently reachable through any real code path (every invoice gets a `watchId` at creation,
  T06).

## Verdict

One disclosed, carried-forward verification gap (Finding 1), not a code defect. No fixes
required before Phase 8.
