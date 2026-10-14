# payment · T12 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

- **`ChainTxReorged.java`** (new DTO) — matches `tx-reorged.v1.schema.json` exactly.
- **`InvoiceWatchView.java`** — widened with `chain`, `tokenContractAddress`, `amount`,
  `expiresAt`; `from(Invoice)` updated.
- **`InvoiceRepository.java`** — `findExpiredCandidatesWithoutInFlightPayment`'s `NOT EXISTS`
  subquery refined (a `WATCHING` row with `reorged_at IS NOT NULL` no longer blocks expiry); new
  `updateWatchIdIfOpen`.
- **`InvoiceService.java`** — new `updateWatchIdIfOpen(Long, UUID, Instant)` passthrough.
- **`PaymentRepository.java`** — new `markSeenIfConfirming`, `markWatchingIfSeen` (both set
  `reorgedAt`); new `existsByInvoiceIdAndStateInAndReorgedAtIsNull` (derived query).
- **`PaymentService.java`** — `applySeen`'s existing-payment guard split into
  `ACTIVE_NON_TERMINAL_STATES` (`CREATED`/`SEEN`/`CONFIRMING`) OR'd with a `WATCHING`-only,
  `reorgedAt IS NULL` check; new `applyReorged(ChainTxReorged): Optional<PaymentState>`.
- **`ChainEventConsumer.java`** — new `onChainTxReorged` listener + `registerReplacementWatch`
  helper; new `TransactionTemplate` field, built from an injected `PlatformTransactionManager`.

## Mapping to acceptance criteria (frozen brief, Phase 4)

- **AC1 (R11)** — `applyReorged`'s `markSeenIfConfirming` path; no `CryptoClient` call in that
  branch.
- **AC2 (R12)** — `applyReorged`'s `markWatchingIfSeen` path returns `Optional.of(WATCHING)`;
  `onChainTxReorged` then calls `registerReplacementWatch`, which calls `registerWatch` with the
  invoice's own real `chain`/`payToAddress`/`tokenContractAddress`/`amount`/`expiresAt` and
  updates `Invoice.watchId` via `updateWatchIdIfOpen`.
- **AC3 (Finding 1)** — `applySeen`'s guard no longer includes a reorged `WATCHING` row;
  verified by the rewritten `existsByInvoiceIdAndStateInShouldCheckOnlyTheActiveNonTerminalSet`
  test (adjusted, not new — see Deviations).
- **AC4 (Finding 1, change 2)** — `findExpiredCandidatesWithoutInFlightPayment`'s subquery now
  reads `p.state IN ('CREATED','SEEN','CONFIRMING') OR (p.state = 'WATCHING' AND p.reorged_at IS
  NULL)`.
- **AC5 (Finding 3)** — `registerReplacementWatch` catches `CryptoServiceUnavailableException`,
  logs at `ERROR`, returns normally; the DB reversal already committed via `transactionTemplate`
  before this method is even called, so nothing rolls back.
- **AC6 (Finding 4)** — `applyReorged` holds no reference to `CryptoClient` or `InvoiceService`
  at all; both calls live in `ChainEventConsumer.registerReplacementWatch`.
- **AC7 (Finding 6)** — `applyReorged`'s mismatch check, before any state-machine/repository
  call.
- **AC8/AC10 (R14/L5 continuity)** — unchanged pattern: `findCurrentState` + `recordIgnored` for
  any non-matching state; `dedupe()` runs first, inside the same `transactionTemplate` block.
- **AC9 (L11)** — no code path exists anywhere in this service that could revoke a receipt (no
  `Receipt` entity/table at all yet); nothing to implement, consistent with the frozen brief's
  own framing of this as a structural fact, not new behavior. (A test documenting this explicitly
  is Phase 10's job, per the brief's own Required Tests list.)

## Deviations from the plan, disclosed

1. **The plan's draft `applyReorgedTransactionally` as a private `@Transactional` method would
   not have worked** — Spring's AOP proxy is bypassed on self-invocation, so a private method
   annotated `@Transactional` and called from within the same class is silently non-transactional
   (the annotation is metadata the proxy never sees). Resolved during implementation (not left
   for Phase 7) with an explicit `TransactionTemplate`, built in the constructor from an injected
   `PlatformTransactionManager` — a real, independently-testable transactional boundary that
   doesn't depend on self-invocation. This adds a 7th constructor parameter to
   `ChainEventConsumer`, and `onChainTxReorged` is the one listener method *not* annotated
   `@Transactional` directly (see the class's own updated Javadoc for the full rationale).
2. **Existing tests broken by the `InvoiceWatchView` widening and the constructor change were
   fixed, not left broken** — three `new InvoiceWatchView(...)` call sites (two in
   `PaymentServiceTest`, one in `ChainEventConsumerTest`) needed the four new positional
   arguments; `ChainEventConsumerTest`'s `setUp()` needed a mocked `PlatformTransactionManager`
   (unstubbed — `TransactionTemplate.execute` calling a mock's `getTransaction`/`commit` with a
   `null` `TransactionStatus` is harmless, since `onChainTxReorged`'s own transactional lambda
   never reads the `TransactionStatus` parameter). This is mechanical compile-fix work forced by
   this task's own signature changes, not new Phase 10 test content.
3. **One existing test's assertion was rewritten, not just made to compile** —
   `existsByInvoiceIdAndStateInShouldCheckTheFullNonTerminalSet` asserted the pre-T12
   `NON_TERMINAL_STATES` constant's full 4-state set on a single call; renamed to
   `existsByInvoiceIdAndStateInShouldCheckOnlyTheActiveNonTerminalSet` and rewritten to verify
   the two separate calls (3-state active set, 1-state `WATCHING`-only set) the Phase 4-approved
   guard refinement now makes. This reflects the frozen brief's own Finding-1 disposition, not an
   undisclosed behavior change.
4. **No `V9` migration** — confirmed both `reorged_at` (`payments.payments`) and `watch_id`
   (`payments.invoices`) already existed in `V1`; nothing to add.

## Verification

- `mvn -o compile` and `mvn -o test-compile`: clean, no errors.
- `mvn -o test -Dtest='!*IntegrationTest,!*IT'`: **229/229 passing** (same count as the T11
  baseline — Phase 6 fixes existing tests for compile/assertion correctness but adds no new test
  methods, per this phase's own "do NOT write tests here" rule; new coverage for R11/R12/AC3-7 is
  Phase 10's job).
