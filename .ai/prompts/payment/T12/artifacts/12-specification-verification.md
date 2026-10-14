# payment · T12 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R11** — `chain.tx.reorged` for `CONFIRMING` walks back to `SEEN`, records reversal | Yes | `PaymentService.java:241-265` (`applyReorged`, `markSeenIfConfirming` branch); `PaymentRepository.java:101-111` | `shouldWalkBackwardConfirmingToSeenOnReorg` (named) | No | No |
| **R12** — `chain.tx.reorged` for `SEEN` walks back to `WATCHING`, records reversal | Yes | `PaymentService.java:266-271` (`markWatchingIfSeen` branch); `ChainEventConsumer.java:154,204` (replacement-watch registration) | `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named), `shouldRegisterReplacementWatchAfterWalkingBackToWatching` | No | No |
| **R13** — dedupe on `chain:txhash:eventtype`, apply at most once | Yes | `ChainEventConsumer.java:154-167` (`transactionTemplate.execute` wraps `dedupe` + `applyReorged` in one transaction) | `shouldDedupeDuplicateChainEventsByEventKeyOnReorg` | No | No |
| **R14** — stale/unknown events ignored, recorded as ignored | Yes | `PaymentService.java:273-277` (`recordIgnored`, "stale-reorg-event"); `PaymentService.java:237-239` (unknown payment) | `shouldRecordIgnoredWhenReorgedEventIsStale`, `shouldLogAndDropChainTxReorgedForAnUnknownPayment` | No | No |
| **L1** — only `CONFIRMING→SEEN`/`SEEN→WATCHING` reversals permitted, no new edges | Yes (unchanged since T09) | `PaymentStateMachine.java` edge set untouched; `PaymentService.java:254-257` defensive `isPermitted` check | Covered by existing `PaymentStateMachineTest` (T09) | No | No |
| **L5** — dedupe-first, same transaction as the effect | Yes | `ChainEventConsumer.java:154-167` — `dedupe()` and `applyReorged()` run inside the same `TransactionTemplate` transaction | `shouldDedupeDuplicateChainEventsByEventKeyOnReorg` | No | **Deviation (disclosed, Phase 6/9):** this listener alone is not itself `@Transactional` on the method — an explicit `TransactionTemplate` achieves the same "dedupe + effect, one transaction" property so the best-effort replacement-watch call can run outside it. The *property* L5 requires holds; the *mechanism* differs from the other three listeners for a disclosed, load-bearing reason (Decision 2). |
| **L11** — reorg is a first-class transition; no receipt exists pre-finality, so no revocation path | Yes, trivially | No `Receipt` entity/table/code path exists anywhere in this codebase | No test needed — structural fact, not new runtime behavior (consistent with the frozen brief's own framing) | No | No. The post-`ATTESTED` `HELD`+ops-alert half of L11 has no caller anywhere in this codebase yet (confirmed at Phase 0) — out of this task's own scope (`R11`/`R12` name only the two pre-finality reversals), not a violation. |
| **L12** — no feature module imports another's entity; `common`/views only | Yes | `InvoiceWatchView.java` widened, never the real `Invoice`; `ReorgOutcome` carries only primitives/enum, not an entity | `ArchitectureTest.shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (re-run, unchanged, passing) | No | No |
| **L14** — one transaction per invoice | Yes, with a deliberate refinement | `PaymentService.java` guard split (`ACTIVE_NON_TERMINAL_STATES` + `existsByInvoiceIdAndStateInAndReorgedAtIsNull`) | `shouldAllowANewSeenWhenTheOnlyNonTerminalPaymentIsAReorgedWatchingRow`, `existsByInvoiceIdAndStateInShouldCheckOnlyTheActiveNonTerminalSet` | No | **Deviation (disclosed, Phase 2/4):** a reorged-out transaction's own row no longer blocks a replacement — reasoned through explicitly as consistent with, not a violation of, L14's intent ("one *successful* transaction"), not assumed. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Both named tests pass; both reversal edges have a real caller for the first time since T09 defined them; the reversal is recorded via `VerificationRecordService` exactly as R11/R12 require.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All ten (AC1–AC10) are satisfied. AC6 and AC9 need no test (structural/compile-time guarantees, consistent with T11's own precedent for its analogous ACs) — both reconfirmed directly in this verification: `PaymentService`'s constructor has no `CryptoClient`/`InvoiceService` parameter (AC6), and no `Receipt` mechanism exists anywhere to reach (AC9).

**(3) Does it violate any LOCKED decision?** No. L5 and L14 both hold at the level of the property each decision actually requires, with disclosed, reasoned-through mechanism differences (not silent deviations) — both were surfaced and resolved at Phase 2/4/9, not discovered here for the first time.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **Testcontainers-gated gaps** (Phase 11 Gaps 2–4): the two new atomic reversal queries' own `reorged_at = :now` assignment, the `existsByInvoiceIdAndStateInAndReorgedAtIsNull` derived query's real SQL, and `InvoiceService.findByIdAsWatchView`/`updateWatchIdIfOpen` have only been verified by Mockito-level argument matching and (for the derived query) a direct `PartTree` parse — never executed against a real Postgres. Docker is unavailable in this environment; deferred, consistent with every prior task's own identical precedent.
- **Crash-window and idempotency-key-collision edge cases** (Phase 9 Findings 5/9, Phase 11 Gap 6): both bounded, self-resolving (the invoice rides its own natural expiry), both explicitly documented in code, neither warranting a code change within this task's own scope.
- **Cross-service gap** (logged at Phase 0, memory: `crypto-service-watcher-unregister-stops-reorg-finality-tracking`): `chain.tx.reorged` may not reliably reach this service in production once `crypto-service`'s own reconciliation loop stops tracking an already-matched transaction — this task's own logic is correct *given the event arrives*; the reliability gap is entirely on the emission side, already tracked, not this task's own defect.
- **Pre-existing, unrelated:** `InvoiceRepositoryIntegrationTest` (T08) remains broken when Docker is available — not caused by, and not fixed by, this task.

## Verdict

**PASS** — R11/R12/R13/R14 and L1/L5/L11/L12/L14 are all satisfied (L5/L14 with disclosed, reasoned mechanism differences, not violations); both named tests pass; 246/246 non-Docker tests green; every open finding from Phases 3, 8, and 11 has an explicit, recorded disposition (fixed or deliberately deferred with a reason).
