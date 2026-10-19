# payment · T26 · Phase 7 — Self-Review

Consumes: `artifacts/06-implementation-notes.md`. The diff is commit `61319bc8`:
- `PaymentEndToEndIntegrationTest.java` (new)
- `pom.xml` (+2 test dependencies)

This artifact records findings only. Fixes, if any, are applied in Phase 9.

## Dimensions with no findings

| Dimension | Assessment |
|---|---|
| Correctness | All 3 tests pass, and each targeted mutation makes them fail (Phase 6). The assertions map one-to-one to the frozen AC1–AC3. |
| Module boundaries (L12) | No entity is imported. The test uses `LedgerVerifier` (a `@Component`) and `CanonicalJson` (a `common` utility) only. All other state is read with admin JDBC. |
| Money (L6) | Every event amount is a quoted JSON string. No numeric type is used for money. |
| Secrets (L13) | Only dummy credentials are used: LocalStack's built-in pair, a `payment_app` password local to the test, and placeholder token strings. |
| Transactions / idempotency | These are observed, not altered. The fixtures use real `chain:txhash:eventtype` keys (L5). |
| Null-safety | `invoiceWatchId` handles a null `watch_id`. `receiptFor` fails with a clear message if the row is missing. |
| Production impact | None. `src/main` is untouched. |

## Findings

### Finding 1: the service-to-service bearer token is not asserted on Crypto Service calls

- **Issue.** The test stubs the token endpoint, but never checks that the `/watches` and
  `/attest` calls really carry `Authorization: Bearer e2e-crypto-token`. A regression that
  dropped `CryptoClient`'s auth interceptor would still pass, because WireMock accepts
  unauthenticated requests.
- **Severity.** Low. `CryptoClientTest` unit-covers the interceptor, but T26 is where the real
  wiring should be proven.
- **Evidence.** `PaymentEndToEndIntegrationTest`: the `WIREMOCK.verify(...)` calls match on URL
  only.
- **Recommendation.** Add `.withHeader("Authorization", equalTo("Bearer e2e-crypto-token"))` to
  the `/watches` and `/attest` verifications in AC1.

### Finding 2: `hasSize(1)` on outbound Kafka records is stricter than L8's at-least-once guarantee

- **Issue.** `awaitOutboundRecord` requires exactly one record per (topic, key). `OutboxRelay`
  sends synchronously (`.get(SEND_TIMEOUT_SECONDS)`) and runs on a fixed delay, so a duplicate
  only arises on an error path: the send times out after the broker has actually accepted the
  record, or the mark-published save fails. Under a heavily loaded CI host that could make the
  test flaky. The duplicate itself would be legitimate L8 behaviour.
- **Severity.** Low. It didn't happen in any of the runs, and the happy path is deterministic.
- **Evidence.** `events/OutboxRelay.java:76-90`; the helper `awaitOutboundRecord`.
- **Recommendation.** Phase 9 decides between two options:
  - **Keep `hasSize(1)`.** It also guards "exactly one receipt", which R17 requires, but on the
    Kafka side.
  - **Relax to `isNotEmpty()`.** R17's "exactly one receipt" is still pinned by the `receipts`
    row, the single `/attest` call and the single ledger entry.

### Finding 3: shutdown log noise from `OutboxRelay`

- **Issue.** After the class finishes, the cached context's `@Scheduled` relay keeps polling a
  Postgres container that Testcontainers has already stopped. The log shows
  `JDBCConnectionException` until the JVM exits.
- **Severity.** Info. No assertion or result is affected. It was already disclosed in Phase 6.
- **Evidence.** The Phase 6 run log.
- **Recommendation.** No change. A fix such as `@DirtiesContext` would only add cost and no
  coverage.

### Finding 4: the genesis-`prev_hash` assertion depends on cleanup between tests

- **Issue.** AC1 expects the only ledger entry to have `prev_hash` = 64 zeros. That holds only
  because `@AfterEach` deletes `ledger_entries` and the containers are per-class.
- **Severity.** Info. The assumption is documented in the plan, and it is correct today.
- **Evidence.** `cleanUp()`, `ledgerPrevHashes()`.
- **Recommendation.** None needed. The `LedgerVerifier.verify()` check is the order-independent
  chain-integrity guard either way.

## Summary

There is no correctness, security or boundary defect. Finding 1 is the only one worth a code
touch: a cheap header assertion that proves L10's outbound half end-to-end. Finding 2 is a
judgement call for Phase 9. Findings 3 and 4 are informational.
