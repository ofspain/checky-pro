# payment · T08 · Phase 11 — Test Review

Artifact: `.ai/prompts/payment/T08/artifacts/11-test-review.md`  
Consumed: `artifacts/10-test-generation.md`, `InvoiceServiceTest.java`, `InvoiceExpirySweepJobTest.java`, `CryptoClientTest.java`, `SchemaBaselineMigrationIntegrationTest.java` results.

---

## Executive summary

- `mvn -pl services/payment test`: **177 tests run, 0 failures**.
- Phase 10 closed the Phase 8 correctness issue by replacing the detached-entity merge with an atomic conditional update, and added strong mutation-verified tests for the job orchestration and `unregisterWatch` behavior.
- Remaining gaps are mostly around real-DB coverage for the native query and a few annotation/URI assertions that are easy to add.

---

## Gap 1 — Native candidate query is never executed against a real database

- **Why it matters:** `shouldNotExpireInvoiceWithPaymentInFlight` is a structural reflection test that only checks the `@Query` string contains the four state literals. It does not prove the SQL parses, that Hibernate maps `SELECT i.*` to `Invoice`, that the `expires_at <= :now` filter works, or that `state = 'OPEN'` is present. The module already has working Testcontainers infrastructure (`SchemaBaselineMigrationIntegrationTest` passes with V4/V5).
- **Suggested test:** Add a Testcontainers-backed integration test (or extend `SchemaBaselineMigrationIntegrationTest`) that seeds `payments.invoices` and `payments.payments` rows and asserts `findExpiredCandidatesWithoutInFlightPayment` returns only the eligible invoice(s).

---

## Gap 2 — Structural proof for the candidate query is too narrow

- **Why it matters:** The current reflection test only asserts the presence of `'CREATED'`, `'WATCHING'`, `'SEEN'`, and `'CONFIRMING'`. It would still pass if the query dropped `state = 'OPEN'`, the `expires_at <= :now` predicate, or the `NOT EXISTS` anti-join shape.
- **Suggested test:** Extend the structural assertion to verify the query also contains `state = 'OPEN'`, `expires_at <= :now`, and `NOT EXISTS`. Better, replace the structural test with the integration test from Gap 1.

---

## Gap 3 — No test that `sweep()` is `@Scheduled`

- **Why it matters:** `sweepMethodIsAnnotatedSchedulerLock` proves the ShedLock guard, but nothing proves the method is actually scheduled via Spring's `@Scheduled`. A missing `@Scheduled` annotation would leave the job dormant despite the cron property being bound.
- **Suggested test:** Add a reflection-based test asserting `sweep()` has `@Scheduled` and that its cron attribute resolves from `${themistra.payment.invoice.expiry-sweep.cron}`.

---

## Gap 4 — No test that `expireInvoice` is `@Transactional`

- **Why it matters:** The atomic update must run inside a transaction. The class has a source-text guard for `create`'s `@Transactional` but no equivalent for `expireInvoice`.
- **Suggested test:** Add `expireInvoiceMethodIsAnnotatedTransactional` mirroring `createMethodIsAnnotatedTransactional` (either source-text or reflection on the method).

---

## Gap 5 — `unregisterWatch` DELETE test does not assert the URI path

- **Why it matters:** `shouldSendDeleteRequestOnUnregisterWatch` only verifies the HTTP method is `DELETE`. It would pass even if the URI were wrong (e.g., missing the `watchId` path segment).
- **Suggested test:** Capture the request path in the test and assert it equals `/internal/v1/watches/{watchId}`.

---

## Gap 6 — No test that `unregisterWatch` sends the bearer token

- **Why it matters:** The `Authorization` interceptor is shared, but each new operation should verify it is actually applied. The `registerWatch` tests cover token injection, but the DELETE path is not.
- **Suggested test:** Add a `shouldSendBearerTokenOnUnregisterWatch` test that captures and asserts the `Authorization: Bearer ...` header.

---

## Gap 7 — No test verifying the `updatedAt` field is part of the conditional update

- **Why it matters:** The JPQL bulk update sets `i.updatedAt = :now`. A regression that dropped this field would leave invoices with a stale `updated_at` after expiration.
- **Suggested test:** Either add a structural reflection assertion on the `@Query` value of `updateStateIfCurrentState`, or assert via the integration test from Gap 1 that the expired invoice's `updated_at` is advanced.

---

## Gap 8 — Residual race between `unregisterWatch` and the atomic update is only documented, not tested

- **Why it matters:** `processOne` unregisters the watch unconditionally and then performs the conditional DB update. If a payment becomes in-flight in between, the watch is removed but the invoice remains `OPEN`. The code logs this, but there is no test guarding the behavior.
- **Suggested test:** Add a test that stubs `expireInvoice` to return `false` and asserts the method completes without throwing and without calling `expireInvoice` again (already partially covered by `shouldNotThrowWhenInvoiceWasNoLongerOpenWhenExpiryWasAttempted` — extend it to verify the warning log message).

---

## Positive confirmations

- `shouldContinueSweepAfterOneInvoiceFails` is a strong AC4 test: one failure does not abort the batch.
- `shouldSkipCandidateWithNullWatchId` covers the defensive null-watchId path.
- `shouldNotOverwriteNonOpenInvoiceState` correctly guards the atomic-update semantics.
- `CryptoClientTest` now covers `204`, `404`, `410`, `500`, and connection failure for `unregisterWatch`.
- `SchemaBaselineMigrationIntegrationTest` was updated for V4/V5 and passes, closing the Phase 8 CI blocker.
- Mutation verification table in Phase 10 confirms the tests fail on real regressions.
