# payment · T08 · Phase 10 — Test Generation

## Placement decision

`expireInvoice`/`findExpiredCandidatesWithoutInFlightPayment` tests were added to the existing
`InvoiceServiceTest.java` (not a new dedicated file) — these are core `InvoiceService` behaviors
alongside `create`, reusing its established fixtures (`NOW`, `MERCHANT_ACCOUNT_UUID`,
`INVOICE_UUID`, `WATCH_ID`, `EXPIRES_AT`, mocked `repository`/`outboxPublisher`, `Clock.fixed`).
A new dedicated `InvoiceExpirySweepJobTest.java` was created for the job's own orchestration
logic, mirroring `notification-service`'s `RetrySchedulerTest` shape exactly (mocked
`InvoiceService`/`CryptoClient`, no Spring context, no Docker, `processOne` called directly).
`CryptoClientTest.java` gained `unregisterWatch` tests in place, reusing its existing real local
`com.sun.net.httpserver.HttpServer` harness (the same one established in T06 specifically to
avoid the `MockRestServiceServer`-vs-`.requestFactory()`-after-binding conflict).

## A necessary test-enabling production change (disclosed)

`InvoiceExpirySweepJob.processOne` was changed from `private` to package-private, mirroring
`RetryScheduler.processOne`'s own identical visibility — needed so the new test can call it
directly, the same reason `RetrySchedulerTest` does. No behavioral change.

## Tests added

**`InvoiceServiceTest.java`** (new methods):
- `shouldDelegateCandidateLookupToRepositoryUsingInjectedClock` — `findExpiredCandidatesWithoutInFlightPayment()` passes the clock-derived `now` straight through.
- `shouldNotExpireInvoiceWithPaymentInFlight` (R5, named) — structural proof via reflection on the real `@Query` annotation value (not a source-text file read, so it survives reformatting) that the native query's `IN` clause names all four non-terminal payment states (`CREATED`/`WATCHING`/`SEEN`/`CONFIRMING`), not only the narrower `SEEN`/`CONFIRMING` pair an earlier reading used. Still cannot be proven by actually running the query against Postgres this session — Docker unavailable, same disclosed, carried-forward gap as Phase 7/8/9.
- `expireInvoiceShouldThrowNullPointerExceptionForNullInvoice`.
- `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (R5, named — the `InvoiceService`-level half; the full end-to-end orchestration half is in `InvoiceExpirySweepJobTest`) — `expireInvoice` returns `true` when the atomic update affects a row.
- `shouldNotOverwriteNonOpenInvoiceState` (AC6, Phase 9 re-shaped) — `expireInvoice` returns `false` when the atomic update affects 0 rows (the invoice was no longer `OPEN`), proving the real post-Phase-9 design rather than the retired `markExpired()` no-op guard the AC's original wording named.

**`InvoiceExpirySweepJobTest.java`** (new file):
- `shouldExpireInvoiceAndUnregisterWatchAfterTtl` (R5, named) — `processOne` calls `unregisterWatch` then `expireInvoice`.
- `shouldSkipCandidateWithNullWatchId` (AC8) — neither call happens for a null-`watchId` candidate.
- `shouldNotThrowWhenInvoiceWasNoLongerOpenWhenExpiryWasAttempted` — the disclosed Phase 9 residual-race path (`expireInvoice` returning `false`) does not throw; `unregisterWatch` has already run regardless.
- `shouldContinueSweepAfterOneInvoiceFails` (AC4) — one invoice's `unregisterWatch` throwing does not stop the next invoice in the batch from being expired.
- `sweepMethodIsAnnotatedSchedulerLock` (AC3) — reflection-based structural proof.
- `sweepMethodIsNotAnnotatedTransactional` — reflection-based structural proof that `sweep()` itself carries no `@Transactional` (one transaction per invoice, never one for the whole sweep). Originally attempted as a source-text search; switched to reflection after it false-positived on this very class's own Javadoc, which mentions `@Transactional` in prose.

**`CryptoClientTest.java`** (new methods):
- `shouldSendDeleteRequestOnUnregisterWatch`.
- `shouldTreatNotFoundAsSuccessOnUnregisterWatch` (AC7).
- `shouldTreatGoneAsSuccessOnUnregisterWatch` (AC7).
- `shouldThrowCryptoServiceUnavailableOnUnregisterWatchServerError`.
- `shouldThrowCryptoServiceUnavailableOnUnregisterWatchConnectionFailure`.

## Mutation verification performed this phase

Each mutation was introduced via `Edit` (never `git checkout --`, since this file has
uncommitted changes), confirmed red, then reverted via `Edit`:

| Mutation | Test(s) that caught it |
|---|---|
| `expireInvoice` always returns `true` | `shouldNotOverwriteNonOpenInvoiceState` |
| `sweep()`'s per-invoice `try/catch` removed | `shouldContinueSweepAfterOneInvoiceFails` |
| `processOne`'s null-`watchId` guard disabled | `shouldSkipCandidateWithNullWatchId` |
| Native query narrowed back to `SEEN`/`CONFIRMING` only | `shouldNotExpireInvoiceWithPaymentInFlight` |
| `unregisterWatch`'s 404/410-as-success catch removed | `shouldTreatNotFoundAsSuccessOnUnregisterWatch`, `shouldTreatGoneAsSuccessOnUnregisterWatch` |

## Full suite

`mvn -pl services/payment -am test -Dtest='!SchemaBaselineMigrationIntegrationTest'` →
**157/157 passing** (141 carried forward + 16 new). `SchemaBaselineMigrationIntegrationTest`
itself remains the same disclosed, carried-forward, Docker-unavailable gap from Phase 7/8/9 —
not re-attempted this phase, no new changes to it.

## Net result

All Required Tests from the frozen brief (`02`/`04` artifacts) are present, named as specified,
and each load-bearing new assertion has been mutation-verified. Proceeding to Phase 11 — per the
Kimi handoff/wait discipline, this is Kimi 2.7's job; stopping here to hand off.
