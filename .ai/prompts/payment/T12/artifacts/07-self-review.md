# payment · T12 · Phase 7 — Self Review

Consumes: `artifacts/06-implementation-notes.md`

Reviewed the Phase 6 diff against the frozen brief and `agents.md`. Findings only — no fixes
applied here. One mechanical check was run that isn't a "finding" but is worth recording: the
new Spring Data derived method `existsByInvoiceIdAndStateInAndReorgedAtIsNull` was verified to
parse correctly against `Payment`'s own property graph via a direct `PartTree` construction
(outside the normal test suite, which never boots a full Spring context without Docker/
Testcontainers — both `@SpringBootTest` classes in this module are `*IntegrationTest`-suffixed
and were excluded from the 229-test run). This closes a real verification gap: the method
compiled as Java but had never actually been checked for Spring Data query-derivation validity.

## Findings

### 1. Dead/misleading half of the Finding-6 mismatch check

- **Issue:** `applyReorged`'s guard (`PaymentService.java:244`) checks
  `!payment.getChain().equals(event.chain()) || !payment.getTokenContractAddress().equals(event.tokenContractAddress())`.
  The `chain` half can never be true: `payment` was found via
  `repository.findByChainAndTxHash(event.chain(), event.txHash())`, whose own derived `WHERE`
  clause already requires `chain = event.chain()` for the row to be returned at all — the found
  row's `chain` is guaranteed equal to the event's by construction, not by this check. Only the
  `tokenContractAddress` half is ever reachable.
- **Severity:** Low (dead code, not a correctness bug — the check doesn't fail to catch anything
  it was meant to catch, it just contains one clause that can never fire).
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/payment/PaymentService.java:244`;
  `PaymentRepository.findByChainAndTxHash` (derived query, `chain` + `txHash` both in the
  `WHERE`).
- **Recommendation:** Drop the `chain` half, or keep it with a one-line comment explaining it's
  intentionally redundant (defends against a future change to `findByChainAndTxHash`'s own query
  shape, not against today's inputs) — as written, with no comment, a future reader could
  reasonably believe both halves are independently load-bearing.

### 2. `registerReplacementWatch`'s catch is narrower than the failure modes it needs to cover

- **Issue:** Only `CryptoServiceUnavailableException` is caught (`ChainEventConsumer.java:170`).
  `invoiceService.findByWatchId`/`updateWatchIdIfOpen` can also throw (e.g. a transient
  `DataAccessException`) and would propagate uncaught. Because the DB-side reversal already
  committed by this point (`transactionTemplate.execute` already returned), an uncaught
  exception here causes Kafka to redeliver the message — but the redelivery is a guaranteed
  no-op: `dedupe()` already consumed the idempotencyKey, so the retry skips `applyReorged`
  entirely and never re-attempts registration either. The Phase 4 Finding 3 resolution ("ride to
  the invoice's own natural expiry") is the right outcome either way, but today it's only
  reached cleanly for one specific exception type — any other exception from this step causes a
  pointless, confusing Kafka redelivery cycle before landing in the same place.
- **Severity:** Medium.
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:159-174`.
- **Recommendation:** Widen the catch around the whole best-effort step (e.g. `RuntimeException`,
  or at minimum add `org.springframework.dao.DataAccessException` alongside
  `CryptoServiceUnavailableException`), so every failure mode in this step reaches the same
  logged-and-swallowed outcome deterministically.

### 3. A successful `registerWatch` whose `updateWatchIdIfOpen` returns false leaks the new watch

- **Issue:** If `cryptoClient.registerWatch(...)` succeeds but the invoice is no longer `OPEN` by
  the time `updateWatchIdIfOpen` runs (e.g. it resolved via some other path in the same narrow
  window), the method returns `false` and the call site does nothing further. The watch crypto-
  service just registered is never referenced by `Invoice.watchId` and nothing in this service
  will ever call `unregisterWatch` for it — an orphaned, permanently-live registration on the
  crypto-service side.
- **Severity:** Low/Medium (a resource leak on the crypto-service side, not a payment-state
  correctness bug — the invoice's own resolution via the other path is unaffected).
- **Evidence:** `services/payment/src/main/java/com/themistra/payment/consumer/ChainEventConsumer.java:166-169`;
  `InvoiceService.updateWatchIdIfOpen`'s own `boolean` return convention.
- **Recommendation:** If `updateWatchIdIfOpen` returns `false`, call
  `cryptoClient.unregisterWatch(response.watchId())` to clean up the now-unreferenced watch.

### 4. The crash-window gap is real but undernamed

- **Issue:** If the process dies after the reversal transaction commits but before (or during)
  `registerReplacementWatch`, Kafka redelivery cannot retry the registration step — `dedupe()`
  already consumed the idempotencyKey on the first (successful) attempt, so the retry skips
  straight past `applyReorged`. This is structurally the same outcome as a `registerWatch` HTTP
  failure (Finding 3's own "ride to natural expiry" resolution covers it), but the Phase 6
  Javadoc and implementation notes only name the HTTP-failure case explicitly.
- **Severity:** Low (self-resolving via the existing expiry-sweep safety net already built for
  Finding 3; this is a documentation gap, not a missing code path).
- **Evidence:** `ChainEventConsumer.onChainTxReorged`/`registerReplacementWatch`.
- **Recommendation:** No code change required. Extend the Javadoc/comment next to Finding 3's
  own reference to name this crash-window case explicitly, so a future reader doesn't assume
  "logged and swallowed" is the only way this path degrades gracefully.

### 5. Idempotency-key collision if the identical transaction is reorged twice

- **Issue:** `contracts/events/chain/tx-reorged.v1.schema.json`'s own description fixes the
  idempotency key as `chain:txHash:reorged` — one key per transaction hash, not per occurrence.
  A second, genuinely distinct reorg of the identical `(chain, txHash)` (rare on most chains, but
  not impossible on ones with weak finality) would be silently treated as an already-processed
  duplicate and dropped by `dedupe()`, even though it represents a real, new backward transition.
- **Severity:** Low (narrow edge case; root cause is the shared event contract, not this task's
  own code, and this task must not modify that contract).
- **Evidence:** `contracts/events/chain/tx-reorged.v1.schema.json:5`.
- **Recommendation:** Log as a tracked, deferred observation (same disposition category as the
  already-logged crypto-service watcher gap) rather than a T12 code fix — fixing it would mean
  changing the shared idempotency-key convention, out of this task's scope.

### 6. Kafka offset-ack latency for this listener is now coupled to an HTTP round-trip

- **Issue:** The other three listeners' Kafka offset commit is tied only to their own DB
  transaction (`@Transactional` directly on the listener method). `onChainTxReorged`'s offset
  commit additionally waits for `registerReplacementWatch` to finish (even though its outcome is
  swallowed), since both run inside the same un-annotated method body before the listener
  returns.
- **Severity:** Low (a latency/throughput characteristic, not a correctness issue; mirrors the
  already-accepted "hold a DB connection open across an outbound HTTP call" trade-off T11 made
  for `unregisterWatch`, just on the Kafka-ack side instead of the DB-connection side).
- **Evidence:** `ChainEventConsumer.onChainTxReorged`.
- **Recommendation:** Accept as-is; document as a deliberate trade-off alongside the existing
  Javadoc. Revisit only if load testing against a slow/unavailable crypto-service shows real
  consumer-lag impact.
