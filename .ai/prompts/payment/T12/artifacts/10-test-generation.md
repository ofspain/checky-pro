# payment · T12 · Phase 10 — Test Generation

Consumes: `artifacts/09-review-resolution.md`

Tests only — no production-code change was planned, but one real implementation bug was
self-caught while writing tests and is disclosed below rather than hidden.

## A self-caught correction during test writing

Writing `shouldSwallowAnUnexpectedRuntimeExceptionFromInvoiceLookupWithoutPropagating` (meant to
prove Phase 9 Finding 2's widened catch) failed immediately: the Phase 9 fix only wrapped the
`cryptoClient.registerWatch`/`invoiceService.updateWatchIdIfOpen` calls in `try`, leaving the
earlier `invoiceService.findByIdAsWatchView(invoiceId)` lookup and the `invoice.state() != OPEN`
check still outside any catch — exactly the gap Finding 2 was meant to close, just not fully.
Fixed in `ChainEventConsumer.registerReplacementWatch`: the `try` now wraps the method's entire
body. Re-ran the full suite after the fix (245/245 passing) before finalizing this manifest.

## Test manifest

| Test | File | Verifies |
|---|---|---|
| `shouldWalkBackwardConfirmingToSeenOnReorg` (named) | `PaymentServiceTest` | AC1/R11 — `CONFIRMING → SEEN` reversal; `ReorgOutcome.newState() == SEEN`; `VerificationRecord` recorded; `markWatchingIfSeen` never attempted once the first branch succeeds |
| `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` (named) | `PaymentServiceTest` | AC2/R12 — `SEEN → WATCHING` reversal; `ReorgOutcome.newState() == WATCHING`; `ReorgOutcome.invoiceId()` carries the payment's own `invoiceId`; `VerificationRecord` recorded |
| `shouldLogAndDropChainTxReorgedForAnUnknownPayment` | `PaymentServiceTest` | AC8/R14 continuity — no known payment, no verification calls |
| `shouldIgnoreReorgWhenTokenContractAddressMismatchesTheStoredPayment` | `PaymentServiceTest` | AC7/Finding 6 (narrowed) — the one real mismatch clause that survives Finding 4's dead-code removal |
| `shouldRecordIgnoredWhenReorgedEventIsStale` | `PaymentServiceTest` | AC8/R14 — neither reversal branch applies (e.g. already `FINALIZED`) → `recordIgnored` |
| `applyReorgedShouldThrowNullPointerExceptionForNullArgument` | `PaymentServiceTest` | null-safety, mirrors the other `apply*` methods' own convention |
| `shouldDedupeDuplicateChainEventsByEventKeyOnReorg` | `ChainEventConsumerTest` | AC10/L5 continuity — dedupe-first, same transaction, mirrors the other 3 listeners |
| `shouldLogAndDropAMalformedReorgedPayloadWithoutPropagating` | `ChainEventConsumerTest` | Phase 8 (T11) Finding 2 continuity — a poison payload never propagates |
| `shouldNotRegisterReplacementWatchWhenReorgWalksBackToSeen` | `ChainEventConsumerTest` | R11 — no watch action for the `CONFIRMING → SEEN` reversal |
| `shouldNotRegisterReplacementWatchWhenReorgIsStaleOrUnknown` | `ChainEventConsumerTest` | R14 continuity — a stale/unknown reorg never even attempts a lookup |
| `shouldRegisterReplacementWatchAfterWalkingBackToWatching` | `ChainEventConsumerTest` | AC2 — registers with the invoice's own real parameters and original `expiresAt`; updates `Invoice.watchId`; looks up by `invoiceId`, not `watchId` (Phase 9 Finding 1) |
| `shouldSkipRegistrationWhenInvoiceIsNoLongerOpen` | `ChainEventConsumerTest` | Phase 9 Finding 7 |
| `shouldUnregisterOrphanedWatchWhenUpdateWatchIdFailsAfterSuccessfulRegister` | `ChainEventConsumerTest` | Phase 9 Finding 3 |
| `shouldSwallowACryptoServiceFailureDuringReplacementWatchRegistrationWithoutPropagating` | `ChainEventConsumerTest` | AC5/Finding 3 (Phase 4) — a `registerWatch` failure never rolls back the already-committed reversal |
| `shouldSwallowAnUnexpectedRuntimeExceptionFromInvoiceLookupWithoutPropagating` | `ChainEventConsumerTest` | Phase 9 Finding 2 — caught the self-correction above |
| `shouldDeserializeChainTxReorged` | `ChainEventDtoDeserializationTest` | field-for-field match against `tx-reorged.v1.schema.json` |

## Acceptance criteria not requiring a new test (continuity, already covered elsewhere)

- **AC6 (layering: `PaymentService` makes no `CryptoClient`/`InvoiceService` call)** — no test
  needed. `PaymentService`'s own constructor has no `CryptoClient`/`InvoiceService` parameter at
  all; there is no reference to call through, a compile-time structural guarantee rather than a
  runtime property, mirroring T11's own identical treatment of its AC10.
- **AC9 (L11)** — no test needed. No `Receipt` entity/table/code path exists anywhere in this
  codebase to even reach; this is a structural fact of the current build, not new runtime
  behavior this task introduces, mirroring the frozen brief's own framing.
- **`ArchitectureTest`** — re-run unchanged; both existing rules (`shouldRejectFloatingPointMoneyTypesAtArchUnit`,
  `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild`) still pass against the
  widened `InvoiceWatchView`/new `ReorgOutcome`/`ChainTxReorged` — no new entity, no new module,
  no new canary needed.
- **Repository-level integration test for the two new atomic reversal queries
  (`markSeenIfConfirming`/`markWatchingIfSeen`) and `updateWatchIdIfOpen`** — deferred, same as
  every prior task's own identical precedent: Docker/Testcontainers is unavailable in this
  environment. The derived query `existsByInvoiceIdAndStateInAndReorgedAtIsNull` was already
  verified directly via `PartTree` at Phase 7 (not a test file, a one-off verification); the two
  `@Query`-annotated reversal methods and `updateWatchIdIfOpen` are plain parameterized JPQL,
  the same low-risk shape every other atomic update in this codebase already uses untested-by-
  Testcontainers until Docker becomes available.

## Verification

`mvn -o compile`, `mvn -o test-compile`: clean. `mvn -o test -Dtest='!*IntegrationTest,!*IT'`:
**245/245 passing** (229 baseline + 16 new).
