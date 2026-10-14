# payment · T11 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T11 — Idempotent consumers |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1/AC1a | First `chain.tx.seen` for an `OPEN` invoice with no existing non-terminal payment creates `Payment` in `SEEN` (R7, named) | `PaymentService.applySeen` | `shouldTransitionToSeenOnChainTxSeen`, `shouldNotCreatePaymentWhenInvoiceIsNoLongerOpen`, `shouldNotCreateASecondPaymentWhenANonTerminalPaymentAlreadyExistsForTheInvoice` (Phase 8 Kimi Finding 1, mutation-verified) |
| AC2 | `unregisterWatch` last, inside the transaction (R7a, named; L14) | `ChainEventConsumer.onChainTxSeen` | `shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch` (`InOrder`-verified), `shouldNotUnregisterWatchWhenApplySeenReturnsFalse` |
| AC3 | `payment.seen` published, same transaction (R8, named) | `PaymentService.applySeen` | `shouldEmitPaymentSeenOnFirstSeen` |
| AC4/AC4a/AC4b | `SEEN→CONFIRMING` transition / refresh / stale three-way split (R9, named; R14) | `PaymentService.applyConfirmed`, `PaymentRepository.markConfirmingIfSeen`/`refreshConfirmationsIfGreaterAndCurrentlyConfirming` | `shouldTransitionToConfirmingOnChainTxConfirmed`, `shouldRefreshConfirmationsWithoutAVerificationRecordWhenAlreadyConfirmingWithAGreaterCount`, `shouldRecordIgnoredWhenConfirmedCountIsNotGreaterOrPaymentIsInAnyOtherState` |
| AC5 | `SEEN`/`CONFIRMING→FINALIZED`, real observed fields persisted (R10, named) | `PaymentService.applyFinalized`, `PaymentRepository.markFinalizedIfCurrentState` (tried per-state, Phase 7 self-caught race fix) | `shouldTransitionToFinalizedFromConfirming`, `shouldTransitionToFinalizedFromSeenWhenNeverConfirmed`, `shouldPersistRealObservedAmountAndAddressesOnFinalize`, `shouldPersistAddressPoisoningFlagWhenTrue`, `shouldRecordIgnoredWhenFinalizedEventIsStale` |
| AC6 | Duplicate delivery is a complete no-op (R13, named; L5) | `ChainEventConsumer.dedupe`, `ProcessedEventRepository.insertIfNew` (native `ON CONFLICT DO NOTHING`, mirrors `notification-service`'s own proven precedent) | `shouldDedupeDuplicateChainEventsByEventKeyOn{Seen,Confirmed,Finalized}` |
| AC7 | Dedupe and effect application in the same transaction (L5) | Every listener method `@Transactional` | Confirmed by direct code read (Phase 7) |
| AC8 | Every real transition calls `VerificationRecordService.record` with the correct key/count | `PaymentService` | Verified across all transition tests above |
| AC9 | No `Payment` ever persisted in `CREATED`/`WATCHING` (Finding 5, decided) | `Payment.createSeen` hardcodes `SEEN` | `createSeenShouldAlwaysSetStateToSeenNeverCreatedOrWatching` |
| AC10 | `consumer`/`payment` never reference `InvoiceRepository` directly (Finding 2; L12) | `InvoiceRepository` is package-private | Compile-time impossibility — no test needed, confirmed and disclosed at Phase 10 |
| AC11 | One listener per event type; `ArchitectureTest` canaries pass (Finding 4) | `ChainEventConsumer` | `ArchitectureTest`'s 5 canaries, re-run clean with `consumer` added |
| AC12 | DTOs deserialize correctly (Finding 7) | 3 DTOs, field-for-field matched to real schemas | `ChainEventDtoDeserializationTest` (4 tests, including the Phase 11 unknown-property rejection test) |

## Required Tests (frozen brief)

All 6 named tests present and passing, plus every implied boundary test. 33 net-new tests across
`PaymentServiceTest` (18), `ChainEventConsumerTest` (11), and `ChainEventDtoDeserializationTest`
(4).

## Scope discipline

`git diff --stat b5449356..HEAD -- services/payment` (T10's own final commit to this task's own
HEAD) touches exactly 20 files — all named in the frozen brief's own Files to Create/Modify list,
with one disclosed, necessary addition: `InvoiceWatchView.java` (Phase 6 — a real ArchUnit
violation found by running the existing T09 rule, not assumed away: the frozen brief's own
Finding 2 addressed repository-level coupling but not entity-instance leakage). No migration
beyond `V7`/`V8` touched. Nothing under `spec/`.

## Locked decisions

- **L1** — `PaymentStateMachine` is the sole arbiter of legal transitions. Held — `PaymentService`
  never duplicates its edge set; the atomic `UPDATE` queries' own `WHERE` clauses encode exactly
  the same edges, with defensive `isPermitted` sanity checks (Phase 9 Kimi Finding 6) guarding
  against drift.
- **L5** — idempotent, dedupe-on-key consumers. Held — `processed_events` dedupe is the first
  statement in every listener, same transaction as the effect.
- **L12** — module boundaries. Held, including the stronger entity-instance form Phase 6 found
  and fixed via `InvoiceWatchView`.
- **L14** — one transaction per invoice. Held — `unregisterWatch` runs last, inside the
  transaction; a failure rolls back the entire `SEEN` transition.

## Open items carried forward (not blockers for this task)

- Real Testcontainers proof of `PaymentRepository`'s own atomic queries — deferred (Phase 11
  Gap 1), Docker unavailable this session, same disclosed pattern as T08/T09/T10.
- `R6` (watch-ack → `CREATED`/`WATCHING`) remains a confirmed, disclosed, orphaned requirement —
  no task in this 29-task breakdown implements it; decided (Phase 4, user) to accept and
  document, not retroactively fix via T06.
- Chain/token consistency validation between the invoice and a consumed event (Phase 9 Kimi
  Finding 5) deferred to task 13's own natural scope (R15/R16 territory).
- `notification-service`'s own `AuthEventConsumer` has the identical "propagates
  `JsonProcessingException`" gap this task fixed for itself (Phase 9 Kimi Finding 2) — not
  retroactively fixed there, logged as a cross-service follow-up.
- `InvoiceRepositoryIntegrationTest` (T08) remains confirmed broken — unrelated, already logged.

## Test suite status

229/229 non-Docker-dependent tests passing (up from 196 at the start of this task).

## Verdict rationale

All 12 acceptance criteria are implemented and test-covered. Two full Kimi review passes
(Phase 3 design challenge, Phase 8 independent review) together found one genuine architectural
gap requiring a real Human Approval decision (the orphaned `R6`), one confirmed high-severity
omission from this task's own frozen brief (the missing existing-payment-row guard), and a
comprehensive set of real hardening/coverage findings — all fixed or deliberately, honestly
deferred with clear rationale. This task's own self-review (Phase 7) additionally caught and
fixed a genuine audit-trail race condition before Kimi ever saw the code. All 4 LOCKED decisions
in scope held. No file outside this task's own disclosed scope touched. **PASS.** Proceeding to
Phase 13.
