# crypto · T17 · Phase 7 — Self-Review

Findings only, ranked most severe first. No fixes applied here (Phase 9).

## 1. `handleSeenIfAgreed` sources amount/fromAddress/toAddress from an arbitrary agreeing provider, not the quorum-verified value

- **Issue:** `representative` is picked via `answers.values().stream().filter(TxResult::exists).findFirst()` — any provider that reported `exists=true`, regardless of whether that same provider's `AMOUNT` answer is the one `AMOUNT`'s own quorum decision (evaluated moments earlier, in the same method) actually agreed on. If two providers report the same amount and a third disagrees, `AMOUNT`'s quorum decision correctly settles on the majority value — but `ConcurrentHashMap`'s unspecified iteration order means `findFirst()` could just as easily pick the *disagreeing* provider's `TxResult`, persisting the wrong amount into `ChainCursor` and, later, into the `chain.tx.finalized` payload.
- **Severity:** High
- **Evidence:** `watch/Watcher.java:295-301` (`handleSeenIfAgreed`'s `representative` selection and the `recordSeenTransaction` call built from it).
- **Recommendation:** Compute `amount` via the same `majorityValue` helper already used elsewhere (`answers.values().stream().filter(TxResult::exists).map(TxResult::amount).toList()`), not an arbitrary pick. `fromAddress`/`toAddress` have no independent quorum fact backing them (no such `FactType` exists) so a "majority" there is still best-effort, but should at least be computed the same deterministic way for consistency.

## 2. A chain with no configured `FinalityPolicy` fails silently, forever, disguised as provider unhealthiness

- **Issue:** `finalityPolicy` is resolved once in the constructor via a `Map.get(...)` that returns `null` on a miss, with no validation. If a configured chain has no matching `FinalityPolicy` bean, every `pollFinalityFor` tick's `finalityPolicy.isFinal(status)` call throws a `NullPointerException` — which is a `RuntimeException`, so it is caught by the same per-provider `catch (RuntimeException e)` meant for transport failures, permanently recording every provider as unhealthy/lagging instead of surfacing the real problem (a missing bean/misconfiguration). This never self-corrects and never distinctly alerts.
- **Severity:** High
- **Evidence:** `watch/Watcher.java:114-117` (constructor resolution, no null-check); `watch/Watcher.java:409-421` (`pollFinalityFor`'s per-provider try/catch, which would swallow the resulting NPE indistinguishably from a real transport failure); `watch/Watcher.java:416` (the call site).
- **Recommendation:** Validate `finalityPolicy != null` in the constructor and throw `IllegalStateException` immediately, mirroring `ProviderSet`'s own eager, fail-fast validation of provider count (T16, Phase 8 Finding 7) rather than letting a missing policy masquerade as routine provider degradation.

## 3. A missing `ChainCursor` row silently drops the durable snapshot and starves finality polling forever, with no log

- **Issue:** `handleSeenIfAgreed`'s `chainCursorRepository.findByWatchId(...).ifPresent(...)` is a no-op if the cursor row is absent — but `pendingFinality.add(txHash)` and `txLifecyclePublisher.seen(...)` run unconditionally, outside that `ifPresent` block. If the cursor is ever missing (a data-consistency assumption, not something this task itself enforces), `chain.tx.seen` is emitted with no snapshot ever recorded, finality polling starts, and `pollFinalityFor`'s own identical `ifPresent`-gated write means `chain.tx.finalized` can then never be emitted for that transaction either — a silent, permanent violation of R10, with no warning logged anywhere.
- **Severity:** Medium
- **Evidence:** `watch/Watcher.java:296-305` (`handleSeenIfAgreed`); the analogous block in `pollFinalityFor`'s `AGREED true` handling.
- **Recommendation:** Log at `warn` (at minimum) when `findByWatchId` returns empty at either call site, so a data-consistency violation is at least observable rather than silently absorbed.

## 4. Ordering race: `pendingFinality.add` happens before `chain.tx.seen` is actually published

- **Issue:** `handleSeenIfAgreed` adds `txHash` to `pendingFinality` *before* calling `txLifecyclePublisher.seen(...)`. `pollFinality()` runs on a separate thread (the shared `sweepScheduler`) and could observe the newly-added `txHash` and — in principle, given a fast enough finality-poll cycle — reach `AGREED true` and call `txLifecyclePublisher.finalized(...)` before the `seen` outbox row has even been inserted. Since `OutboxRelay` delivers in `created_at` order, an out-of-order *insert* means an out-of-order *delivery* to Kafka: `chain.tx.finalized` could reach consumers before `chain.tx.seen` for the same transaction.
- **Severity:** Medium
- **Evidence:** `watch/Watcher.java:304-305`.
- **Recommendation:** Call `txLifecyclePublisher.seen(watch, txHash)` before `pendingFinality.add(txHash)`, so the `seen` row is guaranteed to exist (and thus sort earlier by `created_at`) before finality polling can ever begin for that transaction.

## 5. Finality polling shares a single-thread scheduler with the correlation sweep, and polls providers sequentially

- **Issue:** `pollFinality`/`pollFinalityFor` run on the same single-thread `sweepScheduler` `sweepStaleCorrelations` already uses (`scheduleWithFixedDelay` on the same executor), and `pollFinalityFor`'s per-provider `getFinalityStatus` calls run in a plain sequential `for` loop, not concurrently. A slow or hanging provider call therefore delays both the *next* finality-poll tick and the correlation-staleness sweep for every other in-flight transaction on this watch. This is the first time `Watcher` itself makes synchronous outbound calls from within its own scheduled task (all prior outbound work happens inside each adapter's own independently-scheduled subscription/poll loop) — a new blocking-on-the-shared-thread pattern the frozen brief's "reuses `Watcher`'s existing scheduler" language didn't address, and inconsistent with this codebase's stated per-call virtual-thread philosophy (T01's own rationale; T16 Phase 2's original, later-abandoned fan-out design explicitly called for concurrent per-provider calls on virtual threads).
- **Severity:** Medium
- **Evidence:** `watch/Watcher.java:141-145` (both tasks scheduled on the same `sweepScheduler`); `watch/Watcher.java:409-421` (sequential per-provider loop).
- **Recommendation:** Either dispatch each provider's `getFinalityStatus` call onto its own virtual thread within `pollFinalityFor` (joining before evaluating quorum), or accept and explicitly document the sequential/shared-scheduler tradeoff as intentional for this task's proportionate scope — currently neither is done.

## 6. `toRawJson(FinalityStatus)` omits `txHash`, unlike every other adapter's verbatim-capture convention

- **Issue:** `EthereumAdapter`/`TronAdapter`'s own `toRawJson` helpers embed `txHash` (and other identifying fields) directly in the serialized JSON, even though it is also a separate column on the `Observation` row — a self-describing verbatim capture. `Watcher.toRawJson(FinalityStatus)` serializes only `txBlockNumber`/`currentBlockNumber`/`finalizedBlockNumber`, omitting `txHash` (and provider identity), so the JSON blob alone is not self-describing if ever extracted from its row/S3-key context.
- **Severity:** Low
- **Evidence:** `watch/Watcher.java:469-478`.
- **Recommendation:** Include `txHash` in the serialized fields, consistent with the established adapter convention.

## 7. `FinalizedPayload.amount` can be `null` despite the schema requiring it

- **Issue:** `TxLifecyclePublisher.finalized` passes `cursor.amount() == null ? null : cursor.amount().toPlainString()`. `ChainCursor.amount` is nullable at the schema level and is only ever populated from `TxResult.amount()`, which `TxResult`'s own Javadoc trusts (but does not enforce) to be non-null whenever `exists=true`. The `tx-finalized` event schema (design.md §4c) lists `amount` in its `required` array — a `null` amount would violate the schema this task is deliberately not yet validating against (Phase 4 Finding #13's deferred safety net).
- **Severity:** Low
- **Evidence:** `watch/TxLifecyclePublisher.java` (`finalized` method's amount mapping).
- **Recommendation:** Either trust the upstream `TxResult` contract as-is (already the established trust boundary at every other `Watcher` call site) and accept this as a pre-existing, disclosed risk, or add a defensive check specifically because `amount` — unlike `fromAddress`/`toAddress` — is schema-required.

## 8. Duplicate-publish catch is broader than the frozen brief's stricter framing (carried forward from Phase 6, re-flagged for visibility)

- **Issue:** Already disclosed in the Phase 6 implementation notes as a deliberate, justified deviation — `TxLifecyclePublisher.publish` catches any `DataIntegrityViolationException` rather than re-querying to confirm the row now exists (the `TokenAllowlistSeeder` precedent), because the narrower approach would have required modifying the frozen `OutboxPublisher` or reaching a package-private repository. Re-flagged here per this phase's own remit, not because new information changes the Phase 6 reasoning.
- **Severity:** Low
- **Evidence:** `watch/TxLifecyclePublisher.java` (`publish` method's catch block); `artifacts/06-implementation-notes.md` deviation #2.
- **Recommendation:** No change recommended unless Phase 8/9 disagrees with the Phase 6 justification.
