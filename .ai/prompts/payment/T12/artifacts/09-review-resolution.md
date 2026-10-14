# payment · T12 · Phase 9 — Review Resolution

**STATUS: human sign-off obtained** (see Finding 1 below — the one comment requiring a genuine
decision was put to the user directly, who approved the public-API-changing fix).

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1 / Kimi Finding 1 | Stale `event.watchId()` lookup in `registerReplacementWatch` can silently skip registration after a cross-partition-delayed reorg event, once the invoice has moved on to a newer watchId via an earlier reorg cycle | **ACCEPTED** (human sign-off — required a public-API change to `PaymentService.applyReorged`, which Phase 9's own guardrail defaults against; put to the user directly, who chose to accept the fix over logging it as a deferred follow-up) | Medium severity, High confidence from both reviews, root cause is fundamental (a frozen event field used to look up a value that legitimately changes over the invoice's lifetime) — the only real fix requires carrying `invoiceId` through `applyReorged`'s own return value |
| 2 | Self-review Finding 2 / Kimi Finding 2 | `registerReplacementWatch`'s catch only covers `CryptoServiceUnavailableException`; any other exception (e.g. from `invoiceService`) propagates and triggers a guaranteed-no-op Kafka redelivery | **ACCEPTED** | Internal-only fix (no public API change), both reviews agree, Medium severity |
| 3 | Self-review Finding 3 / Kimi Finding 3 | A successful `registerWatch` whose `updateWatchIdIfOpen` returns `false` leaks the new watch — nothing ever unregisters it | **ACCEPTED** | Internal-only fix, both reviews agree, Medium severity resource leak |
| 4 | Self-review Finding 1 (renumbered 4 in Kimi) | The `chain` half of `applyReorged`'s mismatch guard is dead code — `findByChainAndTxHash` already guarantees it | **ACCEPTED** | Internal-only fix, both reviews agree, Low severity but a confirmed, concrete defect (misleading dead code) |
| 5 | Self-review Finding 4 (renumbered 5 in Kimi) | The crash-window gap (process dies after commit, before registration) is real but wasn't named as its own distinct failure mode in the Javadoc | **ACCEPTED** | Documentation-only, no code behavior change |
| 6 | Self-review Finding 6 (renumbered 6 in Kimi) | Kafka offset-ack latency for this listener is now coupled to the `registerWatch` HTTP round-trip | **ACCEPTED (documentation only)** | Both reviews recommend "document as an accepted trade-off," not a code change; no load-testing evidence yet that this needs fixing |
| 7 | Kimi Finding 7 (new) | Check `invoice.state() == OPEN` before calling `registerWatch` at all, not only after | **ACCEPTED** | Internal-only fix, strictly improves on Finding 3's own fallback (avoids wasting the HTTP call and the immediate unregister-after-register round-trip in the common case) |
| 8 | Kimi Finding 8 (new) | Reversed payment rows retain the reorged-out `confirmations`/possibly-stale `amount` values — an explicit decision, not a silent default, was requested | **ACCEPTED (documentation only, option (a) — leave values as-is)** | Kimi's own confidence on this finding was Low and it explicitly asked only for a documented decision, not a specific fix; option (a) (treat the fields as historical observations, not current-validity claims) requires no new behavior and is the lower-risk choice — `amount` in particular was never non-zero pre-finality in the first place, so there is nothing to "reset" it from |
| 9 | Self-review Finding 5 (renumbered 9 in Kimi) | A second, genuinely distinct reorg of the identical `(chain, txHash)` would collide on the contract's own fixed idempotency key and be dropped | **ACCEPTED (documentation only, tracked as deferred)** | Both reviews agree this is a contract-level issue outside T12's own scope — fixing it would mean changing the shared event schema's idempotency-key convention |

## Exact changes made

- **`PaymentService.java`** — `applyReorged` now returns `Optional<ReorgOutcome>` (new record,
  `payment` package: `paymentId`, `invoiceId`, `newState`) instead of `Optional<PaymentState>`
  (Finding 1). The mismatch guard dropped its dead `chain` clause, keeping only
  `tokenContractAddress` (Finding 4), with an updated comment explaining why.
- **`ReorgOutcome.java`** (new file) — the result record, with Javadoc explaining the staleness
  race it exists to avoid.
- **`InvoiceService.java`** — new `findByIdAsWatchView(Long invoiceId)` passthrough (Finding 1),
  mirroring `findByWatchId`'s own L12-safe shape.
- **`ChainEventConsumer.java`**:
  - `onChainTxReorged` uses `Optional<ReorgOutcome>` and passes `outcome.get().invoiceId()` to
    `registerReplacementWatch` (Finding 1); class-level Javadoc gained a Finding 5 (crash-window)
    and Finding 6 (Kafka-ack-latency trade-off) paragraph.
  - `registerReplacementWatch(Long invoiceId)` (signature changed from `ChainTxReorged event`):
    looks up by `invoiceId` via `findByIdAsWatchView` (Finding 1); short-circuits with a `WARN`
    if the invoice isn't `OPEN` before calling `registerWatch` (Finding 7); calls
    `cryptoClient.unregisterWatch(response.watchId())` if `updateWatchIdIfOpen` returns `false`
    after a successful register (Finding 3); catches `RuntimeException` broadly instead of only
    `CryptoServiceUnavailableException` (Finding 2).
- **`PaymentRepository.java`** — added an explicit (non-code) comment next to the two reversal
  methods documenting the Finding 8 decision (leave `confirmations`/`amount` as historical
  observations).
- **`ChainTxReorged.java`** — added a Javadoc paragraph documenting the Finding 9 idempotency-key
  limitation as a tracked, deferred, contract-level issue.

No public API changed beyond item 1 (the one human-approved exception); no renames; no
refactoring beyond what each accepted finding required; no change to any file the frozen brief
didn't already authorize.

## Verification

`mvn -o compile`, `mvn -o test-compile`: clean. `mvn -o test -Dtest='!*IntegrationTest,!*IT'`:
**229/229 passing** — same count as Phase 6/7 (resolution fixes existing code paths; still no
new test methods, per this pipeline's own Phase 10 boundary).
