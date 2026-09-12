# crypto · T17 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T17 — Seen/confirmed/finalized emission |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the Phase 6 implementation and Phase 7 self-review. Findings only; no rewrites.

---

## 1. Per-watch `ChainCursor` snapshot is write-once and can mis-attribute finality to the wrong transaction

- **Issue:** `ChainCursor.recordSeenTransaction(...)` is write-once per watch. A second, distinct `txHash` observed by the same watch cannot overwrite the snapshot. Worse, `pollFinalityFor(txHash)` is invoked for the *new* `txHash` (it is still added to `pendingFinality`), but `TxLifecyclePublisher.finalized(...)` derives the emitted `txHash` from `cursor.txHash()`. The result is a `chain.tx.finalized` event whose idempotency key and payload refer to the first transaction while the finality poll was driven by the second.
- **Evidence:** `watch/ChainCursor.java:110-120` (write-once guard); `watch/Watcher.java:295-305` (adds every seen `txHash` to `pendingFinality` regardless of whether the cursor snapshot was updated); `watch/Watcher.java:462-465` (publishes using `cursor.txHash()`); `watch/TxLifecyclePublisher.java:82-91` (`finalized` does not receive the polled `txHash` as an argument).
- **Recommendation:** Either (a) make the snapshot per-`txHash` (new table or a JSON/map column on `chain_cursors` keyed by `txHash`) and pass the active `txHash` into `TxLifecyclePublisher.finalized`, or (b) explicitly scope T17 to watches that observe exactly one transaction and enforce that assumption. The current implementation silently violates R10 for multi-tx watches.
- **Confidence:** High

## 2. Finality-poll work list is in-memory only; seen-but-not-finalized transactions are stranded after restart

- **Issue:** `pendingFinality` is a per-`Watcher` `ConcurrentHashMap.newKeySet()`. If the process restarts after `chain.tx.seen` was emitted but before `FINALITY` agrees, the set is empty and finality polling never resumes for that transaction. Even if adapters re-deliver the same transaction, `EXISTENCE` re-evaluation throws `IllegalStateException` (duplicate decision, T16's one-shot guard), so `handleSeenIfAgreed` never re-adds the `txHash` to `pendingFinality`.
- **Evidence:** `watch/Watcher.java:89` (`pendingFinality` field); `watch/Watcher.java:304` (population only inside `handleSeenIfAgreed`); `watch/Watcher.java:269-279` (duplicate decision caught and returned as `null`, so `handleSeenIfAgreed` is a no-op). The durable cursor has no "needs finality polling" flag.
- **Recommendation:** Persist pending-finality state durably (e.g., a nullable `finalized_at` / `last_finality_poll_at` on `chain_cursors` plus startup reconciliation), or accept and document that the service must not restart between `SEEN` and `FINALIZED`. The latter is not realistic for a production watcher.
- **Confidence:** High

## 3. `recordDisagreementsIfAny` violates its own "at most once per provider per transaction" guarantee for `FINALITY`

- **Issue:** `recordDisagreementsIfAny` relies on `correlations.get(txHash)` to one-shot flag a disagreeing provider. For `FINALITY`, the `TxCorrelation` was pruned immediately after the original `EXISTENCE/AMOUNT/TOKEN/CONFIRMATIONS` evaluation, so `correlation` is always `null` here. The one-shot guard short-circuits to `true`, meaning a provider that disagrees on finality is re-flagged on every poll tick until `FINALITY` reaches a persisted outcome. Because a `HELD` outcome is persisted, the next tick throws a duplicate-decision exception and stops polling, but the method's contract and the provider-health counter are both wrong for the `FINALITY` fact type.
- **Evidence:** `watch/Watcher.java:330-347` (`recordDisagreementsIfAny` reads `correlations.get(txHash)`); `watch/Watcher.java:229-233` (correlation pruned after the original observation); `watch/Watcher.java:439-440` (called for `FINALITY`).
- **Recommendation:** Track finality-specific disagreement flagging state inside `pendingFinality` (e.g., a small per-`txHash` set of already-flagged providers) or simply skip disagreement tracking for `FINALITY` if the brief did not intend it.
- **Confidence:** High

## 4. Missing `FinalityPolicy` for a configured chain causes a permanent `NullPointerException` disguised as provider lagging

- **Issue:** `Watcher` resolves `finalityPolicy` from a `Map` built from the injected list; a miss returns `null`. Every `pollFinalityFor` tick then throws `NullPointerException` inside the per-provider try/catch, which is caught as a "RuntimeException" and recorded as `LAGGING` for every provider. The real problem (misconfiguration / missing bean) is never surfaced.
- **Evidence:** `watch/Watcher.java:114-117` (constructor resolution with no null check); `watch/Watcher.java:409-421` (catch swallows the NPE as lagging); `watch/Watcher.java:416` (the call site that throws). This matches Phase 7 Self-Review Finding 2.
- **Recommendation:** Fail fast in the constructor with `IllegalStateException` if no policy exists for the watch's chain. This is consistent with `ProviderSet`'s own eager validation.
- **Confidence:** High

## 5. `handleSeenIfAgreed` sources `amount`/`fromAddress`/`toAddress` from an arbitrary agreeing provider, not the quorum-majority value

- **Issue:** After confirming `EXISTENCE` is `AGREED true`, the code picks the first `TxResult` with `exists=true` (iteration order of `ConcurrentHashMap.values()` is unspecified) to populate the durable `ChainCursor` snapshot. If providers disagree on `amount`, the snapshot may store the minority value even though `AMOUNT`'s own quorum decision settled on the majority value moments earlier. `fromAddress`/`toAddress` have no independent quorum fact, but they should still be computed deterministically from the same majority helper.
- **Evidence:** `watch/Watcher.java:295-301` (`findFirst()` on `answers.values()`). Matches Phase 7 Self-Review Finding 1.
- **Recommendation:** Compute `amount` with the same `majorityValue` helper already used for `confirmations`. Compute `fromAddress`/`toAddress` deterministically (e.g., majority, or the provider that supplied the majority amount).
- **Confidence:** High

## 6. `SeenPayload` and `FinalizedPayload` omit `confirmations`, contradicting the frozen brief field map and design.md schema

- **Issue:** The frozen brief's field-sourcing map lists `confirmations` as optional on `chain.tx.seen` and `chain.tx.finalized`, and `design.md` §4c says "`confirmations` on `seen`/`confirmed`" and includes `confirmations` in the `tx-finalized` schema. The implementation's `SeenPayload` and `FinalizedPayload` records do not contain a `confirmations` field; only `ConfirmedPayload` does.
- **Evidence:** `watch/TxLifecyclePublisher.java:110-122` (all three payload records); frozen brief field-sourcing map; `spec/crypto-service/design.md` §4c.
- **Recommendation:** Add `confirmations` to `SeenPayload` (sourced from the majority count at the `handleSeenIfAgreed` call site) and to `FinalizedPayload` (sourced from the `FinalityStatus` majority count at finality time), or explicitly amend the frozen brief and the deferred schema to remove the field from those events.
- **Confidence:** Medium

## 7. A missing `ChainCursor` row silently emits `chain.tx.seen` and strands finality forever

- **Issue:** `handleSeenIfAgreed` emits `chain.tx.seen` and adds the `txHash` to `pendingFinality` even when `chainCursorRepository.findByWatchId(...)` returns empty. Because `pollFinalityFor`'s own `ifPresent` gate is identical, `chain.tx.finalized` can never be published for that transaction, and no warning is logged.
- **Evidence:** `watch/Watcher.java:299-305` (`ifPresent` block does not gate the subsequent `pendingFinality.add` or `seen(...)` call); `watch/Watcher.java:462-465` (finalization is also `ifPresent`-gated). Matches Phase 7 Self-Review Finding 3.
- **Recommendation:** Log at `warn` when the cursor row is missing at either site, and consider not emitting `chain.tx.seen` until the snapshot is durably recorded.
- **Confidence:** Medium

## 8. `pendingFinality.add` happens before the `chain.tx.seen` outbox row is inserted, allowing out-of-order delivery

- **Issue:** `handleSeenIfAgreed` adds the `txHash` to `pendingFinality` before calling `txLifecyclePublisher.seen(...)`. The shared scheduler could then run `pollFinality()` and insert a `chain.tx.finalized` row before the `seen` row exists. Outbox relay delivers in `created_at` order, so consumers could see `finalized` before `seen`.
- **Evidence:** `watch/Watcher.java:304-305` (order of operations). Matches Phase 7 Self-Review Finding 4.
- **Recommendation:** Publish `chain.tx.seen` before adding the `txHash` to `pendingFinality`.
- **Confidence:** Medium

## 9. Finality polling runs sequentially on the same single-thread scheduler as correlation sweeping

- **Issue:** `pollFinality` and `sweepStaleCorrelations` share `sweepScheduler` (one thread). Each provider's `getFinalityStatus` is called sequentially in a `for` loop. A slow or hanging provider blocks both the next finality tick and the correlation sweep for every other in-flight transaction on this watch.
- **Evidence:** `watch/Watcher.java:141-145` (both tasks on the same scheduler); `watch/Watcher.java:409-421` (sequential loop). Matches Phase 7 Self-Review Finding 5.
- **Recommendation:** Either dispatch each `getFinalityStatus` call on its own virtual thread and join before quorum evaluation, or document the sequential/shared-scheduler trade-off as an explicit, accepted design choice.
- **Confidence:** Medium

## 10. `TxLifecyclePublisher` swallows every `DataIntegrityViolationException`, broader than the frozen brief's constraint

- **Issue:** The frozen brief's Constraints section says the duplicate-key catch "must not mask any other `DataIntegrityViolationException` cause beyond the idempotency-key conflict." The implementation catches all `DataIntegrityViolationException` without re-querying. The Phase 6 notes justify this (outbox's only unique constraint is `idempotency_key`), but it is still a deviation from the brief's stated acceptance criterion.
- **Evidence:** `watch/TxLifecyclePublisher.java:97-108`; frozen brief Constraints; `artifacts/06-implementation-notes.md` deviation #2. Matches Phase 7 Self-Review Finding 8.
- **Recommendation:** Add the explicitly required test that the catch only swallows the idempotency-key violation and propagates other causes, or amend the frozen brief to remove the stricter constraint.
- **Confidence:** Medium

## 11. Required T17 unit tests are missing

- **Issue:** The implementation notes claim `TxLifecyclePublisherTest` was created, but no such file exists. `ChainCursorTest` does not exercise `recordSeenTransaction` or `advanceFinalizedTo`. `WatcherTest` does not cover `handleSeenIfAgreed`, `handleConfirmedIfAgreed`, or `pollFinality` behavior, despite these being the core additions of T17. The named tests from `package.md` §8 (R8–R10, R12) are not yet implemented.
- **Evidence:** `services/crypto/src/test/java/com/themistra/crypto/watch/` listing (no `TxLifecyclePublisherTest.java`); `ChainCursorTest.java` (62 lines, only `advanceTo` and `placeholder` tests); `WatcherTest.java` (no T17-specific scenarios beyond the pre-existing T16 regression set).
- **Recommendation:** Create `TxLifecyclePublisherTest` mirroring `ProviderDegradedPublisherTest`; extend `ChainCursorTest` for the two new mutators; extend `WatcherTest` for seen/confirmed/finalized emission, duplicate suppression, skip-with-<3-answers, and observation-before-decide ordering for `FINALITY`.
- **Confidence:** High

## 12. `FinalizedPayload.amount` may be `null` although the schema marks `amount` required

- **Issue:** `TxLifecyclePublisher.finalized(...)` passes `cursor.amount() == null ? null : cursor.amount().toPlainString()`. `ChainCursor.amount` is nullable at the schema level and is populated from `TxResult.amount()`, which is not enforced non-null. The deferred `tx-finalized` schema lists `amount` in `required`. Matches Phase 7 Self-Review Finding 7.
- **Evidence:** `watch/TxLifecyclePublisher.java:88-89`; `spec/crypto-service/design.md` §4c `tx-finalized.v1.schema.json`.
- **Recommendation:** Either trust the upstream `TxResult` contract (and document that trust) or add a defensive null-check / non-null assertion before publishing.
- **Confidence:** Low

## 13. `toRawJson(FinalityStatus)` omits the transaction hash from the verbatim observation

- **Issue:** `EthereumAdapter`/`TronAdapter` embed `txHash` in their raw JSON captures so the blob is self-describing. `Watcher.toRawJson(FinalityStatus)` serializes only block numbers, so the observation row must be interpreted alongside its `tx_hash` column.
- **Evidence:** `watch/Watcher.java:469-478`. Matches Phase 7 Self-Review Finding 6.
- **Recommendation:** Include `txHash` in the serialized fields for consistency with the adapter convention.
- **Confidence:** Low

## 14. `ChainCursor.advanceFinalizedTo` is forward-only but not write-once

- **Issue:** The frozen brief describes `lastFinalizedBlock` as "set only once `FINALITY` quorum-agrees `true` for this watch's seen transaction." The implementation allows repeated calls with increasing values. Not a correctness problem today, but it diverges from the brief's write-once intent and could hide a finality re-evaluation bug in future tasks.
- **Evidence:** `watch/ChainCursor.java:124-130`; frozen brief lines 62-66 / AC8.
- **Recommendation:** Either document that forward-only advancement is the deliberate semantics, or add a `lastFinalizedBlock != null` guard to enforce write-once.
- **Confidence:** Low

---

(End of independent review. Findings are for human fold-in during Phase 9.)
