# payment · T08 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T08 — Invoice expiry sweep |
| **Consumes** | — (entry phase) |

## 1. Architecture summary

As of T07, `payment-service` has: `Invoice`/`InvoiceService`/`InvoiceController` (create + read,
T05-T07), `CryptoClient.registerWatch` (T06, no `unregisterWatch` yet), the outbox pipeline
(T04). No `@Scheduled`/ShedLock-coordinated job exists anywhere in this service yet — `ShedLock`
is explicitly excluded from the pom (T01's own negative check: "ShedLock lands at task 8, not
this task"), and `PaymentServiceApplication`'s own Javadoc already anticipates this: "
`@EnableSchedulerLock` lands only in the task that introduces the first ShedLock-coordinated
job (task 8, invoice expiry sweep)."

## 2. Existing code this task touches

- **New infrastructure, not yet present**: ShedLock (`shedlock-spring`/
  `shedlock-provider-jdbc-template`, both already used by `crypto-service`/`notification-service`
  — exact version/dependency precedent exists), a `LockProvider` bean (`ShedLockConfig`, mirrors
  `crypto-service`'s own one-bean template), `@EnableSchedulerLock` on
  `PaymentServiceApplication`.
- **`CryptoClient` (T06) is missing `unregisterWatch`** — only `registerWatch` exists. The real
  contract (`crypto-internal.yaml`) already defines `DELETE /internal/v1/watches/{watchId} ->
  204 (idempotent - also returned for an already-unregistered watch)`. This task must add it —
  a necessary implied consequence of task 8's own explicit "unregisters the watch" text.
- **`Invoice` (T05) has no mutator for `state`** — every field is set only at construction; T08
  needs to transition `OPEN → EXPIRED` on an existing row, requiring a new, narrow mutator
  (mirrors `OutboxEvent.markPublished(Instant)`'s own established narrow-mutator precedent, not
  a generic setter).
- **A genuine, real architectural tension, not assumed away**: R5's own text says "no payment
  row in any non-terminal state (`CREATED`, `WATCHING`, `SEEN`, or `CONFIRMING`)" — but no
  `Payment` JPA entity/repository exists yet (task 11's own scope). Per this session's own
  confirmed T06 finding, `CREATED`/`WATCHING` are never persisted as `payments` table rows at
  all (tracked only via `Invoice.watchId`) — only `SEEN`/`CONFIRMING` (and later states) ever
  produce a real row, and only starting at task 11's own `chain.tx.seen` handling. The real,
  already-migrated `payments` table (T02) exists in the schema regardless of whether any Java
  code reads it yet. **Reading, not assumed**: T08's own "no payment row in a non-terminal
  state" check reduces, given the real architecture, to "no `payments` row exists with
  `state IN ('SEEN','CONFIRMING')`" — `CREATED`/`WATCHING` rows categorically never exist, so
  checking for their absence is automatically satisfied. Flagged for Phase 3 challenge — this is
  the single most consequential interpretation in this task.
- **`payment_app` has no grant on `shedlock` or `payments` yet** — confirmed by direct read of
  `V2`/`V3` migrations and `SchemaBaselineMigrationIntegrationTest`'s own `UNGRANTED_TABLES`
  list (still contains `payments`, `shedlock` as of T07). A new migration is needed.

## 3. Established patterns to follow

- **ShedLock-guarded scheduled job**: `notification-service`'s own `RetryScheduler` is the exact
  template — `sweep()` is `@Scheduled` + `@SchedulerLock(name=..., lockAtMostFor=...,
  lockAtLeastFor=...)`, deliberately NOT `@Transactional` itself (one transaction per row, never
  one for the whole sweep — a single row's failure/rollback must never affect another
  already-committed row); fetches a batch, then calls a separate `@Transactional` `processOne`
  per row, catching and logging any exception so the sweep continues past a single row's
  failure.
- **`ShedLockConfig`**: `crypto-service`'s own one-bean template
  (`JdbcTemplateLockProvider(dataSource)`), default table name `shedlock` (already the real
  table name — no custom config needed).
- **`shedlock` table grant**: `notification-service`'s own `V11__notification_app_shedlock_grant.sql`
  is the exact, empirically-confirmed precedent: `GRANT SELECT, INSERT, UPDATE` (no `DELETE` —
  "a lock row is never removed, only ever extended or allowed to expire"), confirmed directly
  against `shedlock-sql-support`'s own real SQL, not assumed.
- **Outside-the-transaction external call**: T06's own `InvoiceController` redesign (compute,
  call `CryptoClient` outside any transaction, then persist) is the direct precedent for this
  task's own `unregisterWatch` call — it must not run inside `processOne`'s own `@Transactional`
  boundary.

## 4. Testing conventions

- `RetrySchedulerTest` (`notification-service`) is the template for a ShedLock-guarded job's own
  unit test (plain JUnit/Mockito, no real scheduler/lock involved — `@SchedulerLock` itself is
  framework plumbing, not exercised by a unit test).
- No Testcontainers precedent yet in `payment-service` for a real ShedLock acquisition test —
  `notification-service`'s own equivalent (if any) would be the template to check in Phase 1/2
  if one is needed.

## 5. Known gaps / unknowns (I do not know — not assumed)

1. **Exact cron-batch query shape for "OPEN invoices past expiry with no in-flight payment."**
   Needs a `@Query` (JPQL or native) combining `invoices.state = 'OPEN' AND expires_at <= :now`
   with a NOT-EXISTS/LEFT-JOIN check against the real `payments` table for `state IN
   ('SEEN','CONFIRMING')`. A native query is the only option without a `Payment` JPA entity
   (not this task's scope to build — task 11's own). Flagged for Phase 2 design.
2. **Batch size / lock timeout values.** No precedent states a specific number for this job;
   `RetryScheduler`'s own `lockAtMostFor="5m"` is a reasonable template value, not a requirement
   to literally copy without justification.
3. **Does `unregisterWatch` failure block the sweep of that one invoice, or is it logged and
   retried next sweep (leaving the invoice `OPEN` past expiry, watch still registered)?** No
   requirement text addresses this explicitly. Reading: treat it the same as `RetryScheduler`'s
   own per-row failure handling — log and skip, invoice stays `OPEN`, retried on the next sweep
   (the watch staying registered a little longer is a known, low-risk, self-healing situation,
   not a correctness violation).

Phase 1 (Specification Extraction) resolves what the spec itself answers; anything still open
after that goes to Phase 3 (Kimi design challenge) as a named finding.
