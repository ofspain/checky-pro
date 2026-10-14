# payment · T13 · Phase 9 — Review Resolution

**STATUS: resolved directly** — all findings were self-evident completions/corrections within
already-established precedent (no public API change, no reopened architectural question), so no
fresh human gate was required beyond this resolution log itself. No finding this round rose to
the weight of T13's own Phase 4 findings (public-API-changing or newly deciding a design question).

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

| # | Source | Comment | Decision | Reason |
|---|--------|---------|----------|--------|
| 1 | Self-review Finding 1 / Kimi Finding 1 | `onChainTxFinalized`'s body had grown to 4 inline steps | **ACCEPTED** | Readability only, no API change, both reviews agree |
| 2 | Self-review Finding 2 / Kimi Finding 2 | Document the first-level-cache behavior of the double `(chain, txHash)` lookup | **ACCEPTED** | Documentation only, both reviews agree |
| 3 | Kimi Finding 3 (new) | No check that the finalized event's own token matches the stored payment's token | **ACCEPTED, corrected** | Real, Medium-confidence finding — but Kimi's own suggested comparison (`invoice.tokenContractAddress()` vs `payment.getTokenContractAddress()`) checks the wrong pair: a mismatch there would indicate a bug several steps earlier, at the original `chain.tx.seen` match (already guaranteed-matching by construction, same "dead code" category as T12's own chain-equality finding) — not in this event. The correct, precedent-matching comparison is `event.tokenContractAddress()` vs `payment.getTokenContractAddress()`, mirroring `applyReorged`'s own identical guard (T12 Phase 3 Finding 6) exactly. |
| 4 | Kimi Finding 4 | `markOutcomeIfOpenOrExpired`'s widening correctly closes the post-expiry gap | **Positive confirmation** | No action — not a finding |
| 5 | Kimi Finding 5 | `amountDiscrepancy` remains a bare `String` | **REJECTED (no new action)** | Already dispositioned at Phase 4 (Finding 3) — rejected as a new enum type, named constants adopted instead. Kimi's own re-raise doesn't add new information. |
| 6 | Kimi Finding 6 | `PaymentFinalizedPayload` still doesn't carry `amountDiscrepancy` | **REJECTED (no new action)** | Already dispositioned at Phase 4 (Finding 6) — forward note for task 24, not a T13 change. |
| 7 | Kimi Finding 7 | Unrelated T08 `InvoiceRepositoryIntegrationTest` still broken | **Out of scope** | Pre-existing, already-tracked defect (confirmed first during T09's own Phase 8) — not caused by, and not this task's job to fix. |

## Exact changes made

- **`PaymentService.java`** — `applyFinalized` gains a token-contract-address mismatch guard
  (`event.tokenContractAddress()` vs `payment.getTokenContractAddress()`), placed right after
  loading the payment, before computing the discrepancy or calling either `tryFinalizeFrom`
  attempt — mirrors `applyReorged`'s own identical T12 guard. `findInvoiceIdByChainAndTxHash`
  gained a Javadoc paragraph documenting the first-level-cache behavior precisely (Finding 2).
- **`ChainEventConsumer.java`** — new private `resolveInvoiceForFinalization(ChainTxFinalized
  event): Optional<InvoiceWatchView>`, extracting the two-step invoice lookup and its logging out
  of `onChainTxFinalized`'s own body (Finding 1). `onChainTxFinalized` now reads as a flat
  3-step pipeline: resolve invoice → apply finalized → mark outcome.

No public API changed; no renames beyond what each accepted finding required; no file touched
beyond what the frozen brief authorized.

## Verification

`mvn -o compile`, `mvn -o test-compile` (forced clean): clean. `mvn -o test
-Dtest='!*IntegrationTest,!*IT'`: **246/246 passing** — unchanged, since none of these fixes
touch a path any existing test exercises differently (the new token-mismatch guard never fires
for any existing test's own consistent token-contract-address values).
