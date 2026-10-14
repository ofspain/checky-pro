# payment · T13 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

- **`FinalizationOutcome.java`** (new) — `applyFinalized`'s own result record
  (`paymentId`, `invoiceId`, `amountDiscrepancy`), mirroring `ReorgOutcome` (T12).
- **`PaymentService.java`** — two named constants (`UNDERPAID`/`OVERPAID`); new
  `findInvoiceIdByChainAndTxHash` (read-only passthrough); `applyFinalized`'s new signature
  (`InvoiceWatchView`, `ChainTxFinalized`) and return (`Optional<FinalizationOutcome>`); new
  private `classifyDiscrepancy`; `tryFinalizeFrom` gains the new `amountDiscrepancy` parameter.
- **`PaymentRepository.java`** — `markFinalizedIfCurrentState`'s `@Query` gains the
  `amount_discrepancy` `SET` clause and parameter.
- **`InvoiceService.java`** — new `markOutcomeIfOpenOrExpired` (two-attempt, null/invalid-value
  guarded), reusing `updateStateIfCurrentState` (T08) with zero new repository code.
- **`ChainEventConsumer.java`** — `onChainTxFinalized`'s new two-step lookup
  (`findInvoiceIdByChainAndTxHash` → `findByIdAsWatchView`) and the new `markOutcomeIfOpenOrExpired`
  call; new private `toInvoiceState` helper.

## Mapping to acceptance criteria (frozen brief, Phase 4)

- **AC1 (R15)** — `classifyDiscrepancy` + the new `SET` clause; computed once, reused across both
  `tryFinalizeFrom` attempts.
- **AC2 (scope addition, Finding 1 widened)** — `markOutcomeIfOpenOrExpired` tries `OPEN` then
  `EXPIRED`; any other invoice state returns `false`, no exception.
- **AC3 (R16)** — no production change; the named test is Phase 10's own deliverable.
- **AC4 (R14 continuity)** — the stale/unknown-payment branches of `applyFinalized` return
  `Optional.empty()` before any discrepancy is computed or outcome returned.
- **AC5 (L12)** — `PaymentService` still has no `InvoiceService`/`CryptoClient` field;
  `findInvoiceIdByChainAndTxHash` returns a bare `Long`.
- **AC6 (L6)** — `BigDecimal` throughout; no floating-point type introduced.
- **AC7 (Finding 2)** — `onChainTxFinalized` looks up by `invoiceId`, never `event.watchId()`.
- **AC8 (Finding 5)** — `markOutcomeIfOpenOrExpired(null, ...)` throws via `Objects.requireNonNull`.

## Deviations from the plan, disclosed

1. **A process correction, caught and reversed before any review saw it, not shipped silently.**
   While fixing the existing tests broken by the new signatures, I drafted two genuinely **new**
   test methods — `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (the R15 named test)
   and a null-argument test for `applyFinalized` — directly in this phase. This repeats the exact
   mistake T03 made earlier in this pipeline (writing tests in Phase 6 instead of Phase 10) and
   contradicts the precedent T12's own Phase 6 correctly held to (zero new test methods, only
   compile-fixes to existing ones). Caught before finishing this phase, not after a review — both
   new tests were removed; the suite was re-verified at exactly 246/246 (the T12 baseline), proving
   this phase's own test diff is now strictly mechanical (existing call sites updated for the new
   signatures, nothing more). Both tests are deferred to Phase 10, where they belong.
2. No other deviation. The implementation matches the frozen brief and Phase 5 plan exactly.

## Verification

- `mvn -o compile`, `mvn -o test-compile` (forced clean, not incremental — the first
  incremental attempt masked real compile errors from the signature changes): clean.
- `mvn -o test -Dtest='!*IntegrationTest,!*IT'`: **246/246 passing** — identical to the T12
  baseline, confirming no new test coverage was accidentally introduced in this phase.
