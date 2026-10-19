# payment · T29 · Phase 7 — Self-Review

Consumes: `artifacts/06-implementation-notes.md` and the diff. This artifact contains no code; it is documentation only.

## AC check

| AC | Status | Evidence |
|---|---|---|
| AC1 | met | `verify` 534/534; `SECURITY-THREAT-MODEL.md` row #5 closed (T28, `b8a654e8`) |
| AC2 | met | 14/14 items addressed. 2–12 `[x]` with citations, 1 and 13 visible Partial, 14 kept with a note |
| AC3 | met | `package.md:8` reads `0.2`; `:9` reads `READY FOR IMPL` plus the disclosure |
| AC4 | met | header guard; mutations M1 and M2 fail it |
| AC5 | met | citation guard finds 20 names; mutation M3 fails it |

## Findings

### S1 — Low. Item 10's same-transaction evidence names only `invoice.created`

- **What the item claims.** Every published event goes through the outbox in the same transaction as its state change.
- **What it cites.** One named unit test, for `invoice.created`, plus the end-to-end check that all four outbox events are relayed and marked published.
- **The gap.** The same-transaction property for `payment.seen`, `payment.finalized` and `receipt.issued` was covered by each emitting task's own tests, but none is cited by name here.
- **Suggested fix.** Cite those tests by name, or reword the citation to say per-task.

### S2 — Low. The citation guard checks names, not classes

- **The gap.** It strips the `Class.` qualifier and searches all test sources for `void <name>(`. A citation that pairs a real method with the wrong class would pass.
- **Today.** Every pair was checked by `grep -l` during Phase 6.
- **Suggested fix.** Check the method inside the named class's file when a qualifier is present.

### S3 — Low. Bare class citations are not checked

- **The gap.** Class names without a test method aren't covered by the guard: `SchemaBaselineMigrationIntegrationTest`, `PaymentsOpenApiContractTest`, `PaymentEventPayloadContractTest`, `CryptoClientTest`, `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`, `LedgerVerifierTest`.
- **Today.** All of them exist.
- **Suggested fix.** A rename of one of them would go unnoticed, so the guard could also resolve `` `…Test` `` tokens to files.

### S4 — Info. Item 2 rests on process, not a test

The LOCKED-decisions item is backed by the per-task Phase 12 verifications, not by an executable check. This is the same as crypto's item 2, and there is no practical single test for "no silent deviation".

### S5 — Info. Item 11's count is a dated snapshot

The 534 count will drift as tests are added. It is labelled as a snapshot (Kimi F6), which is the intended trade-off.

## Scope check

- **Diff.** `spec/payment-service/package.md` (header and §9 only) and `T01SkeletonRegressionTest` (two tests). Nothing else.
- **Main code.** No production code changed.
- **Secrets.** None.
