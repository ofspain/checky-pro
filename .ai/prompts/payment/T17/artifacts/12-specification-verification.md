# payment · T17 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R17** (resolved scope: exactly `PAID`, decided directly by the user) — a `FINALIZED`, invoice-satisfying payment requests attestation | Yes | `ReceiptService.java:83` (`issueIfEligible`'s own two guards) | `shouldIssueReceiptOnlyFromFinalizedState` (named), `shouldReturnNotEligibleWhenInvoiceIsUnderpaidOrOverpaid`, `ChainEventConsumerTest.shouldMarkInvoicePaidOnExactMatch`/`shouldMarkInvoiceUnderpaidOnUnderpayment`/`shouldMarkInvoiceOverpaidOnOverpayment` (strengthened, Phase 10) | No | No |
| **R18** — a non-`FINALIZED` payment never issues a receipt under any code path | Yes | `ReceiptService.java:83`'s own first guard | `shouldNeverIssueReceiptBeforeFinalized` (named) | No | No |
| **R19** (partial — mechanism built at T15/T16; this task is the first real caller) — compute the digest, call `/attest`, never sign locally | Yes | `ReceiptService.java:83` (`buildPayload`/`ReceiptDigest.digest`/`cryptoClient.attest`) | `shouldRequestKmsSignatureViaCryptoAttestEndpoint` (named) | No | No |
| **R20** — `BLOCKED` moves both payment and invoice to `HELD`, emits nothing | Yes | `ReceiptService.java:129` (`handleBlocked`), `PaymentService.java:386` (`markHeldIfFinalized`), `InvoiceService.java:243` (`markHeldIfPaid`) | `shouldNotIssueReceiptWhenAttestReturnsBlocked` (named), both split-result directions, both no-op directions | No | No |
| **L2** — a `SIGNED` result does not reach `ATTESTED`, does not persist a `Receipt` row | Yes | `ReceiptService.java:83`'s own `Signed` branch — no repository call | `shouldIssueReceiptOnlyFromFinalizedState` (strengthened, Phase 11, never-HELD assertions) | No | No |
| **New, found during this task (Phase 3 Finding 1/2/3)** — cross-module reads via dedicated views; atomic, all-or-nothing `HELD` pair; fresh re-read before `attest()` | Yes | `PaymentReceiptView`/`InvoiceReceiptView`; `handleBlocked`'s own split-result guard; `issueIfEligible`'s own fresh `findReceiptPrimitives` calls | Full `ReceiptServiceTest` suite (16 methods) | No | No |
| **New, found during this task (Phase 3 Finding 6/Phase 9)** — `409` never triggers `HELD`, distinct from `200 BLOCKED` | Yes | `ReceiptService.java:83`'s own separate `catch` clauses | `shouldReturnFailedWithoutStateChangeWhenAttestIsRefused`/`...WhenCryptoServiceIsUnavailable` | No | No |
| **New, found during review (Phase 9)** — a null `blockedReason` (a legitimate wire response) does not crash the `HELD` transition | Yes | `PaymentService.java:386`'s own `recordedReason` fallback | `shouldToleratesNullBlockedReasonWithoutThrowing` | No | No |
| **Transaction safety (AC5)** — `attest()` never runs inside an open transaction | Yes | `ChainEventConsumer.java:158` (`onChainTxFinalized`, no longer `@Transactional`, split via `TransactionTemplate`) | `ChainEventConsumerIdempotencyAndOrderingIntegrationTest` (real Postgres+Kafka, re-confirmed stable through every phase) | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. `ReceiptService.issueIfEligible` is the first real
caller of `CryptoClient.attest` (T15) and `ReceiptDigest` (T16); the `HELD` transition — a
`PaymentStateMachine` edge permitted since T09 but never exercised — is now real, atomic, and
audited.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All eight (AC1–AC8)
are satisfied: AC1 (eligibility guard), AC2 (digest/attest), AC3 (BLOCKED→HELD), AC4 (SIGNED
persists nothing), AC5 (transaction safety), AC6 (409 vs BLOCKED, amended at Phase 4, corrected
again at Phase 9 for the null-reason bug), AC7 (split-result atomicity), AC8 (audit trail).

**(3) Does it violate any LOCKED decision?** No. L2 holds — confirmed by direct inspection that
no code path in this task's own new files reaches `ATTESTED` or persists a `Receipt` row. L1/L12
hold — `PaymentStateMachine` unchanged; `ReceiptService`/`ReceiptIssueResult` import no entity.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **No retry mechanism for a failed `attest()` call or the narrower crash-before-`issueIfEligible`
  window** — both explicitly disclosed in `ReceiptService`'s own class Javadoc (Phase 2, sharpened
  at Phase 9 after Kimi's Phase 8 independent confirmation). No task in the current breakdown
  (15–21) adds a retry job; this is a genuine, accepted gap, not a silent one.
- **No end-to-end integration test exercises a real `BLOCKED` outcome** (Phase 11 Gap 5,
  deliberately deferred) — the existing real Postgres+Kafka test only exercises the `FINALIZED`/
  `PAID` path with a `CryptoClient` that fails (confirming the disclosed-failure path works for
  real), not a stubbed `BLOCKED` response. Deferred as disproportionate effort for a Low-
  confidence gap until T18/T19 exist for a more holistic end-to-end test.
- **`CanonicalJson`'s own genuine reusability for T19 remains unverified until T19 exists** —
  unchanged from T16's own disclosed risk, not newly introduced here.

## Verdict

**PASS** — `ReceiptService` is fully implemented, correctly guards R17's resolved scope,
correctly distinguishes `BLOCKED`/`409`/transport-failure, and atomically handles the `HELD`
transition pair with a genuine rollback-on-split-result guard — all independently confirmed
across two real Kimi review rounds (Phase 8 found 2 real bugs: the null-`blockedReason` crash
and the narrow `RuntimeException` catch; Phase 11 found 3 small, genuinely worthwhile test
additions). `ReceiptServiceTest`: 16/16. Full payment-service suite: 374/374 passing.
