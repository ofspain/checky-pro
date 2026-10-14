# payment · T13 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R15** — compute under/overpayment against invoice amount in base units, record on the payment, exact match only (Q1) | Yes | `PaymentService.java:226,243,273` (`applyFinalized`, `classifyDiscrepancy`); `PaymentRepository.java` `markFinalizedIfCurrentState`'s `amount_discrepancy` `SET` clause | `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` (named) | No | No |
| **R16** — persist and surface the address-poisoning flag | Yes (substantially since T11; this task adds its own named test) | `PaymentRepository.markFinalizedIfCurrentState`'s existing `addressPoisoningFlag` `SET` clause (T11); `PaymentFinalizedPayload` (T11) | `shouldSurfaceAddressPoisoningFlagFromObservation` (named) | No | No |
| **Phase 0 scope addition** — transition `Invoice.state` to `PAID`/`UNDERPAID`/`OVERPAID` at `FINALIZED`, closing the orphaned-requirement gap and the T08 sweep-race | Yes | `InvoiceService.java:178` (`markOutcomeIfOpenOrExpired`); `ChainEventConsumer.java:140-161` (`onChainTxFinalized`) | `shouldMarkOutcomeFromOpen`, `shouldMarkOutcomeFromExpiredWhenOpenAttemptFails`, `shouldMarkInvoicePaidOnExactMatch`/`...Underpaid.../...Overpaid...` | No | No |
| **L6** — `BigDecimal`/`NUMERIC` only | Yes | `classifyDiscrepancy` uses `BigDecimal.compareTo` throughout; no new floating-point surface | `ArchitectureTest.shouldRejectFloatingPointMoneyTypesAtArchUnit` (re-run, unchanged, passing) | No | No |
| **L8** (context) — no new outbox event for the invoice-state transition | Yes, by design | `R30`'s own exhaustive event list unchanged; `markOutcomeIfOpenOrExpired` has no outbox call, mirrors `expireInvoice`'s own identical no-outbox shape | N/A (absence of behavior) | No | No. Disclosed explicitly in the frozen brief (Finding 4), not a silent gap. |
| **L12** — `PaymentService` never calls `InvoiceService`/`CryptoClient` | Yes | `PaymentService`'s constructor has no such dependency at all; `findInvoiceIdByChainAndTxHash`/`applyFinalized` both return only primitives/`FinalizationOutcome` (never an entity) | Structural guarantee, no test needed (same treatment as T12's own AC6) | No | No |
| **L1/L5/L14** (context, continuity) — no new state-machine edge, dedupe-first same transaction, one-transaction-per-invoice | Yes, unaffected | `onChainTxFinalized` stays directly `@Transactional` (no `TransactionTemplate` split needed - no outbound call); dedupe precedes the new invoice-resolution step | `shouldDedupeDuplicateChainEventsByEventKeyOnFinalized` (strengthened at Phase 11 to also assert `findInvoiceIdByChainAndTxHash` is skipped) | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Both named tests pass; the amount-discrepancy
computation and the address-poisoning surfacing are both exercised end-to-end through the new
`applyFinalized` signature; the Phase 0-approved scope addition (marking `Invoice.state`) is
fully implemented and tested, including its own widened `OPEN`-or-`EXPIRED` guard.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All eight (AC1–AC8)
are satisfied. AC5 needs no test (structural guarantee, consistent with T12's own precedent for
the analogous AC6).

**(3) Does it violate any LOCKED decision?** No. L6/L8/L12/L1/L5/L14 all hold, with L8's own
"no new outbox event" an explicitly disclosed design choice (Phase 4 Finding 4), not a silent
gap discovered here.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **Testcontainers-gated gaps** (Phase 11 Gaps 1–3): the real `amount_discrepancy` column
  mapping, `markOutcomeIfOpenOrExpired`'s own exact SQL, and a full Kafka+Postgres end-to-end
  flow have only been verified by Mockito-level argument matching — never executed against a
  real Postgres. Docker is unavailable in this environment; deferred, consistent with every
  prior task's own identical precedent.
- **A self-caught process correction during Phase 6** (disclosed in `06-implementation-notes.md`):
  two test methods were briefly drafted directly in the implementation phase, caught and removed
  before this verification, with the suite re-confirmed at the exact pre-task baseline. Not a
  residual risk — fully resolved within the same phase it occurred.
- **Pre-existing, unrelated:** `InvoiceRepositoryIntegrationTest` (T08) remains broken when
  Docker is available — not caused by, and not fixed by, this task (re-confirmed by Kimi's own
  Phase 8 review).

## Verdict

**PASS** — R15 and R16 are both satisfied, with the Phase 0-approved `Invoice.state` scope
addition fully implemented and closing a real, previously-latent sweep-race bug; every LOCKED
decision holds; both named tests pass; 265/265 non-Docker tests green; every finding from
Phases 3, 8, and 11 has an explicit, recorded disposition (fixed, corrected, or deliberately
deferred/rejected with a reason).
