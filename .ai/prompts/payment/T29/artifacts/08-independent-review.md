# payment · T29 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 diff and Phase 7 self-review against the frozen brief.

---

## Finding 1 — Citation guard does not verify the cited class when a qualifier is present

- **Issue:** `everyTestNamedInTheVerificationChecklistExists` strips any `Class.` prefix and then searches all test sources for `void <name>(`. A citation such as `InvoiceServiceTest.shouldEmitInvoiceCreatedInSameTransactionAsInsert` would pass even if the method lived in `OutboxPublisherTest`. The Phase 6 manual `grep -l` caught this today, but the committed guard does not.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java` lines 349-383.
- **Recommendation:** When the citation includes a class qualifier, restrict the search to that class's source file. For bare method names, keep the global search.
- **Confidence:** Low — every pair was manually verified in Phase 6, but the guard should encode that invariant.

---

## Finding 2 — Class-only citations are not checked

- **Issue:** The regex only extracts `should[A-Za-z0-9_]+` names, so class-only citations such as `SchemaBaselineMigrationIntegrationTest`, `PaymentsOpenApiContractTest`, `PaymentEventPayloadContractTest`, `CryptoClientTest`, `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`, and `LedgerVerifierTest` are not validated. A future rename of one of those classes would leave the spec pointing at a ghost.
- **Evidence:** `T01SkeletonRegressionTest.java` line 44 (regex); `spec/payment-service/package.md` §9.
- **Recommendation:** Extend the parser to also match backtick-quoted class names ending in `Test` and assert a corresponding `.java` file exists under `src/test/java`.
- **Confidence:** Low — the classes exist today.

---

## Finding 3 — Spec references new runtime components without a guard

- **Issue:** The updated §9 cites `ReceiptReconciler` and `LedgerIntegrityJob` as interim tamper-detection components. There is no test or source-text guard that proves these classes exist or are scheduled, so a future deletion would not fail the build or the spec guard.
- **Evidence:** `spec/payment-service/package.md` §9 items 7 and 14.
- **Recommendation:** Either add an ArchUnit/source-text test that asserts the classes exist and carry a `@Scheduled`/`@Component` annotation, or soften the spec wording to "interim components introduced at `b8a654e8`" so it reads as a historical snapshot rather than a living guarantee.
- **Confidence:** Low — the classes exist now, but the spec should not over-claim durable presence without a guard.

---

## Finding 4 — The "Not applicable this revision" skip could mask other deferred citations

- **Issue:** The citation parser skips an entire checklist line if it contains the text "Not applicable this revision". Currently only item 14 uses that phrase. If a future partial item also uses that phrase and cites real tests on the same line, those real tests would not be validated.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 361-364.
- **Recommendation:** Parse citations first, then skip only the specific deferred test name (`shouldAnchorLedgerHeadOnChainDaily`), not the whole line.
- **Confidence:** Info — no such line exists today.

---

## Finding 5 — Item 10's same-transaction evidence is still incomplete

- **Issue:** This aligns with Phase 7 self-review S1. Item 10 claims every published event is emitted through the outbox in the same transaction as its state change, but it only names `InvoiceServiceTest.shouldEmitInvoiceCreatedInSameTransactionAsInsert`. The same-transaction property for `payment.seen`, `payment.finalized`, and `receipt.issued` is not cited.
- **Evidence:** `spec/payment-service/package.md` §9 item 10.
- **Recommendation:** Cite the emitting task's own tests by name, or reword the citation to "unit test for `invoice.created` plus per-task emitter tests for the other three events."
- **Confidence:** Low — the property is tested elsewhere; the citation just needs to be honest.

---

## Finding 6 — The header guard does not verify the Version/Status rows are in the header table

- **Issue:** `paymentSpecIsBumpedToReadyForImplVersionZeroPointTwo` searches the entire spec for `^| Version | `0.2` |` and `^| Status | `READY FOR IMPL``. If a later section added a second table with a Version or Status row, the guard could pass even if the header table still said `0.1`/`DRAFT`.
- **Evidence:** `T01SkeletonRegressionTest.java` lines 341-346.
- **Recommendation:** Restrict the regex search to the first N lines of the file (e.g., the first 20 lines), or parse the header table explicitly.
- **Confidence:** Info — unlikely to happen by accident.

---

## Finding 7 — No guard ensures partial items stay partial

- **Issue:** The spec guard checks the header values and cited test names, but it does not check that items 1 and 13 remain visibly partial (`- [ ]`) and item 14 remains deferred. A future edit could silently tick them without failing any test.
- **Evidence:** `spec/payment-service/package.md` §9 items 1, 13, 14.
- **Recommendation:** Add source-text assertions that items 1 and 13 contain the word "Partial", that item 14 contains "Not applicable this revision" or "deferred", and that none of the three is rendered as `- [x]`.
- **Confidence:** Low — defensive completeness for the spec's honesty contract.

---

## Finding 8 — `tasks.md` task 29 status is still open

- **Issue:** The task closes the spec bump and adds a guard, but `spec/payment-service/tasks.md` still lists task 29 as not done. The brief scopes `tasks.md` out, but leaving it untouched creates a mismatch between the spec status and the task tracker.
- **Evidence:** `spec/payment-service/tasks.md` (task 29).
- **Recommendation:** Update `tasks.md` task 29 to a completed status in the same commit, or record in Phase 6 notes that it will be updated separately.
- **Confidence:** Info — administrative consistency.

---

## Summary

T29's spec bump is accurate and honest about partials and deferred work. The guard tests are a good regression addition. Findings 1–3 and 5 would make the guard suite materially stronger; Findings 4, 6–8 are smaller robustness or documentation-consistency notes. No production code changes are needed.
