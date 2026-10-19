# payment · T29 · Phase 9 — Review Resolution

Consumes:
- `artifacts/08-independent-review.md` (Kimi)
- `artifacts/07-self-review.md`

I checked every finding against the source before deciding. All eight were factually accurate. F5 understated the gap (see below), and F8 repeats Phase 3 F5.

## Human decisions (2026-10-19)

| Finding | Decision | Change |
|---|---|---|
| F1 — a qualified citation isn't checked against its class (= S2) | **Accept** | A `Class.method` citation must be declared in that class's own file. A `.method` citation continues the previous class on the same line. A bare `method` is searched globally. |
| F2 — class-only citations are unchecked (= S3) | **Accept** | A bare `` `…Test` `` class citation must have its `.java` file under `src/test/java`. |
| F3 — `ReceiptReconciler` and `LedgerIntegrityJob` are unguarded | **Accept: cite their tests** | Items 7 and 14 now cite `ReceiptReconcilerTest` and `LedgerIntegrityJobTest`. The F2 check fails if either test file disappears, and the tests stop compiling if either class is deleted. No new test is needed. |
| F4 — the whole "Not applicable" line is skipped | **Accept** | The line skip is replaced by an exemption for exactly one name, `DEFERRED_UNWRITTEN_TESTS = {shouldAnchorLedgerHeadOnChainDaily}`. Item 14's other citations are now checked. |
| F5 — item 10 cites only `invoice.created` (= S1) | **Accept: reword, keep the tick** | See below. |
| F6 — header rows are matched anywhere in the file | **Accept** | The Version and Status checks run only on the text before `## 0.`, which is the header table. |
| F7 — partial items aren't guarded | **Accept** | The new `verificationChecklistKeepsItsPartialAndDeferredItemsUnticked` checks that §9 has 14 items, items 1 and 13 start with `- [ ] ` and contain `**Partial —**`, and item 14 starts with `- [ ] **Not applicable this revision:**` and contains `deferred`. |
| F8 — mark task 29 done in `tasks.md` | **Reject** (as at Phase 4) | No `tasks.md` in the repo marks any task done, so `tasks.md` stays out of scope. |

### F5: the gap was wider than reported

The same-transaction claim in item 10 is not test-proven for *any* event, `invoice.created` included.
- `shouldEmitInvoiceCreatedInSameTransactionAsInsert` is a Mockito unit test. It proves `OutboxPublisher.publish` is called in the same method call.
- Atomicity holds by construction. Every emitter calls `publish` inside its own `@Transactional` service method, and `publish` joins that transaction through the default `REQUIRED` propagation (its Javadoc says so).
- Nothing enforces `MANDATORY`, and no rollback test exists.

Item 10 now:
- cites all four emitter tests:
  - `InvoiceServiceTest.shouldEmitInvoiceCreatedInSameTransactionAsInsert`
  - `PaymentServiceTest.shouldEmitPaymentSeenOnFirstSeen`
  - `PaymentServiceTest.shouldPersistRealObservedAmountAndAddressesOnFinalize` (`payment.finalized`)
  - `ReceiptServiceTest.shouldEmitReceiptIssuedAfterAttestation`
- states plainly that atomicity is by construction, with no rollback test.

## Defects found while resolving

**1. A wrong-class citation, caught by the hardened F1 guard on its first run.**
- Item 6 cited `ChainEventConsumerTest.shouldDedupeDuplicateChainEventsByEventKey` as a unit test.
- That method actually lives in `ChainEventConsumerIdempotencyAndOrderingIntegrationTest` and is an integration test.
- The Phase 6 global name search could not catch this. Item 6 is corrected.
- This is exactly the failure F1 described, so it shows the hardening was needed.

**2. A misplaced Javadoc (my own Phase 6 defect).**
- Phase 6 inserted the two T29 tests between `stripDockerfileComments` and its Javadoc.
- That left the Dockerfile Javadoc stacked on the T29 header test.
- It has been moved back onto `stripDockerfileComments`.

## Verification

**Mutations.** Each was restored and confirmed with `cmp`:

| # | Mutation | Fails |
|---|---|---|
| M4 | a citation qualified with the wrong class (`PaymentServiceTest.shouldEmitReceiptIssuedAfterAttestation`) | citation guard (`:403`) |
| M5 | a cited class renamed (`ReceiptReconcilerXTest`) | citation guard, "(class)" |
| M6 | item 1 ticked `[x]` | partial guard (`:415`) |
| M7 | item 13 ticked `[x]` | partial guard (`:415`) |
| M8 | header Version reverted to `0.1`, while a second `0.2` row is added outside the header | header guard (`:347`) |
| M9 | typo in a `.method` continuation citation | citation guard (`:403`) |

- **M5's first attempt was wrong.** It used `ReceiptReconcilerTestX`, which doesn't end in `Test`, so the parser ignored it. That was a flawed mutation, not a guard gap. It was re-run with a valid rename and failed as intended.
- **M1–M3 from Phase 6** were re-run against the rewritten guards and still fail as intended (`:347`, `:349`, `:403`).

**Full suite.** `mvn -o -pl services/payment verify`: **535/535, BUILD SUCCESS**. That is 534 plus the F7 guard.

§9 item 11 still cites "534/534 at T29 Phase 6". That is a labelled snapshot (Kimi Phase 3 F6), and it was accurate when written.
